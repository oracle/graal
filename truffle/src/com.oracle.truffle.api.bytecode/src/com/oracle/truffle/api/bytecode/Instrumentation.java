/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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

/**
 * Instrumentations are operations that can be dynamically enabled at runtime. Dynamically enabling
 * them at runtime allows them to be used to implement features that are not commonly enabled, like
 * tracing, language internal debugging, profiling or taint tracking.
 * <p>
 * Instrumentations are emitted like regular operations with the {@link BytecodeBuilder builder},
 * but only generate instructions if they are enabled in the {@link BytecodeConfig}. A bytecode
 * config with enabled instrumentations can be provided at parse time, when deserializing or using
 * the {@link BytecodeRootNodes#update(BytecodeConfig) update} method at any time.
 * <p>
 * Unlike regular operations, instrumentations must have transparent stack effects. This is
 * important to ensure that that the stack layout remains compatible when instrumentations are
 * enabled at runtime. This means that instrumentations can either have no dynamic operands and no
 * return value or one dynamic operand and one return value. Note that instrumentations can declare
 * {@link ConstantOperand constant operands} since those do not affect the stack.
 * <p>
 * Instrumentations with one operand and return value may freely modify values observed at runtime.
 * {@link GenerateBytecode#boxingEliminationTypes() Boxing elimination} is reset when new
 * instrumentations are enabled, but it will also work for instrumentation operations.
 * <p>
 * Note that instrumentations cannot specify any {@link Operation#tags tags}, because tags must be
 * stable and new tags cannot be specified at runtime. Instrumentations can also not be used as
 * boolean converters for {@link ShortCircuitOperation short circuits}.
 *
 * @since 24.2
 */
@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.TYPE})
public @interface Instrumentation {

    /**
     * Whether executing this operation should force the uncached interpreter (if enabled) to
     * transition to cached.
     *
     * @since 24.2
     * @see Operation#forceCached()
     */
    boolean forceCached() default false;

    /**
     * Optional documentation for the instrumentation. This documentation is included in the javadoc
     * for the generated interpreter.
     *
     * @since 24.2
     */
    String javadoc() default "";
}
