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
package com.oracle.truffle.tools.chromeinspector.server;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.graalvm.polyglot.io.MessageEndpoint;

import com.oracle.truffle.tools.utils.json.JSONArray;
import com.oracle.truffle.tools.utils.json.JSONException;
import com.oracle.truffle.tools.utils.json.JSONObject;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.tools.chromeinspector.InspectorDebugger;
import com.oracle.truffle.tools.chromeinspector.InspectorExecutionContext;
import com.oracle.truffle.tools.chromeinspector.InspectorProfiler;
import com.oracle.truffle.tools.chromeinspector.InspectorRuntime;
import com.oracle.truffle.tools.chromeinspector.commands.Command;
import com.oracle.truffle.tools.chromeinspector.commands.ErrorResponse;
import com.oracle.truffle.tools.chromeinspector.commands.Params;
import com.oracle.truffle.tools.chromeinspector.commands.Result;
import com.oracle.truffle.tools.chromeinspector.domains.DebuggerDomain;
import com.oracle.truffle.tools.chromeinspector.domains.Domain;
import com.oracle.truffle.tools.chromeinspector.domains.ProfilerDomain;
import com.oracle.truffle.tools.chromeinspector.domains.RuntimeDomain;
import com.oracle.truffle.tools.chromeinspector.events.Event;
import com.oracle.truffle.tools.chromeinspector.events.EventHandler;
import com.oracle.truffle.tools.chromeinspector.types.CallArgument;
import com.oracle.truffle.tools.chromeinspector.types.Location;

public final class InspectServerSession implements MessageEndpoint {

    private final RuntimeDomain runtime;
    private final DebuggerDomain debugger;
    private final ProfilerDomain profiler;
    private final ReadWriteLock domainLock;
    final InspectorExecutionContext context;
    private volatile MessageEndpoint messageEndpoint;
    private volatile JSONMessageListener jsonMessageListener;
    private volatile CommandProcessThread processThread;
    private volatile Runnable onClose;

    private InspectServerSession(RuntimeDomain runtime, DebuggerDomain debugger, ProfilerDomain profiler,
                    InspectorExecutionContext context, ReadWriteLock domainLock) {
        this.runtime = runtime;
        this.debugger = debugger;
        this.profiler = profiler;
        this.context = context;
        this.domainLock = domainLock;
    }

    public static InspectServerSession create(InspectorExecutionContext context, boolean debugBreak, ConnectionWatcher connectionWatcher) {
        ReadWriteLock domainLock = new ReentrantReadWriteLock();
        RuntimeDomain runtime = new InspectorRuntime(context);
        DebuggerDomain debugger = new InspectorDebugger(context, debugBreak, domainLock);
        ProfilerDomain profiler = new InspectorProfiler(context, connectionWatcher);
        return new InspectServerSession(runtime, debugger, profiler, context, domainLock);
    }

    public void onClose(Runnable onCloseTask) {
        this.onClose = onCloseTask;
    }

    @Override
    public void sendClose() {
        dispose();
        Runnable onCloseRunnable = onClose;
        onClose = null;
        if (onCloseRunnable != null) {
            onCloseRunnable.run();
        }
    }

    // For tests only
    public DebuggerDomain getDebugger() {
        return debugger;
    }

    public void dispose() {
        Lock lock = domainLock.writeLock();
        lock.lock();
        try {
            runtime.disable();
            debugger.disable();
            profiler.disable();
        } finally {
            lock.unlock();
        }
        context.reset();
        CommandProcessThread cmdProcessThread;
        synchronized (this) {
            this.messageEndpoint = null;
            cmdProcessThread = processThread;
            if (cmdProcessThread != null) {
                cmdProcessThread.dispose();
                processThread = null;
            }
        }
        if (cmdProcessThread != null) {
            try {
                cmdProcessThread.join();
            } catch (InterruptedException e) {
            }
        }
    }

    public synchronized void setMessageListener(MessageEndpoint messageListener) {
        this.messageEndpoint = messageListener;
        if (messageListener != null && processThread == null) {
            EventHandler eh = new EventHandlerImpl();
            runtime.setEventHandler(eh);
            debugger.setEventHandler(eh);
            profiler.setEventHandler(eh);
            processThread = new CommandProcessThread();
            processThread.start();
        }
    }

    public synchronized void setJSONMessageListener(JSONMessageListener messageListener) {
        this.jsonMessageListener = messageListener;
        if (messageListener != null && processThread == null) {
            EventHandler eh = new EventHandlerImpl();
            runtime.setEventHandler(eh);
            debugger.setEventHandler(eh);
            profiler.setEventHandler(eh);
            processThread = new CommandProcessThread();
            processThread.start();
        }
    }

    @Override
    public void sendText(String message) {
        Command cmd;
        try {
            cmd = new Command(message);
        } catch (JSONException ex) {
            PrintWriter err = context.getErr();
            if (err != null) {
                err.println("Illegal message: '" + message + "' " + ex.getLocalizedMessage());
            }
            return;
        }
        CommandProcessThread pt = processThread;
        if (pt != null) {
            pt.push(cmd);
        }
    }

    public void sendCommand(Command cmd) {
        if (context.isSynchronous()) {
            sendCommandSync(cmd);
        } else {
            CommandProcessThread pt = processThread;
            if (pt != null) {
                pt.push(cmd);
            }
        }
    }

    private static boolean isDomainEnabledChange(String domainMethod) {
        switch (domainMethod) {
            case "enable":
            case "disable":
                return true;
            default:
                return false;
        }
    }

    private Domain getDomain(String name) throws CommandProcessException {
        switch (name) {
            case "Debugger":
                return debugger;
            case "Runtime":
                return runtime;
            case "Profiler":
                return profiler;
            case "Schema":
                return null;
            default:
                throw new CommandProcessException("Unknown domain '" + name + "'.");
        }
    }

    private void sendCommandSync(Command cmd) {
        CommandPostProcessor postProcessor = new CommandPostProcessor();
        JSONObject result = processCommand(cmd, postProcessor);
        if (result != null) {
            JSONMessageListener jsonListener = jsonMessageListener;
            if (jsonListener != null) {
                try {
                    jsonListener.onMessage(result);
                } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                    context.logException(e);
                }
            }
        }
        postProcessor.run();
    }

    public void consoleAPICall(String type, Object text) {
        runtime.notifyConsoleAPICalled(type, text);
    }

    @Override
    public void sendBinary(ByteBuffer data) {
        throw new UnsupportedOperationException("Binary messages are not supported.");
    }

    @Override
    public void sendPing(ByteBuffer data) {
    }

    @Override
    public void sendPong(ByteBuffer data) {
    }

    private JSONObject processCommand(Command cmd, CommandPostProcessor postProcessor) {
        JSONObject result;
        String method = cmd.getMethod();
        int dot = method.indexOf('.');
        boolean isEnabledChange = isDomainEnabledChange(method.substring(dot + 1));
        // Assure that domain enable/disable methods are called exclusively.
        Lock lock = isEnabledChange ? domainLock.writeLock() : domainLock.readLock();
        lock.lock();
        try {
            Domain domain = (dot > 0) ? getDomain(method.substring(0, dot)) : null;
            if (domain != null && !isEnabledChange) {
                if (!domain.isEnabled()) {
                    throw new CommandProcessException("Domain " + method.substring(0, dot) + " is disabled.");
                }
            }
            Params resultParams = doProcessCommand(cmd, postProcessor);
            if (resultParams == null) {
                result = Result.emptyResult(cmd.getId());
            } else {
                if (resultParams.getJSONObject() != null) {
                    result = new Result(resultParams).toJSON(cmd.getId());
                } else {
                    result = null;
                }
            }
        } catch (CommandProcessException cpex) {
            result = new ErrorResponse(cmd.getId(), -32601, cpex.getLocalizedMessage()).toJSON();
        } catch (ThreadDeath td) {
            throw td;
        } catch (Throwable t) {
            PrintWriter err = context.getErr();
            if (err != null) {
                t.printStackTrace(err);
            }
            result = new ErrorResponse(cmd.getId(), -32601, "Processing of '" + cmd.getMethod() + "' has caused " + t.getLocalizedMessage()).toJSON();
        } finally {
            lock.unlock();
        }
        return result;
    }

    private Params doProcessCommand(Command cmd, CommandPostProcessor postProcessor) throws CommandProcessException {
        Params resultParams = null;
        switch (cmd.getMethod()) {
            case "Runtime.enable":
                runtime.enable();
                break;
            case "Runtime.disable":
                runtime.disable();
                break;
            case "Runtime.compileScript":
                JSONObject json = cmd.getParams().getJSONObject();
                resultParams = runtime.compileScript(
                                json.optString("expression"),
                                json.optString("sourceURL"),
                                json.optBoolean("persistScript"),
                                json.optInt("executionContextId", -1));
                break;
            case "Runtime.evaluate":
                json = cmd.getParams().getJSONObject();
                resultParams = runtime.evaluate(
                                json.optString("expression"),
                                json.optString("objectGroup"),
                                json.optBoolean("includeCommandLineAPI"),
                                json.optBoolean("silent"),
                                json.optInt("contextId", -1),
                                json.optBoolean("returnByValue"),
                                json.optBoolean("generatePreview"),
                                json.optBoolean("awaitPromise"));
                break;
            case "Runtime.runIfWaitingForDebugger":
                runtime.runIfWaitingForDebugger(postProcessor);
                break;
            case "Runtime.getProperties":
                json = cmd.getParams().getJSONObject();
                resultParams = runtime.getProperties(
                                json.optString("objectId"),
                                json.optBoolean("ownProperties"),
                                json.optBoolean("accessorPropertiesOnly"),
                                json.optBoolean("generatePreview"));
                break;
            case "Runtime.callFunctionOn":
                json = cmd.getParams().getJSONObject();
                resultParams = runtime.callFunctionOn(
                                json.optString("objectId"),
                                json.optString("functionDeclaration"),
                                json.optJSONArray("arguments"),
                                json.optBoolean("silent"),
                                json.optBoolean("returnByValue"),
                                json.optBoolean("generatePreview"),
                                json.optBoolean("awaitPromise"),
                                json.optInt("executionContextId", -1),
                                json.optString("objectGroup"));
                break;
            case "Runtime.setCustomObjectFormatterEnabled":
                json = cmd.getParams().getJSONObject();
                runtime.setCustomObjectFormatterEnabled(json.optBoolean("enabled"));
                break;
            case "Runtime.releaseObject":
                json = cmd.getParams().getJSONObject();
                runtime.releaseObject(json.optString("objectId"));
                break;
            case "Runtime.releaseObjectGroup":
                json = cmd.getParams().getJSONObject();
                runtime.releaseObjectGroup(json.optString("objectGroup"));
                break;
            case "Debugger.enable":
                debugger.enable();
                break;
            case "Debugger.disable":
                debugger.disable();
                break;
            case "Debugger.evaluateOnCallFrame":
                json = cmd.getParams().getJSONObject();
                resultParams = debugger.evaluateOnCallFrame(
                                json.optString("callFrameId"),
                                json.optString("expression"),
                                json.optString("objectGroup"),
                                json.optBoolean("includeCommandLineAPI"),
                                json.optBoolean("silent"),
                                json.optBoolean("returnByValue"),
                                json.optBoolean("generatePreview"),
                                json.optBoolean("throwOnSideEffect"));
                break;
            case "Debugger.getPossibleBreakpoints":
                json = cmd.getParams().getJSONObject();
                resultParams = debugger.getPossibleBreakpoints(
                                Location.create(json.optJSONObject("start")),
                                Location.create(json.optJSONObject("end")),
                                json.optBoolean("restrictToFunction"));
                break;
            case "Debugger.getScriptSource":
                resultParams = debugger.getScriptSource(cmd.getParams().getScriptId());
                break;
            case "Debugger.pause":
                debugger.pause();
                break;
            case "Debugger.resume":
                debugger.resume(postProcessor);
                break;
            case "Debugger.stepInto":
                debugger.stepInto(postProcessor);
                break;
            case "Debugger.stepOver":
                debugger.stepOver(postProcessor);
                break;
            case "Debugger.stepOut":
                debugger.stepOut(postProcessor);
                break;
            case "Debugger.searchInContent":
                json = cmd.getParams().getJSONObject();
                resultParams = debugger.searchInContent(
                                json.optString("scriptId"),
                                json.optString("query"),
                                json.optBoolean("caseSensitive", false),
                                json.optBoolean("isRegex", false));
                break;
            case "Debugger.setAsyncCallStackDepth":
                debugger.setAsyncCallStackDepth(cmd.getParams().getMaxDepth());
                break;
            case "Debugger.setBlackboxPatterns":
                debugger.setBlackboxPatterns(cmd.getParams().getPatterns());
                break;
            case "Debugger.setPauseOnExceptions":
                debugger.setPauseOnExceptions(cmd.getParams().getState());
                break;
            case "Debugger.setBreakpointsActive":
                debugger.setBreakpointsActive(cmd.getParams().getBoolean("active"));
                break;
            case "Debugger.setSkipAllPauses":
                debugger.setSkipAllPauses(cmd.getParams().getBoolean("skip"));
                break;
            case "Debugger.setBreakpointByUrl":
                json = cmd.getParams().getJSONObject();
                resultParams = debugger.setBreakpointByUrl(
                                json.optString("url"),
                                json.optString("urlRegex"),
                                json.optInt("lineNumber", -1) + 1,
                                json.optInt("columnNumber", -1) + 1,
                                json.optString("condition"));
                break;
            case "Debugger.setBreakpoint":
                json = cmd.getParams().getJSONObject();
                resultParams = debugger.setBreakpoint(
                                Location.create(json.getJSONObject("location")),
                                json.optString("condition"));
                break;
            case "Debugger.setBreakpointOnFunctionCall":
                json = cmd.getParams().getJSONObject();
                resultParams = debugger.setBreakpointOnFunctionCall(
                                json.optString("objectId"),
                                json.optString("condition"));
                break;
            case "Debugger.removeBreakpoint":
                debugger.removeBreakpoint(cmd.getParams().getBreakpointId());
                break;
            case "Debugger.continueToLocation":
                debugger.continueToLocation(
                                Location.create(cmd.getParams().getJSONObject().getJSONObject("location")), postProcessor);
                break;
            case "Debugger.restartFrame":
                json = cmd.getParams().getJSONObject();
                resultParams = debugger.restartFrame(cmd.getId(), json.optString("callFrameId"), postProcessor);
                break;
            case "Debugger.setReturnValue":
                json = cmd.getParams().getJSONObject();
                debugger.setReturnValue(CallArgument.get(json.getJSONObject("newValue")));
                break;
            case "Debugger.setVariableValue":
                json = cmd.getParams().getJSONObject();
                debugger.setVariableValue(
                                json.optInt("scopeNumber", -1),
                                json.optString("variableName"),
                                CallArgument.get(json.getJSONObject("newValue")),
                                json.optString("callFrameId"));
                break;
            case "Profiler.enable":
                profiler.enable();
                break;
            case "Profiler.disable":
                profiler.disable();
                break;
            case "Profiler.setSamplingInterval":
                profiler.setSamplingInterval(cmd.getParams().getSamplingInterval());
                break;
            case "Profiler.start":
                profiler.start();
                break;
            case "Profiler.stop":
                resultParams = profiler.stop();
                break;
            case "Profiler.startPreciseCoverage":
                Params params = cmd.getParams();
                if (params != null) {
                    json = params.getJSONObject();
                    profiler.startPreciseCoverage(json.optBoolean("callCount"), json.optBoolean("detailed"));
                } else {
                    profiler.startPreciseCoverage(false, false);
                }
                break;
            case "Profiler.stopPreciseCoverage":
                profiler.stopPreciseCoverage();
                break;
            case "Profiler.takePreciseCoverage":
                resultParams = profiler.takePreciseCoverage();
                break;
            case "Profiler.getBestEffortCoverage":
                resultParams = profiler.getBestEffortCoverage();
                break;
            case "Profiler.startTypeProfile":
                profiler.startTypeProfile();
                break;
            case "Profiler.stopTypeProfile":
                profiler.stopTypeProfile();
                break;
            case "Profiler.takeTypeProfile":
                resultParams = profiler.takeTypeProfile();
                break;
            case "Schema.getDomains":
                resultParams = getDomains();
                break;
            default:
                throw new CommandProcessException("'" + cmd.getMethod() + "' wasn't found");
        }
        return resultParams;
    }

    private static Params getDomains() {
        JSONArray domains = new JSONArray();
        domains.put(createJsonDomain("Runtime"));
        domains.put(createJsonDomain("Debugger"));
        domains.put(createJsonDomain("Profiler"));
        domains.put(createJsonDomain("Schema"));
        JSONObject domainsObj = new JSONObject();
        domainsObj.put("domains", domains);
        return new Params(domainsObj);
    }

    private static JSONObject createJsonDomain(String name) {
        JSONObject dom = new JSONObject();
        dom.put("name", name);
        dom.put("version", "1.2");
        return dom;
    }

    private class EventHandlerImpl implements EventHandler {

        @Override
        public void event(Event event) {
            MessageEndpoint listener = messageEndpoint;
            if (listener != null) {
                try {
                    listener.sendText(event.toJSONString());
                } catch (IOException ex) {
                    context.logException(ex);
                }
            }
            JSONMessageListener jsonListener = jsonMessageListener;
            if (jsonListener != null) {
                try {
                    jsonListener.onMessage(event.toJSON());
                } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                    context.logException(e);
                }
            }
        }

    }

    /**
     * A post-processor of commands. The post-process job is run just after a response from the
     * command is sent. This can assure deterministic order of messages for instance, it is used
     * currently for resumes.
     */
    public final class CommandPostProcessor {

        private Runnable postProcessJob;

        public void setPostProcessJob(Runnable postProcessJob) {
            assert this.postProcessJob == null;
            this.postProcessJob = postProcessJob;
        }

        void run() {
            if (postProcessJob != null) {
                postProcessJob.run();
            }
        }
    }

    private class CommandProcessThread extends Thread {

        private volatile boolean disposed = false;
        private final BlockingQueue<Command> commands = new LinkedBlockingQueue<>();

        CommandProcessThread() {
            super("chromeinspector.server.CommandProcessThread");
            setDaemon(true);
        }

        void push(Command cmd) {
            if (disposed) {
                return;
            }
            try {
                commands.put(cmd);
            } catch (InterruptedException iex) {
                dispose();
            }
        }

        void dispose() {
            disposed = true;
            interrupt();
        }

        @Override
        public void run() {
            while (!disposed) {
                Command cmd;
                try {
                    cmd = commands.take();
                } catch (InterruptedException iex) {
                    break;
                }
                CommandPostProcessor postProcessor = new CommandPostProcessor();
                JSONObject result = processCommand(cmd, postProcessor);
                if (result != null) {
                    MessageEndpoint listener = messageEndpoint;
                    if (listener != null) {
                        try {
                            listener.sendText(result.toString());
                        } catch (IOException ex) {
                            context.logException(ex);
                        }
                    }
                    JSONMessageListener jsonListener = jsonMessageListener;
                    if (jsonListener != null) {
                        try {
                            jsonListener.onMessage(result);
                        } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                            context.logException(e);
                        }
                    }
                }
                postProcessor.run();
            }
        }

    }

}
