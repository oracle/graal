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

import java.util.concurrent.TimeUnit;

public final class Profile {

    private final ProfileNode[] nodes;
    private final long startTime;
    private final long endTime;
    private final TimeLineItem[] timeLine;

    public Profile(ProfileNode[] nodes, long startTime, long endTime, TimeLineItem[] timeLine) {
        this.nodes = nodes;
        this.startTime = startTime;
        this.endTime = endTime;
        this.timeLine = timeLine;
    }

    public ProfileNode[] getNodes() {
        return nodes;
    }

    public long getStartTime() {
        return startTime;
    }

    public long getEndTime() {
        return endTime;
    }

    public TimeLineItem[] getTimeLine() {
        return timeLine;
    }

    public JSONObject toJSON() {
        JSONObject json = new JSONObject();
        json.put("nodes", ProfileNode.toJSON(nodes));
        json.put("startTime", TimeUnit.MILLISECONDS.toMicros(startTime));
        json.put("endTime", TimeUnit.MILLISECONDS.toMicros(endTime));
        JSONArray samples = new JSONArray();
        JSONArray timeDeltas = new JSONArray();
        long lastTimestamp = startTime;
        for (TimeLineItem item : timeLine) {
            timeDeltas.put(TimeUnit.MILLISECONDS.toMicros(item.timestamp - lastTimestamp));
            samples.put(item.id);
            lastTimestamp = item.timestamp;
        }
        json.put("samples", samples);
        json.put("timeDeltas", timeDeltas);
        return json;
    }

    public static final class TimeLineItem {

        private final long timestamp;
        private final int id;

        public TimeLineItem(long timestamp, int id) {
            this.timestamp = timestamp;
            this.id = id;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public int getId() {
            return id;
        }
    }
}
