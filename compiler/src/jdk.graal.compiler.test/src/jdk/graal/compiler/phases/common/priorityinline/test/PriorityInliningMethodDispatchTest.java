/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.phases.common.priorityinline.test;

import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.phases.common.priorityinline.CallTree;
import jdk.graal.compiler.phases.common.priorityinline.nodes.GenericNode;
import jdk.graal.compiler.phases.common.priorityinline.nodes.IndirectNode;
import jdk.graal.compiler.phases.common.priorityinline.nodes.SubgraphNode;

public class PriorityInliningMethodDispatchTest extends PriorityInliningTest {
    abstract static class Abs {
        abstract void m();
    }

    static class Concrete extends Abs {
        volatile int a;

        @Override
        void m() {
            a++;
        }
    }

    static class Sub1 extends Concrete {
    }

    static class Sub2 extends Concrete {
    }

    static class Sub3 extends Concrete {
    }

    static class Sub4 extends Concrete {
    }

    static class Sub5 extends Concrete {
    }

    static class Sub6 extends Concrete {
    }

    static class Sub7 extends Concrete {
    }

    static class Sub8 extends Concrete {
    }

    static class Sub9 extends Concrete {
    }

    static class Concrete2 extends Abs {
        volatile int a;

        @Override
        void m() {
            a++;
        }
    }

    static Abs[] impls4 = new Abs[]{new Sub1(), new Sub2(), new Sub3(), new Sub4()};
    static Abs[] impls9 = new Abs[]{new Sub1(), new Sub2(), new Sub3(), new Sub4(), new Sub5(), new Sub6(), new Sub7(), new Sub8(), new Sub9()};

    @Test
    public void sameMethod() {
        runLoop(1000);
        test("runLoop", 10_000_000);

        CallTree beforeInlining = getFullyExpandedCallTree("runLoop");
        Assert.assertEquals("First IC node must have one child.", 1, beforeInlining.root().children().get(0).children().size());
        Assert.assertTrue("First IC node must have a single concrete dispatch.", beforeInlining.root().children().get(0).children().get(0) instanceof SubgraphNode);
        Assert.assertEquals("Second IC node must have two children.", 2, beforeInlining.root().children().get(1).children().size());
        Assert.assertTrue("Second IC node must have a single concrete dispatch.", beforeInlining.root().children().get(1).children().get(0) instanceof SubgraphNode);
        Assert.assertTrue("Second IC node must have a single generic dispatch.", beforeInlining.root().children().get(1).children().get(1) instanceof GenericNode);

        CallTree afterInlining = getCallTreeAfterInlining("runLoop");
        Assert.assertEquals("Must have only one indirect child after inlining.", 1, afterInlining.root().children().count());
        Assert.assertTrue("Single child must be indirect.", afterInlining.root().children().get(0) instanceof IndirectNode);
    }

    @SuppressWarnings("unused")
    public static void runLoop(int count) {
        new Concrete2();
        for (int i = 0; i < count; i++) {
            for (Abs a : impls4) {
                a.m();
            }
            for (Abs a : impls9) {
                a.m();
            }
        }
    }
}
