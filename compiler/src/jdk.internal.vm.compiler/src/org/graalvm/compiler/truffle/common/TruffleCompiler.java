/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Map;

/**
 * A compiler that partially evaluates and compiles a {@link CompilableTruffleAST} to machine code.
 */
public interface TruffleCompiler {
    String FIRST_TIER_COMPILATION_SUFFIX = "#1";
    String SECOND_TIER_COMPILATION_SUFFIX = "#2";
    int FIRST_TIER_INDEX = 1;
    int LAST_TIER_INDEX = 2;

    /**
     * Initializes the compiler before the first compilation.
     *
     * @param options the options for initialization
     * @param compilable the Truffle AST that triggered the initialization
     * @param firstInitialization first initialization. For a multi-isolate compiler the
     *            {@code firstInitialization} must be {@code true} for an initialization in the
     *            first isolate and {@code false} for an initialization in the following isolates.
     *
     * @since 20.0.0
     */
    void initialize(Map<String, Object> options, CompilableTruffleAST compilable, boolean firstInitialization);

    /**
     * Compiles {@code compilable} to machine code.
     *
     * @param options option values relevant to compilation
     * @param listener a listener receiving events about compilation success or failure
     */
    void doCompile(TruffleCompilationTask task, CompilableTruffleAST compilable, Map<String, Object> options, TruffleCompilerListener listener);

    /**
     * Returns a unique name for the configuration in use by this compiler.
     */
    String getCompilerConfigurationName();

    /**
     * Notifies this object that it will no longer being used and should thus perform all relevant
     * finalization tasks. This is typically performed when the process exits.
     */
    void shutdown();
}
