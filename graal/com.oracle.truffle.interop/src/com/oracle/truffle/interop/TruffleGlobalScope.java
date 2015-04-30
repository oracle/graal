package com.oracle.truffle.interop;

import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.interop.*;

public interface TruffleGlobalScope {
    void exportTruffleObject(Object identifier, TruffleObject object);

    FrameSlot getFrameSlot(Object identifier);

    TruffleObject getTruffleObject(FrameSlot slot);

    boolean contains(Object identifier);
}
