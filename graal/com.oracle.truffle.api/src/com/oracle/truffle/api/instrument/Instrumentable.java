/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.*;

/**
 * Any Truffle node implementing this interface can be "instrumented" by installing a {@link Probe}
 * that intercepts {@link ExecutionEvents} at the node and routes them to any {@link Instrument}s
 * that have been attached to the {@link Probe}. Only one {@link Probe} may be installed at each
 * node; subsequent calls return the one already installed.
 */
public interface Instrumentable {

    /**
     * Enables "instrumentation" of this Truffle node by tools, where this node is presumed to be
     * part (and not the root of) of a well-formed Truffle AST that is not being executed. The AST
     * may be modified.
     *
     * @param context access to language implementation context
     * @return a {@link Probe} to which tools may attach {@link Instrument}s that will receive
     *         {@link ExecutionEvents}
     */
    public Probe probe(ExecutionContext context);
}
