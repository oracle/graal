/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.impl.TVMCI;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;

final class GraalTVMCI extends TVMCI {

    @Override
    public void onLoopCount(Node source, int count) {
        Node node = source;
        Node parentNode = source != null ? source.getParent() : null;
        while (node != null) {
            if (node instanceof OptimizedOSRLoopNode) {
                ((OptimizedOSRLoopNode) node).reportChildLoopCount(count);
            }
            parentNode = node;
            node = node.getParent();
        }
        if (parentNode != null && parentNode instanceof RootNode) {
            CallTarget target = ((RootNode) parentNode).getCallTarget();
            if (target instanceof OptimizedCallTarget) {
                ((OptimizedCallTarget) target).onLoopCount(count);
            }
        }
    }

    void onFirstExecution(OptimizedCallTarget callTarget) {
        super.onFirstExecution(callTarget.getRootNode());
    }

    @Override
    protected void onLoad(RootNode rootNode) {
        super.onLoad(rootNode);
    }

    @Override
    protected void markFrameMaterializeCalled(FrameDescriptor descriptor) {
        super.markFrameMaterializeCalled(descriptor);
    }

    @Override
    protected boolean getFrameMaterializeCalled(FrameDescriptor descriptor) {
        return super.getFrameMaterializeCalled(descriptor);
    }

    @Override
    public RootNode cloneUninitialized(RootNode root) {
        return super.cloneUninitialized(root);
    }

    @Override
    public boolean isCloneUninitializedSupported(RootNode root) {
        return super.isCloneUninitializedSupported(root);
    }
}
