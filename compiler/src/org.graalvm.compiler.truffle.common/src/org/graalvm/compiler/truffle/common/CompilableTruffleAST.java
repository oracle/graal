/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

import java.util.function.Supplier;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.SpeculationLog;

/**
 * A Truffle AST that can be compiled by a {@link TruffleCompiler}.
 */
public interface CompilableTruffleAST {
    /**
     * Gets this AST as a compiler constant.
     */
    JavaConstant asJavaConstant();

    /**
     * Gets a speculation log to be used for a single Truffle compilation. The returned speculation
     * log provides access to all relevant failed speculations as well as support for making
     * speculation during a single compilation.
     */
    SpeculationLog getCompilationSpeculationLog();

    /**
     * Notifies this object that a compilation of the AST it represents failed.
     *
     * @param reasonAndStackTrace the output of {@link Throwable#printStackTrace()} for the
     *            exception representing the reason for compilation failure
     * @param bailout specifies whether the failure was a bailout or an error in the compiler. A
     *            bailout means the compiler aborted the compilation based on some of property of
     *            the AST (e.g., too big). A non-bailout means an unexpected error in the compiler
     *            itself.
     * @param permanentBailout specifies if a bailout is due to a condition that probably won't
     *            change if this AST is compiled again. This value is meaningless if
     *            {@code bailout == false}.
     */
    void onCompilationFailed(Supplier<String> reasonAndStackTrace, boolean bailout, boolean permanentBailout);

    /**
     * Gets a descriptive name for this call target.
     */
    String getName();

    /**
     * Invalidates any machine code attached to this call target.
     */
    default void invalidateCode() {
    }

}
