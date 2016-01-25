package com.oracle.truffle.api.dsl.test.interop;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.AcceptMessage;

@AcceptMessage(value = "INVOKE", receiverType = ValidTruffleObject.class, language = TestTruffleLanguage.class)
public final class Invoke6 extends BaseInvoke6 {
    @Override
    protected int access(VirtualFrame vf, ValidTruffleObjectB receiver, String name, Object[] args) {
        return 0;
    }

    @Override
    protected int access(VirtualFrame vf, ValidTruffleObject receiver, String name, Object[] args) {
        return 0;
    }
}