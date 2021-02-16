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

import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.FrameAccess;
import com.oracle.svm.core.annotate.RestrictHeapAccess;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.code.CodeInfo;
import com.oracle.svm.core.code.CodeInfoAccess;
import com.oracle.svm.core.code.CodeInfoQueryResult;
import com.oracle.svm.core.code.CodeInfoTable;
import com.oracle.svm.core.code.SimpleCodeInfoQueryResult;
import com.oracle.svm.core.code.UntetheredCodeInfo;
import com.oracle.svm.core.deopt.DeoptimizationSupport;
import com.oracle.svm.core.deopt.DeoptimizedFrame;
import com.oracle.svm.core.deopt.Deoptimizer;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.thread.VMOperation;
import com.oracle.svm.core.util.VMError;

/**
 * Provides methods to iterate each of the Java frames in a thread stack. It skips native frames,
 * i.e., it only visits frames where {@link CodeInfoAccess#lookupTotalFrameSize Java frame
 * information} is available.
 * <p>
 * For most cases, the "walk*" methods that apply a {@link StackFrameVisitor} are the preferred way
 * to do stack walking. Use cases that are extremely performance sensitive, or cannot use a visitor
 * approach, can use the various "init*" and "continue*" methods directly.
 * <p>
 * The stack walking code must be allocation free (so that it can be used during garbage collection)
 * and not use static state (so that multiple threads can walk their stacks concurrently). State is
 * therefore stored in a stack-allocated {@link JavaStackWalk} structure.
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
    public static void initWalk(JavaStackWalk walk, Pointer startSP, CodePointer startIP) {
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
    }

    /**
     * See {@link #initWalk(JavaStackWalk, Pointer, CodePointer)}, except that the instruction
     * pointer will be read from the stack later on.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void initWalk(JavaStackWalk walk, Pointer startSP) {
        initWalk(walk, startSP, WordFactory.nullPointer());
        assert walk.getIPCodeInfo().isNull() : "otherwise, the caller would have to be uninterruptible as well";
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
        assert thread.notEqual(CurrentIsolate.getCurrentThread()) : "Cannot walk the current stack with this method, it would miss all frames after the last frame anchor";
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
            lookupCodeInfoInterruptible(info, walk.getPossiblyStaleIP(), queryResult);
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
    public static RuntimeException reportUnknownFrameEncountered(Pointer sp, CodePointer ip, DeoptimizedFrame deoptFrame) {
        Log log = Log.log().string("Stack walk must walk only frames of known code:");
        log.string("  sp=").hex(sp).string("  ip=").hex(ip);
        if (DeoptimizationSupport.enabled()) {
            log.string("  deoptFrame=").object(deoptFrame);
        }
        log.newline();
        throw VMError.shouldNotReachHere("Stack walk must walk only frames of known code");

    }

    public static boolean walkCurrentThread(Pointer startSP, StackFrameVisitor visitor) {
        return walkCurrentThread(startSP, visitor, null);
    }

    @Uninterruptible(reason = "Prevent deoptimization of stack frames while in this method.")
    public static <T> boolean walkCurrentThread(Pointer startSP, ParameterizedStackFrameVisitor<T> visitor, T data) {
        CodePointer startIP = FrameAccess.singleton().readReturnAddress(startSP);
        JavaStackWalk walk = StackValue.get(JavaStackWalk.class);
        initWalk(walk, startSP, startIP);
        return doWalk(walk, visitor, data);
    }

    public static boolean walkThread(IsolateThread thread, StackFrameVisitor visitor) {
        return walkThread(thread, visitor, null);
    }

    @Uninterruptible(reason = "Prevent deoptimization of stack frames while in this method.")
    public static <T> boolean walkThread(IsolateThread thread, ParameterizedStackFrameVisitor<T> visitor, T data) {
        JavaStackWalk walk = StackValue.get(JavaStackWalk.class);
        if (initWalk(walk, thread)) {
            return doWalk(walk, visitor, data);
        } else {
            return true;
        }
    }

    @Uninterruptible(reason = "Prevent deoptimization of stack frames while in this method.", callerMustBe = true)
    static <T> boolean doWalk(JavaStackWalk walk, ParameterizedStackFrameVisitor<T> visitor, T data) {
        while (true) {
            UntetheredCodeInfo untetheredInfo = walk.getIPCodeInfo();
            if (untetheredInfo.isNull()) {
                return callUnknownFrame(walk, visitor, data);
            }

            Object tether = CodeInfoAccess.acquireTether(untetheredInfo);
            CodeInfo tetheredInfo = CodeInfoAccess.convert(untetheredInfo, tether);
            try {
                // now the value in walk.getIPCodeInfo() can be passed to interruptible code
                if (!callVisitor(walk, tetheredInfo, visitor, data)) {
                    return false;
                }
                if (!continueWalk(walk, tetheredInfo)) {
                    return true;
                }
            } finally {
                CodeInfoAccess.releaseTether(untetheredInfo, tether);
            }
        }
    }

    @Uninterruptible(reason = "CodeInfo in JavaStackWalk is currently null, and we are going to abort the stack walking.", calleeMustBe = false)
    private static <T> boolean callUnknownFrame(JavaStackWalk walk, ParameterizedStackFrameVisitor<T> visitor, T data) {
        return visitor.unknownFrame(walk.getSP(), walk.getPossiblyStaleIP(), Deoptimizer.checkDeoptimized(walk.getSP()), data);
    }

    @Uninterruptible(reason = "Wraps the now safe call to the possibly interruptible visitor.", callerMustBe = true, calleeMustBe = false)
    @RestrictHeapAccess(reason = "Whitelisted because some StackFrameVisitor implementations can allocate.", access = RestrictHeapAccess.Access.UNRESTRICTED, overridesCallers = true)
    static <T> boolean callVisitor(JavaStackWalk walk, CodeInfo info, ParameterizedStackFrameVisitor<T> visitor, T data) {
        return visitor.visitFrame(walk.getSP(), walk.getPossiblyStaleIP(), info, Deoptimizer.checkDeoptimized(walk.getSP()), data);
    }
}
