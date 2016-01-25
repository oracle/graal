package com.oracle.truffle.api.dsl.test.interop;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.AcceptMessage;

@AcceptMessage(value = "UNBOX", receiverType = ValidTruffleObject.class, language = TestTruffleLanguage.class)
public final class Unbox extends BaseUnbox {
    @Override
    public Object access(VirtualFrame frame, ValidTruffleObject object) {
        return 0;
    }
}
