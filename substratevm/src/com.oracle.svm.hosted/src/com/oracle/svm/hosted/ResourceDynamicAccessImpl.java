package com.oracle.svm.hosted;

import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RegistrationCondition;
import org.graalvm.nativeimage.hosted.ResourceDynamicAccess;

import com.oracle.svm.core.util.UserError;

/**
 * Instance of this class is used to Java resources and ResourceBundles that should be accessible at
 * runtime. It can only be created at {@link Feature#afterRegistration} via
 * {@link Feature.AfterRegistrationAccess}.
 */
public class ResourceDynamicAccessImpl implements ResourceDynamicAccess {

    private static boolean afterRegistrationFinished;
    private static InternalResourceDynamicAccessImpl rdaInstance;

    ResourceDynamicAccessImpl() {
        rdaInstance = new InternalResourceDynamicAccessImpl();
        afterRegistrationFinished = false;
    }

    public static void setAfterRegistrationFinished() {
        afterRegistrationFinished = true;
    }

    @Override
    public void register(RegistrationCondition condition, Module module, String pattern) {
        UserError.guarantee(!afterRegistrationFinished, "There shouldn't be a registration for runtime access after afterRegistration period. You tried to register glob: %s", pattern);
        rdaInstance.register(condition, module, pattern);
    }

    @Override
    public void registerResourceBundle(RegistrationCondition condition, Module module, String bundleName) {
        UserError.guarantee(!afterRegistrationFinished, "There shouldn't be a registration for runtime access after afterRegistration period. You tried to register: %s",
                        bundleName);
        rdaInstance.registerResourceBundle(condition, module, bundleName);
    }

}
