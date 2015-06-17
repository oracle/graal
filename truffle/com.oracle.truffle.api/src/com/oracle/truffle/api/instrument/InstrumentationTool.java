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
package com.oracle.truffle.api.instrument;

/**
 * {@linkplain Instrument Instrumentation}-based tools that gather data during Guest Language
 * program execution.
 * <p>
 * Tools share a common <em>life cycle</em>:
 * <ul>
 * <li>A newly created tool is inert until {@linkplain #install() installed}.</li>
 * <li>An installed tool becomes <em>enabled</em> and immediately begins installing
 * {@linkplain Instrument instrumentation} on subsequently created ASTs and collecting data from
 * those instruments</li>
 * <li>A tool may only be installed once.</li>
 * <li>It should be possible to install multiple instances of a tool, possibly (but not necessarily)
 * configured differently with respect to what data is being collected.</li>
 * <li>Once installed, a tool can be {@linkplain #setEnabled(boolean) enabled and disabled}
 * arbitrarily.</li>
 * <li>A disabled tool:
 * <ul>
 * <li>Collects no data;</li>
 * <li>Retains existing AST instrumentation;</li>
 * <li>Continues to instrument newly created ASTs; and</li>
 * <li>Retains previously collected data.</li>
 * </ul>
 * </li>
 * <li>An installed tool may be {@linkplain #reset() reset} at any time, which leaves the tool
 * installed but with all previously collected data removed.</li>
 * <li>A {@linkplain #dispose() disposed} tool removes all instrumentation (but not
 * {@linkplain Probe probes}) and becomes permanently disabled; previously collected data persists.</li>
 * </ul>
 * <p>
 * Tool-specific methods that access data collected by the tool should:
 * <ul>
 * <li>Return modification-safe representations of the data; and</li>
 * <li>Not change the state of the data.</li>
 * </ul>
 * <b>Note:</b><br>
 * Tool installation is currently <em>global</em> to the Truffle Execution environment. When
 * language-agnostic management of individual execution environments is added to the platform,
 * installation will be (optionally) specific to a single execution environment.
 */
public abstract class InstrumentationTool {
    // TODO (mlvdv) still thinking about the most appropriate name for this class of tools

    private enum ToolState {

        /** Not yet installed, inert. */
        UNINSTALLED,

        /** Installed, collecting data. */
        ENABLED,

        /** Installed, not collecting data. */
        DISABLED,

        /** Was installed, but now removed, inactive, and no longer usable. */
        DISPOSED;
    }

    private ToolState toolState = ToolState.UNINSTALLED;

    protected InstrumentationTool() {
    }

    /**
     * Connect the tool to some part of the Truffle runtime, and enable data collection to start.
     * Instrumentation will only be added to subsequently created ASTs.
     *
     * @throws IllegalStateException if the tool has previously been installed.
     */
    public final void install() {
        checkUninstalled();
        if (internalInstall()) {
            toolState = ToolState.ENABLED;
        }
    }

    /**
     * @return whether the tool is currently collecting data.
     */
    public final boolean isEnabled() {
        return toolState == ToolState.ENABLED;
    }

    /**
     * Switches tool state between <em>enabled</em> (collecting data) and <em>disabled</em> (not
     * collecting data, but keeping data already collected).
     *
     * @throws IllegalStateException if not yet installed or disposed.
     */
    public final void setEnabled(boolean isEnabled) {
        checkInstalled();
        internalSetEnabled(isEnabled);
        toolState = isEnabled ? ToolState.ENABLED : ToolState.DISABLED;
    }

    /**
     * Clears any data already collected, but otherwise does not change the state of the tool.
     *
     * @throws IllegalStateException if not yet installed or disposed.
     */
    public final void reset() {
        checkInstalled();
        internalReset();
    }

    /**
     * Makes the tool permanently <em>disabled</em>, removes instrumentation, but keeps data already
     * collected.
     *
     * @throws IllegalStateException if not yet installed or disposed.
     */
    public final void dispose() {
        checkInstalled();
        internalDispose();
        toolState = ToolState.DISPOSED;
    }

    /**
     * @return whether the installation succeeded.
     */
    protected abstract boolean internalInstall();

    /**
     * No subclass action required.
     *
     * @param isEnabled
     */
    protected void internalSetEnabled(boolean isEnabled) {
    }

    protected abstract void internalReset();

    protected abstract void internalDispose();

    /**
     * Ensure that the tool is currently installed.
     *
     * @throws IllegalStateException
     */
    private void checkInstalled() throws IllegalStateException {
        if (toolState == ToolState.UNINSTALLED) {
            throw new IllegalStateException("Tool " + getClass().getSimpleName() + " not yet installed");
        }
        if (toolState == ToolState.DISPOSED) {
            throw new IllegalStateException("Tool " + getClass().getSimpleName() + " has been disposed");
        }
    }

    /**
     * Ensure that the tool has not yet been installed.
     *
     * @throws IllegalStateException
     */
    private void checkUninstalled() {
        if (toolState != ToolState.UNINSTALLED) {
            throw new IllegalStateException("Tool " + getClass().getSimpleName() + " has already been installed");
        }
    }

}
