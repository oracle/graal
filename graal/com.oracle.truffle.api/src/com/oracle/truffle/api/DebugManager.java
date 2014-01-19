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
package com.oracle.truffle.api;

import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.api.nodes.instrument.*;
import com.oracle.truffle.api.nodes.instrument.InstrumentationProbeNode.ProbeChain;

/**
 * Language-agnostic access to AST-based debugging support.
 * <p>
 * <strong>Disclaimer:</strong> this interface is under development and will change.
 */
public interface DebugManager {

    /**
     * Receives notification of a suspended execution context; execution resumes when this method
     * returns.
     * 
     * @param astNode a guest language AST node that represents the current execution site, assumed
     *            not to be any kind of {@link InstrumentationNode},
     * @param frame execution frame at the site where execution suspended
     */
    void haltedAt(Node astNode, MaterializedFrame frame);

    void notifyFinishedLoading(Source source);

    void notifyStartLoading(Source source);

    ProbeChain getProbeChain(SourceSection sourceSection);

}
