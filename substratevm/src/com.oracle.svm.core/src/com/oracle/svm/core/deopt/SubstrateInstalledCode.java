/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.deopt;

import org.graalvm.compiler.core.common.CompilationIdentifier;

import com.oracle.svm.core.code.CodeInfo;
import com.oracle.svm.core.code.CodeInfoAccess;
import com.oracle.svm.core.code.RuntimeCodeCache;

import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Interface for objects representing runtime-compiled code that can be installed in the
 * {@linkplain RuntimeCodeCache runtime code cache}. This interface is intentionally compatible with
 * the class {@link InstalledCode}, but can be implemented in an existing class hierarchy. Code also
 * has native structures associated with it, see {@link CodeInfo} and {@link CodeInfoAccess}.
 * <p>
 * Assume that methods such as {@link #isValid}, {@link #isAlive} or {@link #getEntryPoint} return
 * stale values because generally, their internal state can change at any safepoint. Consistent
 * reads of such values require ensuring the absence of safepoint checks and preventing floating
 * reads and read elimination.
 */
public interface SubstrateInstalledCode {

    String getName();

    /** The entry point address of this code if {@linkplain #isValid valid}, or 0 otherwise. */
    long getEntryPoint();

    /** The address of this code if {@linkplain #isAlive alive}, or 0 otherwise. */
    long getAddress();

    /**
     * Returns the last method object passed to {@link #setAddress}. The return value might be
     * passed as the argument to future calls to {@link #setAddress}.
     * <p>
     * May return {@code null} if the subclass does not have a use for the method object (also not
     * in {@link #setAddress}) and therefore no need to retain it. Expected to return {@code null}
     * if {@link #setAddress} has never been called, or after {@link #clearAddress} has been called.
     */
    ResolvedJavaMethod getMethod();

    /**
     * Called during code installation: initialize this instance with the given address where its
     * instructions are, and the method it was compiled from. Afterwards, {@link #getAddress()} and
     * {@link #getEntryPoint()} return the given address, and {@link #isValid()} and
     * {@link #isAlive()} return {@code true}.
     */
    void setAddress(long address, ResolvedJavaMethod method);

    /**
     * This method is called during code uninstallation. Consider {@link #invalidate()} instead.
     * <p>
     * Reset this instance so that {@link #getAddress()} and {@link #getEntryPoint()} return 0, and
     * {@link #isValid()} and {@link #isAlive()} return {@code false}.
     */
    void clearAddress();

    /** Whether the code represented by this object exists and can be invoked. */
    boolean isValid();

    /**
     * Invalidates this installed code and deoptimizes all live invocations, after which both
     * {@link #isValid} and {@link #isAlive} return {@code false}.
     */
    void invalidate();

    /** Whether the code represented by this object exists and could have live invocations. */
    boolean isAlive();

    /**
     * Make this code non-entrant, but let live invocations continue execution. Afterwards,
     * {@link #isValid()} returns {@code false}, {@link #isAlive()} returns {@code true}, and
     * {@link #getEntryPoint()} returns 0.
     */
    void invalidateWithoutDeoptimization();

    SubstrateSpeculationLog getSpeculationLog();

    /**
     * Sets the identifier of the compilation that resulted in this code, which can be used to
     * provide additional information in {@link #getName()}.
     */
    void setCompilationId(CompilationIdentifier id);

    /**
     * Provides access to a {@link SubstrateInstalledCode}.
     *
     * Introduced when {@code OptimizedCallTarget} was changed to no longer extend
     * {@link InstalledCode}.
     */
    interface Factory {
        SubstrateInstalledCode createSubstrateInstalledCode();
    }
}
