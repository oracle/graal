package com.oracle.truffle.api.operation.test.subpackage;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.operation.OperationProxy;
import com.oracle.truffle.api.operation.test.ExpectError;

@OperationProxy.Proxyable
public final class NonPublicGuardExpressionOperationProxy {
    @Specialization(guards = "guardCondition()")
    public static int addGuarded(int x, int y) {
        return x + y;
    }

    protected static boolean guardCondition() {
        return true;
    }
}
