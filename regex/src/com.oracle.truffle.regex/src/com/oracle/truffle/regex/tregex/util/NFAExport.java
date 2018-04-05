/*
 * Copyright (c) 2016, 2016, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.truffle.regex.tregex.dfa.NFAStateSet;
import com.oracle.truffle.regex.tregex.nfa.NFA;
import com.oracle.truffle.regex.tregex.nfa.NFAState;
import com.oracle.truffle.regex.tregex.nfa.NFAStateTransition;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.Collectors;

public final class NFAExport {

    private final NFA nfa;
    private final BufferedWriter writer;
    private final boolean forward;
    private final boolean fullLabels;

    private NFAExport(NFA nfa, BufferedWriter writer, boolean forward, boolean fullLabels) {
        this.nfa = nfa;
        this.writer = writer;
        this.forward = !forward;
        this.fullLabels = fullLabels;
    }

    @CompilerDirectives.TruffleBoundary
    public static void exportDot(NFA nfa, String path, boolean fullLabels) {
        try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(path))) {
            new NFAExport(nfa, writer, false, fullLabels).exportDot();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @CompilerDirectives.TruffleBoundary
    public static void exportDotReverse(NFA nfa, String path, boolean fullLabels) {
        try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(path))) {
            new NFAExport(nfa, writer, true, fullLabels).exportDotReverse();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @CompilerDirectives.TruffleBoundary
    public static void exportLaTex(NFA nfa, String path, boolean fullLabels) {
        try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(path))) {
            new NFAExport(nfa, writer, false, fullLabels).exportLaTex();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void exportDot() throws IOException {
        writer.write("digraph finite_state_machine {");
        writer.newLine();
        writer.newLine();
        if (!nfa.getReverseUnAnchoredEntry().getSource().getPrev().isEmpty()) {
            setDotNodeStyle(nfa.getReverseUnAnchoredEntry().getSource(), "doublecircle");
        }
        if (!nfa.getReverseAnchoredEntry().getSource().getPrev().isEmpty()) {
            setDotNodeStyle(nfa.getReverseAnchoredEntry().getSource(), "Mcircle");
        }
        writer.write("    node [shape = circle];");
        writer.newLine();
        for (NFAState state : nfa.getStates()) {
            if (state == null) {
                continue;
            }
            for (int i = 0; i < state.getNext().size(); i++) {
                NFAStateTransition transition = state.getNext().get(i);
                DotExport.printConnection(writer, labelState(state), labelState(transition.getTarget()), labelTransition(transition, i));
            }
        }
        writer.write("}");
        writer.newLine();
    }

    private void exportDotReverse() throws IOException {
        writer.write("digraph finite_state_machine {");
        writer.newLine();
        writer.newLine();
        if (nfa.getUnAnchoredEntry() == null) { // traceFinder NFA
            for (NFAState state : nfa.getStates()) {
                if (state == null) {
                    continue;
                }
                if (state.isReverseUnAnchoredFinalState()) {
                    setDotNodeStyle(state, "doublecircle");
                } else if (state.isReverseAnchoredFinalState()) {
                    setDotNodeStyle(state, "Mcircle");
                }
            }
        } else {
            for (int i = 0; i < nfa.getAnchoredEntry().length; i++) {
                if (!nfa.getUnAnchoredEntry()[i].getTarget().getNext().isEmpty()) {
                    setDotNodeStyle(nfa.getUnAnchoredEntry()[i].getTarget(), "doublecircle");
                }
                if (!nfa.getAnchoredEntry()[i].getTarget().getNext().isEmpty()) {
                    setDotNodeStyle(nfa.getAnchoredEntry()[i].getTarget(), "Mcircle");
                }
            }
        }
        writer.write("    node [shape = circle];");
        writer.newLine();
        for (NFAState state : nfa.getStates()) {
            if (state == null) {
                continue;
            }
            for (int i = 0; i < state.getPrev().size(); i++) {
                NFAStateTransition transition = state.getPrev().get(i);
                DotExport.printConnection(writer, labelState(state), labelState(transition.getSource()), labelTransition(transition, i));
            }
        }
        writer.write("}");
        writer.newLine();
    }

    private void exportLaTex() throws IOException {
        NFAStateSet visited = new NFAStateSet(nfa);
        writer.write("\\documentclass{standalone}\n" +
                        "\\usepackage[utf8]{inputenc}\n" +
                        "\\usepackage[T1]{fontenc}\n" +
                        "\\usepackage{tikz}\n" +
                        "\n" +
                        "\\usetikzlibrary{automata}\n" +
                        "\\usetikzlibrary{arrows}\n" +
                        "\n" +
                        "\\begin{document}\n" +
                        "\\begin{tikzpicture}[>=stealth',auto,node distance=2.5cm]\n");
        writer.newLine();
        ArrayList<NFAState> curStates = new ArrayList<>();
        ArrayList<NFAState> nextStates = new ArrayList<>();
        curStates.add(nfa.getAnchoredEntry()[nfa.getAnchoredEntry().length - 1].getTarget());
        curStates.add(nfa.getUnAnchoredEntry()[nfa.getUnAnchoredEntry().length - 1].getTarget());
        printLaTexState(curStates.get(0), null, null);
        printLaTexState(curStates.get(1), curStates.get(0), "below");
        while (!curStates.isEmpty()) {
            for (NFAState s : curStates) {
                for (NFAStateTransition t : s.getNext()) {
                    if (visited.add(t.getTarget())) {
                        nextStates.add(t.getTarget());
                    }
                }
            }
            for (int i = 0; i < nextStates.size(); i++) {
                if (i == 0) {
                    printLaTexState(nextStates.get(i), curStates.get(0), "right");
                } else {
                    printLaTexState(nextStates.get(i), nextStates.get(i - 1), "below");
                }
            }
            ArrayList<NFAState> tmp = curStates;
            curStates = nextStates;
            nextStates = tmp;
            nextStates.clear();
        }
        writer.newLine();
        writer.write("\\path[->]");
        writer.newLine();
        for (NFAState s : nfa.getStates()) {
            if (s == null) {
                continue;
            }
            for (int i = 0; i < s.getNext().size(); i++) {
                printLaTexTransition(s.getNext().get(i), i);
            }
        }
        writer.write(";");
        writer.newLine();
        writer.write("\\end{tikzpicture}");
        writer.newLine();
        writer.write("\\end{document}");
        writer.newLine();
    }

    private void printLaTexState(NFAState state, NFAState relativeTo, String direction) throws IOException {
        String offset = "";
        if (relativeTo != null) {
            offset = String.format("%s of=s%s", direction, relativeTo.getId());
        }
        writer.write(String.format("\\node[%s] (s%d) [%s] {$%s$};", getLaTexStateStyle(state), state.getId(), offset, LaTexExport.escape(labelState(state))));
        writer.newLine();
    }

    private void printLaTexTransition(NFAStateTransition t, int priority) throws IOException {
        ArrayList<String> options = new ArrayList<>();
        if (t.getSource() == t.getTarget()) {
            options.add("loop above");
        }
        writer.write(String.format("(s%d) edge [%s] node {%s} (s%d)", t.getSource().getId(),
                        options.stream().collect(Collectors.joining(", ")),
                        LaTexExport.escape(labelTransition(t, priority)),
                        t.getTarget().getId()));
        writer.newLine();
    }

    private String getLaTexStateStyle(NFAState state) {
        if (nfa.isEntry(state, forward)) {
            return "initial,state";
        }
        if (state.isAnchoredFinalState(forward) || state.isUnAnchoredFinalState(forward)) {
            return "accepting,state";
        }
        return "state";
    }

    private String labelTransition(NFAStateTransition transition, int priority) {
        StringBuilder sb = new StringBuilder();
        if (!(transition.getTarget(forward).isAnchoredFinalState(forward) || transition.getTarget(forward).isUnAnchoredFinalState(forward))) {
            sb.append(transition.getTarget(forward));
        }
        if (fullLabels) {
            sb.append(", p").append(priority).append(", ").append(transition.getGroupBoundaries());
        }
        return sb.toString();
    }

    private void setDotNodeStyle(NFAState state, String style) throws IOException {
        writer.write(String.format("    node [shape = %s]; \"%s\";", style, DotExport.escape(labelState(state))));
        writer.newLine();
    }

    private String labelState(NFAState state) {
        StringBuilder sb = new StringBuilder();
        if (nfa.isAnchoredEntry(state, forward)) {
            sb.append("I^");
            if (forward) {
                sb.append(Arrays.asList(nfa.getAnchoredEntry()).indexOf(state));
            }
        } else if (nfa.isUnAnchoredEntry(state, forward)) {
            sb.append("I");
            if (forward) {
                sb.append(Arrays.asList(nfa.getUnAnchoredEntry()).indexOf(state));
            }
        } else if (state.isAnchoredFinalState(forward)) {
            sb.append("F$");
        } else if (state.isUnAnchoredFinalState(forward)) {
            sb.append("F");
        } else {
            if (fullLabels) {
                sb.append("S").append(state.idToString());
            } else {
                sb.append(state.getId());
            }
        }
        if (fullLabels && state.hasPossibleResults()) {
            sb.append("_r").append(state.getPossibleResults());
        }
        return sb.toString();
    }
}
