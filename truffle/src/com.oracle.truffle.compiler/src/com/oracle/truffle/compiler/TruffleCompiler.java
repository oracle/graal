/*
 * Copyright (c) 2018, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.compiler;

/**
 * A compiler that partially evaluates and compiles a {@link TruffleCompilable} to machine code.
 */
public interface TruffleCompiler {

    String FIRST_TIER_COMPILATION_SUFFIX = "#1";
    String SECOND_TIER_COMPILATION_SUFFIX = "#2";

    /**
     * Initializes the compiler before the first compilation.
     *
     * @param compilable the Truffle AST that triggered the initialization
     * @param firstInitialization first initialization. For a multi-isolate compiler the
     *            {@code firstInitialization} must be {@code true} for an initialization in the
     *            first isolate and {@code false} for an initialization in the following isolates.
     *
     * @since 20.0.0
     */
    void initialize(TruffleCompilable compilable, boolean firstInitialization);

    /**
     * Compiles {@code compilable} to machine code.
     *
     * @param listener a listener receiving events about compilation success or failure
     */
    void doCompile(TruffleCompilationTask task, TruffleCompilable compilable, TruffleCompilerListener listener);

    /**
     * Notifies this object that it will no longer being used and should thus perform all relevant
     * finalization tasks. This is typically performed when the process exits.
     */
    void shutdown();

}
