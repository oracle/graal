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
package com.oracle.truffle.tools.chromeinspector;

import java.net.URI;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;

import com.oracle.truffle.api.debug.Breakpoint;
import com.oracle.truffle.api.debug.DebuggerSession;

import com.oracle.truffle.tools.chromeinspector.commands.Params;
import com.oracle.truffle.tools.chromeinspector.types.Location;

final class BreakpointsHandler {

    private long lastID = 0;

    private final DebuggerSession ds;
    private final Map<Breakpoint, Long> bpIDs = new HashMap<>();

    BreakpointsHandler(DebuggerSession ds) {
        this.ds = ds;
    }

    String getId(Breakpoint bp) {
        synchronized (bpIDs) {
            Long id = bpIDs.get(bp);
            if (id != null) {
                return id.toString();
            } else {
                return null;
            }
        }
    }

    Params createURLBreakpoint(URI uri, int line, int column, String condition) {
        Breakpoint bp = Breakpoint.newBuilder(uri).lineIs(line).build();
        if (column > 0) {
            // TODO set breakpoint's column
        }
        if (condition != null && !condition.isEmpty()) {
            bp.setCondition(condition);
        }
        bp = ds.install(bp);
        long id;
        synchronized (bpIDs) {
            id = ++lastID;
            bpIDs.put(bp, id);
        }
        // TODO: Get resolved info
        // Location loc = new Location(, line, column);
        JSONObject json = new JSONObject();
        json.put("breakpointId", Long.toString(id));
        json.put("locations", new JSONArray());
        return new Params(json);
    }

    Params createBreakpoint(Location location, URI uri, String condition) {
        Breakpoint bp = Breakpoint.newBuilder(uri).lineIs(location.getLine()).build();
        if (condition != null) {
            bp.setCondition(condition);
        }
        bp = ds.install(bp);
        long id;
        synchronized (bpIDs) {
            id = ++lastID;
            bpIDs.put(bp, id);
        }
        JSONObject json = new JSONObject();
        json.put("breakpointId", Long.toString(id));
        JSONArray locations = new JSONArray();
        locations.put(location.toJSON());
        json.put("locations", locations);
        return new Params(json);
    }

    boolean removeBreakpoint(String idStr) {
        long id;
        try {
            id = Long.parseLong(idStr);
        } catch (NumberFormatException nfex) {
            return false;
        }
        Breakpoint bp = null;
        synchronized (bpIDs) {
            Iterator<Map.Entry<Breakpoint, Long>> bpEntryIt = bpIDs.entrySet().iterator();
            while (bpEntryIt.hasNext()) {
                Map.Entry<Breakpoint, Long> bpEntry = bpEntryIt.next();
                if (id == bpEntry.getValue().longValue()) {
                    bp = bpEntry.getKey();
                    bpEntryIt.remove();
                    break;
                }
            }
        }
        if (bp == null) {
            return false;
        }
        bp.dispose();
        return true;
    }

    void createOneShotBreakpoint(Location location, URI uri) {
        Breakpoint bp = Breakpoint.newBuilder(uri).lineIs(location.getLine()).oneShot().build();
        ds.install(bp);
    }

}
