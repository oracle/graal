/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.tregex.nodes;

import java.util.ArrayList;
import java.util.List;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.regex.RegexLanguage;
import com.oracle.truffle.regex.tregex.dfa.DFAGenerator;
import com.oracle.truffle.regex.tregex.util.json.Json;
import com.oracle.truffle.regex.tregex.util.json.JsonConvertible;
import com.oracle.truffle.regex.tregex.util.json.JsonValue;

/**
 * This class is used to store a trace of the execution of a
 * {@link TRegexDFAExecutorNode#execute(TRegexDFAExecutorLocals, boolean)}. A trace contains the
 * arguments received by {@link TRegexDFAExecutorNode#execute(TRegexDFAExecutorLocals, boolean)},
 * and the ID of the DFA transition taken for all characters of the input string that have been
 * traversed. After execution, the recorded trace can be dumped to disk as JSON with
 * {@link #finishRecording()}.
 */
public class TRegexDFAExecutorDebugRecorder implements JsonConvertible {

    private static final class Recording implements JsonConvertible {

        private final String input;
        private final int fromIndex;
        private final int initialIndex;
        private final int maxIndex;
        private final List<RecordedTransition> transitions;

        private Recording(String input, int fromIndex, int initialIndex, int maxIndex) {
            this.input = input;
            this.fromIndex = fromIndex;
            this.initialIndex = initialIndex;
            this.maxIndex = maxIndex;
            transitions = new ArrayList<>();
        }

        private int getLowestIndex() {
            return initialIndex < maxIndex ? initialIndex : maxIndex;
        }

        private void initUpToIndex(int currentIndex) {
            for (int i = transitions.size(); i <= currentIndex - getLowestIndex(); i++) {
                transitions.add(new RecordedTransition(getLowestIndex() + i));
            }
        }

        private RecordedTransition getTransition(int currentIndex) {
            RecordedTransition transition = transitions.get(currentIndex - getLowestIndex());
            assert transition.currentIndex == currentIndex;
            return transition;
        }

        @TruffleBoundary
        public void recordTransition(int currentIndex, int transitionID) {
            initUpToIndex(currentIndex);
            getTransition(currentIndex).setTransitionID(transitionID);
        }

        @TruffleBoundary
        public void recordCGPartialTransition(int currentIndex, int cgPartialTransitionIndex) {
            initUpToIndex(currentIndex);
            getTransition(currentIndex).addCgPartialTransitionIDs(cgPartialTransitionIndex);
        }

        @TruffleBoundary
        @Override
        public JsonValue toJson() {
            return Json.obj(Json.prop("input", input),
                            Json.prop("fromIndex", fromIndex),
                            Json.prop("initialIndex", initialIndex),
                            Json.prop("maxIndex", maxIndex),
                            Json.prop("transitions", transitions));
        }
    }

    private static final class RecordedTransition implements JsonConvertible {

        private final int currentIndex;
        private int transitionID = -1;
        private List<Integer> cgPartialTransitionIDs;

        private RecordedTransition(int currentIndex) {
            this.currentIndex = currentIndex;
        }

        public void setTransitionID(int transitionID) {
            this.transitionID = transitionID;
        }

        public void addCgPartialTransitionIDs(int partialTransitionID) {
            if (cgPartialTransitionIDs == null) {
                cgPartialTransitionIDs = new ArrayList<>();
            }
            cgPartialTransitionIDs.add(partialTransitionID);
        }

        @TruffleBoundary
        @Override
        public JsonValue toJson() {
            return Json.obj(Json.prop("currentIndex", currentIndex),
                            Json.prop("transitionID", transitionID),
                            Json.prop("cgPartialTransitionIDs", Json.array(
                                            cgPartialTransitionIDs == null ? new int[0] : cgPartialTransitionIDs.stream().mapToInt(x -> x).toArray())));
        }
    }

    public TRegexDFAExecutorDebugRecorder(DFAGenerator dfa) {
        this.dfa = dfa;
    }

    private final DFAGenerator dfa;
    private List<Recording> recordings = new ArrayList<>();

    public void startRecording(TRegexDFAExecutorLocals locals) {
        CompilerAsserts.neverPartOfCompilation();
        recordings.add(new Recording(locals.getInput().toString(), locals.getFromIndex(), locals.getIndex(), locals.getMaxIndex()));
    }

    private Recording curRecording() {
        return recordings.get(recordings.size() - 1);
    }

    @TruffleBoundary
    public void recordTransition(int currentIndex, short stateNodeID, int transitionIndex) {
        CompilerAsserts.neverPartOfCompilation();
        int transitionID = dfa.getState(stateNodeID).getTransitions()[transitionIndex].getId();
        curRecording().recordTransition(currentIndex, transitionID);
    }

    @TruffleBoundary
    public void recordCGPartialTransition(int currentIndex, int cgPartialTransitionIndex) {
        CompilerAsserts.neverPartOfCompilation();
        curRecording().recordCGPartialTransition(currentIndex, cgPartialTransitionIndex);
    }

    @TruffleBoundary
    public void finishRecording() {
        CompilerAsserts.neverPartOfCompilation();
        TruffleFile file = RegexLanguage.getCurrentContext().getEnv().getTruffleFile(
                        "tregex_" + dfa.getDebugDumpName() + "_" + dfa.getNfa().getAst().getSource().toFileName() + "_recording" + recordings.size() + ".json");
        Json.obj(Json.prop("dfa", dfa), Json.prop("recording", curRecording())).dump(file);
    }

    @TruffleBoundary
    @Override
    public JsonValue toJson() {
        return Json.obj(Json.prop("recordings", recordings));
    }
}
