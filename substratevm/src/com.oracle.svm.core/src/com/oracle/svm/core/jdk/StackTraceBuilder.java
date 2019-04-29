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
package com.oracle.svm.core.jdk;

import java.util.ArrayList;

import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.util.DirectAnnotationAccess;
import org.graalvm.word.Pointer;

import com.oracle.svm.core.JavaMainWrapper;
import com.oracle.svm.core.code.CodeInfoQueryResult;
import com.oracle.svm.core.code.CodeInfoTable;
import com.oracle.svm.core.code.FrameInfoQueryResult;
import com.oracle.svm.core.code.IsolateEnterStub;
import com.oracle.svm.core.code.IsolateLeaveStub;
import com.oracle.svm.core.deopt.DeoptimizedFrame;
import com.oracle.svm.core.snippets.ImplicitExceptions;
import com.oracle.svm.core.stack.StackFrameVisitor;

public class StackTraceBuilder implements StackFrameVisitor {
    private final ArrayList<StackTraceElement> trace = new ArrayList<>();

    private final boolean filterExceptions;

    public StackTraceBuilder(boolean filterExceptions) {
        this.filterExceptions = filterExceptions;
    }

    @Override
    public boolean visitFrame(Pointer sp, CodePointer ip, DeoptimizedFrame deoptimizedFrame) {
        if (deoptimizedFrame != null) {
            for (DeoptimizedFrame.VirtualFrame frame = deoptimizedFrame.getTopFrame(); frame != null; frame = frame.getCaller()) {
                if (!addToTrace(frame.getFrameInfo())) {
                    return false;
                }
            }
        } else {
            CodeInfoQueryResult codeInfo = CodeInfoTable.lookupCodeInfoQueryResult(ip);
            for (FrameInfoQueryResult frameInfo = codeInfo.getFrameInfo(); frameInfo != null; frameInfo = frameInfo.getCaller()) {
                if (!addToTrace(frameInfo)) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean addToTrace(FrameInfoQueryResult frameInfo) {
        Class<?> clazz = frameInfo.getSourceClass();
        if (clazz != null) {
            if (clazz == JavaMainWrapper.class && trace.size() > 0) {
                /*
                 * This frame and everything after it is our code to invoke the application's main()
                 * method. Ignore this frame and stop the stack walking. Unless nothing is on the
                 * stack trace yet, because e.g. an exception is thrown in JavaMainWrapper itself.
                 */
                return false;

            } else if (clazz == IsolateEnterStub.class || clazz == IsolateLeaveStub.class) {
                /*
                 * Always ignore the frame. It is a synthetic frame for entering from C code /
                 * exiting to C code.
                 */
                return true;
            } else if (DirectAnnotationAccess.isAnnotationPresent(clazz, IgnoreForGetCallerClass.class)) {
                /*
                 * Always ignore the frame. It is a synthetic frame for reflective method invocation
                 * or lambda invocation.
                 */
                return true;

            } else if (filterExceptions) {
                if (trace.size() == 0 && Throwable.class.isAssignableFrom(clazz)) {
                    /*
                     * We are still in the constructor invocation chain at the beginning of the
                     * stack trace, which is also filtered by the Java HotSpot VM.
                     */
                    return true;

                } else if (clazz == ImplicitExceptions.class) {
                    /*
                     * ImplicitExceptions is the entry point for creating all exceptions thrown
                     * implicitly by bytecodes. This class is SVM-specific, we do not need it in an
                     * exception stack trace.
                     */
                    return true;
                }
            }
        }

        StackTraceElement sourceReference = frameInfo.getSourceReference();
        trace.add(sourceReference);
        return true;
    }

    public StackTraceElement[] getTrace() {
        return trace.toArray(new StackTraceElement[trace.size()]);
    }
}
