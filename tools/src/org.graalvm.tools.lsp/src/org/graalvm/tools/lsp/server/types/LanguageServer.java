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
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

public class LanguageServer {

    // General methods

    public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
        throw new UnsupportedOperationException();
    }

    public void initialized(InitializedParams params) {
    }

    public CompletableFuture<Object> shutdown() {
        throw new UnsupportedOperationException();
    }

    public void exit() {
    }

    // Workspace related methods

    public void didChangeWorkspaceFolders(DidChangeWorkspaceFoldersParams params) {
    }

    public void didChangeConfiguration(DidChangeConfigurationParams params) {
    }

    public void didChangeWatchedFiles(DidChangeWatchedFilesParams params) {
    }

    public CompletableFuture<List<? extends SymbolInformation>> symbol(WorkspaceSymbolParams params) {
        throw new UnsupportedOperationException();
    }

    public CompletableFuture<Object> executeCommand(ExecuteCommandParams params) {
        throw new UnsupportedOperationException();
    }

    // TextDocument related methods

    public void didOpen(DidOpenTextDocumentParams params) {
    }

    public void didChange(DidChangeTextDocumentParams params) {
    }

    public void willSave(WillSaveTextDocumentParams params) {
    }

    public CompletableFuture<List<? extends TextEdit>> willSaveWaitUntil(WillSaveTextDocumentParams params) {
        throw new UnsupportedOperationException();
    }

    public void didSave(DidSaveTextDocumentParams params) {
    }

    public void didClose(DidCloseTextDocumentParams params) {
    }

    public CompletableFuture<CompletionList> completion(CompletionParams params) {
        throw new UnsupportedOperationException();
    }

    public CompletableFuture<CompletionItem> resolveCompletion(CompletionItem params) {
        throw new UnsupportedOperationException();
    }

    public CompletableFuture<Hover> hover(TextDocumentPositionParams params) {
        throw new UnsupportedOperationException();
    }

    public CompletableFuture<SignatureHelp> signatureHelp(TextDocumentPositionParams params) {
        throw new UnsupportedOperationException();
    }

    public CompletableFuture<List<? extends Location>> declaration(TextDocumentPositionParams params) {
        throw new UnsupportedOperationException();
    }

    public CompletableFuture<List<? extends Location>> definition(TextDocumentPositionParams params) {
        throw new UnsupportedOperationException();
    }

    public CompletableFuture<List<? extends Location>> typeDefinition(TextDocumentPositionParams params) {
        throw new UnsupportedOperationException();
    }

    public CompletableFuture<List<? extends Location>> implementation(TextDocumentPositionParams params) {
        throw new UnsupportedOperationException();
    }

    public CompletableFuture<List<? extends Location>> references(ReferenceParams params) {
        throw new UnsupportedOperationException();
    }

    public CompletableFuture<List<? extends DocumentHighlight>> documentHighlight(TextDocumentPositionParams params) {
        throw new UnsupportedOperationException();
    }

    public CompletableFuture<List<? extends SymbolInformation>> documentSymbol(DocumentSymbolParams params) {
        throw new UnsupportedOperationException();
    }

    public CompletableFuture<List<? extends CodeAction>> codeAction(CodeActionParams params) {
        throw new UnsupportedOperationException();
    }

    public CompletableFuture<List<? extends CodeLens>> codeLens(CodeLensParams params) {
        throw new UnsupportedOperationException();
    }

    public CompletableFuture<CodeLens> resolveCodeLens(CodeLens unresolved) {
        throw new UnsupportedOperationException();
    }

    public CompletableFuture<List<? extends DocumentLink>> documentLink(DocumentLinkParams params) {
        throw new UnsupportedOperationException();
    }

    public CompletableFuture<DocumentLink> resolveDocumentLink(DocumentLink params) {
        throw new UnsupportedOperationException();
    }

    public CompletableFuture<List<ColorInformation>> documentColor(DocumentColorParams params) {
        throw new UnsupportedOperationException();
    }

    public CompletableFuture<List<ColorPresentation>> colorPresentation(ColorPresentationParams params) {
        throw new UnsupportedOperationException();
    }

    public CompletableFuture<List<? extends TextEdit>> formatting(DocumentFormattingParams params) {
        throw new UnsupportedOperationException();
    }

    public CompletableFuture<List<? extends TextEdit>> rangeFormatting(DocumentRangeFormattingParams params) {
        throw new UnsupportedOperationException();
    }

    public CompletableFuture<List<? extends TextEdit>> onTypeFormatting(DocumentOnTypeFormattingParams params) {
        throw new UnsupportedOperationException();
    }

    public CompletableFuture<WorkspaceEdit> rename(RenameParams params) {
        throw new UnsupportedOperationException();
    }

    public CompletableFuture<Range> prepareRename(TextDocumentPositionParams params) {
        throw new UnsupportedOperationException();
    }

    public CompletableFuture<List<? extends FoldingRange>> foldingRange(FoldingRangeParams params) {
        throw new UnsupportedOperationException();
    }

    // infrastructure methods

    public void connect(LanguageClient client) {
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

    public static final class Session implements Runnable {

        private static final String CONTENT_LENGTH_HEADER = "Content-Length:";
        private final LanguageServer server;
        private final InputStream in;
        private final OutputStream out;
        private Map<Object, CompletableFuture<?>> pendingReceivedRequests = new ConcurrentHashMap<>();
        private boolean closed = false;

        private Session(LanguageServer server, InputStream in, OutputStream out) {
            this.server = server;
            this.in = in;
            this.out = out;
            this.server.connect(new LanguageClient() {
                @Override
                public void showMessage(ShowMessageParams params) {
                    sendNotification("window/showMessage", params.jsonData);
                }

                @Override
                public CompletableFuture<MessageActionItem> showMessageRequest(ShowMessageRequestParams params) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public void logMessage(LogMessageParams params) {
                    sendNotification("window/logMessage", params.jsonData);
                }

                @Override
                public void event(Object object) {
                    sendNotification("telemetry/event", object);
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
                StringBuilder line = new StringBuilder();
                int contentLength = -1;
                while (!closed) {
                    int c = in.read();
                    if (c == -1) {
                        // End of input stream
                        closed = true;
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
                                server.getLogger().log(Level.SEVERE, "Error while processing an incomming message: Missing header " + CONTENT_LENGTH_HEADER + " in input.");
                            } else {
                                processMessage(contentLength);
                                contentLength = -1;
                            }
                        }
                        line = new StringBuilder();
                    } else if (c != '\r') {
                        line.append((char) c);
                    }
                }
            } catch (IOException ioe) {

            }
        }

        private void processMessage(int contentLength) {
            try {
                byte[] buffer = new byte[contentLength];
                int bytesRead = 0;
                while (bytesRead < contentLength) {
                    int read = in.read(buffer, bytesRead, contentLength - bytesRead);
                    if (read == -1) {
                        closed = true;
                        return;
                    }
                    bytesRead += read;
                }
                String content = new String(buffer, StandardCharsets.UTF_8);
                JSONObject json = new JSONObject(content);
                if (json.has("id")) {
                    RequestMessage message = new RequestMessage(json);
                    if (server.getLogger().isLoggable(Level.FINER)) {
                        String format = "[Trace - %s] Received request '%s - (%s)'\nParams: %s";
                        server.getLogger().log(Level.FINER, String.format(format, Instant.now().toString(), message.getMethod(), message.getId(), message.getJSONParams().toString()));
                    }
                    processRequest(message);
                } else {
                    NotificationMessage message = new NotificationMessage(json);
                    if (server.getLogger().isLoggable(Level.FINER)) {
                        String format = "[Trace - %s] Received notification '%s'\nParams: %s";
                        server.getLogger().log(Level.FINER, String.format(format, Instant.now().toString(), message.getMethod(), message.getJSONParams().toString()));
                    }
                    processNotification(message);
                }
            } catch (Exception e) {
                server.getLogger().log(Level.SEVERE, "Error while processing an incomming message: " + e.getMessage());
            }
        }

        private void processRequest(RequestMessage req) {
            final AtomicReference<Object> id = new AtomicReference<>();
            try {
                id.set(req.getId());
                JSONObject params = req.getJSONParams();
                CompletableFuture<?> future = null;
                switch (req.getMethod()) {
                    case "initialize":
                        future = server.initialize(new InitializeParams(params)).thenAccept((result) -> {
                            sendResponse(id.get(), result.jsonData);
                        });
                        break;
                    case "shutdown":
                        future = server.shutdown().thenAccept((result) -> {
                            sendResponse(id.get(), result);
                        });
                        break;
                    case "workspace/symbol":
                        future = server.symbol(new WorkspaceSymbolParams(params)).thenAccept((result) -> {
                            final JSONArray json = new JSONArray();
                            for (SymbolInformation si : result) {
                                json.put(si.jsonData);
                            }
                            sendResponse(id.get(), json);
                        });
                        break;
                    case "workspace/executeCommand":
                        future = server.executeCommand(new ExecuteCommandParams(params)).thenAccept((result) -> {
                            sendResponse(id.get(), result);
                        });
                        break;
                    case "textDocument/willSaveWaitUntil":
                        future = server.willSaveWaitUntil(new WillSaveTextDocumentParams(params)).thenAccept((result) -> {
                            final JSONArray json = new JSONArray();
                            for (TextEdit textEdit : result) {
                                json.put(textEdit.jsonData);
                            }
                            sendResponse(id.get(), json);
                        });
                        break;
                    case "textDocument/completion":
                        future = server.completion(new CompletionParams(params)).thenAccept((result) -> {
                            sendResponse(id.get(), result.jsonData);
                        });
                        break;
                    case "completionItem/resolve":
                        future = server.resolveCompletion(new CompletionItem(params)).thenAccept((result) -> {
                            sendResponse(id.get(), result.jsonData);
                        });
                        break;
                    case "textDocument/hover":
                        future = server.hover(new TextDocumentPositionParams(params)).thenAccept((result) -> {
                            sendResponse(id.get(), result.jsonData);
                        });
                        break;
                    case "textDocument/signatureHelp":
                        future = server.signatureHelp(new TextDocumentPositionParams(params)).thenAccept((result) -> {
                            sendResponse(id.get(), result.jsonData);
                        });
                        break;
                    case "textDocument/declaration":
                        future = server.declaration(new TextDocumentPositionParams(params)).thenAccept((result) -> {
                            final JSONArray json = new JSONArray();
                            for (Location location : result) {
                                json.put(location.jsonData);
                            }
                            sendResponse(id.get(), json);
                        });
                        break;
                    case "textDocument/definition":
                        future = server.definition(new TextDocumentPositionParams(params)).thenAccept((result) -> {
                            final JSONArray json = new JSONArray();
                            for (Location location : result) {
                                json.put(location.jsonData);
                            }
                            sendResponse(id.get(), json);
                        });
                        break;
                    case "textDocument/typeDefinition":
                        future = server.typeDefinition(new TextDocumentPositionParams(params)).thenAccept((result) -> {
                            final JSONArray json = new JSONArray();
                            for (Location location : result) {
                                json.put(location.jsonData);
                            }
                            sendResponse(id.get(), json);
                        });
                        break;
                    case "textDocument/implementation":
                        future = server.implementation(new TextDocumentPositionParams(params)).thenAccept((result) -> {
                            final JSONArray json = new JSONArray();
                            for (Location location : result) {
                                json.put(location.jsonData);
                            }
                            sendResponse(id.get(), json);
                        });
                        break;
                    case "textDocument/references":
                        future = server.references(new ReferenceParams(params)).thenAccept((result) -> {
                            final JSONArray json = new JSONArray();
                            for (Location location : result) {
                                json.put(location.jsonData);
                            }
                            sendResponse(id.get(), json);
                        });
                        break;
                    case "textDocument/documentHighlight":
                        future = server.documentHighlight(new TextDocumentPositionParams(params)).thenAccept((result) -> {
                            final JSONArray json = new JSONArray();
                            for (DocumentHighlight documentHighlight : result) {
                                json.put(documentHighlight.jsonData);
                            }
                            sendResponse(id.get(), json);
                        });
                        break;
                    case "textDocument/documentSymbol":
                        future = server.documentSymbol(new DocumentSymbolParams(params)).thenAccept((result) -> {
                            final JSONArray json = new JSONArray();
                            for (SymbolInformation symbolInformation : result) {
                                json.put(symbolInformation.jsonData);
                            }
                            sendResponse(id.get(), json);
                        });
                        break;
                    case "textDocument/codeAction":
                        future = server.codeAction(new CodeActionParams(params)).thenAccept((result) -> {
                            final JSONArray json = new JSONArray();
                            for (CodeAction codeAction : result) {
                                json.put(codeAction.jsonData);
                            }
                            sendResponse(id.get(), json);
                        });
                        break;
                    case "textDocument/codeLens":
                        future = server.codeLens(new CodeLensParams(params)).thenAccept((result) -> {
                            final JSONArray json = new JSONArray();
                            for (CodeLens codeLens : result) {
                                json.put(codeLens.jsonData);
                            }
                            sendResponse(id.get(), json);
                        });
                        break;
                    case "codeLens/resolve":
                        future = server.resolveCodeLens(new CodeLens(params)).thenAccept((result) -> {
                            sendResponse(id.get(), result.jsonData);
                        });
                        break;
                    case "textDocument/documentLink":
                        future = server.documentLink(new DocumentLinkParams(params)).thenAccept((result) -> {
                            final JSONArray json = new JSONArray();
                            for (DocumentLink documentLink : result) {
                                json.put(documentLink);
                            }
                            sendResponse(id.get(), json);
                        });
                        break;
                    case "documentLink/resolve":
                        future = server.resolveDocumentLink(new DocumentLink(params)).thenAccept((result) -> {
                            sendResponse(id.get(), result.jsonData);
                        });
                        break;
                    case "textDocument/documentColor":
                        future = server.documentColor(new DocumentColorParams(params)).thenAccept((result) -> {
                            final JSONArray json = new JSONArray();
                            for (ColorInformation colorInformation : result) {
                                json.put(colorInformation.jsonData);
                            }
                            sendResponse(id.get(), json);
                        });
                        break;
                    case "textDocument/colorPresentation":
                        future = server.colorPresentation(new ColorPresentationParams(params)).thenAccept((result) -> {
                            final JSONArray json = new JSONArray();
                            for (ColorPresentation colorPresentation : result) {
                                json.put(colorPresentation.jsonData);
                            }
                            sendResponse(id.get(), json);
                        });
                        break;
                    case "textDocument/formatting":
                        future = server.formatting(new DocumentFormattingParams(params)).thenAccept((result) -> {
                            final JSONArray json = new JSONArray();
                            for (TextEdit textEdit : result) {
                                json.put(textEdit.jsonData);
                            }
                            sendResponse(id.get(), json);
                        });
                        break;
                    case "textDocument/rangeFormatting":
                        future = server.rangeFormatting(new DocumentRangeFormattingParams(params)).thenAccept((result) -> {
                            final JSONArray json = new JSONArray();
                            for (TextEdit textEdit : result) {
                                json.put(textEdit.jsonData);
                            }
                            sendResponse(id.get(), json);
                        });
                        break;
                    case "textDocument/onTypeFormatting":
                        future = server.onTypeFormatting(new DocumentOnTypeFormattingParams(params)).thenAccept((result) -> {
                            final JSONArray json = new JSONArray();
                            for (TextEdit textEdit : result) {
                                json.put(textEdit.jsonData);
                            }
                            sendResponse(id.get(), json);
                        });
                        break;
                    case "textDocument/rename":
                        future = server.rename(new RenameParams(params)).thenAccept((result) -> {
                            sendResponse(id.get(), result.jsonData);
                        });
                        break;
                    case "textDocument/prepareRename":
                        future = server.prepareRename(new TextDocumentPositionParams(params)).thenAccept((result) -> {
                            sendResponse(id.get(), result.jsonData);
                        });
                        break;
                    case "textDocument/foldingRange":
                        future = server.foldingRange(new FoldingRangeParams(params)).thenAccept((result) -> {
                            final JSONArray json = new JSONArray();
                            for (FoldingRange foldingRange : result) {
                                json.put(foldingRange.jsonData);
                            }
                            sendResponse(id.get(), json);
                        });
                        break;
                    default:
                        sendErrorResponse(id.get(), ErrorCodes.InvalidRequest, String.format("Unexpected method `%s`", req.getMethod()));
                }
                if (future != null) {
                    pendingReceivedRequests.put(id.get(), future);
                    future.exceptionally((throwable) -> {
                        if (isCancel(throwable)) {
                            String msg = String.format("The request '%s - (%s)' has been cancelled", req.getMethod(), id.get());
                            sendErrorResponse(id.get(), ErrorCodes.RequestCancelled, msg);
                        } else {
                            sendErrorResponse(id.get(), ErrorCodes.InternalError, throwable.getMessage());
                        }
                        return null;
                    }).thenApply((obj) -> {
                        pendingReceivedRequests.remove(id.get());
                        return null;
                    });
                }
            } catch (Exception e) {
                server.getLogger().log(Level.SEVERE, e.getMessage(), e);
                if (id.get() != null) {
                    sendErrorResponse(id.get(), ErrorCodes.InternalError, e.getMessage());
                }
            }
        }

        private void processNotification(NotificationMessage msg) {
            try {
                JSONObject params = msg.getJSONParams();
                switch (msg.getMethod()) {
                    case "initialized":
                        server.initialized(new InitializedParams(params));
                        break;
                    case "exit":
                        server.exit();
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
            final JSONObject response = new JSONObject();
            response.put("jsonrpc", "2.0");
            response.put("id", id);
            response.put("result", result);
            if (server.getLogger().isLoggable(Level.FINER)) {
                String format = "[Trace - %s] Sending response '(%s)'\nResult: %s";
                server.getLogger().log(Level.FINER, String.format(format, Instant.now().toString(), id, result.toString()));
            }
            writeMessage(response.toString());
        }

        private void sendErrorResponse(Object id, ErrorCodes code, String message) {
            final JSONObject response = new JSONObject();
            response.put("jsonrpc", "2.0");
            response.put("id", id);
            final JSONObject error = new JSONObject();
            error.put("code", code.getIntValue());
            error.put("message", message);
            response.put("error", error);
            if (server.getLogger().isLoggable(Level.FINER)) {
                String format = "[Trace - %s] Sending error response '(%s)'\nError: %s";
                server.getLogger().log(Level.FINER, String.format(format, Instant.now().toString(), id, error.toString()));
            }
            writeMessage(response.toString());

        }

        private void sendNotification(String method, Object params) {
            final JSONObject notification = new JSONObject();
            notification.put("jsonrpc", "2.0");
            notification.put("method", method);
            notification.put("params", params);
            if (server.getLogger().isLoggable(Level.FINER)) {
                String format = "[Trace - %s] Sending notification '%s'\nParams: %s";
                server.getLogger().log(Level.FINER, String.format(format, Instant.now().toString(), method, params.toString()));
            }
            writeMessage(notification.toString());
        }

        private void writeMessage(String message) {
            try {
                byte[] messageBytes = message.getBytes(StandardCharsets.UTF_8);
                int contentLength = messageBytes.length;
                String header = String.format("Content-Length: %d\r\n\r\n", contentLength);
                byte[] headerBytes = header.getBytes(StandardCharsets.US_ASCII);
                synchronized (out) {
                    out.write(headerBytes);
                    out.write(messageBytes);
                    out.flush();
                }
            } catch (IOException ex) {
                server.getLogger().log(Level.SEVERE, ex.getMessage(), ex);
                throw new RuntimeException(ex);
            }
        }

        private boolean isCancel(Throwable t) {
            return t instanceof CompletionException ? isCancel(t.getCause()) : t instanceof CancellationException;
        }

        public static final Future<?> connect(LanguageServer server, InputStream in, OutputStream out, ExecutorService executors) {
            Session s = new Session(server, in, out);
            return executors.submit(s);
        }
    }

    public static interface LoggerProxy {

        boolean isLoggable(Level level);

        void log(Level level, String msg);

        void log(Level level, String msg, Throwable thrown);
    }
}
