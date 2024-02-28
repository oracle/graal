/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.hotspot.amd64.test;

import static org.graalvm.compiler.nodeinfo.NodeCycles.CYCLES_0;
import static org.graalvm.compiler.nodeinfo.NodeSize.SIZE_0;

import org.junit.Test;
import org.graalvm.compiler.api.directives.GraalDirectives;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.core.test.GraalCompilerTest;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.nodeinfo.InputType;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.IsNullNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugin;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins.Registration;
import org.graalvm.compiler.nodes.memory.ReadNode;
import org.graalvm.compiler.nodes.spi.Canonicalizable;
import org.graalvm.compiler.nodes.spi.CanonicalizerTool;
import org.graalvm.compiler.phases.common.UseTrappingNullChecksPhase;

import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.code.InvalidInstalledCodeException;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Test to ensure that late elimination of memory reads preserves necessary null check semantic with
 * respect to {@link UseTrappingNullChecksPhase}.
 */
public class ReadEliminateLowTierTest extends GraalCompilerTest {
    static class T {
        int x;
        int y;
        int z;
    }

    public static int trappingSnippet(T t) {
        if (t == null) {
            GraalDirectives.deoptimizeAndInvalidate();
            return -1;
        }
        /*
         * The first read from t here will act as trapping null check for all the others. We must
         * not remove this read if its used as a null check even if it does not have any usages any
         * more.
         */
        foldAfterTrappingNullChecks(t.x);
        int result = t.y + t.z;
        return result;
    }

    static void foldAfterTrappingNullChecks(@SuppressWarnings("unused") int i) {
    }

    @Override
    protected Plugins getDefaultGraphBuilderPlugins() {
        Plugins p = super.getDefaultGraphBuilderPlugins();
        Registration r = new Registration(p.getInvocationPlugins(), ReadEliminateLowTierTest.class);
        r.register(new InvocationPlugin("foldAfterTrappingNullChecks", int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode arg) {
                b.append(new FixedUsageUntilFinalCanon(arg));
                return true;
            }
        });
        return p;
    }

    /**
     * Node that gets optimized away be late canonicalization.
     */
    @NodeInfo(cycles = CYCLES_0, size = SIZE_0, allowedUsageTypes = {InputType.Anchor})
    public static class FixedUsageUntilFinalCanon extends FixedWithNextNode implements Canonicalizable {
        public static final NodeClass<FixedUsageUntilFinalCanon> TYPE = NodeClass.create(FixedUsageUntilFinalCanon.class);

        @OptionalInput ValueNode object;

        public FixedUsageUntilFinalCanon(ValueNode object) {
            super(TYPE, StampFactory.forVoid());
            this.object = object;
        }

        @Override
        public Node canonical(CanonicalizerTool tool) {
            // after trapping nulls
            if (graph().getNodes().filter(IsNullNode.class).count() == 0) {
                if (tool.allUsagesAvailable() && object instanceof ReadNode) {
                    ReadNode r = (ReadNode) object;
                    if (r.hasExactlyOneUsage() && r.usages().first().equals(this)) {
                        return null;
                    }
                }
            }
            return this;
        }
    }

    @Test
    public void test() throws InvalidInstalledCodeException {
        InstalledCode ic = getCode(getResolvedJavaMethod("trappingSnippet"));
        assert lastCompiledGraph != null;
        ic.executeVarargs(new T());
        ic.executeVarargs((Object) null);
    }

}
