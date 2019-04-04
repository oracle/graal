/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.regex.tregex.parser.ast.Group;
import com.oracle.truffle.regex.tregex.parser.ast.GroupBoundaries;
import com.oracle.truffle.regex.tregex.util.json.Json;
import com.oracle.truffle.regex.tregex.util.json.JsonArray;
import com.oracle.truffle.regex.tregex.util.json.JsonConvertible;
import com.oracle.truffle.regex.tregex.util.json.JsonValue;

import static com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

/**
 * Provides information about a transition from one NFAState to another state.
 */
public class NFAStateTransition implements JsonConvertible {

    private final short id;
    private final NFAState source;
    private final NFAState target;
    private final GroupBoundaries groupBoundaries;

    public NFAStateTransition(short id, NFAState source, NFAState target, GroupBoundaries groupBoundaries) {
        this.id = id;
        this.source = source;
        this.target = target;
        this.groupBoundaries = groupBoundaries;
    }

    public short getId() {
        return id;
    }

    public NFAState getSource() {
        return source;
    }

    public NFAState getTarget() {
        return target;
    }

    public NFAState getTarget(boolean forward) {
        return forward ? target : source;
    }

    public NFAState getSource(boolean forward) {
        return forward ? source : target;
    }

    /**
     * groups entered and exited by this transition.
     */
    public GroupBoundaries getGroupBoundaries() {
        return groupBoundaries;
    }

    @TruffleBoundary
    private JsonArray sourceSectionsToJson() {
        if (!groupBoundaries.hasIndexUpdates()) {
            return Json.array();
        }
        return Json.array(groupBoundaries.getUpdateIndices().stream().mapToObj(x -> {
            Group group = source.getStateSet().getAst().getGroupByBoundaryIndex(x);
            SourceSection sourceSection = (x & 1) == 0 ? group.getSourceSectionBegin() : group.getSourceSectionEnd();
            return Json.obj(Json.prop("start", sourceSection.getCharIndex()),
                            Json.prop("end", sourceSection.getCharEndIndex()));
        }));
    }

    @TruffleBoundary
    @Override
    public JsonValue toJson() {
        return Json.obj(Json.prop("id", id),
                        Json.prop("source", source.getId()),
                        Json.prop("target", target.getId()),
                        Json.prop("groupBoundaries", groupBoundaries),
                        Json.prop("sourceSections", sourceSectionsToJson()));
    }

    @TruffleBoundary
    public JsonValue toJson(boolean forward) {
        return Json.obj(Json.prop("id", id),
                        Json.prop("source", getSource(forward).getId()),
                        Json.prop("target", getTarget(forward).getId()),
                        Json.prop("groupBoundaries", groupBoundaries),
                        Json.prop("sourceSections", sourceSectionsToJson()));
    }
}
