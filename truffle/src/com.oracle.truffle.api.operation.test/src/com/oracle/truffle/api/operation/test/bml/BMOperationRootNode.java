package com.oracle.truffle.api.operation.test.bml;

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.operation.GenerateOperations;
import com.oracle.truffle.api.operation.Operation;
import com.oracle.truffle.api.operation.OperationRootNode;

@GenerateOperations(//
                languageClass = BenchmarkLanguage.class, //
                decisionsFile = "decisions.json", //
                boxingEliminationTypes = {int.class, boolean.class})
abstract class BMOperationRootNode extends RootNode implements OperationRootNode {

    protected BMOperationRootNode(TruffleLanguage<?> language, FrameDescriptor frameDescriptor) {
        super(language, frameDescriptor);
    }

    protected BMOperationRootNode(TruffleLanguage<?> language, FrameDescriptor.Builder frameDescriptor) {
        super(language, frameDescriptor.build());
    }

    @Operation
    static final class Add {
        @Specialization
        static int doInts(int left, int right) {
            return left + right;
        }

        @Specialization
        static Object doOther(Object left, Object right) {
            return "" + left + right;
        }
    }

    @Operation
    static final class Mod {
        @Specialization
        static int doInts(int left, int right) {
            return left % right;
        }
    }

    @Operation
    static final class AddQuickened {
        @Specialization
        static int doInts(int left, int right) {
            return left + right;
        }
    }

    @Operation
    static final class ModQuickened {
        @Specialization
        static int doInts(int left, int right) {
            return left % right;
        }
    }

    @Operation
    static final class AddBoxed {
        @Specialization
        static Object doInts(Object left, Object right) {
            return (int) left + (int) right;
        }
    }

    @Operation
    static final class ModBoxed {
        @Specialization
        static Object doInts(Object left, Object right) {
            return (int) left % (int) right;
        }
    }

    @Operation
    static final class Less {
        @Specialization
        static boolean doInts(int left, int right) {
            return left < right;
        }
    }
}