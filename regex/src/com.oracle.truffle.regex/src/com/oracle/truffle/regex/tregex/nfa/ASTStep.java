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
package com.oracle.truffle.regex.tregex.nfa;

import com.oracle.truffle.regex.UnsupportedRegexException;
import com.oracle.truffle.regex.tregex.TRegexOptions;
import com.oracle.truffle.regex.tregex.parser.ast.RegexASTNode;
import com.oracle.truffle.regex.tregex.util.json.Json;
import com.oracle.truffle.regex.tregex.util.json.JsonConvertible;
import com.oracle.truffle.regex.tregex.util.json.JsonValue;

import java.util.ArrayList;

import static com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

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
