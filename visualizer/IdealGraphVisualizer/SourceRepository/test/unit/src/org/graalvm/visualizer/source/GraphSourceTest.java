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

package org.graalvm.visualizer.source;

import org.graalvm.visualizer.data.InputGraph;
import org.graalvm.visualizer.data.InputNode;
import static org.graalvm.visualizer.data.KnownPropertyNames.PROPNAME_NODE_SOURCE_POSITION;
import org.graalvm.visualizer.data.serialization.lazy.LazySerDebugUtils;
import org.openide.filesystems.FileObject;
import java.io.File;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;

/**
 *
 * @author sdedic
 */
public class GraphSourceTest extends GraphSourceTestBase {
    public GraphSourceTest(String name) {
        super(name);
    }

    private void assertContainsSource(Collection<FileObject> files, String path) {
        FileObject s = sourcePath.findResource(path);
        assertNotNull("Source " + path + " must exist", s);
        assertTrue("File " + path + " must be present", files.contains(s));
    }

    private void assertNotContainsSource(Collection<FileObject> files, String path) {
        FileObject s = sourcePath.findResource(path);
        assertNotNull("Source " + path + " must exist", s);
        assertFalse("File " + path + " must NOT be present", files.contains(s));
    }

    /**
     * Loads stacktraces, is able to resolve locations. Checks that files are in
     * place. Checks that each file's location is ordered.
     *
     * @throws Exception
     */
    public void testGetFileLocations() throws Exception {
        PlatformLocationResolver.enabled = true;
        GraphSource src = GraphSource.getGraphSource(magnitudeGraph);
        src.prepare().get();
        Collection<FileObject> files = src.getSourceFiles();
        assertContainsSource(files, "java/lang/StringBuilder.java");
        assertContainsSource(files, "java/lang/Math.java");
        assertContainsSource(files, "java/util/Locale.java");
        assertContainsSource(files, "java/util/Formatter.java");

        for (FileObject f : files) {
            List<Location> locs = src.getFileLocations(f, false);
            Set<Location> uniqueLocs = new HashSet<>(locs);
            assertEquals(locs.size(), uniqueLocs.size());
            assertNotNull(locs);
            assertFalse(locs.isEmpty());
            int lineNo = -1;
            for (Location l : locs) {
                assertTrue(lineNo <= l.getLine());
                lineNo = l.getLine();
            }
        }
    }

    /**
     * Checks that locations are initially unresolved, they are not reported
     * from files, but are recognized by the GraphSource.
     *
     * @throws Exception
     */
    public void testPartiallyResolvedLocations() throws Exception {
        PlatformLocationResolver.enabled = true;
        PlatformLocationResolver.enablePackage("java/util", true);

        GraphSource src = GraphSource.getGraphSource(magnitudeGraph);
        Collection<FileObject> files = src.getSourceFiles();

        assertNotContainsSource(files, "java/lang/StringBuilder.java");
        assertNotContainsSource(files, "java/lang/Math.java");
        assertContainsSource(files, "java/util/Locale.java");
        assertContainsSource(files, "java/util/Formatter.java");

        // java.lang was not resolved, but the locations must be registered
        FileKey langString = src.getFileRegistry().enter(new FileKey("text/x-java", "java/lang/StringBuilder.java"), magnitudeGraph);
        FileKey langMath = src.getFileRegistry().enter(new FileKey("text/x-java", "java/lang/Math.java"), magnitudeGraph);

        assertNotNull(langString);
        assertNotNull(langMath);

        Collection<FileKey> fks = src.getFileKeys();
        assertTrue(fks.contains(langString));
        assertTrue(fks.contains(langMath));
    }

    public void testUnresolvedLocationsBecomeResolved() throws Exception {
        PlatformLocationResolver.enabled = true;
        PlatformLocationResolver.enablePackage("java/util", true);

        GraphSource src = GraphSource.getGraphSource(magnitudeGraph);
        Collection<FileObject> files = src.getSourceFiles();

        assertNotContainsSource(files, "java/lang/StringBuilder.java");
        assertNotContainsSource(files, "java/lang/Math.java");
        assertContainsSource(files, "java/util/Locale.java");
        assertContainsSource(files, "java/util/Formatter.java");

        // java.lang was not resolved, but the locations must be registered
        FileKey langString = src.getFileRegistry().enter(new FileKey("text/x-java", "java/lang/StringBuilder.java"), magnitudeGraph);
        FileKey langMath = src.getFileRegistry().enter(new FileKey("text/x-java", "java/lang/Math.java"), magnitudeGraph);

        Collection<FileKey> fks = src.getFileKeys();
        assertTrue(fks.contains(langString));
        assertTrue(fks.contains(langMath));

        FileObject fMath = sourcePath.findResource("java/lang/Math.java");
        FileObject fString = sourcePath.findResource("java/lang/StringBuilder.java");

        Semaphore lck = new Semaphore(0);
        src.getFileRegistry().addFileRegistryListener((e) -> {
            lck.release();
        });
        // these are tested to fire events elsewhere, so hook at the event
        src.getFileRegistry().resolve(langMath, fMath);
        src.getFileRegistry().resolve(langString, fString);

        lck.acquire();
        // wait for the event task to finish:
        FileRegistry.RP.post(() -> {
        }).waitFinished();
        files = src.getSourceFiles();
        assertContainsSource(files, "java/lang/StringBuilder.java");
        assertContainsSource(files, "java/lang/Math.java");
    }

    public void xtestGraphSourceRetainsGraphContents_noload() throws Exception {
        LazySerDebugUtils.setLargeThreshold(100);

        URL bigv = GraphSourceTest.class.getResource("inlined_source.bgv");
        File f = new File(bigv.toURI());

        LazySerDebugUtils.loadResource(rootDocument, f);
        magnitudeGraph = findElement("3900:/After phase org.graalvm.compiler.phases.common.inlining.InliningPhase");

        GraphSource src = GraphSource.getGraphSource(magnitudeGraph);
        src.prepare().get();
        assertFalse(magnitudeGraph.getNodes().isEmpty());
        Reference<InputNode> ref = new WeakReference<>(magnitudeGraph.getNodes().iterator().next());
        magnitudeGraph = null;
        try {
            assertGC("", ref, Collections.singleton(rootDocument));
        } catch (AssertionError err) {
            // actually OK
            return;
        }
        fail("Graph was released");
    }

    public void testGraphContentsReleased_noload() throws Exception {
        LazySerDebugUtils.setLargeThreshold(100);

        URL bigv = GraphSourceTest.class.getResource("inlined_source.bgv");
        File f = new File(bigv.toURI());

        LazySerDebugUtils.loadResource(rootDocument, f);
        magnitudeGraph = findElement("3900:/After phase org.graalvm.compiler.phases.common.inlining.InliningPhase");

        GraphSource src = GraphSource.getGraphSource(magnitudeGraph);
        src.prepare().get();
        assertFalse(magnitudeGraph.getNodes().isEmpty());
        Reference<InputNode> ref = new WeakReference<>(magnitudeGraph.getNodes().iterator().next());
        Reference<InputGraph> refG = new WeakReference<>(magnitudeGraph);
        // forget the reference on source:
        src = null;
        magnitudeGraph = null;
        assertGC("", ref, Collections.singleton(rootDocument));
        assertGC("", refG, Collections.singleton(rootDocument));
    }

    /**
     * Checks that each node which defines 'nodeSourcePosition' has a stack
     * available. Bulk-loads information upfront.
     *
     * @throws Exception
     */
    public void testGetNodeStackBulk() throws Exception {
        PlatformLocationResolver.enabled = true;
        PlatformLocationResolver.enablePackage("java/util", true);
        GraphSource src = GraphSource.getGraphSource(magnitudeGraph);
        src.prepare().get();
        checkNodesStack(src);
    }

    private void checkNodesStack(GraphSource src) {
        for (InputNode n : magnitudeGraph.getNodes()) {
            String stackString = n.getProperties().getString(PROPNAME_NODE_SOURCE_POSITION, null);
            if (stackString == null) {
                NodeStack ns = src.getNodeStack(n);
                assertNull("No location property, must not provide stack", ns);
                continue;
            }

            NodeStack st = src.getNodeStack(n);
            assertNotNull("Stack is missing for node " + n.getId(), st);
            int lineCount = stackString.split("\n").length;
            assertEquals("Stack size is not correct", lineCount, st.size());
        }
    }

    /**
     * Loads incrementally node information one by one.
     *
     * @throws Exception
     */
    public void testGetNodeStackIncremental() throws Exception {
        PlatformLocationResolver.enabled = true;
        PlatformLocationResolver.enablePackage("java/util", true);
        GraphSource src = GraphSource.getGraphSource(magnitudeGraph);
        checkNodesStack(src);
    }

    public void testGetNodesPassingThrough() throws Exception {
        PlatformLocationResolver.enabled = true;

        GraphSource src = GraphSource.getGraphSource(magnitudeGraph);
        Collection<FileObject> files = src.getSourceFiles();
        Set<InputNode> foundNodes = new HashSet<>();

        for (FileObject f : files) {
            List<Location> locs = src.getFileLocations(f, false);
            for (Location l : locs) {
                Iterable<NodeStack> stackI = src.getNodesPassingThrough(l);
                for (NodeStack ns : stackI) {
                    foundNodes.add(ns.getNode());
                    // try to find the location within the stack:
                    boolean found = false;
                    for (NodeStack.Frame frame : ns) {
                        found |= frame.getLocation() == l;
                    }
                    assertTrue("Location " + l + " was not on the stack", found);
                }
            }
        }

        Set<InputNode> nodesWithPositions = src.getGraph().getNodes()
                        .stream().filter((n) -> n.getProperties().get(PROPNAME_NODE_SOURCE_POSITION) != null)
                        .collect(Collectors.toSet());
        assertTrue(foundNodes.containsAll(nodesWithPositions));
    }

    /**
     * Checks that nodes in 'nodesAt' results each list the location the result
     * was obtained for. Checks that just passthrough locations do not list any
     * nodes.
     *
     * @throws Exception
     */
    public void testGetNodesAt() throws Exception {
        PlatformLocationResolver.enabled = true;

        GraphSource src = GraphSource.getGraphSource(magnitudeGraph);
        Collection<FileObject> files = src.getSourceFiles();
        Set<InputNode> foundNodes = new HashSet<>();

        for (FileObject f : files) {
            List<Location> locs = src.getFileLocations(f, true);
            List<Location> locsPassing = src.getFileLocations(f, false);
            locsPassing.removeAll(locs);

            for (Location l : locsPassing) {
                assertTrue("Passing location conrresponds to a node", src.getNodesAt(l).isEmpty());
            }

            for (Location l : locs) {
                Collection<InputNode> nodes = src.getNodesAt(l);
                assertFalse("Cannot find a node for location with nodes", nodes.isEmpty());
                for (InputNode n : nodes) {
                    NodeStack ns = src.getNodeStack(n);
                    assertNotNull(ns);
                    foundNodes.add(ns.getNode());
                    // try to find the location within the stack:
                    boolean found = false;
                    for (NodeStack.Frame frame : ns) {
                        found |= frame.getLocation() == l;
                    }
                    assertTrue(found);
                }
            }
        }
    }

    public void testAddResolvedLocations() throws Exception {
        PlatformLocationResolver.enabled = true;
        PlatformLocationResolver.enablePackage("java/util", true);

        // for exmaple node #199 is in java/lang/AbstractStringBuilder.
        // node #10 is in Formatter, it will be loaded
        GraphSource src = GraphSource.getGraphSource(magnitudeGraph);
        NodeStack stack = src.getNodeStack(src.getGraph().getNode(10));
        assertTrue(stack.top().isResolved());
        PlatformLocationResolver.enablePackage("java/lang", true);

        stack = src.getNodeStack(src.getGraph().getNode(199));
        assertTrue(stack.top().isResolved());
    }

    public void testFindNodeLocation() throws Exception {
        GraphSource src = GraphSource.getGraphSource(magnitudeGraph);
        src.prepare().get();
        Collection<FileKey> allKeys = src.getFileKeys();
        Map<Location, Collection<InputNode>> foundLocations = new HashMap<>();
        for (InputNode n : magnitudeGraph.getNodes()) {
            if (n.getProperties().get(PROPNAME_NODE_SOURCE_POSITION) == null) {
                continue;
            }
            Location loc = src.findNodeLocation(n);
            assertNotNull(loc);
            Collection<InputNode> nodes = foundLocations.computeIfAbsent(loc, (l) -> new ArrayList<>(2));
            nodes.add(n);
        }

        for (FileKey fk : allKeys) {
            Set<Location> locs = new HashSet<>(src.getFileLocations(fk, true));
            for (Location l : locs) {
                Collection<InputNode> allNodes = src.getNodesAt(l);
                assertNotNull(allNodes);
                assertFalse(allNodes.isEmpty());

                Collection<InputNode> compare = foundLocations.remove(l);
                assertNotNull(compare);
                assertEquals(compare.size(), allNodes.size());
                assertTrue(compare.containsAll(allNodes));
            }
        }
    }

    /**
     * Checks that if Graph is released, the source does not hold any large data
     *
     * @throws Exception
     */
    public void disabled_testWorkWithReleasedGraph_noload() throws Exception {
        PlatformLocationResolver.enabled = true;
        LazySerDebugUtils.setLargeThreshold(100);

        URL bigv = GraphSourceTest.class.getResource("inlined_source.bgv");
        File f = new File(bigv.toURI());

        LazySerDebugUtils.loadResource(rootDocument, f);
        magnitudeGraph = findElement("3900:/After phase org.graalvm.compiler.phases.common.inlining.InliningPhase");
        assertFalse(magnitudeGraph.getNodes().isEmpty());
        Reference<InputNode> ref = new WeakReference<>(magnitudeGraph.getNodes().iterator().next());
        GraphSource src = GraphSource.getGraphSource(magnitudeGraph);
        src.prepare().get();
        FileKey fk = src.getFileKeys().iterator().next();
        Collection<Location> locs = src.getFileLocations(fk, false);
        magnitudeGraph = null;
        assertGC("Graph was not released", ref);

        assertNull(src.getGraph());
        assertTrue(src.getFileKeys().isEmpty());
        FileObject fMath = sourcePath.findResource("java/lang/Math.java");
        assertTrue(src.getFileLocations(fMath, true).isEmpty());

        for (Location l : locs) {
            assertFalse(src.getNodesPassingThrough(l).iterator().hasNext());
            assertTrue(src.getNodesAt(l).isEmpty());
        }
        assertTrue(src.getSourceFiles().isEmpty());

        InputNode n = new InputNode(0);
        assertNull(src.getNodeStack(n));
        assertNull(src.findNodeLocation(n));
    }

    public void testReadNewSourcePositions_noLoad() throws Exception {
        loadGraph("node-source-pos.bgv");
        InputGraph charGraph = findElement("32:/2: After phase GraphBuilder");
        assertNotNull(charGraph);

        GraphSource src = GraphSource.getGraphSource(charGraph);
        assertNotNull(src);
        InputNode loadField = charGraph.getNode(7);
        assertNotNull(loadField);
        NodeStack ns = src.getNodeStack(loadField);
        assertNotNull(ns);
        assertEquals(1, ns.size());
        assertEquals("java/lang/String.java", ns.get(0).getFileName());

    }
}
