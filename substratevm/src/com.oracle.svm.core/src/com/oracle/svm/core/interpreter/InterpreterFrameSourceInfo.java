/*
 * Copyright (c) 2024, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.interpreter;

import static com.oracle.svm.shared.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;

import java.util.Objects;

import com.oracle.svm.core.code.FrameSourceInfo;
import com.oracle.svm.shared.Uninterruptible;

import jdk.vm.ci.meta.LineNumberTable;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public final class InterpreterFrameSourceInfo extends FrameSourceInfo {
    private final ResolvedJavaMethod interpretedMethod;
    private final Object interpreterFrame;
    /*
     * Ristretto deoptimization uses this synthetic caller chain to report the Java source-level
     * callers that were inlined into the runtime-compiled frame.
     */
    private final InterpreterFrameSourceInfo callerInfo;

    public InterpreterFrameSourceInfo(Class<?> sourceClass, String sourceMethodName, int sourceLineNumber, int bci, ResolvedJavaMethod interpretedMethod, Object interpreterFrame) {
        this(sourceClass, sourceMethodName, sourceLineNumber, bci, interpretedMethod, interpreterFrame, null);
    }

    public InterpreterFrameSourceInfo(Class<?> sourceClass, String sourceMethodName, int sourceLineNumber, int bci, ResolvedJavaMethod interpretedMethod, Object interpreterFrame,
                    InterpreterFrameSourceInfo callerInfo) {
        super(sourceClass, sourceMethodName, sourceLineNumber, bci);
        this.interpretedMethod = interpretedMethod;
        this.interpreterFrame = interpreterFrame;
        this.callerInfo = callerInfo;

        Objects.requireNonNull(sourceClass);
        Objects.requireNonNull(sourceMethodName);
    }

    public static InterpreterFrameSourceInfo forInterpretedMethod(ResolvedJavaMethod interpretedMethod, int bci, Object interpreterFrame, InterpreterFrameSourceInfo callerInfo) {
        String sourceMethodName = interpretedMethod.getName();
        return new InterpreterFrameSourceInfo(sourceClass(interpretedMethod), sourceMethodName, sourceLineNumber(interpretedMethod, bci), bci, interpretedMethod, interpreterFrame, callerInfo);
    }

    public static InterpreterFrameSourceInfo forInterpretedMethod(ResolvedJavaMethod interpretedMethod, int bci) {
        String sourceMethodName = interpretedMethod.getName();
        return new InterpreterFrameSourceInfo(sourceClass(interpretedMethod), sourceMethodName, sourceLineNumber(interpretedMethod, bci), bci, interpretedMethod, null);
    }

    public static InterpreterFrameSourceInfo forNativeMethod(ResolvedJavaMethod interpretedMethod, Object interpreterFrame) {
        String sourceMethodName = interpretedMethod.getName();
        return new InterpreterFrameSourceInfo(sourceClass(interpretedMethod), sourceMethodName, LINENUMBER_NATIVE, -1, interpretedMethod, interpreterFrame);
    }

    private static Class<?> sourceClass(ResolvedJavaMethod interpretedMethod) {
        return InterpreterSupport.singleton().toClass(interpretedMethod.getDeclaringClass());
    }

    private static int sourceLineNumber(ResolvedJavaMethod interpretedMethod, int bci) {
        LineNumberTable lineNumberTable = interpretedMethod.getLineNumberTable();
        if (lineNumberTable == null || bci < 0) {
            return -1;
        }
        return lineNumberTable.getLineNumber(bci);
    }

    public Object getInterpreterFrame() {
        return interpreterFrame;
    }

    public ResolvedJavaMethod getInterpretedMethod() {
        return interpretedMethod;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public boolean hasDeoptimizationCallerInfo() {
        return callerInfo != null;
    }

    @Override
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public InterpreterFrameSourceInfo getCaller() {
        return callerInfo;
    }

    @Override
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    protected void fillSourceFieldsIfMissing() {
        /* nothing to do */
    }
}
