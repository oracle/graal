/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * Represents entry points for Truffle runtime implementations to Truffle compilation.
 */
public interface TruffleCompilationSupport {

    /**
     * Registers a runtime instance after it was fully initialized.
     */
    void registerRuntime(TruffleCompilerRuntime runtime);

    /**
     * Creates a new compiler handle for compilation. A runtime must be
     * {@link #registerRuntime(TruffleCompilerRuntime) registered} prior to calling this method.
     * Only one compiler instance should be created per Truffle runtime instance.
     */
    TruffleCompiler createCompiler(TruffleCompilerRuntime runtime);

    /**
     * Lists all compiler options available, including deprecated options.
     */
    TruffleCompilerOptionDescriptor[] listCompilerOptions();

    /**
     * Returns <code>true</code> if a compilation key exists, else <code>false</code>.
     */
    boolean compilerOptionExists(String key);

    /**
     * Validates a compiler option and returns <code>null</code> if the option is null. An error
     * message otherwise.
     */
    String validateCompilerOption(String key, String value);

    /**
     * Returns a compiler configuration name that will be used.
     */
    String getCompilerConfigurationName(TruffleCompilerRuntime runtime);

    /**
     * Opens a compiler thread scope for compilation threads. Use with try-with-resourcce.
     */
    default AutoCloseable openCompilerThreadScope() {
        return null;
    }

    default boolean isSuppressedCompilationFailure(@SuppressWarnings("unused") Throwable throwable) {
        return false;
    }

    default String getCompilerVersion() {
        return null;
    }

}
