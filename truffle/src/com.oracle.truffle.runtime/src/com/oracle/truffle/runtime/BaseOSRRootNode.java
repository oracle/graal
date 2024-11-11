/*
 * Copyright (c) 2015, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.runtime;

import java.lang.ref.Reference;

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeInterface;
import com.oracle.truffle.api.nodes.RootNode;

/**
 * Base class for on-stack replaced (OSR) root nodes.
 */
public abstract class BaseOSRRootNode extends RootNode {

    /**
     * Not adopted by the OSRRootNode; belongs to another RootNode. OptimizedCallTarget treats
     * OSRRootNodes specially, skipping adoption of child nodes.
     *
     * This loop node instance is also used by the compiler to find the real root node e.g. for the
     * truffle guest safepoint location. See TruffleSafepointInsertionPhase#skipOSRRoot.
     */
    @Child protected NodeInterface loopNode;

    protected BaseOSRRootNode(TruffleLanguage<?> language, FrameDescriptor frameDescriptor, NodeInterface loopNode) {
        super(language, frameDescriptor);
        this.loopNode = loopNode;
    }

    @Override
    public final Object execute(VirtualFrame frame) {
        try {
            return executeOSR(frame);
        } finally {
            // reachability fence is needed to keep the values from being cleared as non-live locals
            Reference.reachabilityFence(frame);
            Reference.reachabilityFence(this);
        }
    }

    @Override
    protected final boolean prepareForCompilation(boolean rootCompilation, int compilationTier, boolean lastTier) {
        Node node = (Node) loopNode; // safe to cast, always a Node
        RootNode root = node.getRootNode();
        assert root != null : "Loop and OSR nodes must be adopted";
        return OptimizedRuntimeAccessor.NODES.prepareForCompilation(root, rootCompilation, compilationTier, lastTier);
    }

    /**
     * Entrypoint for OSR root nodes.
     */
    protected abstract Object executeOSR(VirtualFrame frame);
}
