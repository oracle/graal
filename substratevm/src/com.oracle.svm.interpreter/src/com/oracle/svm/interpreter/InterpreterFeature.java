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

import org.graalvm.nativeimage.AnnotationAccess;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.word.Pointer;
import org.graalvm.word.WordBase;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.core.InvalidMethodPointerHandler;
import com.oracle.svm.core.ParsingReason;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.graal.aarch64.AArch64InterpreterStubs;
import com.oracle.svm.core.graal.amd64.AMD64InterpreterStubs;
import com.oracle.svm.core.graal.code.InterpreterAccessStubData;
import com.oracle.svm.core.interpreter.InterpreterSupport;
import com.oracle.svm.core.meta.MethodPointer;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.hosted.code.SubstrateCompilationDirectives;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.interpreter.debug.DebuggerEventsFeature;
import com.oracle.svm.util.ReflectionUtil;

import jdk.graal.compiler.api.replacements.Fold;
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

    static boolean executableByInterpreter(AnalysisMethod m) {
        if (AnnotationAccess.getAnnotation(m, CFunction.class) != null) {
            return false;
        }
        if (AnnotationAccess.getAnnotation(m, CEntryPoint.class) != null) {
            return false;
        }
        Uninterruptible uninterruptible = AnnotationAccess.getAnnotation(m, Uninterruptible.class);
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

        return true;
    }

    public static boolean callableByInterpreter(ResolvedJavaMethod m, MetaAccessProvider metaAccess) {
        if (AnnotationAccess.getAnnotation(m, Fold.class) != null) {
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
        AnalysisMethod interpreterRoot = accessImpl.getMetaAccess().lookupJavaType(Interpreter.Root.class).getDeclaredMethods(false)[0];

        accessImpl.registerAsRoot(interpreterRoot, true, "interpreter main loop");
        LocalVariableTable interpreterVariableTable = interpreterRoot.getLocalVariableTable();
        int interpretedMethodSlot = findLocalSlotByName("method", interpreterVariableTable.getLocalsAt(0)); // parameter
        int interpreterFrameSlot = findLocalSlotByName("frame", interpreterVariableTable.getLocalsAt(0)); // parameter
        // Local variable, search all locals.
        int bciSlot = findLocalSlotByName("curBCI", interpreterVariableTable.getLocals());

        ImageSingletons.add(InterpreterSupport.class, new InterpreterSupportImpl(bciSlot, interpretedMethodSlot, interpreterFrameSlot));
        ImageSingletons.add(InterpreterDirectivesSupport.class, new InterpreterDirectivesSupportImpl());
        ImageSingletons.add(InterpreterMethodPointerHolder.class, new InterpreterMethodPointerHolder());

        // Locals must be available at runtime to retrieve BCI, interpreted method and interpreter
        // frame.
        SubstrateCompilationDirectives.singleton().registerFrameInformationRequired(interpreterRoot);

        Method leaveMethod = ReflectionUtil.lookupMethod(InterpreterStubSection.class, "leaveInterpreterStub", CFunctionPointer.class, Pointer.class, long.class, long.class);
        leaveStub = accessImpl.getMetaAccess().lookupJavaMethod(leaveMethod);
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

    @Override
    public void afterCompilation(AfterCompilationAccess access) {
        FeatureImpl.AfterCompilationAccessImpl accessImpl = (FeatureImpl.AfterCompilationAccessImpl) access;

        HostedMethod hLeaveStub = accessImpl.getUniverse().lookup(leaveStub);
        int leaveStubLength = accessImpl.getCompilations().get(hLeaveStub).result.getTargetCodeSize();

        InterpreterSupport.setLeaveStubPointer(new MethodPointer(hLeaveStub), leaveStubLength);
    }
}
