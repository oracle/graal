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

import com.oracle.truffle.api.source.*;

/**
 * A receiver of Truffle execution events that can act on behalf of an external client.
 * <p>
 * The {@link Probe} instance provides access to the {@link SourceSection} associated with the
 * event, as well as any {@link SyntaxTag}s that have been applied at that program's location.
 * <p>
 * This is the simplest kind of listener, suitable for clients that need no other information about
 * the program's execution state at the time of the event. Clients that require access to the AST
 * execution state should use {@link StandardInstrumentListener}.
 * <p>
 * Clients are free, of course, to record additional information in the listener implementation that
 * carries additional information about the context and reason for the particular {@link Instrument}
 * that is to be created from the listener.
 */
public interface SimpleInstrumentListener {

    /**
     * Receive notification that a program location is about to be executed.
     * <p>
     * <strong>Synchronous</strong>: Truffle execution waits until the call returns.
     */
    void enter(Probe probe);

    /**
     * Receive notification that a program location's {@code void}-valued execution has just
     * completed.
     * <p>
     * <strong>Synchronous</strong>: Truffle execution waits until the call returns.
     */
    void returnVoid(Probe probe);

    /**
     * Receive notification that a program location's execution has just completed and returned a
     * value (boxed if primitive).
     * <p>
     * <strong>Synchronous</strong>: Truffle execution waits until the call returns.
     */
    void returnValue(Probe probe, Object result);

    /**
     * Receive notification that a program location's execution has just thrown an exception.
     * <p>
     * <strong>Synchronous</strong>: Truffle execution waits until the call returns.
     */
    void returnExceptional(Probe probe, Exception exception);
}
