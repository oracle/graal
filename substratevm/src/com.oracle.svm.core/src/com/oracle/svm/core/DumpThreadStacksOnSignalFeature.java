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

import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platform.WINDOWS;

import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.heap.VMOperationInfos;
import com.oracle.svm.core.jdk.RuntimeSupport;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.stack.JavaStackWalker;
import com.oracle.svm.core.stack.ThreadStackPrinter.StackFramePrintVisitor;
import com.oracle.svm.core.thread.JavaThreads;
import com.oracle.svm.core.thread.JavaVMOperation;
import com.oracle.svm.core.thread.PlatformThreads;
import com.oracle.svm.core.thread.VMThreads;

import jdk.internal.misc.Signal;

@AutomaticallyRegisteredFeature
public class DumpThreadStacksOnSignalFeature implements InternalFeature {

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return VMInspectionOptions.hasThreadDumpSupport();
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        RuntimeSupport.getRuntimeSupport().addStartupHook(new DumpThreadStacksOnSignalStartupHook());
    }
}

final class DumpThreadStacksOnSignalStartupHook implements RuntimeSupport.Hook {
    @Override
    public void execute(boolean isFirstIsolate) {
        if (isFirstIsolate) {
            DumpAllStacks.install();
        }
    }
}

class DumpAllStacks implements Signal.Handler {
    static void install() {
        Signal.handle(Platform.includedIn(WINDOWS.class) ? new Signal("BREAK") : new Signal("QUIT"), new DumpAllStacks());
    }

    @Override
    public void handle(Signal arg0) {
        DumpAllStacksOperation vmOp = new DumpAllStacksOperation();
        vmOp.enqueue();
    }

    private static class DumpAllStacksOperation extends JavaVMOperation {
        DumpAllStacksOperation() {
            super(VMOperationInfos.get(DumpAllStacksOperation.class, "Dump all stacks", SystemEffect.SAFEPOINT));
        }

        @Override
        protected void operate() {
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
        }

        private static void dumpStack(Log log, IsolateThread vmThread) {
            Thread javaThread = PlatformThreads.fromVMThread(vmThread);
            if (javaThread != null) {
                log.character('"').string(javaThread.getName()).character('"');
                log.string(" #").signed(JavaThreads.getThreadId(javaThread));
                if (javaThread.isDaemon()) {
                    log.string(" daemon");
                }
            } else {
                log.string("(no Java thread)");
            }
            log.string(" thread=").zhex(vmThread);
            if (javaThread != null) {
                log.string(" state=").string(javaThread.getState().name());
            }
            log.newline();

            log.indent(true);
            StackFramePrintVisitor visitor = new StackFramePrintVisitor();
            JavaStackWalker.walkThread(vmThread, visitor, log);
            log.indent(false);
        }
    }
}
