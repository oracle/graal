/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.graph.test;

import static com.oracle.graal.graph.test.matchers.NodeIterableContains.*;
import static com.oracle.graal.graph.test.matchers.NodeIterableIsEmpty.*;
import static org.junit.Assert.*;

import org.junit.*;

import com.oracle.graal.graph.*;
import com.oracle.graal.nodeinfo.*;

public class NodeUsagesTests {

    @NodeInfo
    static class Def extends Node {
        public static Def create() {
            return USE_GENERATED_NODES ? new NodeUsagesTests_DefGen() : new Def();
        }
    }

    @NodeInfo
    static class Use extends Node {
        @Input Def in0;
        @Input Def in1;
        @Input Def in2;

        public static Use create(Def in0, Def in1, Def in2) {
            return USE_GENERATED_NODES ? new NodeUsagesTests_UseGen(in0, in1, in2) : new Use(in0, in1, in2);
        }

        Use(Def in0, Def in1, Def in2) {
            this.in0 = in0;
            this.in1 = in1;
            this.in2 = in2;
        }
    }

    @Test
    public void testReplaceAtUsages() {
        Graph graph = new Graph();
        Def def0 = graph.add(Def.create());
        Def def1 = graph.add(Def.create());
        Use use0 = graph.add(Use.create(def0, null, null));
        Use use1 = graph.add(Use.create(null, def0, null));
        Use use2 = graph.add(Use.create(null, null, def0));

        assertEquals(3, def0.usages().count());
        assertThat(def0.usages(), contains(use0));
        assertThat(def0.usages(), contains(use1));
        assertThat(def0.usages(), contains(use2));

        assertThat(def0.usages(), isNotEmpty());
        assertThat(def1.usages(), isEmpty());

        def0.replaceAtUsages(def1);

        assertThat(def0.usages(), isEmpty());

        assertEquals(3, def1.usages().count());
        assertThat(def1.usages(), contains(use0));
        assertThat(def1.usages(), contains(use1));
        assertThat(def1.usages(), contains(use2));

        assertThat(def1.usages(), isNotEmpty());
    }

    @Test
    public void testReplaceAtUsagesWithPredicateAll() {
        Graph graph = new Graph();
        Def def0 = graph.add(Def.create());
        Def def1 = graph.add(Def.create());
        Use use0 = graph.add(Use.create(def0, null, null));
        Use use1 = graph.add(Use.create(null, def0, null));
        Use use2 = graph.add(Use.create(null, null, def0));

        assertEquals(3, def0.usages().count());
        assertThat(def0.usages(), contains(use0));
        assertThat(def0.usages(), contains(use1));
        assertThat(def0.usages(), contains(use2));

        assertThat(def0.usages(), isNotEmpty());
        assertThat(def1.usages(), isEmpty());

        def0.replaceAtMatchingUsages(def1, u -> true);

        assertThat(def0.usages(), isEmpty());

        assertEquals(3, def1.usages().count());
        assertThat(def1.usages(), contains(use0));
        assertThat(def1.usages(), contains(use1));
        assertThat(def1.usages(), contains(use2));

        assertThat(def1.usages(), isNotEmpty());
    }

    @Test
    public void testReplaceAtUsagesWithPredicateNone() {
        Graph graph = new Graph();
        Def def0 = graph.add(Def.create());
        Def def1 = graph.add(Def.create());
        Use use0 = graph.add(Use.create(def0, null, null));
        Use use1 = graph.add(Use.create(null, def0, null));
        Use use2 = graph.add(Use.create(null, null, def0));

        assertEquals(3, def0.usages().count());
        assertThat(def0.usages(), contains(use0));
        assertThat(def0.usages(), contains(use1));
        assertThat(def0.usages(), contains(use2));

        assertThat(def0.usages(), isNotEmpty());
        assertThat(def1.usages(), isEmpty());

        def0.replaceAtMatchingUsages(def1, u -> false);

        assertThat(def1.usages(), isEmpty());

        assertEquals(3, def0.usages().count());
        assertThat(def0.usages(), contains(use0));
        assertThat(def0.usages(), contains(use1));
        assertThat(def0.usages(), contains(use2));

        assertThat(def0.usages(), isNotEmpty());
    }

    @Test
    public void testReplaceAtUsagesWithPredicate1() {
        Graph graph = new Graph();
        Def def0 = graph.add(Def.create());
        Def def1 = graph.add(Def.create());
        Use use0 = graph.add(Use.create(def0, null, null));
        Use use1 = graph.add(Use.create(null, def0, null));
        Use use2 = graph.add(Use.create(null, null, def0));

        assertEquals(3, def0.usages().count());
        assertThat(def0.usages(), contains(use0));
        assertThat(def0.usages(), contains(use1));
        assertThat(def0.usages(), contains(use2));

        assertThat(def0.usages(), isNotEmpty());
        assertThat(def1.usages(), isEmpty());

        def0.replaceAtMatchingUsages(def1, u -> u == use1);

        assertEquals(1, def1.usages().count());
        assertThat(def1.usages(), contains(use1));

        assertThat(def1.usages(), isNotEmpty());

        assertEquals(2, def0.usages().count());
        assertThat(def0.usages(), contains(use0));
        assertThat(def0.usages(), contains(use2));

        assertThat(def0.usages(), isNotEmpty());
    }

    @Test
    public void testReplaceAtUsagesWithPredicate2() {
        Graph graph = new Graph();
        Def def0 = graph.add(Def.create());
        Def def1 = graph.add(Def.create());
        Use use0 = graph.add(Use.create(def0, null, null));
        Use use1 = graph.add(Use.create(null, def0, null));
        Use use2 = graph.add(Use.create(null, null, def0));

        assertEquals(3, def0.usages().count());
        assertThat(def0.usages(), contains(use0));
        assertThat(def0.usages(), contains(use1));
        assertThat(def0.usages(), contains(use2));

        assertThat(def0.usages(), isNotEmpty());
        assertThat(def1.usages(), isEmpty());

        def0.replaceAtMatchingUsages(def1, u -> u == use2);

        assertEquals(1, def1.usages().count());
        assertThat(def1.usages(), contains(use2));

        assertThat(def1.usages(), isNotEmpty());

        assertEquals(2, def0.usages().count());
        assertThat(def0.usages(), contains(use0));
        assertThat(def0.usages(), contains(use1));

        assertThat(def0.usages(), isNotEmpty());
    }

    @Test
    public void testReplaceAtUsagesWithPredicate0() {
        Graph graph = new Graph();
        Def def0 = graph.add(Def.create());
        Def def1 = graph.add(Def.create());
        Use use0 = graph.add(Use.create(def0, null, null));
        Use use1 = graph.add(Use.create(null, def0, null));
        Use use2 = graph.add(Use.create(null, null, def0));

        assertEquals(3, def0.usages().count());
        assertThat(def0.usages(), contains(use0));
        assertThat(def0.usages(), contains(use1));
        assertThat(def0.usages(), contains(use2));

        assertThat(def0.usages(), isNotEmpty());
        assertThat(def1.usages(), isEmpty());

        def0.replaceAtMatchingUsages(def1, u -> u == use0);

        assertEquals(1, def1.usages().count());
        assertThat(def1.usages(), contains(use0));

        assertThat(def1.usages(), isNotEmpty());

        assertEquals(2, def0.usages().count());
        assertThat(def0.usages(), contains(use1));
        assertThat(def0.usages(), contains(use2));

        assertThat(def0.usages(), isNotEmpty());
    }

    @Test
    public void testReplaceAtUsagesWithPredicate02() {
        Graph graph = new Graph();
        Def def0 = graph.add(Def.create());
        Def def1 = graph.add(Def.create());
        Use use0 = graph.add(Use.create(def0, null, null));
        Use use1 = graph.add(Use.create(null, def0, null));
        Use use2 = graph.add(Use.create(null, null, def0));

        assertEquals(3, def0.usages().count());
        assertThat(def0.usages(), contains(use0));
        assertThat(def0.usages(), contains(use1));
        assertThat(def0.usages(), contains(use2));

        assertThat(def0.usages(), isNotEmpty());
        assertThat(def1.usages(), isEmpty());

        def0.replaceAtMatchingUsages(def1, u -> u != use1);

        assertEquals(1, def0.usages().count());
        assertThat(def0.usages(), contains(use1));

        assertThat(def0.usages(), isNotEmpty());

        assertEquals(2, def1.usages().count());
        assertThat(def1.usages(), contains(use0));
        assertThat(def1.usages(), contains(use2));

        assertThat(def1.usages(), isNotEmpty());
    }

    @Test
    public void testReplaceAtUsagesWithPredicate023() {
        Graph graph = new Graph();
        Def def0 = graph.add(Def.create());
        Def def1 = graph.add(Def.create());
        Use use0 = graph.add(Use.create(def0, null, null));
        Use use1 = graph.add(Use.create(null, def0, null));
        Use use2 = graph.add(Use.create(null, null, def0));
        Use use3 = graph.add(Use.create(null, null, def0));

        assertEquals(4, def0.usages().count());
        assertThat(def0.usages(), contains(use0));
        assertThat(def0.usages(), contains(use1));
        assertThat(def0.usages(), contains(use2));
        assertThat(def0.usages(), contains(use3));

        assertThat(def0.usages(), isNotEmpty());
        assertThat(def1.usages(), isEmpty());

        def0.replaceAtMatchingUsages(def1, u -> u != use1);

        assertEquals(1, def0.usages().count());
        assertThat(def0.usages(), contains(use1));

        assertThat(def0.usages(), isNotEmpty());

        assertEquals(3, def1.usages().count());
        assertThat(def1.usages(), contains(use0));
        assertThat(def1.usages(), contains(use2));
        assertThat(def1.usages(), contains(use3));

        assertThat(def1.usages(), isNotEmpty());
    }

    @Test
    public void testReplaceAtUsagesWithPredicate013() {
        Graph graph = new Graph();
        Def def0 = graph.add(Def.create());
        Def def1 = graph.add(Def.create());
        Use use0 = graph.add(Use.create(def0, null, null));
        Use use1 = graph.add(Use.create(null, def0, null));
        Use use2 = graph.add(Use.create(null, null, def0));
        Use use3 = graph.add(Use.create(null, null, def0));

        assertEquals(4, def0.usages().count());
        assertThat(def0.usages(), contains(use0));
        assertThat(def0.usages(), contains(use1));
        assertThat(def0.usages(), contains(use2));
        assertThat(def0.usages(), contains(use3));

        assertThat(def0.usages(), isNotEmpty());
        assertThat(def1.usages(), isEmpty());

        def0.replaceAtMatchingUsages(def1, u -> u != use2);

        assertEquals(1, def0.usages().count());
        assertThat(def0.usages(), contains(use2));

        assertThat(def0.usages(), isNotEmpty());

        assertEquals(3, def1.usages().count());
        assertThat(def1.usages(), contains(use0));
        assertThat(def1.usages(), contains(use1));
        assertThat(def1.usages(), contains(use3));

        assertThat(def1.usages(), isNotEmpty());
    }

    @Test
    public void testReplaceAtUsagesWithPredicate2_3() {
        Graph graph = new Graph();
        Def def0 = graph.add(Def.create());
        Def def1 = graph.add(Def.create());
        Use use0 = graph.add(Use.create(def0, null, null));
        Use use1 = graph.add(Use.create(null, def0, null));
        Use use2 = graph.add(Use.create(null, null, def0));
        Use use3 = graph.add(Use.create(null, null, def0));

        assertEquals(4, def0.usages().count());
        assertThat(def0.usages(), contains(use0));
        assertThat(def0.usages(), contains(use1));
        assertThat(def0.usages(), contains(use2));
        assertThat(def0.usages(), contains(use3));

        assertThat(def0.usages(), isNotEmpty());
        assertThat(def1.usages(), isEmpty());

        def0.replaceAtMatchingUsages(def1, u -> u == use2);

        assertEquals(1, def1.usages().count());
        assertThat(def1.usages(), contains(use2));

        assertThat(def1.usages(), isNotEmpty());

        assertEquals(3, def0.usages().count());
        assertThat(def0.usages(), contains(use0));
        assertThat(def0.usages(), contains(use1));
        assertThat(def0.usages(), contains(use3));

        assertThat(def0.usages(), isNotEmpty());
    }

    @Test
    public void testReplaceAtUsagesWithPredicate01() {
        Graph graph = new Graph();
        Def def0 = graph.add(Def.create());
        Def def1 = graph.add(Def.create());
        Use use0 = graph.add(Use.create(def0, null, null));
        Use use1 = graph.add(Use.create(null, def0, null));
        Use use2 = graph.add(Use.create(null, null, def0));

        assertEquals(3, def0.usages().count());
        assertThat(def0.usages(), contains(use0));
        assertThat(def0.usages(), contains(use1));
        assertThat(def0.usages(), contains(use2));

        assertThat(def0.usages(), isNotEmpty());
        assertThat(def1.usages(), isEmpty());

        def0.replaceAtMatchingUsages(def1, u -> u != use2);

        assertEquals(1, def0.usages().count());
        assertThat(def0.usages(), contains(use2));

        assertThat(def0.usages(), isNotEmpty());

        assertEquals(2, def1.usages().count());
        assertThat(def1.usages(), contains(use0));
        assertThat(def1.usages(), contains(use1));

        assertThat(def1.usages(), isNotEmpty());
    }

    @Test
    public void testReplaceAtUsagesWithPredicate12() {
        Graph graph = new Graph();
        Def def0 = graph.add(Def.create());
        Def def1 = graph.add(Def.create());
        Use use0 = graph.add(Use.create(def0, null, null));
        Use use1 = graph.add(Use.create(null, def0, null));
        Use use2 = graph.add(Use.create(null, null, def0));

        assertEquals(3, def0.usages().count());
        assertThat(def0.usages(), contains(use0));
        assertThat(def0.usages(), contains(use1));
        assertThat(def0.usages(), contains(use2));

        assertThat(def0.usages(), isNotEmpty());
        assertThat(def1.usages(), isEmpty());

        def0.replaceAtMatchingUsages(def1, u -> u != use0);

        assertEquals(1, def0.usages().count());
        assertThat(def0.usages(), contains(use0));

        assertThat(def0.usages(), isNotEmpty());

        assertEquals(2, def1.usages().count());
        assertThat(def1.usages(), contains(use1));
        assertThat(def1.usages(), contains(use2));

        assertThat(def1.usages(), isNotEmpty());
    }
}
