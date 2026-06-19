/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.interpreter.ristretto;

import org.graalvm.nativeimage.c.function.CFunctionPointer;

import com.oracle.svm.guest.staging.core.threadlocal.FastThreadLocalFactory;
import com.oracle.svm.guest.staging.core.threadlocal.FastThreadLocalObject;
import com.oracle.svm.guest.staging.jdk.InternalVMMethod;
import com.oracle.svm.interpreter.Interpreter.OSRResult;
import com.oracle.svm.interpreter.InterpreterFrame;
import com.oracle.svm.interpreter.InterpreterFrameUtil;
import com.oracle.svm.interpreter.InterpreterStubSection;
import com.oracle.svm.interpreter.SemanticJavaException;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedJavaMethod;
import com.oracle.svm.interpreter.metadata.profile.MethodProfile;
import com.oracle.svm.interpreter.ristretto.meta.RistrettoMethod;
import com.oracle.svm.interpreter.ristretto.meta.RistrettoOSRBackedgeState;
import com.oracle.svm.interpreter.ristretto.profile.RistrettoCompilationManager;
import com.oracle.svm.interpreter.ristretto.profile.RistrettoCompilationRequest;
import com.oracle.svm.interpreter.ristretto.profile.RistrettoProfileSupport;
import com.oracle.svm.shared.util.VMError;

/**
 * Runtime support for Ristretto on-stack replacement.
 *
 * The interpreter copies the active frame reference into {@link #CURRENT_OSR_FRAME} immediately before
 * entering an OSR-compiled body. The OSR graph reads live locals from this frame at its entry BCI and
 * then continues as ordinary runtime-compiled Ristretto code.
 *
 * For example, for a method whose loop header is bytecode index {@code 12}:
 *
 * <pre>
 * int sum = seed;          // local 2
 * for (int i = start;      // local 1
 *      i < limit;
 *      i++) {              // backedge reaches BCI 12
 *     sum += i;
 * }
 * return sum;
 * </pre>
 *
 * OSR is attempted just before the interpreter jumps back to BCI 12. At that point the interpreter
 * records the current {@link InterpreterFrame} in {@link #CURRENT_OSR_FRAME}; the OSR graph starts at BCI
 * 12, calls {@code getIntLocal(1)} and {@code getIntLocal(2)} to materialize {@code i} and {@code sum},
 * and then executes the rest of the loop in compiled code. Monitor entries remain on that frame, so
 * compiled code can reload the active lock objects while unwinding synchronized regions that were
 * entered by the interpreter.
 */
@InternalVMMethod
public final class RistrettoOSRSupport {
    private static final FastThreadLocalObject<InterpreterFrame> CURRENT_OSR_FRAME = FastThreadLocalFactory.createObject(InterpreterFrame.class,
                    "RistrettoOSRSupport.CURRENT_OSR_FRAME");

    /**
     * Checks whether the current backward branch should enter OSR-compiled Ristretto code.
     *
     * <pre>
     * if OSR is disabled or the operand stack is not empty:
     *     stay in the interpreter
     * count = methodProfile.profileOSRBackedge(targetBCI)
     * entry = ristrettoMethod.osrBackedgeState(targetBCI)
     * if count crossed threshold:
     *     submit one OSR compile for targetBCI
     * every ~1024 later backedges while compilation is pending:
     *     poll installed code
     * if installed code exists:
     *     leave the interpreter through the compiled entry point
     * </pre>
     *
     * The per-target state is precomputed from the method bytecodes in {@link RistrettoMethod}, so the
     * hot interpreter path does not allocate or hash while executing a loop backedge.
     */
    public static OSRResult tryOSR(InterpreterResolvedJavaMethod method, MethodProfile methodProfile, InterpreterFrame frame, int targetBCI, int top) {
        if (methodProfile == null || !RistrettoProfileSupport.isEnabled() || !RistrettoOptions.JITEnableCompilation.getValue() || !RistrettoOptions.JITUseOnStackReplacement.getValue()) {
            return null;
        }
        if (top != InterpreterFrameUtil.startingStackOffset(method.getMaxLocals())) {
            /*
             * Reject OSR when the backedge has live operand-stack values. Ristretto's OSR entry
             * reconstructs locals and monitors from the interpreter frame, but it does not reconstruct
             * operand-stack values. This matches HotSpot OSR semantics: C1 and C2 only enter OSR when
             * the operand stack is empty and otherwise bail out of OSR compilation.
             */
            return null;
        }

        RistrettoMethod rMethod = RistrettoMethod.getOrCreate(method);
        RistrettoOSRBackedgeState backedge = rMethod.getOSRBackedgeState(targetBCI);
        if (backedge == null) {
            return null;
        }
        long backedgeCount = methodProfile.profileOSRBackedge(targetBCI);
        int osrThreshold = RistrettoOptions.getJITCompilerOSRBackedgeThreshold();
        if (backedgeCount > osrThreshold && RistrettoOptions.matchesJITCompileOnly(method)) {
            int requestId = rMethod.claimOSRCompilationRequest(targetBCI);
            if (requestId != RistrettoMethod.NO_OSR_COMPILATION_REQUEST) {
                RistrettoCompilationManager.get().submitCompilationRequest(new RistrettoCompilationRequest(rMethod, RistrettoCompilationRequest.DEFAULT_OSR_COMPILATION_PRIORITY, targetBCI,
                                requestId));
            }
        }

        if (!methodProfile.shouldPollOSRBackedgeCode(targetBCI)) {
            return null;
        }
        CFunctionPointer entryPoint = rMethod.getOSRInstalledCodeEntryPointIfLive(targetBCI);
        if (entryPoint.isNull()) {
            /*
             * Invalidated or unpublished OSR code is still an interpreter miss at this point: the
             * frame has not been hidden yet, and Interpreter.execute0 keeps normal monitor-cleanup
             * ownership for the current activation.
             */
            return null;
        }
        /*
         * The compiled OSR body has now produced the logical Java method result, or threw out of the
         * compiled continuation. The interpreter owns the control-flow mechanism that leaves the
         * current bytecode dispatch frame.
         */
        return enterOSR(method, frame, entryPoint);
    }

    private static OSRResult enterOSR(InterpreterResolvedJavaMethod method, InterpreterFrame frame, CFunctionPointer entryPoint) {
        InterpreterFrame previousOSRFrame = CURRENT_OSR_FRAME.get();
        CURRENT_OSR_FRAME.set(frame);
        try {
            /*
             * The compiled or deoptimized continuation owns the live logical Java frame after OSR. Keep
             * the replaced interpreter frame hidden from stack walking until it leaves the interpreter.
             * This is intentionally not undone locally: beforeJumpChecks converts the result into an
             * OSRReturn/OSRException marker, and Interpreter.execute0 is the boundary that unwinds the
             * old activation with interpreter-frame lock cleanup disabled. If OSR deoptimizes and
             * resumes interpretation, InterpreterDeoptEntryPoints resumes through a new interpreter
             * frame that is visible to Java stack walking.
             */
            frame.hideFromStackWalking();
            try {
                Object result = InterpreterStubSection.leaveInterpreterOSR(entryPoint, method);
                /*
                 * Once we leave the interpreter through a live OSR entry point, any non-fatal return
                 * or rethrown guest exception belongs to the compiled continuation or its deoptimized
                 * replacement. The old interpreter frame must therefore skip lock cleanup on every
                 * normal exit from this helper.
                 */
                return OSRResult.forValue(result);
            } catch (SemanticJavaException e) {
                throw VMError.shouldNotReachHere("Unexpected SemanticJavaException escaping compiled OSR entry", e);
            } catch (Throwable e) {
                /*
                 * Only exceptions thrown after control transferred into the compiled continuation are
                 * converted into an OSRResult. Transfer-state setup and teardown failures should still
                 * surface as internal VM failures instead of being normalized into guest exceptions.
                 */
                return OSRResult.forException(e);
            }
        } finally {
            if (previousOSRFrame == null) {
                CURRENT_OSR_FRAME.set(null);
            } else {
                CURRENT_OSR_FRAME.set(previousOSRFrame);
            }
        }
    }

    /*
     * These helpers are resolved by name and descriptor in RistrettoUtils and invoked from generated
     * OSR graphs; ordinary Java call-site searches only see the lookup strings.
     */
    public static int getIntLocal(int slot) {
        return InterpreterFrameUtil.getLocalInt(currentFrame(), slot);
    }

    public static float getFloatLocal(int slot) {
        return InterpreterFrameUtil.getLocalFloat(currentFrame(), slot);
    }

    public static long getLongLocal(int slot) {
        return InterpreterFrameUtil.getLocalLong(currentFrame(), slot);
    }

    public static double getDoubleLocal(int slot) {
        return InterpreterFrameUtil.getLocalDouble(currentFrame(), slot);
    }

    public static Object getObjectLocal(int slot) {
        return InterpreterFrameUtil.getLocalObject(currentFrame(), slot);
    }

    public static Object getLockObject(int index) {
        Object lock = currentFrame().getLock(index);
        if (lock == null) {
            throw VMError.shouldNotReachHere("No active Ristretto OSR lock at index " + index);
        }
        return lock;
    }

    private static InterpreterFrame currentFrame() {
        InterpreterFrame frame = CURRENT_OSR_FRAME.get();
        if (frame == null) {
            throw VMError.shouldNotReachHere("No current Ristretto OSR frame");
        }
        return frame;
    }
}
