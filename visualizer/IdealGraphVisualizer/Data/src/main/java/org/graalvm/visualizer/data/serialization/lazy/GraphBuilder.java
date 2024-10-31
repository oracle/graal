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

package org.graalvm.visualizer.data.serialization.lazy;

import java.util.Objects;

import org.netbeans.api.annotations.common.NonNull;

import jdk.graal.compiler.graphio.parsing.ConstantPool;
import jdk.graal.compiler.graphio.parsing.ModelBuilder;
import jdk.graal.compiler.graphio.parsing.ParseMonitor;
import jdk.graal.compiler.graphio.parsing.model.*;
import jdk.graal.compiler.graphio.parsing.model.InputGraph.GraphData;

/**
 * Loads an individual graph + possible nested subgraphs.
 */
final class GraphBuilder extends LazyModelBuilder {
    private final InputGraph toComplete;
    private final DG dummyGraph = new DG(null, -1, "dummy", new Object[0]); // NOI18N
    private final Object keepData;
    private final ConstantPool pool;
    private final StreamEntry graphEntry;

    public GraphBuilder(GraphDocument rootDocument, InputGraph toComplete, Object keepData,
                        StreamEntry entry, ParseMonitor monitor) {
        super(rootDocument, monitor);
        this.keepData = keepData;
        this.toComplete = toComplete;
        this.graphEntry = entry;
        this.pool = entry.getInitialPool().copy();
        // establish context
        pushGroup(toComplete.getGroup(), false);
    }

    @Override
    @NonNull
    public ConstantPool getConstantPool() {
        return pool;
    }

    @Override
    protected InputGraph createGraph(Properties.Entity parent, int dumpId, String format, Object[] args) {
        if (parent == toComplete.getGroup() && toComplete.getName().equals(ModelBuilder.makeGraphName(dumpId, format, args))) {
            return dummyGraph;
        }
        if (parent instanceof InputNode) {
            return super.doCreateGraph(parent,
                    new NestedGraphId(graphEntry, dumpId, format),
                    dumpId, format, args
            );
        }
        return super.createGraph(parent, dumpId, format, args);
    }

    /**
     * Yields the actual graph data
     *
     * @return
     */
    public GraphData data() {
        return dummyGraph.data();
    }

    @Override
    protected InputNode createNode(int id, NodeClass nodeClass) {
        reportProgress();
        return new InputNode(id, nodeClass, keepData);
    }

    @Override
    protected void registerToParent(Folder parent, FolderElement item) {
        if (item == dummyGraph || parent == toComplete.getGroup()) {
            // avoid duplicate registrations
            return;
        }
        super.registerToParent(parent, item);
    }

    @Override
    public void end() {
        super.end();
        // copy dummy graph's data to the real one
    }

    static class DG extends InputGraph {
        public DG(Object id, int dumpId, String format, Object[] args) {
            super(id, dumpId, format, args);
        }

        // accessor
        @Override
        protected GraphData data() {
            return super.data();
        }

        void copyData(LazyGraph target) {

        }
    }

    static final class NestedGraphId {
        private final Object outerGraph;
        private final int nodeId;
        private final String property;

        public NestedGraphId(Object outerGraph, int nodeId, String property) {
            this.outerGraph = outerGraph;
            this.nodeId = nodeId;
            this.property = property;
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 67 * hash + Objects.hashCode(this.outerGraph);
            hash = 67 * hash + this.nodeId;
            hash = 67 * hash + Objects.hashCode(this.property);
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
            final NestedGraphId other = (NestedGraphId) obj;
            if (this.nodeId != other.nodeId) {
                return false;
            }
            if (!Objects.equals(this.property, other.property)) {
                return false;
            }
            return Objects.equals(this.outerGraph, other.outerGraph);
        }
    }
}
