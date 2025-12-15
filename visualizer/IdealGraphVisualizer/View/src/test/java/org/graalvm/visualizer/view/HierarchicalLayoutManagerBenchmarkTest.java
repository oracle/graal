/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.visualizer.view;

import jdk.graal.compiler.graphio.parsing.BinaryReader;
import jdk.graal.compiler.graphio.parsing.BinarySource;
import jdk.graal.compiler.graphio.parsing.GraphParser;
import jdk.graal.compiler.graphio.parsing.ModelBuilder;
import jdk.graal.compiler.graphio.parsing.model.FolderElement;
import jdk.graal.compiler.graphio.parsing.model.GraphDocument;
import jdk.graal.compiler.graphio.parsing.model.Group;
import jdk.graal.compiler.graphio.parsing.model.InputGraph;
import org.graalvm.visualizer.data.serialization.lazy.FileContent;
import org.graalvm.visualizer.graph.Connection;
import org.graalvm.visualizer.graph.Diagram;
import org.graalvm.visualizer.graph.Figure;
import org.graalvm.visualizer.hierarchicallayout.HierarchicalLayoutManager;
import org.graalvm.visualizer.layout.LayoutGraph;
import org.graalvm.visualizer.settings.layout.LayoutSettings;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;
import org.openide.util.Exceptions;

import java.awt.Dimension;
import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static org.graalvm.visualizer.settings.layout.LayoutSettings.MAX_LAYER_LENGTH;

/**
 * This is a somewhat ad-hoc benchmark of layout performance.  It's ad hoc because of the complexity of integrating
 * JMH with maven since that seems to require having the benchmark in a separate pom.xml.  The test can be launched with
 * the following command line.
 * <pre>
 *     mvn test -Dtest=org.graalvm.visualizer.view.HierarchicalLayoutManagerBenchmarkTest -DfailIfNoTests=false -DenableAssertions=false -DHierarchicalLayoutManagerBenchmarkTest.file=&lt;filename&gt;
 * </pre>
 */
public class HierarchicalLayoutManagerBenchmarkTest {

    static final String BENCHMARK_TEST_FILE = "HierarchicalLayoutManagerBenchmarkTest.file";
    static final String BENCHMARK_TEST_ITERATIONS = "HierarchicalLayoutManagerBenchmarkTest.iterations";

    private static Diagram diagram;

    protected static GraphDocument loadData(File f) {
        try {
            final FileChannel channel = FileChannel.open(f.toPath(), StandardOpenOption.READ);
            FileContent content = new FileContent(f.toPath(), channel);
            BinarySource src = new BinarySource(null, content);

            GraphDocument targetDocument = new GraphDocument();
            ModelBuilder bld = new ModelBuilder(targetDocument, null);
            bld.setDocumentId("");
            GraphParser parser = new BinaryReader(src, bld);
            return parser.parse();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static final LayoutSettings.LayoutSettingBean layoutSetting = LayoutSettings.getBean();

    @BeforeClass
    public static void beforeClass() {
        // Create BinarySource and parse document
        String pathname = System.getProperty(BENCHMARK_TEST_FILE);
        Assume.assumeTrue("Must set " + BENCHMARK_TEST_FILE + " property to run benchmark", pathname != null);
        GraphDocument doc = loadData(new File(pathname));

        // Find first Group and first InputGraph in it
        List<?> elements = doc.getElements();
        Group group = (Group) elements.get(0);
        InputGraph inputGraph = null;
        for (FolderElement fe : group.getElements()) {
            if (fe instanceof InputGraph) {
                inputGraph = (InputGraph) fe;
                break;
            }
        }
        if (inputGraph == null) {
            throw new IllegalStateException("No InputGraph found in BGV file");
        }
        InputGraph g = inputGraph;
        if (g instanceof Group.LazyContent) {
            Group.LazyContent<?> lg = (Group.LazyContent) g;
            if (!lg.isComplete()) {
                try {
                    lg.completeContents(null).get();
                } catch (InterruptedException | ExecutionException ex) {
                    Exceptions.printStackTrace(ex);
                }
            }
        }
        diagram = Diagram.createDiagram(inputGraph, "");
    }


    @Test
    public void benchmarkHierarchicalLayout() {
        int iterations = Integer.parseInt(System.getProperty(BENCHMARK_TEST_ITERATIONS, "3"));

        for (int i = 0; i < iterations; i++) {
            // Prepare layout input
            HashSet<Figure> figures = new HashSet<>(diagram.getFigures());
            figures.forEach(x -> x.setVisible(true));
            HashSet<Connection> edges = new HashSet<>(diagram.getConnections());
            LayoutGraph layoutGraph = new LayoutGraph(edges, figures);

            HierarchicalLayoutManager mgr = new HierarchicalLayoutManager(HierarchicalLayoutManager.Combine.SAME_OUTPUTS, layoutSetting);
            mgr.setTrace(true);

            mgr.setMaxLayerLength(layoutSetting.get(Integer.class, MAX_LAYER_LENGTH));
            mgr.doLayout(layoutGraph);

            Dimension size = layoutGraph.getSize();
            if (size == null) {
                throw new RuntimeException("Layout produced no size.");
            }
        }
    }
}
