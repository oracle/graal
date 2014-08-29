/*
 * Copyright (c) 2013, 2014, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.nodes.*;

/**
 * A node that can be inserted into a Truffle AST in order to attach <em>instrumentation</em> at a
 * particular node.
 * <p>
 * A wrapper <em>decorates</em> an AST node (its <em>child</em>) by acting as a transparent
 * <em>proxy</em> for the child with respect to Truffle execution semantics.
 * <p>
 * A wrapper is also expected to notify its associated {@link Probe} when {@link ExecutionEvents}
 * occur at the wrapper during program execution.
 * <p>
 * The wrapper's {@link Probe} is shared by every copy of the wrapper made when the AST is copied.
 * <p>
 * Wrapper implementation guidelines:
 * <ol>
 * <li>Every guest language implementation should include a Wrapper implementation; usually only one
 * is needed.</li>
 * <li>The Wrapper type should descend from the <em>language-specific node class</em> of the guest
 * language.</li>
 * <li>The Wrapper must have a single {@code @Child private <guestLanguage>Node child} field.</li>
 * <li>The Wrapper must act as a <em>proxy</em> for its child, which means implementing every
 * possible <em>execute-</em> method that gets called on guest language AST node types by their
 * parents, and passing along each call to its child.</li>
 * <li>The Wrapper must have a single {@code private final Probe probe} to which an optional probe
 * can be attached during node construction.</li>
 * <li>Wrapper methods must also notify its attached {@link Probe}, if any, in terms of standard
 * {@link ExecutionEvents}.</li>
 * <li>Most importantly, Wrappers must be implemented so that Truffle optimization will reduce their
 * runtime overhead to zero when there is no probe attached or when a probe has no attached
 * {@link Instrument}s.</li>
 * </ol>
 * <p>
 * <strong>Disclaimer:</strong> experimental interface under development.
 *
 * @see Probe
 * @see ExecutionEvents
 */
public interface Wrapper {

    /**
     * Gets the AST node being instrumented, which should never be an instance of {@link Wrapper}.
     */
    Node getChild();

    /**
     * Gets the {@link Probe} to which events occurring at this wrapper's child are propagated.
     */
    Probe getProbe();

}
