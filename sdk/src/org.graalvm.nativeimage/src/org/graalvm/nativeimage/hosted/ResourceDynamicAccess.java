package org.graalvm.nativeimage.hosted;

/**
 * This interface is used to register Java resources and {@link java.util.ResourceBundle} that
 * should be accessible at runtime.
 *
 * All methods in {@link ResourceDynamicAccess} require a {@link RegistrationCondition} as their
 * first parameter. A class and its members will be registered for dynamic access only if the
 * specified condition is satisfied.
 *
 * {@link ResourceDynamicAccess} should only be used during {@link Feature#afterRegistration}. Any
 * attempt of registration in any other phase will result in an error.
 */
public interface ResourceDynamicAccess {

    /**
     * Registers resources from the given {@code module} described with the provided {@code glob},
     * for dynamic access, if the {@code condition} is satisfied.
     *
     * @param condition represents the condition that needs to be satisfied in order to register
     *            target resources.
     * @param module represents the Java module instance that contains target resources. If the
     *            provided value is {@code null} or an unnamed module, resources are looked up on
     *            the classpath instead.
     * @param glob can either be exact Java resource path, or a glob that contains wildcards such as
     *            star(*) or globstar(**). Find glob syntax explanation <a href=
     *            "/https://www.graalvm.org/latest/reference-manual/native-image/metadata/#resources">here</a>.
     */
    void register(RegistrationCondition condition, Module module, String glob);

    /**
     * Registers resources from the classpath described with the provided {@code glob}, for dynamic
     * access, if the {@code condition} is satisfied.
     *
     * @param condition represents the condition that needs to be satisfied in order to register
     *            target resources.
     * @param glob can either be exact Java resource path, or glob that contains wildcards such as
     *            star(*) or globstar(**). Find glob syntax explanation <a href=
     *            "/https://www.graalvm.org/latest/reference-manual/native-image/metadata/#resources">here</a>.
     */
    default void register(RegistrationCondition condition, String glob) {
        register(condition, null, glob);
    }

    /**
     * Registers {@link java.util.ResourceBundle} that is specified by a {@code bundleName} from the
     * provided {@code module} for dynamic access, if the {@code condition} is satisfied. If the
     * given {@code module} is unnamed or the value is {@code null}, the
     * {@link java.util.ResourceBundle} is looked up on the classpath instead.
     */
    void registerResourceBundle(RegistrationCondition condition, Module module, String bundleName);

    /**
     * Registers {@link java.util.ResourceBundle} that is specified by a {@code bundleName} from the
     * classpath for dynamic access if the {@code condition} is satisfied.
     */
    default void registerResourceBundle(RegistrationCondition condition, String bundleName) {
        registerResourceBundle(condition, null, bundleName);
    }
}
