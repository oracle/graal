/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Arrays;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.regex.tregex.util.json.Json;
import com.oracle.truffle.regex.tregex.util.json.JsonConvertible;
import com.oracle.truffle.regex.tregex.util.json.JsonObject;
import com.oracle.truffle.regex.tregex.util.json.JsonValue;

public final class DFASimpleCG implements JsonConvertible {

    @CompilationFinal(dimensions = 1) private final DFASimpleCGTransition[] transitions;
    private final DFASimpleCGTransition transitionToFinalState;
    private final DFASimpleCGTransition transitionToAnchoredFinalState;

    private DFASimpleCG(
                    DFASimpleCGTransition[] transitions,
                    DFASimpleCGTransition transitionToFinalState,
                    DFASimpleCGTransition transitionToAnchoredFinalState) {
        this.transitions = transitions;
        this.transitionToFinalState = transitionToFinalState;
        this.transitionToAnchoredFinalState = transitionToAnchoredFinalState;
    }

    public static DFASimpleCG create(
                    DFASimpleCGTransition[] transitions,
                    DFASimpleCGTransition transitionToFinalState,
                    DFASimpleCGTransition transitionToAnchoredFinalState) {
        if (allEmpty(transitions) && transitionToFinalState == DFASimpleCGTransition.getEmptyInstance() && transitionToAnchoredFinalState == DFASimpleCGTransition.getEmptyInstance()) {
            return null;
        }
        return new DFASimpleCG(transitions, transitionToFinalState, transitionToAnchoredFinalState);
    }

    private static boolean allEmpty(DFASimpleCGTransition[] transitions) {
        for (DFASimpleCGTransition t : transitions) {
            if (t != DFASimpleCGTransition.getEmptyInstance()) {
                return false;
            }
        }
        return true;
    }

    public DFASimpleCGTransition[] getTransitions() {
        return transitions;
    }

    public DFASimpleCGTransition getTransitionToFinalState() {
        return transitionToFinalState;
    }

    public DFASimpleCGTransition getTransitionToAnchoredFinalState() {
        return transitionToAnchoredFinalState;
    }

    @TruffleBoundary
    @Override
    public JsonValue toJson() {
        JsonObject json = Json.obj(Json.prop("transitionToSelf", Arrays.asList(transitions)));
        if (transitionToAnchoredFinalState != null) {
            json.append(Json.prop("transitionToAnchoredFinalState", transitionToAnchoredFinalState));
        }
        if (transitionToFinalState != null) {
            json.append(Json.prop("transitionToFinalState", transitionToFinalState));
        }
        return json;
    }
}
