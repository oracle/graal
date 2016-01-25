package com.oracle.truffle.api.dsl.test.interop;

import com.oracle.truffle.api.dsl.test.ExpectError;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.AcceptMessage;

@AcceptMessage(value = "INVOKE", receiverType = ValidTruffleObject.class, language = TestTruffleLanguage.class)
public final class Invoke3 extends BaseInvoke3 {
    @SuppressWarnings({"static-method", "unused"})
    @ExpectError({"The last argument must be the arguments array. Required type: java.lang.Object[]- but is java.lang.String"})
    public Object access(VirtualFrame frame, ValidTruffleObject object, String name, String args) {
        return true;
    }
}
