/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_0;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_0;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.word.Pointer;
import org.graalvm.word.WordBase;

import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.core.InvalidMethodPointerHandler;
import com.oracle.svm.core.ParsingReason;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.graal.aarch64.AArch64InterpreterStubs;
import com.oracle.svm.core.graal.amd64.AMD64InterpreterStubs;
import com.oracle.svm.core.graal.code.InterpreterAccessStubData;
import com.oracle.svm.core.graal.code.StubCallingConvention;
import com.oracle.svm.core.interpreter.InterpreterSupport;
import com.oracle.svm.core.meta.MethodPointer;
import com.oracle.svm.core.stack.ThreadStackPrinter;
import com.oracle.svm.core.thread.ThreadListenerSupport;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.espresso.shared.meta.SignaturePolymorphicIntrinsic;
import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.hosted.code.SubstrateCompilationDirectives;
import com.oracle.svm.hosted.image.NativeImageCodeCache;
import com.oracle.svm.hosted.meta.HostedMetaAccess;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.interpreter.debug.DebuggerEventsFeature;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedJavaMethod;
import com.oracle.svm.util.AnnotationUtil;
import com.oracle.svm.util.JVMCIReflectionUtil;
import com.oracle.svm.util.LogUtils;
import com.oracle.svm.util.ReflectionUtil;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.code.CompilationResult;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.calc.FloatingNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugin;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugins.Registration;
import jdk.graal.compiler.nodes.spi.Lowerable;
import jdk.graal.compiler.nodes.spi.LoweringTool;
import jdk.graal.compiler.phases.util.Providers;
import jdk.vm.ci.code.BytecodeFrame;
import jdk.vm.ci.code.DebugInfo;
import jdk.vm.ci.code.site.Call;
import jdk.vm.ci.code.site.Infopoint;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Local;
import jdk.vm.ci.meta.LocalVariableTable;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Signature;

@Platforms(Platform.HOSTED_ONLY.class)
public class InterpreterFeature implements InternalFeature {
    private AnalysisMethod leaveStub;

    static boolean assertionsEnabled() {
        boolean enabled = false;
        assert (enabled = true) == true : "Enabling assertions";
        return enabled;
    }

    static boolean executableByInterpreter(AnalysisMethod m) {
        if (AnnotationUtil.getAnnotation(m, CFunction.class) != null) {
            return false;
        }
        if (AnnotationUtil.getAnnotation(m, CEntryPoint.class) != null) {
            return false;
        }
        Uninterruptible uninterruptible = AnnotationUtil.getAnnotation(m, Uninterruptible.class);
        if (uninterruptible != null) {
            if (uninterruptible.mayBeInlined() && !uninterruptible.callerMustBe()) {
                /*
                 * this method was only annotated with @Uninterruptible so that it can be called
                 * from uninterruptible code, not because it has to be uninterruptible itself.
                 */
            } else {
                return false;
            }
        }
        if (StubCallingConvention.Utils.hasStubCallingConvention(m)) {
            /*
             * enterstub can only deal with the internal Java calling convention of SVM. If ever
             * needed, the enterstub can be adapted.
             */
            return false;
        }

        return true;
    }

    public static boolean callableByInterpreter(ResolvedJavaMethod m, MetaAccessProvider metaAccess) {
        if (AnnotationUtil.getAnnotation(m, Fold.class) != null) {
            /*
             * GR-55052: For now @Fold methods are considered not callable. The problem is that such
             * methods are reachability cut-offs, so we would need to roll our own reachability
             * analysis to have all the relevant methods available at run-time. Instead we should
             * replace the call-site with the constant according to SVM semantics.
             */
            return false;
        }

        ResolvedJavaType wordBaseType = metaAccess.lookupJavaType(WordBase.class);

        if (wordBaseType.isAssignableFrom(m.getDeclaringClass())) {
            return false;
        }

        Signature signature = m.getSignature();
        if (wordBaseType.isAssignableFrom(signature.getReturnType(m.getDeclaringClass()).resolve(m.getDeclaringClass()))) {
            return false;
        }

        for (int i = 0; i < signature.getParameterCount(false); i++) {
            if (wordBaseType.isAssignableFrom(signature.getParameterType(i, null).resolve(m.getDeclaringClass()))) {
                return false;
            }
        }

        return true;
    }

    @Override
    public List<Class<? extends Feature>> getRequiredFeatures() {
        return Arrays.asList(DebuggerEventsFeature.class);
    }

    @Override
    public void registerInvocationPlugins(Providers providers, GraphBuilderConfiguration.Plugins plugins, ParsingReason reason) {
        Registration r = new Registration(plugins.getInvocationPlugins(), InterpreterDirectives.class);

        r.register(new InvocationPlugin.RequiredInvocationPlugin("inInterpreter") {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                /* Dummy node so that analysis will not be able to "see through it". */
                b.addPush(JavaKind.Boolean, new InInterpreterNode());
                return true;
            }
        });
    }

    @NodeInfo(cycles = CYCLES_0, size = SIZE_0)
    public static final class InInterpreterNode extends FloatingNode implements Lowerable {
        public static final NodeClass<InInterpreterNode> TYPE = NodeClass.create(InInterpreterNode.class);

        public InInterpreterNode() {
            super(TYPE, StampFactory.forKind(JavaKind.Boolean));
        }

        @Override
        public void lower(LoweringTool tool) {
            replaceAtUsagesAndDelete(graph().unique(ConstantNode.forBoolean(false)));
        }
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ThreadListenerSupport.get().register(new ThreadListenerThreadLocalHandlesAllocator());
    }

    @Override
    public void duringSetup(DuringSetupAccess access) {
        if (Platform.includedIn(Platform.AARCH64.class)) {
            ImageSingletons.add(InterpreterStubSection.class, new AArch64InterpreterStubSection());
            ImageSingletons.add(InterpreterAccessStubData.class, new AArch64InterpreterStubs.AArch64InterpreterAccessStubData());
        } else if (Platform.includedIn(Platform.AMD64.class)) {
            ImageSingletons.add(InterpreterStubSection.class, new AMD64InterpreterStubSection());
            ImageSingletons.add(InterpreterAccessStubData.class, new AMD64InterpreterStubs.AMD64InterpreterAccessStubData());
        } else {
            throw VMError.unsupportedFeature("Platform not supported yet: " + ImageSingletons.lookup(Platform.class));
        }
    }

    private static int findLocalSlotByName(String localName, Local[] locals) {
        for (Local local : locals) {
            if (localName.equals(local.getName())) {
                return local.getSlot();
            }
        }
        throw new NoSuchElementException(localName);
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        FeatureImpl.BeforeAnalysisAccessImpl accessImpl = (FeatureImpl.BeforeAnalysisAccessImpl) access;

        BuildTimeInterpreterUniverse.freshSingletonInstance();
        AnalysisMetaAccess metaAccess = accessImpl.getMetaAccess();

        AnalysisMethod interpreterRoot = (AnalysisMethod) getExecuteBodyFromBCIMethod(metaAccess);
        assert interpreterRoot.hasNeverInlineDirective();

        LocalVariableTable interpreterVariableTable = interpreterRoot.getLocalVariableTable();
        int interpreterMethodSlot = findLocalSlotByName("method", interpreterVariableTable.getLocalsAt(0)); // parameter
        int interpreterFrameSlot = findLocalSlotByName("frame", interpreterVariableTable.getLocalsAt(0)); // parameter
        // Local variable, search all locals.
        int bciSlot = findLocalSlotByName("curBCI", interpreterVariableTable.getLocals());

        AnalysisMethod intrinsicRoot = (AnalysisMethod) getExecuteIntrinsicMethod(metaAccess);
        assert intrinsicRoot.hasNeverInlineDirective();

        LocalVariableTable intrinsicVariableTable = intrinsicRoot.getLocalVariableTable();
        int intrinsicMethodSlot = findLocalSlotByName("method", intrinsicVariableTable.getLocalsAt(0)); // parameter
        int intrinsicFrameSlot = findLocalSlotByName("frame", intrinsicVariableTable.getLocalsAt(0)); // parameter

        ImageSingletons.add(InterpreterSupport.class, new InterpreterSupportImpl(bciSlot, interpreterMethodSlot, interpreterFrameSlot, intrinsicMethodSlot, intrinsicFrameSlot));
        ImageSingletons.add(InterpreterDirectivesSupport.class, new InterpreterDirectivesSupportImpl());
        ImageSingletons.add(InterpreterMethodPointerHolder.class, new InterpreterMethodPointerHolder());

        // Locals must be available at runtime to retrieve BCI, interpreted method and interpreter
        // frame.
        SubstrateCompilationDirectives.singleton().registerFrameInformationRequired(interpreterRoot);
        SubstrateCompilationDirectives.singleton().registerFrameInformationRequired(intrinsicRoot);

        Method leaveMethod = ReflectionUtil.lookupMethod(InterpreterStubSection.class, "leaveInterpreterStub", CFunctionPointer.class, Pointer.class, long.class);
        leaveStub = metaAccess.lookupJavaMethod(leaveMethod);
        accessImpl.registerAsRoot(leaveStub, true, "low level entry point");
    }

    @Override
    public void beforeCompilation(BeforeCompilationAccess access) {
        FeatureImpl.BeforeCompilationAccessImpl accessImpl = (FeatureImpl.BeforeCompilationAccessImpl) access;

        /* required so that it can hold a relocatable pointer */
        accessImpl.registerAsImmutable(InterpreterMethodPointerHolder.singleton());
        accessImpl.registerAsImmutable(InterpreterSupport.singleton());

        HostedMethod methodNotCompiledHandler = accessImpl.getMetaAccess().lookupJavaMethod(InvalidMethodPointerHandler.METHOD_POINTER_NOT_COMPILED_HANDLER_METHOD);
        InterpreterMethodPointerHolder.setMethodNotCompiledHandler(new MethodPointer(methodNotCompiledHandler));
    }

    /**
     * Must be called by all features that depend on InterpreterFeature in
     * {@link #beforeCompilation(BeforeCompilationAccess)}.
     */
    public static void prepareSignatures() {
        InterpreterSupport interpreterSingleton = InterpreterSupport.singleton();
        for (InterpreterResolvedJavaMethod interpreterMethod : BuildTimeInterpreterUniverse.singleton().getMethods()) {
            interpreterMethod.setPreparedSignature(interpreterSingleton.prepareSignature(interpreterMethod));
        }
    }

    private static boolean verifyPreparedSignatures() {
        for (InterpreterResolvedJavaMethod interpreterMethod : BuildTimeInterpreterUniverse.singleton().getMethods()) {
            assert interpreterMethod.getPreparedSignature() != null;
        }
        return true;
    }

    @Override
    public void afterCompilation(AfterCompilationAccess access) {
        assert verifyPreparedSignatures();

        FeatureImpl.AfterCompilationAccessImpl accessImpl = (FeatureImpl.AfterCompilationAccessImpl) access;

        HostedMethod hLeaveStub = accessImpl.getUniverse().lookup(leaveStub);
        int leaveStubLength = accessImpl.getCompilations().get(hLeaveStub).result.getTargetCodeSize();

        InterpreterSupport.setLeaveStubPointer(new MethodPointer(hLeaveStub), leaveStubLength);
    }

    private static ResolvedJavaMethod getExecuteBodyFromBCIMethod(MetaAccessProvider metaAccess) {
        ResolvedJavaType interpreterRootType = metaAccess.lookupJavaType(Interpreter.Root.class);
        return JVMCIReflectionUtil.getUniqueDeclaredMethod(metaAccess, interpreterRootType, "executeBodyFromBCI",
                        InterpreterFrame.class, InterpreterResolvedJavaMethod.class, int.class, int.class, boolean.class);
    }

    private static ResolvedJavaMethod getExecuteIntrinsicMethod(MetaAccessProvider metaAccess) {
        ResolvedJavaType intrinsicRootType = metaAccess.lookupJavaType(Interpreter.IntrinsicRoot.class);
        return JVMCIReflectionUtil.getUniqueDeclaredMethod(metaAccess, intrinsicRootType, "execute",
                        InterpreterFrame.class, InterpreterResolvedJavaMethod.class, SignaturePolymorphicIntrinsic.class, boolean.class);
    }

    @Override
    public void beforeImageWrite(BeforeImageWriteAccess access) {
        FeatureImpl.BeforeImageWriteAccessImpl accessImpl = (FeatureImpl.BeforeImageWriteAccessImpl) access;
        HostedMetaAccess metaAccess = accessImpl.getMetaAccess();
        NativeImageCodeCache codeCache = accessImpl.getImage().getCodeCache();
        checkPreAllocatedValueInfos(metaAccess, codeCache);
    }

    private static void checkPreAllocatedValueInfos(HostedMetaAccess metaAccess, NativeImageCodeCache codeCache) {
        HostedMethod interpreterRoot = (HostedMethod) getExecuteBodyFromBCIMethod(metaAccess);
        assert interpreterRoot.hasNeverInlineDirective();
        checkPreAllocatedValueInfos(codeCache, interpreterRoot);
        HostedMethod intrinsicRoot = (HostedMethod) getExecuteIntrinsicMethod(metaAccess);
        assert intrinsicRoot.hasNeverInlineDirective();
        // This method might not be compiled (e.g., with the debugger enabled but crema disabled)
        if (intrinsicRoot.isCompiled()) {
            checkPreAllocatedValueInfos(codeCache, intrinsicRoot);
        }
    }

    private static void checkPreAllocatedValueInfos(NativeImageCodeCache codeCache, HostedMethod root) {
        CompilationResult interpreterRootCompilation = codeCache.compilationResultFor(root);
        int maxValues = 0;
        Infopoint maxValuesInfopoint = null;
        for (Infopoint infopoint : interpreterRootCompilation.getInfopoints()) {
            DebugInfo debugInfo = infopoint.debugInfo;
            if (debugInfo == null || !(infopoint instanceof Call)) {
                continue;
            }
            BytecodeFrame frame = debugInfo.frame();
            while (frame.caller() != null) {
                // we need values for the root method
                frame = frame.caller();
            }
            if (maxValues < frame.values.length) {
                maxValues = frame.values.length;
                maxValuesInfopoint = infopoint;
            }
        }
        if (maxValues > ThreadStackPrinter.NUM_INTERPRETER_PREALLOCATED_VALUE_INFO) {
            String message = "NUM_PREALLOCATED_VALUE_INFO is too small (" + ThreadStackPrinter.NUM_INTERPRETER_PREALLOCATED_VALUE_INFO + "): " +
                            interpreterRootCompilation + " contains a Call site with " + maxValues + " values (" + maxValuesInfopoint + ")";
            if (assertionsEnabled()) {
                throw VMError.shouldNotReachHere(message);
            } else {
                LogUtils.warning(message);
            }
        }
    }
}
