package org.graalvm.compiler.test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Specifies packages concealed in JDK modules used by a test. The mx unit test runner will ensure
 * the packages are exported to the module containing annotated test class.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface AddExports {
    /**
     * The qualified name of the concealed package in {@code <module>/<package>} format (e.g.,
     * "jdk.vm.ci/jdk.vm.ci.code").
     */
    String[] value() default "";
}
