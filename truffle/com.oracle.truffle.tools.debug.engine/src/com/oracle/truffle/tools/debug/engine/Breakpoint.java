/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tools.debug.engine;

import com.oracle.truffle.api.instrument.*;
import com.oracle.truffle.api.source.*;

public abstract class Breakpoint {

    /**
     * A general model of the states occupied by a breakpoint during its lifetime.
     */
    public enum BreakpointState {

        /**
         * Not attached, enabled.
         * <p>
         * Created for a source location but not yet attached: perhaps just created and the source
         * hasn't been loaded yet; perhaps source has been loaded, but the line location isn't
         * probed so a breakpoint cannot be attached. Can be either enabled or disabled.
         */
        ENABLED_UNRESOLVED("Enabled/Unresolved"),

        /**
         * Not attached, disabled.
         * <p>
         * Created for a source location but not yet attached: perhaps just created and the source
         * hasn't been loaded yet; perhaps source has been loaded, but the line location isn't
         * probed so a breakpoint cannot be attached.
         */
        DISABLED_UNRESOLVED("Disabled/Unresolved"),

        /**
         * Attached, instrument enabled.
         * <p>
         * Is currently implemented by some {@link Instrument}, which is attached to a {@link Probe}
         * at a specific node in the AST, and the breakpoint is enabled.
         */
        ENABLED("Enabled"),

        /**
         * Attached, instrument disabled.
         * <p>
         * Is currently implemented by some {@link Instrument}, which is attached to a {@link Probe}
         * at a specific node in the AST, and the breakpoint is disabled.
         */
        DISABLED("Disabled"),

        /**
         * Not attached, instrument is permanently disabled.
         */
        DISPOSED("Disposed");

        private final String name;

        BreakpointState(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        @Override
        public String toString() {
            return name;
        }

    }

    private static int nextBreakpointId = 0;

    private final int id;
    private final int groupId;
    private final boolean isOneShot;

    private int ignoreCount;

    private int hitCount = 0;

    private BreakpointState state;

    Breakpoint(BreakpointState state, int groupId, int ignoreCount, boolean isOneShot) {
        this.state = state;
        this.id = nextBreakpointId++;
        this.groupId = groupId;
        this.isOneShot = isOneShot;
        this.ignoreCount = ignoreCount;
    }

    /**
     * Unique ID.
     */
    public final int getId() {
        return id;
    }

    /**
     * Group ID, set when created.
     */
    public final int getGroupId() {
        return groupId;
    }

    /**
     * Enables or disables this breakpoint's AST instrumentation. The breakpoint is enabled by
     * default.
     *
     * @param enabled <code>true</code> to activate the instrumentation, <code>false</code> to
     *            deactivate the instrumentation so that it has no effect.
     */
    public abstract void setEnabled(boolean enabled);

    /**
     * Is this breakpoint active?
     */
    public abstract boolean isEnabled();

    /**
     * Sets the condition on this breakpoint, {@code null} to make it unconditional.
     *
     * @param expr if non{@code -null}, a boolean expression, expressed in the guest language, to be
     *            evaluated in the lexical context at the breakpoint location.
     * @throws DebugException if condition is invalid
     * @throws UnsupportedOperationException if the breakpoint does not support conditions
     */
    public abstract void setCondition(String expr) throws DebugException;

    /**
     * Gets the string, expressed in the Guest Language, that defines the current condition on this
     * breakpoint; {@code null} if this breakpoint is currently unconditional.
     */
    public String getCondition() {
        return null;
    }

    /**
     * Does this breakpoint remove itself after first activation?
     */
    public final boolean isOneShot() {
        return isOneShot;
    }

    /**
     * Gets the number of hits left to be ignored before halting.
     */
    public final int getIgnoreCount() {
        return ignoreCount;
    }

    /**
     * Change the threshold for when this breakpoint should start causing a break. When both an
     * ignore count and a {@linkplain #setCondition(String) condition} are specified, the condition
     * is evaluated first: if {@code false} it is not considered to be a hit. In other words, the
     * ignore count is for successful conditions only.
     */
    public final void setIgnoreCount(int ignoreCount) {
        this.ignoreCount = ignoreCount;
    }

    /**
     * Number of times this breakpoint has reached, with one exception; if the breakpoint has a
     * condition that evaluates to {@code false}, it does not count as a hit.
     */
    public final int getHitCount() {
        return hitCount;
    }

    /**
     * Disables this breakpoint and removes any associated instrumentation; it becomes permanently
     * inert.
     */
    public abstract void dispose();

    /**
     * Gets a human-sensible description of this breakpoint's location in a {@link Source}.
     */
    public abstract String getLocationDescription();

    public final BreakpointState getState() {
        return state;
    }

    final void assertState(BreakpointState s) {
        assert state == s;
    }

    final void setState(BreakpointState state) {
        this.state = state;
    }

    /**
     * Assumes that all conditions for causing the break have been satisfied, so increments the
     * <em>hit count</em>. Then checks if the <em>ignore count</em> has been exceeded, and if so
     * returns {@code true}. If not, it still counts as a <em>hit</em> but should be ignored.
     *
     * @return whether to proceed
     */
    final boolean incrHitCountCheckIgnore() {
        return ++hitCount > ignoreCount;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder(getClass().getSimpleName());
        sb.append(" state=");
        sb.append(getState() == null ? "<none>" : getState().getName());
        if (isOneShot()) {
            sb.append(", " + "One-Shot");
        }
        if (getCondition() != null) {
            sb.append(", condition=\"" + getCondition() + "\"");
        }
        return sb.toString();
    }
}
