/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.sl.nodes;

import java.util.ArrayList;
import java.util.List;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.FrameInstance.FrameAccess;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.nodes.NodeVisitor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.sl.SLLanguage;
import com.oracle.truffle.sl.nodes.controlflow.SLBlockNode;
import com.oracle.truffle.sl.nodes.controlflow.SLFunctionBodyNode;
import com.oracle.truffle.sl.nodes.local.SLReadArgumentNode;
import com.oracle.truffle.sl.nodes.local.SLWriteLocalVariableNode;

public final class SLAstRootNode extends SLRootNode {

    @Child private SLExpressionNode bodyNode;

    private final SourceSection sourceSection;
    private final TruffleString name;

    @CompilationFinal(dimensions = 1) private SLWriteLocalVariableNode[] argumentNodesCache;

    public SLAstRootNode(SLLanguage language, FrameDescriptor frameDescriptor, SLExpressionNode bodyNode, SourceSection sourceSection, TruffleString name) {
        super(language, frameDescriptor);
        this.bodyNode = bodyNode;
        this.sourceSection = sourceSection;
        this.name = name;
    }

    @Override
    public SourceSection getSourceSection() {
        return sourceSection;
    }

    @Override
    public SLExpressionNode getBodyNode() {
        return bodyNode;
    }

    @Override
    public TruffleString getTSName() {
        return name;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        return bodyNode.executeGeneric(frame);
    }

    @Override
    public void setLocalValues(FrameInstance frameInstance, Object[] args) {
        Frame frame = frameInstance.getFrame(FrameAccess.READ_WRITE);
        FrameDescriptor fd = getFrameDescriptor();
        int values = fd.getNumberOfSlots();
        for (int i = 0; i < values; i++) {
            frame.setObject(i, args[i]);
        }
    }

    @Override
    public Object[] getLocalNames(FrameInstance frameInstance) {
        FrameDescriptor fd = getFrameDescriptor();
        int values = fd.getNumberOfSlots();
        Object[] localNames = new Object[values];
        for (int i = 0; i < values; i++) {
            localNames[i] = fd.getSlotName(i);
        }
        return localNames;
    }

    @Override
    public Object[] getLocalValues(FrameInstance frameInstance) {
        Frame frame = frameInstance.getFrame(FrameAccess.READ_ONLY);
        FrameDescriptor fd = getFrameDescriptor();
        int values = fd.getNumberOfSlots();
        Object[] localNames = new Object[values];
        for (int i = 0; i < values; i++) {
            localNames[i] = frame.getValue(i);
        }
        return localNames;
    }

    public SLWriteLocalVariableNode[] getDeclaredArguments() {
        SLWriteLocalVariableNode[] argumentNodes = argumentNodesCache;
        if (argumentNodes == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            argumentNodesCache = argumentNodes = findArgumentNodes();
        }
        return argumentNodes;
    }

    @Override
    public SourceSection ensureSourceSection() {
        return getSourceSection();
    }

    private SLWriteLocalVariableNode[] findArgumentNodes() {
        List<SLWriteLocalVariableNode> writeArgNodes = new ArrayList<>(4);
        NodeUtil.forEachChild(this.getBodyNode(), new NodeVisitor() {

            private SLWriteLocalVariableNode wn; // The current write node containing a slot

            @Override
            public boolean visit(Node node) {
                // When there is a write node, search for SLReadArgumentNode among its children:
                if (node instanceof InstrumentableNode.WrapperNode) {
                    return NodeUtil.forEachChild(node, this);
                }
                if (node instanceof SLWriteLocalVariableNode) {
                    wn = (SLWriteLocalVariableNode) node;
                    boolean all = NodeUtil.forEachChild(node, this);
                    wn = null;
                    return all;
                } else if (wn != null && (node instanceof SLReadArgumentNode)) {
                    writeArgNodes.add(wn);
                    return true;
                } else if (wn == null && (node instanceof SLStatementNode && !(node instanceof SLBlockNode || node instanceof SLFunctionBodyNode))) {
                    // A different SL node - we're done.
                    return false;
                } else {
                    return NodeUtil.forEachChild(node, this);
                }
            }
        });
        return writeArgNodes.toArray(new SLWriteLocalVariableNode[writeArgNodes.size()]);
    }

}
