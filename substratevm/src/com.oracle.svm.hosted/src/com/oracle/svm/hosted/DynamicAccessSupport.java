package com.oracle.svm.hosted;

import com.oracle.svm.core.util.UserError;

public final class DynamicAccessSupport {
    private static boolean afterRegistrationFinished = false;

    static void setAfterRegistrationFinished() {
        afterRegistrationFinished = true;
    }

    public static void printUserError(String registrationEntry) {
        UserError.guarantee(!afterRegistrationFinished, "Registration for runtime access after Feature#afterRegistration is not allowed. You tried to register: %s", registrationEntry);
    }
}
