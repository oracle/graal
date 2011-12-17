/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.max.graal.compiler.alloc;

import com.oracle.max.criutils.*;
import com.oracle.max.graal.compiler.*;
import com.oracle.max.graal.compiler.alloc.Interval.RegisterBinding;
import com.oracle.max.graal.compiler.alloc.Interval.RegisterBindingLists;
import com.oracle.max.graal.compiler.alloc.Interval.State;

/**
 */
public class IntervalWalker {

    protected final GraalCompilation compilation;
    protected final LinearScan allocator;

    /**
     * Sorted list of intervals, not live before the current position.
     */
    RegisterBindingLists unhandledLists;

    /**
     * Sorted list of intervals, live at the current position.
     */
    RegisterBindingLists activeLists;

    /**
     * Sorted list of intervals in a life time hole at the current position.
     */
    RegisterBindingLists inactiveLists;

    /**
     * The current interval (taken from the unhandled list) being processed.
     */
    protected Interval current;

    /**
     * The current position (intercept point through the intervals).
     */
    protected int currentPosition;

    /**
     * The binding of the current interval being processed.
     */
    protected RegisterBinding currentBinding;

    /**
     * Processes the {@linkplain #current} interval in an attempt to allocate a physical
     * register to it and thus allow it to be moved to a list of {@linkplain #activeLists active} intervals.
     *
     * @return {@code true} if a register was allocated to the {@linkplain #current} interval
     */
    boolean activateCurrent() {
        return true;
    }

    void walkBefore(int lirOpId) {
        walkTo(lirOpId - 1);
    }

    void walk() {
        walkTo(Integer.MAX_VALUE);
    }

    /**
     * Creates a new interval walker.
     *
     * @param allocator the register allocator context
     * @param unhandledFixed the list of unhandled {@linkplain RegisterBinding#Fixed fixed} intervals
     * @param unhandledAny the list of unhandled {@linkplain RegisterBinding#Any non-fixed} intervals
     */
    IntervalWalker(LinearScan allocator, Interval unhandledFixed, Interval unhandledAny) {
        this.compilation = allocator.compilation;
        this.allocator = allocator;

        unhandledLists = new RegisterBindingLists(unhandledFixed, unhandledAny);
        activeLists = new RegisterBindingLists(Interval.EndMarker, Interval.EndMarker);
        inactiveLists = new RegisterBindingLists(Interval.EndMarker, Interval.EndMarker);
        currentPosition = -1;
        current = null;
        nextInterval();
    }

    void removeFromList(Interval interval) {
        if (interval.state == State.Active) {
            activeLists.remove(RegisterBinding.Any, interval);
        } else {
            assert interval.state == State.Inactive : "invalid state";
            inactiveLists.remove(RegisterBinding.Any, interval);
        }
    }

    void walkTo(State state, int from) {
        assert state == State.Active || state == State.Inactive : "wrong state";
        for (RegisterBinding binding : RegisterBinding.VALUES) {
            Interval prevprev = null;
            Interval prev = (state == State.Active) ? activeLists.get(binding) : inactiveLists.get(binding);
            Interval next = prev;
            while (next.currentFrom() <= from) {
                Interval cur = next;
                next = cur.next;

                boolean rangeHasChanged = false;
                while (cur.currentTo() <= from) {
                    cur.nextRange();
                    rangeHasChanged = true;
                }

                // also handle move from inactive list to active list
                rangeHasChanged = rangeHasChanged || (state == State.Inactive && cur.currentFrom() <= from);

                if (rangeHasChanged) {
                    // remove cur from list
                    if (prevprev == null) {
                        if (state == State.Active) {
                            activeLists.set(binding, next);
                        } else {
                            inactiveLists.set(binding, next);
                        }
                    } else {
                        prevprev.next = next;
                    }
                    prev = next;
                    if (cur.currentAtEnd()) {
                        // move to handled state (not maintained as a list)
                        cur.state = State.Handled;
                        intervalMoved(cur, binding, state, State.Handled);
                    } else if (cur.currentFrom() <= from) {
                        // sort into active list
                        activeLists.addToListSortedByCurrentFromPositions(binding, cur);
                        cur.state = State.Active;
                        if (prev == cur) {
                            assert state == State.Active : "check";
                            prevprev = prev;
                            prev = cur.next;
                        }
                        intervalMoved(cur, binding, state, State.Active);
                    } else {
                        // sort into inactive list
                        inactiveLists.addToListSortedByCurrentFromPositions(binding, cur);
                        cur.state = State.Inactive;
                        if (prev == cur) {
                            assert state == State.Inactive : "check";
                            prevprev = prev;
                            prev = cur.next;
                        }
                        intervalMoved(cur, binding, state, State.Inactive);
                    }
                } else {
                    prevprev = prev;
                    prev = cur.next;
                }
            }
        }
    }

    void nextInterval() {
        RegisterBinding binding;
        Interval any = unhandledLists.any;
        Interval fixed = unhandledLists.fixed;

        if (any != Interval.EndMarker) {
            // intervals may start at same position . prefer fixed interval
            binding = fixed != Interval.EndMarker && fixed.from() <= any.from() ? RegisterBinding.Fixed : RegisterBinding.Any;

            assert binding == RegisterBinding.Fixed && fixed.from() <= any.from() || binding == RegisterBinding.Any && any.from() <= fixed.from() : "wrong interval!!!";
            assert any == Interval.EndMarker || fixed == Interval.EndMarker || any.from() != fixed.from() || binding == RegisterBinding.Fixed : "if fixed and any-Interval start at same position, fixed must be processed first";

        } else if (fixed != Interval.EndMarker) {
            binding = RegisterBinding.Fixed;
        } else {
            current = null;
            return;
        }
        currentBinding = binding;
        current = unhandledLists.get(binding);
        unhandledLists.set(binding, current.next);
        current.next = Interval.EndMarker;
        current.rewindRange();
    }

    void walkTo(int toOpId) {
        assert currentPosition <= toOpId : "can not walk backwards";
        while (current != null) {
            boolean isActive = current.from() <= toOpId;
            int opId = isActive ? current.from() : toOpId;

            if (GraalOptions.TraceLinearScanLevel >= 2 && !TTY.isSuppressed()) {
                if (currentPosition < opId) {
                    TTY.println();
                    TTY.println("walkTo(%d) *", opId);
                }
            }

            // set currentPosition prior to call of walkTo
            currentPosition = opId;

            // call walkTo even if currentPosition == id
            walkTo(State.Active, opId);
            walkTo(State.Inactive, opId);

            if (isActive) {
                current.state = State.Active;
                if (activateCurrent()) {
                    activeLists.addToListSortedByCurrentFromPositions(currentBinding, current);
                    intervalMoved(current, currentBinding, State.Unhandled, State.Active);
                }

                nextInterval();
            } else {
                return;
            }
        }
    }

    private void intervalMoved(Interval interval, RegisterBinding kind, State from, State to) {
        // intervalMoved() is called whenever an interval moves from one interval list to another.
        // In the implementation of this method it is prohibited to move the interval to any list.
        if (GraalOptions.TraceLinearScanLevel >= 4 && !TTY.isSuppressed()) {
            TTY.print(from.toString() + " to " + to.toString());
            TTY.fillTo(23);
            TTY.out().println(interval.logString(allocator));
        }
    }
}
