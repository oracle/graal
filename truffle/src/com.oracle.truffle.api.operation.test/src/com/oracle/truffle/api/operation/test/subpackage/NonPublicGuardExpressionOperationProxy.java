package com.oracle.truffle.api.operation.test.subpackage;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.operation.test.ExpectError;

@ExpectError("Message redirected from element NonPublicGuardExpressionOperationProxy.addGuarded(int, int):\nError parsing expression 'guardCondition()': The method guardCondition() is not visible.")
public final class NonPublicGuardExpressionOperationProxy {
    @Specialization(guards = "guardCondition()")
    public static int addGuarded(int x, int y) {
        return x + y;
    }

    protected static boolean guardCondition() {
        return true;
    }
}
