package com.oracle.truffle.api.dsl.test.interop;

import com.oracle.truffle.api.dsl.test.ExpectError;
import com.oracle.truffle.api.interop.AcceptMessage;

@ExpectError("There needs to be at least one access method.")
@AcceptMessage(value = "READ", receiverType = ValidTruffleObject.class, language = TestTruffleLanguage.class)
public final class ReadNode extends BaseReadNode {
}