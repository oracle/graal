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
 * Instrument listener for a tool that works by providing an AST to be attached/adopted directly
 * into the AST.
 */
public interface ToolNodeInstrumentListener {

    /**
     * Receive notification that a probed AST node to which the {@link Instrument} is attached is
     * about to be executed for the first time. This is a lazy opportunity for the tool to
     * optionally add the root of a newly created AST fragment that will be attached/adopted
     * directly into the executing AST. The new AST fragment will immediately begin receiving
     * {@link InstrumentationNode.TruffleEvents}, beginning with the current execution event.
     * <p>
     * AST fragments must be written to Truffle conventions. Some of these conventions are
     * especially important if the fragment is to be fully optimized along with it's new parent AST.
     * <p>
     * If this method returns {@code null} then it will be called again the next time the probed
     * node is about to be executed.
     * <p>
     * In some situations, this method will be called more than once for a particular Probe, and a
     * new instance must be supplied each time. Each instance will be attached at the equivalent
     * location in clones of the AST, and so should be behave as if equivalent for most purposes.
     * <p>
     * In some situations the AST fragment supplied by this method maybe cloned for attachment to
     * equivalent locations in cloned AST, so care should be taken about any state local to each
     * instance of the AST fragment.
     *
     * @see Instrument
     */
    ToolNode getToolNode(Probe probe);

}
