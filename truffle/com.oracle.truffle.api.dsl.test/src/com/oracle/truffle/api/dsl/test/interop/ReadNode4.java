package com.oracle.truffle.api.dsl.test.interop;

import com.oracle.truffle.api.dsl.test.ExpectError;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.AcceptMessage;

@AcceptMessage(value = "READ", receiverType = ValidTruffleObject.class, language = TestTruffleLanguage.class)
public final class ReadNode4 extends BaseReadNode4 {

    @SuppressWarnings({"static-method", "unused"})
    @ExpectError({"access method has to have 3 arguments"})
    protected int access(VirtualFrame vf, Object receiver, Object name, int i) {
        return 0;
    }
}