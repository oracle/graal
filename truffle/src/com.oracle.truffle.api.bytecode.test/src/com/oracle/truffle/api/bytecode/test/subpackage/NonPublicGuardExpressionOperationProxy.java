package com.oracle.truffle.api.bytecode.test.subpackage;

import com.oracle.truffle.api.bytecode.OperationProxy;
import com.oracle.truffle.api.bytecode.test.ExpectError;
import com.oracle.truffle.api.dsl.Specialization;

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
