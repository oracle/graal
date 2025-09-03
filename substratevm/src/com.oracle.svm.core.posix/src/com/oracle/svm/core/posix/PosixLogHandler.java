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
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.SubstrateDiagnostics;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.headers.LibC;
import com.oracle.svm.core.imagelayer.ImageLayerBuildingSupport;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.thread.VMThreads;
import com.oracle.svm.core.traits.BuiltinTraits.BuildtimeAccessOnly;
import com.oracle.svm.core.traits.BuiltinTraits.NoLayeredCallbacks;
import com.oracle.svm.core.traits.BuiltinTraits.RuntimeAccessOnly;
import com.oracle.svm.core.traits.BuiltinTraits.SingleLayer;
import com.oracle.svm.core.traits.SingletonLayeredInstallationKind.Independent;
import com.oracle.svm.core.traits.SingletonLayeredInstallationKind.InitialLayerOnly;
import com.oracle.svm.core.traits.SingletonTraits;

@SingletonTraits(access = BuildtimeAccessOnly.class, layeredCallbacks = NoLayeredCallbacks.class, layeredInstallationKind = Independent.class)
@AutomaticallyRegisteredFeature
class PosixLogHandlerFeature implements InternalFeature {
    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return ImageLayerBuildingSupport.firstImageBuild();
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        Log.finalizeDefaultLogHandler(new PosixLogHandler());
    }
}

@SingletonTraits(access = RuntimeAccessOnly.class, layeredCallbacks = SingleLayer.class, layeredInstallationKind = InitialLayerOnly.class)
public class PosixLogHandler implements LogHandler {

    @Override
    public void log(CCharPointer bytes, UnsignedWord length) {
        /* Save and restore errno around calls that would otherwise change errno. */
        final int savedErrno = LibC.errno();
        try {
            if (!PosixUtils.write(getOutputFile(), bytes, length)) {
                /*
                 * We are in a low-level log routine and output failed, so there is little we can
                 * do.
                 */
                fatalError();
            }
        } finally {
            LibC.setErrno(savedErrno);
        }
    }

    @Override
    public void flush() {
        /* Save and restore errno around calls that would otherwise change errno. */
        final int savedErrno = LibC.errno();
        try {
            PosixUtils.flush(getOutputFile());
            /* ignore error -- they're benign */
        } finally {
            LibC.setErrno(savedErrno);
        }
    }

    @Override
    public void fatalError() {
        if (SubstrateDiagnostics.isFatalErrorHandlingInProgress()) {
            // Delay the shutdown a bit if another thread has something important to report.
            VMThreads.singleton().nativeSleep(3000);
        }
        LibC.abort();
    }

    private static FileDescriptor getOutputFile() {
        return FileDescriptor.err;
    }
}
