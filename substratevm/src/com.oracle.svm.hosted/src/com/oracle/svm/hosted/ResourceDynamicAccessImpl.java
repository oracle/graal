package com.oracle.svm.hosted;

import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RegistrationCondition;
import org.graalvm.nativeimage.hosted.ResourceDynamicAccess;

/**
 * Instance of this class is used to Java resources and ResourceBundles that should be accessible at
 * runtime. It can only be created at {@link Feature#afterRegistration} via
 * {@link Feature.AfterRegistrationAccess}.
 */
public class ResourceDynamicAccessImpl implements ResourceDynamicAccess {

    private static InternalResourceDynamicAccess rdaInstance;

    ResourceDynamicAccessImpl() {
        rdaInstance = new InternalResourceDynamicAccess();
    }

    @Override
    public void register(RegistrationCondition condition, Module module, String pattern) {
        DynamicAccessSupport.printUserError(pattern);
        rdaInstance.register(condition, module, pattern);
    }

    @Override
    public void registerResourceBundle(RegistrationCondition condition, Module module, String bundleName) {
        DynamicAccessSupport.printUserError(bundleName);
        rdaInstance.registerResourceBundle(condition, module, bundleName);
    }

}
