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
package com.oracle.truffle.espresso.nodes;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.GenerateWrapper;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.interop.NodeLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;

@GenerateWrapper
@ExportLibrary(NodeLibrary.class)
public abstract class BaseEspressoStatementNode extends Node implements InstrumentableNode {

    public void execute(@SuppressWarnings("unused") VirtualFrame frame) {
        // only here to satisfy wrapper generation
    }

    public boolean hasTag(Class<? extends Tag> tag) {
        return tag == StandardTags.StatementTag.class;
    }

    @Override
    public boolean isInstrumentable() {
        return true;
    }

    @Override
    public WrapperNode createWrapper(ProbeNode probe) {
        return new BaseEspressoStatementNodeWrapper(this, probe);
    }

    public final BytecodeNode getBytecodeNode() {
        Node parent = getParent();
        while (!(parent instanceof BytecodeNode) && parent != null) {
            parent = parent.getParent();
        }
        return (BytecodeNode) parent;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    public final boolean hasScope(@SuppressWarnings("unused") Frame frame) {
        return true;
    }

    @ExportMessage
    @CompilerDirectives.TruffleBoundary
    @SuppressWarnings("static-method")
    public final Object getScope(Frame frame, @SuppressWarnings("unused") boolean nodeEnter) {
        return getBytecodeNode().getScope(frame, nodeEnter);
    }
}
