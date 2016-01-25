package com.oracle.truffle.api.dsl.test.interop;

import com.oracle.truffle.api.dsl.test.ExpectError;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.AcceptMessage;

@ExpectError("Missing isInstance method in class com.oracle.truffle.api.dsl.test.interop.InvalidTruffleObject")
@AcceptMessage(value = "READ", receiverType = InvalidTruffleObject.class, language = TestTruffleLanguage.class)
public final class ReadNode2 extends BaseReadNode2 {

    @SuppressWarnings({"static-method", "unused"})
    protected int access(VirtualFrame vf, Object receiver, Object name) {
        return 0;
    }
}