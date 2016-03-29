/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.debug;

import java.io.IOException;

import com.oracle.truffle.api.source.Source;

/**
 * Breakpoint in a {@link com.oracle.truffle.api.vm.PolyglotEngine} with
 * {@link com.oracle.truffle.api.debug debugging turned on}. You can request an instance of
 * breakpoint by calling
 * {@link Debugger#setLineBreakpoint(int, com.oracle.truffle.api.source.LineLocation, boolean)} or
 * other methods in the {@link Debugger} class.
 * 
 * @since 0.9
 */
@SuppressWarnings("javadoc")
public abstract class Breakpoint {

    /**
     * A general model of the states occupied by a {@link Breakpoint} during its lifetime.
     * 
     * @since 0.9
     */
    public enum State {

        /**
         * No matching source locations have been identified, but it is enables so that the
         * breakpoint will become active when any matching source locations appear.
         */
        ENABLED_UNRESOLVED("Enabled/Unresolved"),

        /**
         * No matching source locations have been identified, and it is disabled. The breakpoint
         * will become associated with any matching source locations that appear, but will not
         * become active until explicitly enabled.
         */
        DISABLED_UNRESOLVED("Disabled/Unresolved"),

        /**
         * Matching source locations have been identified and the breakpoint is active at them.
         */
        ENABLED("Enabled"),

        /**
         * Matching source locations have been identified, but he breakpoint is disabled. It will
         * not be active until explicitly enabled.
         */
        DISABLED("Disabled"),

        /**
         * The breakpoint is permanently inactive.
         */
        DISPOSED("Disposed");

        private final String name;

        State(String name) {
            this.name = name;
        }

        /** @since 0.9 */
        public String getName() {
            return name;
        }

        /** @since 0.9 */
        @Override
        public String toString() {
            return name;
        }

    }

    Breakpoint() {
    }

    /**
     * Gets current state of the breakpoint.
     * 
     * @since 0.9
     */
    public abstract State getState();

    /**
     * Enables/disables this breakpoint; enabled by default.
     *
     * @param enabled <code>true</code> to activate the breakpoint, <code>false</code> to deactivate
     *            it so that it has no effect.
     * @since 0.9
     */
    public abstract void setEnabled(boolean enabled);

    /**
     * Is this breakpoint active?
     * 
     * @since 0.9
     */
    public abstract boolean isEnabled();

    /**
     * Sets the condition on this breakpoint, {@code null} to make it unconditional.
     *
     * @param expr if non{@code -null}, a boolean expression, expressed in the guest language, to be
     *            evaluated in the lexical context at the breakpoint location.
     * @throws IOException if condition is invalid
     * @throws UnsupportedOperationException if the breakpoint does not support conditions
     * @since 0.9
     */
    public abstract void setCondition(String expr) throws IOException;

    /**
     * Gets the text that defines the current condition on this breakpoint; {@code null} if this
     * breakpoint is currently unconditional.
     * 
     * @since 0.9
     */
    public abstract Source getCondition();

    /**
     * Does this breakpoint remove itself after first activation?
     * 
     * @since 0.9
     */
    public abstract boolean isOneShot();

    /**
     * Gets the number of hits left to be ignored before halting.
     * 
     * @since 0.9
     */
    public abstract int getIgnoreCount();

    /**
     * Change the threshold for when this breakpoint should start causing a break. When both an
     * ignore count and a {@linkplain #setCondition(String) condition} are specified, the condition
     * is evaluated first: if {@code false} it is not considered to be a hit. In other words, the
     * ignore count is for successful conditions only.
     * 
     * @since 0.9
     */
    public abstract void setIgnoreCount(int ignoreCount);

    /**
     * Number of times this breakpoint has reached, with one exception; if the breakpoint has a
     * condition that evaluates to {@code false}, it does not count as a hit.
     * 
     * @since 0.9
     */
    public abstract int getHitCount();

    /**
     * Disables this breakpoint and removes any associated instrumentation; it becomes permanently
     * inert.
     * 
     * @since 0.9
     */
    public abstract void dispose();

    /**
     * Gets a human-sensible description of this breakpoint's location in a {@link Source}.
     * 
     * @since 0.9
     */
    public abstract String getLocationDescription();
}
