/*
 * Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Arrays;
import java.util.List;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.regex.RegexLanguage.RegexContext;
import com.oracle.truffle.regex.RegexOptions;
import com.oracle.truffle.regex.tregex.dfa.DFAGenerator;
import com.oracle.truffle.regex.tregex.nodes.TRegexExecutorBaseNode;
import com.oracle.truffle.regex.tregex.nodes.TRegexExecutorLocals;
import com.oracle.truffle.regex.tregex.string.Encodings;
import com.oracle.truffle.regex.tregex.util.json.Json;
import com.oracle.truffle.regex.tregex.util.json.JsonArray;
import com.oracle.truffle.regex.tregex.util.json.JsonConvertible;
import com.oracle.truffle.regex.tregex.util.json.JsonValue;

/**
 * This class is used to store a trace of the execution of a
 * {@link TRegexExecutorBaseNode#execute(com.oracle.truffle.api.frame.VirtualFrame, TRegexExecutorLocals, TruffleString.CodeRange)}.
 * A trace contains the arguments received by
 * {@link TRegexExecutorBaseNode#execute(com.oracle.truffle.api.frame.VirtualFrame, TRegexExecutorLocals, TruffleString.CodeRange)},
 * and the ID of the DFA transition taken for all characters of the input string that have been
 * traversed. After execution, the recorded trace can be dumped to disk as JSON with
 * {@link #finishRecording()}.
 */
public final class TRegexDFAExecutorDebugRecorder implements JsonConvertible {

    private static final class Recording implements JsonConvertible {

        private final TruffleString input;
        private final Encodings.Encoding encoding;
        private final int fromIndex;
        private int initialIndex;
        private final int maxIndex;
        private final boolean forward;
        private final int[] transitions;
        private final int[] cgPartialTransitions;

        private Recording(TruffleString input, Encodings.Encoding encoding, int fromIndex, int initialIndex, int maxIndex, boolean forward) {
            this.input = input;
            this.encoding = encoding;
            this.fromIndex = fromIndex;
            this.initialIndex = initialIndex;
            this.maxIndex = maxIndex;
            this.forward = forward;
            int codepoints = input.codePointLengthUncached(encoding.getTStringEncoding());
            transitions = new int[codepoints];
            cgPartialTransitions = new int[codepoints];
            Arrays.fill(transitions, -1);
            Arrays.fill(cgPartialTransitions, -1);
        }

        @TruffleBoundary
        public void setInitialIndex(int initialIndex) {
            this.initialIndex = initialIndex;
        }

        @TruffleBoundary
        public void recordTransition(int currentIndex, int transitionID) {
            transitions[toCodePointIndex(currentIndex)] = transitionID;
        }

        @TruffleBoundary
        public void recordCGPartialTransition(int currentIndex, int cgPartialTransitionIndex) {
            cgPartialTransitions[toCodePointIndex(currentIndex)] = cgPartialTransitionIndex;
        }

        private int toCodePointIndex(int currentIndex) {
            return input.byteIndexToCodePointIndexUncached(0, currentIndex << encoding.getStride(), encoding.getTStringEncoding()) - (forward ? 0 : 1);
        }

        @TruffleBoundary
        @Override
        public JsonValue toJson() {
            JsonArray jsonTransitions = Json.array();
            if (forward) {
                for (int i = 0; i < transitions.length; i++) {
                    appendJsonTransition(i, jsonTransitions);
                }
            } else {
                for (int i = transitions.length - 1; i >= 0; i--) {
                    appendJsonTransition(i, jsonTransitions);
                }
            }
            return Json.obj(Json.prop("input", input.toJavaStringUncached()),
                            Json.prop("fromIndex", fromIndex),
                            Json.prop("initialIndex", initialIndex),
                            Json.prop("maxIndex", maxIndex),
                            Json.prop("transitions", jsonTransitions));
        }

        private void appendJsonTransition(int i, JsonArray jsonTransitions) {
            if (transitions[i] >= 0) {
                jsonTransitions.append(Json.obj(
                                Json.prop("currentIndex", i),
                                Json.prop("transitionID", transitions[i]),
                                Json.prop("cgPartialTransitionID", cgPartialTransitions[i])));
            }
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
    private final List<Recording> recordings = new ArrayList<>();

    @TruffleBoundary
    public void startRecording(TRegexDFAExecutorLocals locals) {
        recordings.add(new Recording(locals.getInput(), dfa.getOptions().getEncoding(), locals.getFromIndex(), locals.getIndex(), locals.getMaxIndex(), dfa.isForward()));
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
        TruffleFile file = RegexContext.get(null).getEnv().getPublicTruffleFile(
                        "tregex_" + dfa.getDebugDumpName() + "_" + dfa.getNfa().getAst().getSource().toFileName() + "_recording" + recordings.size() + ".json");
        Json.obj(Json.prop("dfa", dfa), Json.prop("recording", curRecording())).dump(file);
    }

    @TruffleBoundary
    @Override
    public JsonValue toJson() {
        return Json.obj(Json.prop("recordings", recordings));
    }
}
