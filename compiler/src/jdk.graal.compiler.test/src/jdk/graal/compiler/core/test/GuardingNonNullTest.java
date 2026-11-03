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

import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.nodes.FixedGuardNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.StructuredGraph.AllowAssumptions;
import jdk.graal.compiler.nodes.extended.BytecodeExceptionNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.BytecodeExceptionMode;
import jdk.vm.ci.code.InstalledCode;

public class GuardingNonNullTest extends GraalCompilerTest {
    private BytecodeExceptionMode mode = BytecodeExceptionMode.OmitAll;

    public static Object guarded(Object value) {
        return GraalDirectives.guardingNonNull(value);
    }

    @Override
    protected GraphBuilderConfiguration editGraphBuilderConfiguration(GraphBuilderConfiguration config) {
        return super.editGraphBuilderConfiguration(config).withBytecodeExceptionMode(mode);
    }

    private void checkGuard(BytecodeExceptionMode selectedMode) throws Throwable {
        mode = selectedMode;
        Assert.assertNull(guarded(null));
        StructuredGraph graph = parseEager("guarded", AllowAssumptions.NO);
        Assert.assertFalse("Directive must be intrinsified", graph.getInvokes().iterator().hasNext());
        Assert.assertEquals("Null must deoptimize in every exception mode", 1, graph.getNodes(FixedGuardNode.TYPE).count());
        Assert.assertEquals("Directive must not throw an explicit exception", 0, graph.getNodes().filter(BytecodeExceptionNode.class).count());
        InstalledCode code = getCode(getResolvedJavaMethod("guarded"), graph, true);
        Object value = new Object();
        Assert.assertSame(value, code.executeVarargs(value));
        Assert.assertNull(code.executeVarargs(new Object[]{null}));
    }

    @Test
    public void testCheckAll() throws Throwable {
        checkGuard(BytecodeExceptionMode.CheckAll);
    }

    @Test
    public void testOmitAll() throws Throwable {
        checkGuard(BytecodeExceptionMode.OmitAll);
    }
}
