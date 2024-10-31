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

import jdk.graal.compiler.graphio.parsing.model.InputGraph;
import jdk.graal.compiler.graphio.parsing.model.InputNode;
import org.junit.Ignore;
import org.openide.util.Lookup;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

/**
 * @author sdedic
 */
public class NodeLocationContextTest extends GraphSourceTestBase {
    public NodeLocationContextTest(String name) {
        super(name);
    }

    NodeLocationContext nctx;

    InputNode node155;
    GraphSource src;

    NodeLocationListener listener;

    protected void setUp() throws Exception {
        super.setUp();
        nctx = Lookup.getDefault().lookup(NodeLocationContext.class);
        node155 = magnitudeGraph.getNode(209);

    }

    protected void tearDown() throws Exception {
        if (listener != null) {
            nctx.removeNodeLocationListener(listener);
        }
        nctx.setGraphContext(null, Collections.emptySet());
        super.tearDown();
    }

    @Ignore("Unresolved test dependencies")
    public void testNodeContextData() throws Exception {
        src = GraphSource.getGraphSource(magnitudeGraph);
        InputNode n = magnitudeGraph.getNode(155);
        GraphSource src = GraphSource.getGraphSource(magnitudeGraph);
        NodeStack ns = src.getNodeStack(n);

        nctx.setGraphContext(src.getGraph(), Arrays.asList(new InputNode[]{
                n
        }));

        assertEquals(1, nctx.getGraphNodes().size());
        assertSame(src, nctx.getGraphSource());
        assertEquals(ns, nctx.getStack(n));

        // selected from no-data:
        assertNotNull(nctx.getSelectedFrame());
        assertNotNull(nctx.getSelectedLocation());

    }

    public void testNodesChangedFired() throws Exception {
        src = GraphSource.getGraphSource(magnitudeGraph);
        InputNode n = magnitudeGraph.getNode(209);
        GraphSource src = GraphSource.getGraphSource(magnitudeGraph);
        nctx.setGraphContext(src.getGraph(), Arrays.asList(new InputNode[]{
                n
        }));
        class L implements NodeLocationListener {
            boolean resolved;
            boolean locChanged;
            NodeLocationEvent changedEvent;

            @Override
            public void nodesChanged(NodeLocationEvent evt) {
                this.changedEvent = evt;
            }

            @Override
            public synchronized void locationsResolved(NodeLocationEvent evt) {
                this.resolved = true;
            }

            @Override
            public void selectedLocationChanged(NodeLocationEvent evt) {
                locChanged = true;
            }

            @Override
            public void selectedNodeChanged(NodeLocationEvent evt) {
            }
        }
        L l = new L();
        nctx.addNodeLocationListener(listener = l);

        InputNode other = magnitudeGraph.getNode(155);
        nctx.setGraphContext(src.getGraph(), Arrays.asList(new InputNode[]{
                other
        }));

        assertTrue(l.changedEvent.getNodes().contains(other));
    }

    @Ignore("Unresolved test dependencies")
    public void testLocationUnchangedForForeignNode() throws Exception {
        src = GraphSource.getGraphSource(magnitudeGraph);
        InputNode n = magnitudeGraph.getNode(209);
        GraphSource src = GraphSource.getGraphSource(magnitudeGraph);
        nctx.setGraphContext(src.getGraph(), Arrays.asList(new InputNode[]{
                n
        }));

        InputGraph og = (InputGraph) findElement("3900:/After phase org.graalvm.compiler.phases.PhaseSuite");
        assertNotNull(og);
        GraphSource os = GraphSource.getGraphSource(og);
        NodeStack ns = os.getNodeStack(og.getNode(11));

        class L implements NodeLocationListener {
            boolean resolved;
            boolean locChanged;
            NodeLocationEvent changedEvent;

            @Override
            public void nodesChanged(NodeLocationEvent evt) {
                this.changedEvent = evt;
            }

            @Override
            public synchronized void locationsResolved(NodeLocationEvent evt) {
                this.resolved = true;
            }

            @Override
            public void selectedLocationChanged(NodeLocationEvent evt) {
                locChanged = true;
            }

            @Override
            public void selectedNodeChanged(NodeLocationEvent evt) {
            }
        }
        L l = new L();
        nctx.addNodeLocationListener(listener = l);
        nctx.setSelectedLocation(ns.get(0));

        Collection<InputNode> nodes = nctx.getGraphNodes();
        assertEquals(1, nodes.size());
        assertSame(n, nodes.iterator().next());

        assertFalse(l.resolved);
        assertFalse(l.locChanged);
        assertNull(l.changedEvent);
    }

    @Ignore("Unresolved test dependencies")
    public void testLocationsResolved() throws Exception {
        // do not resolve everything:
        PlatformLocationResolver.enabled = true;
        PlatformLocationResolver.enablePackage("java/util", true);

        src = GraphSource.getGraphSource(magnitudeGraph);
        nctx.setGraphContext(src.getGraph(), Arrays.asList(new InputNode[]{
                node155
        }));
        Collection<NodeStack.Frame> unresolvedFrames = new ArrayList<>();
        FileKey unresolvedKey = null;
        NodeStack ns = src.getNodeStack(node155);
        for (NodeStack.Frame f : ns) {
            String s = f.getFileName();
            if (s.startsWith("java/util/")) {
                assertTrue(f.isResolved());
            } else {
                assertFalse(f.isResolved());
                if (unresolvedKey == null) {
                    unresolvedKey = f.getLocation().getFile();
                }
                unresolvedFrames.add(f);
            }
        }
        class L implements NodeLocationListener {
            boolean nodesChanged;
            boolean locChanged;
            NodeLocationEvent resolveEvent;

            @Override
            public void nodesChanged(NodeLocationEvent evt) {
                this.nodesChanged = true;
            }

            @Override
            public synchronized void locationsResolved(NodeLocationEvent evt) {
                this.resolveEvent = evt;
            }

            @Override
            public void selectedLocationChanged(NodeLocationEvent evt) {
                locChanged = true;
            }

            @Override
            public void selectedNodeChanged(NodeLocationEvent evt) {
            }
        }
        FileRegistryTest.waitForRevalidation().get();

        L l = new L();
        nctx.addNodeLocationListener(listener = l);
        PlatformLocationResolver.enablePackage("java/lang", true);
        // force resolution of java.util.something:
        src.getFileRegistry().attemptResolve(unresolvedKey);

        // must wait on revalidation, and then on events:
        FileRegistryTest.waitForRevalidation().get();
        // twice to get possible revalidation
        FileRegistryTest.waitForRevalidation().get();
        synchronized (l) {
            assertNotNull(l.resolveEvent);
            assertTrue(unresolvedFrames.containsAll(l.resolveEvent.getResolvedFrames()));
            assertTrue(l.resolveEvent.getResolvedFrames().containsAll(unresolvedFrames));
        }

    }

    @Ignore("Unresolved test dependencies")
    public void testSelectedLocationReset() throws Exception {
        src = GraphSource.getGraphSource(magnitudeGraph);
        nctx.setGraphContext(src.getGraph(), Arrays.asList(new InputNode[]{
                node155
        }));
        NodeStack ns = src.getNodeStack(node155);
        nctx.setSelectedLocation(ns.get(2));
        class L implements NodeLocationListener {
            boolean nodesChanged;
            boolean locChanged;

            @Override
            public void nodesChanged(NodeLocationEvent evt) {
                this.nodesChanged = true;
            }

            @Override
            public void locationsResolved(NodeLocationEvent evt) {
            }

            @Override
            public void selectedLocationChanged(NodeLocationEvent evt) {
                locChanged = true;
            }

            @Override
            public void selectedNodeChanged(NodeLocationEvent evt) {
            }
        }
        L l = new L();
        nctx.addNodeLocationListener(listener = l);

        InputNode n = src.getGraph().getNode(156);
        nctx.setGraphContext(magnitudeGraph, Collections.singleton(n));

        assertTrue(l.nodesChanged);
        assertTrue(l.locChanged);
        assertNull(nctx.getSelectedFrame());
        assertNull(nctx.getSelectedLocation());
    }

    @Ignore("Unresolved test dependencies")
    public void testSelectedLocationUnchanged() throws Exception {
        src = GraphSource.getGraphSource(magnitudeGraph);
        nctx.setGraphContext(src.getGraph(), Arrays.asList(new InputNode[]{
                node155
        }));
        NodeStack ns = src.getNodeStack(node155);
        nctx.setSelectedLocation(ns.get(0));

        InputNode node156 = src.getGraph().getNode(156);
        class L implements NodeLocationListener {
            boolean nodesChanged;
            boolean locChanged;

            @Override
            public void nodesChanged(NodeLocationEvent evt) {
                this.nodesChanged = true;
            }

            @Override
            public void locationsResolved(NodeLocationEvent evt) {
            }

            @Override
            public void selectedLocationChanged(NodeLocationEvent evt) {
                locChanged = true;
            }

            @Override
            public void selectedNodeChanged(NodeLocationEvent evt) {
            }
        }

        L l = new L();
        nctx.addNodeLocationListener(listener = l);
        nctx.setGraphContext(src.getGraph(), Arrays.asList(new InputNode[]{
                node155, node156
        }));
        assertSame(ns.get(0), nctx.getSelectedFrame());

        assertTrue(l.nodesChanged);
        assertFalse(l.locChanged);
    }


    @Ignore("Unresolved test dependencies")
    public void testSelectedLocationChanged() throws Exception {
        src = GraphSource.getGraphSource(magnitudeGraph);
        nctx.setGraphContext(src.getGraph(), Arrays.asList(new InputNode[]{
                node155
        }));
        NodeStack ns = src.getNodeStack(node155);
        class L implements NodeLocationListener {
            boolean nodesChanged;
            boolean locChanged;

            @Override
            public void nodesChanged(NodeLocationEvent evt) {
                fail("Nodes were not changed");
            }

            @Override
            public void locationsResolved(NodeLocationEvent evt) {
                fail("No location should be resolved");
            }

            @Override
            public void selectedLocationChanged(NodeLocationEvent evt) {
                assertSame(ns.get(1), evt.getSelectedFrame());
            }

            @Override
            public void selectedNodeChanged(NodeLocationEvent evt) {
            }
        }

        L l = new L();
        nctx.addNodeLocationListener(listener = l);
        nctx.setSelectedLocation(ns.get(1));

    }
}
