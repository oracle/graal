/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.ref.WeakReference;
import java.util.Objects;

import jdk.vm.ci.code.InstalledCode;

/**
 * For multi tier compilation it is necessary to pair the call target with the installed code as
 * multiple installed codes may be active for a given call target. For example if the tier 1
 * compiled code is currently active while tier 2 code is installed. If now an assumption is
 * invalidated both installed codes must be deoptimized eagerly. Installing tier 2 code over tier 1
 * code does not eagerly deoptimize the tier 1 code.
 */
public final class TruffleCompilerAssumptionDependency implements OptimizedAssumptionDependency {

    private final InstalledCode installedCode;
    private final WeakReference<CompilableTruffleAST> compilableRef;

    public TruffleCompilerAssumptionDependency(CompilableTruffleAST compilation, InstalledCode code) {
        Objects.requireNonNull(code);
        this.installedCode = code;
        this.compilableRef = new WeakReference<>(compilation);
    }

    @Override
    public void onAssumptionInvalidated(Object source, CharSequence reason) {
        boolean wasActive = false;
        InstalledCode code = getInstalledCode();
        if (code != null && code.isAlive()) {
            code.invalidate();
            wasActive = true;
        } else {
            assert !isAlive() : "Cannot be valid but not alive";
        }
        CompilableTruffleAST ast = compilableRef.get();
        if (ast != null) {
            ast.onInvalidate(source, reason, wasActive);
        }
    }

    public InstalledCode getInstalledCode() {
        return installedCode;
    }

    @Override
    public CompilableTruffleAST getCompilable() {
        return this.compilableRef.get();
    }

    @Override
    public boolean isAlive() {
        InstalledCode code = getInstalledCode();
        if (code == null) {
            return false;
        }
        return code.isAlive();
    }

    @Override
    public String toString() {
        return "TruffleCompilerAssumptionDependency[" + getInstalledCode().toString() + "]";
    }

}
