package de.hpi.swa.trufflelsp;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.lsp4j.CodeLens;
import org.eclipse.lsp4j.CodeLensOptions;
import org.eclipse.lsp4j.CodeLensParams;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.CompletionOptions;
import org.eclipse.lsp4j.Diagnostic;
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
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageClientAware;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.eclipse.lsp4j.services.WorkspaceService;

public class Server implements LanguageServer, LanguageClientAware, TextDocumentService, WorkspaceService {
    private int shutdown = 1;
    private LanguageClient client;
    private TruffleAdapter truffle;
    private Map<String, String> openedFileUri2LangId;
    private String trace_server = "off";

    public Server() {
        this.openedFileUri2LangId = new HashMap<>();
        this.truffle = new TruffleAdapter();
    }

    public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
        List<String> triggerCharacters = Arrays.asList("=");
// final SignatureHelpOptions signatureHelpOptions = new SignatureHelpOptions(triggerCharacters);

        ServerCapabilities capabilities = new ServerCapabilities();
        capabilities.setTextDocumentSync(TextDocumentSyncKind.Full);
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

        if (this.isVerbose()) {
            ServerLauncher.logMsg(params.toString());
        }

        return CompletableFuture.supplyAsync(() -> res);
    }

// private void loadWorkspace(final InitializeParams params) {
// try {
// som.loadWorkspace(params.getRootUri());
// } catch (URISyntaxException e) {
// MessageParams msg = new MessageParams();
// msg.setType(MessageType.Error);
// msg.setMessage("Workspace root URI invalid: " + params.getRootUri());
//
// client.logMessage(msg);
//
// ServerLauncher.logErr(msg.getMessage());
// }
// }

    public CompletableFuture<Object> shutdown() {
        shutdown = 0; // regular shutdown
        return CompletableFuture.supplyAsync(Object::new);
    }

    public void exit() {
        System.exit(shutdown);
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
        this.truffle.connect(this);
    }

    public void reportDiagnostics(final List<Diagnostic> diagnostics, final String documentUri) {
        if (diagnostics != null) {
            PublishDiagnosticsParams result = new PublishDiagnosticsParams();
            result.setDiagnostics(diagnostics);
            result.setUri(documentUri);
            this.client.publishDiagnostics(result);
        }
    }

    @Override
    public CompletableFuture<Either<List<CompletionItem>, CompletionList>> completion(
                    TextDocumentPositionParams position) {
        // CompletionList result =
        // som.getCompletions(position.getTextDocument().getUri(),
        // position.getPosition().getLine(), position.getPosition().getCharacter());
        // return CompletableFuture.completedFuture(Either.forRight(result));
        CompletionList result = this.truffle.getCompletions(position.getTextDocument().getUri(), position.getPosition().getLine(), position.getPosition().getCharacter());
        return CompletableFuture.supplyAsync(() -> Either.forRight(result));
    }

    @Override
    public CompletableFuture<CompletionItem> resolveCompletionItem(CompletionItem unresolved) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public CompletableFuture<Hover> hover(TextDocumentPositionParams position) {
        Hover hover = this.truffle.getHover(position.getTextDocument().getUri(), position.getPosition().getLine(), position.getPosition().getCharacter());
        return CompletableFuture.completedFuture(hover);
    }

    @Override
    public CompletableFuture<SignatureHelp> signatureHelp(TextDocumentPositionParams position) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public CompletableFuture<List<? extends Location>> definition(TextDocumentPositionParams position) {
        // List<? extends Location> result =
        // som.getDefinitions(position.getTextDocument().getUri(),
        // position.getPosition().getLine(), position.getPosition().getCharacter());
        // return CompletableFuture.completedFuture(result);
        List<? extends Location> result = this.truffle.getDefinitions(position.getTextDocument().getUri(), position.getPosition().getLine(), position.getPosition().getCharacter());
        return CompletableFuture.completedFuture(result);
    }

    @Override
    public CompletableFuture<List<? extends Location>> references(ReferenceParams params) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public CompletableFuture<List<? extends DocumentHighlight>> documentHighlight(TextDocumentPositionParams position) {
        List<? extends DocumentHighlight> highlights = this.truffle.getHighlights(position.getTextDocument().getUri(), position.getPosition().getLine(), position.getPosition().getCharacter());
        return CompletableFuture.supplyAsync(() -> highlights);
    }

    @Override
    public CompletableFuture<List<? extends SymbolInformation>> documentSymbol(DocumentSymbolParams params) {
        List<? extends SymbolInformation> result = truffle.getSymbolInfo(params.getTextDocument().getUri());
        return CompletableFuture.completedFuture(result);
// return CompletableFuture.supplyAsync(() -> new ArrayList<>());
    }

    @Override
    public CompletableFuture<List<? extends Command>> codeAction(CodeActionParams params) {
        List<Diagnostic> diagnostics = params.getContext().getDiagnostics();
        List<Command> commands = new ArrayList<>();
        if (diagnostics.stream().anyMatch(diag -> TruffleAdapter.NO_TYPES_HARVESTED.equals(diag.getCode()))) {
            Command command = new Command("Harvest types (exec this code)", "harvest_types");
            command.setArguments(Arrays.asList(params.getTextDocument().getUri()));
            commands.add(command);
        }

        return CompletableFuture.completedFuture(commands);
    }

    @Override
    public CompletableFuture<List<? extends CodeLens>> codeLens(CodeLensParams params) {
        // List<CodeLens> result = new ArrayList<>();
        // som.getCodeLenses(result, params.getTextDocument().getUri());
        // return CompletableFuture.completedFuture(result);

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
// ServerLauncher.logMsg("didOpen()");

        this.openedFileUri2LangId.put(params.getTextDocument().getUri(), params.getTextDocument().getLanguageId());

        parseDocument(params.getTextDocument().getUri(), params.getTextDocument().getLanguageId(),
                        params.getTextDocument().getText());
    }

    @Override
    public void didChange(DidChangeTextDocumentParams params) {
        // ServerLauncher.logMsg("didChange()");
        validateTextDocument(params.getTextDocument().getUri(), params.getContentChanges());
    }

    private void validateTextDocument(final String documentUri,
                    final List<? extends TextDocumentContentChangeEvent> list) {
        String langId = this.openedFileUri2LangId.get(documentUri);
        if (langId == null) {
            ServerLauncher.logErr("langId should not be null for opened documents, uri: " + documentUri);
        }

        // Only need the first element, as long as sync mode is
        // TextDocumentSyncKind.Full
        TextDocumentContentChangeEvent e = list.iterator().next();

        parseDocument(documentUri, langId, e.getText());
    }

    private void parseDocument(String documentUri, final String langId, final String text) {
        if (this.isVerbose()) {
            ServerLauncher.logMsg("URI: " + documentUri);
            ServerLauncher.logMsg("langId: " + langId);
            // ServerLauncher.logMsg("Text: " + text);
        }

        List<Diagnostic> diagnostics = truffle.parse(text, langId, documentUri);
        this.reportDiagnostics(diagnostics, documentUri);
    }

    @Override
    public void didClose(DidCloseTextDocumentParams params) {
        if (this.openedFileUri2LangId.remove(params.getTextDocument().getUri()) == null) {
            ServerLauncher.logErr(
                            params.getTextDocument().getUri() + " should be closed, but was not in map of opened files.");
        }

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
// System.out.println(params);
    }

    public CompletableFuture<Object> executeCommand(ExecuteCommandParams params) {
        if ("harvest_types".equals(params.getCommand())) {
            String uri = (String) params.getArguments().get(0);
            this.truffle.exec(uri);
        }
        return CompletableFuture.completedFuture(new Object());
    }

    public boolean isVerbose() {
        return "verbose".equals(this.trace_server);
    }

}
