package com.oracle.truffle.api.operation.test.example;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.operation.GenerateOperations;
import com.oracle.truffle.api.operation.Operation;
import com.oracle.truffle.sl.runtime.SLBigNumber;

// TODO: where to attach this once we don't have a wrapper class?
@GenerateOperations
class SlOperations {
    @Operation
    static class AddOperation {
        // @Specialization(rewriteOn = ArithmeticException.class)
        public static long add(long left, long right) {
            return Math.addExact(left, right);
        }

        // @Specialization
        @TruffleBoundary
        public static SLBigNumber add(SLBigNumber left, SLBigNumber right) {
            return new SLBigNumber(left.getValue().add(right.getValue()));
        }

        // @Specialization(guards = "isString(left, right)")
        @TruffleBoundary
        public static String add(Object left, Object right) {
            return left.toString() + right.toString();
        }

        public static boolean isString(Object a, Object b) {
            return a instanceof String || b instanceof String;
        }

        // @Fallback
        public static Object typeError(Object left, Object right) {
            // throw SLException.typeError(this, left, right);
            throw new RuntimeException("oh no: " + left + " + " + right);
        }
    }

    @Operation
    static class LessThanOperation {

        // @Specialization
        public static boolean lessThan(long left, long right) {
            return left < right;
        }

        // @Specialization
        @TruffleBoundary
        public static boolean lessThan(SLBigNumber left, SLBigNumber right) {
            return left.compareTo(right) < 0;
        }

        // @Fallback
        public static Object typeError(Object left, Object right) {
            // throw SLException.typeError(this, left, right);
            throw new RuntimeException("oh no: " + left + " < " + right);
        }
    }
}