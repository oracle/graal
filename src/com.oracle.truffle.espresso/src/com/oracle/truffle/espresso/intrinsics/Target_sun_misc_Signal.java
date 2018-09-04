package com.oracle.truffle.espresso.intrinsics;

import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.runtime.Utils;

import sun.misc.Signal;

@EspressoIntrinsics
public class Target_sun_misc_Signal {
    @Intrinsic
    public static int findSignal(@Type(String.class) StaticObject name) {
        return new Signal(Meta.toHost(name)).getNumber();
    }

    @Intrinsic
    public static long handle0(int sig, long nativeH) {
        // TODO(peterssen): Find out how to properly manage host/guest signals.
        /* nop */
        return 0;
    }
}
