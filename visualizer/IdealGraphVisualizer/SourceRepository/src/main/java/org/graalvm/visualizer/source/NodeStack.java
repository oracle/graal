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

import jdk.graal.compiler.graphio.parsing.model.InputGraph;
import jdk.graal.compiler.graphio.parsing.model.InputNode;
import org.openide.filesystems.FileObject;
import org.openide.util.Lookup;
import org.openide.util.lookup.ProxyLookup;

import java.util.Collections;
import java.util.Iterator;
import java.util.Objects;

/**
 *
 */
public final class NodeStack implements Iterable<NodeStack.Frame> {
    private final GraphSource source;
    private final StackData internalStack;
    private final InputGraph graph;
    private final Frame[] frames;
    private final int h;

    NodeStack(GraphSource source, StackData internalStack) {
        this.source = source;
        this.internalStack = internalStack;
        this.graph = source.getGraph();
        this.frames = new Frame[internalStack.size()];

        h = source.hashCode() << 5 ^ internalStack.getNodeId();
    }

    NodeStack(GraphSource source, String mime) {
        this.source = source;
        this.internalStack = new StackData(-1, mime, Collections.emptyList());
        this.graph = source.getGraph();
        this.frames = new Frame[0];
        h = source.hashCode() << 5 ^ internalStack.getNodeId();
    }

    public NodeStack getOtherStack(String langMime) {
        return source.getNodeStack(graph.getNode(internalStack.getNodeId()), langMime);
    }

    public String getMime() {
        return internalStack.getLanguageMimeType();
    }

    public boolean isEmpty() {
        return internalStack == null || internalStack.size() == 0;
    }

    @Override
    public String toString() {
        return "Stack[" + internalStack.getNodeId() + "]";
    }

    public Frame get(int index) {
        return frame(index);
    }

    public boolean contains(Frame f) {
        return f != null && f.getInternalStack() == internalStack;
    }

    @Override
    public int hashCode() {
        return h;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final NodeStack other = (NodeStack) obj;
        return this.source == other.source && this.internalStack.getNodeId() == other.internalStack.getNodeId();
    }

    @Override
    public Iterator<Frame> iterator() {
        return new Iterator<Frame>() {
            private int index = 0;

            @Override
            public boolean hasNext() {
                return index < size();
            }

            @Override
            public Frame next() {
                return frame(index++);
            }
        };
    }

    public InputGraph getGraph() {
        return source.getGraph();
    }

    public GraphSource getGraphSource() {
        return source;
    }

    public int size() {
        return frames.length;
    }

    public Frame bottom() {
        return frame(size() - 1);
    }

    public Frame top() {
        return frame(0);
    }

    Lookup[] createLookups(Frame f) {
        return source.findLookup(getNode(), f);
    }

    public Frame frame(int index) {
        if (index < 0 || index >= size()) {
            return null;
        }
        synchronized (this) {
            Frame f = frames[index];
            if (f == null) {
                Location l = internalStack.getLocations().get(index);
                f = new Frame(l, index);
                frames[index] = f;
            }
            return f;
        }
    }

    public InputNode getNode() {
        return getGraph().getNode(internalStack.getNodeId());
    }

    static class P extends ProxyLookup {
        void assign(Lookup[] lkps) {
            this.setLookups(lkps);
        }
    }

    public final class Frame implements Lookup.Provider {
        private final Location loc;
        private final int index;
        Lookup lookup;

        void assignLookup(Lookup l) {
            this.lookup = l;
        }

        @Override
        public Lookup getLookup() {
            P l;
            synchronized (this) {
                if (lookup != null) {
                    return lookup;
                }
                l = new P();
            }
            Lookup[] delegates = createLookups(this);
            l.assign(delegates);
            synchronized (this) {
                if (this.lookup != null) {
                    return this.lookup;
                }
                this.lookup = l;
            }
            return l;
        }

        public Frame(Location loc, int index) {
            this.loc = loc;
            this.index = index;
        }

        public Location getLocation() {
            return loc;
        }

        public int getDepth() {
            return index;
        }

        StackData getInternalStack() {
            return internalStack;
        }

        public InputNode getNode() {
            return NodeStack.this.getNode();
        }

        public InputGraph getGraph() {
            return NodeStack.this.getGraph();
        }

        public GraphSource getGraphSource() {
            return NodeStack.this.getGraphSource();
        }

        public String getOriginSpec() {
            return loc.getOriginSpec();
        }

        public String getFileName() {
            return loc.getFileName();
        }

        public boolean isResolved() {
            return loc.isResolved();
        }

        public FileObject getOriginFile() {
            return loc.getOriginFile();
        }

        public int getLine() {
            return loc.getLine();
        }

        public <T extends SpecificLocationInfo> boolean isOfKind(Class<T> clazz) {
            return loc.isOfKind(clazz);
        }

        public <T extends SpecificLocationInfo> T getSpecificInfo(Class<T> clazz) {
            return loc.getSpecificInfo(clazz);
        }

        public Frame getParent() {
            return frame(index + 1);
        }

        public Frame getNested() {
            return index == 0 ? null : frame(index - 1);
        }

        @Override
        public int hashCode() {
            int hash = 3;
            hash = 43 * hash + Objects.hashCode(this.loc);
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final Frame other = (Frame) obj;
            if (!Objects.equals(this.loc, other.loc)) {
                return false;
            }
            return true;
        }

        public boolean isNestedIn(Frame other) {
            if (!internalStack.equals(other.getInternalStack())) {
                return false;
            }
            return index < other.getDepth();
        }

        public boolean isParentOf(Frame other) {
            if (!internalStack.equals(other.getInternalStack())) {
                return false;
            }
            return index > other.getDepth();
        }

        @Override
        public String toString() {
            return "Frame[nodeId = " + getNode().getId() + ", loc = " + getLocation() + "]";
        }

        public NodeStack getStack() {
            return NodeStack.this;
        }

        public Frame findPeerFrame(String langMime) {
            return findPeerFrame(getOtherStack(langMime));
        }

        public Frame findPeerFrame(NodeStack o) {
            if (o == null) {
                return null;
            }
            for (Frame of : o) {
                if (of.getLocation().compareNesting(loc) == 0) {
                    return of;
                }
            }
            return null;
        }
    }
}
