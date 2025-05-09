package com.oracle.svm.hosted;

import java.util.Objects;

import org.graalvm.nativeimage.hosted.RegistrationCondition;
import org.graalvm.nativeimage.hosted.ResourceDynamicAccess;
import org.graalvm.nativeimage.impl.RuntimeResourceSupport;

public class InternalResourceDynamicAccess implements ResourceDynamicAccess {

    private static RuntimeResourceSupport<RegistrationCondition> rrsInstance;

    InternalResourceDynamicAccess() {
        rrsInstance = RuntimeResourceSupport.singleton();
    }

    @Override
    public void register(RegistrationCondition condition, Module module, String pattern) {
        Objects.requireNonNull(pattern);
        if (pattern.replace("\\*", "").contains("*")) {
            String moduleName = module == null ? null : module.getName();
            rrsInstance.addGlob(condition, moduleName, pattern, "Registered from API");
        } else {
            rrsInstance.addResource(condition, module, pattern.replace("\\*", "*"), "Registered from API");
        }
    }

    @Override
    public void registerResourceBundle(RegistrationCondition condition, Module module, String bundleName) {
        Objects.requireNonNull(bundleName);
        String finalBundleName = (module != null && module.isNamed()) ? module.getName() + ":" + bundleName : bundleName;
        rrsInstance.addResourceBundles(condition, finalBundleName);
    }
}
