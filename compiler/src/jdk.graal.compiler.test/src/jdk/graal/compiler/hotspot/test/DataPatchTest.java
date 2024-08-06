/*
 * Copyright (c) 2014, 2018, Oracle and/or its affiliates. All rights reserved.
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

package jdk.graal.compiler.hotspot.test;

import jdk.graal.compiler.core.common.CompressEncoding;
import jdk.graal.compiler.hotspot.nodes.HotSpotCompressionNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.extended.OpaqueValueNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugin;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugins;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugins.Registration;
import org.junit.Assume;
import org.junit.Test;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class DataPatchTest extends HotSpotGraalCompilerTest {

    public static double doubleSnippet() {
        return 84.72;
    }

    @Test
    public void doubleTest() {
        test("doubleSnippet");
    }

    public static Object oopSnippet() {
        return "asdf";
    }

    @Test
    public void oopTest() {
        test("oopSnippet");
    }

    private static Object compressUncompress(Object obj) {
        return obj;
    }

    public static Object narrowOopSnippet() {
        return compressUncompress("narrowAsdf");
    }

    @Test
    public void narrowOopTest() {
        Assume.assumeTrue("skipping narrow oop data patch test", runtime().getVMConfig().useCompressedOops);
        test("narrowOopSnippet");
    }

    @Override
    protected void registerInvocationPlugins(InvocationPlugins invocationPlugins) {
        Registration r = new Registration(invocationPlugins, DataPatchTest.class);
        r.register(new InvocationPlugin("compressUncompress", Object.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode arg) {
                CompressEncoding encoding = runtime().getVMConfig().getOopEncoding();
                ValueNode compressed = b.add(HotSpotCompressionNode.compress(b.getGraph(), arg, encoding));
                ValueNode proxy = b.add(new OpaqueValueNode(compressed));
                b.addPush(JavaKind.Object, HotSpotCompressionNode.uncompress(b.getGraph(), proxy, encoding));
                return true;
            }
        });
        super.registerInvocationPlugins(invocationPlugins);
    }
}
