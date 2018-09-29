package de.hpi.swa.trufflelsp.server;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.SignatureHelp;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.instrumentation.TruffleInstrument.Env;
import com.oracle.truffle.api.nodes.LanguageInfo;

import de.hpi.swa.trufflelsp.api.ContextAwareExecutorWrapper;
import de.hpi.swa.trufflelsp.api.ContextAwareExecutorWrapperRegistry;
import de.hpi.swa.trufflelsp.api.VirtualLanguageServerFileProvider;
import de.hpi.swa.trufflelsp.exceptions.DiagnosticsNotification;
import de.hpi.swa.trufflelsp.exceptions.UnknownLanguageException;
import de.hpi.swa.trufflelsp.server.request.CompletionRequestHandler;
import de.hpi.swa.trufflelsp.server.request.CoverageRequestHandler;
import de.hpi.swa.trufflelsp.server.request.DefinitionRequestHandler;
import de.hpi.swa.trufflelsp.server.request.DocumentSymbolRequestHandler;
import de.hpi.swa.trufflelsp.server.request.HoverRequestHandler;
import de.hpi.swa.trufflelsp.server.request.SignatureHelpRequestHandler;
import de.hpi.swa.trufflelsp.server.request.SourceCodeEvaluator;
import de.hpi.swa.trufflelsp.server.utils.SourceUtils;
import de.hpi.swa.trufflelsp.server.utils.TextDocumentSurrogate;

public class TruffleAdapter implements VirtualLanguageServerFileProvider, ContextAwareExecutorWrapperRegistry {

    protected final Map<URI, TextDocumentSurrogate> uri2TextDocumentSurrogate = new HashMap<>();

    private final TruffleInstrument.Env env;
    private ContextAwareExecutorWrapper contextAwareExecutor;
    private SourceCodeEvaluator sourceCodeEvaluator;
    private CompletionRequestHandler completionHandler;
    private DocumentSymbolRequestHandler documentSymbolHandler;
    private DefinitionRequestHandler definitionHandler;
    private HoverRequestHandler hoverHandler;
    private SignatureHelpRequestHandler signatureHelpHandler;
    private CoverageRequestHandler coverageHandler;

    public TruffleAdapter(Env env) {
        this.env = env;
    }

    public void register(ContextAwareExecutorWrapper executor) {
        this.contextAwareExecutor = executor;
        createLSPRequestHandlers();
    }

    private void createLSPRequestHandlers() {
        this.sourceCodeEvaluator = new SourceCodeEvaluator(env, uri2TextDocumentSurrogate, contextAwareExecutor);
        this.completionHandler = new CompletionRequestHandler(env, uri2TextDocumentSurrogate, contextAwareExecutor, sourceCodeEvaluator);
        this.documentSymbolHandler = new DocumentSymbolRequestHandler(env, uri2TextDocumentSurrogate, contextAwareExecutor);
        this.definitionHandler = new DefinitionRequestHandler(env, uri2TextDocumentSurrogate, contextAwareExecutor, sourceCodeEvaluator, documentSymbolHandler);
        this.hoverHandler = new HoverRequestHandler(env, uri2TextDocumentSurrogate, contextAwareExecutor, completionHandler);
        this.signatureHelpHandler = new SignatureHelpRequestHandler(env, uri2TextDocumentSurrogate, contextAwareExecutor, sourceCodeEvaluator);
        this.coverageHandler = new CoverageRequestHandler(env, uri2TextDocumentSurrogate, contextAwareExecutor, sourceCodeEvaluator);
    }

    private TextDocumentSurrogate getOrCreateSurrogate(URI uri, String text, LanguageInfo languageInfo) {
        TextDocumentSurrogate surrogate = uri2TextDocumentSurrogate.computeIfAbsent(uri,
                        (_uri) -> new TextDocumentSurrogate(_uri, languageInfo, env.getCompletionTriggerCharacters(languageInfo.getId())));
        surrogate.setEditorText(text);
        return surrogate;
    }

    public void didClose(URI uri) {
        uri2TextDocumentSurrogate.remove(uri);
    }

    public Future<CallTarget> parse(final String text, final String langId, final URI uri) {
        return contextAwareExecutor.executeWithDefaultContext(() -> parseWithEnteredContext(text, langId, uri));
    }

    protected CallTarget parseWithEnteredContext(final String text, final String langId, final URI uri) throws DiagnosticsNotification {
        LanguageInfo languageInfo = findLanguageInfo(langId, uri);
        TextDocumentSurrogate surrogate = getOrCreateSurrogate(uri, text, languageInfo);
        return sourceCodeEvaluator.parse(surrogate);
    }

    /**
     * Special handling needed, because some LSP clients send a MIME type as langId.
     *
     * @param langId an id for a language, e.g. "sl" or "python", or a MIME type
     * @param uri of the concerning file
     * @return a language info
     */
    private LanguageInfo findLanguageInfo(final String langId, final URI uri) {
        if (env.getLanguages().containsKey(langId)) {
            return env.getLanguages().get(langId);
        }

        String possibleMimeType = langId;
        String actualLangId = org.graalvm.polyglot.Source.findLanguage(possibleMimeType);
        if (actualLangId == null) {
            try {
                actualLangId = org.graalvm.polyglot.Source.findLanguage(new File(uri));
            } catch (IOException e) {
            }

            if (actualLangId == null) {
                actualLangId = langId;
            }
        }

        if (!env.getLanguages().containsKey(actualLangId)) {
            throw new UnknownLanguageException("Unknown language: " + actualLangId + ". Known languages are: " + env.getLanguages().keySet());
        }

        return env.getLanguages().get(actualLangId);
    }

    public Future<Void> processChangesAndParse(List<? extends TextDocumentContentChangeEvent> list, URI uri) {
        return contextAwareExecutor.executeWithDefaultContext(() -> {
            processChangesAndParseWithContextEntered(list, uri);
            return null;
        });
    }

    protected void processChangesAndParseWithContextEntered(List<? extends TextDocumentContentChangeEvent> list, URI uri) throws DiagnosticsNotification {
        if (list.isEmpty()) {
            return;
        }

        TextDocumentSurrogate surrogate = uri2TextDocumentSurrogate.get(uri);
        surrogate.getChangeEventsSinceLastSuccessfulParsing().addAll(list);
        surrogate.setLastChange(list.get(list.size() - 1));
        surrogate.setEditorText(SourceUtils.applyTextDocumentChanges(list, surrogate.getEditorText(), surrogate));

        sourceCodeEvaluator.parse(surrogate);

        if (surrogate.hasCoverageData()) {
            showCoverage(uri);
        }
    }

    public Future<List<? extends SymbolInformation>> documentSymbol(URI uri) {
        return contextAwareExecutor.executeWithDefaultContext(() -> documentSymbolHandler.documentSymbolWithEnteredContext(uri));
    }

    /**
     * Provides completions for a specific position in the document. If line or column are out of
     * range, items of global scope (top scope) are provided.
     *
     * @param uri
     * @param line 0-based line number
     * @param column 0-based column number (character offset)
     * @return a {@link Future} of {@link CompletionList} containing all completions for the cursor
     *         position
     */
    public Future<CompletionList> completion(final URI uri, int line, int column) {
        return contextAwareExecutor.executeWithDefaultContext(() -> completionHandler.completionWithEnteredContext(uri, line, column));
    }

    public Future<List<? extends Location>> definition(URI uri, int line, int character) {
        return contextAwareExecutor.executeWithNestedContext(() -> definitionHandler.definitionWithEnteredContext(uri, line, character));
    }

    public Future<Hover> hover(URI uri, int line, int column) {
        return contextAwareExecutor.executeWithNestedContext(() -> hoverHandler.hoverWithEnteredContext(uri, line, column));
    }

    public Future<SignatureHelp> signatureHelp(URI uri, int line, int character) {
        return contextAwareExecutor.executeWithDefaultContext(() -> signatureHelpHandler.signatureHelpWithEnteredContext(uri, line, character));
    }

    public Future<Boolean> runCoverageAnalysis(final URI uri) {
        return contextAwareExecutor.executeWithNestedContext(() -> {
            return coverageHandler.runCoverageAnalysisWithNestedEnteredContext(uri);
        });
    }

    public Future<?> showCoverage(URI uri) {
        return contextAwareExecutor.executeWithDefaultContext(() -> {
            coverageHandler.showCoverageWithEnteredContext(uri);
            return null;
        });
    }

    public Future<List<String>> getCompletionTriggerCharactersOfAllLanguages() {
        return contextAwareExecutor.executeWithDefaultContext(() -> completionHandler.getCompletionTriggerCharactersWithEnteredContext());
    }

    public Future<List<String>> getCompletionTriggerCharacters(String langId) {
        return contextAwareExecutor.executeWithDefaultContext(() -> completionHandler.getCompletionTriggerCharactersWithEnteredContext(langId));
    }

    /**
     * Clears all collected coverage data for all files. See {@link #clearCoverage(URI)} for
     * details.
     */
    public Future<?> clearCoverage() {
        return contextAwareExecutor.executeWithDefaultContext(() -> {
            System.out.println("Clearing and re-parsing all files with coverage data...");
            List<PublishDiagnosticsParams> params = new ArrayList<>();
            uri2TextDocumentSurrogate.entrySet().stream().forEach(entry -> {
                TextDocumentSurrogate surrogate = entry.getValue();
                surrogate.clearCoverage();
                try {
                    sourceCodeEvaluator.parse(surrogate);
                    params.add(new PublishDiagnosticsParams(entry.getKey().toString(), Collections.emptyList()));
                } catch (DiagnosticsNotification e) {
                    params.addAll(e.getDiagnosticParamsCollection());
                }
            });
            System.out.println("Clearing and re-parsing done.");

            throw new DiagnosticsNotification(params);
        });
    }

    /**
     * Clears the coverage data for a specific URI. Clearing means removing all Diagnostics used to
     * highlight covered code. To avoid hiding syntax errors, the URIs source is parsed again. If
     * errors occur during parsing, a {@link DiagnosticsNotification} is thrown. If not, we still
     * have to clear all Diagnostics by throwing an empty {@link DiagnosticsNotification}
     * afterwards.
     *
     * @param uri to source to clear coverage data for
     */
    public Future<?> clearCoverage(URI uri) {
        return contextAwareExecutor.executeWithDefaultContext(() -> {
            TextDocumentSurrogate surrogate = uri2TextDocumentSurrogate.get(uri);
            surrogate.clearCoverage();
            sourceCodeEvaluator.parse(surrogate);

            throw new DiagnosticsNotification(new PublishDiagnosticsParams(uri.toString(), Collections.emptyList()));
        });
    }

    public String getSourceText(Path path) {
        TextDocumentSurrogate surrogate = uri2TextDocumentSurrogate.get(path.toUri());
        return surrogate != null ? surrogate.getEditorText() : null;
    }

    public boolean isVirtualFile(Path path) {
        return uri2TextDocumentSurrogate.containsKey(path.toUri());
    }
}
