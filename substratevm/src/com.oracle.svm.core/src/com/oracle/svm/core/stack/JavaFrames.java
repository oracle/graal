/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.svm.core.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;

import com.oracle.svm.core.interpreter.InterpreterSupport;
import jdk.graal.compiler.word.Word;
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.code.CodeInfo;
import com.oracle.svm.core.code.CodeInfoAccess;
import com.oracle.svm.core.code.CodeInfoDecoder;
import com.oracle.svm.core.code.CodeInfoQueryResult;
import com.oracle.svm.core.code.CodeInfoTable;
import com.oracle.svm.core.code.UntetheredCodeInfo;
import com.oracle.svm.core.deopt.DeoptimizedFrame;
import com.oracle.svm.core.deopt.Deoptimizer;
import com.oracle.svm.core.heap.ReferenceMapIndex;

public class JavaFrames {
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static boolean isUnknownFrame(JavaFrame frame) {
        return frame.getIPCodeInfo().isNull() && Deoptimizer.checkEagerDeoptimized(frame) == null;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static boolean isEntryPoint(JavaFrame frame) {
        return CodeInfoQueryResult.isEntryPoint(frame.getEncodedFrameSize());
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static boolean isInterpreterLeaveStub(JavaFrame frame) {
        if (!InterpreterSupport.isEnabled()) {
            return false;
        }
        return InterpreterSupport.isInInterpreterLeaveStub(frame.getIP());
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static UnsignedWord getTotalFrameSize(JavaFrame frame) {
        long size = CodeInfoQueryResult.getTotalFrameSize(frame.getEncodedFrameSize());
        assert size > 0;
        return Word.unsigned(size);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static Pointer getCallerSP(JavaFrame frame) {
        assert frame.getSP().isNonNull();
        return frame.getSP().add(getTotalFrameSize(frame));
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    static void clearData(JavaFrame frame) {
        frame.setSP(Word.nullPointer());
        frame.setIP(Word.nullPointer());
        frame.setIPCodeInfo(Word.nullPointer());
        frame.setIsPendingLazyDeopt(false);

        frame.setEncodedFrameSize(CodeInfoDecoder.INVALID_SIZE_ENCODING);
        frame.setExceptionOffset(CodeInfoQueryResult.NO_EXCEPTION_OFFSET);
        frame.setReferenceMapIndex(ReferenceMapIndex.NO_REFERENCE_MAP);
    }

    @Uninterruptible(reason = "Prevent deoptimization and GC.", callerMustBe = true)
    public static void setData(JavaFrame frame, Pointer sp, CodePointer ip) {
        frame.setSP(sp);
        frame.setIP(ip);
        frame.setIsPendingLazyDeopt(false);

        DeoptimizedFrame deoptimizedFrame = Deoptimizer.checkEagerDeoptimized(frame);
        if (deoptimizedFrame != null) {
            frame.setIPCodeInfo(Word.nullPointer());
            frame.setEncodedFrameSize(deoptimizedFrame.getSourceEncodedFrameSize());
            frame.setExceptionOffset(CodeInfoQueryResult.NO_EXCEPTION_OFFSET);
            frame.setReferenceMapIndex(ReferenceMapIndex.NO_REFERENCE_MAP);
        } else {
            CodePointer returnAddress = ip;
            if (Deoptimizer.checkLazyDeoptimized(ip)) {
                /*
                 * For lazily deoptimized frames, the return address is stored in a reserved slot.
                 * See Deoptimizer.java for details.
                 */
                frame.setIsPendingLazyDeopt(true);
                returnAddress = sp.readWord(0);
                assert returnAddress.isNonNull();
                frame.setIP(returnAddress);
            }

            UntetheredCodeInfo untetheredCodeInfo = CodeInfoTable.lookupCodeInfo(returnAddress);
            frame.setIPCodeInfo(untetheredCodeInfo);

            if (untetheredCodeInfo.isNull()) {
                /* Encountered an unknown frame. */
                frame.setEncodedFrameSize(CodeInfoDecoder.INVALID_SIZE_ENCODING);
                frame.setExceptionOffset(CodeInfoQueryResult.NO_EXCEPTION_OFFSET);
                frame.setReferenceMapIndex(ReferenceMapIndex.NO_REFERENCE_MAP);
            } else {
                /* Encountered a normal Java frame. */
                Object tether = CodeInfoAccess.acquireTether(untetheredCodeInfo);
                try {
                    CodeInfo info = CodeInfoAccess.convert(untetheredCodeInfo, tether);
                    CodeInfoAccess.lookupCodeInfo(info, returnAddress, frame);
                } finally {
                    CodeInfoAccess.releaseTether(untetheredCodeInfo, tether);
                }
            }
        }
    }
}
