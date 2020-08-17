/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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

//Checkstyle: stop

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

import com.oracle.svm.core.thread.JavaThreads;
import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionType;
import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platform.WINDOWS;
import org.graalvm.nativeimage.ProcessProperties;
import org.graalvm.nativeimage.VMRuntime;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.deopt.DeoptimizationSupport;
import com.oracle.svm.core.jdk.RuntimeSupport;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.stack.JavaStackWalker;
import com.oracle.svm.core.stack.ThreadStackPrinter.StackFramePrintVisitor;
import com.oracle.svm.core.thread.JavaVMOperation;
import com.oracle.svm.core.thread.VMThreads;

import sun.misc.Signal;
import sun.misc.SignalHandler;

//Checkstyle: resume

@AutomaticFeature
public class VMInspection implements Feature {

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return isEnabled() || VMInspectionOptions.DumpThreadStacksOnSignal.getValue();
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        RuntimeSupport.getRuntimeSupport().addStartupHook(() -> {
            DumpAllStacks.install();
            if (VMInspectionOptions.AllowVMInspection.getValue() && !Platform.includedIn(WINDOWS.class)) {
                /* We have enough signals to enable the rest. */
                DumpHeapReport.install();
                if (DeoptimizationSupport.enabled()) {
                    DumpRuntimeCompilation.install();
                }
            }
        });
    }

    @Fold
    public static boolean isEnabled() {
        return VMInspectionOptions.AllowVMInspection.getValue();
    }
}

class VMInspectionOptions {
    @Option(help = "Enables features that allow the VM to be inspected during runtime.", type = OptionType.User) //
    public static final HostedOptionKey<Boolean> AllowVMInspection = new HostedOptionKey<>(false);

    @Option(help = "Dumps all thread stacktraces on SIGQUIT/SIGBREAK.", type = OptionType.User) //
    public static final HostedOptionKey<Boolean> DumpThreadStacksOnSignal = new HostedOptionKey<>(false);
}

class DumpAllStacks implements SignalHandler {
    static void install() {
        Signal.handle(Platform.includedIn(WINDOWS.class) ? new Signal("BREAK") : new Signal("QUIT"), new DumpAllStacks());
    }

    @Override
    public void handle(Signal arg0) {
        JavaVMOperation.enqueueBlockingSafepoint("DumpAllStacks", () -> {
            Log log = Log.log();
            log.string("Full thread dump:").newline().newline();
            for (IsolateThread vmThread = VMThreads.firstThread(); vmThread.isNonNull(); vmThread = VMThreads.nextThread(vmThread)) {
                if (vmThread == CurrentIsolate.getCurrentThread()) {
                    /* Skip the signal handler stack */
                    continue;
                }
                try {
                    dumpStack(log, vmThread);
                } catch (Exception e) {
                    log.string("Exception during dumpStack: ").string(e.getClass().getName()).newline();
                    log.string(e.getMessage()).newline();
                }
            }
            log.flush();
        });
    }

    private static void dumpStack(Log log, IsolateThread vmThread) {
        Thread javaThread = JavaThreads.fromVMThread(vmThread);
        if (javaThread != null) {
            log.character('"').string(javaThread.getName()).character('"');
            log.string(" #").signed(javaThread.getId());
            if (javaThread.isDaemon()) {
                log.string(" daemon");
            }
        } else {
            log.string("(no Java thread)");
        }
        log.string(" tid=0x").zhex(vmThread.rawValue());
        if (javaThread != null) {
            log.string(" state=").string(javaThread.getState().name());
        }
        log.newline();

        log.indent(true);
        JavaStackWalker.walkThread(vmThread, StackFramePrintVisitor.SINGLETON, log);
        log.indent(false);
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
        String heapDumpFileName = "svm-heapdump-" + ProcessProperties.getProcessID() + "-" + dateFormat.format(new Date()) + ".hprof";
        try {
            VMRuntime.dumpHeap(heapDumpFileName, true);
        } catch (IOException e) {
            Log.log().string("IOException during dumpHeap: ").string(e.getMessage()).newline();
        }
    }
}

class DumpRuntimeCompilation implements SignalHandler {
    static void install() {
        Signal.handle(new Signal("USR2"), new DumpRuntimeCompilation());
    }

    @Override
    public void handle(Signal arg0) {
        JavaVMOperation.enqueueBlockingSafepoint("DumpRuntimeCompilation", () -> {
            Log log = Log.log();
            SubstrateUtil.dumpRuntimeCompilation(log);
            log.flush();
        });
    }
}
