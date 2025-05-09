package org.graalvm.nativeimage.hosted;

/**
 * This interface is used to register Java resources and ResourceBundles that should be accessible
 * at runtime.
 *
 * {@link ResourceDynamicAccess} is based on {@link RegistrationCondition}. Registration of any
 * resource for dynamic access will succeed only if condition is met.
 *
 * {@link ResourceDynamicAccess} should only be used at {@link Feature#afterRegistration}.
 */
public interface ResourceDynamicAccess {

    /**
     * If {@code pattern} contains any wildcard patterns, such as star(*) or globstar(**), pattern
     * is treated as glob pattern from {@code module} and it will be registered if {@code condition}
     * is satisfied. All resources that match the glob are available at runtime. Otherwise, pattern
     * represents Java resource from {@code module} that will be available at run time if
     * {@code condition} is satisfied. If the given {@code module} is omitted or null, Java resource
     * is looked up on the classpath instead.
     */
    void register(RegistrationCondition condition, Module module, String pattern);

    default void register(RegistrationCondition condition, String pattern) {
        register(condition, null, pattern);
    }

    /**
     * Makes Java ResourceBundle that is specified by a {@code bundleName} from module
     * {@code module} available at run time if {@code condition} is satisfied. If the given
     * {@code module} is omitted or null, the ResourceBundle is looked up on the classpath instead.
     */
    void registerResourceBundle(RegistrationCondition condition, Module module, String bundleName);

    default void registerResourceBundle(RegistrationCondition condition, String bundleName) {
        registerResourceBundle(condition, null, bundleName);
    }
}
