/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.igvutil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import jdk.graal.compiler.graphio.parsing.LocationStackFrame;
import jdk.graal.compiler.graphio.parsing.LocationStratum;
import jdk.graal.compiler.graphio.parsing.model.Folder;
import jdk.graal.compiler.graphio.parsing.model.FolderElement;
import jdk.graal.compiler.graphio.parsing.model.InputBlock;
import jdk.graal.compiler.graphio.parsing.model.InputEdge;
import jdk.graal.compiler.graphio.parsing.model.InputGraph;
import jdk.graal.compiler.graphio.parsing.model.InputNode;
import jdk.graal.compiler.graphio.parsing.model.KnownPropertyNames;
import jdk.graal.compiler.graphio.parsing.model.Properties;
import jdk.graal.compiler.graphio.parsing.model.Property;
import jdk.graal.compiler.util.json.JsonBuilder;

/**
 * Exports BGV data to JSON.
 */
public class JsonExporter {
    private final Set<String> nodePropertyFilter;
    private final Set<String> documentPropertyFilter;

    public JsonExporter() {
        this.nodePropertyFilter = null;
        this.documentPropertyFilter = null;
    }

    public JsonExporter(Set<String> documentPropertyFilter, Set<String> nodePropertyFilter) {
        this.nodePropertyFilter = nodePropertyFilter;
        this.documentPropertyFilter = documentPropertyFilter;
    }

    protected static HashMap<String, ArrayList<String>> stacktrace(LocationStackFrame lsf) {
        HashMap<String, ArrayList<String>> langStack = new HashMap<>();
        HashSet<LocationStratum> memo = new HashSet<>();

        for (LocationStackFrame t = lsf; t != null; t = t.getParent()) {
            for (LocationStratum s : t.getStrata()) {
                if (!memo.contains(s)) {
                    memo.add(s);
                    String lang = s.language;
                    String methodName = t.getFullMethodName();
                    boolean isJava = lang.contentEquals("Java");
                    ArrayList<String> stack = langStack.getOrDefault(lang, new ArrayList<>());
                    methodName = (isJava && methodName != null) ? ("(" + methodName + ") ") : "";
                    stack.add(methodName + stratumToString(s));
                    langStack.put(lang, stack);
                }
            }
        }
        return langStack;
    }

    protected static String stratumToString(LocationStratum s) {
        String str = (s.uri != null ? s.uri : s.file) + ":" + s.line;
        // bci needed ?
        return str + stratumPosToString(s);
    }

    protected static String stratumPosToString(LocationStratum s) {
        if (s.startOffset > -1 && s.endOffset > -1) {
            return "(" + s.startOffset + "-" + s.endOffset + ")";
        }
        if (s.startOffset > -1) {
            return "(" + s.startOffset + "-?)";
        }
        if (s.endOffset > -1) {
            return "(?-" + s.endOffset + ")";
        }
        return "";
    }

    public static Object interceptStackTrace(Object o) {
        if (o instanceof LocationStackFrame lsf) {
            return stacktrace(lsf);
        }
        return o;
    }

    /**
     * Writes node properties, with special handling for specific properties which are known to have
     * a wrong type, e.g. the ID property is serialized as a string but should be written as a
     * number.
     */
    private static void writeNodeProperty(InputNode node, String name, Object value, JsonBuilder.ObjectBuilder builder) throws IOException {
        if (value == null) {
            return;
        }
        Object writtenValue = switch (name) {
            // ID property is stored as a string, replace with number
            case KnownPropertyNames.PROPNAME_ID -> node.getId();
            default -> interceptStackTrace(value);
        };
        builder.append(name, writtenValue);
    }

    protected void writeNode(InputNode node, JsonBuilder.ObjectBuilder builder) throws IOException {
        if (nodePropertyFilter != null) {
            for (String propName : nodePropertyFilter) {
                Object value = node.getProperties().get(propName);
                writeNodeProperty(node, propName, value, builder);
            }
        } else {
            for (Property<?> p : node.getProperties()) {
                writeNodeProperty(node, p.getName(), p.getValue(), builder);
            }
        }
    }

    protected static void writeEdge(InputEdge edge, JsonBuilder.ObjectBuilder builder) throws IOException {
        builder.append("from", edge.getFrom()).append("to", edge.getTo()).append("label", edge.getLabel()).append("type", edge.getType());
    }

    protected static void writeBlock(InputBlock b, JsonBuilder.ObjectBuilder builder) throws IOException {
        try (JsonBuilder.ArrayBuilder nodesBuilder = builder.append("nodes").array()) {
            for (InputNode node : b.getNodes()) {
                nodesBuilder.append(node.getId());
            }
        }
        try (JsonBuilder.ArrayBuilder edgesBuilder = builder.append("edges").array()) {
            for (InputBlock ss : b.getSuccessors()) {
                edgesBuilder.append(ss.getName());
            }
        }
    }

    public boolean shouldWriteEdges() {
        return nodePropertyFilter == null;
    }

    public boolean shouldWriteBlocks() {
        return nodePropertyFilter == null;
    }

    public void writeGraph(InputGraph graph, JsonBuilder.ObjectBuilder builder) throws IOException {
        if (graph.getProperties().size() <= 1) {
            // This is most likely an IR graph that is not needed
            return;
        }

        builder.append("id", graph.getDumpId());
        builder.append("name", graph.getName());
        builder.append("graph_type", graph.getGraphType());

        try (JsonBuilder.ObjectBuilder nodesBuilder = builder.append("nodes").object()) {
            for (InputNode node : graph.getNodes()) {
                try (JsonBuilder.ObjectBuilder nodeBuilder = nodesBuilder.append(String.valueOf(node.getId())).object()) {
                    writeNode(node, nodeBuilder);
                }
            }
        }
        if (shouldWriteEdges()) {
            try (JsonBuilder.ArrayBuilder edgesBuilder = builder.append("edges").array()) {
                for (InputEdge e : graph.getEdges()) {
                    try (JsonBuilder.ObjectBuilder edgeBuilder = edgesBuilder.nextEntry().object()) {
                        writeEdge(e, edgeBuilder);
                    }
                }
            }
        }
        if (shouldWriteBlocks()) {
            try (JsonBuilder.ObjectBuilder blocksBuilder = builder.append("blocks").object()) {
                /* Blocks can be reconstructed using nodes properties */
                for (InputBlock b : graph.getBlocks()) {
                    try (JsonBuilder.ObjectBuilder blockBuilder = blocksBuilder.append(b.getName()).object()) {
                        writeBlock(b, blockBuilder);
                    }
                }
            }
        }
    }

    public void writeElement(FolderElement element, JsonBuilder.ObjectBuilder builder) throws IOException {
        if (element instanceof InputGraph graph) {
            writeGraph(graph, builder);
        } else if (element instanceof Properties.Provider provider) {
            writeProperties(provider.getProperties(), documentPropertyFilter, builder);
        }

        if (element instanceof Folder folder) {
            try (JsonBuilder.ArrayBuilder elemsBuilder = builder.append("elements").array()) {
                for (FolderElement f : folder.getElements()) {
                    try (JsonBuilder.ObjectBuilder elemBuilder = elemsBuilder.nextEntry().object()) {
                        writeElement(f, elemBuilder);
                    }
                }
            }
        }
    }

    private static void writeProperties(Properties properties, Set<String> filter, JsonBuilder.ObjectBuilder builder) throws IOException {
        if (filter != null) {
            for (String propName : filter) {
                Object o = properties.get(propName);
                if (o != null) {
                    builder.append(propName, interceptStackTrace(o));
                }
            }
            return;
        }

        for (Property<?> p : properties) {
            Object o = p.getValue();
            if (o != null) {
                builder.append(p.getName(), o);
            }
        }
    }
}
