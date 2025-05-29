/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.visualizer.search;

import java.io.File;
import java.net.URL;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.graalvm.visualizer.data.serialization.lazy.FileContent;
import org.graalvm.visualizer.data.serialization.lazy.ScanningModelBuilder;
import org.netbeans.junit.NbTestCase;
import org.openide.util.RequestProcessor;

import jdk.graal.compiler.graphio.parsing.BinaryReader;
import jdk.graal.compiler.graphio.parsing.BinarySource;
import jdk.graal.compiler.graphio.parsing.GraphParser;
import jdk.graal.compiler.graphio.parsing.ModelBuilder;
import jdk.graal.compiler.graphio.parsing.model.*;

/**
 * @author sdedic
 */
public class SearchResultsModelTest extends NbTestCase implements SearchResultsListener {

    SearchResultsModel model = new SearchResultsModel();
    GraphDocument document;
    Group group;
    InputGraph graph1;
    InputGraph graph2;

    public SearchResultsModelTest(String name) {
        super(name);
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        document = loadData("bigv-1.0.bgv");

        for (FolderElement fe : document.getElements()) {
            if (fe instanceof Group) {
                group = (Group) fe;

                List<InputGraph> ig = group.getGraphs();
                graph1 = ig.get(3);
                graph2 = ig.get(4);
            }
        }

        model.addSearchResultsListener(this);
        model.addParentListener(GraphItem.ROOT, new SearchResultsAdapter() {
            @Override
            public void parentsChanged(SearchResultsEvent event) {
                SearchResultsModelTest.this.parentsChanged(event);
            }
        });
    }

    protected GraphDocument loadData(String dFile) throws Exception {
        URL bigv = GraphSearchEngineTest.class.getResource(dFile);
        File f = new File(bigv.toURI());
        return loadData(f);
    }

    protected GraphDocument loadData(File f) throws Exception {
        final FileChannel channel = FileChannel.open(f.toPath(), StandardOpenOption.READ);
        FileContent content = new FileContent(f.toPath(), channel);
        BinarySource src = new BinarySource(null, content);

        GraphDocument targetDocument = new GraphDocument();
        ModelBuilder bld = new ScanningModelBuilder(
                src,
                content,
                targetDocument, null,
                RequestProcessor.getDefault());
        bld.setDocumentId("");

        GraphParser parser = new BinaryReader(src, bld);
        return parser.parse();
    }

    public void testAddItems() throws Exception {
        assertTrue(model.getItems().isEmpty());
        assertTrue(model.getParents().isEmpty());

        InputNode node = graph1.getNodes().iterator().next();
        GraphItem g1 = new GraphItem("", graph1);
        NodeResultItem item = new NodeResultItem(g1, node);

        model.add(item);

        assertEquals(1, items.size());
        assertEquals(1, parents.size());

        assertEquals(1, addedCount);
        assertEquals(1, propertiesCount);
        assertEquals(0, removedCount);
        assertEquals(2, parentsCount);

        assertEquals(1, model.getItems().size());
        assertEquals(1, model.getParents().size());
    }

    private void clearCounters() {
        addedCount = removedCount = parentsCount = propertiesCount = 0;
        parents.clear();
        items.clear();
        names.clear();
        changedParents.clear();
    }

    public void testAddItemsFromSameParent() throws Exception {
        InputNode node = graph1.getNode(22);
        InputNode node2 = graph1.getNode(3);
        InputNode node3 = graph1.getNode(6);

        GraphItem g1 = new GraphItem("", graph1);
        NodeResultItem item = new NodeResultItem(g1, node);

        model.add(item);
        clearCounters();

        model.addAll(Arrays.asList(
                new NodeResultItem(g1, node2),
                new NodeResultItem(g1, node3)
        ));

        assertEquals(2, items.size());
        // no changes in parents
        assertEquals(1, parents.size());

        assertEquals(1, addedCount);
        assertEquals(0, propertiesCount);
        assertEquals(0, removedCount);
        assertEquals(0, parentsCount);

        assertEquals(3, model.getItems().size());
        assertEquals(1, model.getParents().size());

        model.add(new NodeResultItem(g1, graph1.getNode(1)));
        assertEquals(1, propertiesCount);
    }

    public void testAddItemsFromDifferentParents() throws Exception {
        InputNode node = graph1.getNode(22);
        InputNode node2 = graph1.getNode(3);
        InputNode node3 = graph2.getNode(6);

        GraphItem g1 = new GraphItem("", graph1);
        GraphItem g2 = new GraphItem("", graph2);

        NodeResultItem item = new NodeResultItem(g1, node);

        model.add(item);
        clearCounters();

        model.addAll(Arrays.asList(
                new NodeResultItem(g1, node2),
                new NodeResultItem(g2, node3)
        ));

        assertEquals(2, items.size());
        // no changes in parents
        assertEquals(2, parents.size());
        assertEquals(2, changedParents.size());

        assertEquals(3, model.getItems().size());
        assertEquals(2, model.getParents().size());

        assertEquals(1, addedCount);
        assertEquals(0, propertiesCount);
        assertEquals(0, removedCount);
        assertEquals(2, parentsCount);
    }

    public void testRemoveItems() throws Exception {
        InputNode node = graph1.getNode(22);
        InputNode node2 = graph1.getNode(3);
        InputNode node3 = graph1.getNode(6);

        GraphItem g1 = new GraphItem("", graph1);
        GraphItem g2 = new GraphItem("", graph2);

        NodeResultItem item1;
        NodeResultItem item2;

        model.addAll(Arrays.asList(
                item1 = new NodeResultItem(g1, node),
                item2 = new NodeResultItem(g1, node2),
                new NodeResultItem(g1, node3)
        ));

        clearCounters();

        model.removeAll(Arrays.asList(
                item1,
                item2
        ));


        assertEquals(2, items.size());
        // no changes in parents
        assertEquals(1, parents.size());
        assertEquals(0, changedParents.size());

        assertEquals(1, model.getItems().size());
        assertEquals(1, model.getParents().size());

        assertEquals(0, addedCount);
        assertEquals(0, propertiesCount);
        assertEquals(1, removedCount);
        assertEquals(0, parentsCount);
    }

    public void testRemoveItemsMultiParents() throws Exception {
        InputNode node = graph1.getNode(22);
        InputNode node2 = graph2.getNode(3);
        InputNode node3 = graph1.getNode(6);
        InputNode node4 = graph2.getNode(6);

        GraphItem g1 = new GraphItem("", graph1);
        GraphItem g2 = new GraphItem("", graph2);

        NodeResultItem item1;
        NodeResultItem item2;

        model.addAll(Arrays.asList(
                item1 = new NodeResultItem(g1, node),
                item2 = new NodeResultItem(g2, node2),
                new NodeResultItem(g1, node3),
                new NodeResultItem(g2, node4)
        ));

        clearCounters();
        model.removeAll(Arrays.asList(
                item1, item2
        ));
        assertEquals(2, items.size());
        // no changes in parents
        assertEquals(2, parents.size());
        assertEquals(0, changedParents.size());

        assertEquals(2, model.getItems().size());
        assertEquals(2, model.getParents().size());

        assertEquals(0, addedCount);
        assertEquals(0, propertiesCount);
        assertEquals(1, removedCount);
        assertEquals(0, parentsCount);
    }

    public void testRemoveAllItemsParent() throws Exception {
        InputNode node = graph1.getNode(22);
        InputNode node2 = graph2.getNode(3);
        InputNode node3 = graph1.getNode(6);
        InputNode node4 = graph2.getNode(6);

        GraphItem g1 = new GraphItem("", graph1);
        GraphItem g2 = new GraphItem("", graph2);

        NodeResultItem item1;
        NodeResultItem item2;
        NodeResultItem item3;

        model.addAll(Arrays.asList(
                item1 = new NodeResultItem(g1, node),
                new NodeResultItem(g2, node2),
                item2 = new NodeResultItem(g1, node3),
                item3 = new NodeResultItem(g2, node4)
        ));

        clearCounters();
        model.removeAll(Arrays.asList(
                item1, item2, item3
        ));
        assertEquals(3, items.size());
        // no changes in parents
        assertEquals(2, parents.size());
        assertEquals(2, changedParents.size());

        assertEquals(1, model.getItems().size());
        assertEquals(1, model.getParents().size());

        assertEquals(0, addedCount);
        assertEquals(0, propertiesCount);
        assertEquals(1, removedCount);
        assertEquals(2, parentsCount);
    }

    private SearchResultsEvent addEvent;
    private SearchResultsEvent removeEvent;
    private SearchResultsEvent parentsEvent;
    private SearchResultsEvent propertiesEvent;

    private final List<GraphItem> changedParents = new ArrayList<>();
    private final List<GraphItem> parents = new ArrayList<>();
    private final List<NodeResultItem> items = new ArrayList<>();
    private final List<String> names = new ArrayList<>();

    private int addedCount;
    private int removedCount;
    private int parentsCount;
    private int propertiesCount;

    @Override
    public void itemsAdded(SearchResultsEvent event) {
        addedCount++;
        addEvent = event;
        items.addAll(event.getItems());
        parents.addAll(event.getGraphs());
    }

    @Override
    public void itemsRemoved(SearchResultsEvent event) {
        removedCount++;
        removeEvent = event;
        items.addAll(event.getItems());
        parents.addAll(event.getGraphs());
    }

    @Override
    public void parentsChanged(SearchResultsEvent event) {
        parentsCount++;
        changedParents.addAll(event.getGraphs());
    }

    @Override
    public void propertiesChanged(SearchResultsEvent event) {
        propertiesCount++;
        names.addAll(event.getNames());
    }
}
