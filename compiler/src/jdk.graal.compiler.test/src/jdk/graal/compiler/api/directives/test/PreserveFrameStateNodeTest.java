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
package jdk.graal.compiler.api.directives.test;

import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.nodes.DeoptimizeNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.extended.PreserveFrameStateNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration.Plugins;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugin;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugins.Registration;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class PreserveFrameStateNodeTest extends GraalCompilerTest {

    @Override
    protected Plugins getDefaultGraphBuilderPlugins() {
        Plugins plugins = super.getDefaultGraphBuilderPlugins();
        Registration registration = new Registration(plugins.getInvocationPlugins(), PreserveFrameStateNodeTest.class);
        registration.register(new InvocationPlugin("preserveFrameStateMarker") {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver) {
                b.add(new PreserveFrameStateNode());
                return true;
            }
        });
        return plugins;
    }

    public static int preserveBeforeDeopt(int value) {
        int result = value + 1;
        preserveFrameStateMarker();
        GraalDirectives.deoptimizeAndInvalidate();
        return result;
    }

    public static void preserveFrameStateMarker() {
    }

    @Test
    public void testPreservesCurrentFrameState() {
        ResolvedJavaMethod method = getResolvedJavaMethod("preserveBeforeDeopt");
        StructuredGraph graph = parseForCompile(method);
        PreserveFrameStateNode marker = graph.getNodes(PreserveFrameStateNode.TYPE).first();
        Assert.assertNotNull(marker);
        Assert.assertEquals(method, marker.stateAfter().getMethod());

        DeoptimizeNode deoptimize = graph.getNodes(DeoptimizeNode.TYPE).first();
        Assert.assertNotNull(deoptimize);
        Assert.assertSame(marker, deoptimize.predecessor());
    }
}
