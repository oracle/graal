/*
 * Copyright (c) 2013, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.code;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.deopt.SubstrateInstalledCode;
import com.oracle.svm.core.heap.VMOperationInfos;
import com.oracle.svm.core.meta.SharedMethod;
import com.oracle.svm.core.thread.JavaVMOperation;
import com.oracle.svm.core.util.VMError;

public class AbstractRuntimeCodeInstaller {
    protected Pointer allocateCodeMemory(long size) {
        PointerBase result = RuntimeCodeInfoAccess.allocateCodeMemory(WordFactory.unsigned(size));
        if (result.isNull()) {
            throw new OutOfMemoryError("Could not allocate memory for runtime-compiled code.");
        }
        return (Pointer) result;
    }

    protected void makeCodeMemoryExecutableReadOnly(Pointer start, UnsignedWord size) {
        RuntimeCodeInfoAccess.makeCodeMemoryExecutableReadOnly((CodePointer) start, size);
    }

    protected void makeCodeMemoryExecutableWritable(Pointer start, UnsignedWord size) {
        RuntimeCodeInfoAccess.makeCodeMemoryExecutableWritable((CodePointer) start, size);
    }

    protected static void doInstallPrepared(SharedMethod method, CodeInfo codeInfo, SubstrateInstalledCode installedCode) {
        // The tether is acquired when it is created.
        Object tether = RuntimeCodeInfoAccess.beforeInstallInCurrentIsolate(codeInfo, installedCode);
        try {
            doInstallPreparedAndTethered(method, codeInfo, installedCode);
        } finally {
            CodeInfoAccess.releaseTether(codeInfo, tether);
        }
    }

    protected static void doInstallPreparedAndTethered(SharedMethod method, CodeInfo codeInfo, SubstrateInstalledCode installedCode) {
        InstallCodeOperation vmOp = new InstallCodeOperation(method, codeInfo, installedCode);
        vmOp.enqueue();
        if (vmOp.error != null) {
            throw rethrow(vmOp.error);
        }
    }

    @SuppressWarnings({"unchecked"})
    protected static <E extends Throwable> RuntimeException rethrow(Throwable ex) throws E {
        throw (E) ex;
    }

    protected static RuntimeCodeInstallerPlatformHelper platformHelper() {
        return ImageSingletons.lookup(RuntimeCodeInstallerPlatformHelper.class);
    }

    private static class InstallCodeOperation extends JavaVMOperation {
        private final SharedMethod method;
        private final CodeInfo codeInfo;
        private final SubstrateInstalledCode installedCode;
        private Throwable error;

        InstallCodeOperation(SharedMethod method, CodeInfo codeInfo, SubstrateInstalledCode installedCode) {
            super(VMOperationInfos.get(InstallCodeOperation.class, "Install code", SystemEffect.SAFEPOINT));
            this.method = method;
            this.codeInfo = codeInfo;
            this.installedCode = installedCode;
        }

        @Override
        protected void operate() {
            try {
                assert !installedCode.isValid() && !installedCode.isAlive();
                CodePointer codeStart = CodeInfoAccess.getCodeStart(codeInfo);
                UnsignedWord offset = CodeInfoAccess.getCodeEntryPointOffset(codeInfo);
                installedCode.setAddress(codeStart.rawValue(), codeStart.rawValue() + offset.rawValue(), method);
                CodeInfoTable.getRuntimeCodeCache().addMethod(codeInfo);
                platformHelper().performCodeSynchronization(codeInfo);
                VMError.guarantee(CodeInfoAccess.getState(codeInfo) == CodeInfo.STATE_CODE_CONSTANTS_LIVE && installedCode.isValid(), "The code can't be invalidated before the VM operation finishes");
            } catch (Throwable e) {
                error = e;
            }
        }
    }

    /** Methods which are platform specific. */
    public interface RuntimeCodeInstallerPlatformHelper {

        /**
         * Method to enable platforms to perform any needed operations before code becomes visible.
         *
         * @param codeInfo the new code to be installed
         */
        void performCodeSynchronization(CodeInfo codeInfo);
    }
}
