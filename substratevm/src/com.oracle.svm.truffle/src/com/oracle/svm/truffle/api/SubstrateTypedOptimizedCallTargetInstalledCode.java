/*
 * Copyright (c) 2014, 2026, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.svm.shared.Uninterruptible;
import com.oracle.svm.core.deopt.SubstrateInstalledCode;
import com.oracle.svm.core.thread.VMOperation;

import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Represents the compiled code of a {@link SubstrateTypedOptimizedCallTarget}.
 * <p>
 * Assume that methods return stale values because internal state can change at any safepoint (see
 * {@link SubstrateInstalledCode}).
 */
final class SubstrateTypedOptimizedCallTargetInstalledCode extends SubstrateOptimizedCallTargetInstalledCode {
    private TypedEntryMethod method;

    SubstrateTypedOptimizedCallTargetInstalledCode(SubstrateTypedOptimizedCallTarget callTarget) {
        super(callTarget);
    }

    @Override
    public TypedEntryMethod getMethod() {
        return method;
    }

    @Uninterruptible(reason = "Prevent safepoint.", callerMustBe = true)
    public long getEntryPointUninterruptible() {
        return entryPoint;
    }

    @Override
    public void setAddress(long address, long entryPoint, ResolvedJavaMethod m) {
        VMOperation.guaranteeInProgressAtSafepoint("Must be at a safepoint");
        method = (TypedEntryMethod) m;
        super.setAddress(address, entryPoint, m);
    }

    @Override
    public void clearAddress() {
        method = null;
        super.clearAddress();
    }
}
