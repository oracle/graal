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
import java.net.URI;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.logging.Level;

import org.graalvm.tools.lsp.server.types.CompletionContext;
import org.graalvm.tools.lsp.server.types.CompletionList;
import org.graalvm.tools.lsp.server.types.DocumentHighlight;
import org.graalvm.tools.lsp.server.types.Hover;
import org.graalvm.tools.lsp.server.types.PublishDiagnosticsParams;
import org.graalvm.tools.lsp.server.types.SignatureHelp;
import org.graalvm.tools.lsp.server.types.TextDocumentContentChangeEvent;
import org.graalvm.tools.lsp.exceptions.DiagnosticsNotification;
import org.graalvm.tools.lsp.exceptions.UnknownLanguageException;
import org.graalvm.tools.lsp.instrument.LSPInstrument;
import org.graalvm.tools.lsp.server.request.AbstractRequestHandler;
import org.graalvm.tools.lsp.server.request.CompletionRequestHandler;
import org.graalvm.tools.lsp.server.request.CoverageRequestHandler;
import org.graalvm.tools.lsp.server.request.HighlightRequestHandler;
import org.graalvm.tools.lsp.server.request.HoverRequestHandler;
import org.graalvm.tools.lsp.server.request.SignatureHelpRequestHandler;
import org.graalvm.tools.lsp.server.request.SourceCodeEvaluator;
import org.graalvm.tools.lsp.server.utils.SourceUtils;
import org.graalvm.tools.lsp.server.utils.TextDocumentSurrogate;
import org.graalvm.tools.lsp.server.utils.TextDocumentSurrogateMap;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.instrumentation.TruffleInstrument.Env;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.source.Source;

import org.graalvm.tools.lsp.server.types.CompletionOptions;
import org.graalvm.tools.lsp.server.types.ServerCapabilities;
import org.graalvm.tools.lsp.server.types.SignatureHelpOptions;

/**
 * This class delegates LSP requests of {@link LanguageServerImpl} to specific implementations of
 * {@link AbstractRequestHandler}. It is responsible for wrapping requests into tasks for an
 * instance of {@link ContextAwareExecutor}, so that these tasks are executed by a Thread which has
 * entered a {@link org.graalvm.polyglot.Context}.
 *
 */
public final class TruffleAdapter implements VirtualLanguageServerFileProvider {
    private static final TruffleLogger LOG = TruffleLogger.getLogger(LSPInstrument.ID, TruffleAdapter.class);

    private final boolean developerMode;
    private TruffleInstrument.Env env;
    ContextAwareExecutor contextAwareExecutor;
    private SourceCodeEvaluator sourceCodeEvaluator;
    CompletionRequestHandler completionHandler;
    private HoverRequestHandler hoverHandler;
    private SignatureHelpRequestHandler signatureHelpHandler;
    private CoverageRequestHandler coverageHandler;
    private HighlightRequestHandler highlightHandler;
    private TextDocumentSurrogateMap surrogateMap;
    private final LanguageTriggerCharacters completionTriggerCharacters = new LanguageTriggerCharacters();
    private final LanguageTriggerCharacters signatureTriggerCharacters = new LanguageTriggerCharacters();

    public TruffleAdapter(boolean developerMode) {
        this.developerMode = developerMode;
    }

    public void register(Env environment, ContextAwareExecutor executor) {
        this.env = environment;
        this.contextAwareExecutor = executor;
    }

    public void initialize() {
        initSurrogateMap();
        createLSPRequestHandlers();
    }

    private void createLSPRequestHandlers() {
        this.sourceCodeEvaluator = new SourceCodeEvaluator(env, surrogateMap, contextAwareExecutor);
        this.completionHandler = new CompletionRequestHandler(env, surrogateMap, contextAwareExecutor, sourceCodeEvaluator, completionTriggerCharacters);
        this.hoverHandler = new HoverRequestHandler(env, surrogateMap, contextAwareExecutor, completionHandler, developerMode);
        this.signatureHelpHandler = new SignatureHelpRequestHandler(env, surrogateMap, contextAwareExecutor, sourceCodeEvaluator, completionHandler, signatureTriggerCharacters);
        this.coverageHandler = new CoverageRequestHandler(env, surrogateMap, contextAwareExecutor, sourceCodeEvaluator);
        this.highlightHandler = new HighlightRequestHandler(env, surrogateMap, contextAwareExecutor);
    }

    private void initSurrogateMap() {
        try {
            contextAwareExecutor.executeWithDefaultContext(() -> {
                LOG.log(Level.CONFIG, "Truffle Runtime: {0}", Truffle.getRuntime().getName());
                return null;
            }).get();

            this.surrogateMap = new TextDocumentSurrogateMap(env);
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
        LanguageInfo languageInfo = findLanguageInfo(langId, env.getTruffleFile(uri));
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
        Map<String, LanguageInfo> languages = env.getLanguages();
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
        surrogate.setEditorText(SourceUtils.applyTextDocumentChanges(list, surrogate.getSource(), surrogate));

        sourceCodeEvaluator.parse(surrogate);

        if (surrogate.hasCoverageData()) {
            showCoverage(uri);
        }

        return surrogate;
    }

    public List<Future<?>> parseWorkspace(URI rootUri) {
        if (rootUri == null) {
            return new ArrayList<>();
        }
        Path rootPath = Paths.get(rootUri);
        if (!Files.isDirectory(rootPath)) {
            throw new IllegalArgumentException("Root URI is not referencing a directory. URI: " + rootUri);
        }

        Future<List<Future<?>>> futureTasks = contextAwareExecutor.executeWithDefaultContext(() -> {
            Map<String, LanguageInfo> mimeType2LangInfo = new HashMap<>();
            for (LanguageInfo langInfo : env.getLanguages().values()) {
                if (langInfo.isInternal()) {
                    continue;
                }
                langInfo.getMimeTypes().stream().forEach(mimeType -> mimeType2LangInfo.put(mimeType, langInfo));
            }
            try {
                WorkspaceWalker walker = new WorkspaceWalker(mimeType2LangInfo);
                LOG.log(Level.FINE, "Start walking file tree at: {0}", rootPath);
                Files.walkFileTree(rootPath, walker);
                return walker.parsingTasks;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        try {
            return futureTasks.get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    String getLanguageId(URI uri) {
        TextDocumentSurrogate doc = surrogateMap.get(uri);
        if (doc != null) {
            return doc.getLanguageId();
        }
        try {
            return Source.findLanguage(env.getTruffleFile(uri));
        } catch (IOException ex) {
            return null;
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

    final class WorkspaceWalker implements FileVisitor<Path> {

        List<Future<?>> parsingTasks;
        private final Map<String, LanguageInfo> mimeTypesAllLang;

        WorkspaceWalker(Map<String, LanguageInfo> mimeTypesAllLang) {
            this.mimeTypesAllLang = mimeTypesAllLang;
            this.parsingTasks = new ArrayList<>();
        }

        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
            if (dir.endsWith(".git")) { // TODO(ds) where to define this?
                return FileVisitResult.SKIP_SUBTREE;
            }
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            URI uri = file.toUri();
            String mimeType = Source.findMimeType(env.getTruffleFile(uri));
            if (!mimeTypesAllLang.containsKey(mimeType)) {
                return FileVisitResult.CONTINUE;
            }
            TextDocumentSurrogate surrogate = getOrCreateSurrogate(uri, null, mimeTypesAllLang.get(mimeType));
            parsingTasks.add(contextAwareExecutor.executeWithDefaultContext(() -> parseWithEnteredContext(surrogate)));
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
            return FileVisitResult.CONTINUE;
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

    public Future<?> showCoverage(URI uri) {
        return contextAwareExecutor.executeWithDefaultContext(() -> {
            coverageHandler.showCoverageWithEnteredContext(uri);
            return null;
        });
    }

    /**
     * Clears all collected coverage data for all files. See {@link #clearCoverage(URI)} for
     * details.
     *
     * @return a future
     */
    public Future<?> clearCoverage() {
        return contextAwareExecutor.executeWithDefaultContext(() -> {
            LOG.fine("Clearing and re-parsing all files with coverage data...");
            List<PublishDiagnosticsParams> params = new ArrayList<>();
            surrogateMap.getSurrogates().stream().forEach(surrogate -> {
                surrogate.clearCoverage();
                try {
                    sourceCodeEvaluator.parse(surrogate);
                    params.add(PublishDiagnosticsParams.create(surrogate.getUri().toString(), Collections.emptyList()));
                } catch (DiagnosticsNotification e) {
                    params.addAll(e.getDiagnosticParamsCollection());
                }
            });
            LOG.fine("Clearing and re-parsing done.");

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
     * @return a future
     */
    public Future<?> clearCoverage(URI uri) {
        return contextAwareExecutor.executeWithDefaultContext(() -> {
            TextDocumentSurrogate surrogate = surrogateMap.get(uri);
            if (surrogate != null) {
                surrogate.clearCoverage();
                sourceCodeEvaluator.parse(surrogate);
            }
            throw new DiagnosticsNotification(PublishDiagnosticsParams.create(uri.toString(), Collections.emptyList()));
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

    @Override
    public boolean isVirtualFile(Path path) {
        return surrogateMap.containsSurrogate(path.toUri());
    }

    public Function<URI, TextDocumentSurrogate> surrogateGetter(LanguageInfo languageInfo) {
        return (sourceUri) -> {
            return surrogateMap.getOrCreateSurrogate(sourceUri, () -> languageInfo);
        };
    }
}
