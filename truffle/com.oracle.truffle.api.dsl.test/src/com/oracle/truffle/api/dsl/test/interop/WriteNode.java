package com.oracle.truffle.api.dsl.test.interop;

import com.oracle.truffle.api.dsl.test.ExpectError;
import com.oracle.truffle.api.interop.AcceptMessage;

@AcceptMessage(value = "WRITE", receiverType = ValidTruffleObject.class, language = TestTruffleLanguage.class)
public final class WriteNode extends BaseWriteNode {

    @SuppressWarnings({"static-method", "unused"})
    @ExpectError({"The first argument must be a com.oracle.truffle.api.frame.VirtualFrame- but is java.lang.String"})
    protected int access(String vf, Object receiver, Object name, Object value) {
        return 0;
    }
}