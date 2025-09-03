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
import com.oracle.svm.core.imagelayer.ImageLayerBuildingSupport;
import com.oracle.svm.core.log.Log;

import jdk.graal.compiler.word.Word;

public class ThreadStackPrinter {
    @Uninterruptible(reason = "Prevent deoptimization of stack frames while in this method.")
    public static boolean printStacktrace(IsolateThread thread, Pointer initialSP, CodePointer initialIP, StackFramePrintVisitor printVisitor, Log log) {
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
        StackFramePrintVisitor.logSPAndIP(log, sp, ip);
        log.string("  IP is not within Java code. Trying frame anchor of last Java frame instead.").newline();
    }

    /**
     * With every retry, the output is reduced a bit.
     * <ul>
     * <li>1st invocation: maximum details for AOT and JIT compiled code</li>
     * <li>2nd invocation: reduced details for JIT compiled code</li>
     * <li>3rd invocation: minimal information for both AOT and JIT compiled code</li>
     * </ul>
     */
    public static class StackFramePrintVisitor extends ParameterizedStackFrameVisitor {
        private static final int MAX_STACK_FRAMES_PER_THREAD_TO_PRINT = 100_000;

        private final CodeInfoDecoder.FrameInfoCursor frameInfoCursor = new CodeInfoDecoder.FrameInfoCursor();
        private int invocationCount;
        private int printedFrames;
        private Pointer expectedSP;

        public StackFramePrintVisitor() {
        }

        @SuppressWarnings("hiding")
        public StackFramePrintVisitor reset(int invocationCount) {
            assert invocationCount >= 1 && invocationCount <= 3;
            this.invocationCount = invocationCount;
            this.printedFrames = 0;
            this.expectedSP = Word.nullPointer();
            return this;
        }

        @Override
        @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Used for crash log")
        protected final boolean visitRegularFrame(Pointer sp, CodePointer ip, CodeInfo codeInfo, Object data) {
            return visitFrame(sp, ip, codeInfo, null, (Log) data);
        }

        @Override
        @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Used for crash log")
        protected boolean visitDeoptimizedFrame(Pointer originalSP, CodePointer deoptStubIP, DeoptimizedFrame deoptFrame, Object data) {
            return visitFrame(originalSP, deoptStubIP, Word.nullPointer(), deoptFrame, (Log) data);
        }

        @Override
        protected final boolean unknownFrame(Pointer sp, CodePointer ip, Object data) {
            Log log = (Log) data;
            logFrameRaw(log, sp, ip, Word.nullPointer());
            log.string("  IP is not within Java code. Aborting stack trace printing.").newline();
            return false;
        }

        private boolean visitFrame(Pointer sp, CodePointer ip, CodeInfo codeInfo, DeoptimizedFrame deoptFrame, Log log) {
            if (printedFrames >= MAX_STACK_FRAMES_PER_THREAD_TO_PRINT) {
                log.string("... (truncated)").newline();
                return false;
            }

            if (invocationCount >= 3) {
                logJavaFrameMinimalInfo(log, sp, ip, codeInfo, deoptFrame, true);
                log.string("CodeInfo ").zhex(codeInfo).string(", ");
                log.string("DeoptimizedFrame ").zhex(Word.objectToUntrackedPointer(deoptFrame));
            } else {
                if (expectedSP.isNonNull() && sp != expectedSP) {
                    logNativeFrame(log, expectedSP, ip, sp.subtract(expectedSP).rawValue());
                    log.newline();
                }

                if (deoptFrame != null) {
                    logDeoptimizedJavaFrame(log, sp, ip, deoptFrame);
                } else {
                    logRegularJavaFrame(log, sp, ip, codeInfo);
                }
            }
            log.newline();
            return true;
        }

        private void logDeoptimizedJavaFrame(Log log, Pointer sp, CodePointer ip, DeoptimizedFrame deoptFrame) {
            for (DeoptimizedFrame.VirtualFrame frame = deoptFrame.getTopFrame(); frame != null; frame = frame.getCaller()) {
                if (printedFrames >= MAX_STACK_FRAMES_PER_THREAD_TO_PRINT) {
                    log.string("... (truncated)").newline();
                    break;
                }

                boolean isCompilationRoot = frame.getCaller() == null;
                printFrameIdentifier(log, Word.nullPointer(), deoptFrame, isCompilationRoot, false);
                logFrameRaw(log, sp, ip, deoptFrame.getSourceTotalFrameSize());
                logFrameInfo(log, frame.getFrameInfo(), ImageCodeInfo.CODE_INFO_NAME + ", deopt");
                if (!isCompilationRoot) {
                    log.newline();
                }
            }
        }

        private void logRegularJavaFrame(Log log, Pointer sp, CodePointer ip, CodeInfo codeInfo) {
            if (invocationCount == 1 || CodeInfoAccess.isAOTImageCodeSlow(codeInfo)) {
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

                    boolean isCompilationRoot = !frameInfoCursor.hasCaller();
                    logVirtualFrame(log, sp, ip, codeInfo, frameInfoCursor.get(), isCompilationRoot);
                    isFirst = false;
                }

                if (isFirst) {
                    /* We don't have any metadata, so print less detailed information. */
                    logJavaFrameMinimalInfo(log, sp, ip, codeInfo, null, true);
                    log.string("missing metadata");
                }
            } else {
                /* Print less details for JIT compiled code if printing already failed once. */
                logJavaFrameMinimalInfo(log, sp, ip, codeInfo, null, true);
                log.string("CodeInfo ").zhex(codeInfo);
            }
        }

        private void logVirtualFrame(Log log, Pointer sp, CodePointer ip, CodeInfo codeInfo, FrameInfoQueryResult frameInfo, boolean isCompilationRoot) {
            logJavaFrameMinimalInfo(log, sp, ip, codeInfo, null, isCompilationRoot);

            String codeInfoName = DeoptimizationSupport.enabled() ? CodeInfoAccess.getName(codeInfo) : null;
            logFrameInfo(log, frameInfo, codeInfoName);
        }

        private void logJavaFrameMinimalInfo(Log log, Pointer sp, CodePointer ip, CodeInfo codeInfo, DeoptimizedFrame deoptFrame, boolean isCompilationRoot) {
            printFrameIdentifier(log, codeInfo, deoptFrame, isCompilationRoot, false);
            logFrameRaw(log, sp, ip, codeInfo);
        }

        private void logNativeFrame(Log log, Pointer sp, CodePointer ip, long frameSize) {
            printFrameIdentifier(log, Word.nullPointer(), null, true, true);
            logFrameRaw(log, sp, ip, frameSize);
            log.string("unknown");
        }

        private void logFrameRaw(Log log, Pointer sp, CodePointer ip, CodeInfo codeInfo) {
            long frameSize = -1;
            if (codeInfo.isNonNull()) {
                frameSize = CodeInfoAccess.lookupTotalFrameSize(codeInfo, ip);
            }
            logFrameRaw(log, sp, ip, frameSize);
        }

        private void logFrameRaw(Log log, Pointer sp, CodePointer ip, long frameSize) {
            logLayer(log, ip);
            logSPAndIP(log, sp, ip);
            log.string(" size=");
            if (frameSize >= 0) {
                log.signed(frameSize, 4, Log.LEFT_ALIGN);
                expectedSP = sp.add(Word.unsigned(frameSize));
            } else {
                log.string("?").spaces(3);
                expectedSP = Word.nullPointer();
            }
            log.spaces(2);
            printedFrames++;
        }

        private static void logLayer(Log log, CodePointer ip) {
            if (ImageLayerBuildingSupport.buildingImageLayer()) {
                CodeInfo info = CodeInfoTable.getFirstImageCodeInfo();
                int layerNumber = 0;
                while (info.isNonNull()) {
                    if (CodeInfoAccess.contains(info, ip)) {
                        log.string("L").unsigned(layerNumber).spaces(2);
                        return;
                    }
                    info = CodeInfoAccess.getNextImageCodeInfo(info);
                    layerNumber++;
                }
                // No layer information found, print spaces for alignment.
                log.spaces(4);
            }
        }

        private static void logSPAndIP(Log log, Pointer sp, CodePointer ip) {
            log.string("SP ").zhex(sp).spaces(1);
            log.string("IP ").zhex(ip);
        }

        private static void logFrameInfo(Log log, FrameInfoQueryResult frameInfo, String runtimeMethodInfoName) {
            if (runtimeMethodInfoName != null) {
                log.string("[").string(runtimeMethodInfoName).string("] ");
            }
            frameInfo.log(log);
        }

        private static void printFrameIdentifier(Log log, CodeInfo codeInfo, DeoptimizedFrame deoptFrame, boolean isCompilationRoot, boolean isNative) {
            char ch = getFrameIdentifier(codeInfo, deoptFrame, isCompilationRoot, isNative);
            log.character(ch).spaces(2);
        }

        private static char getFrameIdentifier(CodeInfo codeInfo, DeoptimizedFrame deoptFrame, boolean isCompilationRoot, boolean isNative) {
            if (isNative) {
                return 'C';
            } else if (deoptFrame != null) {
                return 'D';
            } else if (!isCompilationRoot) {
                return 'i';
            } else if (codeInfo.isNonNull() && CodeInfoAccess.isAOTImageCodeSlow(codeInfo)) {
                return 'A';
            } else {
                return 'J';
            }
        }
    }
}
