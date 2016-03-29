/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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
 * A <em>binding</em> between:
 * <ol>
 * <li>Some source of <em>execution events</em> in an executing Truffle AST, and</li>
 * <li>A <em>listener</em>: a consumer of execution events on behalf of an external client.
 * </ol>
 * <p>
 * Client-oriented documentation for the use of Instruments is available online at <a
 * HREF="https://wiki.openjdk.java.net/display/Graal/Listening+for+Execution+Events" >https://
 * wiki.openjdk.java.net/display/Graal/Listening+for+Execution+Events</a>
 *
 * @see Instrumenter
 * @since 0.8 or earlier
 */
public abstract class Instrument {

    /** Optional string for debugging. */
    private final String instrumentInfo;

    private boolean isDisposed = false;

    Instrument(String instrumentInfo) {
        this.instrumentInfo = instrumentInfo;
    }

    /**
     * Detaches this from its source of execution events and makes itself unusable.
     *
     * @throws IllegalStateException if this has already been disposed
     * @since 0.8 or earlier
     */
    public void dispose() throws IllegalStateException {
        if (isDisposed) {
            throw new IllegalStateException("Istruments only dispose once");
        }
        innerDispose();
        this.isDisposed = true;
    }

    /**
     * Has this been detached from its source of execution events?
     * 
     * @since 0.8 or earlier
     */
    public boolean isDisposed() {
        return isDisposed;
    }

    /** @since 0.8 or earlier */
    public final String getInstrumentInfo() {
        return instrumentInfo;
    }

    abstract void innerDispose();

}
