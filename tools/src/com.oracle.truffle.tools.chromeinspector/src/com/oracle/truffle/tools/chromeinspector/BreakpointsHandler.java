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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONObject;

import com.oracle.truffle.api.debug.Breakpoint;
import com.oracle.truffle.api.debug.DebuggerSession;

import com.oracle.truffle.tools.chromeinspector.commands.Params;
import com.oracle.truffle.tools.chromeinspector.server.CommandProcessException;
import com.oracle.truffle.tools.chromeinspector.types.Location;
import com.oracle.truffle.tools.chromeinspector.types.Script;

final class BreakpointsHandler {

    private long lastID = 0;

    private final DebuggerSession ds;
    private final ScriptsHandler slh;
    private final Map<Breakpoint, Long> bpIDs = new HashMap<>();
    private final Map<Long, BPInfo> urlBPs = new HashMap<>();

    BreakpointsHandler(DebuggerSession ds, ScriptsHandler slh) {
        this.ds = ds;
        this.slh = slh;
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

    Params createURLBreakpoint(Object url, int line, int column, String condition) {
        long id;
        synchronized (bpIDs) {
            id = ++lastID;
            urlBPs.put(id, new BPInfo(url, line, column, condition));
        }
        JSONArray locations = new JSONArray();
        slh.getScripts().stream().filter(script -> url instanceof Pattern ? ((Pattern) url).matcher(script.getUrl()).matches() : url.equals(script.getUrl())).forEach(script -> {
            Breakpoint bp = Breakpoint.newBuilder(script.getSource()).lineIs(line).build();
            if (column > 0) {
                // TODO set breakpoint's column
            }
            if (condition != null && !condition.isEmpty()) {
                bp.setCondition(condition);
            }
            bp = ds.install(bp);
            synchronized (bpIDs) {
                bpIDs.put(bp, id);
            }
            locations.put(new Location(script.getId(), line, column).toJSON());
        });
        JSONObject json = new JSONObject();
        json.put("breakpointId", Long.toString(id));
        json.put("locations", locations);
        return new Params(json);
    }

    Params createBreakpoint(Location location, String condition) throws CommandProcessException {
        Script script = slh.getScript(location.getScriptId());
        if (script == null) {
            throw new CommandProcessException("No script with id '" + location.getScriptId() + "'");
        }
        Breakpoint bp = Breakpoint.newBuilder(script.getSource()).lineIs(location.getLine()).build();
        if (condition != null && !condition.isEmpty()) {
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
        boolean bpRemoved = false;
        try {
            long id = Long.parseLong(idStr);
            synchronized (bpIDs) {
                Iterator<Map.Entry<Breakpoint, Long>> bpEntryIt = bpIDs.entrySet().iterator();
                while (bpEntryIt.hasNext()) {
                    Map.Entry<Breakpoint, Long> bpEntry = bpEntryIt.next();
                    if (id == bpEntry.getValue().longValue()) {
                        Breakpoint bp = bpEntry.getKey();
                        if (bp != null) {
                            bp.dispose();
                        }
                        bpEntryIt.remove();
                        bpRemoved = true;
                    }
                }
                if (urlBPs.remove(id) != null) {
                    bpRemoved = true;
                }
            }
        } catch (NumberFormatException nfex) {
        }
        return bpRemoved;
    }

    void createOneShotBreakpoint(Location location) throws CommandProcessException {
        Script script = slh.getScript(location.getScriptId());
        if (script == null) {
            throw new CommandProcessException("No script with id '" + location.getScriptId() + "'");
        }
        Breakpoint bp = Breakpoint.newBuilder(script.getSource()).lineIs(location.getLine()).oneShot().build();
        ds.install(bp);
    }

    List<Params> resolveURLBreakpoints(Script script) {
        List<Params> resolvedBPLocations = new ArrayList<>();
        urlBPs.entrySet().forEach(urlBPEntry -> {
            BPInfo urlBP = urlBPEntry.getValue();
            if (urlBP.url instanceof Pattern ? ((Pattern) urlBP.url).matcher(script.getUrl()).matches() : urlBP.url.equals(script.getUrl())) {
                Breakpoint bp = Breakpoint.newBuilder(script.getSource()).lineIs(urlBP.line).build();
                if (urlBP.condition != null && !urlBP.condition.isEmpty()) {
                    bp.setCondition(urlBP.condition);
                }
                bp = ds.install(bp);
                synchronized (bpIDs) {
                    bpIDs.put(bp, urlBPEntry.getKey());
                }
                JSONObject json = new JSONObject();
                json.put("breakpointId", Long.toString(urlBPEntry.getKey()));
                JSONArray locations = new JSONArray();
                locations.put(new Location(script.getId(), urlBP.line, urlBP.column).toJSON());
                json.put("locations", locations);
                resolvedBPLocations.add(new Params(json));
            }
        });
        return resolvedBPLocations;
    }

    private static final class BPInfo {
        private Object url;
        private int line;
        private int column;
        private String condition;

        private BPInfo(Object url, int line, int column, String condition) {
            this.url = url;
            this.line = line;
            this.column = column;
            this.condition = condition;
        }
    }
}
