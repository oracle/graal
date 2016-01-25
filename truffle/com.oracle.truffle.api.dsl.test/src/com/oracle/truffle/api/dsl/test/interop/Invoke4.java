package com.oracle.truffle.api.dsl.test.interop;

import com.oracle.truffle.api.dsl.test.ExpectError;
import com.oracle.truffle.api.interop.AcceptMessage;

@AcceptMessage(value = "INVOKE", receiverType = ValidTruffleObject.class, language = TestTruffleLanguage.class)
public final class Invoke4 extends BaseInvoke4 {

    @SuppressWarnings({"static-method", "unused"})
    @ExpectError({"The first argument must be a com.oracle.truffle.api.frame.VirtualFrame- but is java.lang.String"})
    protected int access(String vf, ValidTruffleObject receiver, Object name, Object[] args) {
        return 0;
    }
}