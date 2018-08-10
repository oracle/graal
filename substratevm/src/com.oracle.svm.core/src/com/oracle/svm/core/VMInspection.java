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

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionType;
import org.graalvm.nativeimage.Feature;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.c.function.CEntryPointContext;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.annotate.NeverInline;
import com.oracle.svm.core.jdk.RuntimeSupport;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.stack.JavaStackWalker;
import com.oracle.svm.core.stack.ThreadStackPrinter;
import com.oracle.svm.core.thread.VMOperation;
import com.oracle.svm.core.thread.VMThreads;
import org.graalvm.compiler.api.replacements.Fold;

//Checkstyle: stop
import sun.misc.Signal;
import sun.misc.SignalHandler;
//Checkstyle: resume

@AutomaticFeature
public class VMInspection implements Feature {

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return isEnabled();
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        RuntimeSupport.getRuntimeSupport().addStartupHook(() -> {
            DumpAllStacks.install();
            DumpHeapReport.install();
            DumpRuntimeCompilation.install();
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
}

class DumpAllStacks implements SignalHandler {
    static void install() {
        Signal.handle(new Signal("QUIT"), new DumpAllStacks());
    }

    @Override
    public void handle(Signal arg0) {
        VMOperation.enqueueBlockingSafepoint("DumpAllStacks", () -> {
            Log log = Log.log();
            for (IsolateThread vmThread = VMThreads.firstThread(); VMThreads.isNonNullThread(vmThread); vmThread = VMThreads.nextThread(vmThread)) {
                if (vmThread == CEntryPointContext.getCurrentIsolateThread()) {
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

    @NeverInline("catch implicit exceptions")
    private static void dumpStack(Log log, IsolateThread vmThread) {
        log.string("VMThread ").zhex(vmThread.rawValue()).spaces(2).string(VMThreads.StatusSupport.getStatusString(vmThread)).newline();
        log.indent(true);
        JavaStackWalker.walkThread(vmThread, ThreadStackPrinter.AllocationFreeStackFrameVisitor);
        log.indent(false);
    }
}

class DumpHeapReport implements SignalHandler {
    static void install() {
        Signal.handle(new Signal("USR1"), new DumpHeapReport());
    }

    @SuppressWarnings("deprecation")
    @NeverInline("Ensure ClassCastException gets caught")
    private static void performHeapDump(FileOutputStream fileOutputStream) throws Exception {
        Object[] args = new Object[]{"HeapDump.dumpHeap(FileOutputStream, Boolean)Boolean", fileOutputStream, Boolean.TRUE};
        if (!((Boolean) Compiler.command(args))) {
            throw new RuntimeException();
        }
    }

    @Override
    public void handle(Signal arg0) {
        Path heapDumpFilePath = null;
        FileOutputStream fileOutputStream = null;
        try {
            heapDumpFilePath = Files.createTempFile(Paths.get("."), "svm-heapdump-", ".hprof");
            fileOutputStream = new FileOutputStream(heapDumpFilePath.toFile());
            performHeapDump(fileOutputStream);
        } catch (Exception e) {
            Log.log().string("svm-heapdump failed").newline().flush();
            try {
                if (fileOutputStream != null) {
                    fileOutputStream.close();
                }
                if (heapDumpFilePath != null) {
                    Files.deleteIfExists(heapDumpFilePath);
                }
            } catch (IOException e1) {
            }
        }
    }
}

class DumpRuntimeCompilation implements SignalHandler {
    static void install() {
        Signal.handle(new Signal("USR2"), new DumpRuntimeCompilation());
    }

    @Override
    public void handle(Signal arg0) {
        VMOperation.enqueueBlockingSafepoint("DumpRuntimeCompilation", () -> {
            Log log = Log.log();
            SubstrateUtil.dumpRuntimeCompilation(log);
            log.flush();
        });
    }
}
