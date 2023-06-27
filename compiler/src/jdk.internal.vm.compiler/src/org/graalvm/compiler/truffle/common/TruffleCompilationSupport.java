/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.common;

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

}
