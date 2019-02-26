package com.oracle.truffle.espresso.substitutions;

@EspressoSubstitutions
public class Target_sun_awt_SunToolkit {
    @Substitution
    public static void closeSplashScreen() {
        /* nop */
    }
}
