/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.graph.test;

import jdk.graal.compiler.graph.Graph.NodeEvent;
import jdk.graal.compiler.graph.Graph.NodeEventListener;
import jdk.graal.compiler.graph.Graph.NodeEventScope;

import org.junit.Test;

import jdk.graal.compiler.graph.Graph;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.options.OptionValues;

/**
 * Tests adding and removing {@link NodeEventListener}s to/from a graph's chain of listeners.
 */
public class GraphNodeEventListenerTest extends GraphTest {

    private static boolean listener0Called = false;
    private static boolean listener1Called = false;

    public class Listener0 extends NodeEventListener {
        @Override
        public void changed(NodeEvent e, Node node) {
            listener0Called = true;
        }
    }

    public class Listener1 extends NodeEventListener {
        @Override
        public void changed(NodeEvent e, Node node) {
            listener1Called = true;
        }
    }

    private static void resetListenersCalled() {
        listener0Called = false;
        listener1Called = false;
    }

    @Test
    @SuppressWarnings("try")
    public void testAddRemove() {
        OptionValues options = getOptions();
        Graph graph = new Graph(options, getDebug(options));

        resetListenersCalled();

        try (NodeEventScope nes0 = graph.trackNodeEvents(new Listener0())) {
            graph.addWithoutUnique(ConstantNode.forInt(0));
        }
        assert listener0Called;

        resetListenersCalled();

        graph.addWithoutUnique(ConstantNode.forInt(1));
        assert !listener0Called;
    }

    @Test
    @SuppressWarnings("try")
    public void testAddRemoveUnstructured() {
        OptionValues options = getOptions();
        Graph graph = new Graph(options, getDebug(options));

        resetListenersCalled();

        NodeEventScope nes1 = null;
        try (NodeEventScope nes0 = graph.trackNodeEvents(new Listener0())) {
            nes1 = graph.trackNodeEvents(new Listener1());
            graph.addWithoutUnique(ConstantNode.forInt(0));
        }
        assert listener0Called;
        assert listener1Called;

        resetListenersCalled();

        graph.addWithoutUnique(ConstantNode.forInt(1));
        assert !listener0Called;
        assert listener1Called;

        resetListenersCalled();

        nes1.close();
        graph.addWithoutUnique(ConstantNode.forInt(2));
        assert !listener0Called;
        assert !listener1Called;
    }
}
