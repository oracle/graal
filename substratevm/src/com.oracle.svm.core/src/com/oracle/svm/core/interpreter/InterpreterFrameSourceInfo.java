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
package com.oracle.svm.core.interpreter;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.code.FrameSourceInfo;
import jdk.vm.ci.meta.ResolvedJavaMethod;

import java.util.Objects;

public final class InterpreterFrameSourceInfo extends FrameSourceInfo {
    private final ResolvedJavaMethod interpretedMethod;
    private final Object interpreterFrame;

    public InterpreterFrameSourceInfo(Class<?> sourceClass, String sourceMethodName, int sourceLineNumber, int bci, ResolvedJavaMethod interpretedMethod, Object interpreterFrame) {
        super(sourceClass, sourceMethodName, sourceLineNumber, bci);
        this.interpretedMethod = interpretedMethod;
        this.interpreterFrame = interpreterFrame;

        Objects.requireNonNull(sourceClass);
        Objects.requireNonNull(sourceMethodName);
    }

    public Object getInterpreterFrame() {
        return interpreterFrame;
    }

    public ResolvedJavaMethod getInterpretedMethod() {
        return interpretedMethod;
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    protected void fillSourceFieldsIfMissing() {
        /* nothing to do */
    }
}
