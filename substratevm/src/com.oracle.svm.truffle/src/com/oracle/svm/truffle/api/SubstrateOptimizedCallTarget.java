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

import org.graalvm.compiler.truffle.common.CompilableTruffleAST;
import org.graalvm.compiler.truffle.common.OptimizedAssumptionDependency;
import org.graalvm.compiler.truffle.common.TruffleCompiler;
import org.graalvm.compiler.truffle.runtime.OptimizedCallTarget;
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.annotate.InvokeJavaFunctionPointer;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.code.CodeInfoTable;
import com.oracle.svm.core.code.UntetheredCodeInfo;
import com.oracle.svm.core.code.UntetheredCodeInfoAccess;
import com.oracle.svm.core.deopt.SubstrateInstalledCode;
import com.oracle.svm.core.deopt.SubstrateSpeculationLog;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.truffle.api.nodes.RootNode;

import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.SpeculationLog;

public class SubstrateOptimizedCallTarget extends OptimizedCallTarget implements SubstrateCompilableTruffleAST, SubstrateInstalledCode, OptimizedAssumptionDependency {

    protected long address;

    public SubstrateOptimizedCallTarget(OptimizedCallTarget sourceCallTarget, RootNode rootNode) {
        super(sourceCallTarget, rootNode);
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
    public void invalidate() {
        invalidate(null, null);
    }

    @Override
    public CompilableTruffleAST getCompilable() {
        return this;
    }

    @Override
    public SubstrateInstalledCode getSubstrateInstalledCode() {
        return this;
    }

    @Override
    public OptimizedAssumptionDependency getDependency() {
        return this;
    }

    @Override
    public void invalidateCode() {
        CodeInfoTable.invalidateInstalledCode(this);
    }

    @Override
    public boolean isValid() {
        return address != 0;
    }

    @Override
    public boolean isValidLastTier() {
        long address0 = getAddress();
        return (address0 != 0) && isValidLastTier0(address0);
    }

    @Uninterruptible(reason = "Prevent the GC from freeing the CodeInfo object.")
    private static boolean isValidLastTier0(long address0) {
        UntetheredCodeInfo info = CodeInfoTable.lookupCodeInfo(WordFactory.pointer(address0));
        if (info.isNonNull() && info.notEqual(CodeInfoTable.getImageCodeInfo())) {
            return UntetheredCodeInfoAccess.getTier(info) == TruffleCompiler.LAST_TIER_INDEX;
        }
        return false;
    }

    @Override
    public long getAddress() {
        return address;
    }

    @Override
    public long getCodeAddress() {
        return getAddress();
    }

    @Override
    public void setAddress(long address, ResolvedJavaMethod method) {
        this.address = address;
    }

    @Override
    public void clearAddress() {
        this.address = 0;
    }

    @Override
    public Object doInvoke(Object[] args) {
        /*
         * We have to be very careful that the calling code is uninterruptible, i.e., has no
         * safepoint between the read of the compiled code address and the indirect call to this
         * address. Otherwise, the code can be invalidated concurrently and we invoke an address
         * that no longer contains executable code.
         */
        long start = address;
        // The call below is not a safepoint as it is intrinsified in TruffleGraphBuilderPlugins.
        if (start != 0) {
            CallBoundaryFunctionPointer target = WordFactory.pointer(start);
            return KnownIntrinsics.convertUnknownValue(target.invoke(this, args), Object.class);
        } else {
            return callBoundary(args);
        }
    }

    @Override
    public boolean cancelCompilation(CharSequence reason) {
        if (SubstateTruffleOptions.isMultiThreaded()) {
            return super.cancelCompilation(reason);
        }
        return false;
    }

    @Override
    public InstalledCode createInstalledCode() {
        return new SubstrateTruffleInstalledCodeBridge(this);
    }

    interface CallBoundaryFunctionPointer extends CFunctionPointer {
        @InvokeJavaFunctionPointer
        Object invoke(OptimizedCallTarget receiver, Object[] args);
    }
}
