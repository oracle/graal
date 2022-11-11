/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

import org.graalvm.nativeimage.ProcessProperties;
import org.graalvm.nativeimage.VMRuntime;

import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.jdk.RuntimeSupport;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;

import sun.misc.Signal;
import sun.misc.SignalHandler;

@AutomaticallyRegisteredFeature
public class DumpHeapOnSignalFeature implements InternalFeature {

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return VMInspectionOptions.hasHeapDumpSupport() && SubstrateOptions.EnableSignalAPI.getValue();
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        RuntimeSupport.getRuntimeSupport().addStartupHook(new DumpHeapStartupHook());
    }
}

final class DumpHeapStartupHook implements RuntimeSupport.Hook {
    @Override
    public void execute(boolean isFirstIsolate) {
        if (isFirstIsolate) {
            DumpHeapReport.install();
        }
    }
}

class DumpHeapReport implements SignalHandler {
    private static final TimeZone UTC_TIMEZONE = TimeZone.getTimeZone("UTC");

    static void install() {
        Signal.handle(new Signal("USR1"), new DumpHeapReport());
    }

    @Override
    public void handle(Signal arg0) {
        DateFormat dateFormat = new SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'");
        dateFormat.setTimeZone(UTC_TIMEZONE);
        String defaultHeapDumpFileName = "svm-heapdump-" + ProcessProperties.getProcessID() + "-" + dateFormat.format(new Date()) + ".hprof";
        String heapDumpPath = SubstrateOptions.getHeapDumpPath(defaultHeapDumpFileName);
        try {
            VMRuntime.dumpHeap(heapDumpPath, true);
        } catch (IOException e) {
            Log.log().string("IOException during dumpHeap: ").string(e.getMessage()).newline();
        }
    }
}
