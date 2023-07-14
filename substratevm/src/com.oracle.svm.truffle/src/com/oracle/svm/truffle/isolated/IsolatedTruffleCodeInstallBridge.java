/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2022, 2022, Red Hat Inc. All rights reserved.
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
package com.oracle.svm.truffle.isolated;

import com.oracle.svm.core.deopt.SubstrateInstalledCode;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.graal.isolated.ClientHandle;
import com.oracle.svm.graal.isolated.IsolatedCodeInstallBridge;
import jdk.vm.ci.code.InstalledCode;
import org.graalvm.compiler.truffle.common.OptimizedAssumptionDependency;
import org.graalvm.compiler.truffle.common.TruffleCompilable;

/**
 * A helper to pass information for installing code in the compilation client through a Truffle
 * compilation. It does not implement {@link InstalledCode} or {@link OptimizedAssumptionDependency}
 * in any meaningful way.
 */
public final class IsolatedTruffleCodeInstallBridge extends IsolatedCodeInstallBridge implements OptimizedAssumptionDependency {
    public IsolatedTruffleCodeInstallBridge(ClientHandle<? extends SubstrateInstalledCode.Factory> factoryHandle) {
        super(factoryHandle);
    }

    @Override
    public void onAssumptionInvalidated(Object source, CharSequence reason) {
        throw VMError.shouldNotReachHere(DO_NOT_CALL_REASON);
    }

    @Override
    public TruffleCompilable getCompilable() {
        throw VMError.shouldNotReachHere(DO_NOT_CALL_REASON);
    }
}
