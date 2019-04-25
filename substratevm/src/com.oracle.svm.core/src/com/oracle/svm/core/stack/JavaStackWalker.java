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
package com.oracle.svm.core.stack;

import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.FrameAccess;
import com.oracle.svm.core.annotate.AlwaysInline;
import com.oracle.svm.core.code.CodeInfoQueryResult;
import com.oracle.svm.core.code.CodeInfoTable;
import com.oracle.svm.core.deopt.DeoptimizedFrame;
import com.oracle.svm.core.deopt.Deoptimizer;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.thread.VMOperation;
import com.oracle.svm.core.util.VMError;

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
     * Initialize a stack walk for the current thread. The given {@code walk} parameter should
     * normally be allocated on the stack.
     * <p>
     * The stack walker is only valid while the stack being walked is stable and existent.
     *
     * @param walk the stack-allocated walk base pointer
     * @param startSP the starting SP
     * @param startIP the starting IP
     */
    public static boolean initWalk(JavaStackWalk walk, Pointer startSP, CodePointer startIP) {
        walk.setSP(startSP);
        walk.setIP(startIP);
        walk.setStartSP(startSP);
        walk.setStartIP(startIP);
        walk.setAnchor(JavaFrameAnchors.getFrameAnchor());
        return true;
    }

    /**
     * Initialize a stack walk for the given thread. The given {@code walk} parameter should
     * normally be allocated on the stack.
     *
     * @param walk the stack-allocated walk base pointer
     * @param thread the thread to examine
     */
    public static boolean initWalk(JavaStackWalk walk, IsolateThread thread) {
        VMOperation.guaranteeInProgress("Walking the stack of another thread is only safe when that thread is stopped at a safepoint");

        JavaFrameAnchor anchor = JavaFrameAnchors.getFrameAnchor(thread);
        boolean result = anchor.isNonNull();
        Pointer sp = WordFactory.nullPointer();
        CodePointer ip = WordFactory.nullPointer();
        if (result) {
            sp = anchor.getLastJavaSP();
            ip = anchor.getLastJavaIP();
        }

        walk.setSP(sp);
        walk.setIP(ip);
        walk.setStartSP(sp);
        walk.setStartIP(ip);
        walk.setAnchor(anchor);

        return result;
    }

    /**
     * Continue a started stack walk. This method must only be called after {@link #initWalk} was
     * called to start the walk. Once this method returns {@code false}, it will always return
     * {@code false}.
     *
     * @param walk the initiated stack walk pointer
     * @return {@code true} if there is another frame, or {@code false} if there are no more frames
     *         to iterate
     */
    public static boolean continueWalk(JavaStackWalk walk) {
        if (walk.getSP().isNull() || walk.getIP().isNull()) {
            return false;
        }

        Pointer sp = walk.getSP();
        CodePointer ip = walk.getIP();

        long totalFrameSize;
        DeoptimizedFrame deoptFrame = Deoptimizer.checkDeoptimized(sp);
        if (deoptFrame != null) {
            totalFrameSize = deoptFrame.getSourceTotalFrameSize();
        } else {
            totalFrameSize = CodeInfoTable.lookupTotalFrameSize(ip);
        }

        if (totalFrameSize == -1) {
            Log.log().string("Stack walk must walk only frames of known code:")
                            .string("  startSP=").hex(walk.getStartSP()).string("  startIP=").hex(walk.getStartIP())
                            .string("  sp=").hex(sp).string("  ip=").hex(ip)
                            .string("  deoptFrame=").object(deoptFrame)
                            .newline();
            throw VMError.shouldNotReachHere("Stack walk must walk only frames of known code");

        } else if (totalFrameSize != CodeInfoQueryResult.ENTRY_POINT_FRAME_SIZE) {
            /* Bump sp *up* over my frame. */
            if (Platform.includedIn(Platform.AArch64.class)) {
                sp = sp.<Pointer>readWord(-16).add(16);
            } else {
                sp = sp.add(WordFactory.unsigned(totalFrameSize));
            }
            /* Read the return address to my caller. */
            ip = FrameAccess.singleton().readReturnAddress(sp);

            walk.setSP(sp);
            walk.setIP(ip);
            return true;

        } else {
            /* Reached an entry point frame. */

            JavaFrameAnchor anchor = walk.getAnchor();
            while (anchor.isNonNull() && anchor.getLastJavaSP().belowOrEqual(sp)) {
                /* Skip anchors that are in parts of the stack we are not traversing. */
                anchor = anchor.getPreviousAnchor();
            }

            if (anchor.isNonNull()) {
                /* We have more Java frames after a block of C frames. */
                assert anchor.getLastJavaSP().aboveThan(sp);
                walk.setSP(anchor.getLastJavaSP());
                walk.setIP(anchor.getLastJavaIP());
                walk.setAnchor(anchor.getPreviousAnchor());
                return true;

            } else {
                /* Really at the end of the stack, we are done with walking. */
                walk.setSP(WordFactory.nullPointer());
                walk.setIP(WordFactory.nullPointer());
                walk.setAnchor(WordFactory.nullPointer());
                return false;
            }
        }
    }

    @AlwaysInline("avoid virtual call to visitor")
    public static boolean walkCurrentThread(Pointer startSP, CodePointer startIP, StackFrameVisitor visitor) {
        JavaStackWalk walk = StackValue.get(JavaStackWalk.class);
        boolean hasFrames = initWalk(walk, startSP, startIP);
        return doWalk(walk, visitor, hasFrames);
    }

    @AlwaysInline("avoid virtual call to visitor")
    public static boolean walkThread(IsolateThread thread, StackFrameVisitor visitor) {
        JavaStackWalk walk = StackValue.get(JavaStackWalk.class);
        boolean hasFrames = initWalk(walk, thread);
        return doWalk(walk, visitor, hasFrames);
    }

    @AlwaysInline("avoid virtual call to visitor")
    private static boolean doWalk(JavaStackWalk walk, StackFrameVisitor visitor, boolean hasFrames) {
        if (!visitor.prologue()) {
            return false;
        }
        if (hasFrames) {
            do {
                if (!visitor.visitFrame(walk.getSP(), walk.getIP(), Deoptimizer.checkDeoptimized(walk.getSP()))) {
                    return false;
                }
            } while (continueWalk(walk));
        }
        return visitor.epilogue();
    }
}
