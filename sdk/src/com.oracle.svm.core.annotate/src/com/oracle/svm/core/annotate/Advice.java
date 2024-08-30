/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2024, Alibaba Group Holding Limited. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package com.oracle.svm.core.annotate;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

public class Advice {

    public enum NotFoundAction {
        error, // throw an exception
        ignore, // ignore, nothing will be reported
        info    // report as a log message
    }

    public static class NoException extends Throwable {
        private static final long serialVersionUID = 2072039301986965302L;
    }

    /**
     * This indicates if a substitution method is for agent support. User provided advice methods
     * could be conflicted with the existing substitution methods. In this case, the advice method
     * shall be ignored. This annotation is added by GraalVM framework when it generates the
     * substitutions from advice methods.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.METHOD, ElementType.CONSTRUCTOR})
    @Platforms(Platform.HOSTED_ONLY.class)
    public @interface ForAgentSupport {

    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.METHOD, ElementType.CONSTRUCTOR})
    @Platforms(Platform.HOSTED_ONLY.class)
    public @interface Before {
        /**
         * The names of method to insert before. This value overrides the name of its annotated
         * method.
         * 
         * @return
         */
        String[] value() default {};

        /**
         * The action to take when the specified method is not found.
         * 
         * @return
         */
        NotFoundAction notFoundAction() default NotFoundAction.error;

        /**
         * Specify the returning type criteria. It must be a {@link BooleanSupplier} or
         * {@link Predicate} class.
         * 
         * @return
         */
        Class<?>[] onlyWithReturnType() default TargetClass.AlwaysIncluded.class;
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.METHOD, ElementType.CONSTRUCTOR})
    @Platforms(Platform.HOSTED_ONLY.class)
    public @interface After {
        /**
         * The names of method to insert before. This value overrides the name of its annotated
         * method.
         * 
         * @return
         */
        String[] value() default {};

        /**
         * The action to take when the specified method is not found.
         * 
         * @return
         */
        NotFoundAction notFoundAction() default NotFoundAction.error;

        /**
         * Specify the returning type criteria. It must be a {@link BooleanSupplier} or
         * {@link Predicate} class.
         * 
         * @return
         */
        Class<?>[] onlyWithReturnType() default TargetClass.AlwaysIncluded.class;

        /**
         * Allow advice method to be invoked when the indicated {@link Throwable} class is thrown
         * from the original method. By default, the after advice method won't be invoked if any
         * exception is thrown from the original method.
         * 
         * @return
         */
        Class<? extends Throwable> onThrowable() default NoException.class;
    }

    /**
     * Represent returned value from the target method. This can only appear in the parameter of
     * {@link After} annotated method.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.PARAMETER})
    @Platforms(Platform.HOSTED_ONLY.class)
    public @interface Return {
    }

    /**
     * Represent returned value from its corresponding {@link Before} method. This can only appear
     * in the parameter of {@link After} annotated method.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.PARAMETER, ElementType.FIELD})
    @Platforms(Platform.HOSTED_ONLY.class)
    public @interface BeforeResult {
    }

    /**
     * Represent the {@code this} reference of the original method.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.PARAMETER})
    @Platforms(Platform.HOSTED_ONLY.class)
    public @interface This {
    }

    /**
     * Represent the exception value thrown by the original method. The annotated parameter type
     * must be the same as the returning type of {@link After#onThrowable()}. I.e.
     * {@link After#onThrowable()} must be present in order to use this annotation.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.PARAMETER})
    @Platforms(Platform.HOSTED_ONLY.class)
    public @interface Thrown {
    }

    /**
     * Indicate the annotated parameter shall be rewritten to a new value and pass to the original
     * method. This must be annotated on the parameter in {@link Before} method. It takes effects
     * together with {@link ResultWrapper}, i.e. another class annotated with {@link ResultWrapper}
     * must present to hold the rewritten parameter values.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.PARAMETER})
    @Platforms(Platform.HOSTED_ONLY.class)
    public @interface Rewrite {
        /**
         * Indicate the {@link ResultWrapper} class' field name that holds this parameter's
         * rewritten value.
         * 
         * @return
         */
        String field();
    }

    /**
     * Annotated on the class holds {@link Rewrite} parameter values and the returning
     * {@link BeforeResult} value in {@link Before} method.
     * <p>
     * An instance of the annotated class is created, filled and returned by the {@link Before}
     * method.
     * <p>
     * The annotated class and all its fields <b>must</b> all be <b>public</b> to other classes.
     * <p>
     * In the annotated class, field annotated with {@link BeforeResult} holds value for
     * {@link BeforeResult} parameter in {@link After} method. Other fields correspond to the
     * {@link Rewrite} parameter according to field name specified by {@link Rewrite#field()}. And
     * they must be of {@link Optional} type. A {@code null} value of the field means the parameter
     * is not rewritten.
     *
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE})
    @Platforms(Platform.HOSTED_ONLY.class)
    public @interface ResultWrapper {
    }

    /**
     * Check if {@code clazz} implements interface {@code interfaceClass} directly or indirectly.
     *
     * @param interfaceClass interface class
     * @param clazz the class to check
     * @return true if {@code clazz} implements {@code interfaceClass} directly or indirectly.
     */
    public static boolean isInterfaceOf(Class<?> interfaceClass, Class<?> clazz) {
        if (clazz == null || clazz.equals(Object.class)) {
            return false;
        }
        // The fast path, if clazz directly implements interfaceClass
        for (Class<?> anInterface : clazz.getInterfaces()) {
            if (interfaceClass.equals(anInterface)) {
                return true;
            }
        }

        // Check the interface hierarchy
        for (Class<?> anInterface : clazz.getInterfaces()) {
            if (isInterfaceOf(interfaceClass, anInterface)) {
                return true;
            }
        }

        // Check the super class hierarchy
        return isInterfaceOf(interfaceClass, clazz.getSuperclass());
    }
}
