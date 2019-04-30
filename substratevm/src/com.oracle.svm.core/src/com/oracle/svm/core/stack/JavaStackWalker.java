/*
 * Copyright (c) 2013, 2018, Oracle and/or its affiliates. All rights reserved.
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
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.FrameAccess;
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

    /**
     * Initialize a stack walk for the current thread.  The given {@code walk} parameter should normally be allocated
     * on the stack.
     * <p>
     * The stack walker is only valid while the stack being walked is stable and existent.
     *
     * @param walk the stack-allocated walk base pointer
     * @param startSP the starting SP
     * @param startIP the starting IP
     */
    @AlwaysInline("Stack walker setup is minimal")
    public static void initWalk(JavaStackWalk walk, Pointer startSP, CodePointer startIP) {
        walk.setSP(startSP);
        walk.setIP(startIP);
        walk.setAnchor(JavaFrameAnchors.getFrameAnchor());
    }

    /**
     * Initialize a stack walk for the given thread.  The given {@code walk} parameter should normally be allocated
     * on the stack.
     * <p>
     * The stack walker is only valid while the stack being walked is stable and existent.
     *
     * @param walk the stack-allocated walk base pointer
     * @param thread the thread to examine
     */
    public static void initWalk(JavaStackWalk walk, IsolateThread thread) {
        JavaFrameAnchor anchor = JavaFrameAnchors.getFrameAnchor(thread);
        Pointer sp = WordFactory.nullPointer();
        CodePointer ip = WordFactory.nullPointer();
        if (anchor.isNonNull()) {
            sp = anchor.getLastJavaSP();
            ip = FrameAccess.singleton().readReturnAddress(sp);
        }
        walk.setSP(sp);
        walk.setIP(ip);
        walk.setAnchor(anchor);
    }

    /**
     * Start the stack walk.  If this method returns {@code false} then calls to {@link #continueWalk(JavaStackWalk)} will
     * also return {@code false}.
     *
     * @param walk the stack walk
     * @return {@code true} if there is a first frame to examine, or {@code false} if there are no frames to iterate
     */
    public static boolean startWalk(JavaStackWalk walk) {
        return walk.getSP().isNonNull() && walk.getIP().isNonNull() && findNextFrame(walk);
    }

    /**
     * Continue a started stack walk.  This method should only be called after {@link #startWalk(JavaStackWalk)} was
     * called to start the walk.  Once this method returns {@code false}, it will always return {@code false}.
     *
     * @param walk the initiated stack walk pointer
     * @return {@code true} if there is another frame, or {@code false} if there are no more frames to iterate
     * @see #startWalk(JavaStackWalk)
     */
    @AlwaysInline("Stack walker continue is minimal")
    public static boolean continueWalk(JavaStackWalk walk) {
        if (walk.getSP().isNull() || walk.getIP().isNull()) return false;
        /* Bump sp *up* over my frame. */
        walk.setSP(walk.getSP().add(WordFactory.unsigned(walk.getTotalFrameSize())));
        /* Read the return address to my caller. */
        walk.setIP(FrameAccess.singleton().readReturnAddress(walk.getSP()));

        return findNextFrame(walk);
    }

    private static boolean findNextFrame(JavaStackWalk walk) {
        Pointer sp = walk.getSP();
        CodePointer ip = walk.getIP();
        JavaFrameAnchor anchor = walk.getAnchor();
        for (;;) {
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
                walk.setSP(sp);
                walk.setIP(ip);
                walk.setAnchor(anchor);
                walk.setTotalFrameSize(totalFrameSize);
                return true;
            } else if (anchor.isNonNull()) {
                /*
                 * At the end of a block of Java frames, but we have more Java frames after a
                 * block of C frames.
                 */
                assert anchor.getLastJavaSP().aboveThan(sp);
                sp = anchor.getLastJavaSP();
                ip = FrameAccess.singleton().readReturnAddress(sp);
                anchor = anchor.getPreviousAnchor();
            } else {
                /* Really at the end of the stack, we are done with walking. */
                walk.setSP(WordFactory.nullPointer());
                walk.setIP(WordFactory.nullPointer());
                return false;
            }
        }
    }

    @AlwaysInline("avoid virtual call to visitor")
    public static boolean walkCurrentThread(Pointer startSP, CodePointer startIP, StackFrameVisitor visitor) {
        JavaStackWalk walk = StackValue.get(JavaStackWalk.class);
        initWalk(walk, startSP, startIP);
        return doWalk(walk, visitor);
    }

    @AlwaysInline("avoid virtual call to visitor")
    public static boolean walkThread(IsolateThread thread, StackFrameVisitor visitor) {
        JavaStackWalk walk = StackValue.get(JavaStackWalk.class);
        initWalk(walk, thread);
        return doWalk(walk, visitor);
    }

    @AlwaysInline("avoid virtual call to visitor")
    private static boolean doWalk(JavaStackWalk walk, StackFrameVisitor visitor) {
        if (!visitor.prologue()) {
            return false;
        }
        if (startWalk(walk)) do {
            if (!visitor.visitFrame(walk.getSP(), walk.getIP(), Deoptimizer.checkDeoptimized(walk.getSP()))) {
                return false;
            }
        } while (continueWalk(walk));
        return visitor.epilogue();
    }
}