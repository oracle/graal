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
package com.oracle.truffle.api.debug;

import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.FrameInstance.FrameAccess;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.nodes.Node;

/**
 * Summary of information about a frame for use by debugging clients.
 */
public final class FrameDebugDescription {
    private final int index;
    private final Node node;
    private final MaterializedFrame frame;

    FrameDebugDescription(int index, Node node, MaterializedFrame frame) {
        this.index = index;
        this.node = node;
        this.frame = frame;
    }

    FrameDebugDescription(int index, FrameInstance frameInstance) {
        this.index = index;
        this.node = frameInstance.getCallNode();
        this.frame = frameInstance.getFrame(FrameAccess.MATERIALIZE, true).materialize();
    }

    /**
     * Position in the current stack: {@code 0} at the top.
     */
    public int index() {
        return index;
    }

    /**
     * AST location of the call that created the frame.
     */
    public Node node() {
        return node;
    }

    public MaterializedFrame frame() {
        return frame;
    }
}
