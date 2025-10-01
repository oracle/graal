/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.dsl.Bind;

/**
 * Declares a custom yield operation. A custom yield operation has the same control flow behaviour
 * as the {@link GenerateBytecode#enableYield built-in yield}, but allows to customize the result
 * produced. For example, a custom yield can create a guest generator object instead of a
 * {@link ContinuationResult}, or it can perform custom logic before
 * {@link ContinuationResult#create(ContinuationRootNode, MaterializedFrame, Object) creating} the
 * continuation result.
 * <p>
 * Below is an example of a custom yield that returns a guest language generator object:
 *
 * <pre>
 * &#64;Yield
 * public static final class CustomYield {
 *     &#64;Specialization
 *     public static Object doYield(Object value, &#64;Bind ContinuationRootNode root, &#64;Bind MaterializedFrame frame) {
 *         return new MyGeneratorObject(root, frame, value);
 *     }
 * }
 * </pre>
 *
 * A custom yield operation has many of the same restrictions and capabilities of a regular
 * {@link Operation}, with a few differences:
 * <ul>
 * <li>It must take zero or one dynamic operand.</li>
 * <li>It yields a value to the caller, so it must have a return value. The result should be a
 * {@link InteropLibrary#isValidValue valid interop type}. Typically, the result should encapsulate
 * the continuation state so that the callee can resume execution at a later time.</li>
 * <li>It can {@link Bind} a {@link ContinuationRootNode} operand, which can be used to resume
 * execution at a later time. (Custom yields will typically also {@link Bind} the
 * {@link MaterializedFrame} to capture the interpreter state, but this is possible in regular
 * operations.)</li>
 * </ul>
 *
 * Note that {@link GenerateBytecode#enableYield} does not need to be {@code true} to use custom
 * yields; it is only necessary if you need the built-in yield operation. The built-in yield
 * operation is semantically equivalent to the following custom yield operation:
 *
 * <pre>
 * &#64;Yield
 * public static final class BuiltinYield {
 *     &#64;Specialization
 *     public static Object doYield(Object result, &#64;Bind ContinuationRootNode root, &#64;Bind MaterializedFrame frame) {
 *         return ContinuationResult.create(root, frame, result);
 *     }
 * }
 * </pre>
 *
 * @since 25.1
 * @see GenerateBytecode#enableYield()
 */
@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.TYPE})
public @interface Yield {

    /**
     * Whether executing this operation should force the uncached interpreter (if enabled) to
     * transition to cached.
     *
     * @since 25.1
     * @see Operation#forceCached()
     */
    boolean forceCached() default false;

    /**
     * The instrumentation tags that should be implicitly associated with this operation.
     *
     * @since 25.1
     * @see GenerateBytecode#enableTagInstrumentation()
     */
    Class<? extends Tag>[] tags() default {};

    /**
     * Optional documentation for the instrumentation. This documentation is included in the javadoc
     * for the generated interpreter.
     *
     * @since 25.1
     */
    String javadoc() default "";

    // no storeBytecodeIndex() attribute. Unconditionally enabled as we yield from the method.
}
