package com.oracle.truffle.api.dsl.test.interop;

import com.oracle.truffle.api.dsl.test.ExpectError;
import com.oracle.truffle.api.interop.AcceptMessage;

@ExpectError({"Unknown message type: unknownMsg"})
@AcceptMessage(value = "unknownMsg", receiverType = ValidTruffleObject.class, language = TestTruffleLanguage.class)
public final class UnknownMessage extends BaseUnknownMessage {
}
