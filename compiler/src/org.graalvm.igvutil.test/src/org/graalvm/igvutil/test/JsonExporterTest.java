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
package org.graalvm.igvutil.test;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Set;

import org.graalvm.igvutil.JsonExporter;
import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.graphio.parsing.model.GraphClassifier;
import jdk.graal.compiler.graphio.parsing.model.GraphDocument;
import jdk.graal.compiler.graphio.parsing.model.Group;
import jdk.graal.compiler.graphio.parsing.model.InputBlock;
import jdk.graal.compiler.graphio.parsing.model.InputEdge;
import jdk.graal.compiler.graphio.parsing.model.InputGraph;
import jdk.graal.compiler.graphio.parsing.model.InputNode;
import jdk.graal.compiler.graphio.parsing.model.KnownPropertyNames;
import jdk.graal.compiler.graphio.parsing.model.Properties;
import jdk.graal.compiler.util.json.JsonBuilder;
import jdk.graal.compiler.util.json.JsonParser;
import jdk.graal.compiler.util.json.JsonWriter;

public class JsonExporterTest {

    public GraphDocument createTestDocument() {
        GraphDocument doc = new GraphDocument();
        Group g = new Group(doc);
        Properties gProps = g.writableProperties();
        gProps.setProperty(KnownPropertyNames.PROPNAME_NAME, "Group1");
        gProps.setProperty(KnownPropertyNames.PROPNAME_SHORT_NAME, "G1");
        gProps.setProperty("CustomProperty", "CustomPropertyValue");
        g.updateProperties(gProps);

        InputGraph graph1 = InputGraph.createTestGraph("0:Graph");
        graph1.setGraphType(GraphClassifier.DEFAULT_TYPE);
        graph1.getProperties().setProperty(KnownPropertyNames.PROPNAME_NAME, "StructuredGraph");
        graph1.getProperties().setProperty(KnownPropertyNames.PROPNAME_SHORT_NAME, "SG");
        InputNode n0 = new InputNode(0);
        InputNode n1 = new InputNode(1);
        n1.getProperties().setProperty(KnownPropertyNames.PROPNAME_HAS_PREDECESSOR, true);
        n1.getProperties().setProperty(KnownPropertyNames.PROPNAME_PREDECESSOR_COUNT, 1);
        n1.getProperties().setProperty("nodeClass", "PiNode");

        graph1.addNode(n0);
        graph1.addNode(n1);
        graph1.addEdge(new InputEdge((char) 0, 0, 1));
        InputBlock b1 = graph1.addBlock("B1");
        InputBlock b2 = graph1.addBlock("B2");
        graph1.addBlockEdge(b1, b2);

        g.addElement(graph1);
        doc.addElement(g);

        return doc;
    }

    @Test
    public void testProducesValidJson() throws IOException {
        GraphDocument doc = createTestDocument();

        StringWriter stringWriter = new StringWriter();
        JsonExporter exporter = new JsonExporter();

        try (JsonWriter jsonWriter = new JsonWriter(stringWriter);
                        JsonBuilder.ObjectBuilder builder = jsonWriter.objectBuilder()) {
            exporter.writeElement(doc, builder);
        }

        // Doesn't throw
        JsonParser.parseDict(stringWriter.toString());
    }

    @Test
    public void testOutputsGraphProperties() throws IOException {
        /*
         * Don't test for a specific output format, which is anyway bound to change, instead just
         * test that properties are included in output.
         */
        GraphDocument doc = createTestDocument();

        StringWriter stringWriter = new StringWriter();
        JsonExporter exporter = new JsonExporter();

        try (JsonWriter jsonWriter = new JsonWriter(stringWriter);
                        JsonBuilder.ObjectBuilder builder = jsonWriter.objectBuilder()) {
            exporter.writeElement(doc, builder);
        }

        String output = stringWriter.toString();
        Assert.assertTrue(output.contains("\"Group1\""));
        Assert.assertTrue(output.contains("\"G1\""));
        Assert.assertTrue(output.contains("\"CustomPropertyValue\""));
        Assert.assertTrue(output.contains(String.format("\"%s\"", KnownPropertyNames.PROPNAME_SHORT_NAME)));
    }

    @Test
    public void testOutputsNodeProperties() throws IOException {
        /*
         * Don't test for a specific output format, which is anyway bound to change, instead just
         * test that properties are included in output.
         */
        GraphDocument doc = createTestDocument();

        StringWriter stringWriter = new StringWriter();
        JsonExporter exporter = new JsonExporter();

        try (JsonWriter jsonWriter = new JsonWriter(stringWriter);
                        JsonBuilder.ObjectBuilder builder = jsonWriter.objectBuilder()) {
            exporter.writeElement(doc, builder);
        }

        String output = stringWriter.toString();
        Assert.assertTrue(output.contains("\"nodeClass\""));
        Assert.assertTrue(output.contains(String.format("\"%s\"", KnownPropertyNames.PROPNAME_HAS_PREDECESSOR)));
        Assert.assertTrue(output.contains(String.format("\"%s\"", KnownPropertyNames.PROPNAME_PREDECESSOR_COUNT)));
    }

    @Test
    public void testPropertyFilter() throws IOException {
        GraphDocument doc = createTestDocument();

        StringWriter stringWriter = new StringWriter();
        JsonExporter exporter = new JsonExporter(Set.of(KnownPropertyNames.PROPNAME_SHORT_NAME),
                        Set.of(KnownPropertyNames.PROPNAME_PREDECESSOR_COUNT));

        try (JsonWriter jsonWriter = new JsonWriter(stringWriter);
                        JsonBuilder.ObjectBuilder builder = jsonWriter.objectBuilder()) {
            exporter.writeElement(doc, builder);
        }

        String output = stringWriter.toString();
        Assert.assertTrue(output.contains("\"G1\""));
        Assert.assertTrue(output.contains(String.format("\"%s\"", KnownPropertyNames.PROPNAME_PREDECESSOR_COUNT)));
        Assert.assertTrue(output.contains(String.format("\"%s\"", KnownPropertyNames.PROPNAME_SHORT_NAME)));

        Assert.assertFalse(output.contains("\"Group1\""));
        Assert.assertFalse(output.contains("\"CustomPropertyValue\""));
        Assert.assertFalse(output.contains("\"nodeClass\""));
    }
}
