/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
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

package org.graalvm.visualizer.source;

import jdk.graal.compiler.graphio.parsing.model.InputNode;

import java.util.Collection;
import java.util.EventObject;
import java.util.HashSet;
import java.util.Set;

/**
 *
 */
public class NodeLocationEvent extends EventObject {
    private final Collection<InputNode> nodes;
    private final InputNode selectedNode;
    private final NodeStack.Frame frame;
    private final Collection<NodeStack.Frame> resolvedFrames;
    private boolean extractNodes;

    NodeLocationEvent(NodeLocationContext ctx, NodeStack.Frame newLocation) {
        super(ctx);
        this.selectedNode = newLocation == null ? null : newLocation.getNode();
        this.nodes = null;
        this.frame = newLocation;
        this.resolvedFrames = null;
    }

    NodeLocationEvent(NodeLocationContext ctx, Set<NodeStack.Frame> resolvedFrames) {
        super(ctx);
        this.selectedNode = null;
        this.nodes = new HashSet<>();
        this.frame = null;
        this.resolvedFrames = resolvedFrames;
        this.extractNodes = true;
    }

    NodeLocationEvent(NodeLocationContext ctx, Collection<InputNode> nodes, InputNode selNode, NodeStack.Frame selFrame) {
        super(ctx);
        this.selectedNode = selNode;
        this.nodes = nodes;
        this.frame = selFrame;
        this.resolvedFrames = null;
    }

    public InputNode getSelectedNode() {
        return selectedNode;
    }

    public Collection<NodeStack.Frame> getResolvedFrames() {
        return resolvedFrames;
    }

    public Location getSelectedLocation() {
        if (frame == null) {
            return null;
        }
        return frame.getLocation();
    }

    public NodeStack.Frame getSelectedFrame() {
        return frame;
    }

    public NodeLocationContext getContext() {
        return (NodeLocationContext) getSource();
    }

    public Collection<InputNode> getNodes() {
        if (extractNodes) {
            for (NodeStack.Frame f : resolvedFrames) {
                nodes.add(f.getNode());
            }
            extractNodes = false;
        }
        return nodes;
    }
}
