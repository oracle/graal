/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.StandardTags.RootBodyTag;
import com.oracle.truffle.api.instrumentation.StandardTags.RootTag;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.interop.NodeLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.espresso.impl.SuppressFBWarnings;

/**
 * {@link RootTag} node that separates the Java method prolog e.g. copying arguments to the frame,
 * initializes {@code bci=0}, from the execution of the {@link BytecodeNode bytecodes/body}.
 * 
 * This class exists to conform to the Truffle instrumentation APIs, namely {@link RootTag} and
 * {@link RootBodyTag} in order to support proper unwind and re-enter.
 */
@ExportLibrary(NodeLibrary.class)
final class MethodWithBytecodeNode extends EspressoInstrumentableRootNodeImpl {

    @Child AbstractInstrumentableBytecodeNode bytecodeNode;

    MethodWithBytecodeNode(BytecodeNode bytecodeNode) {
        super(bytecodeNode.getMethodVersion());
        this.bytecodeNode = bytecodeNode;
    }

    @Override
    public int getBci(Frame frame) {
        return bytecodeNode.getBci(frame);
    }

    @Override
    public Node getLeafNode(int bci) {
        return bytecodeNode.getLeafNode(bci);
    }

    @Override
    void beforeInstumentation(VirtualFrame frame) {
        EspressoFrame.setBCI(frame, -1);
    }

    @Override
    Object execute(VirtualFrame frame) {
        bytecodeNode.initializeFrame(frame);
        return bytecodeNode.execute(frame);
    }

    @Override
    @SuppressFBWarnings(value = "BC_IMPOSSIBLE_INSTANCEOF", justification = "bytecodeNode may be replaced by instrumentation with a wrapper node")
    boolean isTrivial() {
        // Instrumented nodes are not trivial.
        return !(bytecodeNode instanceof WrapperNode) && bytecodeNode.isTrivial();
    }

    @Override
    public boolean hasTag(Class<? extends Tag> tag) {
        if (tag == StandardTags.RootTag.class) {
            return true;
        }
        return false;
    }

    @ExportMessage
    @SuppressWarnings("static-method")
    public boolean hasScope(@SuppressWarnings("unused") Frame frame) {
        return true;
    }

    @ExportMessage
    public Object getScope(Frame frame, boolean nodeEnter) {
        return getScopeSlowPath(frame != null ? frame.materialize() : null, nodeEnter);
    }

    @TruffleBoundary
    private Object getScopeSlowPath(MaterializedFrame frame, boolean nodeEnter) {
        return bytecodeNode.getScope(frame, nodeEnter);
    }
}
