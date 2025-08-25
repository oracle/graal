/*
 * Copyright (c) 2014, 2017, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.svm.core.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;
import static jdk.graal.compiler.core.common.spi.ForeignCallDescriptor.CallSideEffect.NO_SIDE_EFFECT;

import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.word.Pointer;

import com.oracle.svm.core.SubstrateDiagnostics;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.graal.meta.SubstrateForeignCallsProvider;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.snippets.SnippetRuntime;
import com.oracle.svm.core.snippets.SubstrateForeignCallTarget;
import com.oracle.svm.core.thread.VMOperation;
import com.oracle.svm.core.thread.VMThreads.StatusSupport;
import com.oracle.svm.core.threadlocal.FastThreadLocal;
import com.oracle.svm.core.threadlocal.FastThreadLocalFactory;
import com.oracle.svm.core.threadlocal.FastThreadLocalInt;
import com.oracle.svm.core.threadlocal.FastThreadLocalWord;
import com.oracle.svm.core.traits.BuiltinTraits.BuildtimeAccessOnly;
import com.oracle.svm.core.traits.BuiltinTraits.NoLayeredCallbacks;
import com.oracle.svm.core.traits.SingletonLayeredInstallationKind.Independent;
import com.oracle.svm.core.traits.SingletonTraits;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.core.common.spi.ForeignCallDescriptor;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.extended.ForeignCallNode;
import jdk.graal.compiler.word.Word;

/**
 * Maintains the linked list of {@link JavaFrameAnchor} for stack walking. Note that a thread may
 * only push/pop/modify a frame anchor while its thread status is
 * {@link StatusSupport#STATUS_IN_JAVA}. This is necessary to guarantee that we can walk the stack
 * of other threads consistently while in a VM operation.
 */
public class JavaFrameAnchors {
    static final SnippetRuntime.SubstrateForeignCallDescriptor VERIFY_FRAME_ANCHOR_STUB = SnippetRuntime.findForeignCall(JavaFrameAnchors.class, "verifyFrameAnchorStub", NO_SIDE_EFFECT);
    private static final FastThreadLocalWord<JavaFrameAnchor> lastAnchorTL = FastThreadLocalFactory.createWord("JavaFrameAnchors.lastAnchor").setMaxOffset(FastThreadLocal.BYTE_OFFSET);
    private static final FastThreadLocalInt verificationInProgressTL = FastThreadLocalFactory.createInt("JavaFrameAnchors.verificationInProgress");

    public static void pushFrameAnchor(JavaFrameAnchor newAnchor) {
        if (SubstrateOptions.VerifyFrameAnchors.getValue()) {
            newAnchor.setMagicBefore(JavaFrameAnchor.MAGIC);
            newAnchor.setMagicAfter(JavaFrameAnchor.MAGIC);
        }

        /*
         * Set IP and SP to null during initialization, these values will later be overwritten by
         * proper ones (see usages of KnownOffsets.getJavaFrameAnchorLastSPOffset() in the backend).
         * The intention is to not see stale values when debugging or in signal handlers.
         */
        newAnchor.setLastJavaIP(Word.nullPointer());
        newAnchor.setLastJavaSP(Word.nullPointer());

        JavaFrameAnchor prev = lastAnchorTL.get();
        newAnchor.setPreviousAnchor(prev);
        lastAnchorTL.set(newAnchor);

        verifyFrameAnchor(true);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static void popFrameAnchor() {
        verifyFrameAnchor(false);

        JavaFrameAnchor cur = lastAnchorTL.get();
        JavaFrameAnchor prev = cur.getPreviousAnchor();
        lastAnchorTL.set(prev);
    }

    /** Returns the last Java frame anchor for the current thread, or null if there is none. */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static JavaFrameAnchor getFrameAnchor() {
        return lastAnchorTL.get();
    }

    /**
     * Returns the last Java frame anchor for the given thread, or null if there is none. Note that
     * even at a safepoint, there is no guarantee that all stopped {@link IsolateThread}s have a
     * Java frame anchor (e.g., threads that are currently attaching don't necessarily have a frame
     * anchor).
     */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static JavaFrameAnchor getFrameAnchor(IsolateThread thread) {
        assert thread == CurrentIsolate.getCurrentThread() || VMOperation.isInProgressAtSafepoint();
        return lastAnchorTL.get(thread);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static void verifyFrameAnchor(boolean newAnchor) {
        if (SubstrateOptions.VerifyFrameAnchors.getValue()) {
            call(VERIFY_FRAME_ANCHOR_STUB, newAnchor);
        }
    }

    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static void verifyFrameAnchorStub(boolean newAnchor) {
        if (verificationInProgressTL.get() != 0 || SubstrateDiagnostics.isFatalErrorHandlingThread()) {
            return;
        }

        verificationInProgressTL.set(verificationInProgressTL.get() + 1);
        try {
            JavaFrameAnchor cur = lastAnchorTL.get();
            verifyFrameAnchor(cur, newAnchor);

            JavaFrameAnchor prev = cur.getPreviousAnchor();
            if (prev.isNonNull()) {
                verifyFrameAnchor(prev, false);
            }
        } finally {
            verificationInProgressTL.set(verificationInProgressTL.get() - 1);
        }
    }

    /**
     * Verifies that the top {@link JavaFrameAnchor} is in a part of the stack that is safe to
     * access. This method should be called whenever code manipulates the Java stack significantly.
     */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static void verifyTopFrameAnchor(Pointer safeStackBegin) {
        Pointer anchor = (Pointer) JavaFrameAnchors.getFrameAnchor();
        if (anchor.isNonNull()) {
            VMError.guarantee(anchor.aboveOrEqual(safeStackBegin), "The frame anchor struct is outside of the stack that is safe to use.");
        }
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static void verifyFrameAnchor(JavaFrameAnchor cur, boolean newAnchor) {
        VMError.guarantee(StatusSupport.getStatusVolatile() == StatusSupport.STATUS_IN_JAVA, "Invalid thread status.");
        if (((Pointer) cur).belowThan(KnownIntrinsics.readStackPointer())) {
            throw verificationFailed(cur, "The frame anchor struct is outside of the used stack.");
        }
        if (cur.getMagicBefore() != JavaFrameAnchor.MAGIC) {
            throw verificationFailed(cur, "Magic number at the start of the frame anchor is corrupt.");
        }
        if (cur.getMagicAfter() != JavaFrameAnchor.MAGIC) {
            throw verificationFailed(cur, "Magic number at the end of the frame anchor is corrupt.");
        }
        if (newAnchor != cur.getLastJavaIP().isNull()) {
            throw verificationFailed(cur, "Invalid IP");
        }
        if (newAnchor != cur.getLastJavaSP().isNull()) {
            throw verificationFailed(cur, "Invalid SP");
        }
    }

    @Uninterruptible(reason = "No need to be uninterruptible because we are terminating with a fatal error.", calleeMustBe = false)
    private static RuntimeException verificationFailed(JavaFrameAnchor cur, String msg) {
        Log log = Log.log();
        log.string("Frame anchor verification failed: ").string(msg).newline();
        log.string("Anchor ").zhex(cur).string(" MagicBefore ").zhex(cur.getMagicBefore()).string(" LastJavaSP ").zhex(cur.getLastJavaSP()).string(" LastJavaIP ").zhex(cur.getLastJavaIP())
                        .string(" Previous ").zhex(cur.getPreviousAnchor()).string(" MagicAfter ").zhex(cur.getMagicAfter()).newline();

        throw VMError.shouldNotReachHere(msg);
    }

    @Node.NodeIntrinsic(value = ForeignCallNode.class)
    private static native void call(@Node.ConstantNodeParameter ForeignCallDescriptor descriptor, boolean newAnchor);
}

@SingletonTraits(access = BuildtimeAccessOnly.class, layeredCallbacks = NoLayeredCallbacks.class, layeredInstallationKind = Independent.class)
@AutomaticallyRegisteredFeature
class JavaFrameAnchorsFeature implements InternalFeature {
    @Override
    public void registerForeignCalls(SubstrateForeignCallsProvider foreignCalls) {
        foreignCalls.register(JavaFrameAnchors.VERIFY_FRAME_ANCHOR_STUB);
    }
}
