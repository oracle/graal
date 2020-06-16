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
package org.graalvm.tools.lsp.server.types;

import com.oracle.truffle.tools.utils.json.JSONArray;
import com.oracle.truffle.tools.utils.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.graalvm.tools.lsp.server.DelegateServers;
import org.graalvm.tools.lsp.server.TruffleAdapter;

public class LanguageServer {

    // General methods
    public CompletableFuture<InitializeResult> initialize(@SuppressWarnings("unused") InitializeParams params) {
        throw new UnsupportedOperationException();
    }

    public void initialized(@SuppressWarnings("unused") InitializedParams params) {
    }

    public CompletableFuture<Object> shutdown() {
        throw new UnsupportedOperationException();
    }

    public void exit() {
    }

    // Window related methods
    public void cancelProgress(@SuppressWarnings("unused") WorkDoneProgressCancelParams params) {
    }

    // Workspace related methods
    public void didChangeWorkspaceFolders(@SuppressWarnings("unused") DidChangeWorkspaceFoldersParams params) {
    }

    public void didChangeConfiguration(@SuppressWarnings("unused") DidChangeConfigurationParams params) {
    }

    public void didChangeWatchedFiles(@SuppressWarnings("unused") DidChangeWatchedFilesParams params) {
    }

    public CompletableFuture<List<? extends SymbolInformation>> symbol(@SuppressWarnings("unused") WorkspaceSymbolParams params) {
        throw new UnsupportedOperationException();
    }

    public CompletableFuture<Object> executeCommand(@SuppressWarnings("unused") ExecuteCommandParams params) {
        throw new UnsupportedOperationException();
    }

    // TextDocument related methods
    public void didOpen(@SuppressWarnings("unused") DidOpenTextDocumentParams params) {
    }

    public void didChange(@SuppressWarnings("unused") DidChangeTextDocumentParams params) {
    }

    public void willSave(@SuppressWarnings("unused") WillSaveTextDocumentParams params) {
    }

    public CompletableFuture<List<? extends TextEdit>> willSaveWaitUntil(@SuppressWarnings("unused") WillSaveTextDocumentParams params) {
        throw new UnsupportedOperationException();
    }

    public void didSave(@SuppressWarnings("unused") DidSaveTextDocumentParams params) {
    }

    public void didClose(@SuppressWarnings("unused") DidCloseTextDocumentParams params) {
    }

    // Language features related methods
    public CompletableFuture<CompletionList> completion(@SuppressWarnings("unused") CompletionParams params) {
        throw new UnsupportedOperationException();
    }

    public CompletableFuture<CompletionItem> resolveCompletion(@SuppressWarnings("unused") CompletionItem params) {
        throw new UnsupportedOperationException();
    }

    public CompletableFuture<Hover> hover(@SuppressWarnings("unused") TextDocumentPositionParams params) {
        throw new UnsupportedOperationException();
    }

    public CompletableFuture<SignatureHelp> signatureHelp(@SuppressWarnings("unused") TextDocumentPositionParams params) {
        throw new UnsupportedOperationException();
    }

    public CompletableFuture<List<? extends Location>> declaration(@SuppressWarnings("unused") TextDocumentPositionParams params) {
        throw new UnsupportedOperationException();
    }

    public CompletableFuture<List<? extends Location>> definition(@SuppressWarnings("unused") TextDocumentPositionParams params) {
        throw new UnsupportedOperationException();
    }

    public CompletableFuture<List<? extends Location>> typeDefinition(@SuppressWarnings("unused") TextDocumentPositionParams params) {
        throw new UnsupportedOperationException();
    }

    public CompletableFuture<List<? extends Location>> implementation(@SuppressWarnings("unused") TextDocumentPositionParams params) {
        throw new UnsupportedOperationException();
    }

    public CompletableFuture<List<? extends Location>> references(@SuppressWarnings("unused") ReferenceParams params) {
        throw new UnsupportedOperationException();
    }

    public CompletableFuture<List<? extends DocumentHighlight>> documentHighlight(@SuppressWarnings("unused") TextDocumentPositionParams params) {
        throw new UnsupportedOperationException();
    }

    public CompletableFuture<List<? extends SymbolInformation>> documentSymbol(@SuppressWarnings("unused") DocumentSymbolParams params) {
        throw new UnsupportedOperationException();
    }

    public CompletableFuture<List<? extends CodeAction>> codeAction(@SuppressWarnings("unused") CodeActionParams params) {
        throw new UnsupportedOperationException();
    }

    public CompletableFuture<List<? extends CodeLens>> codeLens(@SuppressWarnings("unused") CodeLensParams params) {
        throw new UnsupportedOperationException();
    }

    public CompletableFuture<CodeLens> resolveCodeLens(@SuppressWarnings("unused") CodeLens unresolved) {
        throw new UnsupportedOperationException();
    }

    public CompletableFuture<List<? extends DocumentLink>> documentLink(@SuppressWarnings("unused") DocumentLinkParams params) {
        throw new UnsupportedOperationException();
    }

    public CompletableFuture<DocumentLink> resolveDocumentLink(@SuppressWarnings("unused") DocumentLink params) {
        throw new UnsupportedOperationException();
    }

    public CompletableFuture<List<ColorInformation>> documentColor(@SuppressWarnings("unused") DocumentColorParams params) {
        throw new UnsupportedOperationException();
    }

    public CompletableFuture<List<ColorPresentation>> colorPresentation(@SuppressWarnings("unused") ColorPresentationParams params) {
        throw new UnsupportedOperationException();
    }

    public CompletableFuture<List<? extends TextEdit>> formatting(@SuppressWarnings("unused") DocumentFormattingParams params) {
        throw new UnsupportedOperationException();
    }

    public CompletableFuture<List<? extends TextEdit>> rangeFormatting(@SuppressWarnings("unused") DocumentRangeFormattingParams params) {
        throw new UnsupportedOperationException();
    }

    public CompletableFuture<List<? extends TextEdit>> onTypeFormatting(@SuppressWarnings("unused") DocumentOnTypeFormattingParams params) {
        throw new UnsupportedOperationException();
    }

    public CompletableFuture<WorkspaceEdit> rename(@SuppressWarnings("unused") RenameParams params) {
        throw new UnsupportedOperationException();
    }

    public CompletableFuture<Range> prepareRename(@SuppressWarnings("unused") TextDocumentPositionParams params) {
        throw new UnsupportedOperationException();
    }

    public CompletableFuture<List<? extends FoldingRange>> foldingRange(@SuppressWarnings("unused") FoldingRangeParams params) {
        throw new UnsupportedOperationException();
    }

    public CompletableFuture<List<? extends SelectionRange>> selectionRange(@SuppressWarnings("unused") SelectionRangeParams params) {
        throw new UnsupportedOperationException();
    }

    // infrastructure methods
    protected void connect(@SuppressWarnings("unused") LanguageClient client) {
    }

    protected boolean supportsMethod(@SuppressWarnings("unused") String method, @SuppressWarnings("unused") JSONObject params) {
        return true;
    }

    public LoggerProxy getLogger() {
        Logger l = Logger.getLogger(LanguageServer.class.getName());
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

    public static final class DelegateServer implements Runnable {

        private final LoggerProxy logger;
        private final String languageId;
        private final Socket socket;
        private final InputStream in;
        private final OutputStream out;
        private final OutputStream serverOutput;
        private final TruffleAdapter truffleAdapter;
        private final Map<Object, JSONObject> receivedMessages = new HashMap<>();
        private Object initializeId;
        private ServerCapabilities capabilities;

        public DelegateServer(String languageId, SocketAddress socketAddress, OutputStream serverOutput, TruffleAdapter truffleAdapter, LoggerProxy logger) throws IOException {
            this.languageId = languageId;
            this.socket = new Socket();
            this.serverOutput = serverOutput;
            this.truffleAdapter = truffleAdapter;
            this.logger = logger;
            this.socket.connect(socketAddress);
            this.in = socket.getInputStream();
            this.out = socket.getOutputStream();
        }

        public String getLanguageId() {
            return languageId;
        }

        public String getAddress() {
            return socket.getRemoteSocketAddress().toString();
        }

        public void close() {
            try {
                socket.close();
            } catch (IOException ex) {
            }
        }

        private void addAwaitingId(Object id) {
            synchronized (receivedMessages) {
                receivedMessages.put(id, null);
            }
        }

        public JSONObject awaitMessage(Object id) throws InterruptedException {
            synchronized (receivedMessages) {
                JSONObject message = null;
                if (receivedMessages.containsKey(id)) {
                    message = receivedMessages.get(id);
                    while (message == null) {
                        receivedMessages.wait();
                        message = receivedMessages.get(id);
                    }
                    receivedMessages.remove(id);
                }
                return message;
            }
        }

        public void sendMessage(byte[] bytes, Object id, String method) throws IOException {
            if ("initialize".equals(method)) {
                initializeId = id;
            }
            Session.writeMessageBytes(out, bytes);
            if (id != null) {
                addAwaitingId(id);
            }
        }

        public ServerCapabilities getCapabilities() {
            return capabilities;
        }

        @Override
        public void run() {
            // TODO: merge all PublishDiagnostics Notification
            try {
                while (!socket.isClosed()) {
                    byte[] messageBytes = Session.readMessageBytes(in, logger);
                    if (messageBytes == null) {
                        break;
                    } else {
                        String content = new String(messageBytes, StandardCharsets.UTF_8);
                        JSONObject json = new JSONObject(content);
                        if (json.has("id")) {
                            Object id = json.get("id");
                            if (id.equals(initializeId) && json.has("result")) {
                                JSONObject result = (JSONObject) json.get("result");
                                if (result.has("capabilities")) {
                                    JSONObject c = (JSONObject) result.get("capabilities");
                                    this.capabilities = new ServerCapabilities(c);
                                    truffleAdapter.setServerCapabilities(languageId, capabilities);
                                }
                            }
                            synchronized (receivedMessages) {
                                if (receivedMessages.containsKey(id)) {
                                    receivedMessages.put(id, json);
                                    receivedMessages.notifyAll();
                                }
                            }
                        } else {
                            // A notification
                            Session.writeMessageBytes(serverOutput, messageBytes);
                        }
                    }
                }
            } catch (IOException ex) {
            }
        }

        @Override
        public String toString() {
            return "Delegate LS " + (languageId != null ? languageId : "") + "@" + getAddress();
        }

    }

    public static final class Session implements Runnable {

        private static final String CONTENT_LENGTH_HEADER = "Content-Length:";
        private final LanguageServer server;
        private final InputStream in;
        private final OutputStream out;
        private final DelegateServers delegateServers;
        private final Map<Object, CompletableFuture<?>> pendingReceivedRequests = new ConcurrentHashMap<>();
        private boolean closed = false;

        private Session(LanguageServer server, InputStream in, OutputStream out, DelegateServers delegateServers) {
            this.server = server;
            this.in = in;
            this.out = out;
            this.delegateServers = delegateServers;
            this.server.connect(new LanguageClient() {
                @Override
                public void showMessage(ShowMessageParams params) {
                    sendNotification("window/showMessage", params);
                }

                @Override
                public CompletableFuture<MessageActionItem> showMessageRequest(ShowMessageRequestParams params) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public void logMessage(LogMessageParams params) {
                    sendNotification("window/logMessage", params);
                }

                @Override
                public void event(Object object) {
                    sendNotification("telemetry/event", object);
                }

                @Override
                public CompletableFuture<Void> createProgress(WorkDoneProgressCreateParams params) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public CompletableFuture<Void> registerCapability(RegistrationParams params) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public CompletableFuture<Void> unregisterCapability(UnregistrationParams params) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public CompletableFuture<List<WorkspaceFolder>> workspaceFolders() {
                    throw new UnsupportedOperationException();
                }

                @Override
                public CompletableFuture<List<Object>> configuration(ConfigurationParams params) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public CompletableFuture<ApplyWorkspaceEditResponse> applyEdit(ApplyWorkspaceEditParams params) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public void publishDiagnostics(PublishDiagnosticsParams params) {
                    sendNotification("textDocument/publishDiagnostics", params);
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

            } finally {
                delegateServers.close();
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
                String content = new String(messageBytes, StandardCharsets.UTF_8);
                JSONObject json = new JSONObject(content);
                if (json.has("id")) {
                    RequestMessage message = new RequestMessage(json);
                    if (server.getLogger().isLoggable(Level.FINER)) {
                        String format = "[Trace - %s] Received request '%s - (%s)'\nParams: %s";
                        server.getLogger().log(Level.FINER, String.format(format, Instant.now().toString(), message.getMethod(), message.getId(), message.getParams().toString()));
                    }
                    processRequest(message, messageBytes);
                } else {
                    NotificationMessage message = new NotificationMessage(json);
                    if (server.getLogger().isLoggable(Level.FINER)) {
                        String format = "[Trace - %s] Received notification '%s'\nParams: %s";
                        server.getLogger().log(Level.FINER, String.format(format, Instant.now().toString(), message.getMethod(), message.getParams().toString()));
                    }
                    processNotification(message, messageBytes);
                }
            } catch (Exception e) {
                server.getLogger().log(Level.SEVERE, "Error while processing an incomming message: " + e.getMessage());
            }
        }

        private void processRequest(RequestMessage req, byte[] buffer) {
            final Object id = req.getId();
            try {
                JSONObject params = req.getParams() instanceof JSONObject ? (JSONObject) req.getParams() : null;
                CompletableFuture<?> future = null;
                String method = req.getMethod();
                delegateServers.sendMessageToDelegates(buffer, id, method, params);
                if (server.supportsMethod(method, params)) {
                    switch (method) {
                        case "initialize":
                            future = server.initialize(new InitializeParams(params)).thenAccept((result) -> {
                                sendResponse(id, result);
                            });
                            break;
                        case "shutdown":
                            future = server.shutdown().thenAccept((result) -> {
                                sendResponse(id, result);
                            });
                            break;
                        case "workspace/symbol":
                            future = server.symbol(new WorkspaceSymbolParams(params)).thenAccept((result) -> {
                                sendResponse(id, result);
                            });
                            break;
                        case "workspace/executeCommand":
                            future = server.executeCommand(new ExecuteCommandParams(params)).thenAccept((result) -> {
                                sendResponse(id, result);
                            });
                            break;
                        case "textDocument/willSaveWaitUntil":
                            future = server.willSaveWaitUntil(new WillSaveTextDocumentParams(params)).thenAccept((result) -> {
                                sendResponse(id, result);
                            });
                            break;
                        case "textDocument/completion":
                            future = server.completion(new CompletionParams(params)).thenAccept((result) -> {
                                sendResponse(id, result);
                            });
                            break;
                        case "completionItem/resolve":
                            future = server.resolveCompletion(new CompletionItem(params)).thenAccept((result) -> {
                                sendResponse(id, result);
                            });
                            break;
                        case "textDocument/hover":
                            future = server.hover(new HoverParams(params)).thenAccept((result) -> {
                                sendResponse(id, result);
                            });
                            break;
                        case "textDocument/signatureHelp":
                            future = server.signatureHelp(new SignatureHelpParams(params)).thenAccept((result) -> {
                                sendResponse(id, result);
                            });
                            break;
                        case "textDocument/declaration":
                            future = server.declaration(new DeclarationParams(params)).thenAccept((result) -> {
                                sendResponse(id, result);
                            });
                            break;
                        case "textDocument/definition":
                            future = server.definition(new DefinitionParams(params)).thenAccept((result) -> {
                                sendResponse(id, result);
                            });
                            break;
                        case "textDocument/typeDefinition":
                            future = server.typeDefinition(new TypeDefinitionParams(params)).thenAccept((result) -> {
                                sendResponse(id, result);
                            });
                            break;
                        case "textDocument/implementation":
                            future = server.implementation(new ImplementationParams(params)).thenAccept((result) -> {
                                sendResponse(id, result);
                            });
                            break;
                        case "textDocument/references":
                            future = server.references(new ReferenceParams(params)).thenAccept((result) -> {
                                sendResponse(id, result);
                            });
                            break;
                        case "textDocument/documentHighlight":
                            future = server.documentHighlight(new DocumentHighlightParams(params)).thenAccept((result) -> {
                                sendResponse(id, result);
                            });
                            break;
                        case "textDocument/documentSymbol":
                            future = server.documentSymbol(new DocumentSymbolParams(params)).thenAccept((result) -> {
                                sendResponse(id, result);
                            });
                            break;
                        case "textDocument/codeAction":
                            future = server.codeAction(new CodeActionParams(params)).thenAccept((result) -> {
                                sendResponse(id, result);
                            });
                            break;
                        case "textDocument/codeLens":
                            future = server.codeLens(new CodeLensParams(params)).thenAccept((result) -> {
                                sendResponse(id, result);
                            });
                            break;
                        case "codeLens/resolve":
                            future = server.resolveCodeLens(new CodeLens(params)).thenAccept((result) -> {
                                sendResponse(id, result);
                            });
                            break;
                        case "textDocument/documentLink":
                            future = server.documentLink(new DocumentLinkParams(params)).thenAccept((result) -> {
                                sendResponse(id, result);
                            });
                            break;
                        case "documentLink/resolve":
                            future = server.resolveDocumentLink(new DocumentLink(params)).thenAccept((result) -> {
                                sendResponse(id, result);
                            });
                            break;
                        case "textDocument/documentColor":
                            future = server.documentColor(new DocumentColorParams(params)).thenAccept((result) -> {
                                sendResponse(id, result);
                            });
                            break;
                        case "textDocument/colorPresentation":
                            future = server.colorPresentation(new ColorPresentationParams(params)).thenAccept((result) -> {
                                sendResponse(id, result);
                            });
                            break;
                        case "textDocument/formatting":
                            future = server.formatting(new DocumentFormattingParams(params)).thenAccept((result) -> {
                                sendResponse(id, result);
                            });
                            break;
                        case "textDocument/rangeFormatting":
                            future = server.rangeFormatting(new DocumentRangeFormattingParams(params)).thenAccept((result) -> {
                                sendResponse(id, result);
                            });
                            break;
                        case "textDocument/onTypeFormatting":
                            future = server.onTypeFormatting(new DocumentOnTypeFormattingParams(params)).thenAccept((result) -> {
                                sendResponse(id, result);
                            });
                            break;
                        case "textDocument/rename":
                            future = server.rename(new RenameParams(params)).thenAccept((result) -> {
                                sendResponse(id, result);
                            });
                            break;
                        case "textDocument/prepareRename":
                            future = server.prepareRename(new PrepareRenameParams(params)).thenAccept((result) -> {
                                sendResponse(id, result);
                            });
                            break;
                        case "textDocument/foldingRange":
                            future = server.foldingRange(new FoldingRangeParams(params)).thenAccept((result) -> {
                                sendResponse(id, result);
                            });
                            break;
                        case "textDocument/selectionRange":
                            future = server.selectionRange(new SelectionRangeParams(params)).thenAccept((result) -> {
                                sendResponse(id, result);
                            });
                            break;
                        default:
                            sendErrorResponse(id, ErrorCodes.InvalidRequest, String.format("Unexpected method `%s`", req.getMethod()));
                    }
                } else {
                    future = CompletableFuture.runAsync(() -> sendResponse(id, null));
                }
                if (future != null) {
                    pendingReceivedRequests.put(id, future);
                    future.exceptionally((throwable) -> {
                        if (isCancel(throwable)) {
                            String msg = String.format("The request '%s - (%s)' has been cancelled", req.getMethod(), id);
                            sendErrorResponse(id, ErrorCodes.RequestCancelled, msg);
                        } else {
                            sendErrorResponse(id, ErrorCodes.InternalError, throwable.getMessage());
                        }
                        return null;
                    }).thenApply((obj) -> {
                        pendingReceivedRequests.remove(id);
                        return null;
                    });
                }
            } catch (Exception e) {
                server.getLogger().log(Level.SEVERE, e.getMessage(), e);
                sendErrorResponse(id, ErrorCodes.InternalError, e.getMessage());
            }
        }

        private void processNotification(NotificationMessage msg, byte[] buffer) {
            try {
                JSONObject params = (JSONObject) msg.getParams();
                String method = msg.getMethod();
                delegateServers.sendMessageToDelegates(buffer, null, method, params);
                switch (method) {
                    case "initialized":
                        server.initialized(new InitializedParams(params));
                        break;
                    case "exit":
                        server.exit();
                        break;
                    case "window/workDoneProgress/cancel":
                        server.cancelProgress(new WorkDoneProgressCancelParams(params));
                        break;
                    case "workspace/didChangeWorkspaceFolders":
                        server.didChangeWorkspaceFolders(new DidChangeWorkspaceFoldersParams(params));
                        break;
                    case "workspace/didChangeConfiguration":
                        server.didChangeConfiguration(new DidChangeConfigurationParams(params));
                        break;
                    case "workspace/didChangeWatchedFiles":
                        server.didChangeWatchedFiles(new DidChangeWatchedFilesParams(params));
                        break;
                    case "textDocument/didOpen":
                        server.didOpen(new DidOpenTextDocumentParams(params));
                        break;
                    case "textDocument/didChange":
                        server.didChange(new DidChangeTextDocumentParams(params));
                        break;
                    case "textDocument/willSave":
                        server.willSave(new WillSaveTextDocumentParams(params));
                        break;
                    case "textDocument/didSave":
                        server.didSave(new DidSaveTextDocumentParams(params));
                        break;
                    case "textDocument/didClose":
                        server.didClose(new DidCloseTextDocumentParams(params));
                        break;
                    case "$/cancelRequest":
                        Object id = params.get("id");
                        CompletableFuture<?> pending = pendingReceivedRequests.get(id);
                        if (pending != null) {
                            pending.cancel(true);
                        }
                        break;
                    default:
                        server.getLogger().log(Level.WARNING, String.format("Unexpected method `%s`", msg.getMethod()));
                }
            } catch (Exception e) {
                server.getLogger().log(Level.SEVERE, e.getMessage(), e);
            }
        }

        private void sendResponse(Object id, Object result) {
            final ResponseMessage response = ResponseMessage.create(id, "2.0");
            Object allResults = delegateServers.mergeResults(id, getJSONData(result));
            response.setResult(allResults != null ? allResults : JSONObject.NULL);
            if (server.getLogger().isLoggable(Level.FINER)) {
                String format = "[Trace - %s] Sending response '(%s)'\nResult: %s";
                server.getLogger().log(Level.FINER, String.format(format, Instant.now().toString(), id, Objects.toString(allResults)));
            }
            writeMessage(getJSONData(response).toString());
        }

        private void sendErrorResponse(Object id, ErrorCodes code, String message) {
            final ResponseMessage response = ResponseMessage.create(id, "2.0");
            Object allResults = delegateServers.mergeResults(id, null);
            if (allResults != null) {
                // Provide other results and ignore our error
                response.setResult(allResults);
                if (server.getLogger().isLoggable(Level.FINER)) {
                    String format = "[Trace - %s] Sending response '(%s)'\nResult: %s";
                    server.getLogger().log(Level.FINER, String.format(format, Instant.now().toString(), id, allResults.toString()));
                }
            } else {
                final ResponseErrorLiteral error = ResponseErrorLiteral.create(code.getIntValue(), message);
                response.setError(error);
                if (server.getLogger().isLoggable(Level.FINER)) {
                    String format = "[Trace - %s] Sending error response '(%s)'\nError: %s";
                    server.getLogger().log(Level.FINER, String.format(format, Instant.now().toString(), id, getJSONData(error).toString()));
                }
            }
            writeMessage(getJSONData(response).toString());

        }

        private void sendNotification(String method, Object params) {
            final NotificationMessage notification = NotificationMessage.create(method, "2.0");
            notification.setParams(getJSONData(params));
            if (server.getLogger().isLoggable(Level.FINER)) {
                String format = "[Trace - %s] Sending notification '%s'\nParams: %s";
                server.getLogger().log(Level.FINER, String.format(format, Instant.now().toString(), method, getJSONData(params).toString()));
            }
            writeMessage(getJSONData(notification).toString());
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
            String header = String.format("Content-Length: %d\r\n\r\n", contentLength);
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

        private boolean isCancel(Throwable t) {
            return t instanceof CompletionException ? isCancel(t.getCause()) : t instanceof CancellationException;
        }

        public static Future<?> connect(LanguageServer server, InputStream in, OutputStream out, ExecutorService executors, DelegateServers delegateServers) {
            Session s = new Session(server, in, out, delegateServers);
            Future<?> sessionFuture = executors.submit(s);
            delegateServers.submitAll(executors);
            return sessionFuture;
        }
    }

    public interface LoggerProxy {

        boolean isLoggable(Level level);

        void log(Level level, String msg);

        void log(Level level, String msg, Throwable thrown);
    }
}
