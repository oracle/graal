package com.oracle.truffle.api.dsl.test.interop;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.AcceptMessage;

@AcceptMessage(value = "NEW", receiverType = ValidTruffleObject.class, language = TestTruffleLanguage.class)
public final class New extends BaseNew {
    @Override
    protected int access(VirtualFrame vf, ValidTruffleObjectB receiver, Object[] args) {
        return 0;
    }

    @Override
    protected int access(VirtualFrame vf, ValidTruffleObject receiver, Object[] args) {
        return 0;
    }
}