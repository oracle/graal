package com.oracle.truffle.api.dsl.test.interop;

import com.oracle.truffle.api.dsl.test.ExpectError;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.AcceptMessage;

@AcceptMessage(value = "INVOKE", receiverType = ValidTruffleObject.class, language = TestTruffleLanguage.class)
public final class Invoke extends BaseInvoke {
    @SuppressWarnings({"static-method", "unused"})
    @ExpectError({"access method has to have 4 arguments"})
    public Object access(VirtualFrame frame, ValidTruffleObject object, String name, Object[] args, int i) {
        return true;
    }
}
