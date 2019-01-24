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

import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.word.Pointer;

import com.oracle.svm.core.annotate.NeverInline;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;
import com.oracle.svm.core.code.CodeInfoQueryResult;
import com.oracle.svm.core.code.CodeInfoTable;
import com.oracle.svm.core.code.FrameInfoQueryResult;
import com.oracle.svm.core.deopt.DeoptimizedFrame;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.stack.JavaStackWalker;
import com.oracle.svm.core.stack.StackFrameVisitor;
import com.oracle.svm.core.util.VMError;

@TargetClass(classNameProvider = Package_jdk_internal_reflect.class, className = "Reflection")
public final class Target_jdk_internal_reflect_Reflection {

    @Substitute
    @NeverInline("Starting a stack walk in the caller frame")
    public static Class<?> getCallerClass() {
        GetCallerClassVisitor visitor = new GetCallerClassVisitor();
        JavaStackWalker.walkCurrentThread(KnownIntrinsics.readCallerStackPointer(), KnownIntrinsics.readReturnAddress(), visitor);
        return visitor.result;
    }

    @Substitute
    public static int getClassAccessFlags(Class<?> cls) {
        return cls.getModifiers();
    }

    @Substitute //
    @TargetElement(onlyWith = JDK9OrLater.class) //
    @SuppressWarnings({"unused"})
    public static /* native */ boolean areNestMates(Class<?> currentClass, Class<?> memberClass) {
        throw VMError.unsupportedFeature("JDK9OrLater: Target_jdk_internal_reflect_Reflection.areNestMates");
    }
}

class GetCallerClassVisitor implements StackFrameVisitor {
    int depth;
    Class<?> result;

    @Override
    public boolean visitFrame(Pointer sp, CodePointer ip, DeoptimizedFrame deoptimizedFrame) {
        if (deoptimizedFrame != null) {
            for (DeoptimizedFrame.VirtualFrame frame = deoptimizedFrame.getTopFrame(); frame != null; frame = frame.getCaller()) {
                if (!visitJavaFrame(frame.getFrameInfo())) {
                    return false;
                }
            }
        } else {
            CodeInfoQueryResult codeInfo = CodeInfoTable.lookupCodeInfoQueryResult(ip);
            for (FrameInfoQueryResult frameInfo = codeInfo.getFrameInfo(); frameInfo != null; frameInfo = frameInfo.getCaller()) {
                if (!visitJavaFrame(frameInfo)) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean visitJavaFrame(FrameInfoQueryResult frameInfo) {
        depth++;

        if (depth == 1) {
            /* The method that contains the invoke of getCallerClass(). */
            return true;

        } else if (frameInfo.getSourceClass() != null && frameInfo.getSourceClass().getAnnotation(IgnoreForGetCallerClass.class) != null) {
            /*
             * Ignore a frame from a class manually annotated as an invocation frame, e.g., for
             * reflective method invocation or a lambda.
             */
            return true;
        } else if ((frameInfo.getSourceClass() == java.lang.reflect.Method.class && "invoke".equals(frameInfo.getSourceMethodName())) ||
                        (frameInfo.getSourceClass() == java.lang.reflect.Constructor.class && "newInstance".equals(frameInfo.getSourceMethodName())) ||
                        (frameInfo.getSourceClass() == java.lang.Class.class && "newInstance".equals(frameInfo.getSourceMethodName()))) {
            /* Ignore a reflective method / constructor invocation frame. */
            return true;
        }

        result = frameInfo.getSourceClass();
        return false;
    }
}
