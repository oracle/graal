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

import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.amd64.FrameAccess;
import com.oracle.svm.core.annotate.AlwaysInline;
import com.oracle.svm.core.code.CodeInfoTable;
import com.oracle.svm.core.deopt.DeoptimizedFrame;
import com.oracle.svm.core.deopt.Deoptimizer;

/**
 * Applies a {@link StackFrameVisitor} to each of the Java frames in a thread stack. It skips native
 * frames, i.e., it only visits frames where {@link CodeInfoTable#lookupTotalFrameSize Java frame
 * information} is available.
 * <p>
 * The stack walking code is allocation free (so that it can be used during garbage collection) and
 * stateless (so that multiple threads can walk their stacks concurrently).
 */
public final class JavaStackWalker {

    private JavaStackWalker() {
    }

    @AlwaysInline("avoid virtual call to visitor")
    public static boolean walkCurrentThread(Pointer startSP, CodePointer startIP, StackFrameVisitor visitor) {
        JavaFrameAnchor anchor = JavaFrameAnchors.getFrameAnchor();
        return doWalk(anchor, startSP, startIP, visitor);
    }

    @AlwaysInline("avoid virtual call to visitor")
    public static boolean walkThread(IsolateThread thread, StackFrameVisitor visitor) {
        JavaFrameAnchor anchor = JavaFrameAnchors.getFrameAnchor(thread);
        Pointer sp = WordFactory.nullPointer();
        CodePointer ip = WordFactory.nullPointer();
        if (anchor.isNonNull()) {
            sp = anchor.getLastJavaSP();
            ip = FrameAccess.readReturnAddress(sp);
        }
        // always call doWalk() to invoke visitor's methods
        return doWalk(anchor, sp, ip, visitor);
    }

    @AlwaysInline("avoid virtual call to visitor")
    private static boolean doWalk(JavaFrameAnchor lastAnchor, Pointer startSP, CodePointer startIP, StackFrameVisitor visitor) {
        if (!visitor.prologue()) {
            return false;
        }

        if (startSP.isNonNull() && startIP.isNonNull()) {
            JavaFrameAnchor anchor = lastAnchor;
            Pointer sp = startSP;
            CodePointer ip = startIP;

            while (true) {
                while (anchor.isNonNull() && anchor.getLastJavaSP().belowOrEqual(sp)) {
                    /* Skip anchors that are in parts of the stack we are not traversing. */
                    anchor = anchor.getPreviousAnchor();
                }

                long totalFrameSize;
                DeoptimizedFrame deoptFrame = Deoptimizer.checkDeoptimized(sp);
                if (deoptFrame != null) {
                    totalFrameSize = deoptFrame.getSourceTotalFrameSize();
                } else {
                    totalFrameSize = CodeInfoTable.lookupTotalFrameSize(ip);
                }

                if (totalFrameSize != -1) {
                    /* This is a Java frame, visit it. */
                    if (!visitor.visitFrame(sp, ip, deoptFrame)) {
                        return false;
                    }

                    /* Bump sp *up* over my frame. */
                    sp = sp.add(WordFactory.unsigned(totalFrameSize));
                    /* Read the return address to my caller. */
                    ip = FrameAccess.readReturnAddress(sp);

                } else if (anchor.isNonNull()) {
                    /*
                     * At the end of a block of Java frames, but we have more Java frames after a
                     * block of C frames.
                     */
                    assert anchor.getLastJavaSP().aboveThan(sp);
                    sp = anchor.getLastJavaSP();
                    ip = FrameAccess.readReturnAddress(sp);
                    anchor = anchor.getPreviousAnchor();

                } else {
                    /* Really at the end of the stack, we are done with walking. */
                    break;
                }
            }
        } else {
            /*
             * It is fine for a thread to have no Java frames, for example in the case of a native
             * thread that was attached via JNI, but is currently not executing any Java code.
             */
        }

        return visitor.epilogue();
    }
}
