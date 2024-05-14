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

package jdk.graal.compiler.graph.test.graphio.parsing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import jdk.graal.compiler.graphio.parsing.BinaryReader;
import jdk.graal.compiler.graphio.parsing.DataSource;
import jdk.graal.compiler.graphio.parsing.ModelBuilder;
import jdk.graal.compiler.graphio.parsing.StreamSource;
import jdk.graal.compiler.graphio.parsing.model.GraphDocument;
import jdk.graal.compiler.graphio.parsing.model.InputGraph;

public class DataSourceTest {
    @Test
    public void readData30ViaDataSourceInputStream() throws Exception {
        URL bigv = DataSourceTest.class.getResource("bigv-3.0.bgv");
        assertNotNull("bigv-3.0.bgv found", bigv);
        GraphDocument checkDocument = new GraphDocument();
        List<String> titles = new ArrayList<>();
        ModelBuilder mb = new ModelBuilder(checkDocument, null) {
            @Override
            public InputGraph startGraph(int dumpId, String format, Object[] args) {
                titles.add(ModelBuilder.makeGraphName(dumpId, format, args));
                return super.startGraph(dumpId, format, args);
            }
        };
        DataSource scanSource = createDataSource(bigv);
        BinaryReader rdr = new BinaryReader(scanSource, mb);
        rdr.parse();
        assertEquals("Three graphs started", 3, titles.size());
        int prev = -1;
        for (String t : titles) {
            assertEquals("All % are gone", -1, t.indexOf("%"));
            int idColon = t.indexOf("id: ");
            assertNotEquals("id: found", -1, idColon);
            int cnt = Integer.parseInt(t.substring(idColon + 4));
            assertTrue("New counter (" + cnt + ") is bigger than " + prev, prev < cnt);
            prev = cnt;
        }
        if (prev < 5) {
            fail("Expecting at least 5 counted titles: " + prev);
        }
    }

    protected DataSource createDataSource(URL bigv) throws IOException {
        final InputStream is = bigv.openStream();
        return new StreamSource(new BufferedInputStream(is));
    }
}
