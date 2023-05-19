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
package com.oracle.truffle.api.operation.test;

import java.util.Set;

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystem;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.operation.GenerateOperations;
import com.oracle.truffle.api.operation.LocalSetter;
import com.oracle.truffle.api.operation.LocalSetterRange;
import com.oracle.truffle.api.operation.Operation;
import com.oracle.truffle.api.operation.OperationProxy;
import com.oracle.truffle.api.operation.OperationRootNode;
import com.oracle.truffle.api.operation.Variadic;
import com.oracle.truffle.api.operation.test.subpackage.NonPublicGuardExpressionOperationProxy;
import com.oracle.truffle.api.operation.test.subpackage.NonPublicSpecializationOperationProxy;
import com.oracle.truffle.api.source.SourceSection;

@SuppressWarnings({"unused", "static-method", "truffle"})
public class ErrorTests {
    @ExpectError("Operations class must be declared abstract.")
    @GenerateOperations(languageClass = ErrorLanguage.class)
    public class MustBeDeclaredAbstract extends RootNode implements OperationRootNode {
        protected MustBeDeclaredAbstract(TruffleLanguage<?> language, FrameDescriptor frameDescriptor) {
            super(language, frameDescriptor);
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return null;
        }

        public String dump() {
            return null;
        }

        public SourceSection getSourceSectionAtBci(int bci) {
            return null;
        }

        public InstrumentableNode materializeInstrumentTree(Set<Class<? extends Tag>> materializedTags) {
            return null;
        }
    }

    @ExpectError("Operations class must directly or indirectly subclass RootNode.")
    @GenerateOperations(languageClass = ErrorLanguage.class)
    public abstract class MustBeSubclassOfRootNode implements OperationRootNode {
        protected MustBeSubclassOfRootNode(TruffleLanguage<?> language, FrameDescriptor frameDescriptor) {
        }
    }

    @ExpectError("Operations class must directly or indirectly implement OperationRootNode.")
    @GenerateOperations(languageClass = ErrorLanguage.class)
    public abstract class MustImplementOperationRootNode extends RootNode {
        protected MustImplementOperationRootNode(TruffleLanguage<?> language, FrameDescriptor frameDescriptor) {
            super(language, frameDescriptor);
        }
    }

    @ExpectError("Operations class should declare a constructor that has signature (TruffleLanguage<C>, FrameDescriptor) or (TruffleLanguage<C>, FrameDescriptor.Builder). The constructor should be visible to subclasses.")
    @GenerateOperations(languageClass = ErrorLanguage.class)
    public abstract class HiddenConstructor extends RootNode implements OperationRootNode {
        private HiddenConstructor(TruffleLanguage<?> language, FrameDescriptor descriptor) {
            super(language, descriptor);
        }
    }

    @ExpectError("Operations class should declare a constructor that has signature (TruffleLanguage<C>, FrameDescriptor) or (TruffleLanguage<C>, FrameDescriptor.Builder). The constructor should be visible to subclasses.")
    @GenerateOperations(languageClass = ErrorLanguage.class)
    public abstract class InvalidConstructor extends RootNode implements OperationRootNode {
        protected InvalidConstructor() {
            super(null);
        }

        protected InvalidConstructor(String name) {
            super(null);
        }

        protected InvalidConstructor(TruffleLanguage<?> language) {
            super(language);
        }

        protected InvalidConstructor(TruffleLanguage<?> language, String name) {
            super(language);
        }

        protected InvalidConstructor(FrameDescriptor frameDescriptor, TruffleLanguage<?> language) {
            super(language, frameDescriptor);
        }

        protected InvalidConstructor(FrameDescriptor.Builder builder, TruffleLanguage<?> language) {
            super(language, builder.build());
        }
    }

    @GenerateOperations(languageClass = ErrorLanguage.class)
    public abstract class BadOverrides extends RootNode implements OperationRootNode {
        protected BadOverrides(TruffleLanguage<?> language, FrameDescriptor frameDescriptor) {
            super(language, frameDescriptor);
        }

        @ExpectError("This method is overridden by the generated Operations class, so it cannot be declared final. Since it is overridden, the definition is unreachable and can be removed. Override executeProlog and executeEpilog to perform actions before and after execution.")
        @Override
        public final Object execute(VirtualFrame frame) {
            return null;
        }

        @ExpectError("This method is overridden by the generated Operations class, so it cannot be declared final. Since it is overridden, the definition is unreachable and can be removed.")
        public final Object getOSRMetadata() {
            return null;
        }

        @ExpectError("This method is overridden by the generated Operations class, so it cannot be declared final. Since it is overridden, the definition is unreachable and can be removed.")
        public final void setOSRMetadata(Object osrMetadata) {
        }

        @ExpectError("This method is overridden by the generated Operations class, so it cannot be declared final. Since it is overridden, the definition is unreachable and can be removed.")
        public final Object[] storeParentFrameInArguments(VirtualFrame parentFrame) {
            return null;
        }

        @ExpectError("This method is overridden by the generated Operations class, so it cannot be declared final. Since it is overridden, the definition is unreachable and can be removed.")
        public final Frame restoreParentFrameFromArguments(Object[] arguments) {
            return null;
        }

        @ExpectError("This method is overridden by the generated Operations class, so it cannot be declared final. Since it is overridden, the definition is unreachable and can be removed.")
        public final InstrumentableNode materializeInstrumentTree(Set<Class<? extends Tag>> materializedTags) {
            return null;
        }

        @ExpectError("This method is overridden by the generated Operations class, so it cannot be declared final. Since it is overridden, the definition is unreachable and can be removed.")
        public final SourceSection getSourceSectionAtBci(int bci) {
            return null;
        }
    }

    @ExpectError("The used type system 'com.oracle.truffle.api.operation.test.ErrorTests.ErroredTypeSystem' is invalid. Fix errors in the type system first.")
    @GenerateOperations(languageClass = ErrorLanguage.class)
    @TypeSystemReference(ErroredTypeSystem.class)
    public abstract class BadTypeSystem extends RootNode implements OperationRootNode {
        protected BadTypeSystem(TruffleLanguage<?> language, FrameDescriptor builder) {
            super(language, builder);
        }
    }

    @ExpectError("Cannot perform boxing elimination on java.lang.String. Remove this type from the boxing eliminated types list. Only primitive types boolean, byte, int, float, long, and double are supported.")
    @GenerateOperations(languageClass = ErrorLanguage.class, boxingEliminationTypes = {String.class})
    public abstract class BadBoxingElimination extends RootNode implements OperationRootNode {
        protected BadBoxingElimination(TruffleLanguage<?> language, FrameDescriptor builder) {
            super(language, builder);
        }
    }

    @ExpectError("Could not proxy operation: the proxied type must be a class, not int.")
    @GenerateOperations(languageClass = ErrorLanguage.class)
    @OperationProxy(int.class)
    public abstract class PrimitiveProxyType extends RootNode implements OperationRootNode {
        protected PrimitiveProxyType(TruffleLanguage<?> language, FrameDescriptor builder) {
            super(language, builder);
        }
    }

    @GenerateOperations(languageClass = ErrorLanguage.class)
    @ExpectError("Class com.oracle.truffle.api.operation.test.ErrorTests.NoCachedProxyType.NodeWithNoCache does not generate a cached node, so it cannot be used as an OperationProxy. Enable cached node generation using @GenerateCached(true) or delegate to this node using a regular Operation.")
    @OperationProxy(NoCachedProxyType.NodeWithNoCache.class)
    public abstract class NoCachedProxyType extends RootNode implements OperationRootNode {
        protected NoCachedProxyType(TruffleLanguage<?> language, FrameDescriptor builder) {
            super(language, builder);
        }

        @GenerateCached(false)
        public static final class NodeWithNoCache extends Node {
            @Specialization
            public static int doInt() {
                return 42;
            }

        }
    }

    @GenerateOperations(languageClass = ErrorLanguage.class)
    @ExpectError({
                    "Encountered errors using com.oracle.truffle.api.operation.test.ErrorTests.NonFinalOperationProxy as an OperationProxy. These errors must be resolved before the DSL can proceed.",
                    "Encountered errors using com.oracle.truffle.api.operation.test.ErrorTests.NonStaticInnerOperationProxy as an OperationProxy. These errors must be resolved before the DSL can proceed.",
                    "Encountered errors using com.oracle.truffle.api.operation.test.ErrorTests.PrivateOperationProxy as an OperationProxy. These errors must be resolved before the DSL can proceed.",
                    "Encountered errors using com.oracle.truffle.api.operation.test.ErrorTests.CloneableOperationProxy as an OperationProxy. These errors must be resolved before the DSL can proceed.",
                    "Encountered errors using com.oracle.truffle.api.operation.test.ErrorTests.NonStaticMemberOperationProxy as an OperationProxy. These errors must be resolved before the DSL can proceed.",
                    "Encountered errors using com.oracle.truffle.api.operation.test.ErrorTests.BadSignatureOperationProxy as an OperationProxy. These errors must be resolved before the DSL can proceed.",
                    "Encountered errors using com.oracle.truffle.api.operation.test.ErrorTests.Underscored_Operation_Proxy as an OperationProxy. These errors must be resolved before the DSL can proceed.",
    })
    @OperationProxy(NonFinalOperationProxy.class)
    @OperationProxy(NonStaticInnerOperationProxy.class)
    @OperationProxy(PrivateOperationProxy.class)
    @OperationProxy(CloneableOperationProxy.class)
    @OperationProxy(NonStaticMemberOperationProxy.class)
    @OperationProxy(BadSignatureOperationProxy.class)
    @OperationProxy(Underscored_Operation_Proxy.class)
    public abstract static class OperationErrorTests extends RootNode implements OperationRootNode {
        protected OperationErrorTests(TruffleLanguage<?> language, FrameDescriptor builder) {
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
        public static final class BadSignatureOperation {
            @Specialization
            public static void valueAfterVariadic(VirtualFrame f, @Variadic Object[] a, @ExpectError("Non-variadic value parameters must precede variadic parameters.") Object b) {
            }

            @Specialization
            public static void valueAfterSetter(LocalSetter a, @ExpectError("Value parameters must precede LocalSetter and LocalSetterRange parameters.") Object b) {
            }

            @Specialization
            public static void valueAfterSetterRange(LocalSetterRange a, @ExpectError("Value parameters must precede LocalSetter and LocalSetterRange parameters.") Object b) {
            }

            @Specialization
            public static void variadicAfterSetter(LocalSetter a,
                            @ExpectError("Value parameters must precede LocalSetter and LocalSetterRange parameters.") @Variadic Object[] b) {
            }

            @Specialization
            public static void variadicAfterSetterRange(LocalSetterRange a,
                            @ExpectError("Value parameters must precede LocalSetter and LocalSetterRange parameters.") @Variadic Object[] b) {
            }

            @Specialization
            public static void multipleVariadic(@Variadic Object[] a,
                            @ExpectError("Multiple variadic parameters not allowed to an operation. Split up the operation if such behaviour is required.") @Variadic Object[] b) {
            }

            @Specialization
            public static void setterAfterSetterRange(LocalSetterRange a,
                            @ExpectError("LocalSetter parameters must precede LocalSetterRange parameters.") LocalSetter b) {
            }
        }

        @ExpectError("Operation class name cannot contain underscores.")
        @Operation
        public static final class Underscored_Operation {
        }
    }

    @GenerateOperations(languageClass = ErrorLanguage.class)
    @ExpectError({"Encountered errors using com.oracle.truffle.api.operation.test.subpackage.NonPublicSpecializationOperationProxy as an OperationProxy. These errors must be resolved before the DSL can proceed.",
                    "Encountered errors using com.oracle.truffle.api.operation.test.subpackage.NonPublicGuardExpressionOperationProxy as an OperationProxy. These errors must be resolved before the DSL can proceed."
    })
    @OperationProxy(PackagePrivateSpecializationOperationProxy.class)
    @OperationProxy(NonPublicSpecializationOperationProxy.class)
    @OperationProxy(NonPublicGuardExpressionOperationProxy.class)
    public abstract static class BadSpecializationOrDSLTests extends RootNode implements OperationRootNode {

        protected BadSpecializationOrDSLTests(TruffleLanguage<?> language, FrameDescriptor frameDescriptor) {
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
        // root node would be. The generated node can see them. There are similar versions of these
        // nodes defined in a separate package (e.g., {@link NonPublicSpecializationOperationProxy})
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

// Proxy node definitions

    @ExpectError("Operation class must be declared final. Inheritance in operation specifications is not supported.")
    public static class NonFinalOperationProxy {
    }

    @ExpectError("Operation class must not be an inner class (non-static nested class). Declare the class as static.")
    public final class NonStaticInnerOperationProxy {
    }

    @ExpectError("Operation class must not be declared private. Remove the private modifier to make it visible.")
    private static final class PrivateOperationProxy {
    }

    @ExpectError("Operation class must not extend any classes or implement any interfaces. Inheritance in operation specifications is not supported.")
    public static final class CloneableOperationProxy implements Cloneable {
    }

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

    // These specializations should not be a problem. See {@link
    // OperationErrorTests.PackagePrivateSpecializationOperation}
    public static abstract class PackagePrivateSpecializationOperationProxy extends Node {
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

    public static final class BadSignatureOperationProxy {
        @Specialization
        public static void valueAfterVariadic(VirtualFrame f, @Variadic Object[] a, @ExpectError("Non-variadic value parameters must precede variadic parameters.") Object b) {
        }

        @Specialization
        public static void valueAfterSetter(LocalSetter a, @ExpectError("Value parameters must precede LocalSetter and LocalSetterRange parameters.") Object b) {
        }

        @Specialization
        public static void valueAfterSetterRange(LocalSetterRange a, @ExpectError("Value parameters must precede LocalSetter and LocalSetterRange parameters.") Object b) {
        }

        @Specialization
        public static void variadicAfterSetter(LocalSetter a,
                        @ExpectError("Value parameters must precede LocalSetter and LocalSetterRange parameters.") @Variadic Object[] b) {
        }

        @Specialization
        public static void variadicAfterSetterRange(LocalSetterRange a,
                        @ExpectError("Value parameters must precede LocalSetter and LocalSetterRange parameters.") @Variadic Object[] b) {
        }

        @Specialization
        public static void multipleVariadic(@Variadic Object[] a,
                        @ExpectError("Multiple variadic parameters not allowed to an operation. Split up the operation if such behaviour is required.") @Variadic Object[] b) {
        }

        @Specialization
        public static void setterAfterSetterRange(LocalSetterRange a,
                        @ExpectError("LocalSetter parameters must precede LocalSetterRange parameters.") LocalSetter b) {
        }
    }

    @ExpectError("Operation class name cannot contain underscores.")
    public static final class Underscored_Operation_Proxy {
    }

// todo: test for bad quicken decision when we parse those
    @ExpectError({
                    "Unknown optimization decision type: 'MadeUpType'.",
                    "Error reading optimization decisions: Super-instruction 'si.made.up.instruction' defines a sub-instruction 'made.up.instruction' which does not exist.",
    })
    @GenerateOperations(languageClass = ErrorLanguage.class, decisionsFile = "bad_decisions.json")
    public abstract static class OperationDecisionErrorTests extends RootNode implements OperationRootNode {
        protected OperationDecisionErrorTests(TruffleLanguage<?> language, FrameDescriptor builder) {
            super(language, builder);
        }

        @Operation
        public static final class TestOperation {
            @Specialization
            public static void doStuff() {
            }
        }
    }

    @ExpectError("%")
    @TypeSystem
    private class ErroredTypeSystem {
    }

    public class ErrorLanguage extends TruffleLanguage<Object> {
        @Override
        protected Object createContext(Env env) {
            return null;
        }
    }

}
