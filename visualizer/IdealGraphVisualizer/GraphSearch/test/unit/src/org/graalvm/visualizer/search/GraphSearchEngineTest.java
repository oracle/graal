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
import java.util.Collection;
import java.util.List;
import org.graalvm.visualizer.data.FolderElement;
import org.graalvm.visualizer.data.GraphContainer;
import org.graalvm.visualizer.data.GraphDocument;
import org.graalvm.visualizer.data.Group;
import org.graalvm.visualizer.data.InputGraph;
import org.graalvm.visualizer.data.Properties;
import org.graalvm.visualizer.data.serialization.BinaryReader;
import org.graalvm.visualizer.data.serialization.BinarySource;
import org.graalvm.visualizer.data.serialization.FileContent;
import org.graalvm.visualizer.data.serialization.GraphParser;
import org.graalvm.visualizer.data.serialization.ModelBuilder;
import org.graalvm.visualizer.data.serialization.lazy.ScanningModelBuilder;
import org.netbeans.junit.NbTestCase;
import org.openide.util.RequestProcessor;

/**
 *
 * @author sdedic
 */
public class GraphSearchEngineTest extends NbTestCase {

    public GraphSearchEngineTest(String name) {
        super(name);
    }
    
    static GraphDocument document;
    Group group;
    InputGraph graph1;
    InputGraph graph2;
    
    GraphSearchEngine defaultEngine;
    
    public void setUp() throws Exception {
        super.setUp();
        if (document == null) {
            document = loadData("bigv-1.0.bgv");
        }

        for (FolderElement fe : document.getElements()) {
            if (fe instanceof Group) {
                group = (Group) fe;

                List<InputGraph> ig = group.getGraphs();
                graph1 = ig.get(3);
                graph2 = ig.get(4);
            }
        }
        defaultEngine = new GraphSearchEngine(group, graph1, new SimpleNodeProvider());
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
                RequestProcessor.getDefault()).
                setDocumentId("");

        GraphParser parser = new BinaryReader(src, bld);
        return parser.parse();
    }

    public void testCreateEngine() throws Exception {
        SearchResultsModel model = defaultEngine.getResults();
        assertEquals(0, model.getSize());
        assertTrue(model.getItems().isEmpty());
    }
    
    public void testSearchSimple() throws Exception {
        SearchTask task;
        
        synchronized (defaultEngine) {
            task = defaultEngine.newSearch(new Criteria().setMatcher(
                new Properties.RegexpPropertyMatcher("class", "MethodCallTargetNode")
            ), true);
            
            assertFalse(task.isFinished());
            assertFalse(task.isCancelled());
        }
        
        // let the search to continue
        task.getTask().waitFinished(200);
        assertTrue(task.isFinished());
        assertFalse(task.isCancelled());
        
        SearchResultsModel res = task.getModel();
        assertSame(res, defaultEngine.getResults());
        
        // 5 invoke nodes, from a single graph
        assertEquals(5, res.getSize());
        assertEquals(1, res.getParents().size());
        assertEquals(5, res.getChildren(new GraphItem("", graph1)).size());
    }
    
    public void testSearchCancel() throws Exception {
        SearchTask task;
        
        synchronized (defaultEngine) {
            task = defaultEngine.newSearch(new Criteria().setMatcher(
                new Properties.RegexpPropertyMatcher("class", "MethodCallTargetNode")
            ), true);
            // hope the task will be scheduled.
            Thread.sleep(100);
            // cancel immediately
            task.cancel();
        }
        SearchResultsModel res = task.getModel();
        
        assertTrue(task.isCancelled());
        task.getTask().waitFinished(500);
        assertTrue(res.getSize()< 5);
    }
    
    public void testSearchExtendNextAfterSearch() throws Exception {
        SearchTask task = defaultEngine.newSearch(new Criteria().setMatcher(
            new Properties.RegexpPropertyMatcher("class", "MethodCallTargetNode")
        ), true);
        task.getTask().waitFinished();
        
        SearchTask ntask = defaultEngine.extendSearch(false, true);
        
        assertNotSame(task, ntask);
        
        ntask.getTask().waitFinished();
        
        assertEquals(10, ntask.getModel().getItems().size());
        
        Collection<NodeResultItem> snapshot = ntask.getModel().getItems();
        
        ntask = defaultEngine.extendSearch(false, false);
        
        ntask.getTask().waitFinished();
        assertTrue(ntask.getModel().getItems().size() > 15);
        assertTrue(ntask.getModel().getItems().containsAll(snapshot));
    }
    
    public void testSearchExtendNextWhileSearching() throws Exception {
        defaultEngine.addSearchListener(new SearchListener() {
            @Override
            public void started(SearchEvent ev) {
                defaultEngine.extendSearch(false, false);
            }
        });
        SearchTask task = defaultEngine.newSearch(new Criteria().setMatcher(
            new Properties.RegexpPropertyMatcher("class", "MethodCallTargetNode")
        ), true);
        task.getTask().waitFinished();
        
        // should return 10 results: 2 graphs
        assertTrue(task.getModel().getItems().size() > 5);
        assertTrue(task.getModel().getParents().size() > 1);
    }

    public void testSearchExtendPrevious() throws Exception {
        
    }
}
