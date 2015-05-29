/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.replacements.test;

import static com.oracle.graal.nodeinfo.InputType.*;
import static org.hamcrest.CoreMatchers.*;

import org.junit.*;

import com.oracle.graal.api.replacements.*;
import com.oracle.graal.compiler.common.type.*;
import com.oracle.graal.compiler.test.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.iterators.*;
import com.oracle.graal.nodeinfo.*;
import com.oracle.graal.nodeinfo.StructuralInput.Guard;
import com.oracle.graal.nodeinfo.StructuralInput.Memory;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.memory.*;
import com.oracle.jvmci.meta.*;

public class SubstitutionsTest extends GraalCompilerTest {

    @NodeInfo(allowedUsageTypes = {Memory})
    private static class TestMemory extends FixedWithNextNode implements MemoryNode {
        private static final NodeClass<TestMemory> TYPE = NodeClass.create(TestMemory.class);

        public TestMemory() {
            super(TYPE, StampFactory.forVoid());
        }

        @NodeIntrinsic
        public static native Memory memory();
    }

    @NodeInfo(allowedUsageTypes = {Guard})
    private static class TestGuard extends FloatingNode implements GuardingNode {
        private static final NodeClass<TestGuard> TYPE = NodeClass.create(TestGuard.class);

        @Input(Memory) MemoryNode memory;

        public TestGuard(ValueNode memory) {
            super(TYPE, StampFactory.forVoid());
            this.memory = (MemoryNode) memory;
        }

        @NodeIntrinsic
        public static native Guard guard(Memory memory);
    }

    @NodeInfo
    private static class TestValue extends FloatingNode {
        private static final NodeClass<TestValue> TYPE = NodeClass.create(TestValue.class);

        @Input(Guard) GuardingNode guard;

        public TestValue(ValueNode guard) {
            super(TYPE, StampFactory.forKind(Kind.Int));
            this.guard = (GuardingNode) guard;
        }

        @NodeIntrinsic
        public static native int value(Guard guard);
    }

    private static class TestMethod {

        public static int test() {
            return 42;
        }
    }

    @ClassSubstitution(TestMethod.class)
    private static class TestMethodSubstitution {

        @MethodSubstitution
        public static int test() {
            Memory memory = TestMemory.memory();
            Guard guard = TestGuard.guard(memory);
            return TestValue.value(guard);
        }
    }

    private static boolean substitutionsInstalled;

    public SubstitutionsTest() {
        if (!substitutionsInstalled) {
            getProviders().getReplacements().registerSubstitutions(TestMethod.class, TestMethodSubstitution.class);
            substitutionsInstalled = true;
        }
    }

    public static int callTest() {
        return TestMethod.test();
    }

    @Override
    protected boolean checkHighTierGraph(StructuredGraph graph) {
        // Check that the graph contains the expected test nodes.
        NodeIterable<ReturnNode> retNodes = graph.getNodes().filter(ReturnNode.class);
        Assert.assertTrue("expected exactly one ReturnNode", retNodes.count() == 1);
        ReturnNode ret = retNodes.first();

        Assert.assertThat(ret.result(), instanceOf(TestValue.class));
        TestValue value = (TestValue) ret.result();

        Assert.assertThat(value.guard, instanceOf(TestGuard.class));
        TestGuard guard = (TestGuard) value.guard;

        Assert.assertThat(guard.memory, instanceOf(TestMemory.class));
        TestMemory memory = (TestMemory) guard.memory;

        // Remove the test nodes, replacing them by the constant 42.
        // This implicitly makes sure that the rest of the graph is valid.
        ret.replaceFirstInput(value, graph.unique(ConstantNode.forInt(42)));
        value.safeDelete();
        guard.safeDelete();
        graph.removeFixed(memory);

        return true;
    }

    @Test
    public void snippetTest() {
        test("callTest");
    }
}
