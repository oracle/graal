package com.oracle.truffle.api.operation.test.example;

import com.oracle.truffle.api.operation.GenerateOperations;
import com.oracle.truffle.api.operation.Operation;

// TODO: where to attach this once we don't have a wrapper class?
@GenerateOperations
class SlOperations {
    @Operation
    static class AddOperation {
        public static Object add(Object lhs, Object rhs) {
            return (long) lhs + (long) rhs;
        }
    }

    @Operation
    static class LessThanOperation {
        public static Object lessThan(Object lhs, Object rhs) {
            return (long) lhs < (long) rhs;
        }
    }
}