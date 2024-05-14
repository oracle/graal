/*
 * Copyright (c) 2013, 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.graphio.parsing.model;

import static jdk.graal.compiler.graphio.parsing.model.KnownPropertyNames.PROPNAME_HAS_PREDECESSOR;
import static jdk.graal.compiler.graphio.parsing.model.KnownPropertyNames.PROPNAME_ID;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jdk.graal.compiler.graphio.parsing.Builder;

public class InputNode extends Properties.Entity {

    private int id;
    /**
     * Will keep the referenced "keep" object in memory.
     */
    private final Object keep;
    private final Builder.NodeClass nodeClass;
    private final Map<Builder.Port, List<Integer>> portIdsMap = new HashMap<>();
    private List<InputGraph> subgraphs;

    public static final Comparator<InputNode> COMPARATOR = Comparator.comparingInt(InputNode::getId);

    public InputNode(int id) {
        this(id, null, null);
    }

    private void mapPorts() {
        portIdsMap.clear();
        if (nodeClass != null) {
            for (Builder.TypedPort port : nodeClass.inputs) {
                portIdsMap.put(port, port.ids);
            }
            for (Builder.Port port : nodeClass.sux) {
                portIdsMap.put(port, port.ids);
            }
        }
    }

    public Map<Builder.Port, List<Integer>> getIdsMap() {
        return portIdsMap;
    }

    public Builder.NodeClass getNodeClass() {
        return nodeClass;
    }

    public boolean hasPredecessors() {
        return "true".equals(getProperties().getString(PROPNAME_HAS_PREDECESSOR, null));
    }

    private static Properties nodeProperties(InputNode n, int id) {
        Properties properties = n == null ? Properties.newProperties() : Properties.newProperties(n.getProperties());
        properties.setProperty(PROPNAME_ID, String.valueOf(id));
        return properties;
    }

    public InputNode(InputNode n, Builder.NodeClass nodeClass, Object keep) {
        super(nodeProperties(n, n.id));
        this.id = n.id;
        this.nodeClass = nodeClass;
        mapPorts();
        this.keep = keep;
    }

    public InputNode(int id, Builder.NodeClass nodeClass, Object keep) {
        super(nodeProperties(null, id));
        this.id = id;
        this.nodeClass = nodeClass;
        mapPorts();
        this.keep = keep;
    }

    public InputNode(InputNode n) {
        this(n, n.nodeClass, n.keep);
    }

    public InputNode(int id, Builder.NodeClass nodeClass) {
        this(id, nodeClass, null);
    }

    public void setId(int id) {
        this.id = id;
        getProperties().setProperty(PROPNAME_ID, "" + id);
    }

    public int getId() {
        return id;
    }

    public void addSubgraph(InputGraph graph) {
        if (subgraphs == null) {
            subgraphs = new ArrayList<>();
        }
        subgraphs.add(graph);
    }

    public List<InputGraph> getSubgraphs() {
        return subgraphs;
    }

    public void getSubgraphs(Map<String, Object> props) {
        if (subgraphs != null) {
            for (InputGraph x : subgraphs) {
                String name = x.getName();
                props.put(name.substring(name.indexOf(':') + 1).trim(), x);
            }
        }
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof InputNode)) {
            return false;
        }
        InputNode n = (InputNode) o;
        return n.id == id;
    }

    @Override
    public int hashCode() {
        return id * 13;
    }

    @Override
    public String toString() {
        return "Node " + id + " " + getProperties().toString();
    }
}
