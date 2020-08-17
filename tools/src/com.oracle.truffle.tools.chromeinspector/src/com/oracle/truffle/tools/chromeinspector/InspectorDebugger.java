/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Phaser;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import com.oracle.truffle.tools.utils.json.JSONArray;
import com.oracle.truffle.tools.utils.json.JSONObject;

import com.oracle.truffle.api.debug.Breakpoint;
import com.oracle.truffle.api.debug.DebugException;
import com.oracle.truffle.api.debug.DebugScope;
import com.oracle.truffle.api.debug.DebugStackFrame;
import com.oracle.truffle.api.debug.DebugStackTraceElement;
import com.oracle.truffle.api.debug.DebugValue;
import com.oracle.truffle.api.debug.Debugger;
import com.oracle.truffle.api.debug.DebuggerSession;
import com.oracle.truffle.api.debug.SourceElement;
import com.oracle.truffle.api.debug.StepConfig;
import com.oracle.truffle.api.debug.SuspendAnchor;
import com.oracle.truffle.api.debug.SuspendedCallback;
import com.oracle.truffle.api.debug.SuspendedEvent;
import com.oracle.truffle.api.debug.SuspensionFilter;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

import com.oracle.truffle.tools.chromeinspector.InspectorExecutionContext.CancellableRunnable;
import com.oracle.truffle.tools.chromeinspector.InspectorExecutionContext.NoSuspendedThreadException;
import com.oracle.truffle.tools.chromeinspector.InspectorExecutionContext.SuspendedThreadExecutor;
import com.oracle.truffle.tools.chromeinspector.ScriptsHandler.LoadScriptListener;
import com.oracle.truffle.tools.chromeinspector.commands.Params;
import com.oracle.truffle.tools.chromeinspector.commands.Result;
import com.oracle.truffle.tools.chromeinspector.domains.DebuggerDomain;
import com.oracle.truffle.tools.chromeinspector.events.Event;
import com.oracle.truffle.tools.chromeinspector.server.CommandProcessException;
import com.oracle.truffle.tools.chromeinspector.server.InspectServerSession.CommandPostProcessor;
import com.oracle.truffle.tools.chromeinspector.types.CallArgument;
import com.oracle.truffle.tools.chromeinspector.types.CallFrame;
import com.oracle.truffle.tools.chromeinspector.types.ExceptionDetails;
import com.oracle.truffle.tools.chromeinspector.types.Location;
import com.oracle.truffle.tools.chromeinspector.types.RemoteObject;
import com.oracle.truffle.tools.chromeinspector.types.Scope;
import com.oracle.truffle.tools.chromeinspector.types.Script;
import com.oracle.truffle.tools.chromeinspector.types.StackTrace;
import com.oracle.truffle.tools.chromeinspector.util.LineSearch;

import org.graalvm.collections.Pair;

public final class InspectorDebugger extends DebuggerDomain {

    private static final StepConfig STEP_CONFIG = StepConfig.newBuilder().suspendAnchors(SourceElement.ROOT, SuspendAnchor.AFTER).build();

    // Generic matcher of completion function
    // (function(x){var a=[];for(var o=x;o!==null&&typeof o !== 'undefined';o=o.__proto__){
    // a.push(Object.getOwnPropertyNames(o))
    // };return a})(obj)
    private static final Pattern FUNCTION_COMPLETION_PATTERN = Pattern.compile(
                    "\\(function\\s*\\((?<x>\\w+)\\)\\s*\\{\\s*var\\s+(?<a>\\w+)\\s*=\\s*\\[\\];\\s*" +
                                    "for\\s*\\(var\\s+(?<o>\\w+)\\s*=\\s*\\k<x>;\\s*\\k<o>\\s*\\!==\\s*null\\s*&&\\s*typeof\\s+\\k<o>\\s*\\!==\\s*.undefined.;\\k<o>\\s*=\\s*\\k<o>\\.__proto__\\)\\s*\\{" +
                                    "\\s*\\k<a>\\.push\\(Object\\.getOwnPropertyNames\\(\\k<o>\\)\\)" +
                                    "\\};\\s*return\\s+\\k<a>\\}\\)\\((?<object>.*)\\)$");

    private final InspectorExecutionContext context;
    private final Object suspendLock = new Object();
    private volatile SuspendedCallbackImpl suspendedCallback;
    private volatile DebuggerSession debuggerSession;
    private volatile ScriptsHandler scriptsHandler;
    private volatile BreakpointsHandler breakpointsHandler;
    // private Scope globalScope;
    private volatile DebuggerSuspendedInfo suspendedInfo; // Set when suspended
    private boolean running = true;
    private boolean runningUnwind = false;
    private boolean silentResume = false;
    private volatile CommandLazyResponse commandLazyResponse;
    private final AtomicBoolean delayUnlock = new AtomicBoolean();
    private final Phaser onSuspendPhaser = new Phaser();
    private final BlockingQueue<CancellableRunnable> suspendThreadExecutables = new LinkedBlockingQueue<>();
    private final ReadWriteLock domainLock;

    public InspectorDebugger(InspectorExecutionContext context, boolean suspend, ReadWriteLock domainLock) {
        this.context = context;
        this.domainLock = domainLock;
        context.setSuspendThreadExecutor(new SuspendedThreadExecutor() {
            @Override
            public void execute(CancellableRunnable executable) throws NoSuspendedThreadException {
                try {
                    synchronized (suspendLock) {
                        if (running) {
                            NoSuspendedThreadException.raise();
                        }
                        suspendThreadExecutables.put(executable);
                        suspendLock.notifyAll();
                    }
                } catch (InterruptedException iex) {
                    throw new RuntimeException(iex);
                }
            }
        });
        if (suspend) {
            Lock lock = domainLock.writeLock();
            lock.lock();
            try {
                startSession();
            } finally {
                lock.unlock();
            }
            debuggerSession.suspendNextExecution();
        }
    }

    private void startSession() {
        Debugger tdbg = context.getEnv().lookup(context.getEnv().getInstruments().get("debugger"), Debugger.class);
        suspendedCallback = new SuspendedCallbackImpl();
        debuggerSession = tdbg.startSession(suspendedCallback, SourceElement.ROOT, SourceElement.STATEMENT);
        debuggerSession.setSourcePath(context.getSourcePath());
        debuggerSession.setSteppingFilter(SuspensionFilter.newBuilder().ignoreLanguageContextInitialization(!context.isInspectInitialization()).includeInternal(context.isInspectInternal()).build());
        scriptsHandler = context.acquireScriptsHandler();
        scriptsHandler.setDebuggerSession(debuggerSession);
        breakpointsHandler = new BreakpointsHandler(debuggerSession, scriptsHandler, () -> eventHandler);
    }

    @Override
    public void doEnable() {
        if (debuggerSession == null) {
            startSession();
        }
        scriptsHandler.addLoadScriptListener(new LoadScriptListenerImpl());
    }

    @Override
    public void doDisable() {
        assert debuggerSession != null;
        scriptsHandler.setDebuggerSession(null);
        debuggerSession.close();
        debuggerSession = null;
        suspendedCallback.dispose();
        suspendedCallback = null;
        context.releaseScriptsHandler();
        scriptsHandler = null;
        breakpointsHandler = null;
        synchronized (suspendLock) {
            if (!running) {
                running = true;
                suspendLock.notifyAll();
            }
        }
    }

    @Override
    protected void notifyDisabled() {
        // We might call startSession() in the constructor, without doEnable().
        // That means that doDisable() might not have been called.
        if (debuggerSession != null) {
            doDisable();
        }
    }

    @Override
    public void setAsyncCallStackDepth(int maxDepth) throws CommandProcessException {
        if (maxDepth >= 0) {
            debuggerSession.setAsynchronousStackDepth(maxDepth);
        } else {
            throw new CommandProcessException("Invalid async call stack depth: " + maxDepth);
        }
    }

    @Override
    public void setBlackboxPatterns(String[] patterns) {
        final Pattern[] compiledPatterns = new Pattern[patterns.length];
        for (int i = 0; i < patterns.length; i++) {
            compiledPatterns[i] = Pattern.compile(patterns[i]);
        }
        debuggerSession.setSteppingFilter(SuspensionFilter.newBuilder().ignoreLanguageContextInitialization(!context.isInspectInitialization()).includeInternal(context.isInspectInternal()).sourceIs(
                        source -> !sourceMatchesBlackboxPatterns(source, compiledPatterns)).build());
    }

    @Override
    public void setPauseOnExceptions(String state) throws CommandProcessException {
        switch (state) {
            case "none":
                breakpointsHandler.setExceptionBreakpoint(false, false);
                break;
            case "uncaught":
                breakpointsHandler.setExceptionBreakpoint(false, true);
                break;
            case "all":
                breakpointsHandler.setExceptionBreakpoint(true, true);
                break;
            default:
                throw new CommandProcessException("Unknown Pause on exceptions mode: " + state);
        }

    }

    @Override
    public Params getPossibleBreakpoints(Location start, Location end, boolean restrictToFunction) throws CommandProcessException {
        if (start == null) {
            throw new CommandProcessException("Start location required.");
        }
        int scriptId = start.getScriptId();
        if (end != null && scriptId != end.getScriptId()) {
            throw new CommandProcessException("Different location scripts: " + scriptId + ", " + end.getScriptId());
        }
        Script script = scriptsHandler.getScript(scriptId);
        if (script == null) {
            throw new CommandProcessException("Unknown scriptId: " + scriptId);
        }
        JSONObject json = new JSONObject();
        JSONArray arr = new JSONArray();
        Source source = script.getSource();
        if (source.hasCharacters() && source.getLength() > 0) {
            int lc = source.getLineCount();
            int l1 = start.getLine();
            int c1 = start.getColumn();
            if (c1 <= 0) {
                c1 = 1;
            }
            if (l1 > lc) {
                l1 = lc;
                c1 = source.getLineLength(l1);
            }
            int l2;
            int c2;
            if (end != null) {
                l2 = end.getLine();
                c2 = end.getColumn();
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
            Iterable<SourceSection> locations = SuspendableLocationFinder.findSuspendableLocations(range, restrictToFunction, debuggerSession, context.getEnv());
            for (SourceSection ss : locations) {
                arr.put(new Location(scriptId, ss.getStartLine(), ss.getStartColumn()).toJSON());
            }
        }
        json.put("locations", arr);
        return new Params(json);
    }

    @Override
    public Params getScriptSource(String scriptId) throws CommandProcessException {
        if (scriptId == null) {
            throw new CommandProcessException("A scriptId required.");
        }
        CharSequence characters = getScript(scriptId).getCharacters();
        JSONObject json = new JSONObject();
        json.put("scriptSource", characters.toString());
        return new Params(json);
    }

    private Script getScript(String scriptId) throws CommandProcessException {
        Script script;
        try {
            script = scriptsHandler.getScript(Integer.parseInt(scriptId));
            if (script == null) {
                throw new CommandProcessException("Unknown scriptId: " + scriptId);
            }
        } catch (NumberFormatException nfe) {
            throw new CommandProcessException(nfe.getMessage());
        }
        return script;
    }

    @Override
    public void pause() {
        DebuggerSuspendedInfo susp = suspendedInfo;
        if (susp == null) {
            debuggerSession.suspendNextExecution();
        }
    }

    @Override
    public void resume(CommandPostProcessor postProcessor) {
        DebuggerSuspendedInfo susp = suspendedInfo;
        if (susp != null) {
            postProcessor.setPostProcessJob(() -> doResume());
        }
    }

    @Override
    public void stepInto(CommandPostProcessor postProcessor) {
        DebuggerSuspendedInfo susp = suspendedInfo;
        if (susp != null) {
            susp.getSuspendedEvent().prepareStepInto(STEP_CONFIG);
            delayUnlock.set(true);
            postProcessor.setPostProcessJob(() -> doResume());
        }
    }

    @Override
    public void stepOver(CommandPostProcessor postProcessor) {
        DebuggerSuspendedInfo susp = suspendedInfo;
        if (susp != null) {
            susp.getSuspendedEvent().prepareStepOver(STEP_CONFIG);
            delayUnlock.set(true);
            postProcessor.setPostProcessJob(() -> doResume());
        }
    }

    @Override
    public void stepOut(CommandPostProcessor postProcessor) {
        DebuggerSuspendedInfo susp = suspendedInfo;
        if (susp != null) {
            susp.getSuspendedEvent().prepareStepOut(STEP_CONFIG);
            delayUnlock.set(true);
            postProcessor.setPostProcessJob(() -> doResume());
        }
    }

    private void doResume() {
        synchronized (suspendLock) {
            if (!running) {
                running = true;
                suspendLock.notifyAll();
            }
        }
        // Wait for onSuspend() to finish
        try {
            onSuspendPhaser.awaitAdvanceInterruptibly(0);
        } catch (InterruptedException ex) {
        }
    }

    private CallFrame[] createCallFrames(Iterable<DebugStackFrame> frames, SuspendAnchor topAnchor, DebugValue returnValue) {
        return createCallFrames(frames, topAnchor, returnValue, null);
    }

    CallFrame[] refreshCallFrames(Iterable<DebugStackFrame> frames, SuspendAnchor topAnchor, CallFrame[] oldFrames) {
        DebugValue returnValue = null;
        if (oldFrames.length > 0 && oldFrames[0].getReturnValue() != null) {
            returnValue = oldFrames[0].getReturnValue().getDebugValue();
        }
        return createCallFrames(frames, topAnchor, returnValue, oldFrames);
    }

    private CallFrame[] createCallFrames(Iterable<DebugStackFrame> frames, SuspendAnchor topAnchor, DebugValue returnValue, CallFrame[] oldFrames) {
        List<CallFrame> cfs = new ArrayList<>();
        int depth = 0;
        int depthAll = -1;
        if (scriptsHandler == null || debuggerSession == null) {
            return new CallFrame[0];
        }
        for (DebugStackFrame frame : frames) {
            depthAll++;
            SourceSection sourceSection = frame.getSourceSection();
            if (sourceSection == null || !sourceSection.isAvailable()) {
                continue;
            }
            if (!context.isInspectInternal() && frame.isInternal()) {
                continue;
            }
            Source source = sourceSection.getSource();
            if (!context.isInspectInternal() && source.isInternal()) {
                // should not be, double-check
                continue;
            }
            int scriptId = scriptsHandler.assureLoaded(source);
            if (scriptId < 0) {
                continue;
            }
            Script script = scriptsHandler.getScript(scriptId);
            List<Scope> scopes = new ArrayList<>();
            DebugScope dscope;
            try {
                dscope = frame.getScope();
            } catch (DebugException ex) {
                PrintWriter err = context.getErr();
                if (err != null) {
                    err.println("getScope() has caused " + ex);
                    ex.printStackTrace(err);
                }
                dscope = null;
            }
            String scopeType = "block";
            boolean wasFunction = false;
            SourceSection functionSourceSection = null;
            DebugValue thisValue = null;
            if (dscope == null) {
                functionSourceSection = sourceSection;
            }
            Scope[] oldScopes;
            if (oldFrames != null && oldFrames.length > depth) {
                oldScopes = oldFrames[depth].getScopeChain();
            } else {
                oldScopes = null;
            }
            int scopeIndex = 0;  // index of language implementation scope
            while (dscope != null) {
                if (wasFunction) {
                    scopeType = "closure";
                } else if (dscope.isFunctionScope()) {
                    scopeType = "local";
                    functionSourceSection = dscope.getSourceSection();
                    thisValue = dscope.getReceiver();
                    wasFunction = true;
                }
                addScope(scopes, dscope, scopeType, scopeIndex, oldScopes);
                dscope = getParent(dscope);
                scopeIndex++;
            }
            try {
                dscope = debuggerSession.getTopScope(source.getLanguage());
            } catch (DebugException ex) {
                PrintWriter err = context.getErr();
                if (err != null) {
                    err.println("getTopScope() has caused " + ex);
                    ex.printStackTrace(err);
                }
            }
            while (dscope != null) {
                addScope(scopes, dscope, "global", scopeIndex, oldScopes);
                dscope = getParent(dscope);
                scopeIndex++;
            }
            RemoteObject returnObj = null;
            if (depthAll == 0 && returnValue != null) {
                returnObj = context.getRemoteObjectsHandler().getRemote(returnValue);
            }
            SuspendAnchor anchor = (depthAll == 0) ? topAnchor : SuspendAnchor.BEFORE;
            RemoteObject thisObj;
            if (thisValue != null) {
                thisObj = context.getRemoteObjectsHandler().getRemote(thisValue);
            } else {
                thisObj = RemoteObject.createNullObject(context.getEnv(), frame.getLanguage());
            }
            CallFrame cf = new CallFrame(frame, depth++, script, sourceSection, anchor, functionSourceSection,
                            thisObj, returnObj, scopes.toArray(new Scope[scopes.size()]));
            cfs.add(cf);
        }
        return cfs.toArray(new CallFrame[cfs.size()]);
    }

    private void addScope(List<Scope> scopes, DebugScope dscope, String scopeType, int scopeIndex, Scope[] oldScopes) {
        if (dscope.isFunctionScope() || dscope.getDeclaredValues().iterator().hasNext()) {
            // provide only scopes that have some variables
            String lastId = getLastScopeId(oldScopes, scopeIndex);
            // Create the new scope with the ID of the old one to refresh the content
            scopes.add(createScope(scopeType, dscope, scopeIndex, lastId));
        }
    }

    private static String getLastScopeId(Scope[] oldScopes, int scopeIndex) {
        if (oldScopes != null) {
            for (Scope scope : oldScopes) {
                if (scope.getInternalIndex() == scopeIndex) {
                    return scope.getObject().getId();
                }
            }
        }
        return null;
    }

    private Scope createScope(String scopeType, DebugScope dscope, int index, String lastId) {
        RemoteObject scopeVars = new RemoteObject(dscope, lastId);
        context.getRemoteObjectsHandler().register(scopeVars);
        return new Scope(scopeType, scopeVars, dscope.getName(), null, null, index);
    }

    private DebugScope getParent(DebugScope dscope) {
        DebugScope parentScope;
        try {
            parentScope = dscope.getParent();
        } catch (DebugException ex) {
            PrintWriter err = context.getErr();
            if (err != null) {
                err.println("Scope.getParent() has caused " + ex);
                ex.printStackTrace(err);
            }
            parentScope = null;
        }
        return parentScope;
    }

    @Override
    public Params searchInContent(String scriptId, String query, boolean caseSensitive, boolean isRegex) throws CommandProcessException {
        if (scriptId.isEmpty() || query.isEmpty()) {
            throw new CommandProcessException("Must specify both scriptId and query.");
        }
        Source source = getScript(scriptId).getSource();
        JSONArray matchLines;
        try {
            matchLines = LineSearch.matchLines(source, query, caseSensitive, isRegex);
        } catch (PatternSyntaxException ex) {
            throw new CommandProcessException(ex.getDescription());
        }
        JSONObject match = new JSONObject();
        match.put("properties", matchLines);
        return new Params(match);
    }

    @Override
    public void setBreakpointsActive(Optional<Boolean> active) throws CommandProcessException {
        if (!active.isPresent()) {
            throw new CommandProcessException("Must specify active argument.");
        }
        debuggerSession.setBreakpointsActive(Breakpoint.Kind.SOURCE_LOCATION, active.get());
    }

    @Override
    public void setSkipAllPauses(Optional<Boolean> skip) throws CommandProcessException {
        if (!skip.isPresent()) {
            throw new CommandProcessException("Must specify 'skip' argument.");
        }
        boolean active = !skip.get();
        for (Breakpoint.Kind kind : Breakpoint.Kind.values()) {
            debuggerSession.setBreakpointsActive(kind, active);
        }
    }

    @Override
    public Params setBreakpointByUrl(String url, String urlRegex, int line, int column, String condition) throws CommandProcessException {
        if (url.isEmpty() && urlRegex.isEmpty()) {
            throw new CommandProcessException("Must specify either url or urlRegex.");
        }
        if (line <= 0) {
            throw new CommandProcessException("Must specify line number.");
        }
        if (!url.isEmpty()) {
            return breakpointsHandler.createURLBreakpoint(url, line, column, condition);
        } else {
            return breakpointsHandler.createURLBreakpoint(Pattern.compile(urlRegex), line, column, condition);
        }
    }

    @Override
    public Params setBreakpoint(Location location, String condition) throws CommandProcessException {
        if (location == null) {
            throw new CommandProcessException("Must specify location.");
        }
        return breakpointsHandler.createBreakpoint(location, condition);
    }

    @Override
    public Params setBreakpointOnFunctionCall(String functionObjectId, String condition) throws CommandProcessException {
        if (functionObjectId == null) {
            throw new CommandProcessException("Must specify function object ID.");
        }
        RemoteObject functionObject = context.getRemoteObjectsHandler().getRemote(functionObjectId);
        if (functionObject != null) {
            DebugValue functionValue = functionObject.getDebugValue();
            try {
                return context.executeInSuspendThread(new SuspendThreadExecutable<Params>() {
                    @Override
                    public Params executeCommand() throws CommandProcessException {
                        return breakpointsHandler.createFunctionBreakpoint(functionValue, condition);
                    }

                    @Override
                    public Params processException(DebugException dex) {
                        return new Params(new JSONObject());
                    }
                });
            } catch (NoSuspendedThreadException e) {
                return new Params(new JSONObject());
            }
        } else {
            throw new CommandProcessException("Function with object ID " + functionObjectId + " does not exist.");
        }
    }

    @Override
    public void removeBreakpoint(String id) throws CommandProcessException {
        if (!breakpointsHandler.removeBreakpoint(id)) {
            throw new CommandProcessException("No breakpoint with id '" + id + "'");
        }
    }

    @Override
    public void continueToLocation(Location location, CommandPostProcessor postProcessor) throws CommandProcessException {
        if (location == null) {
            throw new CommandProcessException("Must specify location.");
        }
        breakpointsHandler.createOneShotBreakpoint(location);
        resume(postProcessor);
    }

    static String getEvalNonInteractiveMessage() {
        return "<Can not evaluate in a non-interactive language>";
    }

    @Override
    public Params evaluateOnCallFrame(String callFrameId, String expressionOrig, String objectGroup,
                    boolean includeCommandLineAPI, boolean silent, boolean returnByValue,
                    boolean generatePreview, boolean throwOnSideEffect) throws CommandProcessException {
        if (callFrameId == null) {
            throw new CommandProcessException("A callFrameId required.");
        }
        if (expressionOrig == null) {
            throw new CommandProcessException("An expression required.");
        }
        int frameId;
        try {
            frameId = Integer.parseInt(callFrameId);
        } catch (NumberFormatException ex) {
            throw new CommandProcessException(ex.getLocalizedMessage());
        }
        ConsoleUtilitiesAPI cuAPI;
        if (includeCommandLineAPI) {
            cuAPI = ConsoleUtilitiesAPI.parse(expressionOrig);
        } else {
            cuAPI = null;
        }
        final String expression;
        if (cuAPI != null) {
            expression = cuAPI.getExpression();
        } else {
            expression = expressionOrig;
        }
        JSONObject jsonResult;
        try {
            jsonResult = context.executeInSuspendThread(new SuspendThreadExecutable<JSONObject>() {
                @Override
                public JSONObject executeCommand() throws CommandProcessException {
                    if (frameId >= suspendedInfo.getCallFrames().length) {
                        throw new CommandProcessException("Too big callFrameId: " + frameId);
                    }
                    CallFrame cf = suspendedInfo.getCallFrames()[frameId];
                    JSONObject json = new JSONObject();
                    if (runSpecialFunctions(expression, cf, generatePreview, json)) {
                        return json;
                    }
                    DebugValue value = getVarValue(expression, cf);
                    if (value == null) {
                        try {
                            value = cf.getFrame().eval(expression);
                            suspendedInfo.refreshFrames();
                        } catch (IllegalStateException ex) {
                            // Not an interactive language
                        }
                    }
                    if (value == null) {
                        LanguageInfo languageInfo = cf.getFrame().getLanguage();
                        if (languageInfo == null || !languageInfo.isInteractive()) {
                            String errorMessage = getEvalNonInteractiveMessage();
                            ExceptionDetails exceptionDetails = new ExceptionDetails(errorMessage);
                            json.put("exceptionDetails", exceptionDetails.createJSON(context, generatePreview));
                            JSONObject err = new JSONObject();
                            err.putOpt("value", errorMessage);
                            err.putOpt("type", "string");
                            json.put("result", err);
                        }
                    }
                    if (value != null) {
                        if (cuAPI != null) {
                            value = cuAPI.process(value, breakpointsHandler);
                            if (value == null) {
                                return json;
                            }
                        }
                        RemoteObject ro = new RemoteObject(value, generatePreview, context);
                        context.getRemoteObjectsHandler().register(ro, objectGroup);
                        json.put("result", ro.toJSON());
                    }
                    return json;
                }

                @Override
                public JSONObject processException(DebugException dex) {
                    JSONObject json = new JSONObject();
                    InspectorRuntime.fillExceptionDetails(json, dex, context, generatePreview);
                    DebugValue exceptionObject = dex.getExceptionObject();
                    if (exceptionObject != null) {
                        RemoteObject ro = context.createAndRegister(exceptionObject, generatePreview);
                        json.put("result", ro.toJSON());
                    } else {
                        JSONObject err = new JSONObject();
                        err.putOpt("value", dex.getLocalizedMessage());
                        err.putOpt("type", "string");
                        json.put("result", err);
                    }
                    return json;
                }
            });
        } catch (NoSuspendedThreadException e) {
            jsonResult = new JSONObject();
            JSONObject err = new JSONObject();
            err.putOpt("value", e.getLocalizedMessage());
            jsonResult.put("result", err);
        }
        return new Params(jsonResult);
    }

    private boolean runSpecialFunctions(String expression, CallFrame cf, boolean generatePreview, JSONObject json) {
        // Test whether code-completion on an object was requested:
        Matcher completionMatcher = FUNCTION_COMPLETION_PATTERN.matcher(expression);
        if (completionMatcher.matches()) {
            String objectOfCompletion = completionMatcher.group("object");
            DebugValue value = getVarValue(objectOfCompletion, cf);
            if (value == null) {
                try {
                    value = cf.getFrame().eval(objectOfCompletion);
                } catch (IllegalStateException ex) {
                    // Not an interactive language
                }
            }
            if (value != null) {
                JSONObject result = InspectorRuntime.createCodecompletion(value, null, generatePreview, context, false);
                json.put("result", result);
                return true;
            }
        }
        return false;
    }

    /** Get value of variable "name", if any. */
    static DebugValue getVarValue(String name, CallFrame cf) {
        for (Scope scope : cf.getScopeChain()) {
            DebugScope debugScope = scope.getObject().getScope();
            DebugValue var = debugScope.getDeclaredValue(name);
            if (var != null) {
                return var;
            }
            DebugValue receiver = debugScope.getReceiver();
            if (receiver != null && name.equals(receiver.getName())) {
                return receiver;
            }
        }
        return null;
    }

    @Override
    public Params restartFrame(long cmdId, String callFrameId, CommandPostProcessor postProcessor) throws CommandProcessException {
        if (callFrameId == null) {
            throw new CommandProcessException("A callFrameId required.");
        }
        int frameId;
        try {
            frameId = Integer.parseInt(callFrameId);
        } catch (NumberFormatException ex) {
            throw new CommandProcessException(ex.getLocalizedMessage());
        }
        DebuggerSuspendedInfo susp = suspendedInfo;
        if (susp != null) {
            if (frameId >= susp.getCallFrames().length) {
                throw new CommandProcessException("Too big callFrameId: " + frameId);
            }
            CallFrame cf = susp.getCallFrames()[frameId];
            susp.getSuspendedEvent().prepareUnwindFrame(cf.getFrame());
            postProcessor.setPostProcessJob(() -> {
                silentResume = true;
                commandLazyResponse = (DebuggerSuspendedInfo suspInfo) -> {
                    JSONObject res = new JSONObject();
                    res.put("callFrames", getFramesParam(suspInfo.getCallFrames()));
                    return new Event(cmdId, new Result(new Params(res)));
                };
                runningUnwind = true;
                doResume();
            });
        }
        return new Params(null);
    }

    @Override
    public void setVariableValue(int scopeNumber, String variableName, CallArgument newValue, String callFrameId) throws CommandProcessException {
        if (variableName == null) {
            throw new CommandProcessException("A variableName required.");
        }
        if (newValue == null) {
            throw new CommandProcessException("A newValue required.");
        }
        if (callFrameId == null) {
            throw new CommandProcessException("A callFrameId required.");
        }
        int frameId;
        try {
            frameId = Integer.parseInt(callFrameId);
        } catch (NumberFormatException ex) {
            throw new CommandProcessException(ex.getLocalizedMessage());
        }
        try {
            context.executeInSuspendThread(new SuspendThreadExecutable<Void>() {
                @Override
                public Void executeCommand() throws CommandProcessException {
                    DebuggerSuspendedInfo susp = suspendedInfo;
                    if (susp != null) {
                        if (frameId >= susp.getCallFrames().length) {
                            throw new CommandProcessException("Too big callFrameId: " + frameId);
                        }
                        CallFrame cf = susp.getCallFrames()[frameId];
                        Scope[] scopeChain = cf.getScopeChain();
                        if (scopeNumber < 0 || scopeNumber >= scopeChain.length) {
                            throw new CommandProcessException("Wrong scopeNumber: " + scopeNumber + ", there are " + scopeChain.length + " scopes.");
                        }
                        Scope scope = scopeChain[scopeNumber];
                        DebugScope debugScope = scope.getObject().getScope();
                        DebugValue debugValue = debugScope.getDeclaredValue(variableName);
                        Pair<DebugValue, Object> evaluatedValue = susp.lastEvaluatedValue.getAndSet(null);
                        try {
                            if (evaluatedValue != null && Objects.equals(evaluatedValue.getRight(), newValue.getPrimitiveValue())) {
                                debugValue.set(evaluatedValue.getLeft());
                            } else {
                                context.setValue(debugValue, newValue);
                            }
                        } catch (DebugException ex) {
                            PrintWriter err = context.getErr();
                            if (err != null) {
                                err.println("set of " + debugValue.getName() + " has caused " + ex);
                                ex.printStackTrace(err);
                            }
                            throw ex;
                        }
                    }
                    return null;
                }

                @Override
                public Void processException(DebugException dex) {
                    return null;
                }
            });
        } catch (NoSuspendedThreadException ex) {
            throw new CommandProcessException(ex.getLocalizedMessage());
        }
    }

    @Override
    public void setReturnValue(CallArgument newValue) throws CommandProcessException {
        if (newValue == null) {
            throw new CommandProcessException("A newValue required.");
        }
        try {
            context.executeInSuspendThread(new SuspendThreadExecutable<Void>() {
                @Override
                public Void executeCommand() throws CommandProcessException {
                    DebuggerSuspendedInfo susp = suspendedInfo;
                    if (susp != null) {
                        SuspendedEvent suspendedEvent = susp.getSuspendedEvent();
                        DebugValue returnValue = suspendedEvent.getReturnValue();
                        context.setValue(returnValue, newValue);
                        susp.getSuspendedEvent().setReturnValue(returnValue);
                    }
                    return null;
                }

                @Override
                public Void processException(DebugException dex) {
                    return null;
                }
            });
        } catch (NoSuspendedThreadException ex) {
            throw new CommandProcessException(ex.getLocalizedMessage());
        }
    }

    public boolean sourceMatchesBlackboxPatterns(Source source, Pattern[] patterns) {
        String uri = scriptsHandler.getSourceURL(source);
        for (Pattern pattern : patterns) {
            // Check whether pattern corresponds to:
            // 1) the name of a file
            if (pattern.pattern().equals(source.getName())) {
                return true;
            }
            // 2) regular expression to target
            Matcher matcher = pattern.matcher(uri);
            if (matcher.matches() || pattern.pattern().endsWith("$") && matcher.find()) {
                return true;
            }
            // 3) an entire folder that contains scripts to blackbox
            String path = source.getPath();
            int idx = path != null ? path.lastIndexOf(File.separatorChar) : -1;
            if (idx > 0) {
                path = path.substring(0, idx);
                if (path.endsWith(File.separator + pattern.pattern())) {
                    return true;
                }
            }
        }
        return false;
    }

    private class LoadScriptListenerImpl implements LoadScriptListener {

        @Override
        public void loadedScript(Script script) {
            JSONObject jsonParams = new JSONObject();
            jsonParams.put("scriptId", Integer.toString(script.getId()));
            jsonParams.put("url", script.getUrl());
            jsonParams.put("startLine", 0);
            jsonParams.put("startColumn", 0);
            Source source = script.getSource();
            int lastLine;
            int lastColumn;
            int length;
            if (source.hasCharacters()) {
                lastLine = source.getLineCount() - 1;
                if (lastLine < 0) {
                    lastLine = 0;
                    lastColumn = 0;
                } else {
                    lastColumn = source.getLineLength(lastLine + 1);
                    int srcMapLine = lastLine + 1;
                    CharSequence line;
                    do {
                        line = source.getCharacters(srcMapLine);
                        srcMapLine--;
                        // Node.js wraps source into a function,
                        // skip empty lines and end of a function.
                    } while (srcMapLine > 0 && (line.length() == 0 || "});".equals(line)));
                    CharSequence sourceMapURL = (srcMapLine > 0) ? getSourceMapURL(source, srcMapLine) : null;
                    if (sourceMapURL != null) {
                        jsonParams.put("sourceMapURL", sourceMapURL);
                        lastLine = srcMapLine - 1;
                        lastColumn = source.getLineLength(lastLine + 1);
                    }
                }
                length = source.getLength();
            } else {
                lastLine = 3;
                lastColumn = 0;
                length = script.getCharacters().length();
            }
            jsonParams.put("endLine", lastLine);
            jsonParams.put("endColumn", lastColumn);
            jsonParams.put("executionContextId", context.getId());
            jsonParams.put("hash", script.getHash());
            jsonParams.put("length", length);
            Params params = new Params(jsonParams);
            Event scriptParsed = new Event("Debugger.scriptParsed", params);
            eventHandler.event(scriptParsed);
        }

        private CharSequence getSourceMapURL(Source source, int lastLine) {
            String mapKeyword = "sourceMappingURL=";
            int mapKeywordLenght = mapKeyword.length();
            CharSequence line = source.getCharacters(lastLine + 1);
            int lineLength = line.length();
            int i = 0;
            while (i < lineLength && Character.isWhitespace(line.charAt(i))) {
                i++;
            }
            if (i + 3 < lineLength && line.charAt(i) == '/' && line.charAt(i + 1) == '/' &&
                            (line.charAt(i + 2) == '#' || line.charAt(i + 2) == '@')) {
                i += 3;
            } else {
                return null;
            }
            while (i < lineLength && Character.isWhitespace(line.charAt(i))) {
                i++;
            }
            if (i + mapKeywordLenght < lineLength && line.subSequence(i, i + mapKeywordLenght).equals(mapKeyword)) {
                i += mapKeywordLenght;
            } else {
                return null;
            }
            return line.subSequence(i, line.length());
        }

    }

    private class SuspendedCallbackImpl implements SuspendedCallback {

        private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1, new SchedulerThreadFactory());
        private final AtomicReference<ScheduledFuture<?>> future = new AtomicReference<>();
        private Thread locked = null;

        @Override
        public void onSuspend(SuspendedEvent se) {
            try {
                context.waitForRunPermission();
            } catch (InterruptedException ex) {
            }
            SourceSection ss = se.getSourceSection();
            lock();
            onSuspendPhaser.register();
            try {
                Event paused;
                Lock lock = domainLock.readLock();
                lock.lock();
                try {
                    if (debuggerSession == null) {
                        // Debugger has been disabled while waiting on locks
                        return;
                    }
                    if (se.hasSourceElement(SourceElement.ROOT) && !se.hasSourceElement(SourceElement.STATEMENT) && se.getSuspendAnchor() == SuspendAnchor.BEFORE && se.getBreakpoints().isEmpty()) {
                        // Suspend requested and we're at the begining of a ROOT.
                        debuggerSession.suspendNextExecution();
                        return;
                    }
                    synchronized (suspendLock) {
                        running = false;
                    }
                    if (!runningUnwind) {
                        scriptsHandler.assureLoaded(ss.getSource());
                        context.setLastLanguage(ss.getSource().getLanguage(), ss.getSource().getMimeType());
                    } else {
                        runningUnwind = false;
                    }
                    JSONObject jsonParams = new JSONObject();
                    DebugValue returnValue = se.getReturnValue();
                    if (!se.hasSourceElement(SourceElement.ROOT)) {
                        // It is misleading to see return values on call exit,
                        // when we show it at function exit
                        returnValue = null;
                    }
                    CallFrame[] callFrames = createCallFrames(se.getStackFrames(), se.getSuspendAnchor(), returnValue);
                    suspendedInfo = new DebuggerSuspendedInfo(InspectorDebugger.this, se, callFrames);
                    context.setSuspendedInfo(suspendedInfo);
                    if (commandLazyResponse != null) {
                        paused = commandLazyResponse.getResponse(suspendedInfo);
                        commandLazyResponse = null;
                    } else {
                        jsonParams.put("callFrames", getFramesParam(callFrames));
                        jsonParams.putOpt("asyncStackTrace", findAsyncStackTrace(se.getAsynchronousStacks()));
                        List<Breakpoint> breakpoints = se.getBreakpoints();
                        JSONArray bpArr = new JSONArray();
                        Set<Breakpoint.Kind> kinds = new HashSet<>(1);
                        for (Breakpoint bp : breakpoints) {
                            String id = breakpointsHandler.getId(bp);
                            if (id != null) {
                                bpArr.put(id);
                            }
                            kinds.add(bp.getKind());
                        }
                        jsonParams.put("reason", getHaltReason(kinds));
                        JSONObject data = getHaltData(se);
                        if (data != null) {
                            jsonParams.put("data", data);
                        }
                        jsonParams.put("hitBreakpoints", bpArr);

                        Params params = new Params(jsonParams);
                        paused = new Event("Debugger.paused", params);
                    }
                } finally {
                    lock.unlock();
                }
                eventHandler.event(paused);
                List<CancellableRunnable> executables;
                for (;;) {
                    executables = null;
                    synchronized (suspendLock) {
                        if (!running && suspendThreadExecutables.isEmpty()) {
                            if (context.isSynchronous()) {
                                running = true;
                            } else {
                                try {
                                    suspendLock.wait();
                                } catch (InterruptedException ex) {
                                }
                            }
                        }
                        if (!suspendThreadExecutables.isEmpty()) {
                            executables = new LinkedList<>();
                            CancellableRunnable r;
                            while ((r = suspendThreadExecutables.poll()) != null) {
                                executables.add(r);
                            }
                        }
                        if (running) {
                            suspendedInfo = null;
                            context.setSuspendedInfo(null);
                            break;
                        }
                    }
                    if (executables != null) {
                        for (CancellableRunnable r : executables) {
                            r.run();
                        }
                        executables = null;
                    }
                }
                if (executables != null) {
                    for (CancellableRunnable r : executables) {
                        r.cancel();
                    }
                }
                if (!silentResume) {
                    Event resumed = new Event("Debugger.resumed", null);
                    eventHandler.event(resumed);
                } else {
                    silentResume = false;
                }
            } finally {
                onSuspendPhaser.arrive();
                if (delayUnlock.getAndSet(false)) {
                    future.set(scheduler.schedule(() -> {
                        unlock();
                    }, 1, TimeUnit.SECONDS));
                } else {
                    unlock();
                }
            }
        }

        private synchronized void lock() {
            Thread current = Thread.currentThread();
            if (locked != current) {
                while (locked != null) {
                    try {
                        wait();
                    } catch (InterruptedException ex) {
                    }
                }
                locked = current;
            } else {
                ScheduledFuture<?> sf = future.getAndSet(null);
                if (sf != null) {
                    sf.cancel(true);
                }
            }
        }

        private synchronized void unlock() {
            locked = null;
            notify();
        }

        private String getHaltReason(Set<Breakpoint.Kind> kinds) {
            if (kinds.size() > 1) {
                return "ambiguous";
            } else {
                if (kinds.contains(Breakpoint.Kind.HALT_INSTRUCTION)) {
                    return "debugCommand";
                } else if (kinds.contains(Breakpoint.Kind.EXCEPTION)) {
                    return "exception";
                } else {
                    return "other";
                }
            }
        }

        private JSONObject getHaltData(SuspendedEvent se) {
            DebugException exception = se.getException();
            if (exception == null) {
                return null;
            }
            boolean uncaught = exception.getCatchLocation() == null;
            DebugValue exceptionObject = exception.getExceptionObject();
            JSONObject data;
            if (exceptionObject != null) {
                RemoteObject remoteObject = context.createAndRegister(exceptionObject, false);
                data = remoteObject.toJSON();
            } else {
                data = new JSONObject();
            }
            data.put("uncaught", uncaught);
            return data;
        }

        private void dispose() {
            unlock();
            ScheduledFuture<?> sf = future.getAndSet(null);
            if (sf != null) {
                sf.cancel(true);
            }
            scheduler.shutdown();
            try {
                scheduler.awaitTermination(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
            }
        }
    }

    private static class SchedulerThreadFactory implements ThreadFactory {

        private final ThreadGroup group;

        SchedulerThreadFactory() {
            SecurityManager s = System.getSecurityManager();
            this.group = (s != null) ? s.getThreadGroup() : Thread.currentThread().getThreadGroup();
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(group, r, "Suspend Unlocking Scheduler");
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY);
            return t;
        }
    }

    private static JSONArray getFramesParam(CallFrame[] callFrames) {
        JSONArray array = new JSONArray();
        for (CallFrame cf : callFrames) {
            array.put(cf.toJSON());
        }
        return array;
    }

    private JSONObject findAsyncStackTrace(List<List<DebugStackTraceElement>> asyncStacks) {
        if (asyncStacks.isEmpty()) {
            return null;
        }
        StackTrace stackTrace = new StackTrace(context, asyncStacks);
        return stackTrace.toJSON();
    }

    private interface CommandLazyResponse {

        Event getResponse(DebuggerSuspendedInfo suspendedInfo);

    }
}
