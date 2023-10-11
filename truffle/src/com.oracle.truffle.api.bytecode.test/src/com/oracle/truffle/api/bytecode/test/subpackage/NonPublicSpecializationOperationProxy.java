package com.oracle.truffle.api.bytecode.test.subpackage;

import com.oracle.truffle.api.bytecode.OperationProxy;
import com.oracle.truffle.api.bytecode.test.ExpectError;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;

/**
 * This node is used in {@link ErrorTests}. Since it is declared in a separate package, the
 * non-public specializations are not visible and should cause an error.
 */
@OperationProxy.Proxyable
public final class NonPublicSpecializationOperationProxy {
    @Specialization
    static int add(int x, int y) {
        return x + y;
    }

    @Fallback
    @SuppressWarnings("unused")
    static Object fallback(Object a, Object b) {
        return a;
    }
}
