/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.regex.tregex.util.DebugUtil;

/**
 * Provides information about a transition from one NFAState to another state.
 */
public class NFAStateTransition {

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

    public DebugUtil.Table toTable() {
        return new DebugUtil.Table("NFATransition",
                        new DebugUtil.Value("target", target.idToString()),
                        groupBoundaries.toTable());
    }
}
