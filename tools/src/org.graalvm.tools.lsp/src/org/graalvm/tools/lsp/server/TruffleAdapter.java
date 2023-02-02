/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.logging.Level;

import org.graalvm.tools.api.lsp.LSPCommand;
import org.graalvm.tools.api.lsp.LSPExtension;
import org.graalvm.tools.api.lsp.LSPServerAccessor;
import org.graalvm.tools.lsp.exceptions.DiagnosticsNotification;
import org.graalvm.tools.lsp.exceptions.UnknownLanguageException;
import org.graalvm.tools.lsp.server.request.AbstractRequestHandler;
import org.graalvm.tools.lsp.server.request.CompletionRequestHandler;
import org.graalvm.tools.lsp.server.request.CoverageRequestHandler;
import org.graalvm.tools.lsp.server.request.HighlightRequestHandler;
import org.graalvm.tools.lsp.server.request.HoverRequestHandler;
import org.graalvm.tools.lsp.server.request.SignatureHelpRequestHandler;
import org.graalvm.tools.lsp.server.request.SourceCodeEvaluator;
import org.graalvm.tools.lsp.server.types.CompletionContext;
import org.graalvm.tools.lsp.server.types.CompletionList;
import org.graalvm.tools.lsp.server.types.CompletionOptions;
import org.graalvm.tools.lsp.server.types.Coverage;
import org.graalvm.tools.lsp.server.types.DocumentHighlight;
import org.graalvm.tools.lsp.server.types.ExecuteCommandParams;
import org.graalvm.tools.lsp.server.types.Hover;
import org.graalvm.tools.lsp.server.types.ServerCapabilities;
import org.graalvm.tools.lsp.server.types.SignatureHelp;
import org.graalvm.tools.lsp.server.types.SignatureHelpOptions;
import org.graalvm.tools.lsp.server.types.TextDocumentContentChangeEvent;
import org.graalvm.tools.lsp.server.utils.SourceUtils;
import org.graalvm.tools.lsp.server.utils.TextDocumentSurrogate;
import org.graalvm.tools.lsp.server.utils.TextDocumentSurrogateMap;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.InstrumentInfo;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.instrumentation.TruffleInstrument.Env;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.source.Source;

/**
 * This class delegates LSP requests of {@link LanguageServerImpl} to specific implementations of
 * {@link AbstractRequestHandler}. It is responsible for wrapping requests into tasks for an
 * instance of {@link ContextAwareExecutor}, so that these tasks are executed by a Thread which has
 * entered a {@link org.graalvm.polyglot.Context}.
 *
 */
public final class TruffleAdapter implements VirtualLanguageServerFileProvider {

    private final boolean developerMode;
    private final TruffleLogger logger;
    private final TruffleInstrument.Env envMain;
    private TruffleInstrument.Env envInternal;
    ContextAwareExecutor contextAwareExecutor;
    private SourceCodeEvaluator sourceCodeEvaluator;
    CompletionRequestHandler completionHandler;
    private HoverRequestHandler hoverHandler;
    private SignatureHelpRequestHandler signatureHelpHandler;
    private CoverageRequestHandler coverageHandler;
    private HighlightRequestHandler highlightHandler;
    private List<LSPCommand> extensionCommands;
    private LSPServerAccessor lspServer;
    private TextDocumentSurrogateMap surrogateMap;
    private final LanguageTriggerCharacters completionTriggerCharacters = new LanguageTriggerCharacters();
    private final LanguageTriggerCharacters signatureTriggerCharacters = new LanguageTriggerCharacters();

    public TruffleAdapter(TruffleInstrument.Env mainEnv, boolean developerMode) {
        this.envMain = mainEnv;
        this.developerMode = developerMode;
        this.logger = envMain.getLogger("");
    }

    public void register(Env environment, ContextAwareExecutor executor) {
        this.envInternal = environment;
        this.contextAwareExecutor = executor;
        initSurrogateMap();
        createLSPRequestHandlers();
    }

    public TruffleLogger getLogger() {
        return logger;
    }

    private void createLSPRequestHandlers() {
        this.sourceCodeEvaluator = new SourceCodeEvaluator(envMain, envInternal, surrogateMap, contextAwareExecutor);
        this.completionHandler = new CompletionRequestHandler(envMain, envInternal, surrogateMap, contextAwareExecutor, sourceCodeEvaluator, completionTriggerCharacters);
        this.hoverHandler = new HoverRequestHandler(envMain, envInternal, surrogateMap, contextAwareExecutor, completionHandler, developerMode);
        this.signatureHelpHandler = new SignatureHelpRequestHandler(envMain, envInternal, surrogateMap, contextAwareExecutor, sourceCodeEvaluator, completionHandler, signatureTriggerCharacters);
        this.coverageHandler = new CoverageRequestHandler(envMain, envInternal, surrogateMap, contextAwareExecutor, sourceCodeEvaluator);
        this.highlightHandler = new HighlightRequestHandler(envMain, envInternal, surrogateMap, contextAwareExecutor);
    }

    private void initSurrogateMap() {
        try {
            contextAwareExecutor.executeWithDefaultContext(() -> {
                logger.log(Level.CONFIG, "Truffle Runtime: {0}", Truffle.getRuntime().getName());
                return null;
            }).get();

            this.surrogateMap = new TextDocumentSurrogateMap(envInternal);
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    TextDocumentSurrogate getOrCreateSurrogate(URI uri, String text, LanguageInfo languageInfo) {
        TextDocumentSurrogate surrogate = surrogateMap.getOrCreateSurrogate(uri, languageInfo);
        surrogate.setEditorText(text);
        return surrogate;
    }

    public void didClose(URI uri) {
        surrogateMap.remove(uri);
    }

    public Future<CallTarget> parse(final String text, final String langId, final URI uri) {
        return contextAwareExecutor.executeWithDefaultContext(() -> parseWithEnteredContext(text, langId, uri));
    }

    protected CallTarget parseWithEnteredContext(final String text, final String langId, final URI uri) throws DiagnosticsNotification {
        LanguageInfo languageInfo = findLanguageInfo(langId, envInternal.getTruffleFile(null, uri));
        TextDocumentSurrogate surrogate = getOrCreateSurrogate(uri, text, languageInfo);
        return parseWithEnteredContext(surrogate);
    }

    CallTarget parseWithEnteredContext(TextDocumentSurrogate surrogate) throws DiagnosticsNotification {
        return sourceCodeEvaluator.parse(surrogate);
    }

    public Future<?> reparse(URI uri) {
        TextDocumentSurrogate surrogate = surrogateMap.get(uri);
        return contextAwareExecutor.executeWithDefaultContext(() -> parseWithEnteredContext(surrogate));
    }

    /**
     * Special handling needed, because some LSP clients send a MIME type as langId.
     *
     * @param langId an id for a language, e.g. "sl" or "python", or a MIME type
     * @param truffleFile of the concerning file
     * @return a language info
     */
    private LanguageInfo findLanguageInfo(final String langId, final TruffleFile truffleFile) {
        Map<String, LanguageInfo> languages = envInternal.getLanguages();
        LanguageInfo langInfo = languages.get(langId);
        if (langInfo != null) {
            return langInfo;
        }

        String possibleMimeType = langId;
        String actualLangId = Source.findLanguage(possibleMimeType);
        if (actualLangId == null) {
            try {
                actualLangId = Source.findLanguage(truffleFile);
            } catch (IOException e) {
            }

            if (actualLangId == null) {
                actualLangId = langId;
            }
        }

        langInfo = languages.get(actualLangId);
        if (langInfo == null) {
            throw new UnknownLanguageException("Unknown language: " + actualLangId + ". Known languages are: " + languages.keySet());
        }

        return langInfo;
    }

    public Future<TextDocumentSurrogate> processChangesAndParse(List<? extends TextDocumentContentChangeEvent> list, URI uri) {
        return contextAwareExecutor.executeWithDefaultContext(() -> processChangesAndParseWithContextEntered(list, uri));
    }

    protected TextDocumentSurrogate processChangesAndParseWithContextEntered(List<? extends TextDocumentContentChangeEvent> list, URI uri) throws DiagnosticsNotification {
        TextDocumentSurrogate surrogate = surrogateMap.get(uri);

        if (surrogate == null) {
            throw new IllegalStateException("No internal mapping for uri=" + uri.toString() + " found.");
        }

        if (list.isEmpty()) {
            return surrogate;
        }

        surrogate.getChangeEventsSinceLastSuccessfulParsing().addAll(list);
        surrogate.setLastChange(list.get(list.size() - 1));
        surrogate.setEditorText(SourceUtils.applyTextDocumentChanges(list, surrogate.getSource(), surrogate, logger));

        sourceCodeEvaluator.parse(surrogate);

        return surrogate;
    }

    String getLanguageId(URI uri) {
        TextDocumentSurrogate doc = surrogateMap.get(uri);
        if (doc != null) {
            return doc.getLanguageId();
        }

        Future<String> future = contextAwareExecutor.executeWithDefaultContext(() -> {
            try {
                return Source.findLanguage(envInternal.getTruffleFile(null, uri));
            } catch (IOException ex) {
                return null;
            }
        });

        try {
            return future.get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    public void setServerCapabilities(String languageId, ServerCapabilities capabilities) {
        CompletionOptions completionProvider = capabilities.getCompletionProvider();
        if (completionProvider != null) {
            List<String> triggerCharacters = completionProvider.getTriggerCharacters();
            if (triggerCharacters != null) {
                completionTriggerCharacters.add(languageId, triggerCharacters);
            }
        }
        SignatureHelpOptions signatureHelpProvider = capabilities.getSignatureHelpProvider();
        if (signatureHelpProvider != null) {
            List<String> triggerCharacters = signatureHelpProvider.getTriggerCharacters();
            if (triggerCharacters != null) {
                signatureTriggerCharacters.add(languageId, triggerCharacters);
            }
        }
    }

    /**
     * Provides completions for a specific position in the document. If line or column are out of
     * range, items of global scope (top scope) are provided.
     *
     * @param uri
     * @param line 0-based line number
     * @param column 0-based column number (character offset)
     * @param completionContext has kind and completion character if client supports it
     * @return a {@link Future} of {@link CompletionList} containing all completions for the cursor
     *         position
     */
    public Future<CompletionList> completion(final URI uri, int line, int column, CompletionContext completionContext) {
        return contextAwareExecutor.executeWithDefaultContext(() -> completionHandler.completionWithEnteredContext(uri, line, column, completionContext));
    }

    public Future<Hover> hover(URI uri, int line, int column) {
        return contextAwareExecutor.executeWithDefaultContext(() -> hoverHandler.hoverWithEnteredContext(uri, line, column));
    }

    public Future<SignatureHelp> signatureHelp(URI uri, int line, int character) {
        return contextAwareExecutor.executeWithNestedContext(() -> signatureHelpHandler.signatureHelpWithEnteredContext(uri, line, character), true);
    }

    public Future<Boolean> runCoverageAnalysis(final URI uri) {
        Future<Boolean> future = contextAwareExecutor.executeWithDefaultContext(() -> {
            contextAwareExecutor.resetContextCache(); // We choose coverage runs as checkpoints to
                                                      // clear the cached context. A coverage run
                                                      // can be triggered by the user via the
                                                      // editor, so that the user can actively
                                                      // control the reset of the current cached
                                                      // context.
            Future<Boolean> futureCoverage = contextAwareExecutor.executeWithNestedContext(() -> coverageHandler.runCoverageAnalysisWithEnteredContext(uri), true);
            try {
                return futureCoverage.get();
            } catch (ExecutionException e) {
                if (e.getCause() instanceof Exception) {
                    throw (Exception) e.getCause();
                } else {
                    throw e;
                }
            }
        });
        return future;
    }

    public Future<Coverage> getCoverage(URI uri) {
        return contextAwareExecutor.executeWithDefaultContext(() -> {
            return coverageHandler.getCoverageWithEnteredContext(uri);
        });
    }

    public Future<List<? extends DocumentHighlight>> documentHighlight(URI uri, int line, int character) {
        return contextAwareExecutor.executeWithDefaultContext(() -> highlightHandler.highlightWithEnteredContext(uri, line, character));
    }

    public boolean hasCoverageData(URI uri) {
        TextDocumentSurrogate surrogate = surrogateMap.get(uri);
        return surrogate != null ? surrogate.hasCoverageData() : false;
    }

    @Override
    public String getSourceText(Path path) {
        if (surrogateMap == null) {
            return null;
        }

        TextDocumentSurrogate surrogate = surrogateMap.get(path.toUri());
        return surrogate != null ? surrogate.getEditorText() : null;
    }

    public Source getSource(URI uri) {
        if (surrogateMap == null) {
            return null;
        }

        TextDocumentSurrogate surrogate = surrogateMap.get(uri);
        return surrogate != null ? surrogate.getSource() : null;
    }

    @Override
    public boolean isVirtualFile(Path path) {
        return surrogateMap.containsSurrogate(path.toUri());
    }

    public Function<URI, TextDocumentSurrogate> surrogateGetter(LanguageInfo languageInfo) {
        return (sourceUri) -> {
            return surrogateMap.getOrCreateSurrogate(sourceUri, () -> languageInfo);
        };
    }

    public void initializeLSPServer(LSPServerAccessor server) {
        this.lspServer = server;
    }

    private List<LSPCommand> getExternalCommands() {
        if (extensionCommands == null) {
            extensionCommands = new ArrayList<>();
            for (InstrumentInfo instrument : envMain.getInstruments().values()) {
                if ("lsp".equals(instrument.getId())) {
                    continue;
                }
                LSPExtension extension = envMain.lookup(instrument, LSPExtension.class);
                if (extension != null) {
                    for (LSPCommand command : extension.getCommands()) {
                        extensionCommands.add(command);
                    }
                }
            }
        }
        return extensionCommands;
    }

    public Collection<String> getExtensionCommandNames() {
        ArrayList<String> result = new ArrayList<>();
        for (LSPCommand command : getExternalCommands()) {
            result.add(command.getName());
        }
        return result;
    }

    public Future<?> createExtensionCommand(ExecuteCommandParams params) {
        String commandName = params.getCommand();
        for (LSPCommand command : getExternalCommands()) {
            if (commandName.equals(command.getName())) {
                List<Object> args = params.getArguments();
                return contextAwareExecutor.executeWithNestedContext(() -> command.execute(lspServer, envInternal, args), command.getTimeoutMillis(), () -> command.onTimeout(args));
            }
        }
        return null;
    }
}
