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

import jdk.vm.ci.code.InstalledCode;

/**
 * Represents some machine code whose validity depends on an assumption. Valid machine code can
 * still be executed.
 */
public interface OptimizedAssumptionDependency {

    /**
     * Invalidates the machine code referenced by this object.
     */
    void invalidate();

    /**
     * Determines if the machine code referenced by this object is valid.
     */
    boolean isValid();

    /**
     * Gets the Truffle AST whose machine code is represented by this object. May be {@code null}.
     */
    default CompilableTruffleAST getCompilable() {
        return null;
    }

    /**
     * Determines if the reachability of this object corresponds with the validity of the referenced
     * machine code.
     *
     * @return {@code true} if the referenced machine code is guaranteed to be invalid when this
     *         object becomes unreachable, {@code false} if the reachability of this object says
     *         nothing about the validity of the referenced machine code
     */
    default boolean reachabilityDeterminesValidity() {
        return true;
    }

    /**
     * Provides access to a {@link OptimizedAssumptionDependency}.
     *
     * Introduced when {@code OptimizedCallTarget} was changed to no longer extend
     * {@link InstalledCode}. Prior to that change, {@code OptimizedAssumption} dependencies were
     * {@link InstalledCode} objects.
     */
    interface Access {
        OptimizedAssumptionDependency getDependency();
    }
}
