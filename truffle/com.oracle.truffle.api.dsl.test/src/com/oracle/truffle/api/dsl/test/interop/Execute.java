package com.oracle.truffle.api.dsl.test.interop;

import com.oracle.truffle.api.dsl.test.ExpectError;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.AcceptMessage;

@AcceptMessage(value = "EXECUTE", receiverType = ValidTruffleObject.class, language = TestTruffleLanguage.class)
public final class Execute extends BaseExecute {
    @SuppressWarnings({"static-method", "unused"})
    @ExpectError({"access method has to have 3 arguments"})
    public Object access(VirtualFrame frame, ValidTruffleObject object) {
        return true;
    }
}
