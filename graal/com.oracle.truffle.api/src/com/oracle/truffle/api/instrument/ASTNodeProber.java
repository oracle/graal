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

import com.oracle.truffle.api.nodes.*;

/**
 * Methods for inserting a {@link Probe} at a Truffle AST node.
 * <p>
 * This interface is guest language agnostic, but current extensions are language-specific. This
 * will be revisited.
 * <p>
 * <strong>Disclaimer:</strong> experimental interface under development. Really!
 */
public interface ASTNodeProber {

    /**
     * Optionally applies <em>instrumentation</em> at a Truffle AST node, depending on guest
     * language characteristics and use-case policy.
     * <ul>
     * <li>if no instrumentation is to be applied, returns the AST node unmodified;</li>
     * <li>if an AST node is to be instrumented, then returns a newly created {@link Wrapper} that
     * <em>decorates</em> the AST node and notifies an associated {@link Probe} of all
     * {@link ExecutionEvents} at the wrapped AST node.</li>
     * <li>if the argument is itself a {@link Wrapper}, i.e. if the AST node at this site has
     * already been wrapped, then the wrapper is returned (with the possible addition of a
     * {@linkplain SyntaxTag tag}).</li>
     * </ul>
     *
     * @param astNode an AST node to which instrumentation might be applied
     * @param tag an optional category directing how the node, if instrumented, should be perceived
     *            by tool users
     * @param args additional arguments for instrumentation specific to a particular guest language
     * @return if no instrumentation should be applied or if the node is a {@link Wrapper} then the
     *         unmodified node; otherwise a newly created {@link Wrapper} (whose child
     *         {@code astNode}) with an associated {@link Probe} .
     */

    Node probeAs(Node astNode, SyntaxTag tag, Object... args);
}
