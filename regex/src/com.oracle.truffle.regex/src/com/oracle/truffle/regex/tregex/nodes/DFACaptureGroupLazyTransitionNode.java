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
package com.oracle.truffle.regex.tregex.nodes;

import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.regex.tregex.util.json.Json;
import com.oracle.truffle.regex.tregex.util.json.JsonConvertible;
import com.oracle.truffle.regex.tregex.util.json.JsonObject;
import com.oracle.truffle.regex.tregex.util.json.JsonValue;

import java.util.Arrays;

import static com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

public final class DFACaptureGroupLazyTransitionNode extends Node implements JsonConvertible {

    private final short id;
    @Children private final DFACaptureGroupPartialTransitionNode[] partialTransitions;
    private final DFACaptureGroupPartialTransitionNode transitionToFinalState;
    private final DFACaptureGroupPartialTransitionNode transitionToAnchoredFinalState;

    public DFACaptureGroupLazyTransitionNode(short id,
                    DFACaptureGroupPartialTransitionNode[] partialTransitions,
                    DFACaptureGroupPartialTransitionNode transitionToFinalState,
                    DFACaptureGroupPartialTransitionNode transitionToAnchoredFinalState) {
        this.id = id;
        this.partialTransitions = partialTransitions;
        this.transitionToFinalState = transitionToFinalState;
        this.transitionToAnchoredFinalState = transitionToAnchoredFinalState;
    }

    public short getId() {
        return id;
    }

    public DFACaptureGroupPartialTransitionNode[] getPartialTransitions() {
        return partialTransitions;
    }

    public DFACaptureGroupPartialTransitionNode getTransitionToFinalState() {
        return transitionToFinalState;
    }

    public DFACaptureGroupPartialTransitionNode getTransitionToAnchoredFinalState() {
        return transitionToAnchoredFinalState;
    }

    @TruffleBoundary
    @Override
    public JsonValue toJson() {
        JsonObject json = Json.obj(Json.prop("partialTransitions", Arrays.asList(partialTransitions)));
        if (transitionToAnchoredFinalState != null) {
            json.append(Json.prop("transitionToAnchoredFinalState", transitionToAnchoredFinalState));
        }
        if (transitionToFinalState != null) {
            json.append(Json.prop("transitionToFinalState", transitionToFinalState));
        }
        return json;
    }
}
