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
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import com.oracle.truffle.tools.utils.json.JSONArray;
import com.oracle.truffle.tools.utils.json.JSONObject;

import com.oracle.truffle.api.debug.Breakpoint;
import com.oracle.truffle.api.debug.DebugValue;
import com.oracle.truffle.api.debug.DebuggerSession;
import com.oracle.truffle.api.debug.SourceElement;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

import com.oracle.truffle.tools.chromeinspector.ScriptsHandler.LoadScriptListener;
import com.oracle.truffle.tools.chromeinspector.commands.Params;
import com.oracle.truffle.tools.chromeinspector.events.Event;
import com.oracle.truffle.tools.chromeinspector.events.EventHandler;
import com.oracle.truffle.tools.chromeinspector.server.CommandProcessException;
import com.oracle.truffle.tools.chromeinspector.types.Location;
import com.oracle.truffle.tools.chromeinspector.types.Script;

final class BreakpointsHandler {

    private long lastID = 0;

    private final DebuggerSession ds;
    private final ScriptsHandler slh;
    private final ResolvedHandler resolvedHandler;
    private final Map<Breakpoint, Long> bpIDs = new HashMap<>();
    private final Map<Breakpoint, SourceSection> resolvedBreakpoints = new HashMap<>();
    private final Map<Long, LoadScriptListener> scriptListeners = new HashMap<>();
    private final AtomicReference<Breakpoint> exceptionBreakpoint = new AtomicReference<>();

    BreakpointsHandler(DebuggerSession ds, ScriptsHandler slh, Supplier<EventHandler> eventHandler) {
        this.ds = ds;
        this.slh = slh;
        this.resolvedHandler = new ResolvedHandler(eventHandler);
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
        JSONArray locations = new JSONArray();
        long id;
        LoadScriptListener scriptListener;
        synchronized (bpIDs) {
            id = ++lastID;
            scriptListener = script -> {
                if (url instanceof Pattern ? ((Pattern) url).matcher(script.getUrl()).matches() : ScriptsHandler.compareURLs((String) url, script.getUrl())) {
                    Breakpoint bp = createBuilder(script.getSource(), line, column).resolveListener(resolvedHandler).build();
                    if (condition != null && !condition.isEmpty()) {
                        bp.setCondition(condition);
                    }
                    bp = ds.install(bp);
                    synchronized (bpIDs) {
                        bpIDs.put(bp, id);
                        SourceSection section = resolvedBreakpoints.remove(bp);
                        if (section != null) {
                            Location resolvedLocation = new Location(script.getId(), section.getStartLine(), section.getStartColumn());
                            locations.put(resolvedLocation.toJSON());
                        }
                    }
                }
            };
            scriptListeners.put(id, scriptListener);
        }
        slh.addLoadScriptListener(scriptListener);
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
        Breakpoint bp = createBuilder(script.getSource(), location.getLine(), location.getColumn()).resolveListener(resolvedHandler).build();
        if (condition != null && !condition.isEmpty()) {
            bp.setCondition(condition);
        }
        bp = ds.install(bp);
        Location resolvedLocation = location;
        long id;
        synchronized (bpIDs) {
            id = ++lastID;
            bpIDs.put(bp, id);
            SourceSection section = resolvedBreakpoints.remove(bp);
            if (section != null) {
                resolvedLocation = new Location(location.getScriptId(), section.getStartLine(), section.getStartColumn());
            }
        }
        JSONObject json = new JSONObject();
        json.put("breakpointId", Long.toString(id));
        json.put("actualLocation", resolvedLocation.toJSON());
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
                LoadScriptListener scriptListener = scriptListeners.remove(id);
                if (scriptListener != null) {
                    slh.removeLoadScriptListener(scriptListener);
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
        Breakpoint bp = createBuilder(script.getSource(), location.getLine(), location.getColumn()).oneShot().build();
        ds.install(bp);
    }

    void setExceptionBreakpoint(boolean caught, boolean uncaught) {
        Breakpoint newBp = null;
        if (caught || uncaught) {
            newBp = Breakpoint.newExceptionBuilder(caught, uncaught).build();
            ds.install(newBp);
        }
        Breakpoint oldBp = exceptionBreakpoint.getAndSet(newBp);
        if (oldBp != null) {
            oldBp.dispose();
        }
    }

    private static Breakpoint.Builder createBuilder(Source source, int line, int column) {
        Breakpoint.Builder builder = Breakpoint.newBuilder(source).lineIs(line);
        if (column > 0) {
            builder.columnIs(column);
        }
        return builder;
    }

    Params createFunctionBreakpoint(DebugValue functionValue, String condition) {
        SourceSection functionLocation = functionValue.getSourceLocation();
        Breakpoint.Builder builder;
        if (functionLocation != null) {
            builder = Breakpoint.newBuilder(functionLocation);
        } else {
            builder = Breakpoint.newBuilder((URI) null);
        }
        builder.rootInstance(functionValue);
        builder.sourceElements(SourceElement.ROOT);
        Breakpoint bp = builder.build();
        if (condition != null && !condition.isEmpty()) {
            bp.setCondition(condition);
        }
        ds.install(bp);
        long id;
        synchronized (bpIDs) {
            id = ++lastID;
            bpIDs.put(bp, id);
        }
        JSONObject json = new JSONObject();
        json.put("breakpointId", Long.toString(id));
        return new Params(json);
    }

    void removeFunctionBreakpoint(DebugValue functionValue) {
        List<Breakpoint> breakpoints = functionValue.getRootInstanceBreakpoints();
        for (Breakpoint breakpoint : breakpoints) {
            String id = getId(breakpoint);
            if (id != null) {
                removeBreakpoint(id);
            }
        }
    }

    private final class ResolvedHandler implements Breakpoint.ResolveListener {

        private final Supplier<EventHandler> eventHandler;

        private ResolvedHandler(Supplier<EventHandler> eventHandler) {
            this.eventHandler = eventHandler;
        }

        @Override
        public void breakpointResolved(Breakpoint breakpoint, SourceSection section) {
            Long breakpointId;
            synchronized (bpIDs) {
                breakpointId = bpIDs.get(breakpoint);
                if (breakpointId == null) {
                    resolvedBreakpoints.put(breakpoint, section);
                    return;
                }
            }
            int scriptId = slh.getScriptId(section.getSource());
            Location location = new Location(scriptId, section.getStartLine(), section.getStartColumn());
            JSONObject jsonParams = new JSONObject();
            jsonParams.put("breakpointId", Long.toString(breakpointId));
            jsonParams.put("location", location.toJSON());
            Params params = new Params(jsonParams);
            Event event = new Event("Debugger.breakpointResolved", params);
            eventHandler.get().event(event);
        }

    }

}
