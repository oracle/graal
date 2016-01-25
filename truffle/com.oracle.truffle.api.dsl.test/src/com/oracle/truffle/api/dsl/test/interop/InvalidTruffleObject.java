package com.oracle.truffle.api.dsl.test.interop;

import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.TruffleObject;

public class InvalidTruffleObject implements TruffleObject {

    public ForeignAccess getForeignAccess() {
        return null;
    }
}