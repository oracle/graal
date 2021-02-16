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

import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.word.Pointer;

import com.oracle.svm.core.annotate.NeverInline;
import com.oracle.svm.core.annotate.RestrictHeapAccess;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.c.NonmovableObjectArray;
import com.oracle.svm.core.code.CodeInfo;
import com.oracle.svm.core.code.CodeInfoAccess;
import com.oracle.svm.core.code.CodeInfoTable;
import com.oracle.svm.core.code.FrameInfoDecoder.FrameInfoQueryResultAllocator;
import com.oracle.svm.core.code.FrameInfoDecoder.ValueInfoAllocator;
import com.oracle.svm.core.code.FrameInfoQueryResult;
import com.oracle.svm.core.code.FrameInfoQueryResult.ValueInfo;
import com.oracle.svm.core.code.ImageCodeInfo;
import com.oracle.svm.core.code.ReusableTypeReader;
import com.oracle.svm.core.deopt.DeoptimizationSupport;
import com.oracle.svm.core.deopt.DeoptimizedFrame;
import com.oracle.svm.core.log.Log;

public class ThreadStackPrinter {

    public static class StackFramePrintVisitor extends Stage1StackFramePrintVisitor {

        private static class SingleShotFrameInfoQueryResultAllocator implements FrameInfoQueryResultAllocator {
            private static FrameInfoQueryResult frameInfoQueryResult = new FrameInfoQueryResult();

            private boolean fired;

            void reload() {
                fired = false;
            }

            @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Provide allocation-free StackFrameVisitor")
            @Override
            public FrameInfoQueryResult newFrameInfoQueryResult() {
                if (fired) {
                    return null;
                }
                fired = true;
                frameInfoQueryResult.init();
                return frameInfoQueryResult;
            }
        }

        private static class DummyValueInfoAllocator implements ValueInfoAllocator {
            @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Provide allocation-free StackFrameVisitor")
            @Override
            public ValueInfo newValueInfo() {
                return null;
            }

            @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Provide allocation-free StackFrameVisitor")
            @Override
            public ValueInfo[] newValueInfoArray(int len) {
                return null;
            }

            @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Provide allocation-free StackFrameVisitor")
            @Override
            public ValueInfo[][] newValueInfoArrayArray(int len) {
                return null;
            }

            @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Provide allocation-free StackFrameVisitor")
            @Override
            public void decodeConstant(ValueInfo valueInfo, NonmovableObjectArray<?> frameInfoObjectConstants) {
            }
        }

        public static StackFramePrintVisitor SINGLETON = new StackFramePrintVisitor();

        StackFramePrintVisitor() {
        }

        private static ReusableTypeReader frameInfoReader = new ReusableTypeReader();

        private static SingleShotFrameInfoQueryResultAllocator SingleShotFrameInfoQueryResultAllocator = new SingleShotFrameInfoQueryResultAllocator();
        private static DummyValueInfoAllocator DummyValueInfoAllocator = new DummyValueInfoAllocator();

        @Override
        protected void logFrame(Log log, Pointer sp, CodePointer ip, CodeInfo codeInfo, DeoptimizedFrame deoptFrame) {
            if (deoptFrame != null) {
                logVirtualFrames(log, sp, ip, deoptFrame);
            } else {
                frameInfoReader.reset();
                long entryOffset = CodeInfoAccess.initFrameInfoReader(codeInfo, ip, frameInfoReader);
                if (entryOffset >= 0) {
                    boolean isFirst = true;
                    FrameInfoQueryResult validResult;
                    SingleShotFrameInfoQueryResultAllocator.reload();
                    while ((validResult = CodeInfoAccess.nextFrameInfo(codeInfo, entryOffset, frameInfoReader, SingleShotFrameInfoQueryResultAllocator, DummyValueInfoAllocator, isFirst)) != null) {
                        SingleShotFrameInfoQueryResultAllocator.reload();
                        if (!isFirst) {
                            log.newline();
                        }
                        logFrameRaw(log, sp, ip);
                        logFrameInfo(log, validResult, CodeInfoAccess.getName(codeInfo));
                        isFirst = false;
                    }
                } else {
                    super.logFrame(log, sp, ip, codeInfo, deoptFrame);
                }
            }
        }
    }

    public static class Stage0StackFramePrintVisitor extends ParameterizedStackFrameVisitor<Log> {

        public static Stage0StackFramePrintVisitor SINGLETON = new Stage0StackFramePrintVisitor();

        Stage0StackFramePrintVisitor() {
        }

        @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Provide allocation-free StackFrameVisitor")
        @Override
        protected final boolean visitFrame(Pointer sp, CodePointer ip, CodeInfo codeInfo, DeoptimizedFrame deoptFrame, Log log) {
            logFrame(log, sp, ip, codeInfo, deoptFrame);
            log.newline();
            return true;
        }

        @Override
        protected final boolean unknownFrame(Pointer sp, CodePointer ip, DeoptimizedFrame deoptimizedFrame, Log log) {
            logFrameRaw(log, sp, ip);
            if (DeoptimizationSupport.enabled()) {
                log.string("  deoptFrame=").object(deoptimizedFrame);
            }
            log.string("  IP is not within Java code. Aborting stack trace printing.").newline();
            return false;
        }

        @SuppressWarnings("unused")
        protected void logFrame(Log log, Pointer sp, CodePointer ip, CodeInfo codeInfo, DeoptimizedFrame deoptFrame) {
            logFrameRaw(log, sp, ip);
            log.string("  FrameSize ").signed(CodeInfoAccess.lookupTotalFrameSize(codeInfo, CodeInfoAccess.relativeIP(codeInfo, ip)));
        }

        protected static void logFrameRaw(Log log, Pointer sp, CodePointer ip) {
            log.string("SP ").zhex(sp.rawValue());
            log.string(" IP ").zhex(ip.rawValue());
        }
    }

    public static class Stage1StackFramePrintVisitor extends Stage0StackFramePrintVisitor {

        public static Stage1StackFramePrintVisitor SINGLETON = new Stage1StackFramePrintVisitor();

        Stage1StackFramePrintVisitor() {
        }

        protected static void logFrameInfo(Log log, FrameInfoQueryResult frameInfo, String runtimeMethodInfoName) {
            log.string("  ");
            if (runtimeMethodInfoName != null) {
                log.string("[").string(runtimeMethodInfoName).string("] ");
            }
            frameInfo.log(log);
        }

        protected static void logVirtualFrames(Log log, Pointer sp, CodePointer ip, DeoptimizedFrame deoptFrame) {
            for (DeoptimizedFrame.VirtualFrame frame = deoptFrame.getTopFrame(); frame != null; frame = frame.getCaller()) {
                logFrameRaw(log, sp, ip);
                logFrameInfo(log, frame.getFrameInfo(), ImageCodeInfo.CODE_INFO_NAME + ", deopt");
                if (frame.getCaller() != null) {
                    log.newline();
                }
            }
        }

        @Override
        protected void logFrame(Log log, Pointer sp, CodePointer ip, CodeInfo codeInfo, DeoptimizedFrame deoptFrame) {
            if (deoptFrame != null) {
                logVirtualFrames(log, sp, ip, deoptFrame);
            } else {
                logFrameRaw(log, sp, ip);
                log.spaces(2);
                CodeInfoAccess.log(codeInfo, log);
                log.string(" name = ").string(CodeInfoAccess.getName(codeInfo));
            }
        }
    }

    /** Walk the stack printing each frame. */
    @NeverInline("debugger breakpoint")
    @Uninterruptible(reason = "Called from uninterruptible code.")
    public static void printBacktrace() {
        // Only used as a debugger breakpoint
    }

    @Uninterruptible(reason = "Prevent deoptimization of stack frames while in this method.")
    public static void printStacktrace(Pointer startSP, CodePointer startIP, Stage0StackFramePrintVisitor printVisitor, Log log) {
        JavaStackWalk walk = StackValue.get(JavaStackWalk.class);
        JavaStackWalker.initWalk(walk, startSP, startIP);

        JavaFrameAnchor anchor = walk.getAnchor();
        if (walk.getIPCodeInfo().isNull() && anchor.isNonNull()) {
            logFrameAnchor(log, startSP, startIP);
            walk.setSP(anchor.getLastJavaSP());
            walk.setPossiblyStaleIP(anchor.getLastJavaIP());
            walk.setIPCodeInfo(CodeInfoTable.lookupCodeInfo(anchor.getLastJavaIP()));
        }

        JavaStackWalker.doWalk(walk, printVisitor, log);
    }

    @Uninterruptible(reason = "CodeInfo in JavaStackWalk is currently null, so printing to log is safe right now.", calleeMustBe = false)
    private static void logFrameAnchor(Log log, Pointer startSP, CodePointer startIP) {
        Stage0StackFramePrintVisitor.logFrameRaw(log, startSP, startIP);
        log.string("  IP is not within Java code. Trying frame anchor of last Java frame instead.").newline();
    }
}
