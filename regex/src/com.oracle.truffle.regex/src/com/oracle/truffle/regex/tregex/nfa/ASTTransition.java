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

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.regex.tregex.parser.ast.GroupBoundaries;
import com.oracle.truffle.regex.tregex.parser.ast.Term;
import com.oracle.truffle.regex.tregex.util.json.Json;
import com.oracle.truffle.regex.tregex.util.json.JsonConvertible;
import com.oracle.truffle.regex.tregex.util.json.JsonValue;

public class ASTTransition implements JsonConvertible {

    private Term target;
    private GroupBoundaries groupBoundaries = GroupBoundaries.getEmptyInstance();

    public ASTTransition() {
    }

    public ASTTransition(Term target) {
        this.target = target;
    }

    public Term getTarget() {
        return target;
    }

    public void setTarget(Term target) {
        this.target = target;
    }

    public GroupBoundaries getGroupBoundaries() {
        return groupBoundaries;
    }

    public void setGroupBoundaries(GroupBoundaries groupBoundaries) {
        this.groupBoundaries = groupBoundaries;
    }

    @Override
    public int hashCode() {
        return target.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof ASTTransition && target == ((ASTTransition) obj).target;
    }

    @TruffleBoundary
    @Override
    public JsonValue toJson() {
        return Json.obj(Json.prop("target", target.getId()),
                        Json.prop("groupBoundaries", groupBoundaries));
    }
}
