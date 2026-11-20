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

import static org.junit.Assert.assertSame;

import org.graalvm.word.LocationIdentity;
import org.junit.Before;
import org.junit.Test;

import jdk.graal.compiler.core.common.memory.BarrierType;
import jdk.graal.compiler.core.common.memory.MemoryOrderMode;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.core.common.type.StampPair;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.FixedGuardNode;
import jdk.graal.compiler.nodes.NamedLocationIdentity;
import jdk.graal.compiler.nodes.ParameterNode;
import jdk.graal.compiler.nodes.ReturnNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.calc.IsNullNode;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraph;
import jdk.graal.compiler.nodes.memory.MemoryAnchorNode;
import jdk.graal.compiler.nodes.memory.ReadNode;
import jdk.graal.compiler.nodes.memory.address.OffsetAddressNode;
import jdk.graal.compiler.phases.common.DominatorBasedGlobalValueNumberingPhase.ValueMap;

import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.JavaKind;

public class EarlyGVNValueMapTest extends GraalCompilerTest {
    private StructuredGraph graph;
    private ValueMap map;
    private int nextParameter;

    @Before
    public void setup() {
        graph = new StructuredGraph.Builder(getInitialOptions(), getDebugContext()).build();
        map = new ValueMap();
        nextParameter = 0;
    }

    private ParameterNode parameter() {
        return graph.addWithoutUnique(new ParameterNode(nextParameter++, StampPair.createSingle(StampFactory.forKind(JavaKind.Object))));
    }

    private IsNullNode condition() {
        return graph.addWithoutUnique(new IsNullNode(parameter()));
    }

    private static FixedGuardNode newGuard(IsNullNode condition, boolean negated) {
        return new FixedGuardNode(condition, DeoptimizationReason.NullCheckException, DeoptimizationAction.InvalidateReprofile, negated);
    }

    private FixedGuardNode guard(IsNullNode condition, boolean negated) {
        return graph.add(newGuard(condition, negated));
    }

    private void addUnrelatedGuards() {
        for (int i = 0; i < 32; i++) {
            map.rememberNodeForGVN(guard(condition(), false));
        }
    }

    private OffsetAddressNode address() {
        return graph.addWithoutUnique(new OffsetAddressNode(parameter(), ConstantNode.forLong(0, graph)));
    }

    private ReadNode read(OffsetAddressNode address, LocationIdentity location) {
        return graph.add(new ReadNode(address, location, StampFactory.forKind(JavaKind.Int), BarrierType.NONE, MemoryOrderMode.PLAIN));
    }

    private void kill(LocationIdentity location) {
        map.killValuesByPotentialMemoryKill(graph.add(new MemoryAnchorNode(location)));
    }

    @Test
    public void testGuardInputsAndData() {
        addUnrelatedGuards();
        IsNullNode condition = condition();
        map.rememberNodeForGVN(guard(condition, false));
        assertTrue(map.hasSubstitute(guard(condition, false)));
        assertFalse(map.hasSubstitute(guard(condition, true)));
        assertTrue(map.hasSubstitute(newGuard(condition, false)));
    }

    @Test
    public void testUniqueInput() {
        addUnrelatedGuards();
        IsNullNode condition = condition();
        assertFalse(map.hasSubstitute(newGuard(condition, false)));
        assertFalse(map.hasSubstitute(guard(condition, false)));
    }

    @Test
    public void testGlobalDuplicateOutsideActiveState() {
        addUnrelatedGuards();
        IsNullNode condition = condition();
        FixedGuardNode outside = guard(condition, false);
        FixedGuardNode query = guard(condition, false);
        assertFalse(map.hasSubstitute(query));
        map.rememberNodeForGVN(outside);
        assertTrue(map.hasSubstitute(query));
    }

    @Test
    public void testHighlySharedInput() {
        IsNullNode condition = condition();
        map.rememberNodeForGVN(guard(condition, false));
        for (int i = 0; i < 32; i++) {
            guard(condition, false);
        }
        assertTrue(map.hasSubstitute(guard(condition, false)));
        assertFalse(map.hasSubstitute(guard(condition, true)));
    }

    @Test
    public void testInputMutationPreservesCandidateOrder() {
        addUnrelatedGuards();
        LocationIdentity location = NamedLocationIdentity.mutable("GVN-test-mutation");
        OffsetAddressNode a = address();
        OffsetAddressNode b = address();
        ReadNode first = read(a, location);
        ReadNode second = read(b, location);
        map.rememberNodeForGVN(first);
        map.rememberNodeForGVN(second);
        ReadNode query = read(b, location);
        // The usage list encounters second before first, but the array order must win.
        first.setAddress(b);
        assertFalse(map.hasSubstitute(read(a, location)));
        graph.start().setNext(first);
        first.setNext(second);
        second.setNext(query);
        ReturnNode result = graph.add(new ReturnNode(query));
        query.setNext(result);
        ControlFlowGraph cfg = ControlFlowGraph.newBuilder(graph).connectBlocks(true).computeLoops(true).computeDominators(true).build();
        assertTrue(map.substitute(query, cfg, null, null));
        assertTrue(query.isDeleted());
        assertSame(first, result.result());
    }

    @Test
    public void testMemoryKillsExcludeGlobalDuplicates() {
        addUnrelatedGuards();
        LocationIdentity mutable = NamedLocationIdentity.mutable("GVN-test-mutable");
        LocationIdentity immutable = NamedLocationIdentity.immutable("GVN-test-immutable");
        OffsetAddressNode address = address();
        map.rememberNodeForGVN(read(address, mutable));
        map.rememberNodeForGVN(read(address, immutable));
        ReadNode mutableQuery = read(address, mutable);
        ReadNode immutableQuery = read(address, immutable);
        assertTrue(map.hasSubstitute(mutableQuery));
        assertTrue(map.hasSubstitute(immutableQuery));
        kill(mutable);
        // The killed read is still a global duplicate and appears in the address's usages.
        assertFalse(map.hasSubstitute(mutableQuery));
        assertTrue(map.hasSubstitute(immutableQuery));
        map.rememberNodeForGVN(read(address, mutable));
        assertTrue(map.hasSubstitute(mutableQuery));
        kill(LocationIdentity.any());
        assertFalse(map.hasSubstitute(mutableQuery));
        assertTrue(map.hasSubstitute(immutableQuery));
    }
}
