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

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.graalvm.visualizer.data.GraphDocument;
import org.graalvm.visualizer.data.InputGraph;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.junit.Test;

public class DataSourceTest {
    @Test
    public void readData30ViaDataSourceInputStream() throws Exception {
        URL bigv = DataSourceTest.class.getResource("lazy/bigv-3.0.bgv");
        assertNotNull("bigv-3.0.bgv found", bigv);
        GraphDocument checkDocument = new GraphDocument();
        AtomicInteger count = new AtomicInteger();
        List<String> titles = new ArrayList<>();
        ModelBuilder mb = new ModelBuilder(checkDocument,
                                           (g) -> count.incrementAndGet(), null) {
            @Override
            public InputGraph startGraph(int dumpId, String format, Object[] args) {
                titles.add(InputGraph.makeGraphName(dumpId, format, args));
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
        DataSource scanSource = new StreamSource(is);
        return scanSource;
    }

}
