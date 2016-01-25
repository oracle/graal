package com.oracle.truffle.api.dsl.test.interop;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.AcceptMessage;

@AcceptMessage(value = "WRITE", receiverType = ValidTruffleObject.class, language = TestTruffleLanguage.class)
public final class WriteNode3 extends BaseWriteNode3 {

    @Override
    protected int access(VirtualFrame vf, Object receiver, Object name, Object value) {
        return 0;
    }
}