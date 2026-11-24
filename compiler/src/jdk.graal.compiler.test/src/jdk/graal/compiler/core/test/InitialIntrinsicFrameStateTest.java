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
package jdk.graal.compiler.core.test;

import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.core.common.CompilationIdentifier;
import jdk.graal.compiler.java.FrameStateBuilder;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.replacements.GraphKit;

public class InitialIntrinsicFrameStateTest extends GraalCompilerTest {
    public static long mixedArguments(long first, int last) {
        int extra = last + 1;
        return first + extra;
    }

    public static int narrowArguments(int first, int last) {
        return first + last;
    }

    public double instanceArguments(long first, double middle, int last) {
        return first + middle + last;
    }

    private record Fixture(GraphKit kit, FrameStateBuilder state) {
    }

    private Fixture fixture(String name) {
        GraphKit kit = new GraphKit(getDebugContext(), getResolvedJavaMethod(name), getProviders(), getDefaultGraphBuilderPlugins(),
                        CompilationIdentifier.INVALID_COMPILATION_ID, null, false, false) {
        };
        return new Fixture(kit, new FrameStateBuilder(kit, getResolvedJavaMethod(name), kit.getGraph()));
    }

    private static FrameState entryState(Fixture fixture) {
        FrameState entry = fixture.state().createInitialIntrinsicFrameState();
        Assert.assertEquals(fixture.state().getMethod(), entry.getMethod());
        Assert.assertEquals(fixture.state().localsSize(), entry.localsSize());
        Assert.assertEquals(0, entry.bci);
        Assert.assertEquals(0, entry.stackSize());
        return entry;
    }

    @Test
    public void mixedWidthConstantsAndUnusedLocals() {
        Fixture f = fixture("mixedArguments");
        ValueNode first = ConstantNode.forLong(17, f.kit().getGraph());
        ValueNode last = ConstantNode.forInt(23, f.kit().getGraph());
        f.state().initializeFromArgumentsArray(new ValueNode[]{first, last});

        FrameState entry = entryState(f);
        Assert.assertSame(first, entry.localAt(0));
        Assert.assertNull(entry.localAt(1));
        Assert.assertSame(last, entry.localAt(2));
        Assert.assertTrue("Fixture must have a nonargument local", entry.localsSize() > 3);
        for (int slot = 3; slot < entry.localsSize(); slot++) {
            Assert.assertNull(entry.localAt(slot));
        }
    }

    @Test
    public void oneSlotArguments() {
        Fixture f = fixture("narrowArguments");
        ValueNode first = ConstantNode.forInt(17, f.kit().getGraph());
        ValueNode last = ConstantNode.forInt(23, f.kit().getGraph());
        f.state().initializeFromArgumentsArray(new ValueNode[]{first, last});

        FrameState entry = entryState(f);
        Assert.assertSame(first, entry.localAt(0));
        Assert.assertSame(last, entry.localAt(1));
    }

    @Test
    public void receiverAndConsecutiveWideArguments() {
        Fixture f = fixture("instanceArguments");
        List<ValueNode> arguments = new ArrayList<>();
        f.state().initializeForMethodStart(f.kit().getGraph().getAssumptions(), false, getDefaultGraphBuilderPlugins(), arguments);

        FrameState entry = entryState(f);
        Assert.assertSame(arguments.get(0), entry.localAt(0));
        Assert.assertSame(arguments.get(1), entry.localAt(1));
        Assert.assertNull(entry.localAt(2));
        Assert.assertSame(arguments.get(2), entry.localAt(3));
        Assert.assertNull(entry.localAt(4));
        Assert.assertSame(arguments.get(3), entry.localAt(5));
    }
}
