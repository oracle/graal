package com.oracle.truffle.api.dsl.test.interop;

import com.oracle.truffle.api.dsl.test.ExpectError;
import com.oracle.truffle.api.interop.AcceptMessage;

@AcceptMessage(value = "IS_BOXED", receiverType = ValidTruffleObject.class, language = TestTruffleLanguage.class)
public final class IsBoxed2 extends BaseIsBoxed2 {
    @SuppressWarnings({"static-method", "unused"})
    @ExpectError({"The first argument of access must be of type com.oracle.truffle.api.frame.VirtualFrame- but is java.lang.String"})
    public Object access(String frame, ValidTruffleObject object) {
        return true;
    }
}
