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

import com.oracle.truffle.regex.tregex.dfa.NFAStateSet;
import com.oracle.truffle.regex.tregex.nfa.NFA;
import com.oracle.truffle.regex.tregex.nfa.NFAState;
import com.oracle.truffle.regex.tregex.nfa.NFAStateTransition;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.stream.Collectors;

import static com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

public final class NFAExport {

    private enum StateStyle {
        ANCHORED_INITIAL,
        UN_ANCHORED_INITIAL,
        ANCHORED_FINAL,
        UN_ANCHORED_FINAL,
        REGULAR
    }

    private final NFA nfa;
    private final BufferedWriter writer;
    private final boolean forward;
    private final boolean fullLabels;
    private final boolean mergeFinalStates;

    private int nextStateNumber = 1;
    private final HashMap<NFAState, Integer> stateNumberMap = new HashMap<>();

    private NFAExport(NFA nfa, BufferedWriter writer, boolean forward, boolean fullLabels, boolean mergeFinalStates) {
        this.nfa = nfa;
        this.writer = writer;
        this.forward = forward;
        this.fullLabels = fullLabels;
        this.mergeFinalStates = mergeFinalStates;
    }

    @TruffleBoundary
    public static void exportDot(NFA nfa, String path, boolean fullLabels, boolean mergeFinalStates) {
        try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(path))) {
            new NFAExport(nfa, writer, true, fullLabels, mergeFinalStates).exportDot();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @TruffleBoundary
    public static void exportDotReverse(NFA nfa, String path, boolean fullLabels, boolean mergeFinalStates) {
        try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(path))) {
            new NFAExport(nfa, writer, false, fullLabels, mergeFinalStates).exportDot();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @TruffleBoundary
    public static void exportLaTex(NFA nfa, String path, boolean fullLabels, boolean mergeFinalStates) {
        try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(path))) {
            new NFAExport(nfa, writer, true, fullLabels, mergeFinalStates).exportLaTex();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void exportDot() throws IOException {
        writer.write("digraph finite_state_machine {");
        writer.newLine();
        writer.newLine();
        for (NFAState state : nfa.getStates()) {
            if (showState(state)) {
                setDotNodeStyle(state, getDotStateStyle(state));
            }
        }
        writer.newLine();
        for (NFAState state : nfa.getStates()) {
            if (showState(state)) {
                for (int i = 0; i < state.getNext(forward).size(); i++) {
                    NFAStateTransition transition = state.getNext(forward).get(i);
                    DotExport.printConnection(writer,
                                    labelState(transition.getSource(forward), true),
                                    labelState(transition.getTarget(forward), true),
                                    labelTransition(transition, i));
                }
            }
        }
        writer.write("}");
        writer.newLine();
    }

    private String getDotStateStyle(NFAState state) {
        switch (getStateStyle(state)) {
            case ANCHORED_FINAL:
                return "Mcircle";
            case UN_ANCHORED_FINAL:
                return "doublecircle";
            case ANCHORED_INITIAL:
            case UN_ANCHORED_INITIAL:
            case REGULAR:
                return "circle";
            default:
                throw new IllegalStateException();
        }
    }

    private void setDotNodeStyle(NFAState state, String style) throws IOException {
        writer.write(String.format("    node [shape = %s]; \"%s\";", style, DotExport.escape(labelState(state, true))));
        writer.newLine();
    }

    private void exportLaTex() throws IOException {
        NFAStateSet visited = new NFAStateSet(nfa);
        writer.write("\\documentclass{standalone}\n" +
                        "\\usepackage[utf8]{inputenc}\n" +
                        "\\usepackage[T1]{fontenc}\n" +
                        "\\usepackage{tikz}\n" +
                        "\n" +
                        "\\usetikzlibrary{calc}\n" +
                        "\\usetikzlibrary{automata}\n" +
                        "\\usetikzlibrary{arrows.meta}\n" +
                        "\n" +
                        "\\tikzset{\n" +
                        "\tregex automaton/.style={\n" +
                        "\t\tauto, \n" +
                        "\t\tnode distance=2cm,\n" +
                        "\t\tevery state/.style={\n" +
                        "\t\t\tsemithick,\n" +
                        "\t\t\tfill=gray!5,\n" +
                        "\t\t\tfont=\\footnotesize\\ttfamily,\n" +
                        "\t\t},\n" +
                        "\t\tdouble distance=1.5pt,  % Adjust appearance of accept states\n" +
                        "\t\tinitial text={start},   % label on inital state arrow\n" +
                        "\t\tevery edge/.style={\n" +
                        "\t\t\tdraw,\n" +
                        "\t\t\tfont=\\footnotesize\\ttfamily,\n" +
                        "\t\t\t-Stealth,\n" +
                        "\t\t\tshorten >=1pt,\n" +
                        "\t\t\tauto,\n" +
                        "\t\t\tsemithick\n" +
                        "\t\t},\n" +
                        "\t\tevery loop/.style={\n" +
                        "\t\t\tdraw,\n" +
                        "\t\t\tfont=\\footnotesize\\ttfamily,\n" +
                        "\t\t\t-Stealth,\n" +
                        "\t\t\tshorten >=1pt,\n" +
                        "\t\t\tauto,\n" +
                        "\t\t\tsemithick\n" +
                        "\t\t}\n" +
                        "\t},\n" +
                        "\tanchored/.style={\n" +
                        "\t\tpath picture={\n" +
                        "\t\t\t\\draw[semithick] ($(path picture bounding box.north west)-(0,0.2)$) -- ($(path picture bounding box.north east)-(0,0.2)$);\n" +
                        "\t\t\t\\draw[semithick] ($(path picture bounding box.south west)+(0,0.2)$) -- ($(path picture bounding box.south east)+(0,0.2)$);\n" +
                        "\t\t}\n" +
                        "\t}\n" +
                        "}\n" +
                        "\n" +
                        "\\begin{document}\n" +
                        "\\begin{tikzpicture}[regex automaton]\n" +
                        "\n");
        ArrayList<NFAState> curStates = new ArrayList<>();
        ArrayList<NFAState> nextStates = new ArrayList<>();
        int entryOffset = nfa.getAnchoredEntry().length - 1;
        NFAState lastAnchoredEntry = nfa.getAnchoredEntry()[entryOffset].getTarget();
        NFAState lastUnAnchoredEntry = nfa.getUnAnchoredEntry()[entryOffset].getTarget();
        visited.add(lastAnchoredEntry);
        visited.add(lastUnAnchoredEntry);
        curStates.add(lastAnchoredEntry);
        printLaTexState(lastAnchoredEntry, null, null);
        if (lastAnchoredEntry != lastUnAnchoredEntry) {
            curStates.add(lastUnAnchoredEntry);
            printLaTexState(lastUnAnchoredEntry, lastAnchoredEntry, "below");
        }
        entryOffset--;
        while (!curStates.isEmpty()) {
            for (NFAState s : curStates) {
                for (NFAStateTransition t : s.getNext()) {
                    if (!(mergeFinalStates && t.getTarget().isFinalState(forward)) && visited.add(t.getTarget())) {
                        nextStates.add(t.getTarget());
                    }
                }
            }
            if (entryOffset >= 0) {
                NFAState anchoredEntry = nfa.getAnchoredEntry()[entryOffset].getTarget();
                if (visited.add(anchoredEntry)) {
                    nextStates.add(anchoredEntry);
                }
                NFAState unAnchoredEntry = nfa.getUnAnchoredEntry()[entryOffset].getTarget();
                if (visited.add(unAnchoredEntry)) {
                    nextStates.add(unAnchoredEntry);
                }
                entryOffset--;
            }
            NFAState relativeTo = null;
            for (NFAState nextState : nextStates) {
                printLaTexState(nextState, relativeTo == null ? curStates.get(0) : relativeTo, relativeTo == null ? "right" : "below");
                relativeTo = nextState;
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
                NFAStateTransition t = s.getNext().get(i);
                if (visited.contains(s) && visited.contains(t.getTarget())) {
                    printLaTexTransition(t, i);
                }
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
            offset = String.format("%s of=%s", direction, getLaTexStateID(relativeTo));
        }
        writer.write(String.format("\\node[%s] (%s) [%s] {%s};", getLaTexStateStyle(state),
                        getLaTexStateID(state), offset, LaTexExport.escape(labelState(state, false))));
        writer.newLine();
    }

    private void printLaTexTransition(NFAStateTransition t, int priority) throws IOException {
        ArrayList<String> options = new ArrayList<>();
        if (t.getSource() == t.getTarget()) {
            options.add("loop above");
        }
        writer.write(String.format("(%s) edge [%s] node {%s} (%s)", getLaTexStateID(t.getSource()),
                        options.stream().collect(Collectors.joining(", ")),
                        LaTexExport.escape(labelTransition(t, priority)),
                        getLaTexStateID(t.getTarget())));
        writer.newLine();
    }

    private String getLaTexStateID(NFAState state) {
        if (state.isAnchoredFinalState(forward)) {
            return "af";
        }
        if (state.isUnAnchoredFinalState(forward)) {
            return "f";
        }
        if (nfa.isEntry(state, forward)) {
            String lbl = nfa.isUnAnchoredEntry(state, forward) ? "i" : "ai";
            return lbl + (nfa.isUnAnchoredEntry(state, forward) ? nfa.getUnAnchoredEntryOffset(state, forward) : nfa.getAnchoredEntryOffset(state, forward));
        }
        return "s" + stateNumberMap.computeIfAbsent(state, x -> nextStateNumber++);
    }

    private String getLaTexStateStyle(NFAState state) {
        switch (getStateStyle(state)) {
            case ANCHORED_INITIAL:
                return "anchored,initial,state";
            case UN_ANCHORED_INITIAL:
                return "initial,state";
            case ANCHORED_FINAL:
                return "anchored,accepting,state";
            case UN_ANCHORED_FINAL:
                return "accepting,state";
            case REGULAR:
                return "state";
            default:
                throw new IllegalStateException();
        }
    }

    private boolean showState(NFAState state) {
        if (state == null || state == nfa.getDummyInitialState()) {
            return false;
        }
        if (nfa.isEntry(state, forward)) {
            return !state.getNext(forward).isEmpty();
        }
        if (state.isFinalState(forward)) {
            return !state.getPrev(forward).isEmpty();
        }
        return true;
    }

    private StateStyle getStateStyle(NFAState state) {
        if (nfa.isEntry(state, forward)) {
            if (nfa.isAnchoredEntry(state, forward) && !nfa.isUnAnchoredEntry(state, forward)) {
                return StateStyle.ANCHORED_INITIAL;
            }
            return StateStyle.UN_ANCHORED_INITIAL;
        }
        if (mergeFinalStates && state.hasTransitionToAnchoredFinalState(forward) && !state.hasTransitionToUnAnchoredFinalState(forward) ||
                        state.isAnchoredFinalState(forward)) {
            return StateStyle.ANCHORED_FINAL;
        }
        if (state.isFinalState(forward) || mergeFinalStates && state.hasTransitionToUnAnchoredFinalState(forward)) {
            return StateStyle.UN_ANCHORED_FINAL;
        }
        return StateStyle.REGULAR;
    }

    private String labelState(NFAState state, boolean markAnchored) {
        StringBuilder sb = new StringBuilder();
        if (nfa.isAnchoredEntry(state, forward) && !nfa.isUnAnchoredEntry(state, forward)) {
            sb.append("I");
            if (markAnchored) {
                sb.append("^");
            }
            if (forward) {
                sb.append(nfa.getAnchoredEntryOffset(state, true));
            }
        } else if (nfa.isUnAnchoredEntry(state, forward)) {
            sb.append("I");
            if (forward) {
                sb.append(nfa.getUnAnchoredEntryOffset(state, true));
            }
        } else if (state.isAnchoredFinalState(forward)) {
            sb.append("F");
            if (markAnchored) {
                sb.append("$");
            }
        } else if (state.isUnAnchoredFinalState(forward)) {
            sb.append("F");
        } else {
            if (fullLabels) {
                sb.append("S").append(state.idToString());
            } else {
                sb.append(stateNumberMap.computeIfAbsent(state, x -> nextStateNumber++));
            }
        }
        if (fullLabels && state.hasPossibleResults()) {
            sb.append("_r").append(state.getPossibleResults());
        }
        return sb.toString();
    }

    private String labelTransition(NFAStateTransition transition, int priority) {
        StringBuilder sb = new StringBuilder();
        if (!(transition.getTarget(forward).isFinalState(forward))) {
            sb.append(transition.getTarget(forward).getMatcherBuilder());
        }
        if (fullLabels) {
            sb.append(", p").append(priority).append(", ").append(transition.getGroupBoundaries());
        }
        return sb.toString();
    }
}
