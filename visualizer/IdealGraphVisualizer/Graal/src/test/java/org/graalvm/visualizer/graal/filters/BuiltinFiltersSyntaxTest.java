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
package org.graalvm.visualizer.graal.filters;

import static org.junit.Assert.assertEquals;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.graalvm.visualizer.filter.*;
import org.graalvm.visualizer.graph.Diagram;
import org.junit.Ignore;
import org.junit.Test;
import org.openide.filesystems.FileLock;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.Exceptions;

import jdk.graal.compiler.graphio.parsing.model.InputGraph;

/**
 * @author sdedic
 */
public class BuiltinFiltersSyntaxTest {
    public static final String FOLDER_ID = "Filters";

    public BuiltinFiltersSyntaxTest() {
    }

    public List<Filter> readFilters() {
        FileObject folder = FileUtil.getConfigRoot().getFileObject(FOLDER_ID);
        FileObject[] children = folder.getChildren();

        List<Filter> result = new ArrayList<>();
        for (final FileObject fo : children) {
            InputStream is = null;

            String code = "";
            FileLock lock = null;
            try {
                lock = fo.lock();
                is = fo.getInputStream();
                BufferedReader r = new BufferedReader(new InputStreamReader(is));
                String s;
                StringBuilder sb = new StringBuilder();
                while ((s = r.readLine()) != null) {
                    sb.append(s);
                    sb.append("\n");
                }
                code = sb.toString();
            } catch (IOException ex) {
                Exceptions.printStackTrace(ex);
            } finally {
                try {
                    is.close();
                } catch (IOException ex) {
                    Exceptions.printStackTrace(ex);
                }
                lock.releaseLock();
            }

            String displayName = fo.getName();

            final CustomFilter cf = new CustomFilter(displayName, code);
            result.add(cf);
        }
        return result;
    }

    @Test
    @Ignore
    public void testFilterSyntax() throws Exception {
        List<Filter> customFilters = readFilters();
        InputGraph ig = InputGraph.createTestGraph("test");
        Diagram dg = Diagram.createEmptyDiagram(ig, "root");
        List<Filter> failed = new ArrayList<>();
        for (Filter f : customFilters) {
            FilterChain fch = new FilterChain();
            fch.addFilter(f);
            fch.addFilterListener(new FilterListener() {
                @Override
                public void filterStart(FilterEvent e) {
                }

                @Override
                public void filterEnd(FilterEvent e) {
                    if (e.getExecutionError() != null) {
                        e.getExecutionError().printStackTrace();
                        failed.add(f);
                    }
                }
            });
            try {
                Filters.apply(fch, dg);
            } catch (Exception ex) {
                ex.printStackTrace();
                failed.add(f);
            }
        }
        assertEquals("No filters should fail parsing or execution on empty diagram", Collections.emptyList(), failed);
    }
}
