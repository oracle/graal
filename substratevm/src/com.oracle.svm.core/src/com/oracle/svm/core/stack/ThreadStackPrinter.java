/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.stack;

import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.code.CodeInfo;
import com.oracle.svm.core.code.CodeInfoAccess;
import com.oracle.svm.core.code.CodeInfoDecoder;
import com.oracle.svm.core.code.CodeInfoTable;
import com.oracle.svm.core.code.FrameInfoQueryResult;
import com.oracle.svm.core.code.ImageCodeInfo;
import com.oracle.svm.core.code.UntetheredCodeInfo;
import com.oracle.svm.core.deopt.DeoptimizationSupport;
import com.oracle.svm.core.deopt.DeoptimizedFrame;
import com.oracle.svm.core.heap.RestrictHeapAccess;
import com.oracle.svm.core.log.Log;

public class ThreadStackPrinter {
    private static final int MAX_STACK_FRAMES_PER_THREAD_TO_PRINT = 100_000;

    public static class StackFramePrintVisitor extends Stage1StackFramePrintVisitor {
        private final CodeInfoDecoder.FrameInfoCursor frameInfoCursor = new CodeInfoDecoder.FrameInfoCursor();

        public StackFramePrintVisitor() {
        }

        @Override
        protected void logFrame(Log log, Pointer sp, CodePointer ip, CodeInfo codeInfo, DeoptimizedFrame deoptFrame) {
            if (deoptFrame != null) {
                logVirtualFrames(log, sp, ip, codeInfo, deoptFrame);
                return;
            }

            boolean isFirst = true;
            frameInfoCursor.initialize(codeInfo, ip, false);
            while (frameInfoCursor.advance()) {
                if (printedFrames >= MAX_STACK_FRAMES_PER_THREAD_TO_PRINT) {
                    log.string("... (truncated)").newline();
                    break;
                }

                if (!isFirst) {
                    log.newline();
                }

                boolean compilationRoot = !frameInfoCursor.hasCaller();
                printFrameIdentifier(log, codeInfo, null, compilationRoot);
                logFrameRaw(log, sp, ip, codeInfo);

                String codeInfoName = DeoptimizationSupport.enabled() ? CodeInfoAccess.getName(codeInfo) : null;
                logFrameInfo(log, frameInfoCursor.get(), codeInfoName);
                isFirst = false;
                printedFrames++;
            }

            if (isFirst) {
                /* We don't have any metadata, so print less detailed information. */
                super.logFrame(log, sp, ip, codeInfo, null);
                log.string("missing metadata");
            }
        }
    }

    public static class Stage0StackFramePrintVisitor extends ParameterizedStackFrameVisitor {
        protected int printedFrames;

        public Stage0StackFramePrintVisitor() {
        }

        public Stage0StackFramePrintVisitor reset() {
            printedFrames = 0;
            return this;
        }

        @Override
        @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Provide allocation-free StackFrameVisitor")
        protected final boolean visitRegularFrame(Pointer sp, CodePointer ip, CodeInfo codeInfo, Object data) {
            return visitFrame(sp, ip, codeInfo, null, (Log) data);
        }

        @Override
        @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Provide allocation-free StackFrameVisitor")
        protected boolean visitDeoptimizedFrame(Pointer originalSP, CodePointer deoptStubIP, DeoptimizedFrame deoptFrame, Object data) {
            CodeInfo imageCodeInfo = CodeInfoTable.lookupImageCodeInfo(deoptStubIP);
            return visitFrame(originalSP, deoptStubIP, imageCodeInfo, deoptFrame, (Log) data);
        }

        private boolean visitFrame(Pointer sp, CodePointer ip, CodeInfo codeInfo, DeoptimizedFrame deoptFrame, Log log) {
            if (printedFrames >= MAX_STACK_FRAMES_PER_THREAD_TO_PRINT) {
                log.string("... (truncated)").newline();
                return false;
            }

            logFrame(log, sp, ip, codeInfo, deoptFrame);
            log.newline();
            return true;
        }

        @Override
        protected final boolean unknownFrame(Pointer sp, CodePointer ip, Object data) {
            Log log = (Log) data;
            logFrameRaw(log, sp, ip, WordFactory.nullPointer());
            log.string("  IP is not within Java code. Aborting stack trace printing.").newline();
            printedFrames++;
            return false;
        }

        @SuppressWarnings("unused")
        protected void logFrame(Log log, Pointer sp, CodePointer ip, CodeInfo codeInfo, DeoptimizedFrame deoptFrame) {
            logFrameRaw(log, sp, ip, codeInfo);
            printedFrames++;
        }

        protected static void logFrameRaw(Log log, Pointer sp, CodePointer ip, CodeInfo codeInfo) {
            log.string("SP ").zhex(sp);
            log.string(" IP ").zhex(ip);
            log.string(" size=");
            if (codeInfo.isNonNull()) {
                long frameSize = CodeInfoAccess.lookupTotalFrameSize(codeInfo, ip);
                log.signed(frameSize, 4, Log.LEFT_ALIGN);
            } else {
                log.string("unknown");
            }
        }
    }

    public static class Stage1StackFramePrintVisitor extends Stage0StackFramePrintVisitor {
        public Stage1StackFramePrintVisitor() {
        }

        @Override
        protected void logFrame(Log log, Pointer sp, CodePointer ip, CodeInfo codeInfo, DeoptimizedFrame deoptFrame) {
            if (deoptFrame != null) {
                logVirtualFrames(log, sp, ip, codeInfo, deoptFrame);
            } else {
                logStackFrame(log, sp, ip, codeInfo);
            }
        }

        protected void logVirtualFrames(Log log, Pointer sp, CodePointer ip, CodeInfo codeInfo, DeoptimizedFrame deoptFrame) {
            for (DeoptimizedFrame.VirtualFrame frame = deoptFrame.getTopFrame(); frame != null; frame = frame.getCaller()) {
                if (printedFrames >= MAX_STACK_FRAMES_PER_THREAD_TO_PRINT) {
                    log.string("... (truncated)").newline();
                    break;
                }

                boolean compilationRoot = frame.getCaller() == null;
                printFrameIdentifier(log, WordFactory.nullPointer(), deoptFrame, compilationRoot);
                logFrameRaw(log, sp, ip, codeInfo);
                logFrameInfo(log, frame.getFrameInfo(), ImageCodeInfo.CODE_INFO_NAME + ", deopt");
                if (!compilationRoot) {
                    log.newline();
                }
                printedFrames++;
            }
        }

        private void logStackFrame(Log log, Pointer sp, CodePointer ip, CodeInfo codeInfo) {
            printFrameIdentifier(log, codeInfo, null, true);
            logFrameRaw(log, sp, ip, codeInfo);
            log.spaces(2);
            if (DeoptimizationSupport.enabled()) {
                log.string("[").string(CodeInfoAccess.getName(codeInfo)).string("] ");
            }
            printedFrames++;
        }

        protected static void logFrameInfo(Log log, FrameInfoQueryResult frameInfo, String runtimeMethodInfoName) {
            log.string("  ");
            if (runtimeMethodInfoName != null) {
                log.string("[").string(runtimeMethodInfoName).string("] ");
            }
            frameInfo.log(log);
        }

        protected static void printFrameIdentifier(Log log, CodeInfo codeInfo, DeoptimizedFrame deoptFrame, boolean isCompilationRoot) {
            char ch = getFrameIdentifier(codeInfo, deoptFrame, isCompilationRoot);
            log.character(ch).spaces(2);
        }

        private static char getFrameIdentifier(CodeInfo codeInfo, DeoptimizedFrame deoptFrame, boolean isCompilationRoot) {
            if (deoptFrame != null) {
                return 'D';
            } else if (!isCompilationRoot) {
                return 'i';
            } else if (codeInfo.isNonNull() && CodeInfoAccess.isAOTImageCode(codeInfo)) {
                return 'A';
            } else {
                return 'J';
            }
        }
    }

    @Uninterruptible(reason = "Prevent deoptimization of stack frames while in this method.")
    public static boolean printStacktrace(IsolateThread thread, Pointer initialSP, CodePointer initialIP, Stage0StackFramePrintVisitor printVisitor, Log log) {
        Pointer sp = initialSP;
        CodePointer ip = initialIP;

        /* Don't start the stack walk in a non-Java frame, even if the crash happened there. */
        UntetheredCodeInfo info = CodeInfoTable.lookupCodeInfo(ip);
        if (info.isNull()) {
            logFrame(log, sp, ip);

            JavaFrameAnchor anchor = JavaFrameAnchors.getFrameAnchor(thread);
            if (anchor.isNonNull()) {
                sp = anchor.getLastJavaSP();
                ip = anchor.getLastJavaIP();
            } else {
                return false;
            }
        }

        JavaStackWalk walk = StackValue.get(JavaStackWalker.sizeOfJavaStackWalk());
        JavaStackWalker.initialize(walk, thread, sp, ip);
        return JavaStackWalker.doWalk(walk, thread, printVisitor, log);
    }

    @Uninterruptible(reason = "IP is not within Java code, so there is no risk that it gets invalidated.", calleeMustBe = false)
    private static void logFrame(Log log, Pointer sp, CodePointer ip) {
        Stage0StackFramePrintVisitor.logFrameRaw(log, sp, ip, WordFactory.nullPointer());
        log.string("  IP is not within Java code. Trying frame anchor of last Java frame instead.").newline();
    }
}
