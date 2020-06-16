/*
 * Copyright (c) 2013, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
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
 * @since 19.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface CConstant {

    /**
     * Specifies the name of the C constant. If no name is provided, the method name is used as the
     * field name. A possible "get" prefix of the method name is removed.
     *
     * @since 19.0
     */
    String value() default "";

    /**
     * Allows access to the value of a {@link CConstant} during image generation.
     *
     * @since 19.0
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
         * @since 19.0
         */
        @Platforms(Platform.HOSTED_ONLY.class)
        public static <T> T get(Class<?> declaringClass, String methodName, Class<T> returnType) {
            return ImageSingletons.lookup(CConstantValueSupport.class).getCConstantValue(declaringClass, methodName, returnType);
        }
    }
}
