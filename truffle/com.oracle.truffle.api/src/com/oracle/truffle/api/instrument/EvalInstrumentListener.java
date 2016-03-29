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

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.Source;

/**
 * Listener for receiving the result a client-provided Guest Language expression
 * {@linkplain Instrumenter#attach(Probe, Class, Source, EvalInstrumentListener, String) attached}
 * to a {@link Probe}.
 * <p>
 * Notification is fully synchronous, so method bodies have performance implications. Non-trivial
 * methods should be coded with Truffle guidelines and cautions in mind.
 *
 * @see Instrumenter
 * @since 0.8 or earlier
 */
public interface EvalInstrumentListener {

    /**
     * Notifies listener that a client-provided Guest Language expression
     * {@linkplain Instrumenter#attach(Probe, Class, Source, EvalInstrumentListener, String)
     * attached} to a {@link Probe} has just been executed with the specified result, possibly
     * {@code null}.
     * <p>
     * <strong>Note: </strong> Truffle will attempt to optimize implementations through partial
     * evaluation; annotate with {@link TruffleBoundary} if this should not be permitted.
     *
     * @param node the guest language AST node at which the expression was evaluated
     * @param frame execution frame at the guest-language AST node
     * @param result expression evaluation
     * @since 0.8 or earlier
     */
    void onExecution(Node node, VirtualFrame frame, Object result);

    /**
     * Notifies listener that a client-provided Guest Language expression
     * {@linkplain Instrumenter#attach(Probe, Class, Source, EvalInstrumentListener, String)
     * attached} to a {@link Probe} has just been executed and generated an exception. The exception
     * does not affect Guest language evaluation; the only report is to listeners implementing this
     * methods.
     * <p>
     * <strong>Note: </strong> Truffle will attempt to optimize implementations through partial
     * evaluation; annotate with {@link TruffleBoundary} if this should not be permitted.
     *
     * @param node the guest-language AST node to which the host Instrument's {@link Probe} is
     *            attached
     * @param frame execution frame at the guest-language AST node
     * @param ex the exception
     * @since 0.8 or earlier
     */
    void onFailure(Node node, VirtualFrame frame, Exception ex);

}
