package com.oracle.truffle.api.dsl.test.interop;

import com.oracle.truffle.api.dsl.test.ExpectError;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.AcceptMessage;

@AcceptMessage(value = "INVOKE", receiverType = ValidTruffleObject.class, language = TestTruffleLanguage.class)
public final class Invoke2 extends BaseInvoke2 {
    @SuppressWarnings({"static-method", "unused"})
    @ExpectError({"The third argument must be a java.lang.String- but is int"})
    public Object access(VirtualFrame frame, ValidTruffleObject object, int name, Object[] args) {
        return true;
    }
}
