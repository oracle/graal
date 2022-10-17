package com.oracle.truffle.api.operation.test.dsl_tests;

import java.io.Serializable;

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystem;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
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

        public String dump() {
            return null;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            return null;
        }

        public SourceSection getSourceSectionAtBci(int bci) {
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

    @ExpectError("Invalid constructor declaration, expected (TruffleLanguage, FrameDescriptor) or (TruffleLanguage, FrameDescriptor.Builder).")
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

    @ExpectError({"Cannot perform boxing elimination on java.lang.String. Remove this type from the boxing eliminated types list.", "No operations found."})
    @GenerateOperations(languageClass = ErrorLanguage.class, boxingEliminationTypes = {String.class})
    public abstract class BadBoxingElimination extends RootNode implements OperationRootNode {
        protected BadBoxingElimination(TruffleLanguage<?> language, FrameDescriptor builder) {
            super(language, builder);
        }
    }

    @ExpectError("Could not parse invalid node: ErroredNode. Fix errors in the node first.")
    @GenerateOperations(languageClass = ErrorLanguage.class)
    @OperationProxy(ErroredNode.class)
    public abstract class BadNodeProxy extends RootNode implements OperationRootNode {
        protected BadNodeProxy(TruffleLanguage<?> language, FrameDescriptor builder) {
            super(language, builder);
        }
    }

    @ExpectError({"Type referenced by @OperationProxy must be a class, not int.", "Could not proxy operation"})
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
    public static abstract class OperationErrorTests extends RootNode implements OperationRootNode {
        protected OperationErrorTests(TruffleLanguage<?> language, FrameDescriptor builder) {
            super(language, builder);
        }

        @ExpectError({"@Operation annotated class must be declared static and final.", "Operation contains no specializations."})
        @Operation
        public class TestOperation1 {
        }

        @ExpectError({"@Operation annotated class must not be declared private.", "Operation contains no specializations."})
        @Operation
        private static final class TestOperation2 {
        }

        @ExpectError({"@Operation annotated class must not extend/implement anything. Inheritance is not supported.", "Operation contains no specializations."})
        @Operation
        public static final class TestOperation3 extends Exception {
            private static final long serialVersionUID = 1L;
        }

        @ExpectError({"@Operation annotated class must not contain non-static members.", "Operation contains no specializations."})
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

    public static abstract class TestNode extends Node {
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