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
package org.graalvm.visualizer.search.quicksearch;

import static jdk.graal.compiler.graphio.parsing.model.KnownPropertyNames.PROPNAME_NAME;
import static jdk.graal.compiler.graphio.parsing.model.KnownPropertyNames.PROPNAME_NODE_SOURCE_POSITION;
import static jdk.graal.compiler.graphio.parsing.model.KnownPropertyValues.NAME_START;

import java.lang.reflect.Constructor;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.graalvm.visualizer.filter.FilterChain;
import org.graalvm.visualizer.settings.layout.LayoutSettings;
import org.graalvm.visualizer.settings.layout.LayoutSettings.LayoutSettingBean;
import org.graalvm.visualizer.view.DiagramViewModel;
import org.netbeans.junit.NbTestCase;
import org.netbeans.modules.quicksearch.CategoryResult;
import org.netbeans.modules.quicksearch.ProviderModel.Category;
import org.netbeans.spi.quicksearch.SearchRequest;
import org.netbeans.spi.quicksearch.SearchResponse;
import org.openide.util.Pair;

import jdk.graal.compiler.graphio.parsing.model.GraphDocument;
import jdk.graal.compiler.graphio.parsing.model.Group;
import jdk.graal.compiler.graphio.parsing.model.InputGraph;
import jdk.graal.compiler.graphio.parsing.model.InputNode;

/**
 * @author sdedic
 */
public class NodeQuickSearchTest extends NbTestCase {
    private static final LayoutSettingBean LAYOUT_SETTINGS = LayoutSettings.getBean();
    GraphDocument checkDocument;

    public NodeQuickSearchTest(String name) {
        super(name);
    }

    SearchRequest request;
    SearchResponse response;

    void createRequest(String text) throws Exception {
        Constructor<SearchRequest> ctor = SearchRequest.class.getDeclaredConstructor(String.class, List.class);
        ctor.setAccessible(true);
        SearchRequest request = ctor.newInstance(text, null);

        CategoryResult cat = new CategoryResult(new Category(null, "nodes", ""), true);

        Constructor<SearchResponse> respCtor = SearchResponse.class.getDeclaredConstructor(CategoryResult.class, SearchRequest.class);
        respCtor.setAccessible(true);
        SearchResponse resp = respCtor.newInstance(cat, request);

        this.request = request;
        this.response = resp;
    }

    @Override
    protected void tearDown() throws Exception {
        MockViewerLocator.viewModel = null;
        super.tearDown();
    }

    private Group g;

    private void loadMegaData() throws Exception {
        checkDocument = ViewTestUtil.loadMegaData();

        Group parent = (Group) checkDocument.getElements().get(0);
        g = (Group) parent.getElements().get(0);
        InputGraph graph = g.getGraphs().get(0);

        assert graph != null;

        FilterChain fch = new FilterChain();
        MockViewerLocator.viewModel = new DiagramViewModel(g, fch, LAYOUT_SETTINGS);
    }

    public void testBasicSearch() throws Exception {
        loadMegaData();
        InputGraph graph = g.getGraphs().get(0);
        createRequest("Star");

        NodeQuickSearch search = new NodeQuickSearch();
        Pair<List<InputNode>, List<InputNode>> foundNodes = search.findMatches(PROPNAME_NAME, "Star", graph, response);

        assertNotNull(foundNodes.first());
        assertNotNull(foundNodes.second());

        // only one 'Star'
        assertEquals(1, foundNodes.first().size());
        assertEquals(0, foundNodes.second().size());

        // search partial matches in other graph
        graph = g.getGraphs().get(1);
        foundNodes = search.findMatches(PROPNAME_NAME, "Sta", graph, response);
        assertTrue(foundNodes.first().size() > 0);
        for (InputNode n : foundNodes.first()) {
            assertTrue(n.getProperties().getString(PROPNAME_NAME, "").toLowerCase().contains("sta"));
        }

        foundNodes = search.findMatches(PROPNAME_NAME, NAME_START, graph, response);
        assertEquals(0, foundNodes.first().size());
        assertTrue(foundNodes.second().size() > 0);
        for (InputNode n : foundNodes.second()) {
            assertTrue(n.getProperties().getString(PROPNAME_NAME, "").equalsIgnoreCase(NAME_START));
        }
    }

    /**
     * Checks that query matches part of a multiline property = node stacktrace
     */
    public void testMultiLinePartialSearch() throws Exception {
        loadMegaData();
        InputGraph graph = g.getGraphs().get(0);
        createRequest("SLBuiltinNode");
        NodeQuickSearch search = new NodeQuickSearch();
        Pair<List<InputNode>, List<InputNode>> foundNodes = search.findMatches(PROPNAME_NAME, "SLBuiltinNode", graph, response);
        assertEquals(4, foundNodes.first().size());
        assertEquals(0, foundNodes.second().size());

        foundNodes = search.findMatches(PROPNAME_NODE_SOURCE_POSITION, "SLBuiltinNode", graph, response);
        assertTrue(foundNodes.first().size() > 0);
        assertTrue(foundNodes.second().isEmpty());
    }

    private final ScheduledExecutorService srv = Executors.newSingleThreadScheduledExecutor();
}
