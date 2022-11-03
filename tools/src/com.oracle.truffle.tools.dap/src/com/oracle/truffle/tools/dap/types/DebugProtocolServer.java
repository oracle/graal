/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.tools.dap.types;

import com.oracle.truffle.tools.utils.json.JSONArray;
import com.oracle.truffle.tools.utils.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DebugProtocolServer {

    // protocol methods
    public CompletableFuture<Void> cancel(@SuppressWarnings("unused") CancelArguments args) {
        throw new UnsupportedOperationException("'cancel' command not supported");
    }

    public CompletableFuture<Capabilities> initialize(@SuppressWarnings("unused") InitializeRequestArguments args) {
        throw new UnsupportedOperationException("'initialize' command not supported");
    }

    public CompletableFuture<Void> configurationDone(@SuppressWarnings("unused") ConfigurationDoneArguments args) {
        throw new UnsupportedOperationException("'configurationDone' command not supported");
    }

    public CompletableFuture<Void> launch(@SuppressWarnings("unused") LaunchRequestArguments args) {
        throw new UnsupportedOperationException("'launch' command not supported");
    }

    public CompletableFuture<Void> attach(@SuppressWarnings("unused") AttachRequestArguments args) {
        throw new UnsupportedOperationException("'attach' command not supported");
    }

    public CompletableFuture<Void> restart(@SuppressWarnings("unused") RestartArguments args) {
        throw new UnsupportedOperationException("'restart' command not supported");
    }

    public CompletableFuture<Void> disconnect(@SuppressWarnings("unused") DisconnectArguments args, @SuppressWarnings("unused") Consumer<? super Void> responseConsumer) {
        throw new UnsupportedOperationException("'disconnect' command not supported");
    }

    public CompletableFuture<Void> terminate(@SuppressWarnings("unused") TerminateArguments args) {
        throw new UnsupportedOperationException("'terminate' command not supported");
    }

    public CompletableFuture<BreakpointLocationsResponse.ResponseBody> breakpointLocations(@SuppressWarnings("unused") BreakpointLocationsArguments args) {
        throw new UnsupportedOperationException("'breakpointLocations' command not supported");
    }

    public CompletableFuture<SetBreakpointsResponse.ResponseBody> setBreakpoints(@SuppressWarnings("unused") SetBreakpointsArguments args) {
        throw new UnsupportedOperationException("'setBreakpoints' command not supported");
    }

    public CompletableFuture<SetFunctionBreakpointsResponse.ResponseBody> setFunctionBreakpoints(@SuppressWarnings("unused") SetFunctionBreakpointsArguments args) {
        throw new UnsupportedOperationException("'setFunctionBreakpoints' command not supported");
    }

    public CompletableFuture<Void> setExceptionBreakpoints(@SuppressWarnings("unused") SetExceptionBreakpointsArguments args) {
        throw new UnsupportedOperationException("'setExceptionBreakpoints' command not supported");
    }

    public CompletableFuture<DataBreakpointInfoResponse.ResponseBody> dataBreakpointInfo(@SuppressWarnings("unused") DataBreakpointInfoArguments args) {
        throw new UnsupportedOperationException("'dataBreakpointInfo' command not supported");
    }

    public CompletableFuture<SetDataBreakpointsResponse.ResponseBody> setDataBreakpoints(@SuppressWarnings("unused") SetDataBreakpointsArguments args) {
        throw new UnsupportedOperationException("'setDataBreakpoints' command not supported");
    }

    public CompletableFuture<ContinueResponse.ResponseBody> doContinue(@SuppressWarnings("unused") ContinueArguments args,
                    @SuppressWarnings("unused") Consumer<? super ContinueResponse.ResponseBody> response) {
        throw new UnsupportedOperationException("'doContinue' command not supported");
    }

    public CompletableFuture<Void> next(@SuppressWarnings("unused") NextArguments args, @SuppressWarnings("unused") Consumer<? super Void> responseConsumer) {
        throw new UnsupportedOperationException("'next' command not supported");
    }

    public CompletableFuture<Void> stepIn(@SuppressWarnings("unused") StepInArguments args, @SuppressWarnings("unused") Consumer<? super Void> responseConsumer) {
        throw new UnsupportedOperationException("'stepIn' command not supported");
    }

    public CompletableFuture<Void> stepOut(@SuppressWarnings("unused") StepOutArguments args, @SuppressWarnings("unused") Consumer<? super Void> responseConsumer) {
        throw new UnsupportedOperationException("'stepOut' command not supported");
    }

    public CompletableFuture<Void> stepBack(@SuppressWarnings("unused") StepBackArguments args, @SuppressWarnings("unused") Consumer<? super Void> responseConsumer) {
        throw new UnsupportedOperationException("'stepBack' command not supported");
    }

    public CompletableFuture<Void> reverseContinue(@SuppressWarnings("unused") ReverseContinueArguments args, @SuppressWarnings("unused") Consumer<? super Void> responseConsumer) {
        throw new UnsupportedOperationException("'reverseContinue' command not supported");
    }

    public CompletableFuture<Void> restartFrame(@SuppressWarnings("unused") RestartFrameArguments args, @SuppressWarnings("unused") Consumer<? super Void> responseConsumer) {
        throw new UnsupportedOperationException("'restartFrame' command not supported");
    }

    public CompletableFuture<Void> doGoto(@SuppressWarnings("unused") GotoArguments args, @SuppressWarnings("unused") Consumer<? super Void> responseConsumer) {
        throw new UnsupportedOperationException("'doGoto' command not supported");
    }

    public CompletableFuture<Void> pause(@SuppressWarnings("unused") PauseArguments args) {
        throw new UnsupportedOperationException("'pause' command not supported");
    }

    public CompletableFuture<StackTraceResponse.ResponseBody> stackTrace(@SuppressWarnings("unused") StackTraceArguments args) {
        throw new UnsupportedOperationException("'stackTrace' command not supported");
    }

    public CompletableFuture<ScopesResponse.ResponseBody> scopes(@SuppressWarnings("unused") ScopesArguments args) {
        throw new UnsupportedOperationException("'scopes' command not supported");
    }

    public CompletableFuture<VariablesResponse.ResponseBody> variables(@SuppressWarnings("unused") VariablesArguments args) {
        throw new UnsupportedOperationException("'variables' command not supported");
    }

    public CompletableFuture<SetVariableResponse.ResponseBody> setVariable(@SuppressWarnings("unused") SetVariableArguments args) {
        throw new UnsupportedOperationException("'setVariable' command not supported");
    }

    public CompletableFuture<SourceResponse.ResponseBody> source(@SuppressWarnings("unused") SourceArguments args) {
        throw new UnsupportedOperationException("'source' command not supported");
    }

    public CompletableFuture<ThreadsResponse.ResponseBody> threads() {
        throw new UnsupportedOperationException("'threads' command not supported");
    }

    public CompletableFuture<Void> terminateThreads(@SuppressWarnings("unused") TerminateThreadsArguments args) {
        throw new UnsupportedOperationException("'terminateThreads' command not supported");
    }

    public CompletableFuture<ModulesResponse.ResponseBody> modules(@SuppressWarnings("unused") ModulesArguments args) {
        throw new UnsupportedOperationException("'modules' command not supported");
    }

    public CompletableFuture<LoadedSourcesResponse.ResponseBody> loadedSources(@SuppressWarnings("unused") LoadedSourcesArguments args) {
        throw new UnsupportedOperationException("'loadedSources' command not supported");
    }

    public CompletableFuture<EvaluateResponse.ResponseBody> evaluate(@SuppressWarnings("unused") EvaluateArguments args) {
        throw new UnsupportedOperationException("'evaluate' command not supported");
    }

    public CompletableFuture<SetExpressionResponse.ResponseBody> setExpression(@SuppressWarnings("unused") SetExpressionArguments args) {
        throw new UnsupportedOperationException("'setExpression' command not supported");
    }

    public CompletableFuture<StepInTargetsResponse.ResponseBody> stepInTargets(@SuppressWarnings("unused") StepInTargetsArguments args) {
        throw new UnsupportedOperationException("'stepInTargets' command not supported");
    }

    public CompletableFuture<GotoTargetsResponse.ResponseBody> gotoTargets(@SuppressWarnings("unused") GotoTargetsArguments args) {
        throw new UnsupportedOperationException("'gotoTargets' command not supported");
    }

    public CompletableFuture<CompletionsResponse.ResponseBody> completions(@SuppressWarnings("unused") CompletionsArguments args) {
        throw new UnsupportedOperationException("'completions' command not supported");
    }

    public CompletableFuture<ExceptionInfoResponse.ResponseBody> exceptionInfo(@SuppressWarnings("unused") ExceptionInfoArguments args) {
        throw new UnsupportedOperationException("'exceptionInfo' command not supported");
    }

    public CompletableFuture<ReadMemoryResponse.ResponseBody> readMemory(@SuppressWarnings("unused") ReadMemoryArguments args) {
        throw new UnsupportedOperationException("'readMemory' command not supported");
    }

    public CompletableFuture<DisassembleResponse.ResponseBody> disassemble(@SuppressWarnings("unused") DisassembleArguments args) {
        throw new UnsupportedOperationException("'disassemble' command not supported");
    }

    // infrastructure methods
    protected void connect(@SuppressWarnings("unused") DebugProtocolClient client) {
    }

    public LoggerProxy getLogger() {
        Logger l = Logger.getLogger(DebugProtocolServer.class.getName());
        return new LoggerProxy() {
            @Override
            public boolean isLoggable(Level level) {
                return l.isLoggable(level);
            }

            @Override
            public void log(Level level, String msg) {
                l.log(level, msg);
            }

            @Override
            public void log(Level level, String msg, Throwable thrown) {
                l.log(level, msg, thrown);
            }
        };
    }

    public static final class Session implements Runnable {

        private static final String CONTENT_LENGTH_HEADER = "Content-Length:";
        private final DebugProtocolServer server;
        private final InputStream in;
        private final OutputStream out;
        private final Map<Integer, CompletableFuture<Response>> pendingSentRequests = new ConcurrentHashMap<>();
        private AtomicInteger sequenceNum = new AtomicInteger(1);
        private boolean closed = false;

        private Session(DebugProtocolServer server, InputStream in, OutputStream out) {
            this.server = server;
            this.in = in;
            this.out = out;
            this.server.connect(new DebugProtocolClient() {
                @Override
                public void initialized() {
                    sendEvent(InitializedEvent.create(sequenceNum.getAndIncrement()));
                }

                @Override
                public void stopped(StoppedEvent.EventBody body) {
                    sendEvent(StoppedEvent.create(body, sequenceNum.getAndIncrement()));
                }

                @Override
                public void continued(ContinuedEvent.EventBody body) {
                    sendEvent(ContinuedEvent.create(body, sequenceNum.getAndIncrement()));
                }

                @Override
                public void exited(ExitedEvent.EventBody body) {
                    sendEvent(ExitedEvent.create(body, sequenceNum.getAndIncrement()));
                }

                @Override
                public void terminated(TerminatedEvent.EventBody body) {
                    sendEvent(TerminatedEvent.create(sequenceNum.getAndIncrement()).setBody(body));
                }

                @Override
                public void thread(ThreadEvent.EventBody body) {
                    sendEvent(ThreadEvent.create(body, sequenceNum.getAndIncrement()));
                }

                @Override
                public void output(OutputEvent.EventBody body) {
                    sendEvent(OutputEvent.create(body, sequenceNum.getAndIncrement()));
                }

                @Override
                public void breakpoint(BreakpointEvent.EventBody body) {
                    sendEvent(BreakpointEvent.create(body, sequenceNum.getAndIncrement()));
                }

                @Override
                public void module(ModuleEvent.EventBody body) {
                    sendEvent(ModuleEvent.create(body, sequenceNum.getAndIncrement()));
                }

                @Override
                public void loadedSource(LoadedSourceEvent.EventBody body) {
                    sendEvent(LoadedSourceEvent.create(body, sequenceNum.getAndIncrement()));
                }

                @Override
                public void process(ProcessEvent.EventBody body) {
                    sendEvent(ProcessEvent.create(body, sequenceNum.getAndIncrement()));
                }

                @Override
                public void capabilities(CapabilitiesEvent.EventBody body) {
                    sendEvent(CapabilitiesEvent.create(body, sequenceNum.getAndIncrement()));
                }

                @Override
                public void progressStart(ProgressStartEvent.EventBody body) {
                    sendEvent(ProgressStartEvent.create(body, sequenceNum.getAndIncrement()));
                }

                @Override
                public void progressUpdate(ProgressUpdateEvent.EventBody body) {
                    sendEvent(ProgressUpdateEvent.create(body, sequenceNum.getAndIncrement()));
                }

                @Override
                public void progressEnd(ProgressEndEvent.EventBody body) {
                    sendEvent(ProgressEndEvent.create(body, sequenceNum.getAndIncrement()));
                }

                @Override
                public CompletableFuture<RunInTerminalResponse> runInTerminal(RunInTerminalRequestArguments args) {
                    return sendRequest(Request.create("runInTerminal", sequenceNum.getAndIncrement()).setArguments(getJSONData(args))).thenApply(response -> (RunInTerminalResponse) response);
                }
            });
        }

        @Override
        public void run() {
            try {
                while (!closed) {
                    byte[] messageBytes = readMessageBytes(in, server.getLogger());
                    if (messageBytes == null) {
                        closed = true;
                    } else {
                        processMessage(messageBytes);
                    }
                }
            } catch (IOException ioe) {
            }
        }

        // Message bytes, or null on EOF
        private static byte[] readMessageBytes(InputStream in, LoggerProxy logger) throws IOException {
            StringBuilder line = new StringBuilder();
            int contentLength = -1;
            while (true) {
                int c = in.read();
                if (c == -1) {
                    // End of input stream
                    return null;
                } else if (c == '\n') {
                    String header = line.toString().trim();
                    if (header.length() > 0) {
                        if (header.startsWith(CONTENT_LENGTH_HEADER)) {
                            try {
                                contentLength = Integer.parseInt(header.substring(CONTENT_LENGTH_HEADER.length()).trim());
                            } catch (NumberFormatException nfe) {
                            }
                        }
                    } else {
                        // Two consecutive newlines start the message content
                        if (contentLength < 0) {
                            logger.log(Level.SEVERE, "Error while processing an incomming message: Missing header " + CONTENT_LENGTH_HEADER + " in input.");
                        } else {
                            // Read the message
                            byte[] buffer = new byte[contentLength];
                            int bytesRead = 0;
                            while (bytesRead < contentLength) {
                                int read = in.read(buffer, bytesRead, contentLength - bytesRead);
                                if (read == -1) {
                                    return null;
                                }
                                bytesRead += read;
                            }
                            return buffer;
                        }
                    }
                    line = new StringBuilder();
                } else if (c != '\r') {
                    line.append((char) c);
                }
            }
        }

        private void processMessage(byte[] messageBytes) {
            try {
                final String content = new String(messageBytes, StandardCharsets.UTF_8);
                final ProtocolMessage message = new ProtocolMessage(new JSONObject(content));
                final String messageType = message.getType();
                switch (messageType) {
                    case "request":
                        final Request request = new Request(message.jsonData);
                        if (server.getLogger().isLoggable(Level.FINER)) {
                            String format = "[Trace - %s] Received request '%s - (%d)'\nArgs: %s";
                            server.getLogger().log(Level.FINER,
                                            format(format, Instant.now().toString(), request.getCommand(), request.getSeq(), getJSONData(request.getArguments())));
                        }
                        processRequest(request);
                        break;
                    case "response":
                        final Response response = new Response(message.jsonData);
                        if (server.getLogger().isLoggable(Level.FINER)) {
                            String format = "[Trace - %s] Received response '(%d)'\nResult: %s";
                            server.getLogger().log(Level.FINER, format(format, Instant.now().toString(), response.getRequestSeq(), getJSONData(response.getBody())));
                        }
                        processResponse(response);
                        break;
                    default:
                        sendErrorResponse((ErrorResponse) ErrorResponse.create(ErrorResponse.ResponseBody.create().setError(
                                        Message.create(1014, "Unrecognized message type: {_type}").setVariables(Collections.singletonMap("_type", messageType))),
                                        message.getSeq(), false, null, sequenceNum.getAndIncrement()).setMessage(format("Unrecognized message type: `%s`", messageType)));
                }
            } catch (Exception e) {
                server.getLogger().log(Level.SEVERE, "Error while processing an incomming message: " + e.getMessage());
            }
        }

        private void processRequest(Request request) {
            final int seq = request.getSeq();
            final String command = request.getCommand();
            try {
                final JSONObject args = request.getArguments() instanceof JSONObject ? (JSONObject) request.getArguments() : null;
                CompletableFuture<?> future = null;
                switch (command) {
                    case "cancel":
                        future = server.cancel(new CancelArguments(args)).thenAccept(body -> {
                            sendResponse(CancelResponse.create(seq, true, command, sequenceNum.getAndIncrement()).setBody(body));
                        });
                        break;
                    case "initialize":
                        future = server.initialize(new InitializeRequestArguments(args)).thenAccept(body -> {
                            sendResponse(InitializeResponse.create(seq, true, command, sequenceNum.getAndIncrement()).setBody(body));
                        });
                        break;
                    case "configurationDone":
                        future = server.configurationDone(new ConfigurationDoneArguments(args)).thenAccept(body -> {
                            sendResponse(ConfigurationDoneResponse.create(seq, true, command, sequenceNum.getAndIncrement()).setBody(body));
                        });
                        break;
                    case "launch":
                        future = server.launch(new LaunchRequestArguments(args)).thenAccept(body -> {
                            sendResponse(LaunchResponse.create(seq, true, command, sequenceNum.getAndIncrement()).setBody(body));
                        });
                        break;
                    case "attach":
                        future = server.attach(new AttachRequestArguments(args)).thenAccept(body -> {
                            sendResponse(AttachResponse.create(seq, true, command, sequenceNum.getAndIncrement()).setBody(body));
                        });
                        break;
                    case "restart":
                        future = server.restart(new RestartArguments(args)).thenAccept(body -> {
                            sendResponse(RestartResponse.create(seq, true, command, sequenceNum.getAndIncrement()).setBody(body));
                        });
                        break;
                    case "disconnect":
                        future = server.disconnect(new DisconnectArguments(args), body -> {
                            sendResponse(DisconnectResponse.create(seq, true, command, sequenceNum.getAndIncrement()).setBody(body));
                        });
                        break;
                    case "terminate":
                        future = server.terminate(new TerminateArguments(args)).thenAccept(body -> {
                            sendResponse(TerminateResponse.create(seq, true, command, sequenceNum.getAndIncrement()).setBody(body));
                        });
                        break;
                    case "breakpointLocations":
                        future = server.breakpointLocations(new BreakpointLocationsArguments(args)).thenAccept(body -> {
                            sendResponse(BreakpointLocationsResponse.create(body, seq, true, command, sequenceNum.getAndIncrement()));
                        });
                        break;
                    case "setBreakpoints":
                        future = server.setBreakpoints(new SetBreakpointsArguments(args)).thenAccept(body -> {
                            sendResponse(SetBreakpointsResponse.create(body, seq, true, command, sequenceNum.getAndIncrement()));
                        });
                        break;
                    case "setFunctionBreakpoints":
                        future = server.setFunctionBreakpoints(new SetFunctionBreakpointsArguments(args)).thenAccept(body -> {
                            sendResponse(SetFunctionBreakpointsResponse.create(body, seq, true, command, sequenceNum.getAndIncrement()));
                        });
                        break;
                    case "setExceptionBreakpoints":
                        future = server.setExceptionBreakpoints(new SetExceptionBreakpointsArguments(args)).thenAccept(body -> {
                            sendResponse(SetExceptionBreakpointsResponse.create(seq, true, command, sequenceNum.getAndIncrement()).setBody(body));
                        });
                        break;
                    case "dataBreakpointInfo":
                        future = server.dataBreakpointInfo(new DataBreakpointInfoArguments(args)).thenAccept(body -> {
                            sendResponse(DataBreakpointInfoResponse.create(body, seq, true, command, sequenceNum.getAndIncrement()));
                        });
                        break;
                    case "setDataBreakpoints":
                        future = server.setDataBreakpoints(new SetDataBreakpointsArguments(args)).thenAccept(body -> {
                            sendResponse(SetDataBreakpointsResponse.create(body, seq, true, command, sequenceNum.getAndIncrement()));
                        });
                        break;
                    case "continue":
                        future = server.doContinue(new ContinueArguments(args), body -> {
                            sendResponse(ContinueResponse.create(body, seq, true, command, sequenceNum.getAndIncrement()));
                        });
                        break;
                    case "next":
                        future = server.next(new NextArguments(args), body -> {
                            sendResponse(NextResponse.create(seq, true, command, sequenceNum.getAndIncrement()).setBody(body));
                        });
                        break;
                    case "stepIn":
                        future = server.stepIn(new StepInArguments(args), body -> {
                            sendResponse(StepInResponse.create(seq, true, command, sequenceNum.getAndIncrement()).setBody(body));
                        });
                        break;
                    case "stepOut":
                        future = server.stepOut(new StepOutArguments(args), body -> {
                            sendResponse(StepOutResponse.create(seq, true, command, sequenceNum.getAndIncrement()).setBody(body));
                        });
                        break;
                    case "stepBack":
                        future = server.stepBack(new StepBackArguments(args), body -> {
                            sendResponse(StepBackResponse.create(seq, true, command, sequenceNum.getAndIncrement()).setBody(body));
                        });
                        break;
                    case "reverseContinue":
                        future = server.reverseContinue(new ReverseContinueArguments(args), body -> {
                            sendResponse(ReverseContinueResponse.create(seq, true, command, sequenceNum.getAndIncrement()).setBody(body));
                        });
                        break;
                    case "restartFrame":
                        future = server.restartFrame(new RestartFrameArguments(args), body -> {
                            sendResponse(RestartFrameResponse.create(seq, true, command, sequenceNum.getAndIncrement()).setBody(body));
                        });
                        break;
                    case "goto":
                        future = server.doGoto(new GotoArguments(args), body -> {
                            sendResponse(GotoResponse.create(seq, true, command, sequenceNum.getAndIncrement()).setBody(body));
                        });
                        break;
                    case "pause":
                        future = server.pause(new PauseArguments(args)).thenAccept(body -> {
                            sendResponse(PauseResponse.create(seq, true, command, sequenceNum.getAndIncrement()).setBody(body));
                        });
                        break;
                    case "stackTrace":
                        future = server.stackTrace(new StackTraceArguments(args)).thenAccept(body -> {
                            sendResponse(StackTraceResponse.create(body, seq, true, command, sequenceNum.getAndIncrement()));
                        });
                        break;
                    case "scopes":
                        future = server.scopes(new ScopesArguments(args)).thenAccept(body -> {
                            sendResponse(ScopesResponse.create(body, seq, true, command, sequenceNum.getAndIncrement()));
                        });
                        break;
                    case "variables":
                        future = server.variables(new VariablesArguments(args)).thenAccept(body -> {
                            sendResponse(VariablesResponse.create(body, seq, true, command, sequenceNum.getAndIncrement()));
                        });
                        break;
                    case "setVariable":
                        future = server.setVariable(new SetVariableArguments(args)).thenAccept(body -> {
                            sendResponse(SetVariableResponse.create(body, seq, true, command, sequenceNum.getAndIncrement()));
                        });
                        break;
                    case "source":
                        future = server.source(new SourceArguments(args)).thenAccept(body -> {
                            sendResponse(SourceResponse.create(body, seq, true, command, sequenceNum.getAndIncrement()));
                        });
                        break;
                    case "threads":
                        future = server.threads().thenAccept(body -> {
                            sendResponse(ThreadsResponse.create(body, seq, true, command, sequenceNum.getAndIncrement()));
                        });
                        break;
                    case "terminateThreads":
                        future = server.terminateThreads(new TerminateThreadsArguments(args)).thenAccept(body -> {
                            sendResponse(TerminateThreadsResponse.create(seq, true, command, sequenceNum.getAndIncrement()).setBody(body));
                        });
                        break;
                    case "modules":
                        future = server.modules(new ModulesArguments(args)).thenAccept(body -> {
                            sendResponse(ModulesResponse.create(body, seq, true, command, sequenceNum.getAndIncrement()));
                        });
                        break;
                    case "loadedSources":
                        future = server.loadedSources(new LoadedSourcesArguments(args)).thenAccept(body -> {
                            sendResponse(LoadedSourcesResponse.create(body, seq, true, command, sequenceNum.getAndIncrement()));
                        });
                        break;
                    case "evaluate":
                        future = server.evaluate(new EvaluateArguments(args)).thenAccept(body -> {
                            sendResponse(EvaluateResponse.create(body, seq, true, command, sequenceNum.getAndIncrement()));
                        });
                        break;
                    case "setExpression":
                        future = server.setExpression(new SetExpressionArguments(args)).thenAccept(body -> {
                            sendResponse(SetExpressionResponse.create(body, seq, true, command, sequenceNum.getAndIncrement()));
                        });
                        break;
                    case "stepInTargets":
                        future = server.stepInTargets(new StepInTargetsArguments(args)).thenAccept(body -> {
                            sendResponse(StepInTargetsResponse.create(body, seq, true, command, sequenceNum.getAndIncrement()));
                        });
                        break;
                    case "gotoTargets":
                        future = server.gotoTargets(new GotoTargetsArguments(args)).thenAccept(body -> {
                            sendResponse(GotoTargetsResponse.create(body, seq, true, command, sequenceNum.getAndIncrement()));
                        });
                        break;
                    case "completions":
                        future = server.completions(new CompletionsArguments(args)).thenAccept(body -> {
                            sendResponse(CompletionsResponse.create(body, seq, true, command, sequenceNum.getAndIncrement()));
                        });
                        break;
                    case "exceptionInfo":
                        future = server.exceptionInfo(new ExceptionInfoArguments(args)).thenAccept(body -> {
                            sendResponse(ExceptionInfoResponse.create(body, seq, true, command, sequenceNum.getAndIncrement()));
                        });
                        break;
                    case "readMemory":
                        future = server.readMemory(new ReadMemoryArguments(args)).thenAccept(body -> {
                            sendResponse(ReadMemoryResponse.create(seq, true, command, sequenceNum.getAndIncrement()).setBody(body));
                        });
                        break;
                    case "disassemble":
                        future = server.disassemble(new DisassembleArguments(args)).thenAccept(body -> {
                            sendResponse(DisassembleResponse.create(seq, true, command, sequenceNum.getAndIncrement()).setBody(body));
                        });
                        break;
                    default:
                        sendErrorResponse((ErrorResponse) ErrorResponse.create(ErrorResponse.ResponseBody.create().setError(
                                        Message.create(1014, "Unrecognized command: {_cmd}").setVariables(Collections.singletonMap("_cmd", command))),
                                        seq, false, command, sequenceNum.getAndIncrement()).setMessage(format("Unrecognized command: `%s`", command)));
                }
                if (future != null) {
                    future.exceptionally(throwable -> {
                        if (isCancellationException(throwable)) {
                            sendErrorResponse((ErrorResponse) ErrorResponse.create(ErrorResponse.ResponseBody.create(), seq, false, command, sequenceNum.getAndIncrement()).setMessage("cancelled"));
                        } else if (isExceptionWithMessage(throwable)) {
                            sendErrorResponse((ErrorResponse) ErrorResponse.create(ErrorResponse.ResponseBody.create().setError(
                                            asExceptionWithMessage(throwable).getDebugMessage()), seq, false, command, sequenceNum.getAndIncrement()).setMessage(throwable.getCause().getMessage()));
                        } else {
                            final String msg = throwable.getMessage() != null ? throwable.getMessage() : "";
                            server.getLogger().log(Level.SEVERE, msg, throwable);
                            sendErrorResponse((ErrorResponse) ErrorResponse.create(ErrorResponse.ResponseBody.create().setError(
                                            Message.create(1104, "Internal Error: {_err}").setVariables(Collections.singletonMap("_err", msg))),
                                            seq, false, command, sequenceNum.getAndIncrement()).setMessage(format("Internal Error: `%s`", msg)));
                        }
                        return null;
                    });
                }
            } catch (ExceptionWithMessage ewm) {
                sendErrorResponse((ErrorResponse) ErrorResponse.create(ErrorResponse.ResponseBody.create().setError(
                                ewm.getDebugMessage()), seq, false, command, sequenceNum.getAndIncrement()).setMessage(ewm.getMessage()));
            } catch (Exception e) {
                final String msg = e.getMessage() != null ? e.getMessage() : "";
                server.getLogger().log(Level.SEVERE, msg, e);
                sendErrorResponse((ErrorResponse) ErrorResponse.create(ErrorResponse.ResponseBody.create().setError(
                                Message.create(1104, "Internal Error: {_err}").setVariables(Collections.singletonMap("_err", msg))),
                                seq, false, command, sequenceNum.getAndIncrement()).setMessage(format("Internal Error: `%s`", msg)));
            }
        }

        private void processResponse(Response response) {
            final CompletableFuture<Response> future = pendingSentRequests.remove(response.getRequestSeq());
            if (future != null) {
                try {
                    final String command = response.getCommand();
                    switch (command) {
                        case "runInTerminal":
                            future.complete(new RunInTerminalResponse(response.jsonData));
                            break;
                        default:
                            future.completeExceptionally(new RuntimeException(format("Unrecognized command: `%s`", command)));
                    }
                } catch (Exception e) {
                    server.getLogger().log(Level.SEVERE, e.getMessage(), e);
                    future.completeExceptionally(e);
                }
            }
        }

        private CompletableFuture<Response> sendRequest(Request request) {
            CompletableFuture<Response> future = new CompletableFuture<>();
            if (server.getLogger().isLoggable(Level.FINER)) {
                String format = "[Trace - %s] Sending request '%s - (%d)'\nArgs: %s";
                server.getLogger().log(Level.FINER, format(format, Instant.now().toString(), request.getCommand(), request.getSeq(), getJSONData(request.getArguments())));
            }
            writeMessage(getJSONData(request).toString());
            pendingSentRequests.put(request.getSeq(), future);
            return future;
        }

        private void sendResponse(Response response) {
            if (server.getLogger().isLoggable(Level.FINER)) {
                String format = "[Trace - %s] Sending response '(%d)'\nResult: %s";
                server.getLogger().log(Level.FINER, format(format, Instant.now().toString(), response.getRequestSeq(), getJSONData(response.getBody())));
            }
            writeMessage(getJSONData(response).toString());
        }

        private void sendErrorResponse(ErrorResponse response) {
            if (server.getLogger().isLoggable(Level.FINER)) {
                String format = "[Trace - %s] Sending error response '(%d)'\nError: %s";
                server.getLogger().log(Level.FINER, format(format, Instant.now().toString(), response.getRequestSeq(), getJSONData(response.getBody().getError())));
            }
            writeMessage(getJSONData(response).toString());

        }

        private void sendEvent(Event event) {
            if (server.getLogger().isLoggable(Level.FINER)) {
                String format = "[Trace - %s] Sending event '%s'\nBody: %s";
                server.getLogger().log(Level.FINER, format(format, Instant.now().toString(), event.getEvent(), getJSONData(event.getBody())));
            }
            writeMessage(getJSONData(event).toString());
        }

        private void writeMessage(String message) {
            try {
                byte[] messageBytes = message.getBytes(StandardCharsets.UTF_8);
                writeMessageBytes(out, messageBytes);
            } catch (IOException ex) {
                server.getLogger().log(Level.SEVERE, ex.getMessage(), ex);
                throw new RuntimeException(ex);
            }
        }

        private static void writeMessageBytes(OutputStream out, byte[] messageBytes) throws IOException {
            int contentLength = messageBytes.length;
            String header = format("Content-Length: %d\r\n\r\n", contentLength);
            byte[] headerBytes = header.getBytes(StandardCharsets.US_ASCII);
            synchronized (out) {
                out.write(headerBytes);
                out.write(messageBytes);
                out.flush();
            }
        }

        private static Object getJSONData(Object object) {
            if (object instanceof List) {
                final JSONArray json = new JSONArray();
                for (Object obj : (List<?>) object) {
                    json.put(getJSONData(obj));
                }
                return json;
            } else if (object instanceof JSONBase) {
                return ((JSONBase) object).jsonData;
            }
            return object;
        }

        private boolean isCancellationException(Throwable t) {
            return t instanceof CompletionException ? isCancellationException(t.getCause()) : t instanceof CancellationException;
        }

        private boolean isExceptionWithMessage(Throwable t) {
            return t instanceof CompletionException ? isExceptionWithMessage(t.getCause()) : t instanceof ExceptionWithMessage;
        }

        private ExceptionWithMessage asExceptionWithMessage(Throwable t) {
            return t instanceof CompletionException ? asExceptionWithMessage(t.getCause()) : t instanceof ExceptionWithMessage ? (ExceptionWithMessage) t : null;
        }

        public static Future<?> connect(DebugProtocolServer server, InputStream in, OutputStream out, ExecutorService executors) {
            Session s = new Session(server, in, out);
            return executors.submit(s);
        }
    }

    @SuppressWarnings("serial")
    public static class ExceptionWithMessage extends RuntimeException {

        private static final long serialVersionUID = 4950848492025420535L;

        private final Message debugMessage;

        public ExceptionWithMessage(Message debugMessage, String message) {
            super(message);
            this.debugMessage = debugMessage;
        }

        public Message getDebugMessage() {
            return debugMessage;
        }
    }

    public interface LoggerProxy {

        boolean isLoggable(Level level);

        void log(Level level, String msg);

        void log(Level level, String msg, Throwable thrown);
    }

    private static String format(String format, Object... args) {
        return String.format(Locale.ENGLISH, format, args);
    }
}
