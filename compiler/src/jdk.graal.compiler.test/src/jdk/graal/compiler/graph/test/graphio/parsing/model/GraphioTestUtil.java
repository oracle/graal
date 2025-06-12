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

package jdk.graal.compiler.graph.test.graphio.parsing.model;

import static jdk.graal.compiler.graphio.parsing.model.InputBlock.NO_BLOCK_NAME;
import static jdk.graal.compiler.graphio.parsing.model.KnownPropertyNames.PROPNAME_DUPLICATE;
import static jdk.graal.compiler.graphio.parsing.model.KnownPropertyNames.PROPNAME_NAME;
import static jdk.graal.compiler.graphio.parsing.model.KnownPropertyNames.PROPNAME_NODE_SOURCE_POSITION;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import jdk.graal.compiler.graphio.parsing.DataBinaryWriter;
import jdk.graal.compiler.graphio.parsing.model.FolderElement;
import jdk.graal.compiler.graphio.parsing.model.Group;
import jdk.graal.compiler.graphio.parsing.model.InputBlock;
import jdk.graal.compiler.graphio.parsing.model.InputGraph;
import jdk.graal.compiler.graphio.parsing.model.InputNode;
import jdk.graal.compiler.graphio.parsing.model.Properties;
import jdk.graal.compiler.graphio.parsing.model.Property;
import jdk.graal.compiler.util.CollectionsUtil;
import jdk.graal.compiler.util.EconomicHashMap;

public class GraphioTestUtil {
    public static boolean checkNotNulls(Object a, Object b) {
        if (a == null || b == null) {
            assertEquals(a, b);
            return false;
        }
        return true;
    }

    public static <T> void assertNot(T a, T b, BiConsumer<T, T> assertion, String failMessage) {
        try {
            assertion.accept(a, b);
        } catch (AssertionError e) {
            return;
        }
        fail(failMessage);
    }

    public static void assertElementsEquals(List<? extends FolderElement> a, List<? extends FolderElement> b) {
        if (checkNotNulls(a, b)) {
            assertEquals(a.size(), b.size());
            assertGroupElementsEquals(a, b);
            assertInputGraphElementsEquals(a, b);
        }
    }

    public static void assertGroupElementsEquals(List<? extends FolderElement> a, List<? extends FolderElement> b) {
        if (checkNotNulls(a, b)) {
            assertGroupsEquals(
                            a.stream().filter(x -> x instanceof Group).map(x -> (Group) x).collect(Collectors.toList()),
                            b.stream().filter(x -> x instanceof Group).map(x -> (Group) x).collect(Collectors.toList()));
        }
    }

    public static void assertGroupsEquals(List<? extends Group> a, List<? extends Group> b) {
        if (checkNotNulls(a, b)) {
            assertEquals(a.size(), b.size());
            for (int i = 0; i < a.size(); ++i) {
                assertGroupEquals(a.get(i), b.get(i));
            }
        }
    }

    public static void assertInputGraphElementsEquals(List<? extends FolderElement> a, List<? extends FolderElement> b) {
        if (checkNotNulls(a, b)) {
            assertInputGraphsEquals(
                            a.stream().filter(x -> x instanceof InputGraph).map(x -> (InputGraph) x).collect(Collectors.toList()),
                            b.stream().filter(x -> x instanceof InputGraph).map(x -> (InputGraph) x).collect(Collectors.toList()));
        }
    }

    public static void assertInputGraphsEquals(List<? extends InputGraph> a, List<? extends InputGraph> b) {
        if (checkNotNulls(a, b)) {
            assertEquals(a.size(), b.size());
            for (int i = 0; i < a.size(); ++i) {
                assertInputGraphEquals(a.get(i), b.get(i));
            }
        }
    }

    public static void assertGroupEquals(Group a, Group b) {
        if (checkNotNulls(a, b)) {
            completeContent(a);
            completeContent(b);
            assertEquals(a.getType(), b.getType());
            assertEquals(a.getMethod(), b.getMethod());
            assertPropertiesEquals(a.getProperties(), b.getProperties(), null);
            assertElementsEquals(a.getElements(), b.getElements());
        }
    }

    private static final Collection<String> GRAPH_EXCLUDE_PROPERTIES = CollectionsUtil.setOf(PROPNAME_NAME, PROPNAME_DUPLICATE);

    public static void assertInputGraphEquals(InputGraph a, InputGraph b) {
        if (checkNotNulls(a, b)) {
            completeContent(a);
            completeContent(b);
            assertEquals(a.getDumpId(), b.getDumpId());
            assertEquals(a.getFormat(), b.getFormat());
            assertArrayEquals(a.getArgs(), b.getArgs());
            assertInputNodesEquals(a.getNodes(), b.getNodes());
            assertEquals(new ArrayList<>(a.getEdges()), new ArrayList<>(b.getEdges()));
            assertBlocksEquals(a.getBlocks(), b.getBlocks());
            for (InputNode n : a.getNodes()) {
                assertInputBlockEquals(a.getBlock(n), b.getBlock(n));
            }
            assertPropertiesEquals(a.getProperties(), b.getProperties(), GRAPH_EXCLUDE_PROPERTIES);
        }
    }

    public static void assertInputNodesEquals(Collection<? extends InputNode> a, Collection<? extends InputNode> b) {
        if (checkNotNulls(a, b)) {
            assertEquals(a.size(), b.size());
            Map<Integer, InputNode> nodes = new EconomicHashMap<>();
            b.forEach(x -> nodes.put(x.getId(), x));
            a.forEach(x -> assertInputNodeEquals(x, nodes.get(x.getId())));
        }
    }

    public static void assertInputNodeEquals(InputNode a, InputNode b) {
        if (checkNotNulls(a, b)) {
            assertInputGraphsEquals(a.getSubgraphs(), b.getSubgraphs());
            assertPropertiesEquals(a.getProperties(), b.getProperties(), DataBinaryWriter.NODE_PROPERTY_EXCLUDE);
        }
    }

    public static void assertBlocksEquals(Collection<? extends InputBlock> a, Collection<? extends InputBlock> b) {
        if (checkNotNulls(a, b)) {
            Map<String, InputBlock> blocks = new EconomicHashMap<>();
            b.forEach(x -> blocks.put(x.getName(), x));
            a.forEach(x -> assertInputBlockEquals(x, blocks.get(x.getName())));
        }
    }

    public static void assertInputBlockEquals(InputBlock a, InputBlock b) {
        if ((a != null && b != null) || (a == null && b == null)) {
            assertEquals(a, b);
        } else if (!((a == null && NO_BLOCK_NAME.equals(b.getName())) || (b == null && NO_BLOCK_NAME.equals(a.getName())))) {
            assertEquals(a, b);
        }
    }

    public static void assertPropertiesEquals(Properties a, Properties b, Collection<String> exclude) {
        if (checkNotNulls(a, b)) {
            if (exclude == null || exclude.isEmpty()) {
                assertPropertiesEqualsSimple(a, b);
                assertPropertiesEqualsSimple(b, a);
            } else {
                assertPropertiesEqualsSimple(a, b, exclude);
                assertPropertiesEqualsSimple(b, a, exclude);
            }
        }
    }

    private static void assertPropertiesEqualsSimple(Properties a, Properties b, Collection<String> exclude) {
        for (Property<?> p : b) {
            final String name = p.getName();
            if (!exclude.contains(name)) {
                assertPropertyValueEquals(p.getValue(), a.get(name), name);
            }
        }
    }

    private static void assertPropertiesEqualsSimple(Properties a, Properties b) {
        for (Property<?> p : a) {
            final String name = p.getName();
            assertPropertyValueEquals(p.getValue(), b.get(name), name);
        }
    }

    private static void assertPropertyValueEquals(Object a, Object b, String name) {
        if (checkNotNulls(a, b)) {
            Class<?> klasA = a.getClass();
            Class<?> klasB = b.getClass();
            assertEquals("Property name: " + name + ".", klasA, klasB);
            if (klasB.isArray()) {
                assertEquals("Property name: " + name + ".", klasA.getComponentType(), klasB.getComponentType());
                assertEquals("Property name: " + name + ".", Array.getLength(a), Array.getLength(b));
                for (int i = 0; i < Array.getLength(a); ++i) {
                    assertEquals("Property name: " + name + ", index: " + i + ".", Array.get(a, i), Array.get(b, i));
                }
            } else {
                if (PROPNAME_NODE_SOURCE_POSITION.equals(name)) {
                    // data is read from older formats and contains file:line, but saved in newer
                    // format, and
                    // contain URI:line But textually they should be the same.
                    assertEquals("Property name: " + name + ".", String.valueOf(a), String.valueOf(b));
                } else {
                    assertEquals("Property name: " + name + ".", a, b);
                }
            }
        }
    }

    private static void completeContent(FolderElement element) {
        if (element instanceof Group.LazyContent) {
            final Future<?> fContents = ((Group.LazyContent<?>) element).completeContents(null);
            try {
                fContents.get();
            } catch (ExecutionException | InterruptedException e) {
                // ignore
            }
        }
    }
}
