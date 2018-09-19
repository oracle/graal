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
 * An {@link OptimizedAssumptionDependency} whose machine code is an {@link InstalledCode}.
 */
public class InstalledCodeOptimizedAssumptionDependency implements OptimizedAssumptionDependency {

    private final InstalledCode installedCode;
    private final CompilableTruffleAST compilable;
    private final boolean reachabilityDeterminesValidity;

    public InstalledCodeOptimizedAssumptionDependency(InstalledCode installedCode, CompilableTruffleAST compilable, boolean reachabilityDeterminesValidity) {
        this.installedCode = installedCode;
        this.compilable = compilable;
        this.reachabilityDeterminesValidity = reachabilityDeterminesValidity;
    }

    @Override
    public CompilableTruffleAST getCompilable() {
        return compilable;
    }

    @Override
    public void invalidate() {
        installedCode.invalidate();
    }

    @Override
    public boolean isValid() {
        return installedCode.isValid();
    }

    @Override
    public boolean reachabilityDeterminesValidity() {
        return reachabilityDeterminesValidity;
    }

    /**
     * Gets the machine code whose validity is guarded by this object.
     */
    public InstalledCode getInstalledCode() {
        return installedCode;
    }

    @Override
    public String toString() {
        return compilable == null ? installedCode.toString() : compilable.toString();
    }
}
