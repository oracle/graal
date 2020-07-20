/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.graal.snippets.amd64;

import java.util.Map;

import org.graalvm.compiler.api.replacements.Snippet;
import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.debug.DebugHandlersFactory;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.replacements.SnippetTemplate;
import org.graalvm.compiler.replacements.SnippetTemplate.Arguments;
import org.graalvm.compiler.replacements.SnippetTemplate.SnippetInfo;
import org.graalvm.compiler.replacements.Snippets;
import org.graalvm.word.Pointer;

import com.oracle.svm.core.graal.nodes.VaListNextArgNode;
import com.oracle.svm.core.graal.snippets.NodeLoweringProvider;
import com.oracle.svm.core.graal.snippets.SubstrateTemplates;
import com.oracle.svm.core.util.VMError;

/**
 * Implementation of C {@code va_list} handling for System V systems on AMD64 (Linux, but same
 * behavior on Darwin). A {@code va_list} is used for passing the arguments of a C varargs function
 * ({@code ...} in the argument list) to another function. Varargs functions use the same calling
 * convention as other functions, which entails passing the first few arguments in registers and the
 * remaining arguments (if any) on the stack. Therefore, a varargs function creating a
 * {@code va_list} must initially save the contents of the argument registers so that they can be
 * read from the {@code va_list} in another function. The {@code va_list} structure looks like this:
 *
 * <pre>
 *   typedef struct {
 *     unsigned int gp_offset;  // offset of the next general-purpose argument in reg_save_area
 *     unsigned int fp_offset;  // offset of the next floating-point argument in reg_save_area
 *     void *overflow_arg_area; // address of the next overflow argument (can be gp or fp)
 *     void *reg_save_area;     // start address of the register save area
 *   } va_list[1];
 * </pre>
 *
 * Reading a {@code va_list} requires knowing the types of the arguments. General-purpose values
 * (integers and pointers) are passed in the six 64-bit registers {@code rdi, rsi, rdx, rcx, r8} and
 * {@code r9}, which are saved to the start of {@code reg_save_area}. Floating-point values are
 * passed in the eight 128-bit registers {@code xmm0} through {@code xmm7}, which are saved to
 * {@code reg_save_area} following the general-purpose registers. (Some sources specify that the
 * sixteen registers {@code xmm0} through {@code xmm15} are used, but this appears to be wrong.)
 * <p>
 * In the case of more than six general-purpose values or eight floating-point values, further
 * arguments are passed on the stack and can be read from {@code overflow_arg_area} (which is
 * typically a pointer to the arguments on the stack). For passing on the stack, 32-bit
 * {@code float} values are promoted to 64-bit {@code double}, and integer values with less than 32
 * bits are promoted to 32-bit {@code int}. However, each value in {@code overflow_arg_area} is
 * eight-byte aligned.
 * <p>
 * Reading an argument from a {@code va_list} requires checking whether all of the
 * {@code reg_save_area} for the type of argument has been consumed, that is, if <i>gp_offset == 6 *
 * 8</i>, or in case of a floating-point argument, if <i>fp_offset == 6 * 8 + 8 * 16</i>. If not,
 * the argument is read from {@code reg_save_area+offset}, and then either {@code gp_offset} is
 * increased by 8 or {@code fp_offset} is increased by 16. If the {@code reg_save_area} has already
 * been consumed, the argument is read from {@code overflow_arg_area}, and {@code overflow_arg_area}
 * is increased to point to the next eight-byte-aligned value.
 *
 * <p>
 * References:<br>
 * <cite>Hubicka, Jaeger, Mitchell: System V Application Binary Interface, AMD64 Architecture
 * Processor Supplement (Draft, 0.99.7, 2014-11-17): 3.5.7 Variable Argument Lists.</cite><br>
 * <cite>Agner Fog: Calling conventions for different C++ compilers and operating systems (updated
 * 2017-05-01): 7. Function calling conventions.</cite>
 */
final class PosixAMD64VaListSnippets extends SubstrateTemplates implements Snippets {

    // (read above)
    private static final int GP_OFFSET_LOCATION = 0;
    private static final int NUM_GP_ARG_REGISTERS = 6;
    private static final int MAX_GP_OFFSET = NUM_GP_ARG_REGISTERS * 8;
    private static final int FP_OFFSET_LOCATION = 4;
    private static final int NUM_FP_ARG_REGISTERS = 8;
    private static final int MAX_FP_OFFSET = MAX_GP_OFFSET + NUM_FP_ARG_REGISTERS * 16;
    private static final int OVERFLOW_ARG_AREA_LOCATION = 8;
    private static final int OVERFLOW_ARG_AREA_ALIGNMENT = 8;
    private static final int REG_SAVE_AREA_LOCATION = 16;

    private PosixAMD64VaListSnippets(OptionValues options, Iterable<DebugHandlersFactory> factories, Providers providers, SnippetReflectionProvider snippetReflection) {
        super(options, factories, providers, snippetReflection);
    }

    @Snippet
    protected static double vaArgDoubleSnippet(Pointer vaList) {
        int fpOffset = vaList.readInt(FP_OFFSET_LOCATION);
        if (fpOffset < MAX_FP_OFFSET) {
            Pointer regSaveArea = vaList.readWord(REG_SAVE_AREA_LOCATION);
            double v = regSaveArea.readDouble(fpOffset);
            vaList.writeInt(FP_OFFSET_LOCATION, fpOffset + 16); // 16-byte XMM register
            return v;
        } else {
            Pointer overflowArgArea = vaList.readWord(OVERFLOW_ARG_AREA_LOCATION);
            double v = overflowArgArea.readDouble(0);
            vaList.writeWord(OVERFLOW_ARG_AREA_LOCATION, overflowArgArea.add(OVERFLOW_ARG_AREA_ALIGNMENT));
            return v;
        }
    }

    @Snippet
    protected static float vaArgFloatSnippet(Pointer vaList) {
        // float is always promoted to double when passed in varargs
        return (float) vaArgDoubleSnippet(vaList);
    }

    @Snippet
    protected static long vaArgLongSnippet(Pointer vaList) {
        int gpOffset = vaList.readInt(GP_OFFSET_LOCATION);
        if (gpOffset < MAX_GP_OFFSET) {
            Pointer regSaveArea = vaList.readWord(REG_SAVE_AREA_LOCATION);
            long v = regSaveArea.readLong(gpOffset);
            vaList.writeInt(GP_OFFSET_LOCATION, gpOffset + 8);
            return v;
        } else {
            Pointer overflowArgArea = vaList.readWord(OVERFLOW_ARG_AREA_LOCATION);
            long v = overflowArgArea.readLong(0);
            vaList.writeWord(OVERFLOW_ARG_AREA_LOCATION, overflowArgArea.add(OVERFLOW_ARG_AREA_ALIGNMENT));
            return v;
        }
    }

    @Snippet
    protected static int vaArgIntSnippet(Pointer vaList) {
        return (int) vaArgLongSnippet(vaList);
    }

    @SuppressWarnings("unused")
    public static void registerLowerings(OptionValues options, Iterable<DebugHandlersFactory> factories, Providers providers,
                    SnippetReflectionProvider snippetReflection, Map<Class<? extends Node>, NodeLoweringProvider<?>> lowerings) {

        new PosixAMD64VaListSnippets(options, factories, providers, snippetReflection, lowerings);
    }

    private PosixAMD64VaListSnippets(OptionValues options, Iterable<DebugHandlersFactory> factories, Providers providers,
                    SnippetReflectionProvider snippetReflection, Map<Class<? extends Node>, NodeLoweringProvider<?>> lowerings) {

        super(options, factories, providers, snippetReflection);
        lowerings.put(VaListNextArgNode.class, new VaListSnippetsLowering());
    }

    protected class VaListSnippetsLowering implements NodeLoweringProvider<VaListNextArgNode> {

        private final SnippetInfo vaArgDouble = snippet(PosixAMD64VaListSnippets.class, "vaArgDoubleSnippet");
        private final SnippetInfo vaArgFloat = snippet(PosixAMD64VaListSnippets.class, "vaArgFloatSnippet");
        private final SnippetInfo vaArgLong = snippet(PosixAMD64VaListSnippets.class, "vaArgLongSnippet");
        private final SnippetInfo vaArgInt = snippet(PosixAMD64VaListSnippets.class, "vaArgIntSnippet");

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
                    throw VMError.shouldNotReachHere();
            }
            Arguments args = new Arguments(snippet, node.graph().getGuardsStage(), tool.getLoweringStage());
            args.add("vaList", node.getVaList());
            template(node, args).instantiate(providers.getMetaAccess(), node, SnippetTemplate.DEFAULT_REPLACER, args);
        }
    }
}
