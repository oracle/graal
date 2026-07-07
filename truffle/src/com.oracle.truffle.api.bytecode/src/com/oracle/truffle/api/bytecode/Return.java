/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.interop.InteropLibrary;

/**
 * Declares a custom return operation. A custom return operation has the same control-flow
 * behavior as the built-in {@code Return} operation, but allows custom logic to compute the value
 * returned from the bytecode root node.
 * <p>
 * A custom return operation has many of the same restrictions and capabilities of a regular
 * {@link Operation}, with a few differences:
 * <ul>
 * <li>It must take one or more dynamic operands.
 * <li>It must have a return value. This value is returned from the bytecode root node and should be
 * a {@link InteropLibrary#isValidValue valid interop type}.
 * </ul>
 *
 * If a custom return takes multiple dynamic operands, {@link #resultOperandIndex()} specifies which
 * operand represents the logical "result" of the return before the custom return executes. This result is
 * used by {@link GenerateBytecode#enableTagInstrumentation() tag instrumentation} and
 * {@link EpilogReturn}.
 *
 * @since 25.2
 */
@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.TYPE})
public @interface Return {

    /**
     * Whether executing this operation should force the uncached interpreter (if enabled) to
     * transition to cached.
     *
     * @since 25.2
     * @see Operation#forceCached()
     */
    boolean forceCached() default false;

    /**
     * The instrumentation tags that should be implicitly associated with this operation.
     *
     * @since 25.2
     * @see GenerateBytecode#enableTagInstrumentation()
     */
    Class<? extends Tag>[] tags() default {};

    /**
     * Optional documentation for the operation. This documentation is included in the javadoc for
     * the generated interpreter.
     *
     * @since 25.2
     */
    String javadoc() default "";

    /**
     * Index of the dynamic operand that represents the logical "result" of the return before the
     * custom return executes. This result is used by
     * {@link GenerateBytecode#enableTagInstrumentation() tag instrumentation} and
     * {@link EpilogReturn}.
     * <p>
     * This field must be specified for custom returns with multiple dynamic operands; it
     * should not be specified for custom returns with no dynamic operands.
     *
     * @since 25.2
     */
    int resultOperandIndex() default 0;
}
