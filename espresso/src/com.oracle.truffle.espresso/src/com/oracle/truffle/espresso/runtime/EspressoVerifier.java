package com.oracle.truffle.espresso.runtime;

import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.shared.perf.DebugCloseable;
import com.oracle.truffle.espresso.shared.perf.DebugTimer;
import com.oracle.truffle.espresso.verifier.VerificationException;
import com.oracle.truffle.espresso.verifier.Verifier;

public final class EspressoVerifier {
    public static final DebugTimer VERIFIER_TIMER = DebugTimer.create("verifier");

    @SuppressWarnings({"unused", "try"})
    public static void verify(EspressoContext ctx, Method method) throws VerificationException {
        try (DebugCloseable t = VERIFIER_TIMER.scope(ctx.getTimers())) {
            Verifier.verify(ctx, method);
        }
    }

    public static boolean needsVerify(EspressoLanguage language, StaticObject classLoader) {
        switch (language.getVerifyMode()) {
            case NONE:
                return false;
            case REMOTE:
                return !StaticObject.isNull(classLoader);
            case ALL:
                return true;
            default:
                return true;
        }
    }
}
