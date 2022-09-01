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

import org.graalvm.compiler.core.common.CompilationIdentifier;
import org.graalvm.compiler.truffle.common.CompilableTruffleAST;
import org.graalvm.compiler.truffle.common.OptimizedAssumptionDependency;
import org.graalvm.compiler.truffle.common.TruffleCompiler;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.code.CodeInfo;
import com.oracle.svm.core.code.CodeInfoAccess;
import com.oracle.svm.core.code.CodeInfoTable;
import com.oracle.svm.core.code.RuntimeCodeInfoHistory;
import com.oracle.svm.core.code.UntetheredCodeInfo;
import com.oracle.svm.core.code.UntetheredCodeInfoAccess;
import com.oracle.svm.core.deopt.SubstrateInstalledCode;
import com.oracle.svm.core.deopt.SubstrateSpeculationLog;
import com.oracle.svm.core.thread.VMOperation;
import com.oracle.svm.core.util.VMError;

import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Represents the compiled code of a {@link SubstrateOptimizedCallTarget}.
 * <p>
 * Assume that methods return stale values because internal state can change at any safepoint (see
 * {@link SubstrateInstalledCode}).
 */
public class SubstrateOptimizedCallTargetInstalledCode extends InstalledCode implements SubstrateInstalledCode, OptimizedAssumptionDependency {
    protected final SubstrateOptimizedCallTarget callTarget;
    private String nameSuffix = "";

    protected SubstrateOptimizedCallTargetInstalledCode(SubstrateOptimizedCallTarget callTarget) {
        super(null);
        this.callTarget = callTarget;
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code", mayBeInlined = true)
    public long getAddress() {
        return address;
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code", mayBeInlined = true)
    public long getEntryPoint() {
        return entryPoint;
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code", mayBeInlined = true)
    public boolean isAlive() {
        return this.address != 0L;
    }

    @Override
    public final void invalidate() {
        CodeInfoTable.invalidateInstalledCode(this); // calls clearAddress
        callTarget.onInvalidate(null, null, true);
    }

    @Override
    public void onAssumptionInvalidated(Object source, CharSequence reason) {
        boolean wasActive = false;
        if (isAlive()) {
            CodeInfoTable.invalidateInstalledCode(this); // calls clearAddress
            wasActive = true;
        } else {
            assert !isValid() : "Cannot be valid but not alive";
        }
        callTarget.onInvalidate(source, reason, wasActive);
    }

    /**
     * Returns false if not valid, including if {@linkplain #invalidateWithoutDeoptimization
     * previously invalidated without deoptimization} in which case there can still be
     * {@linkplain #isAlive live activations}. In order to entirely invalidate code in such cases,
     * {@link #invalidate} must still be called even when this method returns false.
     */
    @Override
    public boolean isValid() {
        return super.isValid();
    }

    @Override
    public CompilableTruffleAST getCompilable() {
        return callTarget;
    }

    @Override
    public SubstrateSpeculationLog getSpeculationLog() {
        return callTarget.getSpeculationLog();
    }

    @Override
    public void setCompilationId(CompilationIdentifier id) {
        nameSuffix = " (" + id.toString(CompilationIdentifier.Verbosity.ID) + ')';
    }

    @Override
    public String getName() {
        return callTarget.getName() + nameSuffix;
    }

    @Override
    public ResolvedJavaMethod getMethod() {
        return null;
    }

    @Override
    public void setAddress(long address, ResolvedJavaMethod method) {
        assert VMOperation.isInProgressAtSafepoint();
        this.entryPoint = address;
        this.address = address;
        callTarget.onCodeInstalled(this);
    }

    @Override
    public void clearAddress() {
        assert VMOperation.isInProgressAtSafepoint();
        this.entryPoint = 0;
        this.address = 0;
        callTarget.onCodeCleared(this);
    }

    @Override
    public void invalidateWithoutDeoptimization() {
        assert VMOperation.isInProgressAtSafepoint();
        if (isValid()) {
            invalidateWithoutDeoptimization0();
        }
    }

    @Uninterruptible(reason = "Must tether the CodeInfo.")
    private void invalidateWithoutDeoptimization0() {
        this.entryPoint = 0;

        UntetheredCodeInfo untetheredInfo = CodeInfoTable.lookupCodeInfo(WordFactory.pointer(this.address));
        assert untetheredInfo.isNonNull() && untetheredInfo.notEqual(CodeInfoTable.getImageCodeInfo());

        Object tether = CodeInfoAccess.acquireTether(untetheredInfo);
        try { // Indicates to GC that the code can be freed once there are no activations left
            CodeInfo codeInfo = CodeInfoAccess.convert(untetheredInfo, tether);
            CodeInfoAccess.setState(codeInfo, CodeInfo.STATE_NON_ENTRANT);
            logMakeNonEntrant(codeInfo);
        } finally {
            CodeInfoAccess.releaseTether(untetheredInfo, tether);
        }
    }

    @Uninterruptible(reason = "Call interruptible code now that the CodeInfo is tethered.", calleeMustBe = false)
    private static void logMakeNonEntrant(CodeInfo codeInfo) {
        RuntimeCodeInfoHistory.singleton().logMakeNonEntrant(codeInfo);
    }

    @Uninterruptible(reason = "Must be safepoint free")
    static Object doInvoke(SubstrateOptimizedCallTarget callTarget, Object[] args) {
        /*
         * The calling code must be uninterruptible, i.e., must not have a safepoint between the
         * read of the entry point address and the indirect call to this address. Otherwise, the
         * code can be invalidated concurrently and we invoke an address that no longer contains
         * executable code.
         */
        long start = callTarget.installedCode.entryPoint;
        if (start != 0) {
            SubstrateOptimizedCallTarget.CallBoundaryFunctionPointer target = WordFactory.pointer(start);
            return target.invoke(callTarget, args);
        } else {
            return callTarget.invokeCallBoundary(args);
        }
    }

    @Uninterruptible(reason = "Prevent the GC from freeing the CodeInfo object.")
    boolean isValidLastTier() {
        if (entryPoint == 0) {
            return false; // not valid
        }
        UntetheredCodeInfo info = CodeInfoTable.lookupCodeInfo(WordFactory.pointer(entryPoint));
        return info.isNonNull() && info.notEqual(CodeInfoTable.getImageCodeInfo()) &&
                        UntetheredCodeInfoAccess.getTier(info) == TruffleCompiler.LAST_TIER_INDEX;
    }

    /*
     * All methods below should never be called in SVM. There are others defined in InstalledCode
     * (such as getAddress) that should also not be called but they are unfortunately final.
     */

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
