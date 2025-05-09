package com.oracle.svm.hosted;

import java.util.Objects;

import org.graalvm.nativeimage.hosted.RegistrationCondition;
import org.graalvm.nativeimage.hosted.ResourceDynamicAccess;
import org.graalvm.nativeimage.impl.RuntimeResourceSupport;

public class InternalResourceDynamicAccessImpl implements ResourceDynamicAccess {

    private static RuntimeResourceSupport<RegistrationCondition> rrsInstance;

    InternalResourceDynamicAccessImpl() {
        rrsInstance = RuntimeResourceSupport.singleton();
    }

    @Override
    public void register(RegistrationCondition condition, Module module, String pattern) {
        if (pattern.replace("\\*", "").contains("*")) {
            String moduleName = module == null ? null : module.getName();
            rrsInstance.addGlob(condition, moduleName, pattern, "Registered from API");
        } else {
            rrsInstance.addResource(condition, module, pattern.replace("\\*", "*"), "Registered from API");
        }
    }

    @Override
    public void registerResourceBundle(RegistrationCondition condition, Module module, String bundleName) {
        rrsInstance.addResourceBundles(condition, withModuleName(module, bundleName));
    }

    private static String withModuleName(Module module, String str) {
        Objects.requireNonNull(str);
        boolean isNamed = module == null ? false : module.isNamed();
        return ((isNamed) ? module.getName() : "ALL-UNNAMED") + ":" + str;
    }
}
