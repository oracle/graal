/*
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.truffle.espresso.runtime;

import java.lang.ref.WeakReference;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.espresso.impl.ClassRegistry;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.vm.VM;

public class EspressoThreadLocalState {
    private EspressoException pendingJniException;
    private final ClassRegistry.TypeStack typeStack;
    private final VM.PrivilegedStack privilegedStack;
    // Not compilation final. A single host thread can be associated with multiple different guest
    // threads during its lifetime (for example: on natural exits, the host main thread will be both
    // the guest main thread, and the DestroyVM thread).
    private StaticObject currentPlatformThread;
    private StaticObject currentVirtualThread;

    // A refcount, when >0 the serializable continuation mechanism will refuse to suspend.
    private int suspensionBlocks;
    private boolean inContinuation;

    private int singleSteppingDisabledCounter;

    private boolean stepInProgress;

    private boolean inTransformer;

    private WeakReference<Thread> hostThread;

    @SuppressWarnings("unused")
    public EspressoThreadLocalState(EspressoContext context, Thread t) {
        typeStack = new ClassRegistry.TypeStack();
        privilegedStack = new VM.PrivilegedStack(context);
        assert ((hostThread = new WeakReference<>(t)) != null);
    }

    public StaticObject getPendingExceptionObject() {
        EspressoException espressoException = getPendingException();
        if (espressoException == null) {
            return null;
        }
        return espressoException.getGuestException();
    }

    public EspressoException getPendingException() {
        return pendingJniException;
    }

    public void setPendingException(EspressoException t) {
        // TODO(peterssen): Warn about overwritten pending exceptions.
        pendingJniException = t;
    }

    public void clearPendingException() {
        setPendingException(null);
    }

    public void setCurrentPlatformThread(StaticObject t) {
        assert t != null && StaticObject.notNull(t);
        assert diagnose(t);
        currentPlatformThread = t;
    }

    public void setCurrentVirtualThread(StaticObject t) {
        assert t != null && StaticObject.notNull(t);
        currentVirtualThread = t;
    }

    public void initializeCurrentThread(StaticObject t) {
        assert diagnose(t);
        setCurrentPlatformThread(t);
        setCurrentVirtualThread(t);
    }

    public void clearCurrentThread(StaticObject expectedGuest) {
        if (currentPlatformThread == expectedGuest) {
            currentPlatformThread = null;
            currentVirtualThread = null;
        } else {
            expectedGuest.getKlass().getContext().getLogger().warning("clearCurrentThread: unexpected currentPlatformThread");
        }
    }

    public StaticObject getCurrentPlatformThread(EspressoContext context) {
        StaticObject result = currentPlatformThread;
        if (result == null) {
            // Failsafe, should not happen.
            CompilerDirectives.transferToInterpreterAndInvalidate();
            context.getLogger().warning("Uninitialized fast current thread lookup for " + Thread.currentThread());
            result = context.getGuestThreadFromHost(Thread.currentThread());
            if (result != null) {
                setCurrentPlatformThread(result);
            }
            return result;
        }
        return result;
    }

    public StaticObject getCurrentVirtualThread() {
        assert currentVirtualThread != null;
        return currentVirtualThread;
    }

    public ClassRegistry.TypeStack getTypeStack() {
        return typeStack;
    }

    public VM.PrivilegedStack getPrivilegedStack() {
        return privilegedStack;
    }

    public void setSteppingInProgress(boolean value) {
        stepInProgress = value;
    }

    public boolean isSteppingInProgress() {
        return stepInProgress;
    }

    public boolean disableSingleStepping(boolean forceDisable) {
        if (forceDisable || stepInProgress) {
            singleSteppingDisabledCounter++;
            return true;
        }
        return false;
    }

    public void enableSingleStepping() {
        assert singleSteppingDisabledCounter > 0;
        singleSteppingDisabledCounter--;
    }

    public boolean isSteppingDisabled() {
        return singleSteppingDisabledCounter > 0;
    }

    /**
     * Prevents {@code Continuation.SuspendCapability.suspend()} being called, typically because
     * there is something on the stack that we can't unwind through.
     */
    public void blockContinuationSuspension() {
        assert suspensionBlocks < Integer.MAX_VALUE;
        suspensionBlocks++;
    }

    public void unblockContinuationSuspension() {
        assert suspensionBlocks > 0;
        suspensionBlocks--;
    }

    public ContinuationScope continuationScope() {
        return new ContinuationScope();
    }

    public boolean isInContinuation() {
        return inContinuation;
    }

    public final class ContinuationScope implements AutoCloseable {
        private final int startBlocks;

        private ContinuationScope() {
            startBlocks = suspensionBlocks;
            suspensionBlocks = 0;
            assert !inContinuation;
            inContinuation = true;
        }

        @Override
        public void close() {
            suspensionBlocks = startBlocks;
            inContinuation = false;
        }
    }

    public boolean isContinuationSuspensionBlocked() {
        // Why one and not zero here? Because the fact we reached here means we're inside the
        // suspend intrinsic, and we don't want to consider that as blocking the suspend.
        return suspensionBlocks > 1;
    }

    public TransformerScope transformerScope() {
        return new TransformerScope();
    }

    public boolean isInTransformer() {
        return inTransformer;
    }

    public final class TransformerScope implements AutoCloseable {

        private TransformerScope() {
            inTransformer = true;
        }

        @Override
        public void close() {
            inTransformer = false;
        }
    }

    // Helper methods for assertions
    private boolean diagnose(StaticObject t) {
        // @formatter:off
        // Ensure we work from the thread used for this local creation.
        assert Thread.currentThread() == hostThread.get() : //
                "Initializing current thread in EspressoThreadLocalState for a different thread than current:\n" +
                "    Current: " + Thread.currentThread() + "\n" +
                "    Registered: " + hostThread.get();

        // Ensure the registered guest thread is and stays linked to the corresponding host thread.
        if (currentPlatformThread != null) {
            assert getHost(currentPlatformThread) == Thread.currentThread() : //
                    "Registered platform thread not associated with current host thread.";
        }

        // Ensure we are consistently registering guest threads.
        if (t != null) {
            // The thread we are registering is linked to the current thread.
            assert getHost(t) == Thread.currentThread() : //
                    "Current thread fast access set by non-current thread";

            // Ensure we are not registering multiple guest threads for the same host thread.
            assert currentPlatformThread == null || currentPlatformThread == t : //
                    /*- Report these threads names */
                    getHost(currentPlatformThread).getName() + " vs " + getHost(t).getName() + "\n" +
                    /*- Report these threads identities */
                    "Guest identities" + System.identityHashCode(currentPlatformThread) + " vs " + System.identityHashCode(t) + "\n" +
                    /*- Checks if our host threads are actually different, or if it is simply a renamed one. */
                    "Host identities: " + System.identityHashCode(getHost(currentPlatformThread)) + " vs " + System.identityHashCode(getHost(t));
        }
        // @formatter:on
        /*-
         * Current theory for GR-50089:
         *
         * For some reason, when creating a new guest thread, instead of spawning a new host thread
         * Truffle gives us back a previously created one that had completed.
         *
         * If that is the case, then, on failure, we will see in the report:
         * - Different guest names and/or guest identities
         * - Same host identities
         *
         * This may be solved by unregistering a guest thread from the thread local state in
         * ThreadAccess.terminate().
         */
        return true;
    }

    private static Thread getHost(StaticObject t) {
        assert t != null && StaticObject.notNull(t);
        return t.getKlass().getContext().getThreadAccess().getHost(t);
    }
}
