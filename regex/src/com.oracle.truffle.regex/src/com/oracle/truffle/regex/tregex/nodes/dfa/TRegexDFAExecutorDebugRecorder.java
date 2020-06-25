/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.tregex.nodes.dfa;

import java.util.ArrayList;
import java.util.List;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.regex.RegexLanguage;
import com.oracle.truffle.regex.RegexOptions;
import com.oracle.truffle.regex.tregex.dfa.DFAGenerator;
import com.oracle.truffle.regex.tregex.nodes.TRegexExecutorLocals;
import com.oracle.truffle.regex.tregex.util.json.Json;
import com.oracle.truffle.regex.tregex.util.json.JsonConvertible;
import com.oracle.truffle.regex.tregex.util.json.JsonValue;

/**
 * This class is used to store a trace of the execution of a
 * {@link TRegexDFAExecutorNode#execute(TRegexExecutorLocals, boolean)}. A trace contains the
 * arguments received by {@link TRegexDFAExecutorNode#execute(TRegexExecutorLocals, boolean)}, and
 * the ID of the DFA transition taken for all characters of the input string that have been
 * traversed. After execution, the recorded trace can be dumped to disk as JSON with
 * {@link #finishRecording()}.
 */
public final class TRegexDFAExecutorDebugRecorder implements JsonConvertible {

    private static final class Recording implements JsonConvertible {

        private final String input;
        private final int fromIndex;
        private int initialIndex;
        private final int maxIndex;
        private final List<RecordedTransition> transitions;

        private Recording(String input, int fromIndex, int initialIndex, int maxIndex) {
            this.input = input;
            this.fromIndex = fromIndex;
            this.initialIndex = initialIndex;
            this.maxIndex = maxIndex;
            transitions = new ArrayList<>();
        }

        @TruffleBoundary
        public void setInitialIndex(int initialIndex) {
            this.initialIndex = initialIndex;
        }

        @TruffleBoundary
        private int getLowestIndex() {
            return initialIndex < maxIndex ? initialIndex : maxIndex;
        }

        @TruffleBoundary
        private void initUpToIndex(int currentIndex) {
            for (int i = transitions.size(); i <= currentIndex - getLowestIndex(); i++) {
                transitions.add(new RecordedTransition(getLowestIndex() + i));
            }
        }

        @TruffleBoundary
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
            getTransition(currentIndex).setCgPartialTransitionID(cgPartialTransitionIndex);
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
        private int cgPartialTransitionID = -1;

        @TruffleBoundary
        private RecordedTransition(int currentIndex) {
            this.currentIndex = currentIndex;
        }

        @TruffleBoundary
        public void setTransitionID(int transitionID) {
            this.transitionID = transitionID;
        }

        @TruffleBoundary
        public void setCgPartialTransitionID(int cgPartialTransitionID) {
            assert this.cgPartialTransitionID == -1 || this.cgPartialTransitionID == 0;
            this.cgPartialTransitionID = cgPartialTransitionID;
        }

        @TruffleBoundary
        @Override
        public JsonValue toJson() {
            return Json.obj(Json.prop("currentIndex", currentIndex),
                            Json.prop("transitionID", transitionID),
                            Json.prop("cgPartialTransitionID", cgPartialTransitionID));
        }
    }

    @TruffleBoundary
    public static TRegexDFAExecutorDebugRecorder create(RegexOptions options, DFAGenerator dfaGenerator) {
        return options.isStepExecution() ? new TRegexDFAExecutorDebugRecorder(dfaGenerator) : null;
    }

    private TRegexDFAExecutorDebugRecorder(DFAGenerator dfa) {
        this.dfa = dfa;
    }

    private final DFAGenerator dfa;
    private List<Recording> recordings = new ArrayList<>();

    @TruffleBoundary
    public void startRecording(TRegexDFAExecutorLocals locals) {
        recordings.add(new Recording(locals.getInput().toString(), locals.getFromIndex(), locals.getIndex(), locals.getMaxIndex()));
    }

    @TruffleBoundary
    private Recording curRecording() {
        return recordings.get(recordings.size() - 1);
    }

    @TruffleBoundary
    public void setInitialIndex(int initialIndex) {
        curRecording().setInitialIndex(initialIndex);
    }

    @TruffleBoundary
    public void recordTransition(int currentIndex, short stateNodeID, int transitionIndex) {
        int transitionID = dfa.getState(stateNodeID).getSuccessors()[transitionIndex].getId();
        curRecording().recordTransition(currentIndex, transitionID);
    }

    @TruffleBoundary
    public void recordCGPartialTransition(int currentIndex, int cgPartialTransitionIndex) {
        curRecording().recordCGPartialTransition(currentIndex, cgPartialTransitionIndex);
    }

    @TruffleBoundary
    public void finishRecording() {
        TruffleFile file = RegexLanguage.getCurrentContext().getEnv().getPublicTruffleFile(
                        "tregex_" + dfa.getDebugDumpName() + "_" + dfa.getNfa().getAst().getSource().toFileName() + "_recording" + recordings.size() + ".json");
        Json.obj(Json.prop("dfa", dfa), Json.prop("recording", curRecording())).dump(file);
    }

    @TruffleBoundary
    @Override
    public JsonValue toJson() {
        return Json.obj(Json.prop("recordings", recordings));
    }
}
