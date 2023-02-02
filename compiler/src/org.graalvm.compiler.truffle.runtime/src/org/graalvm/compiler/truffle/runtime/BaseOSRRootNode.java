/*
 * Copyright (c) 2015, 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.runtime;

import java.lang.ref.Reference;

import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
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

    /**
     * Entrypoint for OSR root nodes.
     */
    protected abstract Object executeOSR(VirtualFrame frame);
}
