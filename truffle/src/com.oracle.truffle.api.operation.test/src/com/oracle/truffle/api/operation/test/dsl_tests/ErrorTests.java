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
package com.oracle.truffle.api.operation.test.dsl_tests;

import java.util.Set;

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystem;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.operation.GenerateOperations;
import com.oracle.truffle.api.operation.LocalSetter;
import com.oracle.truffle.api.operation.LocalSetterRange;
import com.oracle.truffle.api.operation.Operation;
import com.oracle.truffle.api.operation.OperationProxy;
import com.oracle.truffle.api.operation.OperationRootNode;
import com.oracle.truffle.api.operation.Variadic;
import com.oracle.truffle.api.operation.test.ExpectError;
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

    @ExpectError("The used type system 'com.oracle.truffle.api.operation.test.dsl_tests.ErrorTests.ErroredTypeSystem' is invalid. Fix errors in the type system first.")
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
    public abstract class BadProxyType extends RootNode implements OperationRootNode {
        protected BadProxyType(TruffleLanguage<?> language, FrameDescriptor builder) {
            super(language, builder);
        }
    }

    @GenerateOperations(languageClass = ErrorLanguage.class)
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

            @ExpectError("@Operation annotated class must not contain non-static members.") public int field;

            @ExpectError("@Operation annotated class must not contain non-static members.")
            public void doSomething() {
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

    @ExpectError("Operation specifications can only contain static specializations. Use @Bind(\"this\") parameter if you need a Node instance.")
    public static final class NonStaticMemberOperationProxy {

        @ExpectError("@Operation annotated class must not contain non-static members.") public int field;

        @Specialization
        @ExpectError("@Operation annotated class must not contain non-static members.")
        public int add(int x, int y) {
            return x + y;
        }
    }

    @Operation
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
