package com.oracle.truffle.api.dsl.test.interop;

import com.oracle.truffle.api.dsl.test.ExpectError;
import com.oracle.truffle.api.interop.AcceptMessage;

@ExpectError({"Class must be final"})
@AcceptMessage(value = "READ", receiverType = ValidTruffleObject.class, language = TestTruffleLanguage.class)
public class ReadNode3 extends BaseReadNode3 {
}
