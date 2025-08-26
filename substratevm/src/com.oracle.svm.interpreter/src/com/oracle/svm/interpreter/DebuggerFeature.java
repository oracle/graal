/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.interpreter;

import static com.oracle.svm.hosted.pltgot.GOTEntryAllocator.GOT_NO_ENTRY;
import static com.oracle.svm.interpreter.metadata.Bytecodes.INVOKEDYNAMIC;
import static com.oracle.svm.interpreter.metadata.Bytecodes.INVOKEINTERFACE;
import static com.oracle.svm.interpreter.metadata.Bytecodes.INVOKESPECIAL;
import static com.oracle.svm.interpreter.metadata.Bytecodes.INVOKESTATIC;
import static com.oracle.svm.interpreter.metadata.Bytecodes.INVOKEVIRTUAL;
import static com.oracle.svm.interpreter.metadata.InterpreterResolvedJavaMethod.EST_NO_ENTRY;
import static com.oracle.svm.interpreter.metadata.InterpreterResolvedJavaMethod.VTBL_NO_ENTRY;
import static com.oracle.svm.interpreter.metadata.InterpreterResolvedJavaMethod.VTBL_ONE_IMPL;
import static com.oracle.svm.interpreter.metadata.InterpreterUniverseImpl.toHexString;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.graalvm.collections.EconomicMap;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.word.Pointer;

import com.oracle.graal.pointsto.constraints.UnsupportedFeatureException;
import com.oracle.graal.pointsto.heap.ImageHeapConstant;
import com.oracle.graal.pointsto.infrastructure.OriginalClassProvider;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.svm.core.BuildArtifacts;
import com.oracle.svm.core.FunctionPointerHolder;
import com.oracle.svm.core.ParsingReason;
import com.oracle.svm.core.RuntimeAssertionsSupport;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.meta.MethodPointer;
import com.oracle.svm.core.option.HostedOptionValues;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.graal.hosted.DeoptimizationFeature;
import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.hosted.NativeImageGenerator;
import com.oracle.svm.hosted.classloading.SymbolsFeature;
import com.oracle.svm.hosted.code.CompileQueue;
import com.oracle.svm.hosted.code.SubstrateCompilationDirectives;
import com.oracle.svm.hosted.image.NativeImageHeap;
import com.oracle.svm.hosted.meta.HostedField;
import com.oracle.svm.hosted.meta.HostedMetaAccess;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.meta.HostedType;
import com.oracle.svm.hosted.meta.HostedUniverse;
import com.oracle.svm.hosted.pltgot.GOTEntryAllocator;
import com.oracle.svm.hosted.pltgot.HostedPLTGOTConfiguration;
import com.oracle.svm.hosted.pltgot.IdentityMethodAddressResolverFeature;
import com.oracle.svm.hosted.pltgot.PLTGOTOptions;
import com.oracle.svm.hosted.snippets.SubstrateGraphBuilderPlugins;
import com.oracle.svm.hosted.substitute.SubstitutionMethod;
import com.oracle.svm.interpreter.classfile.ClassFile;
import com.oracle.svm.interpreter.metadata.BytecodeStream;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedJavaField;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedJavaMethod;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedJavaType;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedObjectType;
import com.oracle.svm.interpreter.metadata.InterpreterUniverseImpl;
import com.oracle.svm.interpreter.metadata.MetadataUtil;
import com.oracle.svm.interpreter.metadata.ReferenceConstant;
import com.oracle.svm.interpreter.metadata.serialization.SerializationContext;
import com.oracle.svm.interpreter.metadata.serialization.Serializers;
import com.oracle.svm.util.ReflectionUtil;

import jdk.graal.compiler.api.replacements.SnippetReflectionProvider;
import jdk.graal.compiler.core.common.SuppressFBWarnings;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugin;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugins;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.util.Providers;
import jdk.vm.ci.meta.ConstantPool;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaMethod;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.PrimitiveConstant;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.UnresolvedJavaMethod;

/**
 * Also known as "YellowBird".
 *
 * In this mode the interpreter is used as an alternative execution engine to already AOT compiled
 * methods in an image. This is needed to enable bytecode level debugging for JDWP.
 *
 * This also implies that all methods that are AOT compiled, need their bytecodes collected at image
 * build-time.
 */
@Platforms(Platform.HOSTED_ONLY.class)
@AutomaticallyRegisteredFeature
public class DebuggerFeature implements InternalFeature {
    private Method enterInterpreterMethod;
    private InterpreterStubTable enterStubTable = null;
    private final List<Class<?>> classesUsedByInterpreter = new ArrayList<>();
    private Set<AnalysisMethod> methodsProcessedDuringAnalysis;
    private InvocationPlugins invocationPlugins;
    private static final String SYNTHETIC_ASSERTIONS_DISABLED_FIELD_NAME = "$assertionsDisabled";

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return InterpreterOptions.DebuggerWithInterpreter.getValue();
    }

    @Override
    public List<Class<? extends Feature>> getRequiredFeatures() {
        return Arrays.asList(
                        DeoptimizationFeature.class,
                        InterpreterFeature.class,
                        IdentityMethodAddressResolverFeature.class,
                        SymbolsFeature.class);
    }

    private static Class<?> getArgumentClass(GraphBuilderContext b, ResolvedJavaMethod targetMethod, int parameterIndex, ValueNode arg) {
        SubstrateGraphBuilderPlugins.checkParameterUsage(arg.isConstant(), b, targetMethod, parameterIndex, "parameter is not a compile time constant");
        return OriginalClassProvider.getJavaClass(b.getConstantReflection().asJavaType(arg.asJavaConstant()));
    }

    @Override
    public void registerInvocationPlugins(Providers providers, GraphBuilderConfiguration.Plugins plugins, ParsingReason reason) {
        invocationPlugins = plugins.getInvocationPlugins();
        InvocationPlugins.Registration r = new InvocationPlugins.Registration(invocationPlugins, InterpreterDirectives.class);

        r.register(new InvocationPlugin.RequiredInvocationPlugin("markKlass", Class.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode arg1) {
                Class<?> targetKlass = getArgumentClass(b, targetMethod, 1, arg1);
                InterpreterUtil.log("[invocation plugin] Adding %s", targetKlass);
                classesUsedByInterpreter.add(targetKlass);

                /* no-op in compiled code */
                return true;
            }
        });
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        enterStubTable = new InterpreterStubTable();

        VMError.guarantee(PLTGOTOptions.EnablePLTGOT.getValue());
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        FeatureImpl.BeforeAnalysisAccessImpl accessImpl = (FeatureImpl.BeforeAnalysisAccessImpl) access;

        try {
            enterInterpreterMethod = InterpreterStubSection.class.getMethod("enterMethodInterpreterStub", int.class, Pointer.class);
            accessImpl.registerAsRoot(enterInterpreterMethod, true, "stub for interpreter");

            // Holds references that must be kept alive in the image heap.
            access.registerAsAccessed(DebuggerSupport.class.getDeclaredField("referencesInImage"));
            access.registerAsAccessed(DebuggerSupport.class.getDeclaredField("methodPointersInImage"));

            accessImpl.registerAsRoot(System.class.getDeclaredMethod("arraycopy", Object.class, int.class, Object.class, int.class, int.class), true,
                            "Allow interpreting methods that call System.arraycopy");
        } catch (NoSuchMethodException | NoSuchFieldException e) {
            throw VMError.shouldNotReachHere(e);
        }

        registerStringConcatenation(accessImpl);

        // GR-53734: Known issues around reachability
        try {
            // JDK code introduced a new optional intrinsic:
            // https://github.com/openjdk/jdk22u/commit/a4e9168bab1c2872ce2dbc7971a45c259270271f
            // consider DualPivotQuicksort.java:268, int.class is not needed if the sort helper
            // is inlined, therefore it's not needed. Still needed for interpreter execution.
            access.registerAsAccessed(Integer.class.getField("TYPE"));
        } catch (NoSuchFieldException e) {
            throw VMError.shouldNotReachHere(e);
        }

        methodsProcessedDuringAnalysis = new HashSet<>();

        ImageSingletons.add(DebuggerSupport.class, new DebuggerSupport());
    }

    private static void registerStringConcatenation(FeatureImpl.BeforeAnalysisAccessImpl accessImpl) {
        /*
         * String concatenation a0 + a1 + ... + an is compiled by the Eclipse Java Compiler (ecj)
         * to:
         *
         * new StringBuilder(a0).append(a1).append(a2) ... append(an).toString()
         *
         * javac emits INVOKEDYNAMIC-based String concatenation instead.
         *
         * String concatenation is heavily optimized by the compiler to the point that most
         * StringBuilder methods/constructor are not included if they are not explicitly used
         * outside String concatenation.
         *
         * These registrations enable the interpreter to "interpret" StringBuilder-based String
         * concatenation optimized away by the compiler.
         */
        try {
            List<Method> appendMethods = Arrays.stream(StringBuilder.class.getDeclaredMethods())
                            .filter(m -> "append".equals(m.getName()))
                            .collect(Collectors.toList());
            for (Method m : appendMethods) {
                accessImpl.registerAsRoot(m, false, "string concat in interpreter");
            }
            for (Constructor<?> c : StringBuilder.class.getDeclaredConstructors()) {
                accessImpl.registerAsRoot(c, true, "string concat in interpreter");
            }
            accessImpl.registerAsRoot(StringBuilder.class.getConstructor(), true, "string concat in interpreter");
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    static boolean isReachable(AnalysisMethod m) {
        return m.isReachable() || m.isDirectRootMethod() || m.isVirtualRootMethod();
    }

    private static boolean isInvokeSpecial(AnalysisMethod method) {
        boolean invokeSpecial = method.isConstructor();
        if (!invokeSpecial) {
            invokeSpecial = Modifier.isPrivate(method.getModifiers());
        }
        return invokeSpecial;
    }

    @Override
    public void duringAnalysis(DuringAnalysisAccess access) {
        FeatureImpl.DuringAnalysisAccessImpl accessImpl = (FeatureImpl.DuringAnalysisAccessImpl) access;
        MetaAccessProvider metaAccessProvider = accessImpl.getMetaAccess();

        boolean addedIndyHelper = false;
        for (AnalysisMethod m : accessImpl.getUniverse().getMethods()) {
            if (isReachable(m)) {
                continue;
            }
            if (m.getName().startsWith("invoke") && m.getDeclaringClass().getName().equals("Ljava/lang/invoke/MethodHandle;")) {
                accessImpl.registerAsRoot(m, true, "method handle for interpreter");
                SubstrateCompilationDirectives.singleton().registerForcedCompilation(m);
                InterpreterUtil.log("[during analysis] Force entry point for %s and mark as reachable", m);
                addedIndyHelper = true;
            }
        }
        if (addedIndyHelper) {
            access.requireAnalysisIteration();
            return;
        }

        if (!classesUsedByInterpreter.isEmpty()) {
            access.requireAnalysisIteration();
            for (Class<?> k : classesUsedByInterpreter) {
                accessImpl.registerAsUsed(k);
                Arrays.stream(k.getDeclaredMethods()).filter(m -> m.getName().startsWith("test")).forEach(m -> {
                    AnalysisMethod aMethod = accessImpl.getMetaAccess().lookupJavaMethod(m);
                    VMError.guarantee(!aMethod.isConstructor());
                    accessImpl.registerAsRoot(aMethod, aMethod.isConstructor(), "reached due to interpreter directive");
                    InterpreterUtil.log("[during analysis] Adding method %s", m);
                });
            }
            classesUsedByInterpreter.clear();
            return;
        }

        DebuggerSupport supportImpl = DebuggerSupport.singleton();
        SnippetReflectionProvider snippetReflection = accessImpl.getUniverse().getSnippetReflection();

        for (AnalysisMethod method : accessImpl.getUniverse().getMethods()) {
            /*
             * Hack: Add frame info for every reachable method, so we have local infos on compiled
             * frames. A proper solution is to externalize this information in our metadata file and
             * retrieve it at run-time on demand.
             */
            SubstrateCompilationDirectives.singleton().registerFrameInformationRequired(method);

            if (method.isReachable() && !methodsProcessedDuringAnalysis.contains(method)) {
                methodsProcessedDuringAnalysis.add(method);
                if (method.wrapped instanceof SubstitutionMethod subMethod && subMethod.isUserSubstitution()) {
                    if (subMethod.getOriginal().isNative()) {
                        accessImpl.registerAsRoot(method, isInvokeSpecial(method), "compiled entry point of substitution needed for interpreter");
                        continue;
                    }
                }
                byte[] code = method.getCode();
                if (code == null || !InterpreterFeature.callableByInterpreter(method, metaAccessProvider)) {
                    continue;
                }
                AnalysisType declaringClass = method.getDeclaringClass();
                if (!declaringClass.isReachable() && !declaringClass.getName().toLowerCase(Locale.ROOT).contains("hosted")) {
                    /*
                     * It rarely happens that a method is reachable but its declaring class is not.
                     * This can't be represented in the interpreter universe currently, thus we
                     * force the declaring class to be reached.
                     *
                     * Example: ImageSingletons.lookup(Ljava/lang/Class);
                     */
                    InterpreterUtil.log("[during analysis] declaring class %s of method %s is not reachable, force it as root", declaringClass, method);
                    accessImpl.registerAsUsed(declaringClass, "interpreter needs dynamic hub at runtime for this class");
                    access.requireAnalysisIteration();
                }
                InvocationPlugin invocationPlugin = accessImpl.getBigBang().getProviders(method).getGraphBuilderPlugins().getInvocationPlugins().lookupInvocation(method,
                                accessImpl.getBigBang().getOptions());
                if (invocationPlugin != null) {
                    // There's an invocation plugin for this method.
                    continue;
                }
                boolean analyzeCallees = true;
                for (int bci = 0; bci < BytecodeStream.endBCI(code); bci = BytecodeStream.nextBCI(code, bci)) {
                    int bytecode = BytecodeStream.currentBC(code, bci);
                    if (bytecode == INVOKEDYNAMIC) {
                        int targetMethodCPI = BytecodeStream.readCPI4(code, bci);
                        AnalysisMethod analysisMethod = getAnalysisMethodAt(method.getConstantPool(), targetMethodCPI, bytecode);
                        if (analysisMethod != null) {
                            accessImpl.registerAsRoot(analysisMethod, false, "forced for indy support in interpreter");
                            InterpreterUtil.log("[during analysis] force %s mark as reachable", analysisMethod);
                        }

                        JavaConstant appendixConstant = method.getConstantPool().lookupAppendix(targetMethodCPI, bytecode);
                        if (appendixConstant instanceof ImageHeapConstant imageHeapConstant) {
                            supportImpl.ensureConstantIsInImageHeap(snippetReflection, imageHeapConstant);
                        }
                    }
                    if (analyzeCallees) {
                        switch (bytecode) {
                            /* GR-53540: Handle invokedyanmic too */
                            case INVOKESPECIAL, INVOKESTATIC, INVOKEVIRTUAL, INVOKEINTERFACE -> {
                                int originalCPI = BytecodeStream.readCPI(code, bci);

                                try {
                                    AnalysisMethod calleeMethod = getAnalysisMethodAt(method.getConstantPool(), originalCPI, bytecode);
                                    if (calleeMethod == null || !calleeMethod.isReachable()) {
                                        continue;
                                    }

                                    if (!InterpreterFeature.callableByInterpreter(calleeMethod, metaAccessProvider)) {
                                        InterpreterUtil.log("[process invokes] cannot execute %s due to call-site (%s) @ bci=%s is not callable by interpreter%n", method.getName(), bci, calleeMethod);
                                        if (method.getAnalyzedGraph() == null) {
                                            accessImpl.registerAsRoot(method, isInvokeSpecial(method), "method handle for interpreter");
                                            accessImpl.registerAsUsed(method.getDeclaringClass().getJavaClass());
                                            access.requireAnalysisIteration();
                                        }
                                        if (method.reachableInCurrentLayer()) {
                                            SubstrateCompilationDirectives.singleton().registerForcedCompilation(method);
                                        }
                                        analyzeCallees = false;
                                        break;
                                    }
                                } catch (UnsupportedFeatureException | UserError.UserException e) {
                                    InterpreterUtil.log("[process invokes] lookup in method %s failed due to:", method);
                                    InterpreterUtil.log(e);
                                    // ignore, call will fail at run-time if reached
                                }
                            }
                        }
                    }
                }
            }
        }
        supportImpl.trimForcedReferencesInImageHeap();
    }

    private static AnalysisMethod getAnalysisMethodAt(ConstantPool constantPool, int targetMethodCPI, int bytecode) {
        JavaMethod targetMethod = constantPool.lookupMethod(targetMethodCPI, bytecode);
        /*
         * SVM optimizes away javac's INVOKDYNAMIC-based String concatenation e.g.
         * MH.makeConcatWithConstants(...) . The CP method entry remains unresolved.
         *
         * Only reachable call sites should have its method and appendix included in the image, for
         * now, ALL INVOKEDYNAMIC call sites of reachable methods are included.
         */
        if (targetMethod instanceof UnresolvedJavaMethod) {
            constantPool.loadReferencedType(targetMethodCPI, bytecode);
            targetMethod = constantPool.lookupMethod(targetMethodCPI, bytecode);
        }
        if (targetMethod instanceof AnalysisMethod analysisMethod) {
            return analysisMethod;
        } else {
            return null;
        }
    }

    @Override
    public void afterAnalysis(AfterAnalysisAccess access) {
        VMError.guarantee(InterpreterToVM.wordJavaKind() == JavaKind.Long ||
                        InterpreterToVM.wordJavaKind() == JavaKind.Int);
    }

    @Override
    public void beforeCompilation(BeforeCompilationAccess access) {
        FeatureImpl.BeforeCompilationAccessImpl accessImpl = (FeatureImpl.BeforeCompilationAccessImpl) access;
        HostedUniverse hUniverse = accessImpl.getUniverse();
        HostedMetaAccess hMetaAccess = accessImpl.getMetaAccess();
        MetaAccessProvider aMetaAccess = hMetaAccess.getWrapped();
        BuildTimeInterpreterUniverse iUniverse = BuildTimeInterpreterUniverse.singleton();

        for (HostedType hType : hUniverse.getTypes()) {
            AnalysisType aType = hType.getWrapped();
            if (aType.isReachable()) {
                iUniverse.getOrCreateType(aType);
                for (ResolvedJavaField staticField : aType.getStaticFields()) {
                    if (staticField instanceof AnalysisField analysisStaticField && !analysisStaticField.isWritten()) {
                        /*
                         * Assertions are implemented by generating a boolean $assertionsDisabled
                         * static field, but native-image substitutes the field reads by a constant,
                         * making the field unreachable sometimes. The interpreter must artificially
                         * preserve the metadata without making it reachable to the analysis. In
                         * some cases, $assertionsDisabled is written in not-yet-executed static
                         * initializers, it can't be made read-only always.
                         */
                        if (staticField.isStatic() && staticField.isSynthetic() && staticField.getName().startsWith(SYNTHETIC_ASSERTIONS_DISABLED_FIELD_NAME)) {
                            Class<?> declaringClass = aType.getJavaClass();
                            boolean value = !RuntimeAssertionsSupport.singleton().desiredAssertionStatus(declaringClass);
                            InterpreterResolvedJavaField field = iUniverse.getOrCreateField(staticField);
                            JavaConstant javaConstant = iUniverse.constant(JavaConstant.forBoolean(value));
                            BuildTimeInterpreterUniverse.setUnmaterializedConstantValue(field, javaConstant);
                            field.markAsArtificiallyReachable();
                        }
                    }
                }
            }
        }

        OptionValues invocationLookupOptions = new OptionValues(EconomicMap.create());
        for (HostedMethod hMethod : hUniverse.getMethods()) {
            AnalysisMethod aMethod = hMethod.getWrapped();
            if (isReachable(aMethod)) {
                boolean needsMethodBody = InterpreterFeature.executableByInterpreter(aMethod) && InterpreterFeature.callableByInterpreter(hMethod, hMetaAccess);
                // Test if the methods needs to be compiled for execution in the interpreter:
                if (aMethod.getAnalyzedGraph() != null && //
                                (aMethod.wrapped instanceof SubstitutionMethod subMethod && subMethod.isUserSubstitution() ||
                                                invocationPlugins.lookupInvocation(aMethod, invocationLookupOptions) != null)) {
                    // The method is substituted, or an invocation plugin is registered
                    SubstrateCompilationDirectives.singleton().registerForcedCompilation(hMethod);
                    needsMethodBody = false;
                }

                if (needsMethodBody) {
                    BuildTimeInterpreterUniverse.singleton().getOrCreateMethodWithMethodBody(aMethod, aMetaAccess);
                } else {
                    BuildTimeInterpreterUniverse.singleton().getOrCreateMethod(aMethod);
                }
            }
        }

        for (HostedField hField : accessImpl.getUniverse().getFields()) {
            AnalysisField aField = hField.getWrapped();
            if (aField.isReachable()) {
                BuildTimeInterpreterUniverse.singleton().getOrCreateField(aField);
            }
        }

        iUniverse.purgeUnreachable(hMetaAccess);

        Field vtableHolderField = ReflectionUtil.lookupField(InterpreterResolvedObjectType.class, "vtableHolder");
        for (HostedType hostedType : hUniverse.getTypes()) {
            iUniverse.mirrorSVMVTable(hostedType, objectType -> accessImpl.getHeapScanner().rescanField(objectType, vtableHolderField));
        }

        // Allow methods that call System.arraycopy to be interpreted.
        try {
            HostedMethod arraycopy = hMetaAccess.lookupJavaMethod(
                            System.class.getDeclaredMethod("arraycopy", Object.class, int.class, Object.class, int.class, int.class));
            SubstrateCompilationDirectives.singleton().registerForcedCompilation(arraycopy);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void afterCompilation(AfterCompilationAccess access) {
        FeatureImpl.AfterCompilationAccessImpl accessImpl = (FeatureImpl.AfterCompilationAccessImpl) access;

        BuildTimeInterpreterUniverse.singleton().createConstantPools(accessImpl.getUniverse());

        int estOffset = 0;
        for (InterpreterResolvedJavaMethod interpreterMethod : BuildTimeInterpreterUniverse.singleton().getMethods()) {
            HostedMethod hostedMethod = accessImpl.getUniverse().optionalLookup(interpreterMethod.getOriginalMethod());

            CompileQueue.CompileTask compileTask = accessImpl.getCompilations().get(hostedMethod);
            ResolvedJavaMethod[] inlinedMethods = compileTask == null ? null : compileTask.result.getMethods();

            if (inlinedMethods == null) {
                InterpreterUtil.log("[inlinedeps] Method %s doesn't have any inlinees", hostedMethod);
            } else if (interpreterMethod.isInterpreterExecutable()) {
                InterpreterUtil.log("[inlinedeps] Inlined methods for %s: %s", hostedMethod, inlinedMethods.length);

                // GR-55054: check if all inlined methods are reachable and included.
                // There are a few exceptions, e.g. Substitutions
                for (ResolvedJavaMethod inlinee : inlinedMethods) {
                    AnalysisMethod analysisMethod = ((HostedMethod) inlinee).getWrapped();
                    JavaMethod inlineeJavaMethod = BuildTimeInterpreterUniverse.singleton().methodOrUnresolved(analysisMethod);
                    if (inlineeJavaMethod instanceof InterpreterResolvedJavaMethod inlineeInterpreterMethod) {
                        if (inlineeInterpreterMethod.equals(interpreterMethod)) {
                            InterpreterUtil.log("[inlinedeps] \t%s includes itself as an inlining dependency", interpreterMethod);
                        } else {
                            inlineeInterpreterMethod.addInliner(interpreterMethod);
                            InterpreterUtil.log("[inlinedeps] \t%s", inlinee);
                        }
                    } else {
                        InterpreterUtil.log("[inlinedeps] \tWarning: did not find interp method for %s", inlinee);
                    }
                }
            }

            if (!hostedMethod.isCompiled()) {
                InterpreterUtil.log("[got] after compilation: %s is not compiled, nulling it out", hostedMethod);
                interpreterMethod.setVTableIndex(VTBL_NO_ENTRY);
                interpreterMethod.setNativeEntryPoint(null);
            } else {
                if (interpreterMethod.hasBytecodes()) {
                    /* only allocate stub for methods that we can actually run in the interpreter */
                    interpreterMethod.setEnterStubOffset(estOffset++);
                }

                interpreterMethod.setNativeEntryPoint(new MethodPointer(interpreterMethod.getOriginalMethod()));
            }

            if (!interpreterMethod.isStatic() && !interpreterMethod.isConstructor()) {
                if (hostedMethod.getImplementations().length > 1) {
                    if (!hostedMethod.hasVTableIndex()) {
                        InterpreterUtil.log("[vtable assignment] %s has multiple implementations but no vtable slot. This is not supported.%n", hostedMethod);
                    } else {
                        InterpreterUtil.log("[vtable assignment] Setting to Index %s for methods %s <> %s%n", hostedMethod.getVTableIndex(), interpreterMethod, hostedMethod);
                        interpreterMethod.setVTableIndex(hostedMethod.getVTableIndex());
                        /*
                         * Do not null out native entry point, the method may be invoked via
                         * INVOKESPECIAL
                         */
                    }
                } else if (hostedMethod.getImplementations().length == 1) {
                    InterpreterUtil.log("[vtable assignment] Only one implementation available for %s%n", hostedMethod);
                    interpreterMethod.setVTableIndex(VTBL_ONE_IMPL);

                    InterpreterResolvedJavaMethod oneImpl = (InterpreterResolvedJavaMethod) BuildTimeInterpreterUniverse.singleton().methodOrUnresolved(hostedMethod.getImplementations()[0]);
                    interpreterMethod.setOneImplementation(oneImpl);
                    InterpreterUtil.log("[vtable assignment]  set oneImpl to -> %s%n", oneImpl);
                } else {
                    InterpreterUtil.log("[vtable assignment] No implementation available: %s%n", hostedMethod);
                    interpreterMethod.setVTableIndex(VTBL_NO_ENTRY);
                }
            }
        }

        NativeImageHeap heap = accessImpl.getHeap();

        for (InterpreterResolvedJavaField field : BuildTimeInterpreterUniverse.singleton().getFields()) {
            HostedField hostedField = accessImpl.getUniverse().optionalLookup(field.getOriginalField());
            if (!hostedField.isReachable()) {
                // Field was not included in the image, so it can only be an artificially reachable
                // field used only by the interpreter. These fields are read-only, thus
                // unmaterialized,
                // by now its value should be already computed.
                VMError.guarantee(field.isArtificiallyReachable());
                VMError.guarantee(field.isUnmaterializedConstant());
                VMError.guarantee(field.getUnmaterializedConstant() != null);
            } else if (hostedField.isUnmaterialized()) {
                AnalysisField analysisField = (AnalysisField) field.getOriginalField();
                if (hostedField.getType().isWordType() || analysisField.getJavaKind().isPrimitive()) {
                    JavaConstant constant = heap.hConstantReflection.readFieldValue(hostedField, null);
                    assert constant instanceof PrimitiveConstant;
                    BuildTimeInterpreterUniverse.setUnmaterializedConstantValue(field, constant);
                } else if (analysisField.isRead() || analysisField.isFolded()) {
                    // May or may not be in the image.
                    assert analysisField.getJavaKind().isObject();
                    JavaConstant constantValue = heap.hConstantReflection.readFieldValue(hostedField, null);
                    BuildTimeInterpreterUniverse.setUnmaterializedConstantValue(field, constantValue);
                } else {
                    // Block access.
                    BuildTimeInterpreterUniverse.setUnmaterializedConstantValue(field, JavaConstant.forIllegal());
                }
                VMError.guarantee(field.isUnmaterializedConstant());
            } else if (!hostedField.hasLocation()) {
                InterpreterUtil.log("Found materialized field without location: %s", hostedField);
                BuildTimeInterpreterUniverse.setUnmaterializedConstantValue(field, JavaConstant.forIllegal());
            } else {
                int fieldOffset = hostedField.getOffset();
                field.setOffset(fieldOffset);
            }
        }

        DebuggerSupport supportImpl = DebuggerSupport.singleton();
        for (InterpreterResolvedJavaMethod method : BuildTimeInterpreterUniverse.singleton().getMethods()) {
            ReferenceConstant<FunctionPointerHolder> nativeEntryPointHolderConstant = method.getNativeEntryPointHolderConstant();
            if (nativeEntryPointHolderConstant != null) {
                supportImpl.ensureMethodPointerIsInImage(nativeEntryPointHolderConstant.getReferent());
            }
        }
    }

    @Override
    public void afterHeapLayout(AfterHeapLayoutAccess access) {
        FeatureImpl.AfterHeapLayoutAccessImpl accessImpl = (FeatureImpl.AfterHeapLayoutAccessImpl) access;
        NativeImageHeap heap = accessImpl.getHeap();

        for (InterpreterResolvedJavaField field : BuildTimeInterpreterUniverse.singleton().getFields()) {
            if (field.isArtificiallyReachable()) {
                // Value should be already computed.
                JavaConstant value = field.getUnmaterializedConstant();
                VMError.guarantee(value != null && value != JavaConstant.ILLEGAL);
                continue;
            }
            HostedField hostedField = accessImpl.getMetaAccess().getUniverse().optionalLookup(field.getOriginalField());
            if (hostedField.isUnmaterialized()) {
                AnalysisField analysisField = (AnalysisField) field.getOriginalField();
                if (hostedField.getType().isWordType()) {
                    // Ignore, words are stored as primitive values.
                } else if ((analysisField.isFolded() && analysisField.getJavaKind().isObject())) {
                    JavaConstant constantValue = heap.hConstantReflection.readFieldValue(hostedField, null);
                    BuildTimeInterpreterUniverse.setUnmaterializedConstantValue(field, constantValue);
                }
            }
        }

        GOTEntryAllocator gotEntryAllocator = HostedPLTGOTConfiguration.singleton().getGOTEntryAllocator();
        for (InterpreterResolvedJavaMethod interpreterMethod : BuildTimeInterpreterUniverse.singleton().getMethods()) {
            HostedMethod hostedMethod = accessImpl.getMetaAccess().getUniverse().optionalLookup(interpreterMethod.getOriginalMethod());

            int gotOffset = GOT_NO_ENTRY;

            if (interpreterMethod.isInterpreterExecutable()) {
                gotOffset = gotEntryAllocator.queryGotEntry(hostedMethod);
            }

            if (gotOffset == GOT_NO_ENTRY) {
                InterpreterUtil.log("[got] Missing GOT offset for %s", interpreterMethod);
            } else {
                InterpreterUtil.log("[got] Got GOT offset=%s for %s", gotOffset, interpreterMethod);
            }
            interpreterMethod.setGOTOffset(gotOffset);
        }
    }

    @Override
    public void afterAbstractImageCreation(AfterAbstractImageCreationAccess access) {
        FeatureImpl.AfterAbstractImageCreationAccessImpl accessImpl = ((FeatureImpl.AfterAbstractImageCreationAccessImpl) access);

        List<InterpreterResolvedJavaMethod> includedMethods = BuildTimeInterpreterUniverse.singleton().getMethods()
                        .stream()
                        .filter(m -> m.getEnterStubOffset() != EST_NO_ENTRY)
                        .collect(Collectors.toList());

        /* create enter stubs */
        InterpreterStubSection stubSection = ImageSingletons.lookup(InterpreterStubSection.class);
        stubSection.createInterpreterEnterStubSection(accessImpl.getImage(), includedMethods);

        /* populate EST */
        enterStubTable.installAdditionalInfoIntoImageObjectFile(accessImpl.getImage(), includedMethods);
    }

    @Override
    public void beforeImageWrite(BeforeImageWriteAccess access) {
        FeatureImpl.BeforeImageWriteAccessImpl accessImpl = (FeatureImpl.BeforeImageWriteAccessImpl) access;

        // Serialize interpreter metadata.
        NativeImageHeap heap = accessImpl.getImage().getHeap();

        Map<Class<?>, DynamicHub> classToHub = new IdentityHashMap<>();
        for (NativeImageHeap.ObjectInfo info : heap.getObjects()) {
            Object object = info.getObject();
            if (object instanceof DynamicHub) {
                DynamicHub hub = (DynamicHub) object;
                classToHub.put(hub.getHostedJavaClass(), hub);
            }
        }

        DebuggerSupport supportImpl = DebuggerSupport.singleton();
        SerializationContext.Builder builder = supportImpl.getUniverseSerializerBuilder()
                        .registerWriter(true, ReferenceConstant.class, Serializers.newReferenceConstantWriter(ref -> {
                            NativeImageHeap.ObjectInfo info = null;
                            if (ref instanceof Class) {
                                DynamicHub hub = classToHub.get(ref);
                                info = heap.getObjectInfo(hub);
                            } else if (ref instanceof ImageHeapConstant imageHeapConstant) {
                                info = heap.getConstantInfo(imageHeapConstant);
                            } else {
                                info = heap.getObjectInfo(ref);
                            }

                            if (info == null) {
                                // avoid side-effects
                                String purgedObject = Objects.toIdentityString(ref);
                                InterpreterUtil.log("Constant not serialized in the image: %s", purgedObject);
                                return 0L;
                            } else {
                                return info.getOffset();
                            }
                        }));

        Path destDir = NativeImageGenerator.generatedFiles(HostedOptionValues.singleton());

        // Be explicit here: .metadata file is derived from <final binary name (including
        // extension)>
        String metadataFileName = MetadataUtil.metadataFileName(accessImpl.getOutputFilename());
        Path metadataPath = destDir.resolve(metadataFileName);

        int crc32;
        InterpreterUniverseImpl snapshot = BuildTimeInterpreterUniverse.singleton().snapshot();
        try {
            snapshot.saveTo(builder, metadataPath);
            crc32 = InterpreterUniverseImpl.computeCRC32(metadataPath);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        BuildArtifacts.singleton().add(BuildArtifacts.ArtifactType.DEBUG_INFO, metadataPath);

        String hashString = "crc32:" + toHexString(crc32);

        String dumpInterpreterClassFiles = InterpreterOptions.InterpreterDumpClassFiles.getValue();

        if (!dumpInterpreterClassFiles.isEmpty()) {
            try {
                dumpInterpreterMetadataAsClassFiles(snapshot, dumpInterpreterClassFiles);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        InterpreterStubSection stubSection = ImageSingletons.lookup(InterpreterStubSection.class);

        stubSection.markEnterStubPatch(accessImpl.getHostedMetaAccess().lookupJavaMethod(enterInterpreterMethod));
        enterStubTable.writeMetadataHashString(hashString.getBytes(StandardCharsets.UTF_8));
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    @SuppressFBWarnings(value = "NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE", justification = "path.getParent() is never null")
    private static void dumpInterpreterMetadataAsClassFiles(InterpreterUniverseImpl universe, String outputFolder) throws IOException {
        for (InterpreterResolvedJavaType type : universe.getTypes()) {
            if (type.isPrimitive() || type.isArray()) {
                continue;
            }
            String typeName = type.getName();
            String separator = FileSystems.getDefault().getSeparator();
            assert typeName.startsWith("L") && typeName.endsWith(";");
            String relativeFilePath = typeName.substring(1, typeName.length() - 1).replace("/", separator) + ".class";
            Path path = Path.of(outputFolder, relativeFilePath);
            if (!Files.exists(path.getParent())) {
                Files.createDirectories(path.getParent());
            }
            byte[] bytes = ClassFile.dumpInterpreterTypeClassFile(universe, type);
            Files.write(path, bytes, StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        }
    }
}
