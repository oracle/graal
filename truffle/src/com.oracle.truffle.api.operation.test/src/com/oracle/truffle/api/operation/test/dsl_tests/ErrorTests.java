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
import com.oracle.truffle.api.nodes.Node;
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

@SuppressWarnings("unused")
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

    @ExpectError("Operations class requires a (TruffleLanguage, FrameDescriptor) constructor.")
    @GenerateOperations(languageClass = ErrorLanguage.class)
    public abstract class MustHaveFDConstructor extends RootNode implements OperationRootNode {
        protected MustHaveFDConstructor(TruffleLanguage<?> language, FrameDescriptor.Builder builder) {
            super(language, builder.build());
        }
    }

    @ExpectError("Invalid constructor declaration, expected (TruffleLanguage, FrameDescriptor) or (TruffleLanguage, FrameDescriptor.Builder). Remove this constructor.")
    @GenerateOperations(languageClass = ErrorLanguage.class)
    public abstract class InvalidConstructor extends RootNode implements OperationRootNode {
        protected InvalidConstructor(TruffleLanguage<?> language, FrameDescriptor builder) {
            super(language, builder);
        }

        protected InvalidConstructor(TruffleLanguage<?> language) {
            super(language);
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

    @ExpectError({"Type referenced by @OperationProxy must be a class, not int.", "Error generating operation. Fix issues on the referenced class first."})
    @GenerateOperations(languageClass = ErrorLanguage.class)
    @OperationProxy(int.class)
    public abstract class BadProxyType extends RootNode implements OperationRootNode {
        protected BadProxyType(TruffleLanguage<?> language, FrameDescriptor builder) {
            super(language, builder);
        }
    }

    @ExpectError("Class referenced by @OperationProxy must have all its specializations static. Use @Bind(\"this\") parameter if you need a Node instance.")
    @GenerateOperations(languageClass = ErrorLanguage.class)
    @OperationProxy(TestNode.class)
    public abstract static class OperationErrorTests extends RootNode implements OperationRootNode {
        protected OperationErrorTests(TruffleLanguage<?> language, FrameDescriptor builder) {
            super(language, builder);
        }

        @ExpectError("Operation class must be declared final. Inheritance in operation specifications is not supported.")
        @Operation
        public static class TestOperation1 {
        }

        @ExpectError("Operation class must not be an inner class (non-static nested class). Declare the class as static.")
        @Operation
        public final class TestOperation1a {
        }

        @ExpectError("Operation class must not be declared private. Remove the private modifier to make it visible.")
        @Operation
        private static final class TestOperation2 {
        }

        @ExpectError("Operation class must not extend any classes or implement any interfaces. Inheritance in operation specifications is not supported.")
        @Operation
        public static final class TestOperation3 implements Cloneable {
        }

        @ExpectError("@Operation annotated class must not contain non-static members.")
        @Operation
        public static final class TestOperation4 {
            public void doSomething() {
            }
        }

        @ExpectError("Multiple @Variadic arguments are not supported.")
        @Operation
        public static final class TestOperation5 {
            @Specialization
            public static void spec(@Variadic Object[] a, @Variadic Object[] b) {
            }
        }

        @ExpectError("Value arguments after LocalSetter are not supported.")
        @Operation
        public static final class TestOperation6 {
            @Specialization
            public static void spec(LocalSetter a, Object b) {
            }
        }

        @ExpectError("Mixing regular and range local setters is not supported.")
        @Operation
        public static final class TestOperation7 {
            @Specialization
            public static void spec(LocalSetter a, LocalSetterRange b) {
            }
        }

        @ExpectError("Value arguments after @Variadic are not supported.")
        @Operation
        public static final class TestOperation8 {
            @Specialization
            public static void spec(@Variadic Object[] a, Object b) {
            }
        }

        @ExpectError("Value arguments after LocalSetter are not supported.")
        @Operation
        public static final class TestOperation9 {
            @Specialization
            public static void spec(LocalSetter a, Object b) {
            }
        }
    }

    @ExpectError("%")
    @TypeSystem
    private class ErroredTypeSystem {
    }

    @ExpectError("%")
    public static class ErroredNode {
        @Specialization
        public static void doStuff() {
        }
    }

    public abstract static class TestNode extends Node {
        public abstract int execute(int x, int y);

        @Specialization
        public int add(int x, int y) {
            return x + y;
        }
    }

    public class ErrorLanguage extends TruffleLanguage<Object> {
        @Override
        protected Object createContext(Env env) {
            return null;
        }
    }
}
