/*
 * Copyright (c) 2013, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.genscavenge;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.word.Pointer;

import com.oracle.svm.core.code.CodeInfo;
import com.oracle.svm.core.code.CodeInfoTable;
import com.oracle.svm.core.code.RuntimeCodeInfoAccess;
import com.oracle.svm.core.deopt.DeoptimizationSupport;
import com.oracle.svm.core.deopt.DeoptimizedFrame;
import com.oracle.svm.core.heap.ObjectReferenceVisitor;
import com.oracle.svm.core.stack.StackFrameVisitor;

/**
 * A StackFrameVisitor that applies an ObjectReferenceVisitor to all the Object references in a
 * frame.
 */
public class FramePointerMapWalker implements StackFrameVisitor {
    private final ObjectReferenceVisitor visitor;

    @Platforms(Platform.HOSTED_ONLY.class)
    protected FramePointerMapWalker(final ObjectReferenceVisitor objRefVisitor) {
        this.visitor = objRefVisitor;
    }

    @Override
    public boolean visitFrame(Pointer sp, CodePointer ip, CodeInfo codeInfo, DeoptimizedFrame deoptimizedFrame) {
        if (DeoptimizationSupport.enabled() && isRuntimeCompiledCode(codeInfo)) {
            /*
             * For runtime-compiled code that is currently on the stack, we need to treat all the
             * references to Java heap objects as strong references. It is important that we really
             * walk *all* those references here. Otherwise, RuntimeCodeCacheWalker might decide to
             * invalidate too much code, depending on the order in which the CodeInfo objects are
             * visited.
             */
            RuntimeCodeInfoAccess.walkStrongReferences(codeInfo, visitor);
            RuntimeCodeInfoAccess.walkWeakReferences(codeInfo, visitor);
        }
        return CodeInfoTable.visitObjectReferences(sp, ip, codeInfo, deoptimizedFrame, visitor);
    }

    private static boolean isRuntimeCompiledCode(CodeInfo codeInfo) {
        return codeInfo != CodeInfoTable.getImageCodeInfo();
    }
}
