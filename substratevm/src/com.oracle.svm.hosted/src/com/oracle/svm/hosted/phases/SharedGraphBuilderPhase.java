/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.svm.hosted.phases;

import static org.graalvm.compiler.bytecode.Bytecodes.NEW;

import org.graalvm.compiler.core.common.calc.Condition;
import org.graalvm.compiler.core.common.spi.ConstantFieldProvider;
import org.graalvm.compiler.core.common.type.StampPair;
import org.graalvm.compiler.java.BytecodeParser;
import org.graalvm.compiler.java.GraphBuilderPhase;
import org.graalvm.compiler.nodes.CallTargetNode.InvokeKind;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import org.graalvm.compiler.nodes.graphbuilderconf.IntrinsicContext;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins.InvocationPluginReceiver;
import org.graalvm.compiler.nodes.java.MethodCallTargetNode;
import org.graalvm.compiler.nodes.spi.StampProvider;
import org.graalvm.compiler.nodes.type.StampTool;
import org.graalvm.compiler.nodes.util.GraphUtil;
import org.graalvm.compiler.phases.OptimisticOptimizations;
import org.graalvm.compiler.word.WordTypes;

import com.oracle.graal.pointsto.infrastructure.WrappedConstantPool;
import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.svm.core.hub.ClassSynchronizationSupport;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.meta.SubstrateObjectConstant;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.core.util.UserError.UserException;
import com.oracle.svm.hosted.NativeImageOptions;

import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.ExceptionHandler;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaField;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaMethod;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.JavaTypeProfile;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

public abstract class SharedGraphBuilderPhase extends GraphBuilderPhase.Instance {
    final WordTypes wordTypes;

    public SharedGraphBuilderPhase(MetaAccessProvider metaAccess, StampProvider stampProvider, ConstantReflectionProvider constantReflection, ConstantFieldProvider constantFieldProvider,
                    GraphBuilderConfiguration graphBuilderConfig, OptimisticOptimizations optimisticOpts, IntrinsicContext initialIntrinsicContext, WordTypes wordTypes) {
        super(metaAccess, stampProvider, constantReflection, constantFieldProvider, graphBuilderConfig, optimisticOpts, initialIntrinsicContext);
        this.wordTypes = wordTypes;
    }

    public abstract static class SharedBytecodeParser extends BytecodeParser {
        private final ResolvedJavaType explicitNullCheck;
        private final ResolvedJavaType[] explicitExceptionTypes;

        protected SharedBytecodeParser(GraphBuilderPhase.Instance graphBuilderInstance, StructuredGraph graph, BytecodeParser parent, ResolvedJavaMethod method, int entryBCI,
                        IntrinsicContext intrinsicContext) {
            super(graphBuilderInstance, graph, parent, method, entryBCI, intrinsicContext);
            explicitNullCheck = metaAccess.lookupJavaType(NullPointerException.class);
            explicitExceptionTypes = new ResolvedJavaType[]{explicitNullCheck, metaAccess.lookupJavaType(ArrayIndexOutOfBoundsException.class),
                            metaAccess.lookupJavaType(IndexOutOfBoundsException.class)};

        }

        @Override
        protected RuntimeException throwParserError(Throwable e) {
            if (e instanceof UserException) {
                throw (UserException) e;
            }
            throw super.throwParserError(e);
        }

        protected WordTypes getWordTypes() {
            return ((SharedGraphBuilderPhase) getGraphBuilderInstance()).wordTypes;
        }

        @Override
        protected void maybeEagerlyResolve(int cpi, int bytecode) {
            try {
                super.maybeEagerlyResolve(cpi, bytecode);
            } catch (Exception e) {
                if (NativeImageOptions.ReportUnsupportedElementsAtRuntime.getValue()) {
                    /*
                     * Silently ignore type resolution exceptions for eagerly resolved types. When
                     * ReportUnsupportedElementsAtRuntime is enabled the resolution mechanism
                     * bellow, e.g., in genInvokeStatic(), will insert DeoptimizeNode when
                     * resolution fails such that the resolution errors are reported at runtime.
                     */
                } else {
                    throw e;
                }
            }
        }

        /**
         * Lookup a method that failed to resolve. If the lookup would go through the usual
         * ConstantPool.lookupMethod() it could fail in the WrappedConstantPool.lookupMethod().
         * Although the WrappedConstantPool.lookupMethod() can return unresolved methods the
         * resolution might still fail in Universe.lookup() if the method has unresolved signature
         * types, i.e., return and parameter types . By using
         * WrappedConstantPool.lookupMethodInWrapped() the lookup is dispatched to the lowest level
         * of wrapped pool, i.e., the HotSpotConstantPool, where the unresolved method can be found.
         */
        private JavaMethod lookupFailedResolutionMethod(int cpi, int opcode) {
            assert constantPool instanceof WrappedConstantPool;
            JavaMethod target = ((WrappedConstantPool) constantPool).lookupMethodInWrapped(cpi, opcode);
            return target;
        }

        private JavaType lookupFailedResolutionType(int cpi, int opcode) {
            assert constantPool instanceof WrappedConstantPool;
            JavaType target = ((WrappedConstantPool) constantPool).lookupTypeInWrapped(cpi, opcode);
            return target;
        }

        private JavaField lookupFailedResolutionField(int cpi, ResolvedJavaMethod lookupMethod, int opcode) {
            assert constantPool instanceof WrappedConstantPool;
            JavaField target = ((WrappedConstantPool) constantPool).lookupFieldInWrapped(cpi, lookupMethod, opcode);
            return target;
        }

        private void handleMethodResolutionError(LinkageError e, int cpi, int opcode, InvokeKind invokeKind) {
            if (NativeImageOptions.ReportUnsupportedElementsAtRuntime.getValue()) {
                /*
                 * During method resolution, even if the method is resolved, we might fail while
                 * trying to resolve the method signature types, i.e, return and parameter types.
                 * When we inquire to JVMCI level about method properties, e.g., annotations,
                 * HotSpotResolvedJavaMethodImpl.toJava() tries to create the method signature
                 * assuming resolved parameter/return types. If that happens, we just treat the
                 * called method itself as unresolved. When DeoptimizeNode will offer more context
                 * information we can attach the actual reason.
                 *
                 * Important note: trying to resolve the method in the HotSpotPool, i.e., by calling
                 * lookupFailedResolutionMethod(), will return a ResolvedJavaMethod even for a
                 * method having unresolved return/parameter types, thus instead of handing it to
                 * super.genInvokeStatic() we just short circuit the parsing and insert a
                 * DeoptimizeNode here!
                 */
                JavaMethod target = lookupFailedResolutionMethod(cpi, opcode);
                handleUnresolvedInvoke(target, invokeKind);
                return;
            }
            throw e;
        }

        @Override
        protected void genInvokeStatic(int cpi, int opcode) {
            try {
                super.genInvokeStatic(cpi, opcode);
            } catch (LinkageError e) {
                handleMethodResolutionError(e, cpi, opcode, InvokeKind.Static);
            }
        }

        @Override
        protected void genInvokeVirtual(int cpi, int opcode) {
            try {
                super.genInvokeVirtual(cpi, opcode);
            } catch (LinkageError e) {
                handleMethodResolutionError(e, cpi, opcode, InvokeKind.Virtual);
            }
        }

        @Override
        protected void genInvokeSpecial(int cpi, int opcode) {
            try {
                super.genInvokeSpecial(cpi, opcode);
            } catch (LinkageError e) {
                handleMethodResolutionError(e, cpi, opcode, InvokeKind.Special);
            }
        }

        @Override
        protected void genInvokeDynamic(int cpi, int opcode) {
            try {
                super.genInvokeDynamic(cpi, opcode);
            } catch (LinkageError e) {
                handleMethodResolutionError(e, cpi, opcode, InvokeKind.Static);
            }
        }

        @Override
        protected void genNewInstance(int cpi) {
            try {
                super.genNewInstance(cpi);
            } catch (LinkageError e) {
                if (NativeImageOptions.ReportUnsupportedElementsAtRuntime.getValue()) {
                    JavaType target = lookupFailedResolutionType(cpi, NEW);
                    handleUnresolvedNewInstance(target);
                    return;
                }
                throw e;
            }
        }

        private boolean handleStoreFieldResolutionError(int cpi, int opcode) {
            if (NativeImageOptions.ReportUnsupportedElementsAtRuntime.getValue()) {
                JavaField target = lookupFailedResolutionField(cpi, method, opcode);
                handleUnresolvedStoreField(target, null, null);
                return true;
            }
            return false;
        }

        private void handleLoadFieldResolutionError(LinkageError e, int cpi, int opcode, ValueNode receiverInput) {
            if (NativeImageOptions.ReportUnsupportedElementsAtRuntime.getValue()) {
                JavaField target = lookupFailedResolutionField(cpi, method, opcode);
                handleUnresolvedLoadField(target, receiverInput);
                return;
            }
            throw e;
        }

        @Override
        protected void genGetField(int cpi, int opcode, ValueNode receiverInput) {
            try {
                super.genGetField(cpi, opcode, receiverInput);
            } catch (LinkageError e) {
                handleLoadFieldResolutionError(e, cpi, opcode, receiverInput);
            }
        }

        @Override
        protected void genPutField(int cpi, int opcode) {
            try {
                JavaField field = lookupField(cpi, opcode);
                if (field == null) {
                    handleStoreFieldResolutionError(cpi, opcode);
                } else {
                    genPutField(field);
                }
            } catch (LinkageError e) {
                if (handleStoreFieldResolutionError(cpi, opcode)) {
                    return;
                }
                throw e;
            }
        }

        @Override
        protected void genGetStatic(int cpi, int opcode) {
            try {
                super.genGetStatic(cpi, opcode);
            } catch (LinkageError e) {
                handleLoadFieldResolutionError(e, cpi, opcode, null);
            }
        }

        @Override
        protected void genPutStatic(int cpi, int opcode) {
            try {
                JavaField field = lookupField(cpi, opcode);
                if (field == null) {
                    handleStoreFieldResolutionError(cpi, opcode);
                } else {
                    genPutStatic(field);
                }
            } catch (LinkageError e) {
                if (handleStoreFieldResolutionError(cpi, opcode)) {
                    return;
                }
                throw e;
            }
        }

        @Override
        protected void emitCheckForInvokeSuperSpecial(ValueNode[] args) {
            /* Not implemented in SVM (GR-4854) */
        }

        @Override
        protected boolean canInlinePartialIntrinsicExit() {
            return false;
        }

        @Override
        protected void genIf(ValueNode x, Condition cond, ValueNode y) {
            if ((x.getStackKind() == JavaKind.Object && y.getStackKind() == getWordTypes().getWordKind()) ||
                            (x.getStackKind() == getWordTypes().getWordKind() && y.getStackKind() == JavaKind.Object)) {
                throw UserError.abort("Should not compare Word to Object in condition at " + method.format("%H.%n(%p)") + " in " + method.asStackTraceElement(bci()));
            }

            super.genIf(x, cond, y);
        }

        @Override
        public MethodCallTargetNode createMethodCallTarget(InvokeKind invokeKind, ResolvedJavaMethod targetMethod, ValueNode[] args, StampPair returnStamp, JavaTypeProfile profile) {
            boolean isStatic = targetMethod.isStatic();
            if (!isStatic) {
                checkWordType(args[0], targetMethod.getDeclaringClass(), "call receiver");
            }
            for (int i = 0; i < targetMethod.getSignature().getParameterCount(false); i++) {
                checkWordType(args[i + (isStatic ? 0 : 1)], targetMethod.getSignature().getParameterType(i, null), "call argument");
            }

            return super.createMethodCallTarget(invokeKind, targetMethod, args, returnStamp, profile);
        }

        @Override
        protected void genReturn(ValueNode returnVal, JavaKind returnKind) {
            checkWordType(returnVal, method.getSignature().getReturnType(null), "return value");

            super.genReturn(returnVal, returnKind);
        }

        private void checkWordType(ValueNode value, JavaType expectedType, String reason) {
            if (expectedType.getJavaKind() == JavaKind.Object) {
                boolean isWordTypeExpected = getWordTypes().isWord(expectedType);
                boolean isWordValue = value.getStackKind() == getWordTypes().getWordKind();

                if (isWordTypeExpected && !isWordValue) {
                    throw UserError.abort("Expected Word but got Object for " + reason + " at " + method.format("%H.%n(%p)") + " in " + method.asStackTraceElement(bci()));
                } else if (!isWordTypeExpected && isWordValue) {
                    throw UserError.abort("Expected Object but got Word for " + reason + " at " + method.format("%H.%n(%p)") + " in " + method.asStackTraceElement(bci()));
                }
            }
        }

        @Override
        protected ValueNode emitExplicitExceptions(ValueNode receiver, ValueNode outOfBoundsIndex) {
            /*
             * Normally, Substrate VM does not allow to catch implicit exceptions (exception thrown
             * by bytecodes other than "throw") from within the same method. However, some JDK code
             * relies on that behavior for field and array accesses. If there is an exception
             * handler explicitly for such an exception type, we generate explicit checks that allow
             * a catch from within the same method. If the catch is for a base class such as
             * Throwable, we still do not catch it.
             */
            for (ExceptionHandler handler : method.getExceptionHandlers()) {
                if (handler.getStartBCI() <= bci() && bci() < handler.getEndBCI()) {
                    for (ResolvedJavaType explicitExceptionType : explicitExceptionTypes) {
                        if (explicitExceptionType.equals(handler.getCatchType())) {
                            return super.emitExplicitExceptions(receiver, outOfBoundsIndex);
                        }
                    }
                }
            }

            // Nothing to do, we do not want explicit exception checks during graph building.
            return receiver;
        }

        @Override
        protected ValueNode emitExplicitExceptions(ValueNode receiver) {
            if (StampTool.isPointerNonNull(receiver) || !needsExplicitException()) {
                return receiver;
            } else {
                /*
                 * Normally, Substrate VM does not allow to catch implicit exceptions (exception
                 * thrown by bytecodes other than "throw") from within the same method. However,
                 * some JDK code relies on that behavior for null checks. If there is an exception
                 * handler explicitly for a NullPointerException, we generate explicit checks that
                 * allow a catch from within the same method. If the catch is for a base class such
                 * as Throwable, we still do not catch it.
                 */
                for (ExceptionHandler handler : method.getExceptionHandlers()) {
                    if (handler.getStartBCI() <= bci() && bci() < handler.getEndBCI()) {
                        if (explicitNullCheck.equals(handler.getCatchType())) {
                            return super.emitExplicitNullCheck(receiver);
                        }
                    }
                }

                // Nothing to do, we do not want explicit exception checks during graph building.
                return receiver;
            }
        }

        @Override
        protected void genMonitorEnter(ValueNode x, int bci) {
            super.genMonitorEnter(interceptDynamicHub(x), bci);
        }

        @Override
        protected void genMonitorExit(ValueNode x, ValueNode escapedReturnValue, int bci) {
            super.genMonitorExit(interceptDynamicHub(x), escapedReturnValue, bci);
        }

        /**
         * See {@link ClassSynchronizationSupport} for an explanation of this method. This method
         * additionally replaces the inputs that have deopt proxies with a constant which leaves a
         * dangling deopt proxy. This is done for two reasons: 1) It is terribly hard to go
         * backwards and fix all Phi and DeoptProxy nodes to a new value. 2) This value is always a
         * constant that does not participate in constant folding so it should not affect
         * deoptimization.
         */
        private ValueNode interceptDynamicHub(ValueNode x) {
            ValueNode node = GraphUtil.originalValue(x);
            if (node.isConstant()) {
                Object oldTarget = SubstrateObjectConstant.asObject(node.asConstant());
                if (oldTarget instanceof DynamicHub) {
                    JavaConstant newTarget = SubstrateObjectConstant.forObject(ClassSynchronizationSupport.synchronizationTarget((DynamicHub) oldTarget));
                    return ConstantNode.forConstant(newTarget, metaAccess, graph);
                }
            }
            return x;
        }

        @Override
        protected IntrinsicGuard guardIntrinsic(ValueNode[] args, ResolvedJavaMethod targetMethod, InvocationPluginReceiver pluginReceiver) {
            /* Currently not supported on Substrate VM, because we do not support LoadMethodNode. */
            return null;
        }

        @Override
        public void notifyReplacedCall(ResolvedJavaMethod targetMethod, ConstantNode node) {
            JavaConstant constant = node.asJavaConstant();
            if (metaAccess instanceof AnalysisMetaAccess && constant.getJavaKind() == JavaKind.Object && constant.isNonNull()) {
                SubstrateObjectConstant sValue = (SubstrateObjectConstant) node.asJavaConstant();
                sValue.setRoot(targetMethod);
            }
        }
    }
}
