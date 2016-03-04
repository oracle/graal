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

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.SourceSection;

/**
 * A receiver of Truffle AST <em>before</em> execution events that can act on behalf of an external
 * client.
 * <p>
 * The {@link Probe} argument provides access to the {@link SourceSection} associated with the
 * event, as well as any {@link SyntaxTag}s that have been applied at that program's location.
 * <p>
 * This listener is designed for clients that also require access to the AST execution state at the
 * time of the event. Clients that do not require access to the AST execution state should use the
 * {@link SimpleInstrumentListener}.
 * <p>
 * Clients are free, of course, to record additional information in the listener implementation that
 * carries additional information about the context and reason for the particular
 * {@link ProbeInstrument} that is to be created from the listener.
 * <p>
 * Notification is fully synchronous, so overrides have performance implications. Non-trivial
 * methods should be coded with Truffle guidelines and cautions in mind.
 * 
 * @since 0.8 or earlier
 */
public interface StandardBeforeInstrumentListener {

    /**
     * Receive notification that an AST node's execute method is about to be called.
     * <p>
     * <strong>Synchronous</strong>: Truffle execution waits until the call returns.
     * 
     * @since 0.8 or earlier
     */
    void onEnter(Probe probe, Node node, VirtualFrame frame);

}
