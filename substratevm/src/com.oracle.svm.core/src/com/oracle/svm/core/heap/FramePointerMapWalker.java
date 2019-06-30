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
package com.oracle.svm.core.heap;

import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.word.Pointer;

import com.oracle.svm.core.code.CodeInfo;
import com.oracle.svm.core.code.CodeInfoTable;
import com.oracle.svm.core.deopt.DeoptimizedFrame;
import com.oracle.svm.core.stack.StackFrameVisitor;

/**
 * A StackFrameVisitor that applies an ObjectReferenceVisitor to all the Object references in a
 * frame.
 */
public class FramePointerMapWalker implements StackFrameVisitor {

    public static FramePointerMapWalker factory(final ObjectReferenceVisitor objRefVisitor) {
        return new FramePointerMapWalker(objRefVisitor);
    }

    @Override
    public boolean visitFrame(Pointer sp, CodePointer ip, CodeInfo codeInfo, DeoptimizedFrame deoptimizedFrame) {
        return CodeInfoTable.visitObjectReferences(sp, ip, codeInfo, deoptimizedFrame, visitor);
    }

    protected FramePointerMapWalker(final ObjectReferenceVisitor objRefVisitor) {
        this.visitor = objRefVisitor;
    }

    private final ObjectReferenceVisitor visitor;
}
