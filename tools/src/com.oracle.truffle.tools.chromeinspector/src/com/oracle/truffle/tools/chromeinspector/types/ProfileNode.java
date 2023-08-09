/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tools.chromeinspector.types;

import org.graalvm.shadowed.org.json.JSONArray;
import org.graalvm.shadowed.org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class ProfileNode {

    private final int id;
    private final RuntimeCallFrame callFrame;
    private final long hitCount;
    private final List<Integer> children;

    public ProfileNode(int id, RuntimeCallFrame callFrame, long hitCount) {
        this.id = id;
        this.callFrame = callFrame;
        this.hitCount = hitCount;
        this.children = new ArrayList<>();
    }

    public int getId() {
        return id;
    }

    public RuntimeCallFrame getCallFrame() {
        return callFrame;
    }

    public long getHitCount() {
        return hitCount;
    }

    public void addChild(int childId) {
        children.add(childId);
    }

    private JSONObject toJSON() {
        JSONObject json = new JSONObject();
        json.put("id", id);
        json.put("callFrame", callFrame.toJSON());
        json.put("hitCount", hitCount);
        JSONArray array = new JSONArray();
        children.forEach(i -> {
            array.put(i.intValue());
        });
        json.put("children", array);
        return json;
    }

    static JSONArray toJSON(ProfileNode[] nodes) {
        JSONArray array = new JSONArray();
        for (ProfileNode node : nodes) {
            array.put(node.toJSON());
        }
        return array;
    }
}
