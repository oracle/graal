package com.oracle.truffle.api.operation.test.dsl_tests;

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.operation.GenerateOperations;
import com.oracle.truffle.api.operation.OperationProxy;
import com.oracle.truffle.api.operation.OperationRootNode;
import com.oracle.truffle.api.operation.test.ExpectError;
import com.oracle.truffle.api.operation.test.GenerateOperationsTestVariants;
import com.oracle.truffle.api.operation.test.GenerateOperationsTestVariants.Variant;

public class TestVariantErrorTests {

    @ExpectError("A variant with name \"A\" already exists. Each variant must have a unique name.")
    @GenerateOperationsTestVariants({
                    @Variant(name = "A", configuration = @GenerateOperations(languageClass = ErrorLanguage.class)),
                    @Variant(name = "A", configuration = @GenerateOperations(languageClass = ErrorLanguage.class))})
    @OperationProxy(ConstantOperation.class)
    public static abstract class SameName extends RootNode implements OperationRootNode {
        protected SameName(TruffleLanguage<?> language, FrameDescriptor frameDescriptor) {
            super(language, frameDescriptor);
        }
    }

    @ExpectError("Incompatible variant: all variants must use the same language class.")
    @GenerateOperationsTestVariants({
                    @Variant(name = "A", configuration = @GenerateOperations(languageClass = ErrorLanguage.class)),
                    @Variant(name = "B", configuration = @GenerateOperations(languageClass = AnotherErrorLanguage.class))
    })
    @OperationProxy(ConstantOperation.class)
    public static abstract class DifferentLanguage extends RootNode implements OperationRootNode {
        protected DifferentLanguage(TruffleLanguage<?> language, FrameDescriptor frameDescriptor) {
            super(language, frameDescriptor);
        }
    }

    @ExpectError("Incompatible variant: all variants must have the same value for enableYield.")
    @GenerateOperationsTestVariants({
                    @Variant(name = "A", configuration = @GenerateOperations(languageClass = ErrorLanguage.class, enableYield = true)),
                    @Variant(name = "B", configuration = @GenerateOperations(languageClass = ErrorLanguage.class))
    })
    @OperationProxy(ConstantOperation.class)
    public static abstract class DifferentYield extends RootNode implements OperationRootNode {
        protected DifferentYield(TruffleLanguage<?> language, FrameDescriptor frameDescriptor) {
            super(language, frameDescriptor);
        }
    }

    // no errors expected
    @GenerateOperationsTestVariants({
                    @Variant(name = "Tier1", configuration = @GenerateOperations(languageClass = ErrorLanguage.class)),
                    @Variant(name = "Tier0", configuration = @GenerateOperations(languageClass = ErrorLanguage.class, enableBaselineInterpreter = true))
    })
    @OperationProxy(ConstantOperation.class)
    public static abstract class DifferentBaselineInterpreters extends RootNode implements OperationRootNode {
        protected DifferentBaselineInterpreters(TruffleLanguage<?> language, FrameDescriptor frameDescriptor) {
            super(language, frameDescriptor);
        }
    }

    public class ErrorLanguage extends TruffleLanguage<Object> {
        @Override
        protected Object createContext(Env env) {
            return null;
        }
    }

    public class AnotherErrorLanguage extends TruffleLanguage<Object> {
        @Override
        protected Object createContext(Env env) {
            return null;
        }
    }

}

@SuppressWarnings("truffle-inlining")
abstract class ConstantOperation extends Node {
    public abstract long execute();

    @Specialization
    public static long doLong() {
        return 42L;
    }
}
