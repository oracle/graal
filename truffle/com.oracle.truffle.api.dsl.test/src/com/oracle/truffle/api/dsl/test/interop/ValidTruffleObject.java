package com.oracle.truffle.api.dsl.test.interop;

import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.TruffleObject;

public class ValidTruffleObject implements TruffleObject {

    public ForeignAccess getForeignAccess() {
        return null;
    }

    public static boolean isInstance(TruffleObject obj) {
        return obj instanceof ValidTruffleObject;
    }

}
