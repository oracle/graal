package de.hpi.swa.trufflelsp.server;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.CodeLens;
import org.eclipse.lsp4j.CodeLensOptions;
import org.eclipse.lsp4j.CodeLensParams;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.CompletionOptions;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
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

import de.hpi.swa.trufflelsp.TruffleAdapter;

public class LSPServer implements LanguageServer, LanguageClientAware, TextDocumentService, WorkspaceService, DiagnosticsPublisher {
    private static final TextDocumentSyncKind TEXT_DOCUMENT_SYNC_KIND = TextDocumentSyncKind.Incremental;
    private final TruffleAdapter truffle;
    private final PrintWriter err;
    private final PrintWriter info;
// private int shutdown = 1;
    private LanguageClient client;
    private Map<URI, String> openedFileUri2LangId = new HashMap<>();
    private String trace_server = "off";
    private List<Diagnostic> diagnostics = new ArrayList<>();
    private ExecutorService executor;
    private ServerSocket serverSocket;

    private LSPServer(TruffleAdapter adapter, PrintWriter info, PrintWriter err) {
        this.truffle = adapter;
        this.info = info;
        this.err = err;
    }

    public static LSPServer create(TruffleAdapter adapter, PrintWriter info, PrintWriter err) {
        LSPServer server = new LSPServer(adapter, info, err);
        adapter.setDiagnosticsPublisher(server);
        return server;
    }

    public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
        List<String> triggerCharacters = Arrays.asList("=", ".");
// final SignatureHelpOptions signatureHelpOptions = new SignatureHelpOptions(triggerCharacters);

        ServerCapabilities capabilities = new ServerCapabilities();
        capabilities.setTextDocumentSync(TEXT_DOCUMENT_SYNC_KIND);
        capabilities.setDocumentSymbolProvider(true);
        capabilities.setWorkspaceSymbolProvider(true);
        capabilities.setDefinitionProvider(true);
        capabilities.setDocumentHighlightProvider(false);
        capabilities.setCodeLensProvider(new CodeLensOptions(false));
        CompletionOptions completionOptions = new CompletionOptions();
        completionOptions.setResolveProvider(false);
        completionOptions.setTriggerCharacters(triggerCharacters);
        capabilities.setCompletionProvider(completionOptions);
        capabilities.setCodeActionProvider(true);
        // capabilities.setSignatureHelpProvider(signatureHelpOptions);
        capabilities.setHoverProvider(true);

        capabilities.setExecuteCommandProvider(new ExecuteCommandOptions(Arrays.asList("harvest_types")));

        final InitializeResult res = new InitializeResult(capabilities);
        return CompletableFuture.supplyAsync(() -> res);
    }

    public CompletableFuture<Object> shutdown() {
        info.println("[Truffle LSP] Shutting down server...");
        info.flush();
        return CompletableFuture.completedFuture(null);
    }

    public void exit() {
        try {
            serverSocket.close();
        } catch (IOException e) {
            err.println("[Truffle LSP] Error while closing socket: " + e.getLocalizedMessage());
        }
        this.executor.shutdownNow();
        info.println("[Truffle LSP] Server shutdown done.");
        info.flush();
    }

    public TextDocumentService getTextDocumentService() {
        return this;
    }

    public WorkspaceService getWorkspaceService() {
        return this;
    }

    @Override
    public void connect(@SuppressWarnings("hiding") LanguageClient client) {
        this.client = client;
    }

    public void addDiagnostics(@SuppressWarnings("hiding") List<Diagnostic> diagnostics) {
        this.diagnostics.addAll(diagnostics);
    }

    private boolean hasErrorsInDiagnostics() {
        return this.diagnostics.stream().anyMatch(diagnostic -> DiagnosticSeverity.Error.equals(diagnostic.getSeverity()));
    }

    public void reportCollectedDiagnostics(final String documentUri, boolean forceIfEmpty) {
        if (!this.diagnostics.isEmpty() || forceIfEmpty) {
            PublishDiagnosticsParams result = new PublishDiagnosticsParams();
            result.setDiagnostics(this.diagnostics);
            result.setUri(documentUri);
            this.client.publishDiagnostics(result);
            this.diagnostics.clear();
        }
    }

    @Override
    public CompletableFuture<Either<List<CompletionItem>, CompletionList>> completion(
                    TextDocumentPositionParams position) {
        try {
            CompletionList result = this.truffle.getCompletions(URI.create(position.getTextDocument().getUri()), position.getPosition().getLine(), position.getPosition().getCharacter());
            return CompletableFuture.supplyAsync(() -> Either.forRight(result));
        } finally {
            reportCollectedDiagnostics(position.getTextDocument().getUri(), false);
        }
    }

    @Override
    public CompletableFuture<CompletionItem> resolveCompletionItem(CompletionItem unresolved) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public CompletableFuture<Hover> hover(TextDocumentPositionParams position) {
        Hover hover = this.truffle.getHover(URI.create(position.getTextDocument().getUri()), position.getPosition().getLine(), position.getPosition().getCharacter());
        return CompletableFuture.completedFuture(hover);
    }

    @Override
    public CompletableFuture<SignatureHelp> signatureHelp(TextDocumentPositionParams position) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public CompletableFuture<List<? extends Location>> definition(TextDocumentPositionParams position) {
        List<? extends Location> result = this.truffle.getDefinitions(URI.create(position.getTextDocument().getUri()), position.getPosition().getLine(), position.getPosition().getCharacter());
        return CompletableFuture.completedFuture(result);
    }

    @Override
    public CompletableFuture<List<? extends Location>> references(ReferenceParams params) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public CompletableFuture<List<? extends DocumentHighlight>> documentHighlight(TextDocumentPositionParams position) {
        List<? extends DocumentHighlight> highlights = this.truffle.getHighlights(URI.create(position.getTextDocument().getUri()), position.getPosition().getLine(),
                        position.getPosition().getCharacter());
        return CompletableFuture.supplyAsync(() -> highlights);
    }

    @Override
    public CompletableFuture<List<? extends SymbolInformation>> documentSymbol(DocumentSymbolParams params) {
        List<? extends SymbolInformation> result = truffle.getSymbolInfo(URI.create(params.getTextDocument().getUri()));
        return CompletableFuture.completedFuture(result);
    }

    @Override
    public CompletableFuture<List<? extends Command>> codeAction(CodeActionParams params) {
        List<Diagnostic> currentDiagnostics = params.getContext().getDiagnostics();
        List<Command> commands = new ArrayList<>();
        if (currentDiagnostics.stream().anyMatch(diag -> TruffleAdapter.NO_TYPES_HARVESTED.equals(diag.getCode()))) {
            Command command = new Command("Harvest types (exec this code)", "harvest_types");
            command.setArguments(Arrays.asList(params.getTextDocument().getUri()));
            commands.add(command);
        }

        return CompletableFuture.completedFuture(commands);
    }

    @Override
    public CompletableFuture<List<? extends CodeLens>> codeLens(CodeLensParams params) {
        CodeLens codeLens = new CodeLens(new Range(new Position(), new Position()));
        Command command = new Command("Harvest types (exec this code)", "harvest_types");
        command.setArguments(Arrays.asList(params.getTextDocument().getUri()));
        codeLens.setCommand(command);

        return CompletableFuture.completedFuture(Arrays.asList(codeLens));
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
        this.openedFileUri2LangId.put(uri, params.getTextDocument().getLanguageId());

        this.truffle.didOpen(uri, params.getTextDocument().getText(), params.getTextDocument().getLanguageId());

        parseDocument(params.getTextDocument().getUri(), params.getTextDocument().getLanguageId(),
                        params.getTextDocument().getText());
    }

    @Override
    public void didChange(DidChangeTextDocumentParams params) {
        processChanges(params.getTextDocument().getUri(), params.getContentChanges());
    }

    private void processChanges(final String documentUri,
                    final List<? extends TextDocumentContentChangeEvent> list) {
        String langId = this.openedFileUri2LangId.get(URI.create(documentUri));
        assert langId != null : documentUri;

        if (TEXT_DOCUMENT_SYNC_KIND.equals(TextDocumentSyncKind.Full)) {
            // Only need the first element, as long as sync mode is
            // TextDocumentSyncKind.Full
            TextDocumentContentChangeEvent e = list.iterator().next();

            parseDocument(documentUri, langId, e.getText());
        } else if (TEXT_DOCUMENT_SYNC_KIND.equals(TextDocumentSyncKind.Incremental)) {
            processChangesAndParseDocument(documentUri, list);
        }
    }

    private void parseDocument(final String documentUri, final String langId, final String text) {
        this.truffle.parse(text, langId, URI.create(documentUri));
        reportCollectedDiagnostics(documentUri, true);
    }

    private void processChangesAndParseDocument(String documentUri, List<? extends TextDocumentContentChangeEvent> list) {
        this.truffle.processChangesAndParse(list, URI.create(documentUri));
        reportCollectedDiagnostics(documentUri, true);
    }

    @Override
    public void didClose(DidCloseTextDocumentParams params) {
        URI uri = URI.create(params.getTextDocument().getUri());
        String removed = this.openedFileUri2LangId.remove(uri);
        assert removed != null : uri.toString();

        this.truffle.didClose(uri);
    }

    @Override
    public void didSave(DidSaveTextDocumentParams params) {
        // TODO Auto-generated method stub

    }

    @Override
    public CompletableFuture<List<? extends SymbolInformation>> symbol(WorkspaceSymbolParams params) {
        List<? extends SymbolInformation> result = new ArrayList<>();
        return CompletableFuture.completedFuture(result);
    }

    @Override
    public void didChangeConfiguration(DidChangeConfigurationParams params) {
        if (params.getSettings() instanceof Map<?, ?>) {
            Map<?, ?> settings = (Map<?, ?>) params.getSettings();
            if (settings.get("truffleLsp") instanceof Map<?, ?>) {
                Map<?, ?> truffleLsp = (Map<?, ?>) settings.get("truffleLsp");
                if (truffleLsp.get("trace") instanceof Map<?, ?>) {
                    Map<?, ?> trace = (Map<?, ?>) truffleLsp.get("trace");
                    if (trace.get("server") instanceof String) {
                        trace_server = (String) trace.get("server");
                    }
                }
            }
        }
    }

    @Override
    public void didChangeWatchedFiles(DidChangeWatchedFilesParams params) {
    }

    public CompletableFuture<Object> executeCommand(ExecuteCommandParams params) {
        if ("harvest_types".equals(params.getCommand())) {
            this.client.showMessage(new MessageParams(MessageType.Info, "Running Type Harvester..."));
            String uri = (String) params.getArguments().get(0);
            boolean hasErrors;
            try {
                this.truffle.exec(URI.create(uri));
            } finally {
                hasErrors = hasErrorsInDiagnostics();
                reportCollectedDiagnostics(uri, false);
            }

            if (hasErrors) {
                this.client.showMessage(new MessageParams(MessageType.Error, "Type Harvesting failed."));
            } else {
                this.client.showMessage(new MessageParams(MessageType.Info, "Type Harvesting done."));
            }
        }

        return CompletableFuture.completedFuture(new Object());
    }

    public boolean isVerbose() {
        return "verbose".equals(this.trace_server);
    }

    public void start(@SuppressWarnings("hiding") final ServerSocket serverSocket) {
        this.serverSocket = serverSocket;
        this.executor = Executors.newSingleThreadExecutor();
        this.executor.execute(new Runnable() {

            public void run() {
                while (true) {
                    try {
                        info.println("[Truffle LSP] Starting server on " + serverSocket.getLocalSocketAddress());
                        info.flush();
                        Socket clientSocket = serverSocket.accept();
                        Launcher<LanguageClient> launcher = LSPLauncher.createServerLauncher(LSPServer.this,
                                        clientSocket.getInputStream(), clientSocket.getOutputStream());
                        LSPServer.this.connect(launcher.getRemoteProxy());
                        Future<?> future = launcher.startListening();
                        try {
                            future.get();
                        } catch (InterruptedException | ExecutionException e) {
                            err.println("[Truffle LSP] Error: " + e.getLocalizedMessage());
                        }
                    } catch (IOException e) {
                        err.println("[Truffle LSP] Error while connecting to client: " + e.getLocalizedMessage());
                    }
                }
            }
        });
    }

}
