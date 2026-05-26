/*
 * Copyright (c) 2017, 2026, Oracle and/or its affiliates. All rights reserved.
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
import org.graalvm.word.impl.Word;

import com.oracle.svm.core.code.CodeInfo;
import com.oracle.svm.core.code.CodeInfoQueryResult;
import com.oracle.svm.core.code.CodeInfoTable;
import com.oracle.svm.core.code.FrameInfoQueryResult;
import com.oracle.svm.core.code.FrameSourceInfo;
import com.oracle.svm.core.deopt.DeoptimizedFrame;
import com.oracle.svm.core.deopt.VirtualFrame;
import com.oracle.svm.core.interpreter.InterpreterFrameSourceInfo;
import com.oracle.svm.core.interpreter.InterpreterSupport;
import com.oracle.svm.core.interpreter.InterpreterSupport.InterpretedFrameData;
import com.oracle.svm.shared.util.VMError;

/**
 * Used to visit source-level Java stack frames, including inlined and deoptimized frames. Native
 * frames will be skipped. The logic in this class must have the same behavior as the core
 * stack-walking logic in {@code AbstractStackFrameSpliterator}.
 */
public abstract class JavaStackFrameVisitor extends StackFrameVisitor {

    @Override
    public boolean visitRegularFrame(Pointer sp, CodePointer ip, CodeInfo codeInfo) {
        CodeInfoQueryResult queryResult = CodeInfoTable.lookupCodeInfoQueryResult(codeInfo, ip);
        for (FrameInfoQueryResult vFrame = queryResult.getFrameInfo(); vFrame != null; vFrame = vFrame.getCaller()) {
            if (!dispatch(vFrame, sp)) {
                return false;
            }
        }
        return true;
    }

    @Override
    protected boolean visitDeoptimizedFrame(Pointer originalSP, CodePointer deoptStubIP, DeoptimizedFrame deoptimizedFrame) {
        /*
         * Deoptimization copies the source-frame values into the DeoptimizedFrame and makes
         * the original frame content invalid. Pass null so source-frame translation cannot
         * read stack slots through the original SP.
         */
        Pointer deoptimizedSP = Word.nullPointer();
        for (VirtualFrame frame = deoptimizedFrame.getTopFrame(); frame != null; frame = frame.getCaller()) {
            if (!dispatch(frame.getFrameInfo(), deoptimizedSP)) {
                return false;
            }
        }
        return true;
    }

    protected boolean dispatch(FrameInfoQueryResult vFrame, Pointer sp) {
        if (vFrame == null) {
            return true;
        }
        if (InterpreterSupport.isEnabled()) {
            /* Translate the VM-level vframe to source-level vframes and visit all of them. */
            FrameSourceInfo sourceVFrame = getSourceLevelVFrames(vFrame, sp, null);
            if (vFrame != sourceVFrame) {
                if (sourceVFrame.getCaller() != null) {
                    VMError.guarantee(sourceVFrame instanceof InterpreterFrameSourceInfo interpreterFrameSourceInfo && interpreterFrameSourceInfo.hasDeoptimizationCallerInfo(),
                                    "Only Ristretto deoptimization caller metadata can expand one VM frame to multiple source frames");
                }
                while (sourceVFrame != null) {
                    if (!visitFrame(sourceVFrame, sp)) {
                        return false;
                    }
                    sourceVFrame = sourceVFrame.getCaller();
                }
                return true;
            }
        }

        /* Visit the single VM-level vframe. */
        return visitFrame(vFrame, sp);
    }

    /**
     * Translates a single VM-level virtual frame to 0..n source-level virtual frames. Note that
     * there is no translation for most AOT and run-time compiled code. However, for interpreted or Ristretto-compiled
     * code, a source-level stack trace is fundamentally different from a VM-level stack trace: the
     * former contains the frames of the interpreted methods while the latter contains the frames of
     * the interpreter infrastructure.
     *
     * The {@code vFrame} parameter is decoded frame metadata, not the physical stack frame itself.
     * When a caller is walking a live physical frame, {@code sp} is the stack pointer of that
     * physical frame. When a caller is walking virtual frames from a {@link DeoptimizedFrame},
     * {@code sp == null} because the deoptimized logical frame has no physical stack slot of its
     * own.
     * When walking a {@link com.oracle.svm.core.heap.StoredContinuation} object, the {@code interpretedFrameData} is the replacement for {@code sp}.
     *
     * The frame categories that reach this method use the parameters as follows:
     * <ul>
     * <li>Regular AOT-compiled frames: live callers pass {@code sp == current physical-frame SP}
     * and {@code interpretedFrameData == null}; deoptimized logical-frame callers pass
     * {@code sp == null} and {@code interpretedFrameData == null}; the continuation walker can pass
     * {@code sp == null} and an empty {@code interpretedFrameData} scratch object. Since the frame
     * is not an interpreter root and has no Ristretto synthetic source mapping, this method returns
     * {@code vFrame} unchanged.</li>
     * <li>Truffle runtime-compiled frames: callers use the same parameter combinations as regular
     * AOT-compiled frames. Since the decoded {@code vFrame} already contains the source class and
     * method for the Truffle frame, this method returns {@code vFrame} unchanged.</li>
     * <li>Crema regular interpreter frames: these are recognized by {@code isInterpreterRoot(vFrame)}.
     * Live frames pass {@code sp == physical Interpreter.Root frame SP} and
     * {@code interpretedFrameData == null}, so the interpreted method, BCI, and interpreter frame
     * are read from physical stack state. Stored-continuation frames pass {@code sp == null} and
     * {@code interpretedFrameData != null}, with data captured from that same {@code vFrame}; the
     * captured data is used instead of re-reading through a stale SP.</li>
     * <li>Ristretto compiled frames that are still on the stack: callers pass
     * {@code sp == current physical-frame SP} and {@code interpretedFrameData == null}. Ristretto
     * runtime-compiled frame metadata is identified by {@code vFrame.getSourceClass() == null} and
     * {@code vFrame.getDeoptMethod() instanceof RistrettoMethod}; this method casts
     * {@code vFrame.getDeoptMethod()} to {@code RistrettoMethod} and builds synthetic source
     * information from its {@code getInterpreterMethod()} plus {@code vFrame.getBci()}.</li>
     * <li>Ristretto deopted frames: walkers visit the {@link DeoptimizedFrame}'s virtual-frame list
     * and pass {@code sp == null} and {@code interpretedFrameData == null}; the deopt-stub SP is not
     * reused. These frames use the same Ristretto metadata path as compiled Ristretto frames
     * ({@code sourceClass == null}, {@code deoptMethod instanceof RistrettoMethod}) to build
     * synthetic source information. Once the deopt has resumed into a live
     * {@code Interpreter.Root} frame, the frame follows the Crema interpreter-frame case above.</li>
     * </ul>
     *
     * At most one of {@code sp} and {@code interpretedFrameData} may provide frame state. Passing
     * neither is valid for frame categories where {@code vFrame} metadata is sufficient or where
     * Ristretto synthetic source information is derived from {@code vFrame.getDeoptMethod()}.
     */
    public static FrameSourceInfo getSourceLevelVFrames(FrameInfoQueryResult vFrame, Pointer sp, InterpretedFrameData interpretedFrameData) {
        assert InterpreterSupport.isEnabled();
        assert interpretedFrameData == null || sp.isNull() : "SP and interpretedFrameData must not both be set";

        if (vFrame == null) {
            /* Interpreter leave stubs do not have any FrameInfo at the moment. */
            return null;
        }

        InterpreterSupport support = InterpreterSupport.singleton();
        if (support.isInterpreterRoot(vFrame)) {
            if (interpretedFrameData != null) {
                return support.getInterpretedMethodFrameInfo(vFrame, interpretedFrameData);
            }
            VMError.guarantee(sp.isNonNull(), "Cannot translate interpreter root without a stack pointer");
            return support.getInterpretedMethodFrameInfo(vFrame, sp);
        }

        FrameSourceInfo syntheticFrameInfo = support.getSyntheticMethodFrameInfo(vFrame);
        if (syntheticFrameInfo != null) {
            return syntheticFrameInfo;
        }
        return vFrame;
    }

    public abstract boolean visitFrame(FrameSourceInfo frameInfo, Pointer sp);
}
