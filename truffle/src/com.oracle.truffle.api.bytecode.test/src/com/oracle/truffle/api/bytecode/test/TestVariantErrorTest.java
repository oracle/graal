package com.oracle.truffle.api.bytecode.test;

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.bytecode.GenerateBytecode;
import com.oracle.truffle.api.bytecode.GenerateBytecodeTestVariants;
import com.oracle.truffle.api.bytecode.OperationProxy;
import com.oracle.truffle.api.bytecode.BytecodeRootNode;
import com.oracle.truffle.api.bytecode.GenerateBytecodeTestVariants.Variant;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;

public class TestVariantErrorTest {

    @ExpectError("A variant with suffix \"A\" already exists. Each variant must have a unique suffix.")
    @GenerateBytecodeTestVariants({
                    @Variant(suffix = "A", configuration = @GenerateBytecode(languageClass = ErrorLanguage.class)),
                    @Variant(suffix = "A", configuration = @GenerateBytecode(languageClass = ErrorLanguage.class))})
    @OperationProxy(ConstantOperation.class)
    public abstract static class SameName extends RootNode implements BytecodeRootNode {
        protected SameName(TruffleLanguage<?> language, FrameDescriptor frameDescriptor) {
            super(language, frameDescriptor);
        }
    }

    @ExpectError("Incompatible variant: all variants must use the same language class.")
    @GenerateBytecodeTestVariants({
                    @Variant(suffix = "A", configuration = @GenerateBytecode(languageClass = ErrorLanguage.class)),
                    @Variant(suffix = "B", configuration = @GenerateBytecode(languageClass = AnotherErrorLanguage.class))
    })
    @OperationProxy(ConstantOperation.class)
    public abstract static class DifferentLanguage extends RootNode implements BytecodeRootNode {
        protected DifferentLanguage(TruffleLanguage<?> language, FrameDescriptor frameDescriptor) {
            super(language, frameDescriptor);
        }
    }

    @ExpectError("Incompatible variant: all variants must have the same value for enableYield.")
    @GenerateBytecodeTestVariants({
                    @Variant(suffix = "A", configuration = @GenerateBytecode(languageClass = ErrorLanguage.class, enableYield = true)),
                    @Variant(suffix = "B", configuration = @GenerateBytecode(languageClass = ErrorLanguage.class))
    })
    @OperationProxy(ConstantOperation.class)
    public abstract static class DifferentYield extends RootNode implements BytecodeRootNode {
        protected DifferentYield(TruffleLanguage<?> language, FrameDescriptor frameDescriptor) {
            super(language, frameDescriptor);
        }
    }

    // no errors expected
    @GenerateBytecodeTestVariants({
                    @Variant(suffix = "Tier1", configuration = @GenerateBytecode(languageClass = ErrorLanguage.class)),
                    @Variant(suffix = "Tier0", configuration = @GenerateBytecode(languageClass = ErrorLanguage.class, enableUncachedInterpreter = true))
    })
    @OperationProxy(ConstantOperation.class)
    public abstract static class DifferentUncachedInterpreters extends RootNode implements BytecodeRootNode {
        protected DifferentUncachedInterpreters(TruffleLanguage<?> language, FrameDescriptor frameDescriptor) {
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
@OperationProxy.Proxyable
@GenerateUncached
abstract class ConstantOperation extends Node {
    public abstract long execute();

    @Specialization
    public static long doLong() {
        return 42L;
    }
}
