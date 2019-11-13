/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.truffle.api;

import org.graalvm.compiler.truffle.common.OptimizedAssumptionDependency;

import com.oracle.svm.core.deopt.SubstrateInstalledCode;
import com.oracle.svm.core.util.VMError;

import jdk.vm.ci.code.CodeCacheProvider;
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.code.InvalidInstalledCodeException;

/**
 * A bridge between {@link InstalledCode} and {@link SubstrateInstalledCode}. This is required as
 * Truffle deals with {@link SubstrateInstalledCode} in its code cache but the Graal backend and
 * {@link CodeCacheProvider} use {@link InstalledCode} instead. The real solution is for
 * {@link InstalledCode} to be an interface.
 */
class SubstrateTruffleInstalledCodeBridge extends InstalledCode implements OptimizedAssumptionDependency.Access, SubstrateInstalledCode.Access {
    private final SubstrateCompilableTruffleAST callTarget;

    SubstrateTruffleInstalledCodeBridge(SubstrateCompilableTruffleAST callTarget) {
        super(callTarget.getName());
        this.callTarget = callTarget;
    }

    @Override
    public SubstrateInstalledCode getSubstrateInstalledCode() {
        return callTarget.getSubstrateInstalledCode();
    }

    @Override
    public OptimizedAssumptionDependency getDependency() {
        return callTarget.getDependency();
    }

    @Override
    public String toString() {
        return callTarget.toString();
    }

    // Methods implementing OptimizedAssumptionDependency

    @Override
    public void invalidate() {
        getSubstrateInstalledCode().invalidate();
    }

    // All methods below should never be called in SVM. There are others defined
    // in InstalledCode (such as getAddress) that should also not be called
    // but they are unfortunately final.

    private static final String NOT_CALLED_IN_SUBSTRATE_VM = "No implementation in Substrate VM";

    @Override
    public boolean isValid() {
        throw VMError.shouldNotReachHere(NOT_CALLED_IN_SUBSTRATE_VM);
    }

    @Override
    public boolean isAlive() {
        throw VMError.shouldNotReachHere(NOT_CALLED_IN_SUBSTRATE_VM);
    }

    @Override
    public String getName() {
        throw VMError.shouldNotReachHere(NOT_CALLED_IN_SUBSTRATE_VM);
    }

    @Override
    public long getStart() {
        throw VMError.shouldNotReachHere(NOT_CALLED_IN_SUBSTRATE_VM);
    }

    @Override
    public byte[] getCode() {
        throw VMError.shouldNotReachHere(NOT_CALLED_IN_SUBSTRATE_VM);
    }

    @Override
    public Object executeVarargs(Object... args) throws InvalidInstalledCodeException {
        throw VMError.shouldNotReachHere(NOT_CALLED_IN_SUBSTRATE_VM);
    }
}
