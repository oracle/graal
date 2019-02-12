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
package org.graalvm.tools.lsp.server;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
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

import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.CodeLens;
import org.eclipse.lsp4j.CodeLensOptions;
import org.eclipse.lsp4j.CodeLensParams;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.CompletionOptions;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.DidChangeConfigurationParams;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidChangeWatchedFilesParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.DocumentFormattingParams;
import org.eclipse.lsp4j.DocumentHighlight;
import org.eclipse.lsp4j.DocumentOnTypeFormattingParams;
import org.eclipse.lsp4j.DocumentRangeFormattingParams;
import org.eclipse.lsp4j.DocumentSymbol;
import org.eclipse.lsp4j.DocumentSymbolParams;
import org.eclipse.lsp4j.ExecuteCommandOptions;
import org.eclipse.lsp4j.ExecuteCommandParams;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.ReferenceParams;
import org.eclipse.lsp4j.RenameParams;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.SignatureHelp;
import org.eclipse.lsp4j.SignatureHelpOptions;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.lsp4j.TextDocumentPositionParams;
import org.eclipse.lsp4j.TextDocumentSyncKind;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.WorkspaceEdit;
import org.eclipse.lsp4j.WorkspaceSymbolParams;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageClientAware;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.eclipse.lsp4j.services.WorkspaceService;
import org.graalvm.tools.lsp.exceptions.DiagnosticsNotification;
import org.graalvm.tools.lsp.exceptions.UnknownLanguageException;
import org.graalvm.tools.lsp.instrument.LSPInstrument;

import com.google.gson.JsonPrimitive;

import com.oracle.truffle.api.TruffleLogger;

/**
 * A LSP4J {@link LanguageServer} implementation using TCP sockets as transportation layer for the
 * JSON-RPC requests. It delegates all requests to {@link TruffleAdapter}.
 */
public final class LanguageServerImpl implements LanguageServer, LanguageClientAware, TextDocumentService, WorkspaceService {

    private static final TruffleLogger LOG = TruffleLogger.getLogger(LSPInstrument.ID, LanguageServer.class);

    private static final String SHOW_COVERAGE = "show_coverage";
    private static final String ANALYSE_COVERAGE = "analyse_coverage";
    private static final String CLEAR_COVERAGE = "clear_coverage";
    private static final String CLEAR_ALL_COVERAGE = "clear_all_coverage";
    private static final TextDocumentSyncKind TEXT_DOCUMENT_SYNC_KIND = TextDocumentSyncKind.Incremental;

    private final TruffleAdapter truffleAdapter;
    private final PrintWriter err;
    private final PrintWriter info;
    private LanguageClient client;
    private final Map<URI, String> openedFileUri2LangId = new HashMap<>();
    private ExecutorService clientConnectionExecutor;

    private final Hover emptyHover = new Hover();
    private final SignatureHelp emptySignatureHelp = new SignatureHelp();

    private LanguageServerImpl(TruffleAdapter adapter, PrintWriter info, PrintWriter err) {
        this.truffleAdapter = adapter;
        this.info = info;
        this.err = err;
    }

    public static LanguageServerImpl create(TruffleAdapter adapter, PrintWriter info, PrintWriter err) {
        LanguageServerImpl server = new LanguageServerImpl(adapter, info, err);
        return server;
    }

    @Override
    public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
        truffleAdapter.initialize();

        List<String> signatureTriggerChars = waitForResultAndHandleExceptions(truffleAdapter.getSignatureHelpTriggerCharactersOfAllLanguages());
        List<String> triggerCharacters = waitForResultAndHandleExceptions(truffleAdapter.getCompletionTriggerCharactersOfAllLanguages());

        ServerCapabilities capabilities = new ServerCapabilities();
        capabilities.setTextDocumentSync(TEXT_DOCUMENT_SYNC_KIND);
        capabilities.setDocumentSymbolProvider(true);
        capabilities.setWorkspaceSymbolProvider(true);
        capabilities.setDefinitionProvider(true);
        capabilities.setDocumentHighlightProvider(true);
        capabilities.setCodeLensProvider(new CodeLensOptions(false));
        CompletionOptions completionOptions = new CompletionOptions();
        completionOptions.setResolveProvider(false);
        completionOptions.setTriggerCharacters(triggerCharacters);
        capabilities.setCompletionProvider(completionOptions);
        capabilities.setCodeActionProvider(true);
        SignatureHelpOptions signatureHelpOptions = new SignatureHelpOptions(signatureTriggerChars);
        capabilities.setSignatureHelpProvider(signatureHelpOptions);
        capabilities.setHoverProvider(true);
        capabilities.setReferencesProvider(true);

        capabilities.setExecuteCommandProvider(new ExecuteCommandOptions(Arrays.asList(ANALYSE_COVERAGE, SHOW_COVERAGE, CLEAR_COVERAGE, CLEAR_ALL_COVERAGE)));

        CompletableFuture.runAsync(() -> parseWorkspace(params.getRootUri()));

        return CompletableFuture.completedFuture(new InitializeResult(capabilities));
    }

    @Override
    public CompletableFuture<Object> shutdown() {
        info.println("[Graal LSP] Shutting down server...");
        return CompletableFuture.completedFuture(new Object());
    }

    @Override
    public void exit() {
        clientConnectionExecutor.shutdown();
        info.println("[Graal LSP] Server shutdown done.");
    }

    @Override
    public TextDocumentService getTextDocumentService() {
        return this;
    }

    @Override
    public WorkspaceService getWorkspaceService() {
        return this;
    }

    @Override
    public void connect(@SuppressWarnings("hiding") LanguageClient client) {
        this.client = client;
    }

    @Override
    public CompletableFuture<Either<List<CompletionItem>, CompletionList>> completion(CompletionParams position) {
        Future<CompletionList> futureCompletionList = truffleAdapter.completion(URI.create(position.getTextDocument().getUri()), position.getPosition().getLine(),
                        position.getPosition().getCharacter(), position.getContext());
        return CompletableFuture.supplyAsync(() -> Either.forRight(waitForResultAndHandleExceptions(futureCompletionList, truffleAdapter.completionHandler.emptyList)));
    }

    @Override
    public CompletableFuture<CompletionItem> resolveCompletionItem(CompletionItem unresolved) {
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
        Future<List<? extends Location>> future = truffleAdapter.definition(URI.create(position.getTextDocument().getUri()), position.getPosition().getLine(),
                        position.getPosition().getCharacter());
        Supplier<List<? extends Location>> supplier = () -> waitForResultAndHandleExceptions(future, Collections.emptyList());
        return CompletableFuture.supplyAsync(supplier);
    }

    @Override
    public CompletableFuture<List<? extends Location>> references(ReferenceParams params) {
        Future<List<? extends Location>> future = truffleAdapter.references(URI.create(params.getTextDocument().getUri()), params.getPosition().getLine(),
                        params.getPosition().getCharacter());
        Supplier<List<? extends Location>> supplier = () -> waitForResultAndHandleExceptions(future, Collections.emptyList());
        return CompletableFuture.supplyAsync(supplier);
    }

    @Override
    public CompletableFuture<List<? extends DocumentHighlight>> documentHighlight(TextDocumentPositionParams position) {
        Future<List<? extends DocumentHighlight>> future = truffleAdapter.documentHighlight(URI.create(position.getTextDocument().getUri()), position.getPosition().getLine(),
                        position.getPosition().getCharacter());
        Supplier<List<? extends DocumentHighlight>> supplier = () -> waitForResultAndHandleExceptions(future, Collections.emptyList());
        return CompletableFuture.supplyAsync(supplier);
    }

    @Override
    public CompletableFuture<List<Either<SymbolInformation, DocumentSymbol>>> documentSymbol(DocumentSymbolParams params) {
        Future<List<Either<SymbolInformation, DocumentSymbol>>> future = truffleAdapter.documentSymbol(URI.create(params.getTextDocument().getUri()));
        Supplier<List<Either<SymbolInformation, DocumentSymbol>>> supplier = () -> waitForResultAndHandleExceptions(future, Collections.emptyList());
        return CompletableFuture.supplyAsync(supplier);
    }

    @Override
    public CompletableFuture<List<Either<Command, CodeAction>>> codeAction(CodeActionParams params) {
        return CompletableFuture.completedFuture(Collections.emptyList());
    }

    @Override
    public CompletableFuture<List<? extends CodeLens>> codeLens(CodeLensParams params) {
        return CompletableFuture.supplyAsync(() -> {
            CodeLens codeLens = new CodeLens(new Range(new Position(), new Position()));
            Command command = new Command("Analyse coverage", ANALYSE_COVERAGE);
            command.setArguments(Arrays.asList(params.getTextDocument().getUri()));
            codeLens.setCommand(command);

            CodeLens codeLensShowCoverage = new CodeLens(new Range(new Position(), new Position()));
            Command commandShowCoverage = new Command("Highlight uncovered code", SHOW_COVERAGE);
            commandShowCoverage.setArguments(Arrays.asList(params.getTextDocument().getUri()));
            codeLensShowCoverage.setCommand(commandShowCoverage);

            CodeLens codeLensClear = new CodeLens(new Range(new Position(), new Position()));
            Command commandClear = new Command("Clear coverage", CLEAR_COVERAGE);
            commandClear.setArguments(Arrays.asList(params.getTextDocument().getUri()));
            codeLensClear.setCommand(commandClear);

            CodeLens codeLensClearAll = new CodeLens(new Range(new Position(), new Position()));
            Command commandClearAll = new Command("Clear coverage (all files)", CLEAR_ALL_COVERAGE);
            commandClearAll.setArguments(Arrays.asList(params.getTextDocument().getUri()));
            codeLensClearAll.setCommand(commandClearAll);

            return Arrays.asList(codeLens, codeLensShowCoverage, codeLensClear, codeLensClearAll);
        });
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
            client.showMessage(new MessageParams(MessageType.Error, "URI with schema other than 'file' are not supported yet. uri=" + uri.toString()));
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
        assert langId != null : documentUri; // TODO: Are we sure we want to throw AssertionError?

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
        String removed = openedFileUri2LangId.remove(uri);
        assert removed != null : uri.toString();

        truffleAdapter.didClose(uri);
    }

    @Override
    public void didSave(DidSaveTextDocumentParams params) {
        Future<?> future;
        URI uri = URI.create(params.getTextDocument().getUri());
        if (params.getText() != null) {
            String langId = openedFileUri2LangId.get(uri);
            assert langId != null : uri;
            future = truffleAdapter.parse(params.getText(), langId, uri);

        } else {
            future = truffleAdapter.reparse(uri);
        }
        CompletableFuture.runAsync(() -> waitForResultAndHandleExceptions(future, null, uri));
    }

    @Override
    public CompletableFuture<List<? extends SymbolInformation>> symbol(WorkspaceSymbolParams params) {
        Future<List<? extends SymbolInformation>> future = truffleAdapter.workspaceSymbol(params.getQuery());
        Supplier<List<? extends SymbolInformation>> supplier = () -> waitForResultAndHandleExceptions(future, Collections.emptyList());
        return CompletableFuture.supplyAsync(supplier);
    }

    @Override
    public void didChangeConfiguration(DidChangeConfigurationParams params) {
        // TODO(ds) client configs are not used yet
    }

    @Override
    public void didChangeWatchedFiles(DidChangeWatchedFilesParams params) {
    }

    @Override
    public CompletableFuture<Object> executeCommand(ExecuteCommandParams params) {
        switch (params.getCommand()) {
            case ANALYSE_COVERAGE:
                client.showMessage(new MessageParams(MessageType.Info, "Running Coverage analysis..."));
                String uri = ((JsonPrimitive) params.getArguments().get(0)).getAsString();

                Future<Boolean> future = truffleAdapter.runCoverageAnalysis(URI.create(uri));
                return CompletableFuture.supplyAsync(() -> {
                    Boolean result = waitForResultAndHandleExceptions(future, Boolean.FALSE);
                    if (result) {
                        client.showMessage(new MessageParams(MessageType.Info, "Coverage analysis done."));
                        Future<?> futureShowCoverage = truffleAdapter.showCoverage(URI.create(uri));
                        waitForResultAndHandleExceptions(futureShowCoverage);
                    } else {
                        client.showMessage(new MessageParams(MessageType.Error, "Coverage analysis failed."));
                    }
                    return new Object();
                });
            case SHOW_COVERAGE:
                uri = ((JsonPrimitive) params.getArguments().get(0)).getAsString();

                Future<?> futureCoverage = truffleAdapter.showCoverage(URI.create(uri));
                return CompletableFuture.supplyAsync(() -> waitForResultAndHandleExceptions(futureCoverage));
            case CLEAR_COVERAGE:
                uri = ((JsonPrimitive) params.getArguments().get(0)).getAsString();

                Future<?> futureClear = truffleAdapter.clearCoverage(URI.create(uri));
                return CompletableFuture.supplyAsync(() -> waitForResultAndHandleExceptions(futureClear));
            case CLEAR_ALL_COVERAGE:
                Future<?> futureClearAll = truffleAdapter.clearCoverage();
                return CompletableFuture.supplyAsync(() -> waitForResultAndHandleExceptions(futureClearAll));
            default:
                err.println("Unkown command: " + params.getCommand());
                return CompletableFuture.completedFuture(new Object());
        }
    }

    private void parseWorkspace(String rootUri) {
        List<Future<?>> parsingTasks = truffleAdapter.parseWorkspace(URI.create(rootUri));

        for (Future<?> future : parsingTasks) {
            waitForResultAndHandleExceptions(future);
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
                client.publishDiagnostics(new PublishDiagnosticsParams(uriToClearDiagnostics.toString(), Collections.emptyList()));
            }
            return result;
        } catch (InterruptedException e) {
            e.printStackTrace(err);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof UnknownLanguageException) {
                String message = "Unknown language: " + e.getCause().getMessage();
                LOG.fine(message);
                client.showMessage(new MessageParams(MessageType.Error, message));
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

    public Future<?> start(final ServerSocket serverSocket) {
        clientConnectionExecutor = Executors.newSingleThreadExecutor(new ThreadFactory() {

            @Override
            public Thread newThread(Runnable r) {
                Thread thread = Executors.defaultThreadFactory().newThread(r);
                thread.setName("LSP client connection thread");
                return thread;
            }
        });
        Future<?> future = clientConnectionExecutor.submit(new Runnable() {

            @Override
            public void run() {
                try {
                    if (serverSocket.isClosed()) {
                        err.println("[Graal LSP] Server socket is closed.");
                        return;
                    }

                    info.println("[Graal LSP] Starting server and listening on " + serverSocket.getLocalSocketAddress());
                    Socket clientSocket = serverSocket.accept();
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

                    Launcher.Builder<LanguageClient> launcherBuilder = new LSPLauncher.Builder<LanguageClient>() //
                                    .setLocalService(LanguageServerImpl.this) //
                                    .setRemoteInterface(LanguageClient.class) //
                                    .setInput(clientSocket.getInputStream()) //
                                    .setOutput(clientSocket.getOutputStream()) //
                                    .setExecutorService(lspRequestExecutor);
                    if (LOG.isLoggable(Level.FINER)) {
                        launcherBuilder.traceMessages(new PrintWriter(new Writer() {
                            @Override
                            public void write(char[] cbuf, int off, int len) throws IOException {
                                LOG.finer(new String(cbuf, off, len));
                            }

                            @Override
                            public void flush() throws IOException {
                            }

                            @Override
                            public void close() throws IOException {
                            }
                        }));
                    }
                    Launcher<LanguageClient> launcher = launcherBuilder.create();

                    LanguageServerImpl.this.connect(launcher.getRemoteProxy());
                    Future<?> listenFuture = launcher.startListening();
                    try {
                        listenFuture.get();
                    } catch (InterruptedException | ExecutionException e) {
                        err.println("[Graal LSP] Error: " + e.getLocalizedMessage());
                    } finally {
                        lspRequestExecutor.shutdown();
                    }
                } catch (IOException e) {
                    err.println("[Graal LSP] Error while connecting to client: " + e.getLocalizedMessage());
                }
            }
        }, Boolean.TRUE);
        return future;
    }
}
