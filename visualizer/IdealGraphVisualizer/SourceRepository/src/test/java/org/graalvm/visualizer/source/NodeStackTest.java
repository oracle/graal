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

import jdk.graal.compiler.graphio.parsing.model.InputNode;
import org.openide.filesystems.FileObject;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author sdedic
 */
public class NodeStackTest extends GraphSourceTestBase {
    public NodeStackTest(String name) {
        super(name);
    }

    public void testStackBasic() throws Exception {
        PlatformLocationResolver.enabled = true;
        GraphSource src = GraphSource.getGraphSource(magnitudeGraph);
        src.prepare().get();

        for (FileObject f : src.getSourceFiles()) {
            List<Location> locs = src.getFileLocations(f, true);

            for (Location l : locs) {
                Collection<InputNode> nodes = src.getNodesAt(l);
                for (InputNode n : nodes) {
                    NodeStack stack = src.getNodeStack(n);
                    assertNotNull(stack);
                    assertFalse(stack.isEmpty());
                    assertSame(l, stack.top().getLocation());
                    assertSame(n, stack.getNode());
                    assertSame(magnitudeGraph, stack.getGraph());
                }
            }
        }
    }

    public void testStacksGCed() throws Exception {
        PlatformLocationResolver.enabled = true;
        GraphSource src = GraphSource.getGraphSource(magnitudeGraph);
        src.prepare().get();

        Collection<Reference<NodeStack>> refStacks = new ArrayList<>();
        for (FileObject f : src.getSourceFiles()) {
            List<Location> locs = src.getFileLocations(f, true);

            for (Location l : locs) {
                Collection<InputNode> nodes = src.getNodesAt(l);
                for (InputNode n : nodes) {
                    NodeStack stack = src.getNodeStack(n);
                    refStacks.add(new WeakReference<>(stack));
                }
            }
        }
        for (Reference<NodeStack> r : refStacks) {
            assertGC("Stack must GC", r);
        }
    }

    public void testStackFrames() throws Exception {
        PlatformLocationResolver.enabled = true;
        GraphSource src = GraphSource.getGraphSource(magnitudeGraph);
        src.prepare().get();

        FileObject fo = sourcePath.findResource("java/lang/AbstractStringBuilder.java");

        for (Location l : src.getFileLocations(fo, true)) {
            assertFalse(src.getNodesAt(l).isEmpty());
            NodeStack prevS = null;
            for (InputNode n : src.getNodesAt(l)) {
                NodeStack s = src.getNodeStack(n);
                NodeStack.Frame prev = s.top();

                assertFalse(s.equals(prevS));
                assertTrue(s.equals(s));

                assertNotNull(s.toString());

                prevS = s;
                assertNull(prev.getNested());

                assertSame(s.top(), s.get(0));
                assertSame(s.bottom(), s.get(s.size() - 1));

                for (int i = 1; i < s.size(); i++) {
                    NodeStack.Frame c = s.get(i);
                    assertSame(prev, c.getNested());
                    assertEquals(i, c.getDepth());
                    assertSame(n, c.getNode());
                    assertSame(magnitudeGraph, c.getGraph());
                    assertSame(src, c.getGraphSource());

                    if (i < s.size() - 1) {
                        assertSame(s.get(i + 1), c.getParent());
                    } else {
                        assertNull(c.getParent());
                    }
                    prev = c;
                }
                if (s.size() > 2) {
                    NodeStack.Frame top = s.top();
                    NodeStack.Frame bottom = s.bottom();

                    assertTrue(bottom.isParentOf(top));
                    assertTrue(top.isNestedIn(bottom));

                    for (int i = 1; i < s.size() - 1; i++) {
                        NodeStack.Frame c = s.get(i);
                        assertTrue(bottom.isParentOf(c));
                        assertTrue(c.isParentOf(top));

                        assertTrue(c.isNestedIn(bottom));
                        assertTrue(top.isNestedIn(c));
                    }
                }
            }

        }
    }
}
