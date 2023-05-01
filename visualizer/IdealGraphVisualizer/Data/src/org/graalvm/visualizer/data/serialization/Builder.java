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

package org.graalvm.visualizer.data.serialization;

import org.graalvm.visualizer.data.GraphDocument;
import org.graalvm.visualizer.data.Group;
import org.graalvm.visualizer.data.InputBlock;
import org.graalvm.visualizer.data.InputGraph;
import org.graalvm.visualizer.data.Properties;
import java.util.List;
import java.util.Objects;
import org.graalvm.visualizer.data.serialization.BinaryReader.EnumValue;
import org.graalvm.visualizer.data.serialization.BinaryReader.Method;
import org.netbeans.api.annotations.common.CheckForNull;
import org.netbeans.api.annotations.common.NonNull;
import java.util.ArrayList;
import static org.graalvm.visualizer.data.KnownPropertyValues.CLASS_ENDNODE;

/**
 * Interface for building IGV data from the stream
 */
public interface Builder {
    /**
     * Allows to control the reader
     *
     * @param ctrl
     */
    public void setModelControl(ModelControl ctrl);

    void addBlockEdge(int from, int to);

    void addNodeToBlock(int nodeId);

    void end();

    void endBlock(int id);

    @CheckForNull
    InputGraph endGraph();

    void endGroup();

    void endNode(int nodeId);

    @NonNull
    ConstantPool getConstantPool();

    @CheckForNull
    Properties getNodeProperties(int nodeId);

    void inputEdge(Port p, int from, int to, char num, int index);

    void makeBlockEdges();

    void makeGraphEdges();

    void markGraphDuplicate();

    /**
     * Called during reading when the reader encounters beginning of a new
     * stream. All pending data should be reset.
     */
    void resetStreamData();

    @NonNull
    GraphDocument rootDocument();

    void setGroupName(String name, String shortName);

    void setMethod(String name, String shortName, int bci, Method method);

    void setNodeName(NodeClass nodeClass);

    void setNodeProperty(String key, Object value);

    void setProperty(String key, Object value);
    
    void setPropertySize(int size);

    void start();

    @CheckForNull
    InputBlock startBlock(int id);

    @CheckForNull
    InputBlock startBlock(String name);

    @CheckForNull
    InputGraph startGraph(int dumpId, String format, Object[] args);

    void startGraphContents(InputGraph g);

    @CheckForNull
    Group startGroup();

    void startGroupContent();

    void startNestedProperty(String propertyKey);

    void startNode(int nodeId, boolean hasPredecessors, NodeClass nodeClass);

    void startRoot();

    void successorEdge(Port p, int from, int to, char num, int index);

    BinaryMap prepareBinaryMap();
    
    void startDocumentHeader();
    
    void endDocumentHeader();
    
    /**
     * Report graph digest to the builder. Can be used to identify identical
     * contents. Called before {@link #endGraph()}.
     * @param dg the digest for the current graph. 
     */
    void graphContentDigest(byte[] dg);
    
    void reportLoadingError(String logMessage, List<String> parentNames);
    
    interface ModelControl {
        public ConstantPool getConstantPool();

        public void setConstantPool(ConstantPool c);
    }

    public enum Length {
        S,
        M,
        L
    }

    public interface LengthToString {
        String toString(ModelBuilder.Length l);
    }

    public static class Port {
        public final boolean isList;
        public final String name;
        public List<Integer> ids = new ArrayList<>();

        Port(boolean isList, String name) {
            this.isList = isList;
            this.name = name;
        }

        @Override
        public int hashCode() {
            return (isList ? 7 : 13) ^ name.hashCode();
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
            final Port other = (Port) obj;
            return this.isList == other.isList && Objects.equals(this.name, other.name);
        }

    }

    public static final class TypedPort extends Port {
        public final EnumValue type;

        TypedPort(boolean isList, String name, EnumValue type) {
            super(isList, name);
            this.type = type;
        }

        @Override
        public int hashCode() {
            return (super.hashCode() * 23) ^ Objects.hashCode(type);
        }

        @Override
        public boolean equals(Object obj) {
            return super.equals(obj) && Objects.equals(type, ((TypedPort) obj).type);
        }
    }

    public final static class Node {
        public final int id;
        public final NodeClass clazz;

        Node(int id, NodeClass clazz) {
            this.id = id;
            this.clazz = clazz;
        }

        @Override
        public String toString() {
            return id + " : " + clazz.toShortString();
        }
    }

    public final static class NodeClass {
        public final String className;
        public final String nameTemplate;
        public final List<TypedPort> inputs;
        public final List<Port> sux;

        NodeClass(String className, String nameTemplate, List<TypedPort> inputs, List<Port> sux) {
            this.className = className;
            this.nameTemplate = nameTemplate;
            this.inputs = inputs;
            this.sux = sux;
        }

        @Override
        public String toString() {
            return className;
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 37 * hash + Objects.hashCode(this.className);
            hash = 37 * hash + Objects.hashCode(this.inputs);
            hash = 37 * hash + Objects.hashCode(this.sux);
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
            final NodeClass other = (NodeClass) obj;
            if (!Objects.equals(this.className, other.className)) {
                return false;
            }
            if (!Objects.equals(this.inputs, other.inputs)) {
                return false;
            }
            return Objects.equals(this.sux, other.sux);
        }

        String toShortString() {
            int lastDot = className.lastIndexOf('.');
            String localShortName = className.substring(lastDot + 1);
            if (localShortName.endsWith("Node") && !localShortName.equals("StartNode") && !localShortName.equals(CLASS_ENDNODE)) {
                return localShortName.substring(0, localShortName.length() - 4);
            } else {
                return localShortName;
            }
        }

    }
}
