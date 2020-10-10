/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.graal.isolated;

import org.graalvm.compiler.truffle.common.CompilableTruffleAST;
import org.graalvm.compiler.truffle.common.OptimizedAssumptionDependency;

import com.oracle.svm.core.deopt.SubstrateInstalledCode;
import com.oracle.svm.core.util.VMError;

import jdk.vm.ci.code.InstalledCode;

/**
 * A helper to pass information for installing code in the compilation client through a Truffle
 * compilation. It does not implement {@link InstalledCode} in any meaningful way, just like
 * {@code SubstrateTruffleInstalledCodeBridge}.
 */
public final class IsolatedCodeInstallBridge extends InstalledCode implements OptimizedAssumptionDependency {
    private final ClientHandle<? extends SubstrateInstalledCode.Access> installedCodeAccessHandle;
    private final ClientHandle<? extends OptimizedAssumptionDependency.Access> dependencyAccessHandle;

    public IsolatedCodeInstallBridge(ClientHandle<? extends SubstrateInstalledCode.Access> installedCodeAccessHandle,
                    ClientHandle<? extends OptimizedAssumptionDependency.Access> dependencyAccessHandle) {
        super(IsolatedCodeInstallBridge.class.getSimpleName());
        this.installedCodeAccessHandle = installedCodeAccessHandle;
        this.dependencyAccessHandle = dependencyAccessHandle;
    }

    public ClientHandle<? extends SubstrateInstalledCode.Access> getInstalledCodeAccessHandle() {
        return installedCodeAccessHandle;
    }

    public ClientHandle<? extends Access> getDependencyAccessHandle() {
        return dependencyAccessHandle;
    }

    private static final String DO_NOT_CALL_REASON = IsolatedCodeInstallBridge.class.getSimpleName() +
                    " only acts as an accessor for cross-isolate data. None of the implemented methods may be called.";

    @Override
    public long getAddress() {
        throw VMError.shouldNotReachHere(DO_NOT_CALL_REASON);
    }

    @Override
    public long getEntryPoint() {
        throw VMError.shouldNotReachHere(DO_NOT_CALL_REASON);
    }

    @Override
    public String getName() {
        throw VMError.shouldNotReachHere(DO_NOT_CALL_REASON);
    }

    @Override
    public long getStart() {
        throw VMError.shouldNotReachHere(DO_NOT_CALL_REASON);
    }

    @Override
    public boolean isValid() {
        throw VMError.shouldNotReachHere(DO_NOT_CALL_REASON);
    }

    @Override
    public boolean isAlive() {
        throw VMError.shouldNotReachHere(DO_NOT_CALL_REASON);
    }

    @Override
    public byte[] getCode() {
        throw VMError.shouldNotReachHere(DO_NOT_CALL_REASON);
    }

    @Override
    public void invalidate() {
        throw VMError.shouldNotReachHere(DO_NOT_CALL_REASON);
    }

    @Override
    public Object executeVarargs(Object... args) {
        throw VMError.shouldNotReachHere(DO_NOT_CALL_REASON);
    }

    @Override
    public CompilableTruffleAST getCompilable() {
        throw VMError.shouldNotReachHere(DO_NOT_CALL_REASON);
    }

    @Override
    public boolean soleExecutionEntryPoint() {
        throw VMError.shouldNotReachHere(DO_NOT_CALL_REASON);
    }
}
