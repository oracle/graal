package com.oracle.truffle.api.operation.test.subpackage;

import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.operation.test.ExpectError;

/**
 * This node is used in {@link ErrorTests}. Since it is declared in a separate package, the
 * non-public specializations are not visible and should cause an error.
 */
public final class NonPublicSpecializationOperationProxy {
    @Specialization
    @ExpectError("Operation specialization is not visible to the generated Operation node.")
    static int add(int x, int y) {
        return x + y;
    }

    @Fallback
    @ExpectError("Operation specialization is not visible to the generated Operation node.")
    static Object fallback(Object a, Object b) {
        return a;
    }
}