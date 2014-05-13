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
 * Implementation of a policy for <em>instrumenting</em> Truffle ASTs with {@link Probe}s at
 * particular nodes by inserting node {@link Wrapper}s.
 * <p>
 * Multiple "node probers" can be added, typically by different tools; the "combined prober" will
 * apply all of them.
 * <p>
 * The current implementation is provisional and does not completely encapsulate everything that
 * needs to be implemented for a particular use-case or set of use-cases. In particular, the AST
 * building code for each language implementation must have hand-coded applications of node probing
 * methods at the desired locations. For the duration of this approach, this must be done for any
 * node that any client tool wishes to probe.
 * <p>
 * A better approach will be to implement such policies as a Truffle {@link NodeVisitor}, but that
 * is not possible at this time.
 * <p>
 * <strong>Disclaimer:</strong> experimental interface under development.
 */
public interface ASTProber {

    // TODO (mlvdv) This is a provisional interface, more of a marker really
    // TODO (mlvdv) AST probing should eventually be done with visitors.

    /**
     * Adds a specification for adding probes at particular kinds of nodes.
     *
     * @param nodeProber
     * @throws IllegalArgumentException if the prober is not applicable to the guest language
     *             implementation.
     */
    void addNodeProber(ASTNodeProber nodeProber) throws IllegalArgumentException;

    /**
     * Gets a (possibly guest language-specific) {@link ASTNodeProber} that will apply all that have
     * been added; {@code null} if no instrumentation in AST.
     */
    ASTNodeProber getCombinedNodeProber();
}
