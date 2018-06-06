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
package com.oracle.truffle.regex.tregex.parser.ast;

import com.oracle.truffle.regex.tregex.util.json.Json;
import com.oracle.truffle.regex.tregex.util.json.JsonValue;

import static com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

/**
 * A reference to the contents of a previously matched capturing group.
 * <p>
 * Corresponds to the goal symbol <em>DecimalEscape</em> in the ECMAScript RegExp syntax.
 * <p>
 * Currently not implemented in TRegex and so any use of this node type causes TRegex to bail out.
 */
public class BackReference extends Term {

    private final int groupNr;

    BackReference(int groupNr) {
        this.groupNr = groupNr;
    }

    private BackReference(BackReference copy) {
        super(copy);
        groupNr = copy.groupNr;
    }

    @Override
    public BackReference copy(RegexAST ast, boolean recursive) {
        return ast.register(new BackReference(this));
    }

    @Override
    public String toString() {
        return "\\" + groupNr;
    }

    public int getGroupNr() {
        return groupNr;
    }

    @TruffleBoundary
    @Override
    public JsonValue toJson() {
        return toJson("BackReference").append(Json.prop("groupNr", groupNr));
    }
}
