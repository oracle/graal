package com.oracle.truffle.espresso.substitutions;

@EspressoSubstitutions
public final class Target_sun_nio_ch_NativeThread {

    @Substitution
    public static void init() {
        // nop
    }
}
