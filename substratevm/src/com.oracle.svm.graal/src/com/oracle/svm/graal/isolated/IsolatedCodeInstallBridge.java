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

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.code.CodeInfo;
import com.oracle.svm.core.code.CodeInfoAccess;
import com.oracle.svm.core.code.CodeInfoTable;
import com.oracle.svm.core.code.UntetheredCodeInfo;
import com.oracle.svm.core.deopt.SubstrateInstalledCode;
import com.oracle.svm.core.util.VMError;
import com.oracle.truffle.compiler.OptimizedAssumptionDependency;
import com.oracle.truffle.compiler.TruffleCompilable;

import jdk.graal.compiler.word.Word;
import jdk.vm.ci.code.InstalledCode;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;

/**
 * A helper to pass information for installing code in the compilation client through a Truffle
 * compilation. It does not implement {@link InstalledCode} or {@link OptimizedAssumptionDependency}
 * in any meaningful way.
 */
public final class IsolatedCodeInstallBridge extends InstalledCode implements OptimizedAssumptionDependency {
    private final ClientHandle<? extends SubstrateInstalledCode.Factory> factoryHandle;
    private ClientHandle<? extends SubstrateInstalledCode> installedCodeHandle;

    public IsolatedCodeInstallBridge(ClientHandle<? extends SubstrateInstalledCode.Factory> factoryHandle) {
        super(IsolatedCodeInstallBridge.class.getSimpleName());
        this.factoryHandle = factoryHandle;
    }

    public ClientHandle<? extends SubstrateInstalledCode.Factory> getSubstrateInstalledCodeFactoryHandle() {
        return factoryHandle;
    }

    public void setSubstrateInstalledCodeHandle(ClientHandle<? extends SubstrateInstalledCode> installedCodeHandle) {
        this.installedCodeHandle = installedCodeHandle;
    }

    public ClientHandle<? extends SubstrateInstalledCode> getSubstrateInstalledCodeHandle() {
        assert installedCodeHandle.notEqual(IsolatedHandles.nullHandle()) : "must have been initialized";
        return installedCodeHandle;
    }

    private static final String DO_NOT_CALL_REASON = IsolatedCodeInstallBridge.class.getSimpleName() +
                    " only acts as an accessor for cross-isolate data. None of the implemented methods may be called.";

    @Override
    public String getName() {
        throw VMError.shouldNotReachHere(DO_NOT_CALL_REASON);
    }

    /**
     * This method is used by the compiler debugging feature
     * {@code jdk.graal.PrintCompilation=true}.
     */
    @Override
    public long getStart() {
        ClientHandle<? extends SubstrateInstalledCode> handle = installedCodeHandle;
        if (handle.notEqual(IsolatedHandles.nullHandle())) {
            return getStart0(IsolatedCompileContext.get().getClient(), handle);
        } else {
            return 0L;
        }
    }

    /**
     * This method is used by the compiler debugging feature {@code jdk.graal.Dump=CodeInstall} to
     * dump a code at the point of code installation.
     */
    @Override
    public boolean isValid() {
        ClientHandle<? extends SubstrateInstalledCode> handle = installedCodeHandle;
        if (handle.notEqual(IsolatedHandles.nullHandle())) {
            return isValid0(IsolatedCompileContext.get().getClient(), handle);
        } else {
            return false;
        }
    }

    @Override
    public boolean isAlive() {
        throw VMError.shouldNotReachHere(DO_NOT_CALL_REASON);
    }

    /**
     * This method is used by the compiler debugging feature {@code jdk.graal.Dump=CodeInstall} to
     * dump a code at the point of code installation.
     */
    @Override
    public byte[] getCode() {
        ClientHandle<? extends SubstrateInstalledCode> handle = installedCodeHandle;
        if (handle.notEqual(IsolatedHandles.nullHandle())) {
            CompilerHandle<byte[]> codeHandle = getCode0(IsolatedCompileContext.get().getClient(), handle);
            return codeHandle == IsolatedHandles.nullHandle() ? null : IsolatedCompileContext.get().unhand(codeHandle);
        } else {
            return null;
        }
    }

    @Override
    public void onAssumptionInvalidated(Object source, CharSequence reason) {
        throw VMError.shouldNotReachHere(DO_NOT_CALL_REASON);
    }

    @Override
    public Object executeVarargs(Object... args) {
        throw VMError.shouldNotReachHere(DO_NOT_CALL_REASON);
    }

    @Override
    public TruffleCompilable getCompilable() {
        throw VMError.shouldNotReachHere(DO_NOT_CALL_REASON);
    }

    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    private static long getStart0(@SuppressWarnings("unused") ClientIsolateThread client, ClientHandle<? extends SubstrateInstalledCode> installedCodeHandle) {
        return IsolatedCompileClient.get().unhand(installedCodeHandle).getAddress();
    }

    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    private static boolean isValid0(@SuppressWarnings("unused") ClientIsolateThread client, ClientHandle<? extends SubstrateInstalledCode> installedCodeHandle) {
        return IsolatedCompileClient.get().unhand(installedCodeHandle).isValid();
    }

    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    private static CompilerHandle<byte[]> getCode0(@SuppressWarnings("unused") ClientIsolateThread client, ClientHandle<? extends SubstrateInstalledCode> installedCodeHandle) {
        SubstrateInstalledCode installedCode = IsolatedCompileClient.get().unhand(installedCodeHandle);
        return getCodeUninterruptible(Word.pointer(installedCode.getEntryPoint()));
    }

    @Uninterruptible(reason = "Accesses code info.")
    private static CompilerHandle<byte[]> getCodeUninterruptible(CodePointer entryPointAddress) {
        UntetheredCodeInfo untetheredCodeInfo = CodeInfoTable.lookupCodeInfo(entryPointAddress);
        if (untetheredCodeInfo.isNull()) {
            return IsolatedHandles.nullHandle();
        }
        Object tether = CodeInfoAccess.acquireTether(untetheredCodeInfo);
        try {
            CodeInfo codeInfo = CodeInfoAccess.convert(untetheredCodeInfo, tether);
            return copyCode0(codeInfo);
        } finally {
            CodeInfoAccess.releaseTether(untetheredCodeInfo, tether);
        }
    }

    @Uninterruptible(reason = "Wrap the now safe call to code info.", calleeMustBe = false)
    private static CompilerHandle<byte[]> copyCode0(CodeInfo codeInfo) {
        return copyCodeInCompilerIsolate0(IsolatedCompileClient.get().getCompiler(), CodeInfoAccess.getCodeStart(codeInfo), (int) CodeInfoAccess.getCodeSize(codeInfo).rawValue());
    }

    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    private static CompilerHandle<byte[]> copyCodeInCompilerIsolate0(@SuppressWarnings("unused") @CEntryPoint.IsolateThreadContext CompilerIsolateThread context, CodePointer codeStart, int codeSize) {
        byte[] code = new byte[codeSize];
        CTypeConversion.asByteBuffer(codeStart, codeSize).get(code);
        return IsolatedCompileContext.get().hand(code);
    }
}
