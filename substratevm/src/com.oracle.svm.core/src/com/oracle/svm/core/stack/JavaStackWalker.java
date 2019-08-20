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
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.FrameAccess;
import com.oracle.svm.core.annotate.AlwaysInline;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.code.CodeInfo;
import com.oracle.svm.core.code.CodeInfoAccess;
import com.oracle.svm.core.code.CodeInfoQueryResult;
import com.oracle.svm.core.code.CodeInfoTable;
import com.oracle.svm.core.deopt.DeoptimizedFrame;
import com.oracle.svm.core.deopt.Deoptimizer;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.thread.VMOperation;
import com.oracle.svm.core.util.VMError;

/**
 * Applies a {@link StackFrameVisitor} to each of the Java frames in a thread stack. It skips native
 * frames, i.e., it only visits frames where {@link CodeInfoAccess#lookupTotalFrameSize Java frame
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
    @Uninterruptible(reason = "Prevent deoptimization of stack frames while in this method.", mayBeInlined = true)
    public static boolean initWalk(JavaStackWalk walk, Pointer startSP, CodePointer startIP) {
        walk.setSP(startSP);
        walk.setPossiblyStaleIP(startIP);
        walk.setStartSP(startSP);
        walk.setStartIP(startIP);
        walk.setAnchor(JavaFrameAnchors.getFrameAnchor());
        if (startIP.isNonNull()) {
            walk.setIPCodeInfo(CodeInfoTable.lookupCodeInfo(startIP));
        } else { // will be read from the stack later
            walk.setIPCodeInfo(WordFactory.nullPointer());
        }
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
        walk.setPossiblyStaleIP(ip);
        walk.setStartSP(sp);
        walk.setStartIP(ip);
        walk.setAnchor(anchor);
        walk.setIPCodeInfo(CodeInfoTable.lookupCodeInfo(ip));

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
    @Uninterruptible(reason = "Prevent deoptimization of stack frames while in this method.", mayBeInlined = true)
    public static boolean continueWalk(JavaStackWalk walk) {
        if (walk.getSP().isNull() || walk.getPossiblyStaleIP().isNull()) {
            return false;
        }

        Pointer sp = walk.getSP();

        long totalFrameSize;
        DeoptimizedFrame deoptFrame = Deoptimizer.checkDeoptimized(sp);
        if (deoptFrame != null) {
            totalFrameSize = deoptFrame.getSourceTotalFrameSize();
        } else {
            CodePointer ip = walk.getPossiblyStaleIP(); // no deopt since last call: still current
            CodeInfo info = walk.getIPCodeInfo();
            assert CodeInfoAccess.isTethered(info) || VMOperation.isInProgress() : "Caller must provide safe access for code information";
            totalFrameSize = lookupTotalFrameSize0(info, ip);
        }

        if (totalFrameSize == -1) {
            throw unknownFrameEncountered(walk, sp, deoptFrame);

        } else if (totalFrameSize != CodeInfoQueryResult.ENTRY_POINT_FRAME_SIZE) {
            /* Bump sp *up* over my frame. */
            sp = sp.add(WordFactory.unsigned(totalFrameSize));
            /* Read the return address to my caller. */
            CodePointer ip = FrameAccess.singleton().readReturnAddress(sp);

            walk.setSP(sp);
            walk.setPossiblyStaleIP(ip);

            walk.setIPCodeInfo(CodeInfoTable.lookupCodeInfo(ip));
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
                walk.setPossiblyStaleIP(anchor.getLastJavaIP());
                walk.setAnchor(anchor.getPreviousAnchor());

                anchor.getLastJavaIP();
                walk.setIPCodeInfo(CodeInfoTable.lookupCodeInfo(anchor.getLastJavaIP()));
                return true;

            } else {
                /* Really at the end of the stack, we are done with walking. */
                walk.setSP(WordFactory.nullPointer());
                walk.setPossiblyStaleIP(WordFactory.nullPointer());
                walk.setAnchor(WordFactory.nullPointer());
                return false;
            }
        }
    }

    @Uninterruptible(reason = "Wraps the now safe call to look up the frame size.", calleeMustBe = false)
    private static long lookupTotalFrameSize0(CodeInfo info, CodePointer ip) {
        return CodeInfoAccess.lookupTotalFrameSize(info, CodeInfoAccess.relativeIP(info, ip));
        /*
         * NOTE: the frame could have been deoptimized during this call, but the frame size must
         * remain the same, and we will move on to the next frame anyway.
         */
    }

    @Uninterruptible(reason = "Not really uninterruptible, but we are about to fatally fail.", calleeMustBe = false)
    private static RuntimeException unknownFrameEncountered(JavaStackWalk walk, Pointer sp, DeoptimizedFrame deoptFrame) {
        Log.log().string("Stack walk must walk only frames of known code:")
                        .string("  startSP=").hex(walk.getStartSP()).string("  startIP=").hex(walk.getStartIP())
                        .string("  sp=").hex(sp).string("  ip=").hex(walk.getPossiblyStaleIP()).string((deoptFrame != null) ? " (possibly before deopt)" : "")
                        .string("  deoptFrame=").object(deoptFrame)
                        .newline();
        throw VMError.shouldNotReachHere("Stack walk must walk only frames of known code");
    }

    @Uninterruptible(reason = "Prevent deoptimization of stack frames while in this method.")
    public static boolean walkCurrentThread(Pointer startSP, StackFrameVisitor visitor) {
        CodePointer startIP = FrameAccess.singleton().readReturnAddress(startSP);
        return walkCurrentThreadWithForcedIP(startSP, startIP, visitor);
    }

    /**
     * Forces a stack walk with the given instruction pointer instead of reading the most current
     * value from the stack. Intended for specific cases only, such as signal handlers.
     */
    @Uninterruptible(reason = "Prevent deoptimization of stack frames while in this method.")
    public static boolean walkCurrentThreadWithForcedIP(Pointer startSP, CodePointer startIP, StackFrameVisitor visitor) {
        JavaStackWalk walk = StackValue.get(JavaStackWalk.class);
        boolean hasFrames = initWalk(walk, startSP, startIP);
        return doWalkCurrentThread(walk, visitor, hasFrames);
    }

    @Uninterruptible(reason = "Prevent deoptimization of stack frames while in this method.", callerMustBe = true)
    private static boolean doWalkCurrentThread(JavaStackWalk walk, StackFrameVisitor visitor, boolean hasFrames) {
        if (hasFrames) {
            while (true) {
                CodeInfo info = walk.getIPCodeInfo();
                Object tether = CodeInfoAccess.acquireTether(info);
                try {
                    if (!callVisitor(walk, visitor)) {
                        return false;
                    }
                    if (!continueWalk(walk)) {
                        break;
                    }
                } finally {
                    CodeInfoAccess.releaseTether(info, tether);
                }
            }
        }
        return true;
    }

    @Uninterruptible(reason = "Wraps the now safe call to the possibly interruptible visitor.", calleeMustBe = false)
    private static boolean callVisitor(JavaStackWalk walk, StackFrameVisitor visitor) {
        return visitor.visitFrame(walk.getSP(), walk.getPossiblyStaleIP(), walk.getIPCodeInfo(), Deoptimizer.checkDeoptimized(walk.getSP()));
    }

    @AlwaysInline("avoid virtual call to visitor")
    public static boolean walkThread(IsolateThread thread, StackFrameVisitor visitor) {
        JavaStackWalk walk = StackValue.get(JavaStackWalk.class);
        boolean hasFrames = initWalk(walk, thread);
        return doWalkThread(walk, visitor, hasFrames);
    }

    @AlwaysInline("avoid virtual call to visitor")
    private static boolean doWalkThread(JavaStackWalk walk, StackFrameVisitor visitor, boolean hasFrames) {
        if (hasFrames) {
            do {
                if (!visitor.visitFrame(walk.getSP(), walk.getPossiblyStaleIP(), walk.getIPCodeInfo(), Deoptimizer.checkDeoptimized(walk.getSP()))) {
                    return false;
                }
            } while (continueWalk(walk));
        }
        return true;
    }
}
