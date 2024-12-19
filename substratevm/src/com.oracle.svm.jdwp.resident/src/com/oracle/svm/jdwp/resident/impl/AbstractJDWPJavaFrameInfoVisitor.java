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
package com.oracle.svm.jdwp.resident.impl;

import com.oracle.svm.core.code.FrameSourceInfo;
import com.oracle.svm.core.jdk.StackTraceUtils;
import com.oracle.svm.core.stack.JavaStackFrameVisitor;
import org.graalvm.word.Pointer;

abstract class AbstractJDWPJavaFrameInfoVisitor extends JavaStackFrameVisitor {
    private final boolean filterExceptions;
    int currentFrameDepth;

    AbstractJDWPJavaFrameInfoVisitor(boolean filterExceptions) {
        this.filterExceptions = filterExceptions;
        this.currentFrameDepth = 0;
    }

    protected boolean ignoreFrame(FrameSourceInfo frameInfo) {
        if (!StackTraceUtils.shouldShowFrame(frameInfo, false, true, false)) {
            /* Always ignore the frame. It is an internal frame of the VM. */
            return true;

        } else if (filterExceptions && currentFrameDepth == 0 && Throwable.class.isAssignableFrom(frameInfo.getSourceClass())) {
            /*
             * We are still in the constructor invocation chain at the beginning of the stack trace,
             * which is also filtered by the Java HotSpot VM.
             */
            return true;
        }

        String sourceClassName = null;
        Class<?> sourceClass = frameInfo.getSourceClass();
        if (sourceClass != null) {
            sourceClassName = sourceClass.getName();
        }
        if (currentFrameDepth == 0 && (sourceClassName != null && sourceClassName.startsWith("com.oracle.svm.jdwp."))) {
            /*
             * Ignore frames used by the debugger to spawn events, but only if they would be
             * reported as top-most frames.
             */
            return true;
        }

        return false;
    }

    @Override
    public boolean visitFrame(FrameSourceInfo frameInfo, Pointer sp) {
        if (ignoreFrame(frameInfo)) {
            return true;
        }
        return processFrame(frameInfo, sp);
    }

    protected boolean processFrame(@SuppressWarnings("unused") FrameSourceInfo frameInfo, @SuppressWarnings("unused") Pointer sp) {
        ++currentFrameDepth;
        return true;
    }
}
