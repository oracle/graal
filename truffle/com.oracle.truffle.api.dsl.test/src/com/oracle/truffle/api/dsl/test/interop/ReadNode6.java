package com.oracle.truffle.api.dsl.test.interop;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.AcceptMessage;

@AcceptMessage(value = "READ", receiverType = ValidTruffleObject.class, language = TestTruffleLanguage.class)
public final class ReadNode6 extends BaseReadNode6 {

    @Override
    protected Object access(VirtualFrame vf, Object receiver, Object name) {
        return 0;
    }
}