/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.bytecode;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.oracle.truffle.api.bytecode.ForceQuickening.Repeat;
import com.oracle.truffle.api.dsl.Specialization;

/**
 * Forces quickening for an {@link Operation} {@link Specialization specialization}. To quicken a
 * combination of specializations, use the same {@link #value() name}. If no name is specified then
 * only the annotated specialization is quickened. It is possible to specify multiple quickenings
 * per specialization (e.g., if a specialization is quickened individually and in a group of
 * specializations).
 *
 * For example, the following code declares two quickenings: one that supports only {@code ints}
 * (the plain {@code @ForceQuickening} on {@code doInts}), and another that supports both
 * {@code ints} and {@code doubles} ({@code @ForceQuickening("primitives")}):
 *
 * <pre>
 * &#64;Operation
 * public static final class Add {
 *     &#64;Specialization
 *     &#64;ForceQuickening
 *     &#64;ForceQuickening("primitives")
 *     public static int doInts(int lhs, int rhs) {
 *         return lhs + rhs;
 *     }
 *
 *     &#64;Specialization
 *     &#64;ForceQuickening("primitives")
 *     public static double doDoubles(double lhs, double rhs) {
 *         return lhs + rhs;
 *     }
 *
 *     &#64;Specialization
 *     &#64;TruffleBoundary
 *     public static String doStrings(String lhs, String rhs) {
 *         return lhs + rhs;
 *     }
 * }
 * </pre>
 *
 * @since 24.2
 */
@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.METHOD})
@Repeatable(Repeat.class)
public @interface ForceQuickening {

    /**
     * The name of the quickening group. If nonempty, all specializations annotated with the same
     * value will be included in a quickened instruction together.
     *
     * By default, this value is empty, which signifies that a specialization should have its own
     * quickened instruction.
     *
     * @since 24.2
     */
    String value() default "";

    /**
     * Repeat annotation for {@link ForceQuickening}.
     *
     * @since 24.2
     */
    @Retention(RetentionPolicy.SOURCE)
    @Target({ElementType.METHOD})
    public @interface Repeat {
        /**
         * Repeat value for {@link ForceQuickening}.
         *
         * @since 24.2
         */
        ForceQuickening[] value();
    }

}
