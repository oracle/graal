/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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
    private final WeakReference<TruffleCompilable> compilableRef;

    public TruffleCompilerAssumptionDependency(TruffleCompilable compilation, InstalledCode code) {
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
        TruffleCompilable ast = compilableRef.get();
        if (ast != null) {
            ast.onInvalidate(source, reason, wasActive);
        }
    }

    public InstalledCode getInstalledCode() {
        return installedCode;
    }

    @Override
    public TruffleCompilable getCompilable() {
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
