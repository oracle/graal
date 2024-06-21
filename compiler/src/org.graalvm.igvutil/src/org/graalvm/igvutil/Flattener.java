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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import jdk.graal.compiler.graphio.parsing.BinaryReader;
import jdk.graal.compiler.graphio.parsing.DataBinaryWriter;
import jdk.graal.compiler.graphio.parsing.ModelBuilder;
import jdk.graal.compiler.graphio.parsing.StreamSource;
import jdk.graal.compiler.graphio.parsing.model.GraphDocument;
import jdk.graal.compiler.graphio.parsing.model.Group;
import jdk.graal.compiler.graphio.parsing.model.InputGraph;
import jdk.graal.compiler.graphio.parsing.model.KnownPropertyNames;

/**
 * Groups {@linkplain InputGraph InputGraphs} passed to {@link #visit} by a property specified by
 * {@link #flattenKey}, and saves them into a new document.
 *
 * @see InputGraph#getProperties()
 */
public final class Flattener {
    private final GraphDocument newDoc = new GraphDocument();
    private final Map<String, Group> groups = new HashMap<>();

    private final String flattenKey;

    public Flattener(String flattenKey) {
        this.flattenKey = flattenKey;
    }

    public GraphDocument getFlattenedDocument() {
        return newDoc;
    }

    public void save(String filename) {
        try {
            DataBinaryWriter.export(new File(filename), newDoc, null, null);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Adds a single graph to a new or existing group.
     */
    public void visit(InputGraph graph) {
        Group group = (Group) graph.getParent();
        String id = graph.getProperties().get(flattenKey, String.class);
        if (id == null) {
            id = "<none>";
        }
        Group newGroup = groups.get(id);
        if (newGroup == null) {
            newGroup = new Group(newDoc, group.getID());
            newGroup.setMethod(group.getMethod());
            newGroup.updateProperties(group.getProperties());
            newGroup.getProperties().setProperty(KnownPropertyNames.PROPNAME_NAME, id);
            newDoc.addElement(newGroup);
            groups.put(id, newGroup);
        }
        newGroup.addElement(graph);
    }

    /**
     * Groups all the graphs contained in the .bgv dump read by {@code stream}.
     */
    public void visitDump(InputStream stream) throws IOException {
        ModelBuilder mb = new ModelBuilder(new GraphDocument(), null) {
            @Override
            public InputGraph endGraph() {
                InputGraph graph = super.endGraph();
                visit(graph);
                return graph;
            }
        };
        new BinaryReader(new StreamSource(new BufferedInputStream(stream)), mb).parse();
    }
}
