/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.api;

import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.impl.Accessor.EngineSupport;
import com.oracle.truffle.api.impl.ThreadLocalHandshake;
import com.oracle.truffle.api.nodes.LoopNode;
import com.oracle.truffle.api.nodes.Node;

public abstract class TruffleSafepoint {

    private static final ThreadLocalHandshake HANDSHAKE = LanguageAccessor.ACCESSOR.runtimeSupport().getThreadLocalHandshake();
    public static final Interruptable THREAD_INTERRUPTION = new Interruptable() {

        @Override
        public void interrupted() {
            Thread.interrupted();
        }

        @Override
        public void interrupt(Thread t) {
            t.interrupt();
        }
    };

    protected TruffleSafepoint(EngineSupport support) {
        if (support == null) {
            throw new AssertionError("Only runtime is allowed create truffle safepoint instances.");
        }
    }

    /**
     * Allows to run thread local execution at this location in the interpreter. Guest language
     * implementations must call this method repeatedly within a constant amount of time. Any guest
     * language call {@link CallTarget} or loop iteration with {@link LoopNode} automatically
     * notifies this method. In compiled code paths invocations of this method are ignored and the
     * compiler may insert its own guest language safepoint locations, typically method exits and
     * loop ends.
     *
     * @see Env#runThreadLocalAsynchronous(Thread[], java.util.function.Consumer)
     * @see Env#runThreadLocalSynchronous(Thread[], java.util.function.Consumer)
     *
     * @since 21.1
     */
    public static void poll(Node node) {
        HANDSHAKE.poll(node);
    }

    /**
     * Cooperative blocking.
     *
     * <pre>
     * TruffleSafepoint safepoint = TruffleSafepoint.getCurrent();
     * Interruptable prev = safepoint.setBlocked(Interrruptable.THREAD_INTERRUPTABLE);
     * try {
     *     while (true) {
     *         try {
     *             lock.lockInterruptibly();
     *             break;
     *         } catch (InterruptedException e) {
     *             CompilerDirectives.safepoint();
     *         }
     *     }
     * } finally {
     *     safepoint.setBlocked(prev);
     * }
     * </pre>
     *
     * @param unblockingAction
     */
    public abstract Interruptable setBlocked(Interruptable interruptable);

    /**
     * Allows to temporarily disables thread location notifications on this thread. This method may
     * be used to determine critical sections in the guest language application during which the
     * execution must not be interrupted or exited. The guest language implementation must make sure
     * that safepoints are only disabled for a constant amount of time. It is required to disable
     * safepoints only for as little time as possible as during this synchronous thread local
     * notification needs to wait. Because of these restrictions disabling safepoints must not be
     * exposed to guest applications. Exceptions may be made for known trusted guest code parts,
     * like lock implementations.
     * <p>
     * Example usage:
     *
     * <pre>
     * TruffleSafepoint safepoint = TruffleSafepoint.getCurrent();
     * boolean prev = safepoint.setEnabled(false);
     * try {
     *     // criticial section
     * } finally {
     *     safepoint.setEnabled(prev);
     * }
     * </pre>
     *
     *
     * @see Env#runThreadLocalAsynchronous(Thread[], java.util.function.Consumer)
     * @see Env#runThreadLocalSynchronous(Thread[], java.util.function.Consumer)
     * @see #safepoint()
     *
     * @since 21.1
     */
    public abstract boolean setEnabled(boolean enabled);

    /**
     * Returns the current safepoint configuration for this thread.
     *
     * @since 21.1
     */
    public static TruffleSafepoint getCurrent() {
        return HANDSHAKE.getCurrent();
    }

    public interface Interruptable {

        /**
         * Interrupts a thread. Must not run guest code. A lock is held while executing, should not
         * acquire other locks.
         *
         * @param t
         */
        void interrupt(Thread t);

        /**
         * Will be invoked when a safepoint was called during unblocking.
         */
        void interrupted();

    }

}
