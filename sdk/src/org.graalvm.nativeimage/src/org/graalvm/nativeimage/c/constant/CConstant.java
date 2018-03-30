/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.nativeimage.c.constant;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.impl.CConstantValueSupport;

/**
 * Denotes a method as a C constant value.
 * <p>
 * Calls to the method are replaced with a compile time constant. The constant value is extracted
 * from the C header file during native image generation.
 * <p>
 * The class containing the annotated method must be annotated with {@link CContext}.
 *
 * @since 1.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface CConstant {

    /**
     * Specifies the name of the C constant. If no name is provided, the method name is used as the
     * field name. A possible "get" prefix of the method name is removed.
     *
     * @since 1.0
     */
    String value() default "";

    /**
     * Allows access to the value of a {@link CConstant} during image generation.
     *
     * @since 1.0
     */
    final class ValueAccess {
        private ValueAccess() {
        }

        /**
         * Returns the value of a {@link CConstant}, i.e., the same value that calling the annotated
         * method would return.
         * <p>
         * This method is useful during native image generation, when the annotated method cannot be
         * called.
         *
         * @param declaringClass The class that contains the method annotated with {@link CConstant}
         *            .
         * @param methodName The name of the method annotated with {@link CConstant}.
         * @param returnType The desired type of the returned value. For integer-kind constants, the
         *            supported types are {@link Long}, {@link Integer}, and {@link Boolean}. For
         *            floating point constants, the only supported type is {@link Double}. For
         *            string constants, the only supported type is {@link String}.
         * @return The value of the C constant.
         *
         * @since 1.0
         */
        @Platforms(Platform.HOSTED_ONLY.class)
        public static <T> T get(Class<?> declaringClass, String methodName, Class<T> returnType) {
            return ImageSingletons.lookup(CConstantValueSupport.class).getCConstantValue(declaringClass, methodName, returnType);
        }
    }
}
