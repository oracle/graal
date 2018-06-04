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

import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.word.Pointer;

import com.oracle.svm.core.annotate.NeverInline;
import com.oracle.svm.core.annotate.RestrictHeapAccess;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.code.AbstractCodeInfo;
import com.oracle.svm.core.code.CodeInfoTable;
import com.oracle.svm.core.code.FrameInfoDecoder.FrameInfoQueryResultAllocator;
import com.oracle.svm.core.code.FrameInfoDecoder.ValueInfoAllocator;
import com.oracle.svm.core.code.FrameInfoQueryResult;
import com.oracle.svm.core.code.FrameInfoQueryResult.ValueInfo;
import com.oracle.svm.core.code.ImageCodeInfo;
import com.oracle.svm.core.code.ReusableTypeReader;
import com.oracle.svm.core.deopt.DeoptimizedFrame;
import com.oracle.svm.core.log.Log;

public class ThreadStackPrinter {

    public static class AllocationFreeStackFrameVisitor extends Stage1StackFrameVisitor {

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
            public void decodeConstant(ValueInfo valueInfo, Object[] frameInfoObjectConstants) {
            }
        }

        private static ReusableTypeReader frameInfoReader = new ReusableTypeReader();

        private static SingleShotFrameInfoQueryResultAllocator SingleShotFrameInfoQueryResultAllocator = new SingleShotFrameInfoQueryResultAllocator();
        private static DummyValueInfoAllocator DummyValueInfoAllocator = new DummyValueInfoAllocator();

        @Override
        protected void logFrame(Log log, Pointer sp, CodePointer ip, DeoptimizedFrame deoptFrame) {
            if (deoptFrame != null) {
                logVirtualFrames(log, sp, ip, deoptFrame);
            } else {
                AbstractCodeInfo codeInfo = CodeInfoTable.lookupCodeInfo(ip);
                if (codeInfo != null) {
                    frameInfoReader.reset();
                    long entryOffset = codeInfo.initFrameInfoReader(ip, frameInfoReader);
                    if (entryOffset >= 0) {
                        boolean isFirst = true;
                        FrameInfoQueryResult validResult;
                        SingleShotFrameInfoQueryResultAllocator.reload();
                        while ((validResult = codeInfo.nextFrameInfo(entryOffset, frameInfoReader, SingleShotFrameInfoQueryResultAllocator, DummyValueInfoAllocator, isFirst)) != null) {
                            SingleShotFrameInfoQueryResultAllocator.reload();
                            if (!isFirst) {
                                log.newline();
                            }
                            logFrameRaw(log, sp, ip);
                            logFrameInfo(log, validResult, codeInfo.getName());
                            isFirst = false;
                        }
                    }
                }
            }
        }
    }

    public static class Stage0StackFrameVisitor implements StackFrameVisitor {
        @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Provide allocation-free StackFrameVisitor")
        @Override
        public boolean visitFrame(Pointer sp, CodePointer ip, DeoptimizedFrame deoptFrame) {
            Log log = Log.log();
            logFrame(log, sp, ip, deoptFrame);
            log.newline();
            return true;
        }

        @SuppressWarnings("unused")
        protected void logFrame(Log log, Pointer sp, CodePointer ip, DeoptimizedFrame deoptFrame) {
            logFrameRaw(log, sp, ip);
            Log.log().string(" FrameSize ").signed(CodeInfoTable.lookupTotalFrameSize(ip));
        }

        protected static void logFrameRaw(Log log, Pointer sp, CodePointer ip) {
            log.string("RSP ").zhex(sp.rawValue());
            log.string(" RIP ").zhex(ip.rawValue());
        }
    }

    public static class Stage1StackFrameVisitor extends Stage0StackFrameVisitor {

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
        protected void logFrame(Log log, Pointer sp, CodePointer ip, DeoptimizedFrame deoptFrame) {
            if (deoptFrame != null) {
                logVirtualFrames(log, sp, ip, deoptFrame);
            } else {
                logFrameRaw(log, sp, ip);
                log.spaces(2);
                CodeInfoTable.logCodeInfoResult(log, ip);
            }
        }
    }

    /** Walk the stack printing each frame. */
    @NeverInline("debugger breakpoint")
    @Uninterruptible(reason = "Called from uninterruptible code.")
    public static void printBacktrace() {
        // Only used as a debugger breakpoint
    }

    public static final StackFrameVisitor AllocationFreeStackFrameVisitor = new AllocationFreeStackFrameVisitor();

    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Provide allocation-free Stacktrace printing")
    @Uninterruptible(reason = "Must be uninterruptible until it gets immune to safepoints", calleeMustBe = false)
    public static void printStacktrace(Pointer startSP, CodePointer startIP) {
        JavaStackWalker.walkCurrentThread(startSP, startIP, AllocationFreeStackFrameVisitor);
    }
}
