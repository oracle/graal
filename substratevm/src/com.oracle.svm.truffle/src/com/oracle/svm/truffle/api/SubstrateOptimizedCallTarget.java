/*
 * Copyright (c) 2014, 2019, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.compiler.truffle.runtime.OptimizedCallTarget;
import org.graalvm.nativeimage.c.function.CFunctionPointer;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.c.InvokeJavaFunctionPointer;
import com.oracle.svm.core.deopt.SubstrateSpeculationLog;
import com.oracle.svm.core.thread.VMOperation;
import com.oracle.svm.truffle.TruffleSupport;
import com.oracle.truffle.api.nodes.RootNode;

import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.meta.SpeculationLog;

/**
 * Truffle call target which can be partially evaluated and compiled to machine code, which is then
 * represented by {@link SubstrateOptimizedCallTargetInstalledCode}. At most one piece of code is
 * <em>valid</em> as an entry point for new invocations at any given time, but arbitrarily many
 * pieces of code for this call target can be <em>alive</em> at the same time (for example, from
 * previous lower-tier compilations).
 * <p>
 * Assume that methods such as {@link #isValid} or {@link #getCodeAddress} return stale values
 * because internal state can change at any safepoint. Consistent reads of such values require
 * ensuring the absence of safepoint checks and preventing floating reads and read elimination.
 */
public class SubstrateOptimizedCallTarget extends OptimizedCallTarget implements SubstrateCompilableTruffleAST {

    /**
     * Stores the most recently installed code, which is the only entry point for this call target
     * at one point in time (or not an entry point, if invalid). Must never be {@code null}.
     * <p>
     * Does not need to be volatile because it is modified only in safepoint operations. Reads of
     * this field must be done carefully so they cannot float across safepoint checks.
     */
    protected SubstrateOptimizedCallTargetInstalledCode installedCode;

    public SubstrateOptimizedCallTarget(OptimizedCallTarget sourceCallTarget, RootNode rootNode) {
        super(sourceCallTarget, rootNode);
        this.installedCode = createInitializationInstalledCode();
        assert this.installedCode != null : "Must never be null";
    }

    @SuppressWarnings("sync-override")
    @Override
    public SubstrateSpeculationLog getSpeculationLog() {
        return (SubstrateSpeculationLog) super.getSpeculationLog();
    }

    @Override
    public SpeculationLog getCompilationSpeculationLog() {
        return getSpeculationLog();
    }

    @Override
    public boolean isValid() {
        // Only the most recently installed code can be valid, which entails being an entry point.
        return installedCode.isValid();
    }

    @Override
    public boolean isValidLastTier() {
        return installedCode.isValidLastTier();
    }

    @Override
    public long getCodeAddress() {
        return installedCode.getAddress();
    }

    @Override
    public Object doInvoke(Object[] args) {
        return SubstrateOptimizedCallTargetInstalledCode.doInvoke(this, args);
    }

    @Override
    public boolean cancelCompilation(CharSequence reason) {
        if (SubstrateTruffleOptions.isMultiThreaded()) {
            return super.cancelCompilation(reason);
        }
        return false;
    }

    @Override
    public InstalledCode createPreliminaryInstalledCode() {
        return createInstalledCode();
    }

    @Uninterruptible(reason = "Transitioning back to interruptible code", calleeMustBe = false)
    protected Object invokeCallBoundary(Object[] args) {
        return callBoundary(args);
    }

    @Override
    public SubstrateOptimizedCallTargetInstalledCode createSubstrateInstalledCode() {
        assert TruffleSupport.isIsolatedCompilation() : "Must be called only with isolated compilation";
        return createInstalledCode();
    }

    /**
     * Called from {@link SubstrateOptimizedCallTargetInstalledCode#setAddress} for code
     * representing this call target.
     */
    protected void onCodeInstalled(SubstrateOptimizedCallTargetInstalledCode code) {
        VMOperation.guaranteeInProgressAtSafepoint("Must be at a safepoint");
        assert code != null : "Must never become null";
        if (code == installedCode) {
            return;
        }
        installedCode.invalidateWithoutDeoptimization();
        installedCode = code;
    }

    /**
     * Called from {@link SubstrateOptimizedCallTargetInstalledCode#clearAddress} for code
     * representing this call target. The caller can, however, be older code and not the current
     * entry point, so that {@code code != installedCode}.
     */
    protected void onCodeCleared(@SuppressWarnings("unused") SubstrateOptimizedCallTargetInstalledCode code) {
        VMOperation.guaranteeInProgressAtSafepoint("Must be at a safepoint");
    }

    /** Creates the instance for initializing {@link #installedCode} so it is never {@code null}. */
    protected SubstrateOptimizedCallTargetInstalledCode createInitializationInstalledCode() {
        return createInstalledCode();
    }

    private SubstrateOptimizedCallTargetInstalledCode createInstalledCode() {
        return new SubstrateOptimizedCallTargetInstalledCode(this);
    }

    interface CallBoundaryFunctionPointer extends CFunctionPointer {
        @InvokeJavaFunctionPointer
        Object invoke(OptimizedCallTarget receiver, Object[] args);
    }
}
