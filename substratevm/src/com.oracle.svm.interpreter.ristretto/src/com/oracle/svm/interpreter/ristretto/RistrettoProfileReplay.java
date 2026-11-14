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

import static com.oracle.svm.interpreter.ristretto.RistrettoProfileReplaySupport.LINKAGE_VERSION;
import static com.oracle.svm.interpreter.ristretto.RistrettoProfileReplaySupport.METHOD_FORMAT;
import static com.oracle.svm.interpreter.ristretto.RistrettoProfileReplaySupport.binaryName;
import static com.oracle.svm.interpreter.ristretto.RistrettoProfileReplaySupport.boundTarget;
import static com.oracle.svm.interpreter.ristretto.RistrettoProfileReplaySupport.captureLinkage;
import static com.oracle.svm.interpreter.ristretto.RistrettoProfileReplaySupport.concatRecipe;
import static com.oracle.svm.interpreter.ristretto.RistrettoProfileReplaySupport.constantSiteState;
import static com.oracle.svm.interpreter.ristretto.RistrettoProfileReplaySupport.dict;
import static com.oracle.svm.interpreter.ristretto.RistrettoProfileReplaySupport.fieldSiteState;
import static com.oracle.svm.interpreter.ristretto.RistrettoProfileReplaySupport.isConstantSite;
import static com.oracle.svm.interpreter.ristretto.RistrettoProfileReplaySupport.list;
import static com.oracle.svm.interpreter.ristretto.RistrettoProfileReplaySupport.methodIdentity;
import static com.oracle.svm.interpreter.ristretto.RistrettoProfileReplaySupport.originMethod;
import static com.oracle.svm.interpreter.ristretto.RistrettoProfileReplaySupport.recordOrigins;
import static com.oracle.svm.interpreter.ristretto.RistrettoProfileReplaySupport.siteState;
import static com.oracle.svm.interpreter.ristretto.RistrettoProfileReplaySupport.string;

import java.io.IOException;
import java.io.Reader;
import java.lang.invoke.MethodHandle;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.graalvm.collections.EconomicMap;

import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.DynamicHubUtils;
import com.oracle.svm.core.hub.registry.ClassRegistries;
import com.oracle.svm.core.methodhandles.MethodHandleInterpreterUtils;
import com.oracle.svm.espresso.classfile.ConstantPool.Tag;
import com.oracle.svm.espresso.shared.resolver.CallKind;
import com.oracle.svm.espresso.shared.resolver.CallSiteType;
import com.oracle.svm.graal.meta.SubstrateMetaAccess;
import com.oracle.svm.graal.meta.SubstrateType;
import com.oracle.svm.interpreter.CallSiteLink;
import com.oracle.svm.interpreter.CremaLinkResolver;
import com.oracle.svm.interpreter.CremaRuntimeAccess;
import com.oracle.svm.interpreter.ResolvedInvokeDynamicConstant;
import com.oracle.svm.interpreter.RuntimeInterpreterConstantPool;
import com.oracle.svm.interpreter.SuccessfulCallSiteLink;
import com.oracle.svm.interpreter.metadata.BytecodeStream;
import com.oracle.svm.interpreter.metadata.Bytecodes;
import com.oracle.svm.interpreter.metadata.InterpreterConstantPool;
import com.oracle.svm.interpreter.metadata.InterpreterConstantPool.LinkedInvoke;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedInvokeGenericJavaMethod;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedJavaField;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedJavaMethod;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedJavaType;
import com.oracle.svm.interpreter.ristretto.RistrettoProfileReplaySupport.ConcatRecipe;
import com.oracle.svm.interpreter.ristretto.RistrettoProfileReplaySupport.DynamicOrigin;
import com.oracle.svm.interpreter.ristretto.RistrettoProfileReplaySupport.OriginRole;
import com.oracle.svm.interpreter.ristretto.meta.RistrettoMetaAccess;
import com.oracle.svm.interpreter.ristretto.meta.RistrettoMethod;

import jdk.graal.compiler.graph.NodeSourcePosition;
import jdk.graal.compiler.nodes.spi.StableProfileProvider;
import jdk.graal.compiler.util.json.JsonParser;
import jdk.vm.ci.meta.DefaultProfilingInfo;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.ProfilingInfo;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.TriState;

/**
 * Loads captured profiles for replaying a Ristretto compilation.
 *
 * {@link #prepare} resolves method owners and receiver types using the root method's class loader
 * and reconstructs recorded field and call-site linkage before the compilation is submitted to a compiler
 * thread, where class loading is unavailable. Preparation does not initialize workload classes or
 * execute their invocations. Captures without compatible linkage metadata must be regenerated;
 * linked string-concat sites are rebuilt from validated JDK bootstrap recipes. Other dynamic
 * bootstraps and unsupported appendices are rejected.
 * {@link #newProvider} creates a provider for each compilation attempt and freezes it so missing
 * profile data causes an error. Hidden methods' profiling records are ignored and queries use empty
 * profiling information. Generated linkage owners are identified by the originating concat site's
 * invoker, appendix entry or bound-target entry, never by their loader-local names.
 *
 * Candidate links remain private until their bytecodes, sites and class-link states match the
 * capture. The caller must prevent workload execution while preparing and verifying roots that may
 * share constant pools.
 */
public final class RistrettoProfileReplay {
    /**
     * Distinguishes independently captured normal and OSR profiles of the same method.
     *
     * @param method formatted method identity in the capture
     * @param normal whether the profile includes normal-entry execution
     * @param osr whether the profile includes on-stack-replacement execution
     */
    private record ProfileViewKey(String method, boolean normal, boolean osr) {
    }

    /**
     * Keys a receiver type within the method profile that refers to it.
     *
     * @param method formatted identity of the method whose profile contains the type
     * @param type type descriptor resolved in the root method's loader namespace
     */
    private record TypeKey(String method, String type) {
    }

    /// Root method whose identity and class loader every compilation attempt must match.
    private final RistrettoMethod root;
    /// Captured compilation entry BCI, including the normal-entry sentinel or an OSR entry.
    private final int entryBCI;
    /// Validated capture with hidden profiles removed, loaded into a fresh cache for each attempt.
    private final EconomicMap<String, Object> data;
    /// Visible profile method identities mapped to their prepared classes for loader checks.
    private final Map<String, Class<?>> owners;
    /// Receiver types resolved before submission to compiler threads that cannot load classes.
    private final Map<TypeKey, ResolvedJavaType> types;
    /// Prepared linkage and expected shared-cache state, rechecked after other roots are prepared.
    private final Linkage linkage;

    private RistrettoProfileReplay(RistrettoMethod root, int entryBCI, EconomicMap<String, Object> data,
                    Map<String, Class<?>> owners, Map<TypeKey, ResolvedJavaType> types, Linkage linkage) {
        this.root = root;
        this.entryBCI = entryBCI;
        this.data = data;
        this.owners = Map.copyOf(owners);
        this.types = Map.copyOf(types);
        this.linkage = linkage;
    }

    /**
     * Reads one successful root compilation capture and resolves its types before worker submission.
     * The exact file and root identity avoid selecting a different compilation from a directory.
     */
    public static RistrettoProfileReplay prepare(Path file, RistrettoMethod root, int entryBCI) throws IOException {
        EconomicMap<String, Object> selected;
        try (Reader reader = Files.newBufferedReader(file)) {
            selected = JsonParser.parseDict(reader);
        }
        if (!root.format(METHOD_FORMAT).equals(selected.get("method")) || !Integer.valueOf(entryBCI).equals(selected.get("entryBCI"))) {
            throw new IllegalArgumentException("Profile root or entry BCI differs: " + file);
        }
        if (!Boolean.TRUE.equals(selected.get("result"))) {
            throw new IllegalArgumentException("Cannot replay profiles from a failed compilation");
        }
        ClassLoader rootLoader = root.getDeclaringClass().getHub().getClassLoader();
        Map<String, Class<?>> owners = new HashMap<>();
        Map<TypeKey, ResolvedJavaType> types = new HashMap<>();
        Set<ProfileViewKey> views = new HashSet<>();
        RistrettoMetaAccess metaAccess = new RistrettoMetaAccess(SubstrateMetaAccess.singleton());
        try {
            boolean foundRoot = false;
            List<Object> replayProfiles = new ArrayList<>();
            for (Object record : list(selected.get("profiles"), "profiles")) {
                EconomicMap<String, Object> profile = dict(record, "method profile");
                if (profile.containsKey("stableLambdaName")) {
                    continue;
                }
                replayProfiles.add(profile);
                String signature = string(profile.get("method"), "method");
                int separator = signature.lastIndexOf('.', signature.indexOf('('));
                if (separator < 1) {
                    throw new IllegalArgumentException("Unsupported profile method identity: " + signature);
                }
                Object normal = profile.get("includeNormal");
                Object osr = profile.get("includeOSR");
                if (!(normal instanceof Boolean) || !(osr instanceof Boolean)) {
                    throw new IllegalArgumentException("Malformed normal/OSR profile view: " + signature);
                }
                if (!views.add(new ProfileViewKey(signature, (Boolean) normal, (Boolean) osr))) {
                    throw new IllegalArgumentException("Duplicate profile view: " + signature);
                }
                foundRoot |= signature.equals(root.format(METHOD_FORMAT));
                Class<?> owner = ClassRegistries.forName(signature.substring(0, separator), rootLoader);
                owners.put(signature, owner);
                prepareTypes(profile, signature, rootLoader, metaAccess, types);
            }
            if (!foundRoot) {
                throw new IllegalArgumentException("Missing root method profile: " + root.format(METHOD_FORMAT));
            }
            selected.put("profiles", replayProfiles);
            // Parse every profile before prepareLinkage publishes any candidate call-site links.
            new StableProfileProvider().load(selected, root.getDeclaringClass(), false, null);
            return new RistrettoProfileReplay(root, entryBCI, selected, owners, types, prepareLinkage(selected, rootLoader, root, types.keySet()));
        } catch (ClassNotFoundException | RuntimeException ex) {
            throw new IllegalArgumentException("Cannot prepare Ristretto profiles for " + root.format(METHOD_FORMAT) + " from " + file, ex);
        }
    }

    /** Validates saved bytecode records and resolves receivers while class loading is allowed. */
    private static void prepareTypes(EconomicMap<String, Object> profile, String signature, ClassLoader rootLoader,
                    RistrettoMetaAccess metaAccess, Map<TypeKey, ResolvedJavaType> types) throws ClassNotFoundException {
        Object counts = profile.get("deoptimizationCounts");
        if (counts != null) {
            var cursor = dict(counts, "deoptimizationCounts").getEntries();
            while (cursor.advance()) {
                DeoptimizationReason.valueOf(cursor.getKey());
                if (!(cursor.getValue() instanceof Integer count) || count < 0) {
                    throw new IllegalArgumentException("Malformed deoptimization count: " + cursor.getKey());
                }
            }
        }
        Set<Integer> offsets = new HashSet<>();
        for (Object datum : list(profile.get("data"), "bytecode data")) {
            EconomicMap<String, Object> bci = dict(datum, "bytecode data");
            if (!(bci.get("bci") instanceof Integer offset) || offset < 0 || !offsets.add(offset)) {
                throw new IllegalArgumentException("Invalid or duplicate profile BCI in " + signature);
            }
            Object receiver = bci.get("typeProfile");
            if (receiver == null) {
                continue;
            }
            EconomicMap<String, Object> typeProfile = dict(receiver, "typeProfile");
            TriState.valueOf(string(typeProfile.get("nullSeen"), "nullSeen"));
            probability(typeProfile.get("notRecordedProbability"));
            for (Object item : list(typeProfile.get("types"), "types")) {
                EconomicMap<String, Object> type = dict(item, "profiled type");
                String name = string(type.get("type"), "type");
                probability(type.get("probability"));
                Class<?> clazz = ClassRegistries.forName(binaryName(name), rootLoader);
                if (DynamicHubUtils.getInterpreterType(DynamicHub.fromClass(clazz)) == null) {
                    throw new IllegalArgumentException("Profile type lacks interpreter metadata: " + name);
                }
                types.put(new TypeKey(signature, name), metaAccess.lookupJavaType(clazz));
            }
        }
    }

    /** Returns an independent frozen cache, also for a compilation retry. */
    StableProfileProvider newProvider(RistrettoMethod method, int bci) {
        if (!method.equals(root) || bci != entryBCI) {
            throw new IllegalArgumentException("Prepared profiles belong to a different root method, loader or entry BCI");
        }
        StableProfileProvider provider = new StableProfileProvider(this::resolve) {
            @Override
            public ProfilingInfo getProfilingInfo(NodeSourcePosition context, ResolvedJavaMethod queriedMethod, boolean normal, boolean osr) {
                if (queriedMethod.getDeclaringClass().isHidden()) {
                    return DefaultProfilingInfo.get(TriState.UNKNOWN);
                }
                verifyOwner(queriedMethod);
                return super.getProfilingInfo(context, queriedMethod, normal, osr);
            }
        };
        provider.load(data, root.getDeclaringClass(), false, null);
        provider.freeze();
        return provider;
    }

    @SuppressWarnings("unused")
    private ResolvedJavaType resolve(ResolvedJavaMethod method, ProfilingInfo unusedLiveProfile, int unusedBCI, ResolvedJavaType unusedIssuingType, String name) {
        String signature = method.format(METHOD_FORMAT);
        ResolvedJavaType type = types.get(new TypeKey(signature, name));
        if (type == null) {
            throw new IllegalArgumentException("Unprepared profile type " + name + " in " + signature);
        }
        return type;
    }

    private void verifyOwner(ResolvedJavaMethod method) {
        String signature = method.format(METHOD_FORMAT);
        Class<?> owner = owners.get(signature);
        if (owner == null || !(method.getDeclaringClass() instanceof SubstrateType declaringType) || declaringType.getHub() != DynamicHub.fromClass(owner)) {
            throw new IllegalArgumentException("Profile method loader mismatch: " + signature);
        }
    }

    /**
     * Validated replay state retained after candidate links have been published.
     *
     * @param methods resolved methods and their expected bytecode/site snapshots
     * @param classes expected class-link states, including classes that must remain unlinked
     * @param origins generated methods identified by their originating concat call sites
     */
    private record Linkage(List<MethodLinkage> methods, Map<Class<?>, Boolean> classes, Map<InterpreterResolvedJavaMethod, DynamicOrigin> origins) {
    }

    /**
     * Binds a captured method snapshot to the corresponding method in the replay loader.
     *
     * @param method resolved replay method whose constant pool is checked
     * @param saved normalized bytecodes and ordered linkage-site records from capture
     */
    private record MethodLinkage(InterpreterResolvedJavaMethod method, EconomicMap<String, Object> saved) {
    }

    /**
     * Keeps a reconstructed concat link private until all linkage candidates validate.
     *
     * @param method method containing the invokedynamic instruction
     * @param bci bytecode offset whose loader-local cache operand will be patched
     * @param constant constant-pool entry that owns the call-site cache
     * @param link reconstructed invoker and appendix to publish
     * @param saved captured site state used to validate the candidate
     */
    private record PendingDynamic(InterpreterResolvedJavaMethod method, int bci, ResolvedInvokeDynamicConstant constant, SuccessfulCallSiteLink link, EconomicMap<String, Object> saved) {
        /// Publishes this validated candidate into the interpreter cache.
        void publish() {
            int extraCPI = constant.cacheCallSiteLink(method, bci, link);
            method.patchInvokeDynamicExtraCPI(bci, extraCPI);
        }
    }

    /**
     * Identifies ordinary call sites that share one interpreter link-cache entry.
     *
     * @param pool constant pool containing the method reference
     * @param cpi method-reference index
     * @param opcode invocation opcode, which distinguishes link-cache entries
     */
    private record InvokeCacheKey(InterpreterConstantPool pool, int cpi, int opcode) {
    }

    /**
     * Identifies field opcodes that share one constant-pool resolution state.
     *
     * @param pool constant pool containing the field reference
     * @param cpi field-reference index shared by reads and writes
     */
    private record FieldCacheKey(InterpreterConstantPool pool, int cpi) {
    }

    /**
     * Identifies constant and type instructions that share one cached constant.
     *
     * @param pool constant pool containing the constant
     * @param cpi constant index, independent of the instruction using it
     */
    private record ConstantCacheKey(InterpreterConstantPool pool, int cpi) {
    }

    /**
     * Retains one linked constant for resolution after all shared cache states validate.
     *
     * @param method method providing the accessing class and constant pool
     * @param bci representative instruction used to recheck the resolved state
     * @param cpi constant-pool index to resolve
     * @param saved captured state that the resolved constant must match
     */
    private record PendingConstant(InterpreterResolvedJavaMethod method, int bci, int cpi, EconomicMap<String, Object> saved) {
    }

    /**
     * Keeps field resolution separate from publication to the field-reference cache.
     *
     * @param method method providing the accessing class and constant pool
     * @param cpi field-reference index to publish
     * @param field candidate field that must still match resolution at publication
     */
    private record PendingField(InterpreterResolvedJavaMethod method, int cpi, InterpreterResolvedJavaField field) {
        /// Publishes this validated candidate into the interpreter cache.
        void publish() {
            if (!field.equals(method.getConstantPool().resolvedFieldAt(method.getDeclaringClass(), cpi))) {
                throw new IllegalArgumentException("Reconstructed field changed before publication: " + method + "#" + cpi);
            }
        }
    }

    /**
     * Retains an ordinary call link until all candidate linkage has been validated.
     *
     * @param method method containing the invocation and owning the constant pool
     * @param bci instruction supplying the opcode and method-reference index
     * @param link candidate seed, dispatch kind and appendix to cache
     */
    private record PendingInvoke(InterpreterResolvedJavaMethod method, int bci, LinkedInvoke link) {
        /// Publishes this validated candidate into the interpreter cache.
        void publish() {
            byte[] code = method.getCode();
            method.getConstantPool().cacheLinkedInvoke(BytecodeStream.uncheckedReadCPI2(code, bci), BytecodeStream.currentBC(code, bci), link);
        }
    }

    /**
     * Validates visible records, constructs private concat candidates, then binds generated records
     * through their recorded semantic roles. All ordinary and dynamic sites validate before publication.
     */
    private static Linkage prepareLinkage(EconomicMap<String, Object> data, ClassLoader loader, RistrettoMethod root, Set<TypeKey> profileTypes) throws ClassNotFoundException {
        if (!data.containsKey("ristrettoLinkage")) {
            throw new IllegalArgumentException("Missing Ristretto linkage; recapture profiles with this runtime");
        }
        var linkage = dict(data.get("ristrettoLinkage"), "ristrettoLinkage");
        if (!Integer.valueOf(LINKAGE_VERSION).equals(linkage.get("version"))) {
            throw new IllegalArgumentException("Incompatible Ristretto linkage; recapture profiles");
        }
        List<MethodLinkage> methods = new ArrayList<>();
        List<EconomicMap<String, Object>> generated = new ArrayList<>();
        Set<String> requiredClasses = new HashSet<>();
        for (TypeKey type : profileTypes) {
            requiredClasses.add(binaryName(type.type));
        }
        Set<String> identities = new HashSet<>();
        for (Object item : list(linkage.get("methods"), "linkage methods")) {
            var saved = dict(item, "linkage method");
            rejectUnsupported(saved);
            String signature = string(saved.get("method"), "linkage method");
            if (!identities.add(signature)) {
                throw new IllegalArgumentException("Duplicate linkage method: " + signature);
            }
            for (Object site : list(saved.get("sites"), "linkage sites")) {
                rejectUnsupported(dict(site, "linkage site"));
            }
            if (saved.containsKey("originMethod")) {
                generated.add(saved);
            } else {
                var found = resolveLinkageMethod(signature, loader, root);
                validateMethod(found, saved, Map.of(), requiredClasses);
                methods.add(new MethodLinkage(found, saved));
            }
        }
        Set<String> required = new HashSet<>();
        required.add(root.format(METHOD_FORMAT));
        for (Object item : list(data.get("profiles"), "profiles")) {
            required.add(string(dict(item, "profile").get("method"), "profile method"));
        }
        for (String signature : required) {
            var method = resolveLinkageMethod(signature, loader, root);
            if (method != null && method.getConstantPool() instanceof RuntimeInterpreterConstantPool && method.getCode() != null && !identities.contains(signature)) {
                throw new IllegalArgumentException("Missing captured linkage method: " + signature);
            }
        }
        // Only metadata-whitelisted JDK factories may execute here. Workload targets remain cold.
        List<PendingDynamic> dynamic = new ArrayList<>();
        Map<InterpreterResolvedJavaMethod, DynamicOrigin> origins = new HashMap<>();
        for (var method : methods) {
            for (Object item : list(method.saved.get("sites"), "linkage sites")) {
                var site = dict(item, "linkage site");
                if (Boolean.TRUE.equals(site.get("linked")) && Integer.valueOf(Bytecodes.INVOKEDYNAMIC).equals(site.get("opcode"))) {
                    int bci = (Integer) site.get("bci");
                    ConcatRecipe recipe = concatRecipe(method.method, bci);
                    if (recipe == null || !recipe.recipe().equals(site.get("recipe")) || !recipe.constants().equals(site.get("constants")) ||
                                    !"StringConcatFactory.makeConcatWithConstants".equals(site.get("bootstrap"))) {
                        throw new IllegalArgumentException("Unsupported or changed concat bootstrap recipe");
                    }
                    var pool = (RuntimeInterpreterConstantPool) method.method.getConstantPool();
                    int cpi = (Integer) site.get("cpi");
                    var constant = (ResolvedInvokeDynamicConstant) pool.resolvedAt(cpi, method.method.getDeclaringClass());
                    CallSiteLink candidate = constant.getCallSiteLink(method.method, bci);
                    if (candidate == null) {
                        candidate = constant.createCallSiteLink(pool, method.method.getDeclaringClass().getJavaClass(), method.method, bci);
                    }
                    if (!(candidate instanceof SuccessfulCallSiteLink link) || !(link.getUnboxedAppendix() instanceof MethodHandle handle)) {
                        throw new IllegalArgumentException("Concat bootstrap did not produce a supported link");
                    }
                    MethodHandleInterpreterUtils.extractVMEntry(handle);
                    MethodHandle target = boundTarget(link);
                    if (target != null) {
                        MethodHandleInterpreterUtils.extractVMEntry(target);
                    }
                    recordOrigins(origins, method.method, bci, link);
                    dynamic.add(new PendingDynamic(method.method, bci, constant, link, site));
                }
            }
        }
        for (var saved : generated) {
            String caller = string(saved.get("originMethod"), "origin method");
            if (!(saved.get("originBCI") instanceof Integer bci)) {
                throw new IllegalArgumentException("Malformed generated entry origin");
            }
            OriginRole role = OriginRole.valueOf(string(saved.get("originRole"), "origin role"));
            InterpreterResolvedJavaMethod found = null;
            for (var candidate : dynamic) {
                if (caller.equals(candidate.method.format(METHOD_FORMAT)) && candidate.bci == bci) {
                    found = originMethod(candidate.link, role);
                    break;
                }
            }
            if (found == null || !saved.get("method").equals(methodIdentity(found, origins)) ||
                            !new DynamicOrigin(caller, bci, role).equals(origins.get(found))) {
                throw new IllegalArgumentException("Generated entry origin differs: " + saved.get("method"));
            }
            validateMethod(found, saved, origins, requiredClasses);
            methods.add(new MethodLinkage(found, saved));
        }
        for (var entry : origins.entrySet()) {
            if (!identities.contains(entry.getValue().identity(entry.getKey()))) {
                throw new IllegalArgumentException("Missing captured generated entry: " + entry.getValue());
            }
        }
        Map<Class<?>, Boolean> classes = new HashMap<>();
        Set<String> recordedClasses = new HashSet<>();
        var classEntries = dict(linkage.get("classes"), "linkage classes").getEntries();
        while (classEntries.advance()) {
            if (!(classEntries.getValue() instanceof Boolean linked)) {
                throw new IllegalArgumentException("Malformed class-link state: " + classEntries.getKey());
            }
            recordedClasses.add(classEntries.getKey());
            Class<?> clazz = ClassRegistries.forName(classEntries.getKey(), loader);
            if (classes.putIfAbsent(clazz, linked) != null) {
                throw new IllegalArgumentException("Duplicate captured class-link state: " + clazz.getName());
            }
        }
        if (!recordedClasses.containsAll(requiredClasses)) {
            requiredClasses.removeAll(recordedClasses);
            throw new IllegalArgumentException("Missing captured class-link state: " + requiredClasses);
        }
        // Class linking verifies and prepares metadata; it does not run class initializers.
        for (var entry : classes.entrySet()) {
            if (entry.getValue()) {
                DynamicHubUtils.getInterpreterType(DynamicHub.fromClass(entry.getKey())).link();
            }
        }
        List<PendingInvoke> ordinary = new ArrayList<>();
        List<PendingField> fields = new ArrayList<>();
        Map<ConstantCacheKey, PendingConstant> constants = new LinkedHashMap<>();
        Map<ConstantCacheKey, EconomicMap<String, Object>> constantStates = new HashMap<>();
        Map<InvokeCacheKey, Boolean> ordinaryStates = new HashMap<>();
        Map<FieldCacheKey, Boolean> fieldStates = new HashMap<>();
        for (var method : methods) {
            for (Object item : list(method.saved.get("sites"), "linkage sites")) {
                var site = dict(item, "linkage site");
                int bci = (Integer) site.get("bci");
                int opcode = (Integer) site.get("opcode");
                boolean linked = Boolean.TRUE.equals(site.get("linked"));
                if (isConstantSite(opcode)) {
                    int cpi = (Integer) site.get("cpi");
                    var key = new ConstantCacheKey(method.method.getConstantPool(), cpi);
                    var state = EconomicMap.create(site);
                    state.removeKey("bci");
                    state.removeKey("opcode");
                    var previous = constantStates.putIfAbsent(key, state);
                    if (previous != null) {
                        if (!Objects.equals(previous.get("linked"), linked)) {
                            throw new IllegalArgumentException("Conflicting shared ldc state: " + method.method + "@" + bci);
                        }
                        verifySite(method.method, previous, state);
                    }
                    if (linked) {
                        constants.putIfAbsent(key, new PendingConstant(method.method, bci, cpi, site));
                    } else {
                        verifySite(method.method, site, constantSiteState(method.method, bci, null));
                    }
                    continue;
                }
                if (Bytecodes.isFieldAccess(opcode)) {
                    var key = new FieldCacheKey(method.method.getConstantPool(), (Integer) site.get("cpi"));
                    Boolean previous = fieldStates.putIfAbsent(key, linked);
                    if (previous != null && previous != linked) {
                        throw new IllegalArgumentException("Conflicting shared field state: " + method.method + "@" + bci);
                    }
                    if (linked) {
                        var candidate = prepareField(method.method, site);
                        verifySite(method.method, site, fieldSiteState(method.method, bci, null, candidate.field));
                        fields.add(candidate);
                    } else {
                        verifySite(method.method, site, siteState(method.method, bci, null, origins));
                    }
                    continue;
                }
                if (opcode != Bytecodes.INVOKEDYNAMIC) {
                    var key = new InvokeCacheKey(method.method.getConstantPool(), (Integer) site.get("cpi"), opcode);
                    Boolean previous = ordinaryStates.putIfAbsent(key, linked);
                    if (previous != null && previous != linked) {
                        throw new IllegalArgumentException("Conflicting shared call-link state: " + method.method + "@" + bci);
                    }
                }
                if (linked) {
                    if (opcode != Bytecodes.INVOKEDYNAMIC) {
                        var candidate = prepareInvoke(method.method, site);
                        verifySite(method.method, site, siteState(method.method, bci, null, origins, candidate.link, null));
                        ordinary.add(candidate);
                    }
                } else {
                    verifySite(method.method, site, siteState(method.method, bci, null, origins));
                }
            }
        }
        for (var candidate : dynamic) {
            verifySite(candidate.method, candidate.saved, siteState(candidate.method, candidate.bci, null, origins, null, candidate.link));
        }
        // Resolve supported constant metadata only after all identities and shared cache states validate.
        // Like class preparation, this may cache metadata; it does not invoke workload targets.
        for (var candidate : constants.values()) {
            var pool = candidate.method.getConstantPool();
            if (pool.tagAt(candidate.cpi) == Tag.CLASS) {
                // lookupConstant only reads the cached CLASS entry, even when resolve is true.
                pool.resolvedTypeAt(candidate.method.getDeclaringClass(), candidate.cpi);
            } else {
                pool.lookupConstant(candidate.cpi, true);
            }
            verifySite(candidate.method, candidate.saved, constantSiteState(candidate.method, candidate.bci, null));
        }
        // Later preparation can resolve metadata used by an earlier cold site.
        // Reject that change before publishing any field or call links.
        for (var method : methods) {
            for (Object item : list(method.saved.get("sites"), "linkage sites")) {
                var site = dict(item, "linkage site");
                if (Boolean.FALSE.equals(site.get("linked"))) {
                    verifySite(method.method, site, siteState(method.method, (Integer) site.get("bci"), null, origins));
                }
            }
        }
        verifyClasses(classes);
        // Field resolution and call-site links are published only after every candidate validates.
        fields.forEach(PendingField::publish);
        ordinary.forEach(PendingInvoke::publish);
        dynamic.forEach(PendingDynamic::publish);
        Linkage prepared = new Linkage(List.copyOf(methods), Map.copyOf(classes), Map.copyOf(origins));
        verifyLinkage(prepared);
        return prepared;
    }

    /// Checks the normalized bytecodes and ordered site inventory before any call-site cache mutation.
    private static void validateMethod(InterpreterResolvedJavaMethod found, EconomicMap<String, Object> saved,
                    Map<InterpreterResolvedJavaMethod, DynamicOrigin> origins, Set<String> requiredClasses) {
        String signature = string(saved.get("method"), "linkage method");
        if (found == null || !(found.getConstantPool() instanceof RuntimeInterpreterConstantPool) || found.getCode() == null) {
            throw new IllegalArgumentException("Missing runtime linkage method: " + signature);
        }
        if (!found.getDeclaringClass().isHidden()) {
            requiredClasses.add(found.getDeclaringClass().getJavaClass().getName());
        }
        var current = captureLinkage(found, null, origins);
        if (!Objects.equals(saved.get("code"), current.get("code"))) {
            throw new IllegalArgumentException("Linkage bytecodes differ: " + signature);
        }
        List<?> sites = list(saved.get("sites"), "linkage sites");
        List<?> actualSites = list(current.get("sites"), "current linkage sites");
        if (sites.size() != actualSites.size()) {
            throw new IllegalArgumentException("Linkage site inventory differs: " + signature);
        }
        for (int index = 0; index < sites.size(); index++) {
            var site = dict(sites.get(index), "linkage site");
            var actual = dict(actualSites.get(index), "current linkage site");
            if (isConstantSite((Integer) actual.get("opcode"))) {
                rejectUnsupported(actual);
            }
            for (String key : List.of("bci", "opcode", "cpi", "name", "descriptor", "symbolicHolder", "constantKind", "referenceKind")) {
                if (!Objects.equals(site.get(key), actual.get(key))) {
                    throw new IllegalArgumentException("Linkage site identity differs: " + signature);
                }
            }
            if (!(site.get("linked") instanceof Boolean linked)) {
                throw new IllegalArgumentException("Malformed linkage state: " + signature);
            }
            if (linked) {
                if (isConstantSite((Integer) site.get("opcode"))) {
                    for (Object type : list(site.get("constantClasses"), "ldc classes")) {
                        requiredClasses.add(string(type, "ldc class"));
                    }
                    continue;
                }
                if (site.containsKey("seedClass")) {
                    requiredClasses.add(string(site.get("seedClass"), "seed class"));
                }
                if (!Integer.valueOf(Bytecodes.INVOKEDYNAMIC).equals(site.get("opcode")) &&
                                (!Bytecodes.isFieldAccess((Integer) site.get("opcode")) || site.containsKey("holderClass"))) {
                    requiredClasses.add(string(site.get("holderClass"), "holder class"));
                }
            }
        }
    }

    /// Resolves a field candidate without publishing it into the field-reference cache.
    private static PendingField prepareField(InterpreterResolvedJavaMethod method, EconomicMap<String, Object> saved) {
        int cpi = (Integer) saved.get("cpi");
        var pool = method.getConstantPool();
        var accessingClass = method.getDeclaringClass();
        var symbolicHolder = pool.resolvedTypeAt(accessingClass, pool.memberClassIndex(cpi));
        var field = CremaLinkResolver.resolveFieldSymbolOrThrow(CremaRuntimeAccess.getInstance(), accessingClass, pool.fieldName(cpi), pool.fieldType(cpi), symbolicHolder, true, true);
        return new PendingField(method, cpi, field);
    }

    /// Reuses ordinary resolution, including invoke-generic adapters, without publishing the result.
    private static PendingInvoke prepareInvoke(InterpreterResolvedJavaMethod method, EconomicMap<String, Object> saved) {
        int bci = (Integer) saved.get("bci");
        int opcode = (Integer) saved.get("opcode");
        int cpi = (Integer) saved.get("cpi");
        var pool = method.getConstantPool();
        var accessingClass = method.getDeclaringClass();
        var symbolicMethod = pool.resolvedMethodAt(accessingClass, cpi);
        var symbolicHolder = pool.resolvedTypeAt(accessingClass, pool.memberClassIndex(cpi));
        var call = CremaLinkResolver.resolveCallSiteOrThrow(CremaRuntimeAccess.getInstance(), accessingClass, symbolicMethod, CallSiteType.fromOpCode(opcode), symbolicHolder);
        var seed = call.getResolvedMethod();
        var kind = call.getCallKind();
        Object appendix = null;
        if (seed instanceof InterpreterResolvedInvokeGenericJavaMethod generic) {
            seed = generic.getInvoker();
            appendix = generic.getAppendix();
            kind = CallKind.DIRECT;
        }
        boolean requiresReceiverCheck = pool.tagAt(cpi) == Tag.INTERFACE_METHOD_REF ||
                        (opcode == Bytecodes.INVOKESPECIAL && accessingClass.isInterface() && !symbolicMethod.isConstructor());
        var link = new LinkedInvoke(symbolicHolder, seed, kind, appendix, requiresReceiverCheck, opcode);
        return new PendingInvoke(method, bci, link);
    }

    private static InterpreterResolvedJavaMethod resolveLinkageMethod(String signature, ClassLoader loader, RistrettoMethod root) throws ClassNotFoundException {
        if (signature.equals(root.format(METHOD_FORMAT))) {
            return root.getInterpreterMethod();
        }
        int separator = signature.lastIndexOf('.', signature.indexOf('('));
        if (separator < 1) {
            throw new IllegalArgumentException("Invalid linkage method: " + signature);
        }
        Class<?> owner = ClassRegistries.forName(signature.substring(0, separator), loader);
        var type = (InterpreterResolvedJavaType) DynamicHubUtils.getInterpreterType(DynamicHub.fromClass(owner));
        InterpreterResolvedJavaMethod found = null;
        List<InterpreterResolvedJavaMethod> candidates = new ArrayList<>();
        candidates.addAll(List.of(type.getDeclaredMethods(false)));
        candidates.addAll(List.of(type.getDeclaredConstructors(false)));
        for (var candidate : candidates) {
            if (signature.equals(candidate.format(METHOD_FORMAT))) {
                found = candidate;
                break;
            }
        }
        return found;
    }

    /** Recheck after all roots have been prepared, because their constant pools may be shared. */
    public void verifyLinkage() {
        verifyLinkage(linkage);
    }

    private static void verifyLinkage(Linkage linkage) {
        verifyClasses(linkage.classes);
        for (var method : linkage.methods) {
            List<?> saved = list(method.saved.get("sites"), "linkage sites");
            for (Object item : saved) {
                var expected = dict(item, "linkage site");
                int bci = (Integer) expected.get("bci");
                verifySite(method.method, expected, siteState(method.method, bci, null, linkage.origins));
            }
        }
    }

    private static void verifyClasses(Map<Class<?>, Boolean> classes) {
        for (var entry : classes.entrySet()) {
            if (DynamicHub.fromClass(entry.getKey()).isLinked() != entry.getValue()) {
                throw new IllegalArgumentException("Reconstructed class-link state differs: " + entry.getKey().getName());
            }
        }
    }

    private static void verifySite(InterpreterResolvedJavaMethod method, EconomicMap<String, Object> expected, EconomicMap<String, Object> actual) {
        rejectUnsupported(actual);
        if (expected.size() != actual.size()) {
            throw new IllegalArgumentException("Reconstructed linkage differs: " + method + "@" + expected.get("bci"));
        }
        var entries = expected.getEntries();
        while (entries.advance()) {
            if (!actual.containsKey(entries.getKey()) || !Objects.equals(entries.getValue(), actual.get(entries.getKey()))) {
                throw new IllegalArgumentException("Reconstructed linkage differs: " + method + "@" + expected.get("bci") + " " + entries.getKey());
            }
        }
    }

    private static void rejectUnsupported(EconomicMap<String, Object> record) {
        if (record.containsKey("unsupported")) {
            throw new IllegalArgumentException("Unsupported replay linkage: " + record.get("unsupported"));
        }
    }

    private static void probability(Object value) {
        if (!(value instanceof Double probability) || !Double.isFinite(probability) || probability < 0 || probability > 1) {
            throw new IllegalArgumentException("Malformed type profile probability: " + value);
        }
    }
}
