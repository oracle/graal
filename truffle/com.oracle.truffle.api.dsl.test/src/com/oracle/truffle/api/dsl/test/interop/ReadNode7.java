package com.oracle.truffle.api.dsl.test.interop;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.AcceptMessage;

@AcceptMessage(value = "READ", receiverType = ValidTruffleObject.class, language = TestTruffleLanguage.class)
public final class ReadNode7 extends BaseReadNode7 {

    @Override
    protected Object access(VirtualFrame vf, ValidTruffleObjectB receiver, Object name) {
        return 0;
    }

    @Override
    protected Object access(VirtualFrame vf, ValidTruffleObject receiver, Object name) {
        return 0;
    }

    @Override
    protected Object access(VirtualFrame vf, Object receiver, Object name) {
        return 0;
    }
}