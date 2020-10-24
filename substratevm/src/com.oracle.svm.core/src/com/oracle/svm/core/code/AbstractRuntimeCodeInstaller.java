/*
 * Copyright (c) 2013, 2020, Oracle and/or its affiliates. All rights reserved.
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
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.deopt.SubstrateInstalledCode;
import com.oracle.svm.core.meta.SharedMethod;
import com.oracle.svm.core.thread.JavaVMOperation;

public class AbstractRuntimeCodeInstaller {
    protected Pointer allocateCodeMemory(long size) {
        PointerBase result = RuntimeCodeInfoAccess.allocateCodeMemory(WordFactory.unsigned(size));
        if (result.isNull()) {
            throw new OutOfMemoryError();
        }
        return (Pointer) result;
    }

    protected void makeCodeMemoryReadOnly(Pointer start, long size) {
        RuntimeCodeInfoAccess.makeCodeMemoryExecutableReadOnly((CodePointer) start, WordFactory.unsigned(size));
    }

    protected void makeCodeMemoryWriteableNonExecutable(Pointer start, long size) {
        RuntimeCodeInfoAccess.makeCodeMemoryWriteableNonExecutable((CodePointer) start, WordFactory.unsigned(size));
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
        Throwable[] errorBox = {null};
        JavaVMOperation.enqueueBlockingSafepoint("Install code", () -> {
            try {
                CodeInfoTable.getRuntimeCodeCache().addMethod(codeInfo);
                CodePointer codeStart = CodeInfoAccess.getCodeStart(codeInfo);
                platformHelper().performCodeSynchronization(codeInfo);
                installedCode.setAddress(codeStart.rawValue(), method);
            } catch (Throwable e) {
                errorBox[0] = e;
            }
        });
        if (errorBox[0] != null) {
            throw rethrow(errorBox[0]);
        }
    }

    @SuppressWarnings({"unchecked"})
    protected static <E extends Throwable> RuntimeException rethrow(Throwable ex) throws E {
        throw (E) ex;
    }

    protected static RuntimeCodeInstallerPlatformHelper platformHelper() {
        return ImageSingletons.lookup(RuntimeCodeInstallerPlatformHelper.class);
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
