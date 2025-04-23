/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.truffle.nfi;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.heap.VMOperationInfos;
import com.oracle.svm.core.thread.JavaVMOperation;
import com.oracle.svm.core.thread.PlatformThreads;
import com.oracle.truffle.api.CompilerDirectives;
import org.graalvm.nativeimage.IsolateThread;

@TargetClass(className = "com.oracle.truffle.nfi.backend.spi.NFIState", onlyWith = TruffleNFIFeature.IsEnabled.class)
final class Target_com_oracle_truffle_nfi_backend_spi_NFIState {

    @Alias boolean hasPendingException;

    @Substitute
    private static long initNfiErrnoAddress(Thread thread) {
        var op = new GetErrnoMirrorAddressOperation(thread);
        op.enqueue();
        long address = op.result;
        if (address == 0) {
            throw CompilerDirectives.shouldNotReachHere("Could not find the IsolateThread for " + thread);
        }
        return address;
    }

    @Substitute
    private void freeNfiErrnoAddress() {
    }

    private static class GetErrnoMirrorAddressOperation extends JavaVMOperation {
        private final Thread thread;
        private long result = 0;

        GetErrnoMirrorAddressOperation(Thread thread) {
            super(VMOperationInfos.get(GetErrnoMirrorAddressOperation.class, "Get ErrnoMirror address", SystemEffect.SAFEPOINT));
            this.thread = thread;
        }

        @Override
        protected void operate() {
            IsolateThread isolateThread = PlatformThreads.getIsolateThread(thread);
            if (isolateThread.isNonNull()) {
                this.result = ErrnoMirror.errnoMirror.getAddress(isolateThread).rawValue();
            }
        }
    }

    @Alias
    native void setPendingException(Throwable t);
}
