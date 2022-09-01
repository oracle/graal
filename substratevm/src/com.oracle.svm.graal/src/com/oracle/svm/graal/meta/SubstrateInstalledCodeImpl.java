/*
 * Copyright (c) 2013, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.graal.meta;

import static com.oracle.svm.core.util.VMError.shouldNotReachHere;

import com.oracle.svm.core.Uninterruptible;
import org.graalvm.compiler.core.common.CompilationIdentifier;

import com.oracle.svm.core.code.CodeInfo;
import com.oracle.svm.core.code.CodeInfoTable;
import com.oracle.svm.core.deopt.SubstrateInstalledCode;
import com.oracle.svm.core.deopt.SubstrateSpeculationLog;
import com.oracle.svm.core.graal.meta.SharedRuntimeMethod;
import com.oracle.svm.core.thread.VMOperation;
import com.oracle.svm.core.util.VMError;

import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Represents the installed code of a runtime compiled method. Note that Truffle uses its own
 * implementation of {@link SubstrateInstalledCode}, so no code within Substrate VM must assume that
 * this is the only representation of runtime compiled code.
 *
 * Metadata for the code is maintained by the class {@link CodeInfo}.
 */
public class SubstrateInstalledCodeImpl extends InstalledCode implements SubstrateInstalledCode {

    public SubstrateInstalledCodeImpl(SharedRuntimeMethod method) {
        super(method.format("%H.%n#(%p)"));
    }

    public SubstrateInstalledCodeImpl(String name, SharedRuntimeMethod method) {
        super(name != null ? name : method.format("%H.%n#(%p)"));
    }

    @Override
    public ResolvedJavaMethod getMethod() {
        return null;
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
    public void setAddress(long address, ResolvedJavaMethod method) {
        assert VMOperation.isInProgressAtSafepoint();
        this.address = address;
        this.entryPoint = address;
    }

    @Override
    public void clearAddress() {
        assert VMOperation.isInProgressAtSafepoint();
        this.entryPoint = 0;
        this.address = 0;
    }

    @Override
    public void invalidate() {
        CodeInfoTable.invalidateInstalledCode(this);
    }

    /**
     * Currently not supported. Existing code would need modifications to ensure that there cannot
     * be a safepoint between a read of {@link #entryPoint} (such as, at the end of
     * {@link #getEntryPoint()}), and the invocation of the entry point that was read.
     */
    @Override
    public void invalidateWithoutDeoptimization() {
        assert VMOperation.isInProgressAtSafepoint();
        throw VMError.unimplemented();
    }

    @Override
    public SubstrateSpeculationLog getSpeculationLog() {
        return null;
    }

    @Override
    public void setCompilationId(CompilationIdentifier id) {
    }

    @Override
    public long getStart() {
        throw shouldNotReachHere("No implementation in Substrate VM");
    }

    @Override
    public byte[] getCode() {
        throw shouldNotReachHere("No implementation in Substrate VM");
    }

    @Override
    public Object executeVarargs(Object... args) {
        throw shouldNotReachHere("No implementation in Substrate VM");
    }
}
