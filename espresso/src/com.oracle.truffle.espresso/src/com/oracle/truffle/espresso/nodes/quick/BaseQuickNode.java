/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.truffle.espresso.nodes.quick;

import static com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.GenerateWrapper;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.api.interop.NodeLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.espresso.nodes.BciProvider;
import com.oracle.truffle.espresso.nodes.BytecodeNode;
import com.oracle.truffle.espresso.nodes.EspressoNode;

@GenerateWrapper
@ExportLibrary(NodeLibrary.class)
public abstract class BaseQuickNode extends EspressoNode implements BciProvider, InstrumentableNode {

    public abstract int execute(VirtualFrame frame);

    public final boolean isInstrumentable() {
        return true;
    }

    @Override
    public final WrapperNode createWrapper(ProbeNode probeNode) {
        return new BaseQuickNodeWrapper(this, probeNode);
    }

    public boolean removedByRedefinition() {
        return false;
    }

    @ExplodeLoop
    public final BytecodeNode getBytecodeNode() {
        Node parent = getParent();
        while (!(parent instanceof BytecodeNode)) {
            parent = parent.getParent();
        }
        return (BytecodeNode) parent;
    }

    @Override
    public Node getLeafNode(int bci) {
        return getBytecodeNode().getLeafNode(bci);
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    public final boolean hasScope(@SuppressWarnings("unused") Frame frame) {
        return true;
    }

    @ExportMessage
    public final Object getScope(Frame frame, boolean nodeEnter) {
        return getScopeSlowPath(frame != null ? frame.materialize() : null, nodeEnter);
    }

    @TruffleBoundary
    private Object getScopeSlowPath(MaterializedFrame frame, boolean nodeEnter) {
        return getBytecodeNode().getScope(frame, nodeEnter);
    }
}
