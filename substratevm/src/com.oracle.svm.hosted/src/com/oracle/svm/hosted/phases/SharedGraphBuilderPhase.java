/*
 * Copyright (c) 2013, 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.invoke.LambdaConversionException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;

import com.oracle.graal.pointsto.constraints.TypeInstantiationException;
import com.oracle.graal.pointsto.constraints.UnresolvedElementException;
import com.oracle.graal.pointsto.constraints.UnsupportedFeatureException;
import com.oracle.graal.pointsto.infrastructure.OriginalClassProvider;
import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.svm.common.meta.MultiMethod;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.deopt.DeoptimizationSupport;
import com.oracle.svm.core.graal.nodes.DeoptEntryBeginNode;
import com.oracle.svm.core.graal.nodes.DeoptEntryNode;
import com.oracle.svm.core.graal.nodes.DeoptEntrySupport;
import com.oracle.svm.core.graal.nodes.DeoptProxyAnchorNode;
import com.oracle.svm.core.graal.nodes.LoweredDeadEndNode;
import com.oracle.svm.core.nodes.SubstrateMethodCallTargetNode;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.core.util.UserError.UserException;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.ExceptionSynthesizer;
import com.oracle.svm.hosted.LinkAtBuildTimeSupport;
import com.oracle.svm.hosted.code.FactoryMethodSupport;
import com.oracle.svm.hosted.code.SubstrateCompilationDirectives;
import com.oracle.svm.hosted.nodes.DeoptProxyNode;
import com.oracle.svm.util.ReflectionUtil;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.core.common.calc.Condition;
import jdk.graal.compiler.core.common.type.StampPair;
import jdk.graal.compiler.graph.Node.NodeIntrinsic;
import jdk.graal.compiler.java.BciBlockMapping;
import jdk.graal.compiler.java.BytecodeParser;
import jdk.graal.compiler.java.FrameStateBuilder;
import jdk.graal.compiler.java.GraphBuilderPhase;
import jdk.graal.compiler.nodes.AbstractBeginNode;
import jdk.graal.compiler.nodes.BeginNode;
import jdk.graal.compiler.nodes.CallTargetNode.InvokeKind;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.UnreachableBeginNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.IsNullNode;
import jdk.graal.compiler.nodes.extended.BranchProbabilityNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GeneratedInvocationPlugin;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderPlugin;
import jdk.graal.compiler.nodes.graphbuilderconf.IntrinsicContext;
import jdk.graal.compiler.nodes.java.ExceptionObjectNode;
import jdk.graal.compiler.nodes.java.MethodCallTargetNode;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.phases.OptimisticOptimizations;
import jdk.graal.compiler.replacements.SnippetTemplate;
import jdk.graal.compiler.word.WordTypes;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaField;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaMethod;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.JavaTypeProfile;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

public abstract class SharedGraphBuilderPhase extends GraphBuilderPhase.Instance {
    final WordTypes wordTypes;

    public SharedGraphBuilderPhase(CoreProviders providers, GraphBuilderConfiguration graphBuilderConfig, OptimisticOptimizations optimisticOpts, IntrinsicContext initialIntrinsicContext,
                    WordTypes wordTypes) {
        super(providers, graphBuilderConfig, optimisticOpts, initialIntrinsicContext);
        this.wordTypes = wordTypes;
    }

    @Override
    protected void run(StructuredGraph graph) {
        super.run(graph);
        assert wordTypes == null || wordTypes.ensureGraphContainsNoWordTypeReferences(graph);
    }

    public abstract static class SharedBytecodeParser extends BytecodeParser {

        private int currentDeoptIndex;

        private final boolean explicitExceptionEdges;
        private final boolean linkAtBuildTime;

        protected SharedBytecodeParser(GraphBuilderPhase.Instance graphBuilderInstance, StructuredGraph graph, BytecodeParser parent, ResolvedJavaMethod method, int entryBCI,
                        IntrinsicContext intrinsicContext, boolean explicitExceptionEdges) {
            this(graphBuilderInstance, graph, parent, method, entryBCI, intrinsicContext, explicitExceptionEdges, LinkAtBuildTimeSupport.singleton().linkAtBuildTime(method.getDeclaringClass()));
        }

        protected SharedBytecodeParser(GraphBuilderPhase.Instance graphBuilderInstance, StructuredGraph graph, BytecodeParser parent, ResolvedJavaMethod method, int entryBCI,
                        IntrinsicContext intrinsicContext, boolean explicitExceptionEdges, boolean linkAtBuildTime) {
            super(graphBuilderInstance, graph, parent, method, entryBCI, intrinsicContext);
            this.explicitExceptionEdges = explicitExceptionEdges;
            this.linkAtBuildTime = linkAtBuildTime;
        }

        @Override
        protected BciBlockMapping generateBlockMap() {
            if (isDeoptimizationEnabled() && isMethodDeoptTarget()) {
                /*
                 * Need to add blocks representing where deoptimization entrypoint nodes will be
                 * inserted.
                 */
                return DeoptimizationTargetBciBlockMapping.create(stream, code, options, graph.getDebug(), false);
            } else {
                return BciBlockMapping.create(stream, code, options, graph.getDebug(), asyncExceptionLiveness());
            }
        }

        protected boolean shouldVerifyFrameStates() {
            return false;
        }

        @Override
        protected void build(FixedWithNextNode startInstruction, FrameStateBuilder startFrameState) {
            if (!shouldVerifyFrameStates()) {
                startFrameState.disableStateVerification();
            }

            super.build(startInstruction, startFrameState);

            if (isMethodDeoptTarget()) {
                /*
                 * All DeoptProxyNodes should be valid.
                 */
                for (DeoptProxyNode deoptProxy : graph.getNodes(DeoptProxyNode.TYPE)) {
                    assert deoptProxy.hasProxyPoint();
                }
            }
        }

        @Override
        protected RuntimeException throwParserError(Throwable e) {
            if (e instanceof UserException) {
                throw (UserException) e;
            }
            if (e instanceof UnsupportedFeatureException) {
                throw (UnsupportedFeatureException) e;
            }
            throw super.throwParserError(e);
        }

        private WordTypes getWordTypes() {
            return ((SharedGraphBuilderPhase) getGraphBuilderInstance()).wordTypes;
        }

        private boolean checkWordTypes() {
            return getWordTypes() != null;
        }

        /**
         * {@link Fold} and {@link NodeIntrinsic} can be deferred during parsing/decoding. Only by
         * the end of {@linkplain SnippetTemplate#instantiate Snippet instantiation} do they need to
         * have been processed.
         *
         * This is how SVM handles snippets. They are parsed with plugins disabled and then encoded
         * and stored in the image. When the snippet is needed at runtime the graph is decoded and
         * the plugins are run during the decoding process. If they aren't handled at this point
         * then they will never be handled.
         */
        @Override
        public boolean canDeferPlugin(GeneratedInvocationPlugin plugin) {
            return plugin.getSource().equals(Fold.class) || plugin.getSource().equals(NodeIntrinsic.class);
        }

        @Override
        protected JavaMethod lookupMethodInPool(int cpi, int opcode) {
            JavaMethod result = super.lookupMethodInPool(cpi, opcode);
            if (result == null) {
                throw VMError.shouldNotReachHere("Discovered an unresolved callee while parsing " + method.asStackTraceElement(bci()) + '.');
            }
            return result;
        }

        @Override
        protected Object lookupConstant(int cpi, int opcode, boolean allowBootstrapMethodInvocation) {
            try {
                // Native Image forces bootstrap method invocation at build time
                // until support has been added for doing the invocation at runtime (GR-45806)
                return super.lookupConstant(cpi, opcode, true);
            } catch (BootstrapMethodError | IncompatibleClassChangeError | IllegalArgumentException ex) {
                if (linkAtBuildTime) {
                    reportUnresolvedElement("constant", method.format("%H.%n(%P)"), ex);
                } else {
                    replaceWithThrowingAtRuntime(this, ex);
                }
                return ex;
            }
        }

        /**
         * Native image can suffer high contention when synchronizing resolution and initialization
         * of a type referenced by a constant pool entry. Such synchronization should be unnecessary
         * for native-image.
         */
        @Override
        protected Object loadReferenceTypeLock() {
            return null;
        }

        @Override
        protected void maybeEagerlyResolve(int cpi, int bytecode) {
            try {
                super.maybeEagerlyResolve(cpi, bytecode);
            } catch (UnresolvedElementException e) {
                if (e.getCause() instanceof LambdaConversionException || e.getCause() instanceof LinkageError || e.getCause() instanceof IllegalAccessError) {
                    /*
                     * Ignore LinkageError, LambdaConversionException or IllegalAccessError if
                     * thrown from eager resolution attempt. This is usually followed by a call to
                     * ConstantPool.lookupType() which should return an UnresolvedJavaType which we
                     * know how to deal with.
                     */
                } else {
                    throw e;
                }
            }
        }

        @Override
        protected JavaType maybeEagerlyResolve(JavaType type, ResolvedJavaType accessingClass) {
            try {
                return super.maybeEagerlyResolve(type, accessingClass);
            } catch (LinkageError e) {
                /*
                 * Type resolution fails if the type is missing or has an incompatible change. Just
                 * erase the type by returning the Object type. This is the same handling as in
                 * WrappedConstantPool, which is not triggering when parsing is done with the
                 * HotSpot universe instead of the AnalysisUniverse.
                 */
                return getMetaAccess().lookupJavaType(Object.class);
            }
        }

        @Override
        protected void handleIllegalNewInstance(JavaType type) {
            /*
             * If linkAtBuildTime was set for type, report the error during image building,
             * otherwise defer the error reporting to runtime.
             */
            if (linkAtBuildTime) {
                String message = "Cannot instantiate " + type.toJavaName() + ". " +
                                LinkAtBuildTimeSupport.singleton().errorMessageFor(method.getDeclaringClass());
                throw new TypeInstantiationException(message);
            } else {
                ExceptionSynthesizer.throwException(this, InstantiationError.class, type.toJavaName());
            }
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
            // The INSTANCEOF byte code refers to a type that could not be resolved.
            // INSTANCEOF must not throw an exception if the object is null.
            BeginNode nullObj = graph.add(new BeginNode());
            BeginNode nonNullObj = graph.add(new BeginNode());
            append(new IfNode(graph.addOrUniqueWithInputs(IsNullNode.create(object)),
                            nullObj, nonNullObj, BranchProbabilityNode.NOT_FREQUENT_PROFILE));

            // Case where the object is not null, and type could not be resolved: Throw an
            // exception.
            lastInstr = nonNullObj;
            handleUnresolvedType(type);

            // Case where the object is null: INSTANCEOF does not care about the type.
            // Push zero to the byte code stack, then continue running normally.
            lastInstr = nullObj;
            frameState.push(JavaKind.Int, appendConstant(JavaConstant.INT_0));
        }

        @Override
        protected void handleUnresolvedCheckCast(JavaType type, ValueNode object) {
            // The CHECKCAST byte code refers to a type that could not be resolved.
            // CHECKCAST must throw an exception if, and only if, the object is not null.
            BeginNode nullObj = graph.add(new BeginNode());
            BeginNode nonNullObj = graph.add(new BeginNode());
            append(new IfNode(graph.addOrUniqueWithInputs(IsNullNode.create(object)),
                            nullObj, nonNullObj, BranchProbabilityNode.NOT_FREQUENT_PROFILE));

            // Case where the object is not null, and type could not be resolved: Throw an
            // exception.
            lastInstr = nonNullObj;
            handleUnresolvedType(type);

            // Case where the object is null: CHECKCAST does not care about the type.
            // Push "null" to the byte code stack, then continue running normally.
            lastInstr = nullObj;
            frameState.push(JavaKind.Object, appendConstant(JavaConstant.NULL_POINTER));
        }

        @Override
        protected void handleUnresolvedLoadConstant(JavaType unresolvedType) {
            handleUnresolvedType(unresolvedType);
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

        /**
         * This method is used to delay errors from image build-time to run-time. It does so by
         * invoking a synthesized method that throws an instance like the one given as throwable in
         * the given GraphBuilderContext. If the given throwable has a non-null cause, a
         * cause-instance of the same type with a proper cause-message is created first that is then
         * passed to the method that creates and throws the outer throwable-instance.
         */
        public static <T extends Throwable> void replaceWithThrowingAtRuntime(SharedBytecodeParser b, T throwable) {
            Throwable cause = throwable.getCause();
            if (cause != null) {
                var metaAccess = (AnalysisMetaAccess) b.getMetaAccess();
                /* Invoke method that creates a cause-instance with cause-message */
                var causeCtor = ReflectionUtil.lookupConstructor(cause.getClass(), String.class);
                ResolvedJavaMethod causeCtorMethod = FactoryMethodSupport.singleton().lookup(metaAccess, metaAccess.lookupJavaMethod(causeCtor), false);
                ValueNode causeMessageNode = ConstantNode.forConstant(b.getConstantReflection().forString(cause.getMessage()), metaAccess, b.getGraph());
                Invoke causeCtorInvoke = (Invoke) b.appendInvoke(InvokeKind.Static, causeCtorMethod, new ValueNode[]{causeMessageNode}, null);
                /*
                 * Invoke method that creates and throws throwable-instance with message and cause
                 */
                var errorCtor = ReflectionUtil.lookupConstructor(throwable.getClass(), String.class, Throwable.class);
                ResolvedJavaMethod throwingMethod = FactoryMethodSupport.singleton().lookup(metaAccess, metaAccess.lookupJavaMethod(errorCtor), true);
                ValueNode messageNode = ConstantNode.forConstant(b.getConstantReflection().forString(throwable.getMessage()), metaAccess, b.getGraph());
                /*
                 * As this invoke will always throw, its state after will not respect the expected
                 * stack effect.
                 */
                boolean verifyStates = b.getFrameStateBuilder().disableStateVerification();
                b.appendInvoke(InvokeKind.Static, throwingMethod, new ValueNode[]{messageNode, causeCtorInvoke.asNode()}, null);
                b.getFrameStateBuilder().setStateVerification(verifyStates);
                b.add(new LoweredDeadEndNode());
            } else {
                replaceWithThrowingAtRuntime(b, throwable.getClass(), throwable.getMessage());
            }
        }

        /**
         * This method is used to delay errors from image build-time to run-time. It does so by
         * invoking a synthesized method that creates an instance of type throwableClass with
         * throwableMessage as argument and then throws that instance in the given
         * GraphBuilderContext.
         */
        public static void replaceWithThrowingAtRuntime(SharedBytecodeParser b, Class<? extends Throwable> throwableClass, String throwableMessage) {
            /*
             * This method is currently not able to replace
             * ExceptionSynthesizer.throwException(GraphBuilderContext, Method, String) because
             * there are places where GraphBuilderContext.getMetaAccess() does not contain a
             * UniverseMetaAccess (e.g. in case of ParsingReason.EarlyClassInitializerAnalysis). If
             * we can access the ParsingReason in here we will be able to get rid of throwException.
             */
            var errorCtor = ReflectionUtil.lookupConstructor(throwableClass, String.class);
            var metaAccess = (AnalysisMetaAccess) b.getMetaAccess();
            ResolvedJavaMethod throwingMethod = FactoryMethodSupport.singleton().lookup(metaAccess, metaAccess.lookupJavaMethod(errorCtor), true);
            ValueNode messageNode = ConstantNode.forConstant(b.getConstantReflection().forString(throwableMessage), b.getMetaAccess(), b.getGraph());
            boolean verifyStates = b.getFrameStateBuilder().disableStateVerification();
            b.appendInvoke(InvokeKind.Static, throwingMethod, new ValueNode[]{messageNode}, null);
            b.getFrameStateBuilder().setStateVerification(verifyStates);
            b.add(new LoweredDeadEndNode());
        }

        private void handleUnresolvedType(JavaType type) {
            /*
             * If linkAtBuildTime was set for type, report the error during image building,
             * otherwise defer the error reporting to runtime.
             */
            if (linkAtBuildTime) {
                reportUnresolvedElement("type", type.toJavaName());
            } else {
                ExceptionSynthesizer.throwException(this, NoClassDefFoundError.class, type.toJavaName());
            }
        }

        private void handleUnresolvedField(JavaField field) {
            JavaType declaringClass = field.getDeclaringClass();
            if (!typeIsResolved(declaringClass)) {
                /* The field could not be resolved because its declaring class is missing. */
                handleUnresolvedType(declaringClass);
            } else {
                /*
                 * If linkAtBuildTime was set for type, report the error during image building,
                 * otherwise defer the error reporting to runtime.
                 */
                if (linkAtBuildTime) {
                    reportUnresolvedElement("field", field.format("%H.%n"));
                } else {
                    ExceptionSynthesizer.throwException(this, NoSuchFieldError.class, field.format("%H.%n"));
                }
            }
        }

        private void handleUnresolvedMethod(JavaMethod javaMethod) {
            JavaType declaringClass = javaMethod.getDeclaringClass();
            if (!typeIsResolved(declaringClass)) {
                /* The method could not be resolved because its declaring class is missing. */
                handleUnresolvedType(declaringClass);
            } else {
                /*
                 * If linkAtBuildTime was set for type, report the error during image building,
                 * otherwise defer the error reporting to runtime.
                 */
                if (linkAtBuildTime) {
                    reportUnresolvedElement("method", javaMethod.format("%H.%n(%P)"));
                } else {
                    ExceptionSynthesizer.throwException(this, findResolutionError((ResolvedJavaType) declaringClass, javaMethod), javaMethod.format("%H.%n(%P)"));
                }
            }
        }

        /**
         * Finding the correct exception that needs to be thrown at run time is a bit tricky, since
         * JVMCI does not report that information back when method resolution fails. We need to look
         * down the class hierarchy to see if there would be an appropriate method with a matching
         * signature which is just not accessible.
         *
         * We do all the method lookups (to search for a method with the same signature as
         * searchMethod) using reflection and not JVMCI because the lookup can throw all sorts of
         * errors, and we want to ignore the errors without any possible side effect on AnalysisType
         * and AnalysisMethod.
         */
        private static Class<? extends IncompatibleClassChangeError> findResolutionError(ResolvedJavaType declaringType, JavaMethod searchMethod) {
            Class<?>[] searchSignature = signatureToClasses(searchMethod);
            Class<?> searchReturnType = null;
            if (searchMethod.getSignature().getReturnType(null) instanceof ResolvedJavaType) {
                searchReturnType = OriginalClassProvider.getJavaClass((ResolvedJavaType) searchMethod.getSignature().getReturnType(null));
            }

            Class<?> declaringClass = OriginalClassProvider.getJavaClass(declaringType);
            for (Class<?> cur = declaringClass; cur != null; cur = cur.getSuperclass()) {
                Executable[] methods = null;
                try {
                    if (searchMethod.getName().equals("<init>")) {
                        methods = cur.getDeclaredConstructors();
                    } else {
                        methods = cur.getDeclaredMethods();
                    }
                } catch (Throwable ignored) {
                    /*
                     * A linkage error was thrown, or something else random is wrong with the class
                     * files. Ignore this class.
                     */
                }
                if (methods != null) {
                    for (Executable method : methods) {
                        if (Arrays.equals(searchSignature, method.getParameterTypes()) &&
                                        (method instanceof Constructor || (searchMethod.getName().equals(method.getName()) && searchReturnType == ((Method) method).getReturnType()))) {
                            if (Modifier.isAbstract(method.getModifiers())) {
                                return AbstractMethodError.class;
                            } else {
                                return IllegalAccessError.class;
                            }
                        }
                    }
                }
                if (searchMethod.getName().equals("<init>")) {
                    /* For constructors, do not search in superclasses. */
                    break;
                }
            }
            return NoSuchMethodError.class;
        }

        private static Class<?>[] signatureToClasses(JavaMethod method) {
            int paramCount = method.getSignature().getParameterCount(false);
            Class<?>[] result = new Class<?>[paramCount];
            for (int i = 0; i < paramCount; i++) {
                JavaType parameterType = method.getSignature().getParameterType(0, null);
                if (parameterType instanceof ResolvedJavaType) {
                    result[i] = OriginalClassProvider.getJavaClass((ResolvedJavaType) parameterType);
                }
            }
            return result;
        }

        private void reportUnresolvedElement(String elementKind, String elementAsString) {
            reportUnresolvedElement(elementKind, elementAsString, null);
        }

        private void reportUnresolvedElement(String elementKind, String elementAsString, Throwable cause) {
            String message = "Discovered unresolved " + elementKind + " during parsing: " + elementAsString + ". " +
                            LinkAtBuildTimeSupport.singleton().errorMessageFor(method.getDeclaringClass());
            throw new UnresolvedElementException(message, cause);
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
            if (checkWordTypes()) {
                if ((x.getStackKind() == JavaKind.Object && y.getStackKind() == getWordTypes().getWordKind()) ||
                                (x.getStackKind() == getWordTypes().getWordKind() && y.getStackKind() == JavaKind.Object)) {
                    throw UserError.abort("Should not compare Word to Object in condition at %s in %s", method, method.asStackTraceElement(bci()));
                }
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

            return new SubstrateMethodCallTargetNode(invokeKind, targetMethod, args, returnStamp);
        }

        @Override
        protected void genReturn(ValueNode returnVal, JavaKind returnKind) {
            checkWordType(returnVal, method.getSignature().getReturnType(null), "return value");

            super.genReturn(returnVal, returnKind);
        }

        private void checkWordType(ValueNode value, JavaType expectedType, String reason) {
            if (expectedType.getJavaKind() == JavaKind.Object && checkWordTypes()) {
                boolean isWordTypeExpected = getWordTypes().isWord(expectedType);
                boolean isWordValue = value.getStackKind() == getWordTypes().getWordKind();

                if (isWordTypeExpected && !isWordValue) {
                    throw UserError.abort("Expected Word but got Object for %s in %s", reason, method.asStackTraceElement(bci()));
                } else if (!isWordTypeExpected && isWordValue) {
                    throw UserError.abort("Expected Object but got Word for %s in %s. One possible cause for this error is when word values are passed into lambdas as parameters " +
                                    "or from variables in an enclosing scope, which is not supported, but can be solved by instead using explicit classes (including anonymous classes).",
                                    reason, method.asStackTraceElement(bci()));
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
        protected boolean needsIncompatibleClassChangeErrorCheck() {
            /*
             * Note that the explicit check for incompatible class changes is necessary even when
             * explicit exception edges for other exception are not required. We have no mechanism
             * to do the check implicitly as part of interface calls. Interface calls are vtable
             * calls both in AOT compiled code and JIT compiled code.
             */
            return !parsingIntrinsic();
        }

        @Override
        protected boolean needsExplicitIncompatibleClassChangeError() {
            /*
             * For AOT compilation, incompatible class change checks must be BytecodeExceptionNode.
             * For JIT compilation at image run time, they must be guards.
             */
            return needsExplicitException();
        }

        @Override
        public boolean isPluginEnabled(GraphBuilderPlugin plugin) {
            return true;
        }

        protected static boolean isDeoptimizationEnabled() {
            boolean result = DeoptimizationSupport.enabled();
            assert !(result && SubstrateUtil.isBuildingLibgraal()) : "Deoptimization support should not be enabled while building libgraal";
            return result;
        }

        protected final boolean isMethodDeoptTarget() {
            return MultiMethod.isDeoptTarget(method);
        }

        @Override
        protected boolean asyncExceptionLiveness() {
            /*
             * Only methods which can deoptimize need to consider live locals from asynchronous
             * exception handlers.
             */
            if (method instanceof MultiMethod) {
                return ((MultiMethod) method).getMultiMethodKey() == SubstrateCompilationDirectives.RUNTIME_COMPILED_METHOD;
            }

            /*
             * If deoptimization is enabled, then must assume that any method can deoptimize at any
             * point while throwing an exception.
             */
            return isDeoptimizationEnabled();
        }

        @Override
        protected void clearNonLiveLocalsAtTargetCreation(BciBlockMapping.BciBlock block, FrameStateBuilder state) {
            /*
             * In order to match potential DeoptEntryNodes, within runtime compiled code it is not
             * possible to clear non-live locals at the start of an exception dispatch block if
             * deoptimizations can be present, as exception dispatch blocks have the same deopt bci
             * as the exception.
             */
            if ((!(isDeoptimizationEnabled() && block instanceof BciBlockMapping.ExceptionDispatchBlock)) || isMethodDeoptTarget()) {
                super.clearNonLiveLocalsAtTargetCreation(block, state);
            }
        }

        @Override
        protected void clearNonLiveLocalsAtLoopExitCreation(BciBlockMapping.BciBlock block, FrameStateBuilder state) {
            /*
             * In order to match potential DeoptEntryNodes, within runtime compiled code it is not
             * possible to clear non-live locals when deoptimizations can be present.
             */
            if (!isDeoptimizationEnabled() || isMethodDeoptTarget()) {
                super.clearNonLiveLocalsAtLoopExitCreation(block, state);
            }
        }

        @Override
        protected void createExceptionDispatch(BciBlockMapping.ExceptionDispatchBlock block) {
            if (block instanceof DeoptimizationTargetBciBlockMapping.DeoptEntryInsertionPoint) {
                /*
                 * If this block is an DeoptEntryInsertionPoint, then a DeoptEntry must be inserted.
                 * Afterwards, this block should jump to either the original ExceptionDispatchBlock
                 * or the UnwindBlock if there is no handler.
                 */
                assert block instanceof DeoptimizationTargetBciBlockMapping.DeoptExceptionDispatchBlock;
                insertDeoptNode((DeoptimizationTargetBciBlockMapping.DeoptEntryInsertionPoint) block);
                List<BciBlockMapping.BciBlock> successors = block.getSuccessors();
                assert successors.size() <= 1;
                BciBlockMapping.BciBlock successor = successors.isEmpty() ? blockMap.getUnwindBlock() : successors.get(0);
                appendGoto(successor);
            } else {
                super.createExceptionDispatch(block);
            }
        }

        @Override
        protected void iterateBytecodesForBlock(BciBlockMapping.BciBlock block) {
            if (block instanceof DeoptimizationTargetBciBlockMapping.DeoptEntryInsertionPoint) {
                /*
                 * If this block is an DeoptEntryInsertionPoint, then a DeoptEntry must be inserted.
                 * Afterwards, this block should jump to the original BciBlock.
                 */
                assert block instanceof DeoptimizationTargetBciBlockMapping.DeoptBciBlock;
                assert block.getSuccessors().size() == 1 || block.getSuccessors().size() == 2;
                assert block.getSuccessor(0).isInstructionBlock();
                stream.setBCI(block.getStartBci());
                insertDeoptNode((DeoptimizationTargetBciBlockMapping.DeoptEntryInsertionPoint) block);
                appendGoto(block.getSuccessor(0));
            } else {
                super.iterateBytecodesForBlock(block);
            }
        }

        /**
         * Inserts either a DeoptEntryNode or DeoptProxyAnchorNode into the graph.
         */
        private void insertDeoptNode(DeoptimizationTargetBciBlockMapping.DeoptEntryInsertionPoint deopt) {
            /*
             * Ensuring current frameState matches the expectations of the DeoptEntryInsertionPoint.
             */
            if (deopt instanceof DeoptimizationTargetBciBlockMapping.DeoptBciBlock) {
                assert !frameState.rethrowException();
            } else {
                assert deopt instanceof DeoptimizationTargetBciBlockMapping.DeoptExceptionDispatchBlock;
                assert frameState.rethrowException();
            }

            int proxifiedInvokeBci = deopt.proxifiedInvokeBci();
            boolean isProxy = deopt.isProxy();
            DeoptEntrySupport deoptNode;
            if (isProxy) {
                deoptNode = graph.add(new DeoptProxyAnchorNode(proxifiedInvokeBci));
            } else {
                boolean proxifysInvoke = deopt.proxifysInvoke();
                deoptNode = graph.add(proxifysInvoke ? DeoptEntryNode.create(proxifiedInvokeBci) : DeoptEntryNode.create());
            }
            FrameState stateAfter = frameState.create(deopt.frameStateBci(), deoptNode);
            deoptNode.setStateAfter(stateAfter);
            if (lastInstr != null) {
                lastInstr.setNext(deoptNode.asFixedNode());
            }

            if (isProxy) {
                lastInstr = (DeoptProxyAnchorNode) deoptNode;
            } else {
                DeoptEntryNode deoptEntryNode = (DeoptEntryNode) deoptNode;
                deoptEntryNode.setNext(graph.add(new DeoptEntryBeginNode()));

                /*
                 * DeoptEntries for positions not during an exception dispatch (rethrowException)
                 * also must be linked to their exception target.
                 */
                if (!deopt.isExceptionDispatch()) {
                    /*
                     * Saving frameState so that different modifications can be made for next() and
                     * exceptionEdge().
                     */
                    FrameStateBuilder originalFrameState = frameState.copy();

                    /* Creating exception object and its state after. */
                    ExceptionObjectNode newExceptionObject = graph.add(new ExceptionObjectNode(getMetaAccess()));
                    frameState.clearStack();
                    frameState.push(JavaKind.Object, newExceptionObject);
                    frameState.setRethrowException(true);
                    int bci = ((DeoptimizationTargetBciBlockMapping.DeoptBciBlock) deopt).getStartBci();
                    newExceptionObject.setStateAfter(frameState.create(bci, newExceptionObject));
                    deoptEntryNode.setExceptionEdge(newExceptionObject);

                    /* Inserting proxies for the exception edge. */
                    insertProxies(newExceptionObject, frameState);

                    /* Linking exception object to exception target. */
                    newExceptionObject.setNext(handleException(newExceptionObject, bci, false));

                    /* Now restoring FrameState so proxies can be inserted for the next() edge. */
                    frameState = originalFrameState;
                } else {
                    /* Otherwise, indicate that the exception edge is not reachable. */
                    AbstractBeginNode newExceptionEdge = graph.add(new UnreachableBeginNode());
                    newExceptionEdge.setNext(graph.add(new LoweredDeadEndNode()));
                    deoptEntryNode.setExceptionEdge(newExceptionEdge);
                }

                /* Correctly setting last instruction. */
                lastInstr = deoptEntryNode.next();
            }

            insertProxies(deoptNode.asFixedNode(), frameState);
        }

        private void insertProxies(FixedNode deoptTarget, FrameStateBuilder state) {
            /*
             * At a deoptimization point we wrap non-constant locals (and java stack elements) with
             * proxy nodes. This is to avoid global value numbering on locals (or derived
             * expressions). The effect is that when a local is accessed after a deoptimization
             * point it is really loaded from its location. This is similar to what happens in the
             * GraphBuilderPhase if entryBCI is set for OSR.
             */
            state.insertProxies(value -> createProxyNode(value, deoptTarget));
            currentDeoptIndex++;
        }

        private ValueNode createProxyNode(ValueNode value, FixedNode deoptTarget) {
            ValueNode v = DeoptProxyNode.create(value, deoptTarget, currentDeoptIndex);
            if (v.graph() != null) {
                return v;
            }
            return graph.addOrUniqueWithInputs(v);
        }

        @Override
        protected boolean forceLoopPhis() {
            return isMethodDeoptTarget() || super.forceLoopPhis();
        }

        @Override
        public boolean allowDeoptInPlugins() {
            return super.allowDeoptInPlugins();
        }
    }
}
