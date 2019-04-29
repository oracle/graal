/*
 * Copyright (c) 2016, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.tregex.util;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.regex.tregex.dfa.DFAGenerator;
import com.oracle.truffle.regex.tregex.dfa.DFAStateNodeBuilder;
import com.oracle.truffle.regex.tregex.dfa.DFAStateTransitionBuilder;
import com.oracle.truffle.regex.tregex.matchers.AnyMatcher;
import com.oracle.truffle.regex.tregex.matchers.BitSetMatcher;
import com.oracle.truffle.regex.tregex.matchers.CharMatcher;
import com.oracle.truffle.regex.tregex.matchers.EmptyMatcher;
import com.oracle.truffle.regex.tregex.matchers.SingleCharMatcher;
import com.oracle.truffle.regex.tregex.matchers.SingleRangeMatcher;
import com.oracle.truffle.regex.tregex.nodes.DFAStateNode;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Collectors;

public class DFAExport {

    @CompilerDirectives.TruffleBoundary
    public static void exportDot(DFAGenerator dfaGenerator, TruffleFile path, boolean shortLabels) {
        DFAStateNodeBuilder[] entryStates = dfaGenerator.getEntryStates();
        Map<DFAStateNodeBuilder, DFAStateNodeBuilder> stateMap = dfaGenerator.getStateMap();
        TreeSet<Short> entryIDs = new TreeSet<>();
        for (DFAStateNodeBuilder s : entryStates) {
            if (s != null) {
                entryIDs.add(s.getId());
            }
        }
        try (BufferedWriter writer = path.newBufferedWriter(StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            writer.write("digraph finite_state_machine {");
            writer.newLine();
            String finalStates = stateMap.values().stream().filter(DFAStateNodeBuilder::isFinalState).map(
                            s -> DotExport.escape(dotState(s, shortLabels))).collect(Collectors.joining("\" \""));
            if (!finalStates.isEmpty()) {
                writer.write(String.format("    node [shape = doublecircle]; \"%s\";", finalStates));
                writer.newLine();
            }
            String anchoredFinalStates = stateMap.values().stream().filter(DFAStateNodeBuilder::isAnchoredFinalState).map(
                            s -> DotExport.escape(dotState(s, shortLabels))).collect(Collectors.joining("\" \""));
            if (!anchoredFinalStates.isEmpty()) {
                writer.write(String.format("    node [shape = Mcircle]; \"%s\";", anchoredFinalStates));
                writer.newLine();
            }
            writer.write("    node [shape = circle];");
            writer.newLine();
            for (DFAStateNodeBuilder state : stateMap.values()) {
                if (entryIDs.contains(state.getId())) {
                    for (int i = 0; i < entryStates.length; i++) {
                        if (entryStates[i] == state) {
                            String initStateLabel;
                            if (i < entryStates.length / 2) {
                                initStateLabel = "I^" + i;
                            } else {
                                initStateLabel = "I" + (i - entryStates.length / 2);
                            }
                            DotExport.printConnection(writer, initStateLabel, dotState(state, shortLabels), "");
                            break;
                        }
                    }
                }
                for (DFAStateTransitionBuilder t : state.getTransitions()) {
                    DotExport.printConnection(writer, dotState(state, shortLabels), dotState(t.getTarget(), shortLabels), t.getMatcherBuilder().toString());
                }
            }
            writer.write("}");
            writer.newLine();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static String dotState(DFAStateNodeBuilder state, boolean shortLabels) {
        return "S" + (shortLabels ? state.getId() : state.stateSetToString());
    }

    @CompilerDirectives.TruffleBoundary
    public static void exportUnitTest(DFAStateNode entry, DFAStateNode[] states) {
        System.out.printf("int initialState = %d;\n", entry.getId());
        System.out.printf("DFAStateNode[] states = createStates(%d);\n", states.length);
        for (DFAStateNode state : states) {
            System.out.printf("states[%d].setSuccessors(new int[] { %d", state.getId(), state.getSuccessors()[0]);
            for (int i = 1; i < state.getSuccessors().length; i++) {
                System.out.printf(", %d", state.getSuccessors()[i]);
            }
            System.out.println(" });");
            System.out.printf("states[%d].setMatchers(new ByteMatcher[] {\n    ", state.getId());
            printMatcher(state.getMatchers()[0]);
            for (int i = 1; i < state.getMatchers().length; i++) {
                System.out.print(",\n    ");
                printMatcher(state.getMatchers()[i]);
            }
            System.out.println("\n});");
            if (state.isFinalState()) {
                System.out.printf("states[%d].setFinalState();\n", state.getId());
            }
        }
    }

    private static void printMatcher(CharMatcher matcher) {
        if (matcher instanceof EmptyMatcher) {
            System.out.print("EmptyByteMatcher.create()");
        }
        if (matcher instanceof SingleCharMatcher) {
            System.out.printf("SingleByteMatcher.create(0x%02x)", (int) ((SingleCharMatcher) matcher).getChar());
        }
        if (matcher instanceof SingleRangeMatcher) {
            System.out.printf("RangeByteMatcher.create(0x%02x, 0x%02x)", (int) ((SingleRangeMatcher) matcher).getLo(), (int) ((SingleRangeMatcher) matcher).getHi());
        }
        if (matcher instanceof BitSetMatcher) {
            long[] words = ((BitSetMatcher) matcher).getBitSet().toLongArray();
            System.out.printf("MultiByteMatcher.create(new CompilationFinalBitSet(new long[] {\n        0x%016xL", words[0]);
            for (int i = 1; i < words.length; i++) {
                System.out.printf(", 0x%016xL", words[i]);
            }
            System.out.print("}))");
        }
        if (matcher instanceof AnyMatcher) {
            System.out.print("AnyByteMatcher.create()");
        }
    }
}
