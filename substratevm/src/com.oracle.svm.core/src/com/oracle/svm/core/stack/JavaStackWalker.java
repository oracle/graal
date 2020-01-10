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
import com.oracle.svm.core.code.SimpleCodeInfoQueryResult;
import com.oracle.svm.core.code.UntetheredCodeInfo;
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
    @Uninterruptible(reason = "Prevent deoptimization of stack frames while in this method.", callerMustBe = true)
    public static boolean initWalk(JavaStackWalk walk, Pointer startSP, CodePointer startIP) {
        walk.setSP(startSP);
        walk.setPossiblyStaleIP(startIP);
        walk.setStartSP(startSP);
        walk.setStartIP(startIP);
        walk.setAnchor(JavaFrameAnchors.getFrameAnchor());
        if (startIP.isNonNull()) {
            // Storing the untethered object in a data structures requires that the caller and all
            // places that use that value are uninterruptible as well.
            walk.setIPCodeInfo(CodeInfoTable.lookupCodeInfo(startIP));
        } else { // will be read from the stack later
            walk.setIPCodeInfo(WordFactory.nullPointer());
        }
        return true;
    }

    /**
     * See {@link #initWalk(JavaStackWalk, Pointer, CodePointer)}, except that the instruction
     * pointer will be read from the stack later on.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static boolean initWalk(JavaStackWalk walk, Pointer startSP) {
        boolean result = initWalk(walk, startSP, WordFactory.nullPointer());
        assert walk.getIPCodeInfo().isNull() : "otherwise, the caller would have to be uninterruptible as well";
        return result;
    }

    /**
     * Initialize a stack walk for the given thread. The given {@code walk} parameter should
     * normally be allocated on the stack.
     *
     * @param walk the stack-allocated walk base pointer
     * @param thread the thread to examine
     */
    @Uninterruptible(reason = "Prevent deoptimization of stack frames while in this method.", callerMustBe = true)
    public static boolean initWalk(JavaStackWalk walk, IsolateThread thread) {
        assert VMOperation.isInProgressAtSafepoint() : "Walking the stack of another thread is only safe when that thread is stopped at a safepoint";

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
        // Storing the untethered object in a data structures requires that the caller and all
        // places that use that value are uninterruptible as well.
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
    @Uninterruptible(reason = "Prevent deoptimization of stack frames while in this method.")
    public static boolean continueWalk(JavaStackWalk walk, CodeInfo info) {
        if (walk.getSP().isNull() || walk.getPossiblyStaleIP().isNull()) {
            return false;
        }

        Pointer sp = walk.getSP();
        SimpleCodeInfoQueryResult queryResult = StackValue.get(SimpleCodeInfoQueryResult.class);

        DeoptimizedFrame deoptFrame = Deoptimizer.checkDeoptimized(sp);
        if (deoptFrame == null) {
            CodePointer ip = walk.getPossiblyStaleIP(); // no deopt since last call: still current
            if (info.isNull()) {
                throw reportUnknownFrameEncountered(walk, sp, deoptFrame);
            }

            lookupCodeInfoInterruptible(info, ip, queryResult);
            /* Frame could have been deoptimized during interruptible lookup above, check again. */
            deoptFrame = Deoptimizer.checkDeoptimized(sp);
        }

        return continueWalk(walk, queryResult, deoptFrame);
    }

    @Uninterruptible(reason = "Prevent deoptimization of stack frames while in this method.", callerMustBe = true)
    public static boolean continueWalk(JavaStackWalk walk, SimpleCodeInfoQueryResult queryResult, DeoptimizedFrame deoptFrame) {
        Pointer sp = walk.getSP();

        long encodedFrameSize;
        if (deoptFrame != null) {
            encodedFrameSize = deoptFrame.getSourceEncodedFrameSize();
        } else {
            encodedFrameSize = queryResult.getEncodedFrameSize();
        }

        if (!CodeInfoQueryResult.isEntryPoint(encodedFrameSize)) {
            long totalFrameSize = CodeInfoQueryResult.getTotalFrameSize(encodedFrameSize);

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
                walk.setIPCodeInfo(CodeInfoTable.lookupCodeInfo(anchor.getLastJavaIP()));
                return true;

            } else {
                /* Really at the end of the stack, we are done with walking. */
                walk.setSP(WordFactory.nullPointer());
                walk.setPossiblyStaleIP(WordFactory.nullPointer());
                walk.setAnchor(WordFactory.nullPointer());
                walk.setIPCodeInfo(WordFactory.nullPointer());
                return false;
            }
        }
    }

    @Uninterruptible(reason = "Wrap call to interruptible code.", calleeMustBe = false)
    private static void lookupCodeInfoInterruptible(CodeInfo codeInfo, CodePointer ip, SimpleCodeInfoQueryResult queryResult) {
        CodeInfoAccess.lookupCodeInfo(codeInfo, CodeInfoAccess.relativeIP(codeInfo, ip), queryResult);
    }

    @Uninterruptible(reason = "Not really uninterruptible, but we are about to fatally fail.", calleeMustBe = false)
    public static RuntimeException reportUnknownFrameEncountered(JavaStackWalk walk, Pointer sp, DeoptimizedFrame deoptFrame) {
        Log.log().string("Stack walk must walk only frames of known code:")
                        .string("  startSP=").hex(walk.getStartSP()).string("  startIP=").hex(walk.getStartIP())
                        .string("  sp=").hex(sp).string("  ip=").hex(walk.getPossiblyStaleIP()).string((deoptFrame != null) ? " (possibly before deopt)" : "")
                        .string("  deoptFrame=").object(deoptFrame)
                        .newline();
        throw VMError.shouldNotReachHere("Stack walk must walk only frames of known code");
    }

    @Uninterruptible(reason = "Prevent deoptimization of stack frames while in this method.")
    public static boolean walkCurrentThread(Pointer startSP, StackFrameVisitor visitor) {
        return walkCurrentThreadInline(startSP, visitor);
    }

    @AlwaysInline("Avoid virtual call to visitor - for the inlining, the caller must be uninterruptible as well.")
    @Uninterruptible(reason = "Prevent deoptimization of stack frames while in this method.", callerMustBe = true)
    public static boolean walkCurrentThreadInline(Pointer startSP, StackFrameVisitor visitor) {
        CodePointer startIP = FrameAccess.singleton().readReturnAddress(startSP);
        return walkCurrentThreadWithForcedIPInline(startSP, startIP, visitor);
    }

    @Uninterruptible(reason = "Prevent deoptimization of stack frames while in this method.")
    public static boolean walkCurrentThreadWithForcedIP(Pointer startSP, CodePointer startIP, StackFrameVisitor visitor) {
        return walkCurrentThreadWithForcedIPInline(startSP, startIP, visitor);
    }

    /**
     * Forces a stack walk with the given instruction pointer instead of reading the most current
     * value from the stack. Intended for specific cases only, such as signal handlers.
     */
    @AlwaysInline("Avoid virtual call to visitor - for the inlining, the caller must be uninterruptible as well.")
    @Uninterruptible(reason = "Prevent deoptimization of stack frames while in this method.", callerMustBe = true)
    public static boolean walkCurrentThreadWithForcedIPInline(Pointer startSP, CodePointer startIP, StackFrameVisitor visitor) {
        JavaStackWalk walk = StackValue.get(JavaStackWalk.class);
        boolean hasFrames = initWalk(walk, startSP, startIP);
        return doWalkInline(walk, visitor, hasFrames);
    }

    @AlwaysInline("Avoid virtual call to visitor - for the inlining, the caller must be uninterruptible as well.")
    @Uninterruptible(reason = "Prevent deoptimization of stack frames while in this method.", callerMustBe = true)
    private static boolean doWalkInline(JavaStackWalk walk, StackFrameVisitor visitor, boolean hasFrames) {
        if (hasFrames) {
            while (true) {
                UntetheredCodeInfo untetheredInfo = walk.getIPCodeInfo();
                Object tether = CodeInfoAccess.acquireTether(untetheredInfo);
                CodeInfo tetheredInfo = CodeInfoAccess.convert(untetheredInfo, tether);
                try {
                    // now the value in walk.getIPCodeInfo() can be passed to interruptible code
                    if (!callVisitorInline(walk, tetheredInfo, visitor)) {
                        return false;
                    }
                    if (!continueWalk(walk, tetheredInfo)) {
                        break;
                    }
                } finally {
                    CodeInfoAccess.releaseTether(untetheredInfo, tether);
                }
            }
        }
        return true;
    }

    @AlwaysInline("Avoid virtual call to visitor - for the inlining, the caller must be uninterruptible as well.")
    @Uninterruptible(reason = "Wraps the now safe call to the possibly interruptible visitor.", callerMustBe = true, calleeMustBe = false)
    private static boolean callVisitorInline(JavaStackWalk walk, CodeInfo info, StackFrameVisitor visitor) {
        return visitor.visitFrame(walk.getSP(), walk.getPossiblyStaleIP(), info, Deoptimizer.checkDeoptimized(walk.getSP()));
    }

    @Uninterruptible(reason = "Prevent deoptimization of stack frames while in this method.")
    public static boolean walkThread(IsolateThread thread, StackFrameVisitor visitor) {
        return walkThreadInline(thread, visitor);
    }

    @AlwaysInline("Avoid virtual call to visitor - for the inlining, the caller must be uninterruptible as well.")
    @Uninterruptible(reason = "Prevent deoptimization of stack frames while in this method.", callerMustBe = true)
    public static boolean walkThreadInline(IsolateThread thread, StackFrameVisitor visitor) {
        JavaStackWalk walk = StackValue.get(JavaStackWalk.class);
        boolean hasFrames = initWalk(walk, thread);
        return doWalkInline(walk, visitor, hasFrames);
    }
}
