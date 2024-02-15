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
package jdk.graal.compiler.hotspot.amd64.test;

import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_0;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_0;

import org.junit.Test;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.InputType;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.IsNullNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugin;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugins.Registration;
import jdk.graal.compiler.nodes.memory.ReadNode;
import jdk.graal.compiler.nodes.spi.Canonicalizable;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.phases.common.UseTrappingNullChecksPhase;
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
