package com.oracle.truffle.api.dsl.test.interop;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.AcceptMessage;

@AcceptMessage(value = "EXECUTE", receiverType = ValidTruffleObject.class, language = TestTruffleLanguage.class)
public final class Execute5 extends BaseExecute5 {
    @Override
    public Object access(VirtualFrame frame, ValidTruffleObjectB object, Object[] args) {
        return true;
    }

    @Override
    public Object access(VirtualFrame frame, ValidTruffleObject object, Object[] args) {
        return true;
    }
}
