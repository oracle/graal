/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.api.bytecode.test.error_tests;

import java.util.Set;

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleStackTraceElement;
import com.oracle.truffle.api.bytecode.BytecodeNode;
import com.oracle.truffle.api.bytecode.BytecodeRootNode;
import com.oracle.truffle.api.bytecode.BytecodeRootNodes;
import com.oracle.truffle.api.bytecode.EpilogExceptional;
import com.oracle.truffle.api.bytecode.EpilogReturn;
import com.oracle.truffle.api.bytecode.GenerateBytecode;
import com.oracle.truffle.api.bytecode.Instrumentation;
import com.oracle.truffle.api.bytecode.Operation;
import com.oracle.truffle.api.bytecode.OperationProxy;
import com.oracle.truffle.api.bytecode.Prolog;
import com.oracle.truffle.api.bytecode.ShortCircuitOperation;
import com.oracle.truffle.api.bytecode.ShortCircuitOperation.Operator;
import com.oracle.truffle.api.bytecode.Variadic;
import com.oracle.truffle.api.bytecode.test.error_tests.subpackage.NestedNodeOperationProxy;
import com.oracle.truffle.api.bytecode.test.error_tests.subpackage.NonPublicGuardExpressionOperationProxy;
import com.oracle.truffle.api.bytecode.test.error_tests.subpackage.NonPublicSpecializationOperationProxy;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateAOT;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystem;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.ProvidedTags;
import com.oracle.truffle.api.instrumentation.StandardTags.RootBodyTag;
import com.oracle.truffle.api.instrumentation.StandardTags.RootTag;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;

@SuppressWarnings({"unused", "static-method", "truffle"})
public class ErrorTests {
    @ExpectError("Bytecode DSL class must be declared abstract.")
    @GenerateBytecode(languageClass = ErrorLanguage.class)
    public static class MustBeDeclaredAbstract extends RootNode implements BytecodeRootNode {
        protected MustBeDeclaredAbstract(ErrorLanguage language, FrameDescriptor frameDescriptor) {
            super(language, frameDescriptor);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return null;
        }

        public String dump() {
            return null;
        }

        public SourceSection findSourceSectionAtBci(int bci) {
            return null;
        }

        public InstrumentableNode materializeInstrumentTree(Set<Class<? extends Tag>> materializedTags) {
            return null;
        }
    }

    @ExpectError("Bytecode DSL class must directly or indirectly subclass RootNode.")
    @GenerateBytecode(languageClass = ErrorLanguage.class)
    public abstract static class MustBeSubclassOfRootNode implements BytecodeRootNode {
        protected MustBeSubclassOfRootNode(ErrorLanguage language, FrameDescriptor frameDescriptor) {
        }
    }

    @ExpectError("Bytecode DSL class must directly or indirectly implement BytecodeRootNode.")
    @GenerateBytecode(languageClass = ErrorLanguage.class)
    public abstract static class MustImplementBytecodeRootNode extends RootNode {
        protected MustImplementBytecodeRootNode(ErrorLanguage language, FrameDescriptor frameDescriptor) {
            super(language, frameDescriptor);
        }
    }

    @ExpectError("Bytecode DSL class must be public or package-private.")
    @GenerateBytecode(languageClass = ErrorLanguage.class)
    private abstract static class MustBePublic extends RootNode implements BytecodeRootNode {
        protected MustBePublic(ErrorLanguage language, FrameDescriptor frameDescriptor) {
            super(language, frameDescriptor);
        }
    }

    @ExpectError("Bytecode DSL class must be static if it is a nested class.")
    @GenerateBytecode(languageClass = ErrorLanguage.class)
    public abstract class MustBeStatic extends RootNode implements BytecodeRootNode {
        protected MustBeStatic(ErrorLanguage language, FrameDescriptor frameDescriptor) {
            super(language, frameDescriptor);
        }
    }

    @ExpectError({
                    "Bytecode DSL always generates a cached interpreter.",
                    "Set GenerateBytecode#enableUncachedInterpreter to generate an uncached interpreter.",
                    "Bytecode DSL interpreters do not support the GenerateAOT annotation.",
                    "Bytecode DSL interpreters do not support the GenerateInline annotation."
    })
    @GenerateBytecode(languageClass = ErrorLanguage.class)
    @GenerateCached
    @GenerateUncached
    @GenerateAOT
    @GenerateInline
    public abstract static class BadAnnotations extends RootNode implements BytecodeRootNode {
        protected BadAnnotations(ErrorLanguage language, FrameDescriptor frameDescriptor) {
            super(language, frameDescriptor);
        }
    }

    @ExpectError("Bytecode DSL class should declare a constructor that has signature (ErrorLanguage, FrameDescriptor) or (ErrorLanguage, FrameDescriptor.Builder). The constructor should be visible to subclasses.")
    @GenerateBytecode(languageClass = ErrorLanguage.class)
    public abstract static class HiddenConstructor extends RootNode implements BytecodeRootNode {
        private HiddenConstructor(TruffleLanguage<?> language, FrameDescriptor descriptor) {
            super(language, descriptor);
        }
    }

    @ExpectError("Bytecode DSL class should declare a constructor that has signature (ErrorLanguage, FrameDescriptor) or (ErrorLanguage, FrameDescriptor.Builder). The constructor should be visible to subclasses.")
    @GenerateBytecode(languageClass = ErrorLanguage.class)
    public abstract static class InvalidConstructor extends RootNode implements BytecodeRootNode {
        protected InvalidConstructor() {
            super(null);
        }

        protected InvalidConstructor(String name) {
            super(null);
        }

        protected InvalidConstructor(ErrorLanguage language) {
            super(language);
        }

        protected InvalidConstructor(ErrorLanguage language, String name) {
            super(language);
        }

        protected InvalidConstructor(FrameDescriptor frameDescriptor, TruffleLanguage<?> language) {
            super(language, frameDescriptor);
        }

        protected InvalidConstructor(FrameDescriptor.Builder builder, TruffleLanguage<?> language) {
            super(language, builder.build());
        }
    }

    @GenerateBytecode(languageClass = ErrorLanguage.class)
    public abstract static class BadOverrides extends RootNode implements BytecodeRootNode {
        protected BadOverrides(ErrorLanguage language, FrameDescriptor frameDescriptor) {
            super(language, frameDescriptor);
        }

        @ExpectError("This method is overridden by the generated Bytecode DSL class, so it cannot be declared final. Since it is overridden, the definition is unreachable and can be removed.")
        @Override
        public final Object execute(VirtualFrame frame) {
            return null;
        }

        @ExpectError("This method is overridden by the generated Bytecode DSL class, so it cannot be declared final. Since it is overridden, the definition is unreachable and can be removed.")
        public final Object getOSRMetadata() {
            return null;
        }

        @ExpectError("This method is overridden by the generated Bytecode DSL class, so it cannot be declared final. Since it is overridden, the definition is unreachable and can be removed.")
        public final void setOSRMetadata(Object osrMetadata) {
        }

        @ExpectError("This method is overridden by the generated Bytecode DSL class, so it cannot be declared final. Since it is overridden, the definition is unreachable and can be removed.")
        public final Object[] storeParentFrameInArguments(VirtualFrame parentFrame) {
            return null;
        }

        @ExpectError("This method is overridden by the generated Bytecode DSL class, so it cannot be declared final. Since it is overridden, the definition is unreachable and can be removed.")
        public final Frame restoreParentFrameFromArguments(Object[] arguments) {
            return null;
        }

        @ExpectError("This method is overridden by the generated Bytecode DSL class, so it cannot be declared final. Since it is overridden, the definition is unreachable and can be removed.")
        public final BytecodeNode getBytecodeNode() {
            return null;
        }

        @ExpectError("This method is overridden by the generated Bytecode DSL class, so it cannot be declared final. Since it is overridden, the definition is unreachable and can be removed.")
        public final BytecodeRootNodes<?> getRootNodes() {
            return null;
        }

    }

    /**
     * The following root node declares a parent class that widens the visibility of root node
     * methods. The generated code should respect the wider visibility (otherwise, a compiler error
     * will occur).
     */
    @GenerateBytecode(languageClass = ErrorLanguage.class, enableTagInstrumentation = true)
    public abstract static class AcceptableOverrides extends RootNodeWithMoreOverrides implements BytecodeRootNode {
        protected AcceptableOverrides(ErrorLanguage language, FrameDescriptor frameDescriptor) {
            super(language, frameDescriptor);
        }

        @Operation
        public static final class Add {
            @Specialization
            static int add(int x, int y) {
                return x + y;
            }
        }

        @Override
        public int findBytecodeIndex(Node node, Frame frame) {
            return super.findBytecodeIndex(node, frame);
        }

        @Override
        public boolean isCaptureFramesForTrace(boolean compiledFrame) {
            return super.isCaptureFramesForTrace(compiledFrame);
        }
    }

    private abstract static class RootNodeWithOverrides extends RootNode {
        protected RootNodeWithOverrides(ErrorLanguage language, FrameDescriptor frameDescriptor) {
            super(language, frameDescriptor);
        }

        @Override
        protected Node findInstrumentableCallNode(Node callNode, Frame frame, int bytecodeIndex) {
            return super.findInstrumentableCallNode(callNode, frame, bytecodeIndex);
        }

        @Override
        public boolean isInstrumentable() {
            return super.isInstrumentable();
        }
    }

    private abstract static class RootNodeWithMoreOverrides extends RootNodeWithOverrides {
        protected RootNodeWithMoreOverrides(ErrorLanguage language, FrameDescriptor frameDescriptor) {
            super(language, frameDescriptor);
        }

        @Override
        public Object translateStackTraceElement(TruffleStackTraceElement element) {
            return super.translateStackTraceElement(element);
        }

        @Override
        public void prepareForInstrumentation(Set<Class<?>> tags) {
            super.prepareForInstrumentation(tags);
        }

    }

    @ExpectError("The used type system is invalid. Fix errors in the type system first.")
    @GenerateBytecode(languageClass = ErrorLanguage.class)
    @TypeSystemReference(ErroredTypeSystem.class)
    public abstract static class BadTypeSystem extends RootNode implements BytecodeRootNode {
        protected BadTypeSystem(ErrorLanguage language, FrameDescriptor builder) {
            super(language, builder);
        }
    }

    @ExpectError("Cannot perform boxing elimination on java.lang.String. Remove this type from the boxing eliminated types list. Only primitive types boolean, byte, int, float, long, and double are supported.")
    @GenerateBytecode(languageClass = ErrorLanguage.class, boxingEliminationTypes = {String.class})
    public abstract static class BadBoxingElimination extends RootNode implements BytecodeRootNode {
        protected BadBoxingElimination(ErrorLanguage language, FrameDescriptor builder) {
            super(language, builder);
        }
    }

    @ExpectError("Could not proxy operation: the proxied type must be a class, not int.")
    @GenerateBytecode(languageClass = ErrorLanguage.class)
    @OperationProxy(int.class)
    public abstract static class PrimitiveProxyType extends RootNode implements BytecodeRootNode {
        protected PrimitiveProxyType(ErrorLanguage language, FrameDescriptor builder) {
            super(language, builder);
        }
    }

    @GenerateBytecode(languageClass = ErrorLanguage.class)
    @ExpectError("Encountered errors using com.oracle.truffle.api.bytecode.test.error_tests.ErrorTests.NoCachedProxyType.NodeWithNoCache as an OperationProxy. These errors must be resolved before the DSL can proceed.")
    @OperationProxy(NoCachedProxyType.NodeWithNoCache.class)
    public abstract static class NoCachedProxyType extends RootNode implements BytecodeRootNode {
        protected NoCachedProxyType(ErrorLanguage language, FrameDescriptor builder) {
            super(language, builder);
        }

        @GenerateCached(false)
        @OperationProxy.Proxyable
        @ExpectError("Class com.oracle.truffle.api.bytecode.test.error_tests.ErrorTests.NoCachedProxyType.NodeWithNoCache does not generate a cached node, so it cannot be used as an OperationProxy. " +
                        "Enable cached node generation using @GenerateCached(true) or delegate to this node using a regular Operation.")
        public static final class NodeWithNoCache extends Node {
            @Specialization
            public static int doInt() {
                return 42;
            }

        }
    }

    @GenerateBytecode(languageClass = ErrorLanguage.class)
    @ExpectError({
                    "Encountered errors using com.oracle.truffle.api.bytecode.test.error_tests.ErrorTests.NonFinalOperationProxy as an OperationProxy. These errors must be resolved before the DSL can proceed.",
                    "Encountered errors using com.oracle.truffle.api.bytecode.test.error_tests.ErrorTests.NonStaticInnerOperationProxy as an OperationProxy. These errors must be resolved before the DSL can proceed.",
                    "Encountered errors using com.oracle.truffle.api.bytecode.test.error_tests.ErrorTests.PrivateOperationProxy as an OperationProxy. These errors must be resolved before the DSL can proceed.",
                    "Encountered errors using com.oracle.truffle.api.bytecode.test.error_tests.ErrorTests.CloneableOperationProxy as an OperationProxy. These errors must be resolved before the DSL can proceed.",
                    "Encountered errors using com.oracle.truffle.api.bytecode.test.error_tests.ErrorTests.NonStaticMemberOperationProxy as an OperationProxy. These errors must be resolved before the DSL can proceed.",
                    "Encountered errors using com.oracle.truffle.api.bytecode.test.error_tests.ErrorTests.BadVariadicOperationProxy as an OperationProxy. These errors must be resolved before the DSL can proceed.",
                    "Encountered errors using com.oracle.truffle.api.bytecode.test.error_tests.ErrorTests.Underscored_Operation_Proxy as an OperationProxy. These errors must be resolved before the DSL can proceed.",
                    "Could not use com.oracle.truffle.api.bytecode.test.error_tests.ErrorTests.UnproxyableOperationProxy as an operation proxy: the class must be annotated with @OperationProxy.Proxyable.",
    })
    @OperationProxy(NonFinalOperationProxy.class)
    @OperationProxy(NonStaticInnerOperationProxy.class)
    @OperationProxy(PrivateOperationProxy.class)
    @OperationProxy(CloneableOperationProxy.class)
    @OperationProxy(NonStaticMemberOperationProxy.class)
    @OperationProxy(BadVariadicOperationProxy.class)
    @OperationProxy(Underscored_Operation_Proxy.class)
    @OperationProxy(UnproxyableOperationProxy.class)
    public abstract static class OperationErrorTests extends RootNode implements BytecodeRootNode {
        protected OperationErrorTests(ErrorLanguage language, FrameDescriptor builder) {
            super(language, builder);
        }

        @ExpectError("Operation class must be declared final. Inheritance in operation specifications is not supported.")
        @Operation
        public static class NonFinalOperation {
        }

        @ExpectError("Operation class must not be an inner class (non-static nested class). Declare the class as static.")
        @Operation
        public final class NonStaticInnerOperation {
        }

        @ExpectError("Operation class must not be declared private. Remove the private modifier to make it visible.")
        @Operation
        private static final class PrivateOperation {
        }

        @ExpectError("Operation class must not extend any classes or implement any interfaces. Inheritance in operation specifications is not supported.")
        @Operation
        public static final class CloneableOperation implements Cloneable {
        }

        @Operation
        public static final class NonStaticMemberOperation {

            @ExpectError("Operation class must not contain non-static members.") public int field;

            @ExpectError("Operation class must not contain non-static members.")
            public void doSomething() {
            }

            @Specialization
            @ExpectError("Operation specializations must be static. This method should be rewritten as a static specialization.")
            public int add(int x, int y) {
                return x + y;
            }

            @Fallback
            @ExpectError("Operation specializations must be static. This method should be rewritten as a static specialization.")
            public Object fallback(Object a, Object b) {
                return a;
            }
        }

        @Operation
        public static final class BadVariadicOperation {
            @Specialization
            public static void valueAfterVariadic(VirtualFrame f, @Variadic Object[] a, @ExpectError("Non-variadic operands must precede variadic operands.") Object b) {
            }

            @Specialization
            public static void multipleVariadic(@Variadic Object[] a,
                            @ExpectError("Multiple variadic operands not allowed to an operation. Split up the operation if such behaviour is required.") @Variadic Object[] b) {
            }

            @Specialization
            public static void variadicWithWrongType(@ExpectError("Variadic operand must have type Object[].") @Variadic String[] a) {
            }
        }

        @Operation
        public static final class BadFallbackOperation {
            @Specialization
            public static void doInts(int a, int b) {
            }

            @Fallback
            public static void doFallback(Object a,
                            @ExpectError("Operands to @Fallback specializations of Operation nodes must have type Object.") int b) {
            }
        }

        @ExpectError("Operation class name cannot contain underscores.")
        @Operation
        public static final class Underscored_Operation {
        }

        @ExpectError("@Operation and @Instrumentation cannot be used at the same time. Remove one of the annotations to resolve this.")
        @Operation
        @Instrumentation
        public static final class OverlappingAnnotations1 {
            @Specialization
            public static void doExecute() {
            }
        }

        @ExpectError("@Instrumentation and @Prolog cannot be used at the same time. Remove one of the annotations to resolve this.")
        @Instrumentation
        @Prolog
        public static final class OverlappingAnnotations2 {
            @Specialization
            public static void doExecute() {
            }
        }

        @ExpectError("@Prolog and @EpilogReturn cannot be used at the same time. Remove one of the annotations to resolve this.")
        @Prolog
        @EpilogReturn
        public static final class OverlappingAnnotations3 {
            @Specialization
            public static void doExecute() {
            }
        }

        @ExpectError("@EpilogReturn and @EpilogExceptional cannot be used at the same time. Remove one of the annotations to resolve this.")
        @EpilogReturn
        @EpilogExceptional
        public static final class OverlappingAnnotations4 {
            @Specialization
            public static void doExecute() {
            }
        }
    }

    @GenerateBytecode(languageClass = ErrorLanguage.class)
    @ExpectError({
                    "Operation NonPublicSpecializationOperationProxy's specialization \"add\" must be visible from this node.",
                    "Operation NonPublicSpecializationOperationProxy's specialization \"fallback\" must be visible from this node.",
    })
    @OperationProxy(PackagePrivateSpecializationOperationProxy.class)
    @OperationProxy(NonPublicSpecializationOperationProxy.class)
    @OperationProxy(NonPublicGuardExpressionOperationProxy.class)
    @OperationProxy(NestedNodeOperationProxy.class)
    public abstract static class BadSpecializationOrDSLTests extends RootNode implements BytecodeRootNode {

        protected BadSpecializationOrDSLTests(ErrorLanguage language, FrameDescriptor frameDescriptor) {
            super(language, frameDescriptor);
        }

        @Operation
        public static final class NonStaticGuardExpressionOperation {
            @Specialization(guards = "guardCondition()")
            public static int addGuarded(int x, int y) {
                return x + y;
            }

            @ExpectError("Operation class must not contain non-static members.")
            public boolean guardCondition() {
                return true;
            }
        }

        // These should not cause an issue because they are in the same package as the generated
        // root node would be. The generated node can see them. There are similar versions of
        // these
        // nodes defined in a separate package (e.g., {@link
        // NonPublicSpecializationOperationProxy})
        // which are not visible and should cause issues.
        @Operation
        public static final class PackagePrivateSpecializationOperation {
            @Specialization
            static int add(int x, int y) {
                return x + y;
            }

            @Fallback
            static Object fallback(Object a, Object b) {
                return a;
            }
        }

        @Operation
        public static final class PackagePrivateGuardExpressionOperation {
            @Specialization(guards = "guardCondition()")
            public static int addGuarded(int x, int y) {
                return x + y;
            }

            protected static boolean guardCondition() {
                return true;
            }
        }
    }

    @ExpectError("Operation class must be declared final. Inheritance in operation specifications is not supported.")
    @OperationProxy.Proxyable
    public static class NonFinalOperationProxy {
    }

    @ExpectError("Operation class must not be an inner class (non-static nested class). Declare the class as static.")
    @OperationProxy.Proxyable
    public final class NonStaticInnerOperationProxy {
    }

    @ExpectError("Operation class must not be declared private. Remove the private modifier to make it visible.")
    @OperationProxy.Proxyable
    private static final class PrivateOperationProxy {
    }

    @ExpectError("Operation class must not extend any classes or implement any interfaces. Inheritance in operation specifications is not supported.")
    @OperationProxy.Proxyable
    public static final class CloneableOperationProxy implements Cloneable {
    }

    @OperationProxy.Proxyable
    public static final class NonStaticMemberOperationProxy {

        @ExpectError("Operation class must not contain non-static members.") public int field;

        @Specialization
        @ExpectError("Operation specializations must be static. This method should be rewritten as a static specialization.")
        public int add(int x, int y) {
            return x + y;
        }

        @Fallback
        @ExpectError("Operation specializations must be static. This method should be rewritten as a static specialization.")
        public Object fallback(Object a, Object b) {
            return a;
        }
    }

    /**
     * These specializations should not be a problem. See
     * {@link OperationErrorTests.PackagePrivateSpecializationOperation}
     */
    @OperationProxy.Proxyable
    public abstract static class PackagePrivateSpecializationOperationProxy extends Node {
        public abstract Object execute(Object x, Object y);

        @Specialization
        static int add(int x, int y) {
            return x + y;
        }

        @Fallback
        static Object fallback(Object a, Object b) {
            return a;
        }
    }

    @OperationProxy.Proxyable
    public static final class BadVariadicOperationProxy {
        @Specialization
        public static void valueAfterVariadic(VirtualFrame f, @Variadic Object[] a, @ExpectError("Non-variadic operands must precede variadic operands.") Object b) {
        }

        @Specialization
        public static void multipleVariadic(@Variadic Object[] a,
                        @ExpectError("Multiple variadic operands not allowed to an operation. Split up the operation if such behaviour is required.") @Variadic Object[] b) {
        }

        @Specialization
        public static void variadicWithWrongType(@ExpectError("Variadic operand must have type Object[].") @Variadic String[] a) {
        }
    }

    @ExpectError("Operation class name cannot contain underscores.")
    @OperationProxy.Proxyable
    public static final class Underscored_Operation_Proxy {
    }

    public static final class UnproxyableOperationProxy {
        @Specialization
        static int add(int x, int y) {
            return x + y;
        }
    }

    @GenerateBytecode(languageClass = ErrorLanguage.class, enableUncachedInterpreter = true)
    @OperationProxy(UncachedOperationProxy.class)
    @ExpectError("Could not use com.oracle.truffle.api.bytecode.test.error_tests.ErrorTests.NoUncachedOperationProxy as an operation proxy: " +
                    "the class must be annotated with @OperationProxy.Proxyable(allowUncached=true) when an uncached interpreter is requested.")
    @OperationProxy(NoUncachedOperationProxy.class)
    public abstract static class OperationErrorUncachedTests extends RootNode implements BytecodeRootNode {
        protected OperationErrorUncachedTests(ErrorLanguage language, FrameDescriptor builder) {
            super(language, builder);
        }
    }

    @OperationProxy.Proxyable(allowUncached = true)
    public static final class UncachedOperationProxy {
        @Specialization
        static int add(int x, int y) {
            return x + y;
        }
    }

    @OperationProxy.Proxyable
    public static final class NoUncachedOperationProxy {
        @Specialization
        static int add(int x, int y) {
            return x + y;
        }
    }

    @GenerateBytecode(languageClass = ErrorLanguage.class)
    @ExpectError({"Multiple operations declared with name MyOperation. Operation names must be distinct."})
    @OperationProxy(value = AddOperation.class, name = "MyOperation")
    @OperationProxy(value = SubOperation.class, name = "MyOperation")
    public abstract static class DuplicateOperationNameTest extends RootNode implements BytecodeRootNode {
        protected DuplicateOperationNameTest(ErrorLanguage language, FrameDescriptor builder) {
            super(language, builder);
        }
    }

    @OperationProxy.Proxyable
    public static final class AddOperation {
        @Specialization
        static int add(int x, int y) {
            return x + y;
        }
    }

    @OperationProxy.Proxyable
    public static final class SubOperation {
        @Specialization
        static int sub(int x, int y) {
            return x - y;
        }
    }

    @GenerateBytecode(languageClass = ErrorLanguage.class)
    @ExpectError({"At least one operation must be declared using @Operation, @OperationProxy, or @ShortCircuitOperation."})
    public abstract static class NoOperationsTest extends RootNode implements BytecodeRootNode {
        protected NoOperationsTest(ErrorLanguage language, FrameDescriptor builder) {
            super(language, builder);
        }
    }

    @GenerateBytecode(languageClass = ErrorLanguage.class)
    @ExpectError({
                    "Specializations for boolean converter ToBooleanBadReturn must only take one dynamic operand and return boolean.",
                    "Encountered errors using ToBooleanBadOperation as a boolean converter. These errors must be resolved before the DSL can proceed."
    })
    @ShortCircuitOperation(name = "Foo", operator = Operator.AND_RETURN_VALUE, booleanConverter = BadBooleanConverterTest.ToBooleanBadReturn.class)
    @ShortCircuitOperation(name = "Bar", operator = Operator.AND_RETURN_VALUE, booleanConverter = BadBooleanConverterTest.ToBooleanBadOperation.class)
    public abstract static class BadBooleanConverterTest extends RootNode implements BytecodeRootNode {
        protected BadBooleanConverterTest(ErrorLanguage language, FrameDescriptor builder) {
            super(language, builder);
        }

        @Operation
        public static final class ToBooleanBadReturn {
            @Specialization
            public static boolean fromInt(int x) {
                return x != 0;
            }

            @Specialization
            public static int badSpec(boolean x) {
                return 42;
            }
        }

        public static final class ToBooleanBadOperation {
            @Specialization
            @ExpectError("Operation specializations must be static. This method should be rewritten as a static specialization.")
            public boolean fromInt(int x) {
                return x != 0;
            }
        }
    }

    @ExpectError("%")
    @TypeSystem
    private class ErroredTypeSystem {
    }

    @ProvidedTags({RootTag.class, RootBodyTag.class})
    public class ErrorLanguage extends TruffleLanguage<Object> {
        @Override
        protected Object createContext(Env env) {
            return null;
        }
    }

}
