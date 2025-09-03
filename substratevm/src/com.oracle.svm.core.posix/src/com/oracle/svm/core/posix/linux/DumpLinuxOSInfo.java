/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.posix.linux;

import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.word.Pointer;

import com.oracle.svm.core.SubstrateDiagnostics;
import com.oracle.svm.core.SubstrateDiagnostics.DiagnosticThunkRegistry;
import com.oracle.svm.core.SubstrateDiagnostics.ErrorContext;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.c.CGlobalData;
import com.oracle.svm.core.c.CGlobalDataFactory;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.heap.RestrictHeapAccess;
import com.oracle.svm.core.imagelayer.ImageLayerBuildingSupport;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.os.RawFileOperationSupport;
import com.oracle.svm.core.traits.BuiltinTraits.BuildtimeAccessOnly;
import com.oracle.svm.core.traits.BuiltinTraits.NoLayeredCallbacks;
import com.oracle.svm.core.traits.SingletonLayeredInstallationKind.Independent;
import com.oracle.svm.core.traits.SingletonTraits;

import jdk.graal.compiler.core.common.NumUtil;
import jdk.graal.compiler.word.Word;

class DumpLinuxOSInfo extends SubstrateDiagnostics.DiagnosticThunk {
    private static final CGlobalData<CCharPointer> MAX_THREADS_PATH = CGlobalDataFactory.createCString("/proc/sys/kernel/threads-max");
    private static final CGlobalData<CCharPointer> MAX_MAPPINGS_PATH = CGlobalDataFactory.createCString("/proc/sys/vm/max_map_count");
    private static final CGlobalData<CCharPointer> MAX_PID_PATH = CGlobalDataFactory.createCString("/proc/sys/kernel/pid_max");

    @Override
    public int maxInvocationCount() {
        return 1;
    }

    @Override
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate while printing diagnostics.")
    public void printDiagnostics(Log log, ErrorContext context, int maxDiagnosticLevel, int invocationCount) {
        log.string("OS information:").indent(true);

        log.string("Max threads: ");
        printFirstLine(log, MAX_THREADS_PATH.get());
        log.newline();

        log.string("Max memory mappings: ");
        printFirstLine(log, MAX_MAPPINGS_PATH.get());
        log.newline();

        log.string("Max PID: ");
        printFirstLine(log, MAX_PID_PATH.get());
        log.newline();

        log.indent(false);
    }

    private static void printFirstLine(Log log, CCharPointer filename) {
        RawFileOperationSupport fs = RawFileOperationSupport.nativeByteOrder();
        RawFileOperationSupport.RawFileDescriptor fd = fs.open(filename, RawFileOperationSupport.FileAccessMode.READ);
        if (!fs.isValid(fd)) {
            log.string("unknown");
            return;
        }

        try {
            int bufferSize = 64;
            CCharPointer buffer = StackValue.get(bufferSize);
            long readBytes = fs.read(fd, (Pointer) buffer, Word.unsigned(bufferSize));
            int length = countLineBytes(buffer, NumUtil.safeToInt(readBytes));
            log.string(buffer, length);
        } finally {
            fs.close(fd);
        }
    }

    private static int countLineBytes(CCharPointer buffer, int len) {
        for (int i = 0; i < len; i++) {
            if (buffer.read(i) == '\n') {
                return i;
            }
        }
        return len;
    }
}

@SingletonTraits(access = BuildtimeAccessOnly.class, layeredCallbacks = NoLayeredCallbacks.class, layeredInstallationKind = Independent.class)
@AutomaticallyRegisteredFeature
class DumpLinuxOSInfoFeature implements InternalFeature {
    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return ImageLayerBuildingSupport.firstImageBuild();
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        if (!SubstrateOptions.AsyncSignalSafeDiagnostics.getValue()) {
            DiagnosticThunkRegistry.singleton().addAfter(new DumpLinuxOSInfo(), SubstrateDiagnostics.DumpRuntimeInfo.class);
        }
    }
}
