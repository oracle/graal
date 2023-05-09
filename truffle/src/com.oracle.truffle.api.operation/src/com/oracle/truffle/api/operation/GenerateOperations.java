/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.operation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.oracle.truffle.api.TruffleLanguage;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface GenerateOperations {
    // The TruffleLanguage for this node.
    Class<? extends TruffleLanguage<?>> languageClass();

    // Whether to generate a yield operation to support coroutines.
    boolean enableYield() default false;

    // Whether to generate serialization/deserialization logic.
    boolean enableSerialization() default false;

    // Whether to generate a baseline interpreter that does not use specialization.
    // The node will transition to a specializing interpreter when it is hot enough.
    boolean enableBaselineInterpreter() default false;

    // Path to a file containing optimization decisions. This file is generated using tracing on a
    // representative corpus of code.
    String decisionsFile() default "";

    // Path to files with manually-provided optimization decisions.
    String[] decisionOverrideFiles() default {};

    // Whether to build the interpreter with tracing. Can also be set with the
    // truffle.dsl.OperationsEnableTracing option.
    boolean forceTracing() default false;

    // Types the interpreter should attempt to avoid boxing.
    Class<?>[] boxingEliminationTypes() default {};

    // Whether to use Unsafe array accesses. Unsafe accesses are optimized, since they do not
    // perform array bounds checks.
    boolean allowUnsafe() default false;

}
