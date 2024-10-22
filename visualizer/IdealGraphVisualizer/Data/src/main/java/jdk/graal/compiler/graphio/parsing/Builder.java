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

package jdk.graal.compiler.graphio.parsing;

import static jdk.graal.compiler.graphio.parsing.model.KnownPropertyValues.CLASS_ENDNODE;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import jdk.graal.compiler.graphio.parsing.BinaryReader.EnumValue;
import jdk.graal.compiler.graphio.parsing.BinaryReader.Method;
import jdk.graal.compiler.graphio.parsing.model.GraphDocument;
import jdk.graal.compiler.graphio.parsing.model.Group;
import jdk.graal.compiler.graphio.parsing.model.InputBlock;
import jdk.graal.compiler.graphio.parsing.model.InputGraph;
import jdk.graal.compiler.graphio.parsing.model.Properties;

/**
 * Interface for building IGV data from the stream.
 */
public interface Builder {
    /**
     * Allows to control the reader.
     *
     * @param ctrl new model control
     */
    void setModelControl(ModelControl ctrl);

    void addBlockEdge(int from, int to);

    void addNodeToBlock(int nodeId);

    void end();

    void endBlock(int id);

    InputGraph endGraph();

    void endGroup();

    void endNode(int nodeId);

    ConstantPool getConstantPool();

    Properties getNodeProperties(int nodeId);

    void inputEdge(Port p, int from, int to, char num, int index);

    void makeBlockEdges();

    void makeGraphEdges();

    void markGraphDuplicate();

    /**
     * Called during reading when the reader encounters beginning of a new stream. All pending data
     * should be reset.
     */
    void resetStreamData();

    GraphDocument rootDocument();

    void setGroupName(String name, String shortName);

    void setMethod(String name, String shortName, int bci, Method method);

    void setNodeName(NodeClass nodeClass);

    void setNodeProperty(String key, Object value);

    void setProperty(String key, Object value);

    void setPropertySize(int size);

    void start();

    InputBlock startBlock(int id);

    InputBlock startBlock(String name);

    InputGraph startGraph(int dumpId, String format, Object[] args);

    void startGraphContents(InputGraph g);

    Group startGroup();

    void startGroupContent();

    void startNestedProperty(String propertyKey);

    void startNode(int nodeId, boolean hasPredecessors, NodeClass nodeClass);

    void startRoot();

    void successorEdge(Port p, int from, int to, char num, int index);

    NameTranslator prepareNameTranslator();

    void startDocumentHeader();

    void endDocumentHeader();

    /**
     * Report graph digest to the builder. Can be used to identify identical contents. Called before
     * {@link #endGraph()}.
     *
     * @param dg the digest for the current graph.
     */
    void graphContentDigest(byte[] dg);

    void reportLoadingError(String logMessage, List<String> parentNames);

    interface ModelControl {
        ConstantPool getConstantPool();

        void setConstantPool(ConstantPool c);
    }

    enum Length {
        S,
        M,
        L
    }

    interface LengthToString {
        String toString(ModelBuilder.Length l);
    }

    class Port {
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

    final class TypedPort extends Port {
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

    final class Node {
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

    final class NodeClass {
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
