/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.posix;

import java.io.FileDescriptor;

import org.graalvm.nativeimage.LogHandler;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.CErrorNumber;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.posix.headers.LibC;
import com.oracle.svm.core.posix.headers.Unistd;

@AutomaticFeature
class PosixLogHandlerFeature implements Feature {
    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        Log.finalizeDefaultLogHandler(new PosixLogHandler());
    }
}

public class PosixLogHandler implements LogHandler {

    @Override
    public void log(CCharPointer bytes, UnsignedWord length) {
        /* Save and restore errno around calls that would otherwise change errno. */
        final int savedErrno = CErrorNumber.getCErrorNumber();
        try {
            if (!PosixUtils.writeBytes(getOutputFile(), bytes, length)) {
                /*
                 * We are in a low-level log routine and output failed, so there is little we can
                 * do.
                 */
                fatalError();
            }
        } finally {
            CErrorNumber.setCErrorNumber(savedErrno);
        }
    }

    @Override
    public void flush() {
        /* Save and restore errno around calls that would otherwise change errno. */
        final int savedErrno = CErrorNumber.getCErrorNumber();
        try {
            PosixUtils.flush(getOutputFile());
            /* ignore error -- they're benign */
        } finally {
            CErrorNumber.setCErrorNumber(savedErrno);
        }
    }

    @Override
    public void fatalError() {
        if (SubstrateUtil.isPrintDiagnosticsInProgress()) {
            // Delay the shutdown a bit if another thread has something important to report.
            Unistd.sleep(3);
        }
        LibC.abort();
    }

    private static FileDescriptor getOutputFile() {
        return FileDescriptor.err;
    }
}
