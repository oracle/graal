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
package com.oracle.truffle.regex.tregex.nfa;

import java.util.ArrayList;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.regex.UnsupportedRegexException;
import com.oracle.truffle.regex.tregex.TRegexOptions;
import com.oracle.truffle.regex.tregex.parser.ast.RegexASTNode;
import com.oracle.truffle.regex.tregex.util.json.Json;
import com.oracle.truffle.regex.tregex.util.json.JsonConvertible;
import com.oracle.truffle.regex.tregex.util.json.JsonValue;

public final class ASTStep implements JsonConvertible {

    private final RegexASTNode root;
    private final ArrayList<ASTSuccessor> successors = new ArrayList<>();

    public ASTStep(RegexASTNode root) {
        this.root = root;
    }

    public RegexASTNode getRoot() {
        return root;
    }

    public ArrayList<ASTSuccessor> getSuccessors() {
        return successors;
    }

    public void addSuccessor(ASTSuccessor successor) {
        successors.add(successor);
        // When compiling a regular expression, almost all ASTSuccessors will yield at least 1 NFA
        // transition (the only case when an ASTSuccessor yields no NFA transitions is when the NFA
        // transition would collide with a position assertion such as $ or ^, as in /(?=a$)ab/, see
        // NFAGenerator#createNFATransitions). Furthermore, there exist regular expressions such as
        // (a?|b?|c?|d?|e?|f?|g?)(a?|b?|c?|d?|e?|f?|g?)... The number of ASTSuccessors in a single
        // ASTStep rises exponentially with the number of repetitions of this pattern (there is a
        // different ASTSuccessor for every possible path to a next matching character). If we want
        // to avoid running out of memory in such situations, we have to bailout during the
        // collection of ASTSuccessors in ASTStep, before they are transformed into NFA transitions.
        if (successors.size() > TRegexOptions.TRegexMaxNumberOfASTSuccessorsInOneASTStep) {
            throw new UnsupportedRegexException("ASTSuccessor explosion");
        }
    }

    @TruffleBoundary
    @Override
    public JsonValue toJson() {
        return Json.obj(Json.prop("root", root.getId()),
                        Json.prop("successors", successors));
    }
}
