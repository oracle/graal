/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.code.CompilationResult;
import jdk.graal.compiler.core.common.CompilationIdentifier;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.hotspot.nodes.AllocaNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.word.WordTypes;
import jdk.vm.ci.code.StackSlot;
import jdk.vm.ci.meta.JavaValue;
import org.junit.Test;

import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugin;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugins;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugins.Registration;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Regression test for JDK-8351036. Tests that stack offsets > Short.MAX_VALUE can be used.
 */
public class StackSlotLimitTest extends HotSpotGraalCompilerTest {

    /**
     * Returns the address of an on-stack buffer. Intrinsified by
     * {@link #registerInvocationPlugins}.
     *
     * @return non-zero value
     */
    public static long alloca(int size) {
        return 0xFFFFL + size;
    }

    static boolean hasStackSlotWithLargeOffset(CompilationResult res) {
        for (var info : res.getInfopoints()) {
            if (info.debugInfo != null) {
                for (var frame = info.debugInfo.frame(); frame != null; frame = frame.caller()) {
                    for (JavaValue value : frame.values) {
                        if (value instanceof StackSlot slot) {
                            int offset = slot.getRawOffset();
                            if (offset < Short.MIN_VALUE || offset > Short.MAX_VALUE) {
                                return true;
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    @Override
    protected CompilationResult compile(ResolvedJavaMethod installedCodeOwner, StructuredGraph graph, CompilationResult compilationResult, CompilationIdentifier compilationId, OptionValues options) {
        CompilationResult res = super.compile(installedCodeOwner, graph, compilationResult, compilationId, options);
        if (!hasStackSlotWithLargeOffset(res)) {
            // If this fails, then testSnippet1 needs to be updated
            throw new AssertionError(String.format("no infopoint containing a stack slot with a non-short offset:%n%s", res.getInfopoints()));
        }
        return res;
    }

    public static boolean testSnippet1() {
        long buffer = alloca(Short.MAX_VALUE + 1024);

        // This call seems to reliably produce code with safepoints
        // where the frame state includes stack slots with high offsets.
        GraalDirectives.opaque(new Exception());

        return buffer != 0L;
    }

    @Test
    public void test1() {
        test("testSnippet1");
    }

    @Override
    protected void registerInvocationPlugins(InvocationPlugins invocationPlugins) {
        Registration r = new Registration(invocationPlugins, StackSlotLimitTest.class);
        r.register(new InvocationPlugin("alloca", int.class) {
            @Override
            public boolean apply(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode arg) {
                GraalError.guarantee(arg.isConstant(), "%s must be compile-time constant", arg);
                WordTypes wordTypes = getProviders().getWordTypes();
                int size = arg.asJavaConstant().asInt();
                AllocaNode alloca = new AllocaNode(wordTypes, size, Byte.BYTES);
                b.addPush(wordTypes.getWordKind(), alloca);
                return true;
            }
        });
        super.registerInvocationPlugins(invocationPlugins);
    }
}
