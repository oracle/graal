/*
 * Copyright (c) 2020, 2024, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.InstrumentInfo;
import com.oracle.truffle.api.TruffleContext;
import com.oracle.truffle.api.debug.DebugException;
import com.oracle.truffle.api.debug.DebugValue;
import com.oracle.truffle.api.debug.Debugger;
import com.oracle.truffle.api.debug.DebuggerSession;
import com.oracle.truffle.api.debug.SourceElement;
import com.oracle.truffle.api.debug.StepConfig;
import com.oracle.truffle.api.debug.SuspendAnchor;
import com.oracle.truffle.api.debug.SuspendedCallback;
import com.oracle.truffle.api.debug.SuspendedEvent;
import com.oracle.truffle.api.debug.SuspensionFilter;
import com.oracle.truffle.api.instrumentation.ContextsListener;
import com.oracle.truffle.api.instrumentation.EventBinding;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.tools.dap.instrument.Enabler;
import com.oracle.truffle.tools.dap.instrument.OutputConsumerInstrument;
import com.oracle.truffle.tools.dap.types.AttachRequestArguments;
import com.oracle.truffle.tools.dap.types.BreakpointLocationsArguments;
import com.oracle.truffle.tools.dap.types.BreakpointLocationsResponse;
import com.oracle.truffle.tools.dap.types.Capabilities;
import com.oracle.truffle.tools.dap.types.ConfigurationDoneArguments;
import com.oracle.truffle.tools.dap.types.ContinueArguments;
import com.oracle.truffle.tools.dap.types.ContinueResponse;
import com.oracle.truffle.tools.dap.types.DebugProtocolClient;
import com.oracle.truffle.tools.dap.types.DebugProtocolServer;
import com.oracle.truffle.tools.dap.types.DisconnectArguments;
import com.oracle.truffle.tools.dap.types.EvaluateArguments;
import com.oracle.truffle.tools.dap.types.EvaluateResponse;
import com.oracle.truffle.tools.dap.types.ExceptionBreakpointsFilter;
import com.oracle.truffle.tools.dap.types.ExceptionInfoArguments;
import com.oracle.truffle.tools.dap.types.ExceptionInfoResponse;
import com.oracle.truffle.tools.dap.types.InitializeRequestArguments;
import com.oracle.truffle.tools.dap.types.LaunchRequestArguments;
import com.oracle.truffle.tools.dap.types.LoadedSourcesArguments;
import com.oracle.truffle.tools.dap.types.LoadedSourcesResponse;
import com.oracle.truffle.tools.dap.types.NextArguments;
import com.oracle.truffle.tools.dap.types.OutputEvent;
import com.oracle.truffle.tools.dap.types.PauseArguments;
import com.oracle.truffle.tools.dap.types.Scope;
import com.oracle.truffle.tools.dap.types.ScopesArguments;
import com.oracle.truffle.tools.dap.types.ScopesResponse;
import com.oracle.truffle.tools.dap.types.SetBreakpointsArguments;
import com.oracle.truffle.tools.dap.types.SetBreakpointsResponse;
import com.oracle.truffle.tools.dap.types.SetExceptionBreakpointsArguments;
import com.oracle.truffle.tools.dap.types.SetFunctionBreakpointsArguments;
import com.oracle.truffle.tools.dap.types.SetFunctionBreakpointsResponse;
import com.oracle.truffle.tools.dap.types.SetVariableArguments;
import com.oracle.truffle.tools.dap.types.SetVariableResponse;
import com.oracle.truffle.tools.dap.types.SourceArguments;
import com.oracle.truffle.tools.dap.types.SourceResponse;
import com.oracle.truffle.tools.dap.types.StackFrame;
import com.oracle.truffle.tools.dap.types.StackTraceArguments;
import com.oracle.truffle.tools.dap.types.StackTraceResponse;
import com.oracle.truffle.tools.dap.types.StepInArguments;
import com.oracle.truffle.tools.dap.types.StepOutArguments;
import com.oracle.truffle.tools.dap.types.TerminatedEvent;
import com.oracle.truffle.tools.dap.types.ThreadsResponse;
import com.oracle.truffle.tools.dap.types.Variable;
import com.oracle.truffle.tools.dap.types.VariablesArguments;
import com.oracle.truffle.tools.dap.types.VariablesResponse;
import org.graalvm.shadowed.org.json.JSONArray;
import org.graalvm.shadowed.org.json.JSONObject;

import java.io.IOException;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.Phaser;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * A {@link DebugProtocolServer} implementation using TCP sockets as transportation layer for the
 * JSON-RPC requests.
 */
public final class DebugProtocolServerImpl extends DebugProtocolServer {

    private static final StepConfig STEP_CONFIG = StepConfig.newBuilder().suspendAnchors(SourceElement.ROOT, SuspendAnchor.AFTER).build();

    private final ExecutionContext context;
    private volatile DebugProtocolClient client;
    private volatile DebuggerSession debuggerSession;
    private final Enabler ioEnabler;
    private volatile boolean launched;  // true when launched, false when attached
    private volatile boolean disposed = false;
    private final List<Runnable> runOnDispose = new CopyOnWriteArrayList<>();
    private final List<URI> sourcePath;
    private volatile OneTimeExecutor clientConnectionExecutor;

    private DebugProtocolServerImpl(ExecutionContext context, final boolean debugBreak, final boolean waitAttached, @SuppressWarnings("unused") final boolean inspectInitialization,
                    final List<URI> sourcePath) {
        this.context = context;
        this.sourcePath = sourcePath;
        if (debugBreak) {
            debuggerSession = startDebuggerSession();
            context.initSession(debuggerSession);
            debuggerSession.suspendNextExecution();
        }
        if (debugBreak || waitAttached) {
            final AtomicReference<EventBinding<?>> execEnter = new AtomicReference<>();
            final AtomicBoolean disposeBinding = new AtomicBoolean(false);
            execEnter.set(context.getEnv().getInstrumenter().attachContextsListener(new ContextsListener() {
                @Override
                public void onContextCreated(TruffleContext ctx) {
                }

                @Override
                public void onLanguageContextCreated(TruffleContext ctx, LanguageInfo language) {
                    if (inspectInitialization) {
                        waitForRunPermission();
                    }
                }

                @Override
                public void onLanguageContextInitialized(TruffleContext ctx, LanguageInfo language) {
                    if (!inspectInitialization) {
                        waitForRunPermission();
                    }
                }

                @Override
                public void onLanguageContextFinalized(TruffleContext ctx, LanguageInfo language) {
                }

                @Override
                public void onLanguageContextDisposed(TruffleContext ctx, LanguageInfo language) {
                }

                @Override
                public void onContextClosed(TruffleContext ctx) {
                }

                @CompilerDirectives.TruffleBoundary
                private void waitForRunPermission() {
                    try {
                        context.waitForRunPermission();
                    } catch (InterruptedException ex) {
                    }
                    final EventBinding<?> binding = execEnter.getAndSet(null);
                    if (binding != null) {
                        binding.dispose();
                    } else {
                        disposeBinding.set(true);
                    }
                }
            }, true));
            if (disposeBinding.get()) {
                execEnter.get().dispose();
            }
        }
        InstrumentInfo instrumentInfo = context.getEnv().getInstruments().get(OutputConsumerInstrument.ID);
        ioEnabler = context.getEnv().lookup(instrumentInfo, Enabler.class);
        ioEnabler.enable();
        OutputHandler oh = context.getEnv().lookup(instrumentInfo, OutputHandler.Provider.class).getOutputHandler();
        ConsoleOutputListener outL = new ConsoleOutputListener("stdout");
        ConsoleOutputListener errL = new ConsoleOutputListener("stderr");
        oh.setOutListener(outL);
        oh.setErrListener(errL);
    }

    public static DebugProtocolServerImpl create(ExecutionContext context, final boolean debugBreak, final boolean waitAttached, final boolean inspectInitialization, final List<URI> sourcePath) {
        return new DebugProtocolServerImpl(context, debugBreak, waitAttached, inspectInitialization, sourcePath);
    }

    @Override
    public CompletableFuture<Capabilities> initialize(InitializeRequestArguments args) {
        context.setLinesStartAt1(args.getLinesStartAt1());
        context.setColumnsStartAt1(args.getColumnsStartAt1());
        ExceptionBreakpointsFilter[] exceptionBreakpointFilters = new ExceptionBreakpointsFilter[]{
                        ExceptionBreakpointsFilter.create("all", "All Exceptions"),
                        ExceptionBreakpointsFilter.create("uncaught", "Uncaught Exceptions")
        };
        final CompletableFuture<Capabilities> future = CompletableFuture.completedFuture(Capabilities.create() //
                        .setExceptionBreakpointFilters(Arrays.asList(exceptionBreakpointFilters)) //
                        .setSupportsConfigurationDoneRequest(true) //
                        .setSupportsFunctionBreakpoints(true) //
                        .setSupportsConditionalBreakpoints(true) //
                        .setSupportsHitConditionalBreakpoints(true) //
                        .setSupportsSetVariable(true) //
                        .setSupportsExceptionInfoRequest(true) //
                        .setSupportsLoadedSourcesRequest(true) //
                        .setSupportsLogPoints(true) //
                        .setSupportsBreakpointLocationsRequest(true));
        future.thenRunAsync(() -> {
            client.initialized();
        });
        return future;
    }

    @Override
    public CompletableFuture<Void> configurationDone(ConfigurationDoneArguments args) {
        return CompletableFuture.runAsync(() -> context.doRunIfWaitingForDebugger());
    }

    @Override
    public CompletableFuture<Void> launch(LaunchRequestArguments args) {
        return CompletableFuture.runAsync(() -> {
            JSONObject info = (JSONObject) args.get("graalVMLaunchInfo");
            if (info != null) {
                StringBuilder sb = new StringBuilder(info.getString("exec"));
                JSONArray argsInfo = info.getJSONArray("args");
                for (int i = 0; i < argsInfo.length(); i++) {
                    sb.append(' ').append(argsInfo.getString(i));
                }
                client.output(OutputEvent.EventBody.create(sb.toString()));
            }
            client.output(OutputEvent.EventBody.create("Debugger attached.").setCategory("stderr"));
            launched = true;
        });
    }

    @Override
    public CompletableFuture<Void> attach(AttachRequestArguments args) {
        return CompletableFuture.runAsync(() -> {
            client.output(OutputEvent.EventBody.create("Debugger attached.").setCategory("stderr"));
        });
    }

    @Override
    public CompletableFuture<Void> disconnect(DisconnectArguments args, Consumer<? super Void> responseConsumer) {
        return CompletableFuture.runAsync(() -> {
            DebuggerSession session;
            synchronized (DebugProtocolServerImpl.this) {
                disposed = true;
                session = debuggerSession;
                debuggerSession = null;
            }
            ioEnabler.disable();
            if (session != null) {
                session.close();
            }
            context.dispose();
            responseConsumer.accept(null);
            if (launched) {
                // Cancel all contexts to terminate the execution
                AllContextsCancel cancel = new AllContextsCancel();
                EventBinding<ContextsListener> binding = context.getEnv().getInstrumenter().attachContextsListener(cancel, true);
                cancel.waitForAllCanceled();
                binding.dispose();
            }
        });
    }

    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        DebugProtocolClient theClient = client;
        if (theClient != null) {
            theClient.terminated(TerminatedEvent.EventBody.create());
        }
        for (Runnable r : runOnDispose) {
            r.run();
        }
        clientConnectionExecutor.shutDownAndJoin();
    }

    private void onDispose(Runnable r) {
        runOnDispose.add(r);
    }

    @Override
    public CompletableFuture<BreakpointLocationsResponse.ResponseBody> breakpointLocations(BreakpointLocationsArguments args) {
        return CompletableFuture.completedFuture(BreakpointLocationsResponse.ResponseBody.create(context.getBreakpointsHandler().breakpointLocations(args)));
    }

    @Override
    public CompletableFuture<SetBreakpointsResponse.ResponseBody> setBreakpoints(SetBreakpointsArguments args) {
        try {
            return CompletableFuture.completedFuture(SetBreakpointsResponse.ResponseBody.create(context.getBreakpointsHandler().setBreakpoints(args)));
        } catch (ExceptionWithMessage ex) {
            return CompletableFuture.failedFuture(ex);
        }
    }

    @Override
    public CompletableFuture<SetFunctionBreakpointsResponse.ResponseBody> setFunctionBreakpoints(SetFunctionBreakpointsArguments args) {
        return CompletableFuture.completedFuture(SetFunctionBreakpointsResponse.ResponseBody.create(context.getBreakpointsHandler().setFunctionBreakpoints(args)));
    }

    @Override
    public CompletableFuture<Void> setExceptionBreakpoints(SetExceptionBreakpointsArguments args) {
        if (args.getFilters().indexOf("all") >= 0) {
            context.getBreakpointsHandler().setExceptionBreakpoint(true, true);
        } else if (args.getFilters().indexOf("uncaught") >= 0) {
            context.getBreakpointsHandler().setExceptionBreakpoint(false, true);
        } else {
            context.getBreakpointsHandler().setExceptionBreakpoint(false, false);
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<ContinueResponse.ResponseBody> doContinue(ContinueArguments args, Consumer<? super ContinueResponse.ResponseBody> responseConsumer) {
        CompletableFuture<ContinueResponse.ResponseBody> future = new CompletableFuture<>();
        context.getThreadsHandler().executeInSuspendedThread(args.getThreadId(), (info) -> {
            if (info == null) {
                future.completeExceptionally(Errors.invalidThread(args.getThreadId()));
                return false;
            }
            info.getSuspendedEvent().prepareContinue();
            ContinueResponse.ResponseBody response = ContinueResponse.ResponseBody.create().setAllThreadsContinued(false);
            responseConsumer.accept(response);
            future.complete(response);
            return true;
        });
        return future;
    }

    @Override
    public CompletableFuture<Void> next(NextArguments args, Consumer<? super Void> responseConsumer) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        context.getThreadsHandler().executeInSuspendedThread(args.getThreadId(), (info) -> {
            if (info == null) {
                future.completeExceptionally(Errors.invalidThread(args.getThreadId()));
                return false;
            }
            info.getSuspendedEvent().prepareStepOver(STEP_CONFIG);
            responseConsumer.accept(null);
            future.complete(null);
            return true;
        });
        return future;
    }

    @Override
    public CompletableFuture<Void> stepIn(StepInArguments args, Consumer<? super Void> responseConsumer) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        context.getThreadsHandler().executeInSuspendedThread(args.getThreadId(), (info) -> {
            if (info == null) {
                future.completeExceptionally(Errors.invalidThread(args.getThreadId()));
                return false;
            }
            info.getSuspendedEvent().prepareStepInto(STEP_CONFIG);
            responseConsumer.accept(null);
            future.complete(null);
            return true;
        });
        return future;
    }

    @Override
    public CompletableFuture<Void> stepOut(StepOutArguments args, Consumer<? super Void> responseConsumer) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        context.getThreadsHandler().executeInSuspendedThread(args.getThreadId(), (info) -> {
            if (info == null) {
                future.completeExceptionally(Errors.invalidThread(args.getThreadId()));
                return false;
            }
            info.getSuspendedEvent().prepareStepOut(STEP_CONFIG);
            responseConsumer.accept(null);
            future.complete(null);
            return true;
        });
        return future;
    }

    @Override
    public CompletableFuture<Void> pause(PauseArguments args) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        if (context.getThreadsHandler().pause(args.getThreadId())) {
            future.complete(null);
        } else {
            future.completeExceptionally(Errors.invalidThread(args.getThreadId()));
        }
        return future;
    }

    @Override
    public CompletableFuture<StackTraceResponse.ResponseBody> stackTrace(StackTraceArguments args) {
        CompletableFuture<StackTraceResponse.ResponseBody> future = new CompletableFuture<>();
        context.getThreadsHandler().executeInSuspendedThread(args.getThreadId(), (info) -> {
            if (info == null) {
                future.completeExceptionally(Errors.noCallStackAvailable());
            } else {
                List<StackFrame> stackTrace = context.getStackFramesHandler().getStackTrace(info);
                int startIdx = args.getStartFrame() != null ? args.getStartFrame() : 0;
                int endIdx = startIdx + (args.getLevels() != null ? args.getLevels() : stackTrace.size());
                if (startIdx > 0 || endIdx < stackTrace.size()) {
                    stackTrace = stackTrace.subList(startIdx, endIdx);
                }
                future.complete(StackTraceResponse.ResponseBody.create(stackTrace).setTotalFrames(stackTrace.size()));
            }
            return false;
        });
        return future;
    }

    @Override
    public CompletableFuture<ScopesResponse.ResponseBody> scopes(ScopesArguments args) {
        CompletableFuture<ScopesResponse.ResponseBody> future = new CompletableFuture<>();
        context.getThreadsHandler().executeInSuspendedThread(args.getFrameId(), (info) -> {
            List<Scope> scopes = info != null ? context.getStackFramesHandler().getScopes(info, args.getFrameId()) : null;
            if (scopes == null) {
                future.completeExceptionally(Errors.stackFrameNotValid());
            } else {
                future.complete(ScopesResponse.ResponseBody.create(scopes));
            }
            return false;
        });
        return future;
    }

    @Override
    public CompletableFuture<VariablesResponse.ResponseBody> variables(VariablesArguments args) {
        CompletableFuture<VariablesResponse.ResponseBody> future = new CompletableFuture<>();
        context.getThreadsHandler().executeInSuspendedThread(args.getVariablesReference(), (info) -> {
            List<Variable> variables = info != null ? context.getVariablesHandler().getVariables(info, args) : null;
            if (variables == null) {
                variables = Collections.emptyList();
            }
            future.complete(VariablesResponse.ResponseBody.create(variables));
            return false;
        });
        return future;
    }

    @Override
    public CompletableFuture<SetVariableResponse.ResponseBody> setVariable(SetVariableArguments args) {
        CompletableFuture<SetVariableResponse.ResponseBody> future = new CompletableFuture<>();
        context.getThreadsHandler().executeInSuspendedThread(args.getVariablesReference(), (info) -> {
            try {
                Variable var = info != null ? VariablesHandler.setVariable(info, args) : null;
                if (var == null) {
                    future.completeExceptionally(Errors.setValueNotSupported());
                } else {
                    future.complete(SetVariableResponse.ResponseBody.create(var.getValue()).setType(var.getType()).setVariablesReference(var.getVariablesReference()).setIndexedVariables(
                                    var.getIndexedVariables()).setNamedVariables(var.getNamedVariables()));
                }
            } catch (Exception e) {
                future.completeExceptionally(Errors.errorFromEvaluate(e.getMessage()));
            }
            return false;
        });
        return future;
    }

    @Override
    public CompletableFuture<SourceResponse.ResponseBody> source(SourceArguments args) {
        CompletableFuture<SourceResponse.ResponseBody> future = new CompletableFuture<>();
        Source source;
        Integer sourceReference = args.getSource().getSourceReference();
        if (sourceReference != null && sourceReference > 0) {
            source = context.getLoadedSourcesHandler().getSource(sourceReference);
        } else {
            source = context.getLoadedSourcesHandler().getSource(args.getSource().getPath());
        }
        if (source == null) {
            future.completeExceptionally(Errors.sourceRequestIllegalHandle());
        } else if (!source.hasCharacters()) {
            future.completeExceptionally(Errors.sourceRequestCouldNotRetrieveContent());
        } else {
            future.complete(SourceResponse.ResponseBody.create(source.getCharacters().toString()).setMimeType(source.getMimeType()));
        }
        return future;
    }

    @Override
    public CompletableFuture<ThreadsResponse.ResponseBody> threads() {
        return CompletableFuture.completedFuture(ThreadsResponse.ResponseBody.create(context.getThreadsHandler().getThreads()));
    }

    @Override
    public CompletableFuture<LoadedSourcesResponse.ResponseBody> loadedSources(LoadedSourcesArguments args) {
        return CompletableFuture.completedFuture(LoadedSourcesResponse.ResponseBody.create(context.getLoadedSourcesHandler().getLoadedSources()));
    }

    @Override
    public CompletableFuture<EvaluateResponse.ResponseBody> evaluate(EvaluateArguments args) {
        CompletableFuture<EvaluateResponse.ResponseBody> future = new CompletableFuture<>();
        Integer frameId = args.getFrameId();
        context.getThreadsHandler().executeInSuspendedThread(frameId != null ? frameId : 0, (info) -> {
            if (info == null) {
                future.completeExceptionally(Errors.stackFrameNotValid());
            } else {
                try {
                    Variable var = StackFramesHandler.evaluateOnStackFrame(info, args.getFrameId(), args.getExpression());
                    if (var == null) {
                        future.completeExceptionally(Errors.stackFrameNotValid());
                    } else {
                        future.complete(EvaluateResponse.ResponseBody.create(var.getValue(), var.getVariablesReference()).setType(var.getType()).setIndexedVariables(
                                        var.getIndexedVariables()).setNamedVariables(var.getNamedVariables()));
                    }
                } catch (Exception e) {
                    future.completeExceptionally(Errors.errorFromEvaluate(e.getMessage()));
                }
            }
            return false;
        });
        return future;
    }

    @Override
    public CompletableFuture<ExceptionInfoResponse.ResponseBody> exceptionInfo(ExceptionInfoArguments args) {
        CompletableFuture<ExceptionInfoResponse.ResponseBody> future = new CompletableFuture<>();
        context.getThreadsHandler().executeInSuspendedThread(args.getThreadId(), (info) -> {
            if (info == null) {
                future.completeExceptionally(Errors.invalidThread(args.getThreadId()));
            } else {
                DebugException exception = info.getSuspendedEvent().getException();
                if (exception == null) {
                    future.completeExceptionally(Errors.noStoredException());
                } else {
                    DebugValue exceptionObject = exception.getExceptionObject();
                    String description = exceptionObject != null && exceptionObject.isReadable() ? exceptionObject.toDisplayString() : null;
                    DebugValue metaObject = exceptionObject != null ? exceptionObject.getMetaObject() : null;
                    String exceptionId = metaObject != null ? metaObject.getMetaSimpleName() : null;
                    future.complete(ExceptionInfoResponse.ResponseBody.create(exceptionId != null ? exceptionId : "Error", "unhandled").setDescription(description));
                }
            }
            return false;
        });
        return future;
    }

    @Override
    protected void connect(DebugProtocolClient clnt) {
        this.client = clnt;
        if (debuggerSession == null) {
            DebuggerSession session = startDebuggerSession();
            boolean isDisposed;
            synchronized (this) {
                isDisposed = disposed;
                if (!isDisposed) {
                    debuggerSession = session;
                }
            }
            if (isDisposed) {
                session.close();
            } else {
                context.initSession(session);
            }
        }
        context.initClient(client);
    }

    @Override
    public LoggerProxy getLogger() {
        return new LoggerProxy() {
            @Override
            public boolean isLoggable(Level level) {
                return context.getLogger().isLoggable(level);
            }

            @Override
            public void log(Level level, String msg) {
                context.getLogger().log(level, msg);
            }

            @Override
            public void log(Level level, String msg, Throwable thrown) {
                context.getLogger().log(level, msg, thrown);
            }
        };
    }

    /**
     * The executors created by Executors do not allow to join the threads in the pool. System
     * threads must be joined before Engine close and ExecutorService.awaitTermination() does not
     * guarantee threads termination.
     */
    private static class OneTimeExecutor implements Executor {

        private final TruffleInstrument.Env env;
        private final String threadName;
        private Reference<Thread> thread;
        private boolean shutDown;

        OneTimeExecutor(TruffleInstrument.Env env, String threadName) {
            this.env = env;
            this.threadName = threadName;
        }

        public synchronized void execute(Runnable command) {
            if (shutDown) {
                return;
            }
            if (thread != null) {
                throw new IllegalStateException("This is a one-time executor.");
            }
            Thread t = env.createSystemThread(command);
            t.setName(threadName);
            t.start();
            thread = new WeakReference<>(t);
        }

        synchronized void shutDownAndJoin() {
            Thread t = (thread != null) ? thread.get() : null;
            if (t != null) {
                t.interrupt();
                try {
                    t.join();
                } catch (InterruptedException ex) {
                    // Interrupted
                }
            }
            shutDown = true;
        }
    }

    public CompletableFuture<?> start(final ServerSocket serverSocket) {
        clientConnectionExecutor = new OneTimeExecutor(context.getEnv(), "DAP client connection thread");
        context.getInfo().println("[Graal DAP] Starting server and listening on " + serverSocket.getLocalSocketAddress());
        return CompletableFuture.runAsync(new Runnable() {

            @Override
            public void run() {
                AtomicBoolean terminated = new AtomicBoolean(false);
                try {
                    if (serverSocket.isClosed()) {
                        context.getErr().println("[Graal DAP] Server socket is closed.");
                        return;
                    }

                    onDispose(() -> {
                        terminated.set(true);
                        try {
                            serverSocket.close();
                        } catch (IOException e) {
                            context.getErr().println("[Graal DAP] Error while closing the server socket: " + e.getLocalizedMessage());
                        }
                    });
                    if (disposed) {
                        // Disposed in the mean time.
                        return;
                    }
                    try (Socket clientSocket = serverSocket.accept()) {
                        context.getInfo().println("[Graal DAP] Client connected on " + clientSocket.getRemoteSocketAddress());

                        OneTimeExecutor dapRequestExecutor = new OneTimeExecutor(context.getEnv(), "DAP request handler");
                        Future<?> listenFuture = Session.connect(DebugProtocolServerImpl.this, clientSocket.getInputStream(), clientSocket.getOutputStream(), dapRequestExecutor);
                        try {
                            listenFuture.get();
                        } catch (InterruptedException | ExecutionException e) {
                            context.getErr().println("[Graal DAP] Error: " + e.getLocalizedMessage());
                        } finally {
                            dapRequestExecutor.shutDownAndJoin();
                        }
                    }
                } catch (IOException e) {
                    if (terminated.get() && (e instanceof SocketException)) {
                        // We've terminated the socket, thus we ignore any exceptions from it.
                        // serverSocket.accept() will always throw "Socket closed" exception
                        // when serverSocket.close() is called.
                    } else {
                        context.getErr().println("[Graal DAP] Error while connecting to client: " + e.getLocalizedMessage());
                    }
                }
            }
        }, clientConnectionExecutor);
    }

    private DebuggerSession startDebuggerSession() {
        Debugger tdbg = context.getEnv().lookup(context.getEnv().getInstruments().get("debugger"), Debugger.class);
        DebuggerSession session = tdbg.startSession(new SuspendedCallbackImpl(), SourceElement.ROOT, SourceElement.STATEMENT);
        session.setSourcePath(sourcePath);
        session.setSteppingFilter(SuspensionFilter.newBuilder() //
                        .ignoreLanguageContextInitialization(!context.isInspectInitialization()) //
                        .includeInternal(context.isInspectInternal()) //
                        .sourceSectionAvailableOnly(true).build());
        return session;
    }

    private final class SuspendedCallbackImpl implements SuspendedCallback {

        @Override
        public void onSuspend(SuspendedEvent event) {
            try {
                context.waitForRunPermission();
            } catch (InterruptedException ex) {
            }
            SourceSection ss = event.getSourceSection();
            if (debuggerSession == null) {
                // Debugger has been disabled while waiting
                return;
            }
            DebugValue returnValue = event.getReturnValue();
            if (event.hasSourceElement(SourceElement.ROOT) && event.getBreakpoints().isEmpty()) {
                if ((!event.hasSourceElement(SourceElement.STATEMENT) && event.getSuspendAnchor() == SuspendAnchor.BEFORE) ||
                                (event.getSuspendAnchor() == SuspendAnchor.AFTER && returnValue == null)) {
                    // We're at the begining of a `RootTag` node, or
                    // we're at the end of `RootTag` node and have no return value.
                    // We use `RootTag` to intercept return values of functions during stepping.
                    // But if there's no return value, there's no point in suspending at the end of
                    // a function. That would cause an unnecessary distraction.
                    event.prepareStepInto(STEP_CONFIG);
                    return;
                }
            }
            context.getLoadedSourcesHandler().assureLoaded(ss.getSource());
            context.getThreadsHandler().threadSuspended(Thread.currentThread(), event);
        }
    }

    private class ConsoleOutputListener implements OutputHandler.Listener {

        private final String category;

        ConsoleOutputListener(String category) {
            this.category = category;
        }

        @Override
        public void outputText(String text) {
            DebugProtocolClient debugClient = context.getClient();
            if (client != null) {
                OutputEvent.EventBody event = OutputEvent.EventBody.create(text);
                event.setCategory(category);
                debugClient.output(event);
            }
        }
    }

    private static final class AllContextsCancel implements ContextsListener {

        private final Phaser allClosed = new Phaser(1);

        @Override
        public void onContextCreated(TruffleContext context) {
            allClosed.register();
            context.closeCancelled(null, "Cancel on debugger disconnect.");
        }

        @Override
        public void onLanguageContextCreated(TruffleContext context, LanguageInfo language) {
        }

        @Override
        public void onLanguageContextInitialized(TruffleContext context, LanguageInfo language) {
        }

        @Override
        public void onLanguageContextFinalized(TruffleContext context, LanguageInfo language) {
        }

        @Override
        public void onLanguageContextDisposed(TruffleContext context, LanguageInfo language) {
        }

        @Override
        public void onContextClosed(TruffleContext context) {
            allClosed.arriveAndDeregister();
        }

        void waitForAllCanceled() {
            allClosed.arriveAndAwaitAdvance();
        }
    }
}
