/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.phases;

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
import org.graalvm.compiler.phases.OptimisticOptimizations;
import org.graalvm.compiler.word.WordTypes;

import com.oracle.graal.pointsto.constraints.UnresolvedElementException;
import com.oracle.graal.pointsto.infrastructure.OriginalClassProvider;
import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.svm.core.meta.SubstrateObjectConstant;
import com.oracle.svm.core.option.SubstrateOptionsParser;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.core.util.UserError.UserException;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.ExceptionSynthesizer;
import com.oracle.svm.hosted.HostedConfiguration;
import com.oracle.svm.hosted.NativeImageClassLoader;
import com.oracle.svm.hosted.NativeImageOptions;
import com.oracle.svm.hosted.c.GraalAccess;

import jdk.vm.ci.meta.ConstantReflectionProvider;
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

        private final boolean explicitExceptionEdges;

        protected SharedBytecodeParser(GraphBuilderPhase.Instance graphBuilderInstance, StructuredGraph graph, BytecodeParser parent, ResolvedJavaMethod method, int entryBCI,
                        IntrinsicContext intrinsicContext, boolean explicitExceptionEdges) {
            super(graphBuilderInstance, graph, parent, method, entryBCI, intrinsicContext);
            this.explicitExceptionEdges = explicitExceptionEdges;
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
        protected boolean typeIsResolved(JavaType type) {
            /*
             * There aren't any UnresolvedJavaType objects during the analysis, all missing types,
             * i.e., the types that fail to be loaded, are replaced with a ghost interface, i.e., a
             * marker signifying that the type is missing, which is a ResolvedJavaType.
             */
            /* Test the elemental type. */
            ResolvedJavaType resolvedType = guaranteeResolved(type.getElementalType());
            return !typeIsMissing(resolvedType);
        }

        @Override
        protected void handleUnresolvedNewInstance(JavaType type) {
            handleUnresolvedType(type);
        }

        @Override
        protected void handleUnresolvedNewObjectArray(JavaType type, ValueNode length) {
            handleUnresolvedType(type);
        }

        @Override
        protected void handleUnresolvedNewMultiArray(JavaType type, ValueNode[] dims) {
            handleUnresolvedType(type.getElementalType());
        }

        @Override
        protected void handleUnresolvedInstanceOf(JavaType type, ValueNode object) {
            handleUnresolvedType(type);
        }

        @Override
        protected void handleUnresolvedCheckCast(JavaType type, ValueNode object) {
            handleUnresolvedType(type);
        }

        @Override
        protected void handleUnresolvedLoadConstant(JavaType type) {
            handleUnresolvedType(type);
        }

        @Override
        protected void handleUnresolvedExceptionType(JavaType type) {
            handleUnresolvedType(type);
        }

        @Override
        protected void handleUnresolvedStoreField(JavaField field, ValueNode value, ValueNode receiver) {
            handleUnresolvedField(field);
        }

        @Override
        protected void handleUnresolvedLoadField(JavaField field, ValueNode receiver) {
            handleUnresolvedField(field);
        }

        @Override
        protected void handleUnresolvedInvoke(JavaMethod javaMethod, InvokeKind invokeKind) {
            handleUnresolvedMethod(javaMethod);
        }

        /** Is the type missing from the classpath? */
        private static boolean typeIsMissing(ResolvedJavaType type) {
            return NativeImageClassLoader.classIsMissing(asJavaClass(type));
        }

        /** Unwrap the original java.lang.Class from a ResolvedJavaType. */
        private static Class<?> asJavaClass(ResolvedJavaType type) {
            return OriginalClassProvider.getJavaClass(GraalAccess.getOriginalSnippetReflection(), type);
        }

        /**
         * There shouldn't be any UnresolvedJavaType objects during the analysis, all missing types
         * are replaced with a ghost interface which is a ResolvedJavaType.
         */
        private static ResolvedJavaType guaranteeResolved(JavaType type) {
            if (type instanceof ResolvedJavaType) {
                return (ResolvedJavaType) type;
            }
            throw VMError.shouldNotReachHere("Unresolved type during parsing: " + type);
        }

        private void handleUnresolvedType(JavaType type) {
            ResolvedJavaType targetType = guaranteeResolved(type);
            /* Only a missing type can flow in this method. */
            VMError.guarantee(typeIsMissing(targetType));
            handleMissingType(targetType);
        }

        private void handleUnresolvedField(JavaField field) {
            ResolvedJavaType declaringClass = guaranteeResolved(field.getDeclaringClass());
            if (typeIsMissing(declaringClass)) {
                handleMissingType(declaringClass);
            } else {
                handleMissingField(field);
            }
        }

        private void handleUnresolvedMethod(JavaMethod javaMethod) {
            ResolvedJavaType declaringClass = guaranteeResolved(javaMethod.getDeclaringClass());
            if (typeIsMissing(declaringClass)) {
                handleMissingType(declaringClass);
            } else {
                handleMissingMethod(javaMethod);
            }
        }

        /**
         * If NativeImageOptions.AllowIncompleteClasspath is set defer the error reporting to
         * runtime, otherwise report the error during image building.
         */
        void handleMissingType(ResolvedJavaType type) {
            if (NativeImageOptions.AllowIncompleteClasspath.getValue()) {
                ExceptionSynthesizer.throwNoClassDefFoundError(this, type.toJavaName());
            } else {
                reportUnresolvedElement("type", type.toJavaName());
            }
        }

        /**
         * If NativeImageOptions.AllowIncompleteClasspath is set defer the error reporting to
         * runtime, otherwise report the error during image building.
         */
        private void handleMissingField(JavaField field) {
            if (NativeImageOptions.AllowIncompleteClasspath.getValue()) {
                ExceptionSynthesizer.throwNoSuchFieldError(this, field.format("%H.%n"));
            } else {
                reportUnresolvedElement("field", field.format("%H.%n"));
            }
        }

        /**
         * If NativeImageOptions.AllowIncompleteClasspath is set defer the error reporting to
         * runtime, otherwise report the error during image building.
         */
        private void handleMissingMethod(JavaMethod javaMethod) {
            if (NativeImageOptions.AllowIncompleteClasspath.getValue()) {
                ExceptionSynthesizer.throwNoSuchMethodError(this, javaMethod.format("%H.%n(%P)"));
            } else {
                reportUnresolvedElement("method", javaMethod.format("%H.%n(%P)"));
            }
        }

        private static void reportUnresolvedElement(String elementKind, String elementAsString) {
            String message = "Discovered unresolved " + elementKind + " during parsing: " + elementAsString +
                            ". To diagnose the issue you can use the " +
                            SubstrateOptionsParser.commandArgument(NativeImageOptions.AllowIncompleteClasspath, "+") +
                            " option. The missing " + elementKind + " is then reported at run time when it is accessed the first time.";
            throw new UnresolvedElementException(message);
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
        protected boolean shouldComplementProbability() {
            /*
             * Probabilities from AOT profiles are about canonical conditions as they are coming
             * from Graal IR. That is, they are collected after `BytecodeParser` has done conversion
             * to Graal IR. Unfortunately, `BytecodeParser` assumes that probabilities are about
             * original conditions and loads them before conversion to Graal IR.
             *
             * Therefore, in order to maintain correct probabilities we need to prevent
             * `BytecodeParser` from complementing probability during transformations such as
             * negation of a condition, or elimination of logical negation.
             */
            return !HostedConfiguration.instance().isUsingAOTProfiles();
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
        protected boolean needsExplicitNullCheckException(ValueNode object) {
            return needsExplicitException() && object.getStackKind() == JavaKind.Object;
        }

        @Override
        protected boolean needsExplicitStoreCheckException(ValueNode array, ValueNode value) {
            return needsExplicitException() && value.getStackKind() == JavaKind.Object;
        }

        @Override
        public boolean needsExplicitException() {
            return explicitExceptionEdges && !parsingIntrinsic();
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
