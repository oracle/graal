/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.hotspot.test;

import static org.graalvm.compiler.test.SubprocessUtil.getVMCommandLine;
import static org.graalvm.compiler.test.SubprocessUtil.withoutDebuggerArguments;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.replacements.nodes.LogNode;
import org.graalvm.compiler.test.SubprocessUtil;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.code.InvalidInstalledCodeException;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Simple test to exercise {@linkplain LogNode} is actually producing output.
 */
public class LogNodeTest extends HotSpotGraalCompilerTest {

    @Rule public TestName name = new TestName();

    @Before
    public void checkSubprocess() {
        boolean inSubprocess = Boolean.getBoolean(getClass().getSimpleName() + ".SUBPROCESS");
        boolean isSpawnSubprocess = name.getMethodName().equals("spawnSubprocess");
        Assume.assumeTrue("subprocess already spawned -> skip", (inSubprocess && !isSpawnSubprocess) || (!inSubprocess && isSpawnSubprocess));
    }

    public static long testSnippet(long a, long b) {
        return a + b;
    }

    public void transformGraph(StructuredGraph graph, String s, ValueNode a, ValueNode b) {
        LogNode as = graph.add(new LogNode(s, a, b));
        graph.addAfterFixed(graph.start(), as);
    }

    @Test
    public void testPrintTwoArgs() throws InvalidInstalledCodeException {
        ResolvedJavaMethod method = getResolvedJavaMethod("testSnippet");
        StructuredGraph g = parseEager(method, AllowAssumptions.YES);
        transformGraph(g, "hello world %d %d", g.getParameter(0), g.getParameter(1));
        InstalledCode c = getCode(method, g);
        c.executeVarargs(42L, 77L);
    }

    @Test
    public void testPrintSingleArg() throws InvalidInstalledCodeException {
        ResolvedJavaMethod method = getResolvedJavaMethod("testSnippet");
        StructuredGraph g = parseEager(method, AllowAssumptions.YES);
        transformGraph(g, "hello universe %d", g.getParameter(0), null);
        InstalledCode c = getCode(method, g);
        c.executeVarargs(42L, 77L);
    }

    @Test
    public void testPrintNo() throws InvalidInstalledCodeException {
        ResolvedJavaMethod method = getResolvedJavaMethod("testSnippet");
        StructuredGraph g = parseEager(method, AllowAssumptions.YES);
        transformGraph(g, "hello cosmos", null, null);
        InstalledCode c = getCode(method, g);
        c.executeVarargs(42L, 77L);
    }

    private final String expected2Args = "hello world 42 77";
    private final String expected1Arg = "hello universe 42";
    private final String expectedNoArg = "hello cosmos";

    @Test
    public void spawnSubprocess() throws IOException, InterruptedException {
        String subprocessProperty = getClass().getSimpleName() + ".SUBPROCESS";
        List<String> vmArgs = withoutDebuggerArguments(getVMCommandLine());
        vmArgs.add(SubprocessUtil.PACKAGE_OPENING_OPTIONS);
        vmArgs.add("-D" + subprocessProperty + "=true");

        List<String> mainClassAndArgs = new ArrayList<>();
        mainClassAndArgs.add("com.oracle.mxtool.junit.MxJUnitWrapper");
        mainClassAndArgs.add(this.getClass().getName());

        SubprocessUtil.Subprocess proc = SubprocessUtil.java(vmArgs, mainClassAndArgs);

        Assert.assertTrue(proc.output.toString().contains(expected2Args));
        Assert.assertTrue(proc.output.toString().contains(expected1Arg));
        Assert.assertTrue(proc.output.toString().contains(expectedNoArg));

        Assert.assertEquals("Non-zero exit status for subprocess - check for failures: \n" + proc, 0, proc.exitCode);
    }

}
