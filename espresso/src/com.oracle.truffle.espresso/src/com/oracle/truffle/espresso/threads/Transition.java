/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.threads;

import static com.oracle.truffle.espresso.threads.Transition.NoTransition.NO_TRANSITION;

import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.impl.ContextAccess;
import com.oracle.truffle.espresso.nodes.EspressoNode;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.EspressoThreadLocalState;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;

/**
 * Represents thread state transitions in Espresso.
 * <p>
 * Use in try-finally blocks as follows:
 *
 * <pre>
 * {@code
 * Transition transition = Transition.transition(state, node);
 * try {
 *     // ...
 * } finally {
 *     transition.restore(node);
 * }
 * }
 * </pre>
 * <p>
 * Prefer using the methods that takes a node as input when available.
 * <p>
 * Transitions and restores will eagerly
 * {@link EspressoThreadLocalState#blockContinuationSuspension() block} and
 * {@link EspressoThreadLocalState#unblockContinuationSuspension() unblock} continuation suspension
 * respectively.
 *
 * @see #transition(ThreadState, EspressoNode)
 * @see #restore(EspressoNode)
 */
public abstract class Transition {
    private static final EspressoNode NO_LOCATION = new EspressoNode() {
        @Override
        public boolean isAdoptable() {
            return false;
        }
    };

    /**
     * Implements transition of a thread state (ie: Runnable, Waiting, etc...). Use this method when
     * a precise {@link EspressoNode node} is available.
     *
     * @see ThreadState
     */
    public static Transition transition(ThreadState state, EspressoNode location) {
        return transition(EspressoContext.get(location), EspressoLanguage.get(location), state, location);
    }

    /**
     * Restores the previous state of the thread on which this transition was performed. Use this
     * method when a precise {@link EspressoNode node} is available.
     */
    public final void restore(EspressoNode location) {
        restore(EspressoContext.get(location), EspressoLanguage.get(location), location);
    }

    /**
     * Implements transition of a thread state (ie: Runnable, Waiting, etc...). Use this method when
     * no node is available.
     *
     * @see ThreadState
     */
    public static Transition transition(ThreadState state, ContextAccess access) {
        return transition(access.getContext(), access.getLanguage(), state, NO_LOCATION);
    }

    /**
     * Implements transition of a thread state (ie: Runnable, Waiting, etc...). Use this method when
     * no node is available.
     *
     * @see ThreadState
     */
    public static Transition transition(ThreadState state, EspressoContext ctx) {
        return transition(ctx, ctx.getLanguage(), state, NO_LOCATION);
    }

    /**
     * Implements transition of a thread state (ie: Runnable, Waiting, etc...). Use this method when
     * neither a node nor a context is easily obtainable.
     *
     * @see ThreadState
     */
    public static Transition transition(ThreadState state) {
        return transition(state, NO_LOCATION);
    }

    /**
     * Restores the previous state of the thread on which this transition was performed. Use this
     * method when no node is available.
     */
    public final void restore(ContextAccess access) {
        restore(access.getContext(), access.getLanguage(), NO_LOCATION);
    }

    /**
     * Restores the previous state of the thread on which this transition was performed. Use this
     * method when no node is available.
     */
    public final void restore(EspressoContext ctx) {
        restore(ctx, ctx.getLanguage(), NO_LOCATION);
    }

    /**
     * Restores the previous state of the thread on which this transition was performed. Use this
     * method when neither a node nor a context is easily obtainable.
     */
    public final void restore() {
        restore(NO_LOCATION);
    }

    // Internals

    private static Transition transition(EspressoContext context, EspressoLanguage language, ThreadState state, EspressoNode location) {
        language.getThreadLocalState().blockContinuationSuspension();
        StaticObject currentThread = context.getThreadAccess().readyForTransitions();
        if (currentThread == null) {
            return NO_TRANSITION;
        }
        int oldState = context.getThreadAccess().getState(currentThread);
        int toState = state.from(oldState);
        if (oldState == toState) {
            return NO_TRANSITION;
        }
        return new TransitionImpl(oldState, currentThread, toState).perform(context, location);
    }

    private void restore(EspressoContext context, EspressoLanguage language, EspressoNode location) {
        language.getThreadLocalState().unblockContinuationSuspension();
        doRestore(context, location);
    }

    abstract void doRestore(EspressoContext context, EspressoNode location);

    private static final class TransitionImpl extends Transition {
        private final int outerState;
        private final int innerState;
        private final StaticObject thread;

        TransitionImpl(int old, StaticObject thread, int to) {
            this.thread = thread;
            this.outerState = old;
            this.innerState = to;
        }

        Transition perform(EspressoContext context, EspressoNode location) {
            context.getThreadAccess().transition(thread, outerState, innerState, location);
            return this;
        }

        @Override
        void doRestore(EspressoContext context, EspressoNode location) {
            context.getThreadAccess().restoreState(thread, outerState, innerState, location);
        }
    }

    static final class NoTransition extends Transition {
        static final Transition NO_TRANSITION = new NoTransition();

        @Override
        void doRestore(EspressoContext context, EspressoNode location) {
        }
    }
}
