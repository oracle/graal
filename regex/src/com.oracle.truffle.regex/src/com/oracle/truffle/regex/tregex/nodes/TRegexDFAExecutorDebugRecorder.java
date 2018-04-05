/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.regex.tregex.dfa.DFAGenerator;
import com.oracle.truffle.regex.tregex.util.json.Json;
import com.oracle.truffle.regex.tregex.util.json.JsonConvertible;
import com.oracle.truffle.regex.tregex.util.json.JsonValue;

import java.util.ArrayList;
import java.util.List;

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

        public void recordTransition(int currentIndex, int transitionID) {
            transitions.add(new RecordedTransition(currentIndex, transitionID));
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
        private final int transitionID;

        private RecordedTransition(int currentIndex, int transitionID) {
            this.currentIndex = currentIndex;
            this.transitionID = transitionID;
        }

        @TruffleBoundary
        @Override
        public JsonValue toJson() {
            return Json.obj(Json.prop("currentIndex", currentIndex),
                            Json.prop("transitionID", transitionID));
        }
    }

    public TRegexDFAExecutorDebugRecorder(DFAGenerator dfa) {
        this.dfa = dfa;
    }

    private final DFAGenerator dfa;
    private List<Recording> recordings = new ArrayList<>();

    public void startRecording(VirtualFrame frame, TRegexDFAExecutorNode executor) {
        CompilerAsserts.neverPartOfCompilation();
        recordings.add(new Recording(executor.getInput(frame).toString(), executor.getFromIndex(frame), executor.getIndex(frame), executor.getMaxIndex(frame)));
    }

    private Recording curRecording() {
        return recordings.get(recordings.size() - 1);
    }

    public void recordTransition(int currentIndex, short stateNodeID, int transitionIndex) {
        CompilerAsserts.neverPartOfCompilation();
        int transitionID = dfa.getState(stateNodeID).getTransitions()[transitionIndex].getId();
        curRecording().recordTransition(currentIndex, transitionID);
    }

    public void finishRecording() {
        CompilerAsserts.neverPartOfCompilation();
        Json.obj(Json.prop("dfa", dfa), Json.prop("recording", curRecording())).dump(
                        "tregex_" + dfa.getDebugDumpName() + "_" + dfa.getNfa().getAst().getSource().toFileName() + "_recording" + recordings.size() + ".json");
    }

    @TruffleBoundary
    @Override
    public JsonValue toJson() {
        return Json.obj(Json.prop("recordings", recordings));
    }
}
