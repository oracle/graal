/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tools.dap.server;

import com.oracle.truffle.api.debug.Breakpoint;
import com.oracle.truffle.api.debug.DebugStackFrame;
import com.oracle.truffle.api.debug.DebuggerSession;
import com.oracle.truffle.api.debug.SourceElement;
import com.oracle.truffle.api.instrumentation.LoadSourceSectionEvent;
import com.oracle.truffle.api.instrumentation.LoadSourceSectionListener;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.tools.dap.types.BreakpointEvent;
import com.oracle.truffle.tools.dap.types.BreakpointLocation;
import com.oracle.truffle.tools.dap.types.BreakpointLocationsArguments;
import com.oracle.truffle.tools.dap.types.DebugProtocolClient;
import com.oracle.truffle.tools.dap.types.FunctionBreakpoint;
import com.oracle.truffle.tools.dap.types.SetBreakpointsArguments;
import com.oracle.truffle.tools.dap.types.SetFunctionBreakpointsArguments;
import com.oracle.truffle.tools.dap.types.SourceBreakpoint;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BreakpointsHandler {

    private static final Set<Class<? extends Tag>> SUSPENDABLE_TAGS_SET = Collections.singleton(StandardTags.StatementTag.class);
    private static final Class<?>[] SUSPENDABLE_TAGS = SUSPENDABLE_TAGS_SET.toArray(new Class<?>[SUSPENDABLE_TAGS_SET.size()]);
    private static final Pattern HITCONDITION_REGEXP = Pattern.compile("^(>|>=|=|<|<=|%)?\\s*([0-9]+)$");

    private final ExecutionContext context;
    private final DebuggerSession debuggerSession;
    private final ResolvedHandler resolvedHandler = new ResolvedHandler();
    private final Map<String, Map<SourceBreakpoint, Integer>> sourceBreakpoints = new HashMap<>();
    private final AtomicReference<Map<FunctionBreakpoint, Integer>> functionBreakpoints = new AtomicReference<>();
    private final Map<Breakpoint, Integer> bp2IDs = new HashMap<>();
    private final Map<Integer, Breakpoint> id2Bps = new HashMap<>();
    private final Map<Breakpoint, String> logMessages = new HashMap<>();
    private final Map<Breakpoint, String[]> hitConditions = new HashMap<>();
    private final Map<Breakpoint, String> functionNames = new HashMap<>();
    private final Map<Breakpoint, SourceSection> resolvedBreakpoints = new HashMap<>();
    private final AtomicReference<Breakpoint> exceptionBreakpoint = new AtomicReference<>();
    private int lastId = 0;

    public BreakpointsHandler(ExecutionContext context, DebuggerSession debuggerSession) {
        this.context = context;
        this.debuggerSession = debuggerSession;
    }

    public List<com.oracle.truffle.tools.dap.types.Breakpoint> setBreakpoints(SetBreakpointsArguments args) {
        Source source = null;
        Integer sourceReference = args.getSource().getSourceReference();
        String path = args.getSource().getPath();
        String srcId = null;
        if (sourceReference != null && sourceReference > 0) {
            source = context.getLoadedSourcesHandler().getSource(sourceReference);
            srcId = (path != null ? path : "") + '#' + sourceReference;
        }
        if (source == null && path != null) {
            source = context.getLoadedSourcesHandler().getSource(path);
            srcId = path;
        }
        if (srcId == null) {
            throw Errors.sourceRequestIllegalHandle();
        }
        Map<SourceBreakpoint, Integer> toAdd = new HashMap<>();
        Map<SourceBreakpoint, Integer> toRemove;
        synchronized (bp2IDs) {
            toRemove = sourceBreakpoints.put(srcId, toAdd);
        }
        List<com.oracle.truffle.tools.dap.types.Breakpoint> breakpoints = new ArrayList<>(args.getBreakpoints().size());
        if (source != null) {
            for (SourceBreakpoint sourceBreakpoint : args.getBreakpoints()) {
                Integer id;
                Breakpoint bp;
                SourceSection section;
                String message = null;
                synchronized (bp2IDs) {
                    id = toRemove != null ? toRemove.remove(sourceBreakpoint) : null;
                }
                if (id == null) {
                    Breakpoint.Builder builder = Breakpoint.newBuilder(source).lineIs(context.clientToDebuggerLine(sourceBreakpoint.getLine()));
                    if (sourceBreakpoint.getColumn() != null) {
                        builder.columnIs(context.clientToDebuggerColumn(sourceBreakpoint.getColumn()));
                    }
                    bp = builder.resolveListener(resolvedHandler).build();
                    if (sourceBreakpoint.getCondition() != null && !sourceBreakpoint.getCondition().isEmpty()) {
                        bp.setCondition(sourceBreakpoint.getCondition());
                    }
                    String[] hitCondition = null;
                    if (sourceBreakpoint.getHitCondition() != null) {
                        String trimmedHitCondition = sourceBreakpoint.getHitCondition().trim();
                        if (!trimmedHitCondition.isEmpty()) {
                            Matcher matcher = HITCONDITION_REGEXP.matcher(trimmedHitCondition);
                            if (matcher.matches() && matcher.groupCount() == 2) {
                                if (matcher.group(0) == null) {
                                    builder.ignoreCount(Integer.parseInt(matcher.group(2)));
                                } else {
                                    hitCondition = new String[]{matcher.group(1), matcher.group(2)};
                                }
                            } else {
                                message = "Invalid hit condition: " + trimmedHitCondition;
                            }
                        }
                    }
                    bp = debuggerSession.install(bp);
                    String logMessage = sourceBreakpoint.getLogMessage() != null ? sourceBreakpoint.getLogMessage().trim() : "";
                    synchronized (bp2IDs) {
                        id = ++lastId;
                        bp2IDs.put(bp, id);
                        id2Bps.put(id, bp);
                        if (!logMessage.isEmpty()) {
                            logMessages.put(bp, logMessage);
                        }
                        if (hitCondition != null) {
                            hitConditions.put(bp, hitCondition);
                        }
                    }
                } else {
                    bp = id2Bps.get(id);
                }
                synchronized (bp2IDs) {
                    toAdd.put(sourceBreakpoint, id);
                    section = resolvedBreakpoints.get(bp);
                }
                if (section != null) {
                    breakpoints.add(com.oracle.truffle.tools.dap.types.Breakpoint.create(message == null).setId(id).setMessage(message) //
                                    .setLine(context.debuggerToClientLine(section.getStartLine())).setColumn(context.debuggerToClientColumn(section.getStartColumn())) //
                                    .setEndLine(context.debuggerToClientLine(section.getEndLine())).setEndColumn(context.debuggerToClientColumn(section.getEndColumn())));
                } else {
                    breakpoints.add(com.oracle.truffle.tools.dap.types.Breakpoint.create(false).setId(id).setMessage(message) //
                                    .setLine(sourceBreakpoint.getLine()).setColumn(sourceBreakpoint.getColumn()));
                }
            }
            if (toRemove != null) {
                for (Integer id : toRemove.values()) {
                    synchronized (bp2IDs) {
                        Breakpoint bp = id2Bps.remove(id);
                        if (bp != null) {
                            bp.dispose();
                            bp2IDs.remove(bp);
                            logMessages.remove(bp);
                            hitConditions.remove(bp);
                            resolvedBreakpoints.remove(bp);
                        }
                    }
                }
            }
        } else {
            context.getLoadedSourcesHandler().runOnLoad(path, null);
            List<Consumer<Source>> tasks = new ArrayList<>(args.getBreakpoints().size());
            for (SourceBreakpoint sourceBreakpoint : args.getBreakpoints()) {
                Integer id;
                synchronized (bp2IDs) {
                    id = toRemove != null ? toRemove.remove(sourceBreakpoint) : null;
                    if (id == null) {
                        id = ++lastId;
                        toAdd.put(sourceBreakpoint, id);
                    }
                    final Integer finalId = id;
                    final String logMessage = sourceBreakpoint.getLogMessage() != null ? sourceBreakpoint.getLogMessage().trim() : "";
                    String[] hitCondition = null;
                    String message = null;
                    if (sourceBreakpoint.getHitCondition() != null) {
                        String trimmedHitCondition = sourceBreakpoint.getHitCondition().trim();
                        if (!trimmedHitCondition.isEmpty()) {
                            Matcher matcher = HITCONDITION_REGEXP.matcher(trimmedHitCondition);
                            if (matcher.matches() && matcher.groupCount() == 2) {
                                hitCondition = new String[]{matcher.group(1), matcher.group(2)};
                            } else {
                                message = "Invalid hit condition: " + trimmedHitCondition;
                            }
                        }
                    }
                    final String[] finalHitCondition = hitCondition;
                    final String finalMessage = message;
                    tasks.add((src) -> {
                        Breakpoint.Builder builder = Breakpoint.newBuilder(src).lineIs(context.clientToDebuggerLine(sourceBreakpoint.getLine()));
                        if (sourceBreakpoint.getColumn() != null) {
                            builder.columnIs(context.clientToDebuggerColumn(sourceBreakpoint.getColumn()));
                        }
                        Breakpoint bp = builder.resolveListener(resolvedHandler).build();
                        if (sourceBreakpoint.getCondition() != null && !sourceBreakpoint.getCondition().isEmpty()) {
                            bp.setCondition(sourceBreakpoint.getCondition());
                        }
                        bp = debuggerSession.install(bp);
                        SourceSection section;
                        synchronized (bp2IDs) {
                            bp2IDs.put(bp, finalId);
                            id2Bps.put(finalId, bp);
                            if (!logMessage.isEmpty()) {
                                logMessages.put(bp, logMessage);
                            }
                            if (finalHitCondition != null) {
                                hitConditions.put(bp, finalHitCondition);
                            }
                            section = resolvedBreakpoints.get(bp);
                        }
                        if (section != null) {
                            DebugProtocolClient client = context.getClient();
                            if (client != null) {
                                client.breakpoint(BreakpointEvent.EventBody.create("changed",
                                                com.oracle.truffle.tools.dap.types.Breakpoint.create(finalMessage == null).setId(finalId).setMessage(finalMessage) //
                                                                .setLine(context.debuggerToClientLine(section.getStartLine())).setColumn(context.debuggerToClientColumn(section.getStartColumn())) //
                                                                .setEndLine(context.debuggerToClientLine(section.getEndLine())).setEndColumn(context.debuggerToClientColumn(section.getEndColumn()))));
                            }
                        }
                    });
                    breakpoints.add(com.oracle.truffle.tools.dap.types.Breakpoint.create(false).setId(id).setMessage(finalMessage) //
                                    .setLine(sourceBreakpoint.getLine()).setColumn(sourceBreakpoint.getColumn()));
                }
            }
            if (!tasks.isEmpty()) {
                context.getLoadedSourcesHandler().runOnLoad(path, (src) -> {
                    for (Consumer<Source> task : tasks) {
                        task.accept(src);
                    }
                });
            }
        }
        return breakpoints;
    }

    public List<com.oracle.truffle.tools.dap.types.Breakpoint> setFunctionBreakpoints(SetFunctionBreakpointsArguments args) {
        Map<FunctionBreakpoint, Integer> toAdd = new HashMap<>();
        Map<FunctionBreakpoint, Integer> toRemove = functionBreakpoints.getAndSet(toAdd);
        List<com.oracle.truffle.tools.dap.types.Breakpoint> breakpoints = new ArrayList<>(args.getBreakpoints().size());
        for (FunctionBreakpoint functionBreakpoint : args.getBreakpoints()) {
            Integer id;
            Breakpoint bp;
            SourceSection section;
            String message = null;
            synchronized (bp2IDs) {
                id = toRemove != null ? toRemove.remove(functionBreakpoint) : null;
            }
            if (id == null) {
                Breakpoint.Builder builder = Breakpoint.newBuilder((Source) null).sourceElements(SourceElement.ROOT);
                bp = builder.resolveListener(resolvedHandler).build();
                if (functionBreakpoint.getCondition() != null && !functionBreakpoint.getCondition().isEmpty()) {
                    bp.setCondition(functionBreakpoint.getCondition());
                }
                String[] hitCondition = null;
                if (functionBreakpoint.getHitCondition() != null) {
                    String trimmedHitCondition = functionBreakpoint.getHitCondition().trim();
                    if (!trimmedHitCondition.isEmpty()) {
                        Matcher matcher = HITCONDITION_REGEXP.matcher(trimmedHitCondition);
                        if (matcher.matches() && matcher.groupCount() == 2) {
                            if (matcher.group(0) == null) {
                                builder.ignoreCount(Integer.parseInt(matcher.group(2)));
                            } else {
                                hitCondition = new String[]{matcher.group(1), matcher.group(2)};
                            }
                        } else {
                            message = "Invalid hit condition: " + trimmedHitCondition;
                        }
                    }
                }
                bp = debuggerSession.install(bp);
                synchronized (bp2IDs) {
                    id = ++lastId;
                    bp2IDs.put(bp, id);
                    id2Bps.put(id, bp);
                    if (hitCondition != null) {
                        hitConditions.put(bp, hitCondition);
                    }
                    functionNames.put(bp, functionBreakpoint.getName());
                }
            } else {
                bp = id2Bps.get(id);
            }
            synchronized (bp2IDs) {
                toAdd.put(functionBreakpoint, id);
                section = resolvedBreakpoints.get(bp);
            }
            if (section != null) {
                breakpoints.add(com.oracle.truffle.tools.dap.types.Breakpoint.create(message == null).setId(id).setMessage(message) //
                                .setLine(context.debuggerToClientLine(section.getStartLine())).setColumn(context.debuggerToClientColumn(section.getStartColumn())) //
                                .setEndLine(context.debuggerToClientLine(section.getEndLine())).setEndColumn(context.debuggerToClientColumn(section.getEndColumn())));
            } else {
                breakpoints.add(com.oracle.truffle.tools.dap.types.Breakpoint.create(false).setId(id).setMessage(message));
            }
        }
        if (toRemove != null) {
            for (Integer id : toRemove.values()) {
                synchronized (bp2IDs) {
                    Breakpoint bp = id2Bps.remove(id);
                    if (bp != null) {
                        bp.dispose();
                        bp2IDs.remove(bp);
                        logMessages.remove(bp);
                        hitConditions.remove(bp);
                        resolvedBreakpoints.remove(bp);
                        functionNames.remove(bp);
                    }
                }
            }
        }
        return breakpoints;
    }

    public void setExceptionBreakpoint(boolean caught, boolean uncaught) {
        Breakpoint newBp = null;
        if (caught || uncaught) {
            newBp = Breakpoint.newExceptionBuilder(caught, uncaught).build();
            debuggerSession.install(newBp);
        }
        Breakpoint oldBp = exceptionBreakpoint.getAndSet(newBp);
        if (oldBp != null) {
            oldBp.dispose();
        }
    }

    public List<BreakpointLocation> breakpointLocations(BreakpointLocationsArguments args) {
        Source source;
        Integer sourceReference = args.getSource().getSourceReference();
        if (sourceReference != null && sourceReference > 0) {
            source = context.getLoadedSourcesHandler().getSource(sourceReference);
        } else {
            source = context.getLoadedSourcesHandler().getSource(args.getSource().getPath());
        }
        List<BreakpointLocation> locations = new ArrayList<>();
        if (source != null && source.hasCharacters() && source.getLength() > 0) {
            int lc = source.getLineCount();
            int l1 = context.clientToDebuggerLine(args.getLine());
            int c1 = args.getColumn() != null ? context.clientToDebuggerColumn(args.getColumn()) : 0;
            if (c1 <= 0) {
                c1 = 1;
            }
            if (l1 > lc) {
                l1 = lc;
                c1 = source.getLineLength(l1);
            }
            int l2;
            int c2;
            if (args.getEndLine() != null) {
                l2 = context.clientToDebuggerLine(args.getEndLine());
                c2 = args.getEndColumn() != null ? context.clientToDebuggerColumn(args.getEndColumn()) : source.getLineLength(l2);
                // The end should be exclusive, but not all clients adhere to that.
                if (l1 != l2 || c1 != c2) {
                    // Only when start != end consider end as exclusive:
                    if (l2 > lc) {
                        l2 = lc;
                        c2 = source.getLineLength(l2);
                    } else {
                        if (c2 <= 1) {
                            l2 = l2 - 1;
                            if (l2 <= 0) {
                                l2 = 1;
                            }
                            c2 = source.getLineLength(l2);
                        } else {
                            c2 = c2 - 1;
                        }
                    }
                    if (l1 > l2) {
                        l1 = l2;
                    }
                }
            } else {
                l2 = l1;
                c2 = source.getLineLength(l2);
            }
            if (c2 == 0) {
                c2 = 1; // 1-based column on zero-length line
            }
            if (l1 == l2 && c2 < c1) {
                c1 = c2;
            }
            SourceSection range = source.createSection(l1, c1, l2, c2);
            for (SourceSection ss : findSuspendableLocations(range)) {
                locations.add(BreakpointLocation.create(context.debuggerToClientLine(ss.getStartLine())).setColumn(context.debuggerToClientColumn(ss.getStartColumn())));
            }
        }
        return locations;
    }

    public String getLogMessage(Breakpoint bp) {
        synchronized (bp2IDs) {
            return logMessages.get(bp);
        }
    }

    public boolean checkConditions(Breakpoint bp, DebugStackFrame topStackFrame) {
        String functionName;
        String[] hitCondition;
        synchronized (bp2IDs) {
            functionName = functionNames.get(bp);
            hitCondition = hitConditions.get(bp);
        }
        if (functionName != null) {
            if (!functionName.equals(topStackFrame.getName())) {
                return false;
            }
        }
        if (hitCondition != null) {
            int count = bp.getHitCount();
            int value = Integer.parseInt(hitCondition[1]);
            switch (hitCondition[0]) {
                case ">":
                    return count > value;
                case ">=":
                    return count >= value;
                case "=":
                    return count == value;
                case "<":
                    return count < value;
                case "<=":
                    return count <= value;
                case "%":
                    return (count % value) == 0;
            }
        }
        return true;
    }

    private Iterable<SourceSection> findSuspendableLocations(SourceSection range) {
        Source source = range.getSource();
        int startIndex = range.getCharIndex();
        int endIndex = range.getCharEndIndex();
        SectionsCollector sectionsCollector = collectSuspendableLocations(source, startIndex, endIndex);
        List<SourceSection> sections = sectionsCollector.getSections();
        if (sections.isEmpty()) {
            AtomicReference<SourceSection> nearestSection = new AtomicReference<>();
            // Submit a test breakpoint that will be moved to the nerest suspendable location:
            Breakpoint breakpoint = Breakpoint.newBuilder(source).ignoreCount(Integer.MAX_VALUE).lineIs(range.getStartLine()).columnIs(range.getStartColumn()).resolveListener(
                            new Breakpoint.ResolveListener() {
                                @Override
                                public void breakpointResolved(Breakpoint b, SourceSection section) {
                                    nearestSection.set(section);
                                }
                            }).build();
            try {
                debuggerSession.install(breakpoint);
            } finally {
                // Dispose the test breakpoint, a real breakpoint is likely to be submitted at that
                // location by the protocol client.
                breakpoint.dispose();
            }
            SourceSection suspendableSection = nearestSection.get();
            if (suspendableSection != null) {
                startIndex = suspendableSection.getCharIndex();
                endIndex = suspendableSection.getCharEndIndex();
                sectionsCollector = collectSuspendableLocations(source, startIndex, endIndex);
                sections = sectionsCollector.getSections();
            }
        }
        return sections;
    }

    private SectionsCollector collectSuspendableLocations(Source source, int startIndex, int endIndex) {
        SourceSectionFilter filter = SourceSectionFilter.newBuilder().sourceIs(source).indexIn(SourceSectionFilter.IndexRange.between(startIndex, endIndex)).tagIs(SUSPENDABLE_TAGS).build();
        SectionsCollector sectionsCollector = new SectionsCollector(startIndex);
        context.getEnv().getInstrumenter().visitLoadedSourceSections(filter, sectionsCollector);
        return sectionsCollector;
    }

    private final class ResolvedHandler implements Breakpoint.ResolveListener {

        @Override
        public void breakpointResolved(Breakpoint breakpoint, SourceSection section) {
            Integer breakpointId;
            synchronized (bp2IDs) {
                resolvedBreakpoints.put(breakpoint, section);
                breakpointId = bp2IDs.get(breakpoint);
                if (breakpointId == null) {
                    return;
                }
            }
            DebugProtocolClient client = context.getClient();
            if (client != null) {
                client.breakpoint(BreakpointEvent.EventBody.create("changed",
                                com.oracle.truffle.tools.dap.types.Breakpoint.create(true).setId(breakpointId) //
                                                .setLine(context.debuggerToClientLine(section.getStartLine())).setColumn(context.debuggerToClientColumn(section.getStartColumn())) //
                                                .setEndLine(context.debuggerToClientLine(section.getEndLine())).setEndColumn(context.debuggerToClientColumn(section.getEndColumn()))));
            }
        }
    }

    private static final class SectionsCollector implements LoadSourceSectionListener {

        protected final int startIndex;
        private final List<SourceSection> sections = new ArrayList<>();

        SectionsCollector(int startIndex) {
            this.startIndex = startIndex;
        }

        @Override
        public void onLoad(LoadSourceSectionEvent event) {
            SourceSection section = event.getSourceSection();
            if (section.getCharIndex() >= startIndex) {
                sections.add(section);
            }
        }

        List<SourceSection> getSections() {
            return sections;
        }
    }
}
