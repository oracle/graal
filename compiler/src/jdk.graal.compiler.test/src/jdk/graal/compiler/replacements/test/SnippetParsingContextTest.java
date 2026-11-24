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
package jdk.graal.compiler.replacements.test;

import java.util.LinkedHashSet;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.api.replacements.Snippet;
import jdk.graal.compiler.bytecode.Bytecode;
import jdk.graal.compiler.bytecode.BytecodeProvider;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.java.StoreFieldNode;
import jdk.vm.ci.code.BytecodeFrame;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class SnippetParsingContextTest extends SnippetsTest {

    private static long sink;

    private static long storeAndReturn(long value) {
        sink = value;
        return value;
    }

    @Snippet
    public static long nestedSnippet(long first, int last) {
        if (last >= 0) {
            return storeAndReturn(first + last);
        }
        return storeAndReturn(first - last);
    }

    private static native long original(long first, int last);

    @Snippet
    public static long partialSnippet(long first, int last) {
        if (last >= 0) {
            return storeAndReturn(first + last);
        }
        return original(first, last);
    }

    private StructuredGraph parseSnippet(String name, ResolvedJavaMethod originalMethod, Set<ResolvedJavaMethod> parsedMethods) {
        BytecodeProvider recordingProvider = new BytecodeProvider() {
            @Override
            public Bytecode getBytecode(ResolvedJavaMethod method) {
                parsedMethods.add(method);
                return bytecodeProvider.getBytecode(method);
            }

            @Override
            public boolean supportsInvokedynamic() {
                return bytecodeProvider.supportsInvokedynamic();
            }

            @Override
            public boolean shouldRecordMethodDependencies() {
                return bytecodeProvider.shouldRecordMethodDependencies();
            }
        };
        return installer.makeGraph(getDebugContext(), recordingProvider, getResolvedJavaMethod(name), null, null, originalMethod, false, null);
    }

    @Test
    public void nestedParsingInheritsBytecodeProvider() {
        Set<ResolvedJavaMethod> parsedMethods = new LinkedHashSet<>();
        StructuredGraph graph = parseSnippet("nestedSnippet", null, parsedMethods);
        Assert.assertTrue(parsedMethods.contains(getResolvedJavaMethod("nestedSnippet")));
        Assert.assertTrue(parsedMethods.contains(getResolvedJavaMethod("storeAndReturn")));
        Assert.assertFalse(graph.getInvokes().iterator().hasNext());
        Assert.assertEquals(BytecodeFrame.BEFORE_BCI, graph.start().stateAfter().bci);
        Assert.assertFalse(graph.getNodes().filter(StoreFieldNode.class).isEmpty());
        for (StoreFieldNode store : graph.getNodes().filter(StoreFieldNode.class)) {
            Assert.assertEquals(BytecodeFrame.AFTER_BCI, store.stateAfter().bci);
        }
    }

    @Test
    public void partialExitKeepsPlaceholderCallSite() {
        ResolvedJavaMethod originalMethod = getResolvedJavaMethod("original");
        StructuredGraph graph = parseSnippet("partialSnippet", originalMethod, new LinkedHashSet<>());
        Assert.assertEquals(BytecodeFrame.BEFORE_BCI, graph.start().stateAfter().bci);
        int exits = 0;
        for (Invoke invoke : graph.getInvokes()) {
            Assert.assertEquals(originalMethod, invoke.getTargetMethod());
            Assert.assertEquals(BytecodeFrame.UNKNOWN_BCI, invoke.bci());
            exits++;
        }
        Assert.assertEquals(1, exits);
    }
}
