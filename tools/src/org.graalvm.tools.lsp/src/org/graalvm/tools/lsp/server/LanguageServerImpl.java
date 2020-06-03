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
package org.graalvm.tools.lsp.server;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.function.Supplier;
import java.util.logging.Level;

import org.graalvm.collections.Pair;

import org.graalvm.tools.lsp.server.types.CodeAction;
import org.graalvm.tools.lsp.server.types.CodeActionParams;
import org.graalvm.tools.lsp.server.types.CodeLens;
import org.graalvm.tools.lsp.server.types.CodeLensOptions;
import org.graalvm.tools.lsp.server.types.CodeLensParams;
import org.graalvm.tools.lsp.server.types.CompletionItem;
import org.graalvm.tools.lsp.server.types.CompletionList;
import org.graalvm.tools.lsp.server.types.CompletionOptions;
import org.graalvm.tools.lsp.server.types.CompletionParams;
import org.graalvm.tools.lsp.server.types.Coverage;
import org.graalvm.tools.lsp.server.types.DidChangeTextDocumentParams;
import org.graalvm.tools.lsp.server.types.DidCloseTextDocumentParams;
import org.graalvm.tools.lsp.server.types.DidOpenTextDocumentParams;
import org.graalvm.tools.lsp.server.types.DidSaveTextDocumentParams;
import org.graalvm.tools.lsp.server.types.DocumentFormattingParams;
import org.graalvm.tools.lsp.server.types.DocumentHighlight;
import org.graalvm.tools.lsp.server.types.DocumentOnTypeFormattingParams;
import org.graalvm.tools.lsp.server.types.DocumentRangeFormattingParams;
import org.graalvm.tools.lsp.server.types.DocumentSymbolParams;
import org.graalvm.tools.lsp.server.types.ExecuteCommandOptions;
import org.graalvm.tools.lsp.server.types.ExecuteCommandParams;
import org.graalvm.tools.lsp.server.types.Hover;
import org.graalvm.tools.lsp.server.types.InitializeParams;
import org.graalvm.tools.lsp.server.types.InitializeResult;
import org.graalvm.tools.lsp.server.types.LanguageClient;
import org.graalvm.tools.lsp.server.types.LanguageServer;
import org.graalvm.tools.lsp.server.types.Location;
import org.graalvm.tools.lsp.server.types.ShowMessageParams;
import org.graalvm.tools.lsp.server.types.MessageType;
import org.graalvm.tools.lsp.server.types.PublishDiagnosticsParams;
import org.graalvm.tools.lsp.server.types.ReferenceParams;
import org.graalvm.tools.lsp.server.types.RenameParams;
import org.graalvm.tools.lsp.server.types.ServerCapabilities;
import org.graalvm.tools.lsp.server.types.SignatureHelp;
import org.graalvm.tools.lsp.server.types.SignatureHelpOptions;
import org.graalvm.tools.lsp.server.types.SymbolInformation;
import org.graalvm.tools.lsp.server.types.TextDocumentContentChangeEvent;
import org.graalvm.tools.lsp.server.types.TextDocumentPositionParams;
import org.graalvm.tools.lsp.server.types.TextDocumentSyncKind;
import org.graalvm.tools.lsp.server.types.TextEdit;
import org.graalvm.tools.lsp.server.types.WorkspaceEdit;
import org.graalvm.tools.lsp.server.types.WorkspaceFolder;
import org.graalvm.tools.lsp.server.types.WorkspaceSymbolParams;
import org.graalvm.tools.lsp.exceptions.DiagnosticsNotification;
import org.graalvm.tools.lsp.exceptions.UnknownLanguageException;

import com.oracle.truffle.tools.utils.json.JSONObject;

/**
 * A LSP4J {@link LanguageServer} implementation using TCP sockets as transportation layer for the
 * JSON-RPC requests. It delegates all requests to {@link TruffleAdapter}.
 */
public final class LanguageServerImpl extends LanguageServer {

    private static final String DRY_RUN = "dry_run";
    private static final String GET_COVERAGE = "get_coverage";
    private static final TextDocumentSyncKind TEXT_DOCUMENT_SYNC_KIND = TextDocumentSyncKind.Incremental;

    private final TruffleAdapter truffleAdapter;
    private final PrintWriter err;
    private final PrintWriter info;
    private LanguageClient client;
    private final Map<URI, String> openedFileUri2LangId = new HashMap<>();
    private ExecutorService clientConnectionExecutor;

    private final Hover emptyHover = Hover.create(Collections.emptyList());
    private final SignatureHelp emptySignatureHelp = SignatureHelp.create(Collections.emptyList(), null, null);
    private ServerCapabilities serverCapabilities;

    private LanguageServerImpl(TruffleAdapter adapter, PrintWriter info, PrintWriter err) {
        this.truffleAdapter = adapter;
        this.info = info;
        this.err = err;
    }

    public static LanguageServerImpl create(TruffleAdapter adapter, PrintWriter info, PrintWriter err) {
        return new LanguageServerImpl(adapter, info, err);
    }

    @Override
    public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
        // TODO: Read params.getCapabilities();

        ServerCapabilities capabilities = ServerCapabilities.create();
        capabilities.setTextDocumentSync(TEXT_DOCUMENT_SYNC_KIND);
        capabilities.setDocumentSymbolProvider(false);
        capabilities.setWorkspaceSymbolProvider(false);
        capabilities.setDefinitionProvider(false);
        capabilities.setDocumentHighlightProvider(true);
        capabilities.setCodeLensProvider(CodeLensOptions.create().setResolveProvider(false));
        capabilities.setCompletionProvider(CompletionOptions.create().setResolveProvider(false));
        capabilities.setCodeActionProvider(true);
        capabilities.setSignatureHelpProvider(SignatureHelpOptions.create());
        capabilities.setHoverProvider(true);
        capabilities.setReferencesProvider(false);
        capabilities.setExecuteCommandProvider(ExecuteCommandOptions.create(Arrays.asList(DRY_RUN, GET_COVERAGE)));

        this.serverCapabilities = capabilities;
        CompletableFuture.runAsync(() -> parseWorkspace(params.getWorkspaceFolders()));

        return CompletableFuture.completedFuture(InitializeResult.create(capabilities));
    }

    @Override
    protected boolean supportsMethod(String method, JSONObject params) {
        return DelegateServers.supportsMethod(method, params, serverCapabilities);
    }

    @Override
    public CompletableFuture<Object> shutdown() {
        info.println("[Graal LSP] Shutting down server...");
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void exit() {
        clientConnectionExecutor.shutdown();
        info.println("[Graal LSP] Server shutdown done.");
    }

    @Override
    public void connect(@SuppressWarnings("hiding") LanguageClient client) {
        this.client = client;
    }

    @Override
    public CompletableFuture<CompletionList> completion(CompletionParams position) {
        Future<CompletionList> futureCompletionList = truffleAdapter.completion(URI.create(position.getTextDocument().getUri()), position.getPosition().getLine(),
                        position.getPosition().getCharacter(), position.getContext());
        return CompletableFuture.supplyAsync(() -> waitForResultAndHandleExceptions(futureCompletionList, truffleAdapter.completionHandler.emptyList));
    }

    @Override
    public CompletableFuture<CompletionItem> resolveCompletion(CompletionItem unresolved) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public CompletableFuture<Hover> hover(TextDocumentPositionParams position) {
        Future<Hover> futureHover = truffleAdapter.hover(URI.create(position.getTextDocument().getUri()), position.getPosition().getLine(), position.getPosition().getCharacter());
        return CompletableFuture.supplyAsync(() -> waitForResultAndHandleExceptions(futureHover, emptyHover));
    }

    @Override
    public CompletableFuture<SignatureHelp> signatureHelp(TextDocumentPositionParams position) {
        Future<SignatureHelp> future = truffleAdapter.signatureHelp(URI.create(position.getTextDocument().getUri()), position.getPosition().getLine(), position.getPosition().getCharacter());
        return CompletableFuture.supplyAsync(() -> waitForResultAndHandleExceptions(future, emptySignatureHelp));
    }

    @Override
    public CompletableFuture<List<? extends Location>> definition(TextDocumentPositionParams position) {
        return CompletableFuture.completedFuture(Collections.emptyList());
    }

    @Override
    public CompletableFuture<List<? extends Location>> references(ReferenceParams params) {
        return CompletableFuture.completedFuture(Collections.emptyList());
    }

    @Override
    public CompletableFuture<List<? extends DocumentHighlight>> documentHighlight(TextDocumentPositionParams position) {
        Future<List<? extends DocumentHighlight>> future = truffleAdapter.documentHighlight(URI.create(position.getTextDocument().getUri()), position.getPosition().getLine(),
                        position.getPosition().getCharacter());
        Supplier<List<? extends DocumentHighlight>> supplier = () -> waitForResultAndHandleExceptions(future, Collections.emptyList());
        return CompletableFuture.supplyAsync(supplier);
    }

    @Override
    public CompletableFuture<List<? extends SymbolInformation>> documentSymbol(DocumentSymbolParams params) {
        return CompletableFuture.completedFuture(Collections.emptyList());
    }

    @Override
    public CompletableFuture<List<? extends CodeAction>> codeAction(CodeActionParams params) {
        return CompletableFuture.completedFuture(Collections.emptyList());
    }

    @Override
    public CompletableFuture<List<? extends CodeLens>> codeLens(CodeLensParams params) {
        return CompletableFuture.completedFuture(Collections.emptyList());
    }

    @Override
    public CompletableFuture<CodeLens> resolveCodeLens(CodeLens unresolved) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public CompletableFuture<List<? extends TextEdit>> formatting(DocumentFormattingParams params) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public CompletableFuture<List<? extends TextEdit>> rangeFormatting(DocumentRangeFormattingParams params) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public CompletableFuture<List<? extends TextEdit>> onTypeFormatting(DocumentOnTypeFormattingParams params) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public CompletableFuture<WorkspaceEdit> rename(RenameParams params) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void didOpen(DidOpenTextDocumentParams params) {
        URI uri = URI.create(params.getTextDocument().getUri());

        if (!uri.getScheme().equals("file")) {
            client.showMessage(ShowMessageParams.create(MessageType.Error, "URI with schema other than 'file' are not supported yet. uri=" + uri.toString()));
            return;
        }

        openedFileUri2LangId.put(uri, params.getTextDocument().getLanguageId());

        Future<?> future = truffleAdapter.parse(params.getTextDocument().getText(), params.getTextDocument().getLanguageId(), uri);
        CompletableFuture.runAsync(() -> waitForResultAndHandleExceptions(future, null, uri));
    }

    @Override
    public void didChange(DidChangeTextDocumentParams params) {
        processChanges(params.getTextDocument().getUri(), params.getContentChanges());
    }

    private void processChanges(final String documentUri,
                    final List<? extends TextDocumentContentChangeEvent> list) {
        String langId = openedFileUri2LangId.get(URI.create(documentUri));
        if (langId == null) {
            truffleAdapter.getLogger().warning("Changed document that was not opened: " + documentUri);
            return;
        }

        URI uri = URI.create(documentUri);
        Future<?> future;
        switch (TEXT_DOCUMENT_SYNC_KIND) {
            case Full:
                // Only need the first element, as long as sync mode isTextDocumentSyncKind.Full
                TextDocumentContentChangeEvent e = list.iterator().next();
                future = truffleAdapter.parse(e.getText(), langId, uri);
                break;
            case Incremental:
                future = truffleAdapter.processChangesAndParse(list, uri);
                break;
            default:
                throw new IllegalStateException("Unknown TextDocumentSyncKind: " + TEXT_DOCUMENT_SYNC_KIND);
        }

        CompletableFuture.runAsync(() -> waitForResultAndHandleExceptions(future, null, uri));
    }

    @Override
    public void didClose(DidCloseTextDocumentParams params) {
        URI uri = URI.create(params.getTextDocument().getUri());
        openedFileUri2LangId.remove(uri);
        truffleAdapter.didClose(uri);
    }

    @Override
    public void didSave(DidSaveTextDocumentParams params) {
        Future<?> future;
        URI uri = URI.create(params.getTextDocument().getUri());
        if (params.getText() != null) {
            String langId = openedFileUri2LangId.get(uri);
            if (langId == null) {
                truffleAdapter.getLogger().warning("Saved document that was not opened: " + uri);
                return;
            }
            future = truffleAdapter.parse(params.getText(), langId, uri);
        } else {
            future = truffleAdapter.reparse(uri);
        }
        CompletableFuture.runAsync(() -> waitForResultAndHandleExceptions(future, null, uri));
    }

    @Override
    public CompletableFuture<List<? extends SymbolInformation>> symbol(WorkspaceSymbolParams params) {
        return CompletableFuture.completedFuture(Collections.emptyList());
    }

    @Override
    public CompletableFuture<Object> executeCommand(ExecuteCommandParams params) {
        switch (params.getCommand()) {
            case DRY_RUN:
                String uri = (String) params.getArguments().get(0);
                Future<?> future = truffleAdapter.runCoverageAnalysis(URI.create(uri));
                return CompletableFuture.supplyAsync(() -> waitForResultAndHandleExceptions(future));
            case GET_COVERAGE:
                uri = (String) params.getArguments().get(0);
                Future<Coverage> futureCoverage = truffleAdapter.getCoverage(URI.create(uri));
                return CompletableFuture.supplyAsync(() -> waitForResultAndHandleExceptions(futureCoverage));
            default:
                err.println("Unkown command: " + params.getCommand());
                return CompletableFuture.completedFuture(new Object());
        }
    }

    @Override
    public LoggerProxy getLogger() {
        return new LoggerProxy() {
            @Override
            public boolean isLoggable(Level level) {
                return truffleAdapter.getLogger().isLoggable(level);
            }

            @Override
            public void log(Level level, String msg) {
                truffleAdapter.getLogger().log(level, msg);
            }

            @Override
            public void log(Level level, String msg, Throwable thrown) {
                truffleAdapter.getLogger().log(level, msg, thrown);
            }
        };
    }

    private void parseWorkspace(List<WorkspaceFolder> workspaces) {
        for (WorkspaceFolder workspace : workspaces) {
            List<Future<?>> parsingTasks = truffleAdapter.parseWorkspace(URI.create(workspace.getUri()));
            for (Future<?> future : parsingTasks) {
                waitForResultAndHandleExceptions(future);
            }
        }
    }

    private <T> T waitForResultAndHandleExceptions(Future<T> future) {
        return waitForResultAndHandleExceptions(future, null, null);
    }

    private <T> T waitForResultAndHandleExceptions(Future<T> future, T resultOnError) {
        return waitForResultAndHandleExceptions(future, resultOnError, null);
    }

    private <T> T waitForResultAndHandleExceptions(Future<T> future, T resultOnError, URI uriToClearDiagnostics) {
        try {
            T result = future.get();
            if (uriToClearDiagnostics != null) {
                // No exceptions occurred during future execution, so clear diagnostics (e.g. after
                // fixing a syntax error etc.)
                client.publishDiagnostics(PublishDiagnosticsParams.create(uriToClearDiagnostics.toString(), Collections.emptyList()));
            }
            return result;
        } catch (InterruptedException e) {
            e.printStackTrace(err);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof UnknownLanguageException) {
                String message = "Unknown language: " + e.getCause().getMessage();
                truffleAdapter.getLogger().fine(message);
                client.showMessage(ShowMessageParams.create(MessageType.Error, message));
            } else if (e.getCause() instanceof DiagnosticsNotification) {
                for (PublishDiagnosticsParams params : ((DiagnosticsNotification) e.getCause()).getDiagnosticParamsCollection()) {
                    client.publishDiagnostics(params);
                }
            } else {
                e.printStackTrace(err);
            }
        }

        return resultOnError;
    }

    public CompletableFuture<?> start(final ServerSocket serverSocket, final List<Pair<String, SocketAddress>> delegateAddresses) {
        clientConnectionExecutor = Executors.newSingleThreadExecutor(new ThreadFactory() {

            @Override
            public Thread newThread(Runnable r) {
                Thread thread = Executors.defaultThreadFactory().newThread(r);
                thread.setName("LSP client connection thread");
                return thread;
            }
        });
        CompletableFuture<?> future = CompletableFuture.runAsync(new Runnable() {

            @Override
            public void run() {
                try {
                    if (serverSocket.isClosed()) {
                        err.println("[Graal LSP] Server socket is closed.");
                        return;
                    }

                    info.println("[Graal LSP] Starting server and listening on " + serverSocket.getLocalSocketAddress());
                    try (Socket clientSocket = serverSocket.accept()) {
                        info.println("[Graal LSP] Client connected on " + clientSocket.getRemoteSocketAddress());

                        ExecutorService lspRequestExecutor = Executors.newCachedThreadPool(new ThreadFactory() {
                            private final ThreadFactory factory = Executors.defaultThreadFactory();

                            @Override
                            public Thread newThread(Runnable r) {
                                Thread thread = factory.newThread(r);
                                thread.setName("LSP client request handler " + thread.getName());
                                return thread;
                            }
                        });

                        OutputStream serverOutput = clientSocket.getOutputStream();
                        DelegateServers delegateServers = createDelegateServers(serverOutput);
                        Future<?> listenFuture = Session.connect(LanguageServerImpl.this, clientSocket.getInputStream(), serverOutput, lspRequestExecutor, delegateServers);
                        try {
                            listenFuture.get();
                        } catch (InterruptedException | ExecutionException e) {
                            err.println("[Graal LSP] Error: " + e.getLocalizedMessage());
                        } finally {
                            lspRequestExecutor.shutdown();
                        }
                    }
                } catch (IOException e) {
                    err.println("[Graal LSP] Error while connecting to client: " + e.getLocalizedMessage());
                }
            }

            private DelegateServers createDelegateServers(OutputStream serverOutput) {
                List<DelegateServer> delegateServersList;
                if (delegateAddresses.isEmpty()) {
                    delegateServersList = Collections.emptyList();
                } else {
                    delegateServersList = new ArrayList<>(delegateAddresses.size());
                    for (Pair<String, SocketAddress> langAddress : delegateAddresses) {
                        String languageId = langAddress.getLeft();
                        SocketAddress address = langAddress.getRight();
                        try {
                            delegateServersList.add(new DelegateServer(languageId, address, serverOutput, truffleAdapter, getLogger()));
                        } catch (IOException ex) {
                            err.println("[Graal LSP] Error while connecting to delegate server at " + address + " : " + ex.getLocalizedMessage());
                        }
                    }
                }
                return new DelegateServers(truffleAdapter, delegateServersList, getLogger());
            }
        }, clientConnectionExecutor);
        return future;
    }
}
