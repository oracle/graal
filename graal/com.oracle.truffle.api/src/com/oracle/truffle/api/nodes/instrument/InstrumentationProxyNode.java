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
package com.oracle.truffle.api.nodes.instrument;

import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.api.nodes.instrument.InstrumentationProbeNode.ProbeChain;

/**
 * Interface implemented by language-specific Truffle <strong>proxy nodes</strong>: nodes that do
 * not participate in the language's execution semantics, but which are inserted into an AST so that
 * tools (e.g. tracers, analyzers, debuggers) can be notified of AST interpretation events and
 * possibly intervene.
 * <p>
 * Language-specific proxy nodes call notification methods on an attached {@linkplain ProbeChain
 * probe chain} which passes along {@linkplain InstrumentationProbeEvents events} to any
 * {@linkplain InstrumentationProbeNode probes} that might have been attached.
 */
public interface InstrumentationProxyNode extends InstrumentationNode, PhylumMarked {

    /**
     * Gets the non-instrumentation node being proxied.
     */
    Node getChild();

    /**
     * Gets the chain of probes to which events at this node are delegated. Note that a chain of
     * probes may be used by more than one proxy.
     */
    ProbeChain getProbeChain();

}
