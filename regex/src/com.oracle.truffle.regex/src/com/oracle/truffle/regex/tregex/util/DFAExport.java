/*
 * Copyright (c) 2018, 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.regex.tregex.util;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Collectors;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.regex.tregex.dfa.DFAGenerator;
import com.oracle.truffle.regex.tregex.dfa.DFAStateNodeBuilder;
import com.oracle.truffle.regex.tregex.dfa.DFAStateTransitionBuilder;

public class DFAExport {

    @TruffleBoundary
    public static void exportDot(DFAGenerator dfaGenerator, TruffleFile path, boolean shortLabels) {
        DFAStateNodeBuilder[] entryStates = dfaGenerator.getEntryStates();
        Map<DFAStateNodeBuilder, DFAStateNodeBuilder> stateMap = dfaGenerator.getStateMap();
        TreeSet<Integer> entryIDs = new TreeSet<>();
        for (DFAStateNodeBuilder s : entryStates) {
            if (s != null) {
                entryIDs.add(s.getId());
            }
        }
        try (BufferedWriter writer = path.newBufferedWriter(StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            writer.write("digraph finite_state_machine {");
            writer.newLine();
            String finalStates = stateMap.values().stream().filter(DFAStateNodeBuilder::isUnAnchoredFinalState).map(
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
                for (DFAStateTransitionBuilder t : state.getSuccessors()) {
                    DotExport.printConnection(writer, dotState(state, shortLabels), dotState(t.getTarget(), shortLabels), t.getCodePointSet().toString());
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
}
