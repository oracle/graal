/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.graal.snippets.riscv64;

import java.util.Map;

import org.graalvm.word.LocationIdentity;
import org.graalvm.word.Pointer;

import com.oracle.svm.core.FrameAccess;
import com.oracle.svm.core.graal.nodes.VaListInitializationNode;
import com.oracle.svm.core.graal.nodes.VaListNextArgNode;
import com.oracle.svm.core.graal.snippets.NodeLoweringProvider;
import com.oracle.svm.core.graal.snippets.SubstrateTemplates;
import com.oracle.svm.core.graal.stackvalue.StackValueNode;
import com.oracle.svm.core.graal.stackvalue.StackValueNode.StackSlotIdentity;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.api.replacements.Snippet;
import jdk.graal.compiler.core.common.memory.BarrierType;
import jdk.graal.compiler.core.common.memory.MemoryOrderMode;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.memory.WriteNode;
import jdk.graal.compiler.nodes.memory.address.OffsetAddressNode;
import jdk.graal.compiler.nodes.spi.LoweringTool;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.util.Providers;
import jdk.graal.compiler.replacements.SnippetTemplate;
import jdk.graal.compiler.replacements.SnippetTemplate.Arguments;
import jdk.graal.compiler.replacements.SnippetTemplate.SnippetInfo;
import jdk.graal.compiler.replacements.Snippets;
import jdk.vm.ci.code.BytecodeFrame;

/**
 * Implementation of C {@code va_list} handling for System V systems on RISCV64 (Linux). A
 * {@code va_list} is used for passing the arguments of a C varargs function ({@code ...} in the
 * argument list) to another function. Varargs functions use the same calling convention as other
 * functions, which entails passing the first few arguments in registers and the remaining arguments
 * (if any) on the stack. The {@code va_list} type is void*.
 * <p>
 * Reading a {@code va_list} requires knowing the types of the arguments. General-purpose values
 * (integers and pointers) are passed in the eight 64-bit registers {@code x10} to {@code x17}.
 * Floating-point values are passed in the eight 64-bit registers {@code f10} through {@code f17}.
 * The callee is responsible for copying the contents of the registers used to pass variadic
 * arguments to the {@code vararg} save area, which must be contiguous with the arguments passed on
 * the stack.
 * <p>
 * Reading an argument from the {@code va_list} only necessitates to read the value pointed by
 * {@code va_list} and then increment the pointer using the size of the value read.
 * <p>
 * References:<br>
 * <cite>https://github.com/riscv-non-isa/riscv-elf-psabi-doc/blob/master/riscv-cc.adoc</cite><br>
 */
final class PosixRISCV64VaListSnippets extends SubstrateTemplates implements Snippets {

    private static final int STACK_AREA_GP_ALIGNMENT = 8;
    private static final int STACK_AREA_FP_ALIGNMENT = 8;

    private static final StackSlotIdentity vaListIdentity = new StackSlotIdentity("PosixRISCV64VaListSnippets.vaListSlotIdentifier", false);

    @Snippet
    protected static double vaArgDoubleSnippet(Pointer vaListPointer) {
        Pointer vaList = vaListPointer.readWord(0);
        vaListPointer.writeWord(0, vaList.add(STACK_AREA_FP_ALIGNMENT));
        return vaList.readDouble(0);
    }

    @Snippet
    protected static float vaArgFloatSnippet(Pointer vaListPointer) {
        // float is always promoted to double when passed in varargs
        return (float) vaArgDoubleSnippet(vaListPointer);
    }

    @Snippet
    protected static long vaArgLongSnippet(Pointer vaListPointer) {
        Pointer vaList = vaListPointer.readWord(0);
        vaListPointer.writeWord(0, vaList.add(STACK_AREA_GP_ALIGNMENT));
        return vaList.readLong(0);
    }

    @Snippet
    protected static int vaArgIntSnippet(Pointer vaListPointer) {
        return (int) vaArgLongSnippet(vaListPointer);
    }

    @SuppressWarnings("unused")
    public static void registerLowerings(OptionValues options, Providers providers, Map<Class<? extends Node>, NodeLoweringProvider<?>> lowerings) {
        new PosixRISCV64VaListSnippets(options, providers, lowerings);
    }

    private final SnippetInfo vaArgDouble;
    private final SnippetInfo vaArgFloat;
    private final SnippetInfo vaArgLong;
    private final SnippetInfo vaArgInt;

    private PosixRISCV64VaListSnippets(OptionValues options, Providers providers, Map<Class<? extends Node>, NodeLoweringProvider<?>> lowerings) {
        super(options, providers);

        this.vaArgDouble = snippet(providers, PosixRISCV64VaListSnippets.class, "vaArgDoubleSnippet");
        this.vaArgFloat = snippet(providers, PosixRISCV64VaListSnippets.class, "vaArgFloatSnippet");
        this.vaArgLong = snippet(providers, PosixRISCV64VaListSnippets.class, "vaArgLongSnippet");
        this.vaArgInt = snippet(providers, PosixRISCV64VaListSnippets.class, "vaArgIntSnippet");

        lowerings.put(VaListInitializationNode.class, new VaListInitializationSnippetsLowering());
        lowerings.put(VaListNextArgNode.class, new VaListSnippetsLowering());
    }

    protected class VaListInitializationSnippetsLowering implements NodeLoweringProvider<VaListInitializationNode> {
        @Override
        public void lower(VaListInitializationNode node, LoweringTool tool) {
            StructuredGraph graph = node.graph();

            StackValueNode stackValueNode = graph.add(StackValueNode.create(FrameAccess.wordSize(), vaListIdentity, true));
            FrameState frameState = new FrameState(BytecodeFrame.UNKNOWN_BCI);
            frameState.invalidateForDeoptimization();
            stackValueNode.setStateAfter(graph.add(frameState));

            OffsetAddressNode address = graph.unique(new OffsetAddressNode(stackValueNode, graph.unique(ConstantNode.forLong(0))));
            WriteNode writeNode = graph.add(new WriteNode(address, LocationIdentity.any(), node.getVaList(), BarrierType.NONE, MemoryOrderMode.PLAIN));

            FixedNode successor = node.next();
            node.replaceAndDelete(stackValueNode);
            stackValueNode.setNext(successor);

            graph.addAfterFixed(stackValueNode, writeNode);
            stackValueNode.lower(tool);
        }
    }

    protected class VaListSnippetsLowering implements NodeLoweringProvider<VaListNextArgNode> {
        @Override
        public void lower(VaListNextArgNode node, LoweringTool tool) {
            SnippetInfo snippet;
            switch (node.getStackKind()) {
                case Double:
                    snippet = vaArgDouble;
                    break;
                case Float:
                    snippet = vaArgFloat;
                    break;
                case Long:
                    snippet = vaArgLong;
                    break;
                case Int:
                    // everything narrower than int is promoted to int when passed in varargs
                    snippet = vaArgInt;
                    break;
                default:
                    // getStackKind() should be at least int
                    throw VMError.shouldNotReachHereUnexpectedInput(node.getStackKind()); // ExcludeFromJacocoGeneratedReport
            }
            Arguments args = new Arguments(snippet, node.graph(), tool.getLoweringStage());
            args.add("vaListPointer", node.getVaList());
            template(tool, node, args).instantiate(tool.getMetaAccess(), node, SnippetTemplate.DEFAULT_REPLACER, args);
        }
    }
}
