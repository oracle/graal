package com.oracle.truffle.api.dsl.test.interop;

import com.oracle.truffle.api.dsl.test.ExpectError;
import com.oracle.truffle.api.interop.AcceptMessage;

@AcceptMessage(value = "EXECUTE", receiverType = ValidTruffleObject.class, language = TestTruffleLanguage.class)
public final class Execute2 extends BaseExecute2 {
    @SuppressWarnings({"static-method", "unused"})
    @ExpectError({"The first argument must be a com.oracle.truffle.api.frame.VirtualFrame- but is java.lang.String"})
    public Object access(String frame, ValidTruffleObject object, Object[] args) {
        return true;
    }
}
