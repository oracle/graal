/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.svm.interpreter.ristretto;

import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandleInfo;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.StringConcatFactory;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import org.graalvm.collections.EconomicMap;

import com.oracle.svm.core.classinitialization.EnsureClassInitializedNode;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.DynamicHubUtils;
import com.oracle.svm.core.hub.RuntimeClassLoading;
import com.oracle.svm.core.hub.registry.ClassRegistries;
import com.oracle.svm.core.methodhandles.MethodHandleInterpreterUtils;
import com.oracle.svm.core.methodhandles.Target_java_lang_invoke_BoundMethodHandle;
import com.oracle.svm.espresso.classfile.ConstantPool.Tag;
import com.oracle.svm.espresso.shared.resolver.CallKind;
import com.oracle.svm.graal.meta.SubstrateMetaAccess;
import com.oracle.svm.graal.meta.SubstrateType;
import com.oracle.svm.interpreter.CallSiteLink;
import com.oracle.svm.interpreter.ResolvedInvokeDynamicConstant;
import com.oracle.svm.interpreter.RuntimeInterpreterConstantPool;
import com.oracle.svm.interpreter.SuccessfulCallSiteLink;
import com.oracle.svm.interpreter.metadata.BytecodeStream;
import com.oracle.svm.interpreter.metadata.Bytecodes;
import com.oracle.svm.interpreter.metadata.InterpreterConstantPool.LinkedInvoke;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedJavaField;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedJavaMethod;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedJavaType;
import com.oracle.svm.interpreter.ristretto.meta.RistrettoConstantReflectionProvider;
import com.oracle.svm.interpreter.ristretto.meta.RistrettoMethod;
import com.oracle.svm.interpreter.ristretto.meta.RistrettoType;
import com.oracle.svm.shared.util.SubstrateUtil;

import jdk.graal.compiler.core.ProfileReplaySupport;
import jdk.graal.compiler.core.common.spi.ConstantFieldProvider;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.NodeSourcePosition;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.graphbuilderconf.ClassInitializationPlugin;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodes.spi.StableProfileProvider;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.util.Providers;
import jdk.vm.ci.meta.ConstantPool;
import jdk.vm.ci.meta.DefaultProfilingInfo;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.ProfilingInfo;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.TriState;

/// Compiler configuration, profile capture and linkage observation for Ristretto replay.
final class RistrettoProfileReplaySupport {

    /// Method identity format shared by profile capture and prepared replay.
    static final String METHOD_FORMAT = "%H.%n(%P)%R";
    /// Linkage schema version required by the current capture and reconstruction logic.
    static final int LINKAGE_VERSION = 7;

    /**
     * Selects the provider before graph construction and optimistic optimization queries. Prepared
     * replay is frozen and cannot fall back to live data; disk loading must happen on the caller.
     */
    static StableProfileProvider createProfileProvider(OptionValues options, RistrettoMethod method, int entryBCI, RistrettoProfileReplay profiles) {
        GraalError.guarantee(!ProfileReplaySupport.Options.SaveProfiles.getValue(options) || !(Thread.currentThread() instanceof RuntimeClassLoading.NoClassLoadingThread),
                        "Ristretto profile capture requires a thread that permits class loading");
        GraalError.guarantee(ProfileReplaySupport.Options.LoadProfiles.getValue(options) == null || profiles != null,
                        "Ristretto profile replay requires a prepared type resolver");
        GraalError.guarantee(profiles == null || !ProfileReplaySupport.Options.SaveProfiles.getValue(options),
                        "Ristretto prepared replay cannot capture profiles at the same time");
        if (profiles != null) {
            return profiles.newProvider(method, entryBCI);
        }
        return ProfileReplaySupport.Options.SaveProfiles.getValue(options) ? new CaptureProfileProvider(method) : new StableProfileProvider();
    }

    static ProfileReplaySupport startCapture(DebugContext debug, int entryBCI, RistrettoMethod method, StableProfileProvider profileProvider) {
        return ProfileReplaySupport.Options.SaveProfiles.getValue(debug.getOptions())
                        ? ProfileReplaySupport.profileReplayPrologue(debug, entryBCI, method, profileProvider,
                                        type -> validateCaptureType(method, type))
                        : null;
    }

    /** Installs constant reflection and protects uninitialized static storage during replay. */
    static Providers configureProviders(Providers providers, boolean replay) {
        Providers configuredProviders = providers.copyWith(new RistrettoConstantReflectionProvider((SubstrateMetaAccess) providers.getMetaAccess(), providers.getSnippetReflection(), replay));

        // Lowered reads can fold through raw memory, bypassing constant reflection.
        if (replay) {
            ConstantFieldProvider delegate = configuredProviders.getConstantFieldProvider();
            configuredProviders = configuredProviders.copyWith(new ConstantFieldProvider() {
                @Override
                public <T> T readConstantField(ResolvedJavaField field, ConstantFieldTool<T> tool) {
                    if (field.isStatic() && !field.getDeclaringClass().isInitialized()) {
                        return null;
                    }
                    return delegate.readConstantField(field, tool);
                }

                @Override
                public boolean maybeFinal(ResolvedJavaField field) {
                    return delegate.maybeFinal(field);
                }

                @Override
                public boolean isTrustedFinal(CanonicalizerTool tool, ResolvedJavaField field) {
                    return (!field.isStatic() || field.getDeclaringClass().isInitialized()) && delegate.isTrustedFinal(tool, field);
                }
            });
        }
        return configuredProviders;
    }

    /** Emits initialization barriers for replay without running workload initializers at compile time. */
    static void configureGraphBuilder(GraphBuilderConfiguration configuration, boolean replay) {
        if (replay) {
            configuration.getPlugins().setClassInitializationPlugin(new ClassInitializationPlugin() {
                @Override
                public boolean supportsLazyInitialization(ConstantPool pool) {
                    return true;
                }

                @Override
                public void loadReferencedType(GraphBuilderContext builder, ConstantPool pool, int cpi, int bytecode) {
                    pool.loadReferencedType(cpi, bytecode);
                }

                @Override
                public boolean apply(GraphBuilderContext builder, ResolvedJavaType type, Supplier<FrameState> frameState) {
                    if (type.isArray()) {
                        return false;
                    }
                    GraalError.guarantee(type instanceof SubstrateType, "Unexpected replay initialization type: %s", type);
                    DynamicHub hub = ((SubstrateType) type).getHub();
                    if (!hub.getClassInitializationInfo().isSlowPathRequired()) {
                        return false;
                    }
                    var constant = builder.getSnippetReflection().forObject(hub);
                    var hubNode = ConstantNode.forConstant(constant, builder.getMetaAccess(), builder.getGraph());
                    builder.canonicalizeAndAdd(new EnsureClassInitializedNode(hubNode, frameState.get()));
                    return true;
                }
            });
        }
    }

    /// Supported semantic positions in a validated concat call-site link.
    enum OriginRole {
        APPENDIX_ENTRY,
        INVOKER,
        BOUND_TARGET_ENTRY
    }

    /**
     * Identifies a generated method by its first observed concat call site and semantic role.
     *
     * @param method formatted identity of the visible method containing the concat site
     * @param bci offset of that invokedynamic instruction
     * @param role position of the generated method within the validated concat link
     */
    record DynamicOrigin(String method, int bci, OriginRole role) {
        /// Combines the stable call-site origin with the generated entry signature.
        String identity(InterpreterResolvedJavaMethod entry) {
            return "indy:" + method + "@" + bci + ":" + role + ":" + entry.format("%n(%P)%R");
        }
    }

    /**
     * Contains only validated literal strings from a JDK concat bootstrap's arguments.
     *
     * @param recipe first bootstrap argument, containing the concat recipe markers
     * @param constants remaining literal string arguments in class-file order
     */
    record ConcatRecipe(String recipe, List<String> constants) {
    }

    /** Excludes hidden types and requires visible types to match the root loader namespace. */
    private static boolean validateCaptureType(RistrettoMethod root, ResolvedJavaType type) {
        if (type.isHidden()) {
            return false;
        }
        ClassLoader loader = root.getDeclaringClass().getHub().getClassLoader();
        try {
            Class<?> visible = ClassRegistries.forName(type.toClassName(), loader);
            if (!(type instanceof SubstrateType substrateType) || DynamicHub.fromClass(visible) != substrateType.getHub()) {
                throw new IllegalArgumentException("Captured type is outside the root loader namespace: " + type.toJavaName());
            }
            return true;
        } catch (ClassNotFoundException ex) {
            throw new IllegalArgumentException("Captured type is unavailable in the root loader namespace: " + type.toJavaName(), ex);
        }
    }

    /**
     * Completes the training profile inventory before serializing it with observed linkage.
     * Hidden methods participate in linkage discovery but use empty profiling information.
     */
    private static final class CaptureProfileProvider extends StableProfileProvider {
        /// Root whose loader defines the namespace for captured receiver types.
        private final RistrettoMethod root;

        CaptureProfileProvider(RistrettoMethod root) {
            this.root = root;
        }

        @Override
        public ProfilingInfo deriveForContext(NodeSourcePosition context, ResolvedJavaMethod method, boolean normal, boolean osr) {
            return method.getDeclaringClass().isHidden() ? DefaultProfilingInfo.get(TriState.UNKNOWN) : super.deriveForContext(context, method, normal, osr);
        }

        @Override
        public EconomicMap<String, Object> recordProfiles(EconomicMap<String, Object> map, TypeFilter filter, LambdaNameFormatter formatter) {
            Set<RistrettoMethod> methods = collectLinkedMethods();
            completeProfiles();
            // Hidden profiles are excluded; their call-site linkage is recorded separately.
            super.recordProfiles(map, filter, null);
            map.put("ristrettoLinkage", captureLinkageData(methods, map));
            return map;
        }

        /**
         * Adds inlineable linked callees before iterating the profile map, which discovery extends.
         * A cold handler can inline a target linked through another training path.
         */
        private Set<RistrettoMethod> collectLinkedMethods() {
            Set<RistrettoMethod> methods = new HashSet<>();
            methods.add(root);
            forQueriedProfiles((key, _) -> {
                if (key.method() instanceof RistrettoMethod method) {
                    methods.add(method);
                }
            });
            List<RistrettoMethod> pending = new ArrayList<>(methods);
            pending.sort(Comparator.comparing(method -> method.format(METHOD_FORMAT)));
            for (int index = 0; index < pending.size(); index++) {
                captureLinkedCallees(pending.get(index), methods, pending);
            }
            return methods;
        }

        /// Extends the worklist using cached ordinary links, without resolving cold call sites.
        private void captureLinkedCallees(RistrettoMethod method, Set<RistrettoMethod> methods, List<RistrettoMethod> pending) {
            byte[] code = method.getCode();
            if (code == null) {
                return;
            }
            for (int bci = 0; bci < code.length; bci = BytecodeStream.nextBCI(code, bci)) {
                int opcode = BytecodeStream.currentBC(code, bci);
                if (!Bytecodes.isInvoke(opcode) || opcode == Bytecodes.INVOKEDYNAMIC) {
                    continue;
                }
                var link = method.getInterpreterMethod().getConstantPool().peekLinkedInvoke(BytecodeStream.uncheckedReadCPI2(code, bci), opcode);
                if (link != null) {
                    RistrettoMethod seed = RistrettoMethod.getOrCreate(link.seedMethod);
                    captureCallee(seed, methods, pending);
                    if (link.callKind == CallKind.VTABLE_LOOKUP || link.callKind == CallKind.ITABLE_LOOKUP) {
                        captureReceiverCallees(method, bci, seed, methods, pending);
                    }
                }
            }
        }

        /// Includes profiled overrides when a cached dispatch seed is abstract or less specific.
        private void captureReceiverCallees(RistrettoMethod method, int bci, RistrettoMethod seed, Set<RistrettoMethod> methods, List<RistrettoMethod> pending) {
            var receivers = getProfilingInfo(null, method).getTypeProfile(bci);
            if (receivers != null) {
                for (var profiled : receivers.getTypes()) {
                    ResolvedJavaType receiver = profiled.getType();
                    if (receiver instanceof InterpreterResolvedJavaType interpreterType) {
                        receiver = RistrettoType.getOrCreate(interpreterType);
                    } else if (receiver instanceof SubstrateType substrateType) {
                        receiver = RistrettoUtils.toRType(substrateType);
                    }
                    captureCallee(receiver.resolveConcreteMethod(seed, method.getDeclaringClass()), methods, pending);
                }
            }
        }

        /// Queries a visible inlineable callee's profile and queues its bytecodes only once.
        private void captureCallee(ResolvedJavaMethod candidate, Set<RistrettoMethod> methods, List<RistrettoMethod> pending) {
            if (candidate instanceof RistrettoMethod callee && !callee.getDeclaringClass().isHidden() && callee.canBeInlined() && callee.getCode() != null) {
                getProfilingInfo(null, callee);
                if (methods.add(callee)) {
                    pending.add(callee);
                }
            }
        }

        /// Records queries for handlers that replay initialization barriers may make reachable.
        private void completeProfiles() {
            forQueriedProfiles((key, profile) -> {
                if (key.method() instanceof RistrettoMethod method && !method.getDeclaringClass().isHidden()) {
                    completeProfile(method, profile);
                }
            });
        }

        /// Materializes method-wide and bytecode-specific queries before the provider is serialized.
        private static void completeProfile(RistrettoMethod method, ProfilingInfo profile) {
            profile.isMature();
            profile.getCompilerIRSize(StructuredGraph.class);
            for (DeoptimizationReason reason : DeoptimizationReason.values()) {
                profile.getDeoptimizationCount(reason);
            }
            byte[] code = method.getCode();
            if (code != null) {
                for (int bci = 0; bci < code.length; bci = BytecodeStream.nextBCI(code, bci)) {
                    int opcode = BytecodeStream.currentBC(code, bci);
                    profile.getExceptionSeen(bci);
                    profile.getNullSeen(bci);
                    profile.getExecutionCount(bci);
                    if (Bytecodes.isProfiledIfBranch(opcode)) {
                        profile.getBranchTakenProbability(bci);
                    }
                    if (Bytecodes.isTypeProfiled(opcode)) {
                        profile.getTypeProfile(bci);
                    }
                    if (opcode == Bytecodes.TABLESWITCH || opcode == Bytecodes.LOOKUPSWITCH) {
                        profile.getSwitchProbabilities(bci);
                    }
                }
            }
        }

        /// Captures method/site snapshots before adding receiver classes to the linkage inventory.
        private EconomicMap<String, Object> captureLinkageData(Set<RistrettoMethod> methods, EconomicMap<String, Object> profiles) {
            List<InterpreterResolvedJavaMethod> captured = new ArrayList<>();
            methods.stream().sorted(Comparator.comparing(method -> method.format(METHOD_FORMAT))).forEach(method -> {
                if (method.getInterpreterMethod().getConstantPool() instanceof RuntimeInterpreterConstantPool && method.getInterpreterMethod().getCode() != null) {
                    captured.add(method.getInterpreterMethod());
                }
            });
            Map<InterpreterResolvedJavaMethod, DynamicOrigin> origins = captureOrigins(captured);
            for (var entry : origins.keySet()) {
                if (!captured.contains(entry)) {
                    captured.add(entry);
                }
            }
            List<Object> records = new ArrayList<>();
            EconomicMap<String, Object> classes = EconomicMap.create();
            captured.sort(Comparator.comparing(method -> methodIdentity(method, origins)));
            for (var method : captured) {
                var record = captureLinkage(method, classes, origins);
                if (!method.getDeclaringClass().isHidden() || origins.containsKey(method) || !list(record.get("sites"), "linkage sites").isEmpty()) {
                    records.add(record);
                }
            }
            EconomicMap<String, Object> linkage = EconomicMap.create();
            linkage.put("version", LINKAGE_VERSION);
            linkage.put("methods", records);
            captureProfileClasses(profiles, classes);
            linkage.put("classes", classes);
            return linkage;
        }

        /// Resolves captured receiver descriptors through the root loader and records link states.
        private void captureProfileClasses(EconomicMap<String, Object> profiles, EconomicMap<String, Object> classes) {
            try {
                ClassLoader loader = root.getDeclaringClass().getHub().getClassLoader();
                for (Object item : list(profiles.get("profiles"), "profiles")) {
                    captureMethodProfileClasses(dict(item, "profile"), loader, classes);
                }
            } catch (ClassNotFoundException ex) {
                throw new IllegalArgumentException("Captured profile type is outside the root loader namespace", ex);
            }
        }

        /// Visits the receiver profiles at each recorded bytecode of one captured method.
        private static void captureMethodProfileClasses(EconomicMap<String, Object> profile, ClassLoader loader, EconomicMap<String, Object> classes) throws ClassNotFoundException {
            for (Object datum : list(profile.get("data"), "bytecode data")) {
                Object receiver = dict(datum, "bytecode data").get("typeProfile");
                if (receiver != null) {
                    captureReceiverClasses(dict(receiver, "type profile"), loader, classes);
                }
            }
        }

        /// Records each receiver's class-link state using the same loader as the root method.
        private static void captureReceiverClasses(EconomicMap<String, Object> receiver, ClassLoader loader, EconomicMap<String, Object> classes) throws ClassNotFoundException {
            for (Object type : list(receiver.get("types"), "types")) {
                String name = string(dict(type, "profiled type").get("type"), "type");
                Class<?> clazz = ClassRegistries.forName(binaryName(name), loader);
                classes.put(clazz.getName(), DynamicHub.fromClass(clazz).isLinked());
            }
        }
    }

    /// Discovers generated entries only at visible, successfully linked, supported concat sites.
    private static Map<InterpreterResolvedJavaMethod, DynamicOrigin> captureOrigins(List<InterpreterResolvedJavaMethod> captured) {
        Map<InterpreterResolvedJavaMethod, DynamicOrigin> origins = new HashMap<>();
        for (var method : captured) {
            if (!method.getDeclaringClass().isHidden()) {
                byte[] code = method.getCode();
                for (int bci = 0; bci < code.length; bci = BytecodeStream.nextBCI(code, bci)) {
                    if (BytecodeStream.currentBC(code, bci) == Bytecodes.INVOKEDYNAMIC && dynamicLink(method, bci) instanceof SuccessfulCallSiteLink link &&
                                    concatRecipe(method, bci) != null) {
                        recordOrigins(origins, method, bci, link);
                    }
                }
            }
        }
        return origins;
    }

    /// Accepts only the actual JDK concat factory with literal String bootstrap arguments.
    static ConcatRecipe concatRecipe(InterpreterResolvedJavaMethod method, int bci) {
        var pool = (RuntimeInterpreterConstantPool) method.getConstantPool();
        int cpi = BytecodeStream.uncheckedReadCPI2(method.getCode(), bci);
        var owner = method.getDeclaringClass();
        var constant = (ResolvedInvokeDynamicConstant) pool.resolvedAt(cpi, owner);
        var bootstrap = constant.getBootstrapMethod();
        if (bootstrap.numBootstrapArguments() == 0) {
            return null;
        }
        for (int index = 0; index < bootstrap.numBootstrapArguments(); index++) {
            if (pool.tagAt(bootstrap.argAt(index)) != Tag.STRING) {
                return null;
            }
        }
        MethodHandle handle = pool.resolvedMethodHandleAt(bootstrap.getBootstrapMethodRef(), owner);
        MethodHandleInfo info;
        try {
            info = MethodHandles.lookup().revealDirect(handle);
        } catch (IllegalArgumentException ex) {
            return null;
        }
        MethodType factoryType = MethodType.methodType(CallSite.class, MethodHandles.Lookup.class, String.class, MethodType.class, String.class, Object[].class);
        if (info.getReferenceKind() != MethodHandleInfo.REF_invokeStatic || info.getDeclaringClass() != StringConcatFactory.class ||
                        !info.getName().equals("makeConcatWithConstants") || !info.getMethodType().equals(factoryType)) {
            return null;
        }
        List<String> constants = new ArrayList<>();
        for (int index = 1; index < bootstrap.numBootstrapArguments(); index++) {
            constants.add(pool.resolveStringAt(bootstrap.argAt(index)));
        }
        return new ConcatRecipe(pool.resolveStringAt(bootstrap.argAt(0)), List.copyOf(constants));
    }

    private static CallSiteLink dynamicLink(InterpreterResolvedJavaMethod method, int bci) {
        Object constant = method.getConstantPool().peekCachedEntry(BytecodeStream.uncheckedReadCPI2(method.getCode(), bci));
        return constant instanceof ResolvedInvokeDynamicConstant dynamic ? dynamic.getCallSiteLink(method, bci) : null;
    }

    /// Observes an existing entry without preparing the handle; absent entries remain null.
    private static InterpreterResolvedJavaMethod handleEntry(MethodHandle handle) {
        if (handle != null) {
            var entry = MethodHandleInterpreterUtils.peekVMEntry(handle);
            if (entry != null) {
                return InterpreterResolvedJavaMethod.fromMemberName(entry);
            }
        }
        return null;
    }

    /**
     * Recognizes the two-field first-argument binding produced by the concat factory. Logical
     * parameter types may be erased or promoted in its target, so only the binding's arity and bound
     * value are checked. Reading the trusted species fields does not invoke the stored target.
     */
    static MethodHandle boundTarget(SuccessfulCallSiteLink link) {
        if (!(link.getUnboxedAppendix() instanceof MethodHandle appendix) || !Target_java_lang_invoke_BoundMethodHandle.class.isInstance(appendix)) {
            return null;
        }
        var bound = SubstrateUtil.cast(appendix, Target_java_lang_invoke_BoundMethodHandle.class);
        if (bound.fieldCount() == 2 && bound.arg(0) instanceof MethodHandle target &&
                        target.type().parameterCount() == appendix.type().parameterCount() + 1 && target.type().parameterType(0).isInstance(bound.arg(1))) {
            return target;
        }
        return null;
    }

    /// Resolves a recorded semantic position without looking up a generated class name.
    static InterpreterResolvedJavaMethod originMethod(SuccessfulCallSiteLink link, OriginRole role) {
        return switch (role) {
            case INVOKER -> link.getInvoker();
            case APPENDIX_ENTRY -> handleEntry(link.getUnboxedAppendix() instanceof MethodHandle handle ? handle : null);
            case BOUND_TARGET_ENTRY -> handleEntry(boundTarget(link));
        };
    }

    /// Shared forms retain the first observed caller, BCI and role as their canonical identity.
    static void recordOrigins(Map<InterpreterResolvedJavaMethod, DynamicOrigin> origins, InterpreterResolvedJavaMethod method, int bci, SuccessfulCallSiteLink link) {
        for (OriginRole role : OriginRole.values()) {
            var entry = originMethod(link, role);
            if (entry != null && entry.getDeclaringClass().isHidden()) {
                origins.putIfAbsent(entry, new DynamicOrigin(method.format(METHOD_FORMAT), bci, role));
            }
        }
    }

    /// Uses a concat origin for generated entries and the formatted method identity otherwise.
    static String methodIdentity(InterpreterResolvedJavaMethod method, Map<InterpreterResolvedJavaMethod, DynamicOrigin> origins) {
        DynamicOrigin origin = origins.get(method);
        return origin == null ? method.format(METHOD_FORMAT) : origin.identity(method);
    }

    /// Snapshots a method identity, normalized bytecodes and ordered linkage sites without publishing links.
    static EconomicMap<String, Object> captureLinkage(InterpreterResolvedJavaMethod method, EconomicMap<String, Object> classes, Map<InterpreterResolvedJavaMethod, DynamicOrigin> origins) {
        EconomicMap<String, Object> record = EconomicMap.create();
        record.put("method", methodIdentity(method, origins));
        DynamicOrigin origin = origins.get(method);
        if (origin != null) {
            record.put("originMethod", origin.method);
            record.put("originBCI", origin.bci);
            record.put("originRole", origin.role.name());
        }
        recordClassLinkage(method.getDeclaringClass(), classes);
        byte[] code = method.getCode();
        byte[] normalized = code.clone();
        List<Object> sites = captureSites(method, classes, origins, normalized);
        record.put("code", HexFormat.of().formatHex(normalized));
        record.put("sites", sites);
        if (method.getDeclaringClass().isHidden() && origin == null) {
            record.put("unsupported", "Hidden linkage owner has no replay identity: " + method.format(METHOD_FORMAT));
        }
        return record;
    }

    /**
     * Visits linkage sites in bytecode order and clears only loader-local invokedynamic operands in
     * the supplied bytecode copy. The method's live bytecodes and cold cache entries are unchanged.
     */
    private static List<Object> captureSites(InterpreterResolvedJavaMethod method, EconomicMap<String, Object> classes,
                    Map<InterpreterResolvedJavaMethod, DynamicOrigin> origins, byte[] normalized) {
        byte[] code = method.getCode();
        List<Object> sites = new ArrayList<>();
        for (int bci = 0; bci < code.length; bci = BytecodeStream.nextBCI(code, bci)) {
            int opcode = BytecodeStream.currentBC(code, bci);
            if (isLinkageSite(method, bci, opcode)) {
                EconomicMap<String, Object> site = siteState(method, bci, classes, origins);
                sites.add(site);
                if (opcode == Bytecodes.INVOKEDYNAMIC) {
                    // These two bytes are a loader-local cache, not class-file identity.
                    normalized[bci + 3] = 0;
                    normalized[bci + 4] = 0;
                }
            }
        }
        return sites;
    }

    /// Selects invokes, fields and type-bearing constants whose cache state replay must reproduce.
    private static boolean isLinkageSite(InterpreterResolvedJavaMethod method, int bci, int opcode) {
        boolean constant = isTypeInstruction(opcode);
        if (isLdc(opcode)) {
            var tag = method.getConstantPool().tagAt(BytecodeStream.readCPI(method.getCode(), bci));
            constant = tag == Tag.CLASS || tag == Tag.METHODTYPE || tag == Tag.METHODHANDLE || tag == Tag.DYNAMIC;
        }
        return Bytecodes.isInvoke(opcode) || Bytecodes.isFieldAccess(opcode) || constant;
    }

    /// Reads the current constant, field or invocation cache state without resolving a cold entry.
    static EconomicMap<String, Object> siteState(InterpreterResolvedJavaMethod method, int bci, EconomicMap<String, Object> classes,
                    Map<InterpreterResolvedJavaMethod, DynamicOrigin> origins) {
        int opcode = BytecodeStream.currentBC(method.getCode(), bci);
        if (isConstantSite(opcode)) {
            return constantSiteState(method, bci, classes);
        }
        if (Bytecodes.isFieldAccess(opcode)) {
            Object cached = method.getConstantPool().peekCachedEntry(BytecodeStream.uncheckedReadCPI2(method.getCode(), bci));
            return fieldSiteState(method, bci, classes, cached instanceof InterpreterResolvedJavaField field ? field : null);
        }
        LinkedInvoke link = opcode == Bytecodes.INVOKEDYNAMIC ? null : method.getConstantPool().peekLinkedInvoke(BytecodeStream.uncheckedReadCPI2(method.getCode(), bci), opcode);
        return siteState(method, bci, classes, origins, link, opcode == Bytecodes.INVOKEDYNAMIC ? dynamicLink(method, bci) : null);
    }

    /// Captures the shared constant-pool field state without resolving a cold entry.
    static EconomicMap<String, Object> fieldSiteState(InterpreterResolvedJavaMethod method, int bci, EconomicMap<String, Object> classes, InterpreterResolvedJavaField field) {
        byte[] code = method.getCode();
        int opcode = BytecodeStream.currentBC(code, bci);
        int cpi = BytecodeStream.uncheckedReadCPI2(code, bci);
        var pool = method.getConstantPool();
        EconomicMap<String, Object> site = EconomicMap.create();
        site.put("bci", bci);
        site.put("opcode", opcode);
        site.put("cpi", cpi);
        site.put("name", pool.fieldName(cpi).toString());
        site.put("descriptor", pool.fieldType(cpi).toString());
        site.put("symbolicHolder", pool.className(pool.memberClassIndex(cpi)).toString());
        site.put("linked", field != null);
        if (field != null) {
            var holder = field.getDeclaringClass();
            if (holder.isHidden()) {
                site.put("unsupported", "Hidden field owner is not supported");
            } else {
                site.put("holderClass", holder.getJavaClass().getName());
                recordClassLinkage(holder, classes);
            }
            site.put("fieldStatic", field.isStatic());
        }
        return site;
    }

    /// Describes supplied invocation candidates before replay publishes them into their caches.
    static EconomicMap<String, Object> siteState(InterpreterResolvedJavaMethod method, int bci, EconomicMap<String, Object> classes, Map<InterpreterResolvedJavaMethod, DynamicOrigin> origins,
                    LinkedInvoke ordinaryLink, CallSiteLink dynamic) {
        byte[] code = method.getCode();
        int opcode = BytecodeStream.currentBC(code, bci);
        int cpi = BytecodeStream.uncheckedReadCPI2(code, bci);
        EconomicMap<String, Object> site = EconomicMap.create();
        site.put("bci", bci);
        site.put("opcode", opcode);
        site.put("cpi", cpi);
        if (opcode == Bytecodes.INVOKEDYNAMIC) {
            recordDynamicSite(method, bci, classes, origins, dynamic, site);
        } else {
            recordOrdinarySite(method, bci, classes, origins, ordinaryLink, site);
        }
        return site;
    }

    /// Describes a concat site from its existing link and literal recipe; no bootstrap is invoked.
    private static void recordDynamicSite(InterpreterResolvedJavaMethod method, int bci, EconomicMap<String, Object> classes, Map<InterpreterResolvedJavaMethod, DynamicOrigin> origins,
                    CallSiteLink dynamic, EconomicMap<String, Object> site) {
        int cpi = BytecodeStream.uncheckedReadCPI2(method.getCode(), bci);
        var pool = method.getConstantPool();
        InterpreterResolvedJavaMethod seed = null;
        site.put("name", pool.invokeDynamicName(cpi).toString());
        site.put("descriptor", pool.invokeDynamicSignature(cpi).toString());
        if (dynamic instanceof SuccessfulCallSiteLink successful) {
            seed = successful.getInvoker();
            recordConcatRecipe(method, bci, origins, successful, site);
        } else if (dynamic != null) {
            site.put("unsupported", "Failed dynamic call-site linkage");
        }
        recordLinkedTarget(seed, classes, origins, site);
    }

    /// Records only supported literal concat recipes and entries with reproducible identities.
    private static void recordConcatRecipe(InterpreterResolvedJavaMethod method, int bci, Map<InterpreterResolvedJavaMethod, DynamicOrigin> origins,
                    SuccessfulCallSiteLink link, EconomicMap<String, Object> site) {
        ConcatRecipe recipe = concatRecipe(method, bci);
        var entry = originMethod(link, OriginRole.APPENDIX_ENTRY);
        if (recipe == null || entry == null || !(link.getUnboxedAppendix() instanceof MethodHandle handle)) {
            site.put("unsupported", "Dynamic site has no supported concat recipe or prepared entry");
            return;
        }
        site.put("bootstrap", "StringConcatFactory.makeConcatWithConstants");
        site.put("recipe", recipe.recipe);
        site.put("constants", recipe.constants);
        site.put("entry", methodIdentity(entry, origins));
        site.put("appendix", handle.type().toMethodDescriptorString());
        recordBoundTarget(link, origins, site);
        if (entry.getDeclaringClass().isHidden() && !origins.containsKey(entry)) {
            site.put("unsupported", "Generated concat entry has no replay origin");
        }
    }

    /// Observes an optional bound target; an unprepared entry makes the concat site unsupported.
    private static void recordBoundTarget(SuccessfulCallSiteLink link, Map<InterpreterResolvedJavaMethod, DynamicOrigin> origins, EconomicMap<String, Object> site) {
        MethodHandle target = boundTarget(link);
        if (target != null) {
            var targetEntry = handleEntry(target);
            if (targetEntry == null) {
                site.put("unsupported", "Concat bound target has no prepared entry");
            } else {
                site.put("boundEntry", methodIdentity(targetEntry, origins));
            }
        }
    }

    /// Describes an ordinary link and its supported appendix without inserting it into the cache.
    private static void recordOrdinarySite(InterpreterResolvedJavaMethod method, int bci, EconomicMap<String, Object> classes,
                    Map<InterpreterResolvedJavaMethod, DynamicOrigin> origins, LinkedInvoke ordinaryLink, EconomicMap<String, Object> site) {
        int opcode = BytecodeStream.currentBC(method.getCode(), bci);
        int cpi = BytecodeStream.uncheckedReadCPI2(method.getCode(), bci);
        var pool = method.getConstantPool();
        InterpreterResolvedJavaMethod seed = null;
        Object appendix = null;
        site.put("name", pool.methodName(cpi).toString());
        site.put("descriptor", pool.methodSignature(cpi).toString());
        site.put("symbolicHolder", pool.className(pool.memberClassIndex(cpi)).toString());
        if (ordinaryLink != null) {
            seed = ordinaryLink.seedMethod;
            recordClassLinkage(ordinaryLink.symbolicHolder, classes);
            appendix = ordinaryLink.getAppendix(opcode);
            site.put("holder", ordinaryLink.symbolicHolder.toJavaName());
            site.put("holderClass", ordinaryLink.symbolicHolder.getJavaClass().getName());
            site.put("kind", ordinaryLink.callKind.name());
            site.put("receiver", ordinaryLink.hasReceiver(opcode));
            site.put("typeCheck", ordinaryLink.getReceiverCheckKind(opcode) != LinkedInvoke.NO_TYPE_CHECK);
        }
        recordLinkedTarget(seed, classes, origins, site);
        if (seed != null) {
            if (appendix == null) {
                site.put("appendix", "none");
            } else if (appendix instanceof MethodType type) {
                site.put("appendix", type.toMethodDescriptorString());
            } else {
                site.put("unsupported", "Cannot reconstruct arbitrary call-site appendix: " + appendix.getClass().getName());
            }
        }
    }

    /// Records a cached target's identity, marking hidden entries unsupported without a concat origin.
    private static void recordLinkedTarget(InterpreterResolvedJavaMethod seed, EconomicMap<String, Object> classes,
                    Map<InterpreterResolvedJavaMethod, DynamicOrigin> origins, EconomicMap<String, Object> site) {
        site.put("linked", seed != null);
        if (seed != null) {
            site.put("seed", methodIdentity(seed, origins));
            if (!seed.getDeclaringClass().isHidden()) {
                site.put("seedClass", seed.getDeclaringClass().getJavaClass().getName());
            }
            recordClassLinkage(seed.getDeclaringClass(), classes);
            if (seed.getDeclaringClass().isHidden() && !origins.containsKey(seed)) {
                site.put("unsupported", "Generated linked target has no replay identity");
            }
        }
    }

    /** Type instructions and class literals read the same constant-pool CLASS cache. */
    private static boolean isTypeInstruction(int opcode) {
        return opcode == Bytecodes.NEW || opcode == Bytecodes.ANEWARRAY || opcode == Bytecodes.MULTIANEWARRAY ||
                        opcode == Bytecodes.CHECKCAST || opcode == Bytecodes.INSTANCEOF;
    }

    static boolean isConstantSite(int opcode) {
        return isLdc(opcode) || isTypeInstruction(opcode);
    }

    private static boolean isLdc(int opcode) {
        return opcode == Bytecodes.LDC || opcode == Bytecodes.LDC_W || opcode == Bytecodes.LDC2_W;
    }

    /// Reads constant identity and cached state without resolving a cold entry or invoking a bootstrap.
    static EconomicMap<String, Object> constantSiteState(InterpreterResolvedJavaMethod method, int bci, EconomicMap<String, Object> classes) {
        var pool = method.getConstantPool();
        int cpi = BytecodeStream.readCPI(method.getCode(), bci);
        var tag = pool.tagAt(cpi);
        EconomicMap<String, Object> site = EconomicMap.create();
        site.put("bci", bci);
        site.put("opcode", BytecodeStream.currentBC(method.getCode(), bci));
        site.put("cpi", cpi);
        site.put("constantKind", tag.name());
        Object cached = pool.peekCachedEntry(cpi);
        site.put("linked", cached != null);
        if (tag == Tag.CLASS) {
            site.put("symbolicHolder", pool.className(cpi).toString());
        } else if (tag == Tag.METHODTYPE) {
            site.put("descriptor", pool.methodTypeSignature(cpi).toString());
        } else if (tag == Tag.METHODHANDLE) {
            int member = pool.methodHandleMemberIndex(cpi);
            boolean field = pool.tagAt(member) == Tag.FIELD_REF;
            site.put("referenceKind", pool.methodHandleRefKind(cpi));
            site.put("symbolicHolder", pool.className(pool.memberClassIndex(member)).toString());
            site.put("name", field ? pool.fieldName(member).toString() : pool.methodName(member).toString());
            site.put("descriptor", field ? pool.fieldType(member).toString() : pool.methodSignature(member).toString());
        } else if (tag == Tag.DYNAMIC) {
            site.put("name", pool.dynamicName(cpi).toString());
            site.put("descriptor", pool.dynamicType(cpi).toString());
            site.put("unsupported", "CONSTANT_Dynamic replay requires an explicit bootstrap/value policy");
            return site;
        } else {
            throw new IllegalArgumentException("Unexpected ldc constant kind: " + tag);
        }
        if (cached == null) {
            return site;
        }
        List<Class<?>> constantTypes;
        if (tag == Tag.CLASS && cached instanceof InterpreterResolvedJavaType classType) {
            constantTypes = List.of(classType.getJavaClass());
        } else {
            MethodType type;
            if (tag == Tag.METHODTYPE && cached instanceof MethodType methodType) {
                type = methodType;
            } else if (tag == Tag.METHODHANDLE && cached instanceof MethodHandle handle) {
                type = handle.type();
            } else {
                site.put("unsupported", "Unsupported cached ldc value");
                return site;
            }
            site.put("valueType", type.toMethodDescriptorString());
            constantTypes = new ArrayList<>(type.parameterList());
            constantTypes.add(type.returnType());
        }
        if (tag == Tag.METHODHANDLE) {
            Object owner = pool.peekCachedEntry(pool.memberClassIndex(pool.methodHandleMemberIndex(cpi)));
            if (!(owner instanceof InterpreterResolvedJavaType ownerType)) {
                site.put("unsupported", "Resolved method handle has no cached owner type");
                return site;
            }
            constantTypes.add(ownerType.getJavaClass());
        }
        List<String> names = new ArrayList<>();
        for (Class<?> clazz : constantTypes) {
            var hub = DynamicHub.fromClass(clazz);
            if (!hub.isPrimitive() && !names.contains(clazz.getName())) {
                if (hub.isHidden() || !(DynamicHubUtils.getInterpreterType(hub) instanceof InterpreterResolvedJavaType interpreterType)) {
                    site.put("unsupported", "ldc type has no replay identity: " + clazz.getName());
                    return site;
                }
                names.add(clazz.getName());
                recordClassLinkage(interpreterType, classes);
            }
        }
        names.sort(String::compareTo);
        site.put("constantClasses", names);
        return site;
    }

    private static void recordClassLinkage(InterpreterResolvedJavaType type, EconomicMap<String, Object> classes) {
        if (classes != null && !type.isHidden()) {
            Class<?> clazz = type.getJavaClass();
            classes.put(clazz.getName(), DynamicHub.fromClass(clazz).isLinked());
        }
    }

    /// Converts an object or array descriptor to the name accepted by the root class loader.
    static String binaryName(String name) {
        if (name.startsWith("[")) {
            return name.replace('/', '.');
        }
        if (name.startsWith("L") && name.endsWith(";")) {
            return name.substring(1, name.length() - 1).replace('/', '.');
        }
        throw new IllegalArgumentException("Invalid profiled type descriptor: " + name);
    }

    /// Requires a string-valued capture field instead of accepting an implicit conversion.
    static String string(Object value, String field) {
        if (value instanceof String string) {
            return string;
        }
        throw new IllegalArgumentException("Malformed profile field " + field);
    }

    /// Requires an ordered JSON array for a capture field.
    static List<?> list(Object value, String field) {
        if (value instanceof List<?> list) {
            return list;
        }
        throw new IllegalArgumentException("Malformed profile field " + field);
    }

    /// Requires a JSON object represented by the capture parser's economic map.
    @SuppressWarnings("unchecked")
    static EconomicMap<String, Object> dict(Object value, String field) {
        if (value instanceof EconomicMap<?, ?> map) {
            return (EconomicMap<String, Object>) map;
        }
        throw new IllegalArgumentException("Malformed profile field " + field);
    }

}
