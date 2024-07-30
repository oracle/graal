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
package com.oracle.svm.core.stack;

import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.word.Pointer;

import com.oracle.svm.core.code.CodeInfo;
import com.oracle.svm.core.code.CodeInfoQueryResult;
import com.oracle.svm.core.code.CodeInfoTable;
import com.oracle.svm.core.code.FrameInfoQueryResult;
import com.oracle.svm.core.code.FrameSourceInfo;
import com.oracle.svm.core.deopt.DeoptimizedFrame;
import com.oracle.svm.core.interpreter.InterpreterSupport;
import com.oracle.svm.core.util.VMError;

public abstract class JavaStackFrameVisitor extends StackFrameVisitor {

    @Override
    public boolean visitRegularFrame(Pointer sp, CodePointer ip, CodeInfo codeInfo) {
        CodeInfoQueryResult queryResult = CodeInfoTable.lookupCodeInfoQueryResult(codeInfo, ip);
        for (FrameInfoQueryResult frameInfo = queryResult.getFrameInfo(); frameInfo != null; frameInfo = frameInfo.getCaller()) {
            if (!dispatchPossiblyInterpretedFrame(frameInfo, sp)) {
                return false;
            }
        }
        return true;
    }

    @Override
    protected boolean visitDeoptimizedFrame(Pointer originalSP, CodePointer deoptStubIP, DeoptimizedFrame deoptimizedFrame) {
        for (DeoptimizedFrame.VirtualFrame frame = deoptimizedFrame.getTopFrame(); frame != null; frame = frame.getCaller()) {
            if (!dispatchPossiblyInterpretedFrame(frame.getFrameInfo(), originalSP)) {
                return false;
            }
        }
        return true;
    }

    private static FrameSourceInfo interpreterToInterpretedMethodFrame(FrameInfoQueryResult frameInfo, Pointer sp) {
        InterpreterSupport interpreter = InterpreterSupport.singleton();
        VMError.guarantee(interpreter.isInterpreterRoot(frameInfo.getSourceClass()));
        return interpreter.getInterpretedMethodFrameInfo(frameInfo, sp);
    }

    protected final boolean dispatchPossiblyInterpretedFrame(FrameInfoQueryResult frameInfo, Pointer sp) {
        if (InterpreterSupport.isEnabled() && InterpreterSupport.singleton().isInterpreterRoot(frameInfo.getSourceClass())) {
            return visitFrame(interpreterToInterpretedMethodFrame(frameInfo, sp), sp);
        } else {
            return visitFrame(frameInfo, sp);
        }
    }

    public boolean visitFrame(FrameSourceInfo frameInfo, @SuppressWarnings("unused") Pointer sp) {
        return visitFrame(frameInfo);
    }

    public boolean visitFrame(@SuppressWarnings("unused") FrameSourceInfo frameInfo) {
        throw VMError.shouldNotReachHere("override this method or visitFrame::(FrameSourceInfo,Pointer)");
    }
}
