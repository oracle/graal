/*
 * Copyright (c) 2007, 2019, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.nativeimage.c.function;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.constant.CEnum;
import org.graalvm.word.WordBase;

/**
 * Denotes a {@code native} method that calls directly from Java to C, without following the JNI
 * protocol. This means that there are no artificial additional parameters such as the JNI
 * environment passed, and no marshaling or processing of arguments (such as creating handles for
 * objects) is performed. If the method is non-static, the receiver will be ignored.
 * <p>
 * Parameter types and return types must not be Java reference types, only
 * {@linkplain Class#isPrimitive() primitive} Java types, {@linkplain WordBase word types} and
 * {@link CEnum} types are allowed. The representation of passed primitive values matches exactly
 * how they are specified in the Java language, for example, {@code int} as a 32-bit signed integer
 * or {@code char} as a 16-bit unsigned integer. {@code boolean} is specified as a single byte that
 * corresponds to {@code true} if non-zero, and to {@code false} if zero. If a Word value is passed
 * that points to a Java object, no guarantees are taken regarding its integrity as a pointer.
 * <p>
 * The class containing the annotated method must be annotated with {@link CContext}.
 *
 * @since 19.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface CFunction {

    /**
     * Describes the thread state transition performed when the C function is invoked.
     *
     * @since 19.0
     */
    enum Transition {
        /**
         * The thread state is transitioned from Java to C, and the Java parts of the stack are made
         * walkable. The C code can block and call back to Java.
         *
         * @since 19.0
         */
        TO_NATIVE,
        /**
         * No prologue and epilogue is emitted. The C code must not block and must not call back to
         * Java. Also, long running C code delays safepoints (and therefore garbage collection) of
         * other threads until the call returns.
         *
         * @since 19.0
         */
        NO_TRANSITION,
    }

    /**
     * The symbol name to use to link this method. If no value is specified, the name of the method
     * (without name mangling or a class name prefix) is used.
     *
     * @since 19.0
     */
    String value() default "";

    /**
     * The Java-to-C thread transition code used when calling the function.
     *
     * @since 19.0
     */
    Transition transition() default Transition.TO_NATIVE;
}
