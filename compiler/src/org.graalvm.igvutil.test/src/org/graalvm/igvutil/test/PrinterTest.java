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
import java.io.PrintWriter;
import java.io.StringWriter;

import org.graalvm.igvutil.Printer;
import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.graphio.parsing.model.GraphDocument;
import jdk.graal.compiler.graphio.parsing.model.Group;
import jdk.graal.compiler.graphio.parsing.model.InputGraph;
import jdk.graal.compiler.graphio.parsing.model.KnownPropertyNames;
import jdk.graal.compiler.graphio.parsing.model.Properties;

public class PrinterTest {

    public Group createTestGroup(GraphDocument doc, String name) {
        Group g = new Group(doc);
        Properties p = g.writableProperties();
        p.setProperty(KnownPropertyNames.PROPNAME_NAME, name);
        g.updateProperties(p);
        return g;
    }

    public GraphDocument createTestDocument() {
        GraphDocument doc = new GraphDocument();
        Group outer1 = createTestGroup(doc, "OuterGroup1");
        outer1.addElement(InputGraph.createTestGraph("GraphA"));
        outer1.addElement(InputGraph.createTestGraph("GraphB"));

        Group inner1 = createTestGroup(doc, "InnerGroup1");
        inner1.addElement(InputGraph.createTestGraph("GraphC"));
        inner1.addElement(InputGraph.createTestGraph("GraphD"));

        Group outer2 = createTestGroup(doc, "OuterGroup2");
        outer2.addElement(InputGraph.createTestGraph("GraphE"));

        outer1.addElement(inner1);
        doc.addElement(outer1);
        doc.addElement(outer2);
        return doc;
    }

    @Test
    public void testPrinter() throws IOException {
        try (StringWriter sw = new StringWriter();
                        PrintWriter writer = new PrintWriter(sw)) {
            Printer p = new Printer(writer);
            p.print(createTestDocument(), "document.bgv");

            String output = sw.toString();
            Assert.assertTrue(output.contains("document.bgv"));
            Assert.assertTrue(output.contains("GraphA"));
            Assert.assertTrue(output.contains("GraphB"));
            Assert.assertTrue(output.contains("GraphC"));
            Assert.assertTrue(output.contains("GraphD"));
            Assert.assertTrue(output.contains("GraphE"));

            Assert.assertTrue(output.contains("OuterGroup1"));
            Assert.assertTrue(output.contains("OuterGroup2"));
            Assert.assertTrue(output.contains("InnerGroup1"));
        }
    }
}
