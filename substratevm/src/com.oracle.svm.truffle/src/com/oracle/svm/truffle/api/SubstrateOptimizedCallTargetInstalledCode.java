/*
 * Copyright (c) 2014, 2020, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.truffle.common.TruffleCompiler;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.code.CodeInfoTable;
import com.oracle.svm.core.code.UntetheredCodeInfo;
import com.oracle.svm.core.code.UntetheredCodeInfoAccess;
import com.oracle.svm.core.deopt.SubstrateInstalledCode;
import com.oracle.svm.core.deopt.SubstrateSpeculationLog;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.thread.VMOperation;
import com.oracle.svm.core.util.VMError;

import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class SubstrateOptimizedCallTargetInstalledCode extends InstalledCode implements SubstrateInstalledCode {
    protected final SubstrateOptimizedCallTarget callTarget;

    protected SubstrateOptimizedCallTargetInstalledCode(SubstrateOptimizedCallTarget callTarget) {
        super(null);
        this.callTarget = callTarget;
    }

    @Override
    public void invalidate() {
        CodeInfoTable.invalidateInstalledCode(this); // calls clearAddress
    }

    @Override
    public SubstrateSpeculationLog getSpeculationLog() {
        return callTarget.getSpeculationLog();
    }

    @Override
    public String getName() {
        return callTarget.getName();
    }

    @Override
    public ResolvedJavaMethod getMethod() {
        return null;
    }

    @Override
    public void setAddress(long address, ResolvedJavaMethod method) {
        assert VMOperation.isInProgressAtSafepoint();
        this.address = address;
        callTarget.setInstalledCode(this);
    }

    @Override
    public void clearAddress() {
        assert VMOperation.isInProgressAtSafepoint();
        this.address = 0;
    }

    @Override
    public boolean isValid() {
        return address != 0;
    }

    @Override
    public boolean isAlive() {
        // Same as isValid(): when SVM invalidates code, it immediately deoptimizes all frames.
        return isValid();
    }

    static Object doInvoke(SubstrateOptimizedCallTarget callTarget, SubstrateOptimizedCallTargetInstalledCode installedCode, Object[] args) {
        /*
         * We have to be very careful that the calling code is uninterruptible, i.e., has no
         * safepoint between the read of the entry point address and the indirect call to this
         * address. Otherwise, the code can be invalidated concurrently and we invoke an address
         * that no longer contains executable code.
         */
        long start = installedCode.address;
        if (start != 0) {
            SubstrateOptimizedCallTarget.CallBoundaryFunctionPointer target = WordFactory.pointer(start);
            Object result = target.invoke(callTarget, args);
            return KnownIntrinsics.convertUnknownValue(result, Object.class);
        } else {
            return callTarget.invokeCallBoundary(args);
        }
    }

    @Uninterruptible(reason = "Prevent the GC from freeing the CodeInfo object.")
    boolean isValidLastTier() {
        UntetheredCodeInfo info = CodeInfoTable.lookupCodeInfo(WordFactory.pointer(address));
        if (info.isNonNull() && info.notEqual(CodeInfoTable.getImageCodeInfo())) {
            return UntetheredCodeInfoAccess.getTier(info) == TruffleCompiler.LAST_TIER_INDEX;
        }
        return false;
    }

    // All methods below should never be called in SVM. There are others defined
    // in InstalledCode (such as getAddress) that should also not be called
    // but they are unfortunately final.

    private static final String NOT_CALLED_IN_SUBSTRATE_VM = "No implementation in Substrate VM";

    @Override
    public long getStart() {
        throw VMError.shouldNotReachHere(NOT_CALLED_IN_SUBSTRATE_VM);
    }

    @Override
    public byte[] getCode() {
        throw VMError.shouldNotReachHere(NOT_CALLED_IN_SUBSTRATE_VM);
    }

    @Override
    public Object executeVarargs(Object... args) {
        throw VMError.shouldNotReachHere(NOT_CALLED_IN_SUBSTRATE_VM);
    }
}
