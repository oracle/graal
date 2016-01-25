package com.oracle.truffle.api.dsl.test.interop;

import com.oracle.truffle.api.dsl.test.ExpectError;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.AcceptMessage;

@AcceptMessage(value = "WRITE", receiverType = ValidTruffleObject.class, language = TestTruffleLanguage.class)
public final class WriteNode2 extends BaseWriteNode2 {

    @SuppressWarnings({"static-method", "unused"})
    @ExpectError({"access method has to have 4 arguments"})
    protected int access(VirtualFrame vf, Object receiver, Object name) {
        return 0;
    }
}