package com.oracle.truffle.api.dsl.test.interop;

import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.TruffleObject;

public class ExampleTruffleObject implements TruffleObject {
    static final String MEMBER_NAME = "value";

    private int value = 0;

    void setValue(int value) {
        this.value = value;
    }

    int getValue() {
        return value;
    }

    // BEGIN: getForeignAccessMethod
    public ForeignAccess getForeignAccess() {
        return ExampleTruffleObjectForeign.ACCESS;
    }
    // END: getForeignAccessMethod

    // BEGIN: isInstanceCheck
    public static boolean isInstance(TruffleObject obj) {
        return obj instanceof ExampleTruffleObject;
    }
    // END: isInstanceCheck
}
