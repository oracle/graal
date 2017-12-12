/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.svm.core.jdk;

import java.util.ArrayList;

import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.word.Pointer;

import com.oracle.svm.core.code.CodeInfoQueryResult;
import com.oracle.svm.core.code.CodeInfoTable;
import com.oracle.svm.core.code.FrameInfoQueryResult;
import com.oracle.svm.core.deopt.DeoptimizedFrame;
import com.oracle.svm.core.stack.StackFrameVisitor;

public class StackTraceBuilder implements StackFrameVisitor {
    private final ArrayList<StackTraceElement> trace = new ArrayList<>();

    @Override
    public boolean visitFrame(Pointer sp, CodePointer ip, DeoptimizedFrame deoptimizedFrame) {
        if (deoptimizedFrame != null) {
            for (DeoptimizedFrame.VirtualFrame frame = deoptimizedFrame.getTopFrame(); frame != null; frame = frame.getCaller()) {
                trace.add(frame.getFrameInfo().getSourceReference());
            }
        } else {
            CodeInfoQueryResult codeInfo = CodeInfoTable.lookupCodeInfoQueryResult(ip);
            for (FrameInfoQueryResult frameInfo = codeInfo.getFrameInfo(); frameInfo != null; frameInfo = frameInfo.getCaller()) {
                trace.add(frameInfo.getSourceReference());
            }
        }
        return true;
    }

    public StackTraceElement[] getTrace() {
        return trace.toArray(new StackTraceElement[trace.size()]);
    }
}
