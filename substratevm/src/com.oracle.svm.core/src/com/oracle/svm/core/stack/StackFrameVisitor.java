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

import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.code.CodeInfo;
import com.oracle.svm.core.code.CodeInfoAccess;
import com.oracle.svm.core.deopt.DeoptimizedFrame;

/** Given access to a thread stack frame, perform some computation on it. */
public abstract class StackFrameVisitor extends ParameterizedStackFrameVisitor<Void> {

    /**
     * Called for each frame that is visited. Note that unless this method is annotated with
     * {@link Uninterruptible} or executing within a safepoint, the frame on the stack could be
     * deoptimized at any safepoint check. Nevertheless, the passed codeInfo remains valid for
     * accessing information about the code at the (possibly outdated) instruction pointer (this is
     * ensured by the caller).
     *
     * @param sp The stack pointer of the frame being visited.
     * @param ip The instruction pointer of the frame being visited.
     * @param codeInfo Information on the code at the IP, for use with {@link CodeInfoAccess}.
     * @param deoptimizedFrame The information about a deoptimized frame, or {@code null} if the
     *            frame is not deoptimized.
     * @return true if visiting should continue, false otherwise.
     */
    protected abstract boolean visitFrame(Pointer sp, CodePointer ip, CodeInfo codeInfo, DeoptimizedFrame deoptimizedFrame);

    @Override
    protected final boolean visitFrame(Pointer sp, CodePointer ip, CodeInfo codeInfo, DeoptimizedFrame deoptimizedFrame, Void data) {
        return visitFrame(sp, ip, codeInfo, deoptimizedFrame);
    }

    @Override
    protected final boolean unknownFrame(Pointer sp, CodePointer ip, DeoptimizedFrame deoptimizedFrame, Void data) {
        throw JavaStackWalker.reportUnknownFrameEncountered(sp, ip, deoptimizedFrame);
    }
}
