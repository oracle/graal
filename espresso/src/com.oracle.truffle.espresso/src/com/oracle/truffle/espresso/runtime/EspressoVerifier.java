package com.oracle.truffle.espresso.runtime;

import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.shared.perf.DebugCloseable;
import com.oracle.truffle.espresso.shared.perf.DebugTimer;
import com.oracle.truffle.espresso.shared.verifier.VerificationException;
import com.oracle.truffle.espresso.shared.verifier.Verifier;

public final class EspressoVerifier {
    public static final DebugTimer VERIFIER_TIMER = DebugTimer.create("verifier");

    @SuppressWarnings({"unused", "try"})
    public static void verify(EspressoContext ctx, Method method) {
        try (DebugCloseable t = VERIFIER_TIMER.scope(ctx.getTimers())) {
            Verifier.verify(ctx, method);
        } catch (VerificationException e) {
            Meta meta = ctx.getMeta();
            String message = String.format("Verification for class `%s` failed for method `%s` with message `%s`",
                            method.getDeclaringKlass().getExternalName(),
                            method.getNameAsString(),
                            e.getMessage());
            switch (e.kind()) {
                case Verify:
                    throw meta.throwExceptionWithMessage(meta.java_lang_VerifyError, message);
                case ClassFormat:
                    throw meta.throwExceptionWithMessage(meta.java_lang_ClassFormatError, message);
                case NoClassDefFound:
                    throw meta.throwExceptionWithMessage(meta.java_lang_NoClassDefFoundError, message);
            }
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

    private EspressoVerifier() {
    }
}
