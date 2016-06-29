/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.graal.hotspot.test;

import org.junit.Assume;
import org.junit.Test;

import com.oracle.graal.hotspot.CompressEncoding;
import com.oracle.graal.hotspot.nodes.CompressionNode;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.debug.OpaqueNode;
import com.oracle.graal.nodes.graphbuilderconf.GraphBuilderConfiguration;
import com.oracle.graal.nodes.graphbuilderconf.GraphBuilderContext;
import com.oracle.graal.nodes.graphbuilderconf.InvocationPlugin;
import com.oracle.graal.nodes.graphbuilderconf.InvocationPlugins;
import com.oracle.graal.nodes.graphbuilderconf.InvocationPlugins.Registration;

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
    protected GraphBuilderConfiguration editGraphBuilderConfiguration(GraphBuilderConfiguration conf) {
        InvocationPlugins invocationPlugins = conf.getPlugins().getInvocationPlugins();
        Registration r = new Registration(invocationPlugins, DataPatchTest.class);
        r.register1("compressUncompress", Object.class, new InvocationPlugin() {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode arg) {
                CompressEncoding encoding = runtime().getVMConfig().getOopEncoding();
                ValueNode compressed = b.add(CompressionNode.compress(arg, encoding));
                ValueNode proxy = b.add(new OpaqueNode(compressed));
                b.addPush(JavaKind.Object, CompressionNode.uncompress(proxy, encoding));
                return true;
            }
        });
        return super.editGraphBuilderConfiguration(conf);
    }
}
