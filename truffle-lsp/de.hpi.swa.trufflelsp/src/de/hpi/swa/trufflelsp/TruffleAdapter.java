package de.hpi.swa.trufflelsp;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.DocumentHighlight;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.MarkedString;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Scope;
import com.oracle.truffle.api.TruffleContext;
import com.oracle.truffle.api.TruffleException;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.ContextsListener;
import com.oracle.truffle.api.instrumentation.EventBinding;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.ExecutionEventListener;
import com.oracle.truffle.api.instrumentation.ExecutionEventNodeFactory;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.SourceFilter;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter.IndexRange;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter.SourcePredicate;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.StandardTags.ExpressionTag;
import com.oracle.truffle.api.instrumentation.StandardTags.StatementTag;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

import de.hpi.swa.trufflelsp.NearestSectionsFinder.NearestSections;
import de.hpi.swa.trufflelsp.NearestSectionsFinder.NodeLocationType;
import de.hpi.swa.trufflelsp.exceptions.EvaluationResultException;
import de.hpi.swa.trufflelsp.exceptions.InvalidCoverageScriptURI;
import de.hpi.swa.trufflelsp.filesystem.VirtualLSPFileProvider;
import de.hpi.swa.trufflelsp.server.DiagnosticsPublisher;

public class TruffleAdapter implements ContextsListener, VirtualLSPFileProvider {
    private static final int SORTING_PRIORITY_LOCALS = 1;
    private static final int SORTING_PRIORITY_GLOBALS = 2;
    private static final String COVERAGE_SCRIPT = "COVERAGE_SCRIPT:";
// public static final String NO_TYPES_HARVESTED = "NO_TYPES_HARVESTED";
    private static final Node HAS_SIZE = Message.HAS_SIZE.createNode();
    private static final Node KEYS = Message.KEYS.createNode();
    private static final Node IS_INSTANTIABLE = Message.IS_INSTANTIABLE.createNode();
    private static final Node IS_EXECUTABLE = Message.IS_EXECUTABLE.createNode();

    protected final Map<URI, TextDocumentSurrogate> uri2TextDocumentSurrogate = new HashMap<>();
    protected final Map<SourceSection, SourceSection> section2definition = new HashMap<>();

    private final TruffleInstrument.Env env;
    private final PrintWriter err;
// private final PrintWriter info;
    private final SourceProvider sourceProvider;
    private DiagnosticsPublisher diagnosticsPublisher;
    private TruffleContext globalInnerContext;
    private boolean isContextBeingCreated = false;
    private Function<VirtualLSPFileProvider, TruffleContext> contextProvider;

    private static final class SourceUriFilter implements SourcePredicate {

        public boolean test(Source source) {
            return source.getName().startsWith("file://"); // TODO(ds) how to filter? tag is STATEMENT && source.isAvailable()?
        }
    }

    protected static final class SourceFix {
        public final String text;
        public final int character;
        public final boolean isObjectPropertyCompletion;

        public SourceFix(String text, int character, boolean isObjectPropertyCompletion) {
            this.text = text;
            this.character = character;
            this.isObjectPropertyCompletion = isObjectPropertyCompletion;
        }
    }

    public TruffleAdapter(TruffleInstrument.Env env, Function<VirtualLSPFileProvider, TruffleContext> contextProvider) {
        assert env != null;
        this.env = env;
        this.contextProvider = contextProvider;
        this.err = new PrintWriter(env.err());
// this.info = new PrintWriter(env.out());
        this.sourceProvider = new SourceProvider();
        env.getInstrumenter().attachLoadSourceListener(SourceFilter.newBuilder().sourceIs(new SourceUriFilter()).build(), this.sourceProvider, false);
        env.getInstrumenter().attachLoadSourceSectionListener(SourceSectionFilter.newBuilder().sourceIs(new SourceUriFilter()).build(), this.sourceProvider, false);
        env.getInstrumenter().attachContextsListener(this, true);
    }

    public void setDiagnosticsPublisher(DiagnosticsPublisher diagnosticsPublisher) {
        assert diagnosticsPublisher != null;
        this.diagnosticsPublisher = diagnosticsPublisher;
    }

    public synchronized void didOpen(URI uri, String text, String langId) {
        this.uri2TextDocumentSurrogate.put(uri, new TextDocumentSurrogate(uri, langId, text));
    }

    public void didClose(URI uri) {
        this.uri2TextDocumentSurrogate.remove(uri);
    }

    public synchronized void parse(final String text, final String langId, final URI uri) {
        TextDocumentSurrogate surrogate = this.uri2TextDocumentSurrogate.computeIfAbsent(uri, (_uri) -> new TextDocumentSurrogate(_uri, langId));
        surrogate.setEditorText(text);

        doWithGlobalInnerContext(() -> parse(surrogate));
    }

    protected synchronized CallTarget parse(final TextDocumentSurrogate surrogate) {
        CallTarget callTarget = null;
        try {
            callTarget = parseInternal(surrogate);
        } catch (IllegalStateException e) {
            this.err.println(e.getLocalizedMessage());
            this.err.flush();
        } catch (RuntimeException e) {
            if (e instanceof TruffleException) {
                this.diagnosticsPublisher.addDiagnostics(surrogate.getUri(), new Diagnostic(getRangeFrom((TruffleException) e), e.getMessage(), DiagnosticSeverity.Error, "Truffle"));
            } else {
                throw new RuntimeException(e);
            }
        }

        return callTarget;
    }

    private static Range getRangeFrom(TruffleException te) {
        Range range = new Range(new Position(), new Position());
        SourceSection sourceLocation = te.getSourceLocation() != null ? te.getSourceLocation()
                        : (te.getLocation() != null ? te.getLocation().getEncapsulatingSourceSection() : null);
        if (sourceLocation != null && sourceLocation.isAvailable()) {
            range = sourceSectionToRange(sourceLocation);
        }
        return range;
    }

    private CallTarget parseInternal(final TextDocumentSurrogate surrogate) {
        String langId = surrogate.getLangId();
        URI uri = surrogate.getUri();
        String text = surrogate.getCurrentText();
        this.sourceProvider.remove(langId, uri);

        System.out.println("Parsing " + langId + " " + uri);
        try {
            Source source = Source.newBuilder(new File(uri)).name(uri.toString()).language(langId).content(text).build();
            CallTarget callTarget = env.parse(source);

            SourceWrapper sourceWrapper = this.sourceProvider.getLoadedSource(langId, uri);
            if (sourceWrapper == null) {
                System.out.println("!!! Cannot lookup Source. No parsed Source found for URI: " + uri);
                return null;
            }
            sourceWrapper.setParsingSuccessful(true);
            sourceWrapper.setText(text);
            sourceWrapper.setCallTarget(callTarget);
            surrogate.setParsedSourceWrapper(sourceWrapper);
            surrogate.getChangeEventsSinceLastSuccessfulParsing().clear();
            return callTarget;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public synchronized void processChangesAndParse(List<? extends TextDocumentContentChangeEvent> list, URI uri) {
        if (list.isEmpty()) {
            return;
        }

        TextDocumentSurrogate surrogate = this.uri2TextDocumentSurrogate.get(uri);
        surrogate.getChangeEventsSinceLastSuccessfulParsing().addAll(list);
        surrogate.setEditorText(applyTextDocumentChanges(list, surrogate.getEditorText()));

        doWithGlobalInnerContext(() -> parse(surrogate));
    }

    private static String applyTextDocumentChanges(List<? extends TextDocumentContentChangeEvent> list, String text) {
        TextMap textMap = TextMap.fromCharSequence(text);
        StringBuilder sb = new StringBuilder(text);
        for (TextDocumentContentChangeEvent event : list) {
            Range range = event.getRange();
            Position start = range.getStart();
            Position end = range.getEnd();
            int startLine = start.getLine() + 1;
            int endLine = end.getLine() + 1;
            int replaceBegin;
            int replaceEnd;
            if (textMap.lineCount() < startLine) {
                assert start.getCharacter() == 0 : start.getCharacter();
                assert textMap.finalNL;
                assert textMap.lineCount() < endLine;
                assert end.getCharacter() == 0 : end.getCharacter();

                replaceBegin = textMap.length();
                replaceEnd = replaceBegin;
            } else if (textMap.lineCount() < endLine) {
                replaceBegin = textMap.lineStartOffset(startLine) + start.getCharacter();
                replaceEnd = text.length();
            } else {
                replaceBegin = textMap.lineStartOffset(startLine) + start.getCharacter();
                replaceEnd = textMap.lineStartOffset(endLine) + end.getCharacter();
            }
            sb.replace(replaceBegin, replaceEnd, event.getText());
        }
        return sb.toString();
    }

    public synchronized List<? extends SymbolInformation> getSymbolInfo(URI uri) {
        List<SymbolInformation> symbolInformation = new ArrayList<>();

        TextDocumentSurrogate surrogate = this.uri2TextDocumentSurrogate.get(uri);
        Iterable<Scope> topScopes = env.findTopScopes(surrogate.getLangId());
        Map<String, Map<Object, Object>> scopeMap = scopesToObjectMap(topScopes);
        for (Entry<String, Map<Object, Object>> scopeEntry : scopeMap.entrySet()) {
            for (Entry<Object, Object> entry : scopeEntry.getValue().entrySet()) {
                if (entry.getValue() instanceof TruffleObject) {
                    TruffleObject truffleObjVal = (TruffleObject) entry.getValue();

                    SourceSection sourceLocation = this.findSourceLocation(truffleObjVal);
                    if (sourceLocation != null && uri.equals(sourceLocation.getSource().getURI())) {
                        Range range = sourceSectionToRange(sourceLocation);

                        SymbolKind kind = SymbolKind.Variable;
                        boolean isExecutable = ForeignAccess.sendIsExecutable(IS_EXECUTABLE, truffleObjVal);
                        boolean isInstatiatable = ForeignAccess.sendIsExecutable(IS_INSTANTIABLE, truffleObjVal);
                        if (isExecutable) {
                            kind = SymbolKind.Function;
                        }
                        if (isInstatiatable) {
                            kind = SymbolKind.Class;
                        }

                        SymbolInformation si = new SymbolInformation(entry.getKey().toString(), kind, new Location(uri.toString(), range), scopeEntry.getKey());
                        symbolInformation.add(si);
                    }
                }
            }
        }
        return symbolInformation;
    }

    public static LinkedHashMap<String, Map<Object, Object>> scopesToObjectMap(Iterable<Scope> scopes) {
        LinkedHashMap<String, Map<Object, Object>> map = new LinkedHashMap<>();
        for (Scope scope : scopes) {
            Object variables = scope.getVariables();
            if (variables instanceof TruffleObject) {
                TruffleObject truffleObj = (TruffleObject) variables;
                try {
                    // TODO(ds) check KEY_INFO has keys?
                    TruffleObject keys = ForeignAccess.sendKeys(KEYS, truffleObj, true);
                    boolean hasSize = ForeignAccess.sendHasSize(HAS_SIZE, keys);
                    if (!hasSize) {
                        continue;
                    }

                    map.put(scope.getName(), ObjectStructures.asMap(new ObjectStructures.MessageNodes(), truffleObj));
                } catch (UnsupportedMessageException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        return map;
    }

    public synchronized CompletionList getCompletions(final URI uri, int line, int originalCharacter) {
        CompletionList completions = new CompletionList();
        completions.setIsIncomplete(false);

        TextDocumentSurrogate textDocumentSurrogate = this.uri2TextDocumentSurrogate.get(uri);
        String langId = textDocumentSurrogate.getLangId();
        boolean isObjectPropertyCompletion = false;

        int character = originalCharacter;
        SourceWrapper sourceWrapper = this.sourceProvider.getLoadedSource(langId, uri);
        if (sourceWrapper == null || !sourceWrapper.isParsingSuccessful()) {
            // Parsing failed, now try to fix simple syntax errors
            System.out.println("No source wrapper found, fixing source.");
            SourceFix sourceFix = fixSourceAtPosition(textDocumentSurrogate, line, character);
            if (sourceFix != null) {
                try {
                    textDocumentSurrogate.setFixedText(sourceFix.text);

                    doWithGlobalInnerContext(() -> parseInternal(textDocumentSurrogate));
                    sourceWrapper = this.sourceProvider.getLoadedSource(langId, uri);
                    character = sourceFix.character;
                    isObjectPropertyCompletion = sourceFix.isObjectPropertyCompletion;
                } catch (IllegalStateException e) {
                    this.err.println(e.getLocalizedMessage());
                    this.err.flush();
                } catch (RuntimeException e) {
                    if (!(e instanceof TruffleException)) {
                        throw new IllegalStateException(e);
                    }
                }
            }
        }

        try {
            if (sourceWrapper != null) {
                Source source = sourceWrapper.getSource();

                if (isLineValid(line, source)) {
                    int oneBasedLineNumber = zeroBasedLineToOneBasedLine(line, source);
                    NearestNodeHolder nearestNodeHolder = NearestSectionsFinder.findNearestNode(oneBasedLineNumber, character, source, env);
                    Node nearestNode = nearestNodeHolder.getNearestNode();
                    NodeLocationType locationType = nearestNodeHolder.getLocationType();

                    System.out.println("nearestNode: " +
                                    (nearestNode != null ? nearestNode.getClass().getSimpleName() : "--NULL--") + "\t-" + locationType + "-\t" +
                                    (nearestNode != null ? nearestNode.getSourceSection() : ""));

                    if (nearestNode instanceof InstrumentableNode && ((InstrumentableNode) nearestNode).isInstrumentable()) {
                        if (isObjectPropertyCompletion) {
                            if (locationType == NodeLocationType.CONTAINS_END) {
                                boolean isLiteralObject = doWithGlobalInnerContext(() -> {
                                    Object literalObj = LanguageSpecificHacks.getLiteralObject(nearestNode, langId);
                                    if (literalObj != null) {
                                        System.out.println("Literal shortcut!");
                                        fillCompletionsFromTruffleObject(completions, langId, literalObj);
                                        return true;
                                    }
                                    return false;
                                });
                                if (!isLiteralObject) {
                                    try (TruffleContextWrapper context = createNewContextFromOtherThread()) {
                                        EvaluationResult evalResult = executeToSection(nearestNode, sourceWrapper);
                                        if (evalResult.isEvaluationDone()) {
                                            if (!evalResult.isError()) {
                                                fillCompletionsFromTruffleObject(completions, langId, evalResult.getResult());
                                            }
                                        } else {
                                            try {
                                                System.out.println("Trying global eval...");
                                                // TODO(ds) in global scope? Or in new, fresh context?
                                                Object object = executeInGlobalScope(langId, nearestNode);
                                                fillCompletionsFromTruffleObject(completions, langId, object);
                                            } catch (Exception e) {
                                                this.diagnosticsPublisher.addDiagnostics(uri,
                                                                new Diagnostic(sourceSectionToRange(nearestNode.getSourceSection()), "No coverage information available for this source section.",
                                                                                DiagnosticSeverity.Information, "Truffle"));
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            fillCompletionsWithLocals(textDocumentSurrogate, nearestNode, completions, null);
                        }
                    }
                } // isLineValid
            } else {
                // TODO(ds) remove that when solved
                System.out.println("!!! Cannot lookup Source for local scoping. No parsed Node found for URI: " + uri);
            }

            if (!isObjectPropertyCompletion) {
                fillCompletionsWithGlobals(textDocumentSurrogate, completions);
            }

        } finally {
            // Clean up
            textDocumentSurrogate.setFixedText(null);
        }

        return completions;
    }

    private Object executeInGlobalScope(String langId, Node nearestNode) throws IOException {
        CallTarget callTarget = this.env.parse(
                        Source.newBuilder(nearestNode.getEncapsulatingSourceSection().getCharacters()).language(langId).name("eval in global scope").build());
        return callTarget.call();
    }

    private EvaluationResult executeToSection(final Node nearestNode, final SourceWrapper sourceWrapper) {
        if (nearestNode instanceof InstrumentableNode && (((InstrumentableNode) nearestNode).hasTag(StandardTags.StatementTag.class)) ||
                        ((InstrumentableNode) nearestNode).hasTag(StandardTags.ExpressionTag.class)) {
            final URI uri = sourceWrapper.getSource().getURI();
            final TextDocumentSurrogate surrogateOfOpenedFile = this.uri2TextDocumentSurrogate.get(uri);
            final SourceSection sourceSection = nearestNode.getSourceSection();
            Set<URI> coverageUris = surrogateOfOpenedFile.getCoverageUri(sourceSection);
            URI coverageUri = coverageUris == null ? null : coverageUris.stream().findFirst().orElseGet(() -> null);

            if (coverageUri == null) {
                try {
                    coverageUri = extractCoverageScriptPath(surrogateOfOpenedFile);
                } catch (InvalidCoverageScriptURI e) {
                    this.diagnosticsPublisher.addDiagnostics(uri, new Diagnostic(new Range(new Position(0, e.getIndex()), new Position(0, e.getLength())), e.getReason(), DiagnosticSeverity.Error,
                                    "Coverage analysis"));
                }

                if (coverageUri == null) {
                    return EvaluationResult.createUnknownExecutionTarget();
                }
            }

            // TODO(ds) can we always assume the same language for the source and its test?
            TextDocumentSurrogate surrogateOfTestFile = this.uri2TextDocumentSurrogate.computeIfAbsent(coverageUri,
                            (_uri) -> new TextDocumentSurrogate(_uri, surrogateOfOpenedFile.getLangId()));

            final String name = uri.getPath();
            final CallTarget callTarget = parse(surrogateOfTestFile);

            EventBinding<ExecutionEventNodeFactory> binding = this.env.getInstrumenter().attachExecutionEventFactory(
                            createSourceSectionFilter(uri, sourceSection, name),
                            new InlineEvaluationEventFactory(this.env));
            try {
                callTarget.call();
            } catch (EvaluationResultException e) {
                return e.isError() ? EvaluationResult.createError() : EvaluationResult.createResult(e.getResult());
            } catch (RuntimeException e) {
                if (e instanceof TruffleException) {
                    if (((TruffleException) e).isExit()) {
                        return EvaluationResult.createEvaluationSectionNotReached();
                    } else {
                        return EvaluationResult.createError();
                    }
                }
            } finally {
                binding.dispose();
            }
            return EvaluationResult.createEvaluationSectionNotReached();
        } else {
            return EvaluationResult.createEvaluationSectionNotReached();
        }
    }

    private static SourceSectionFilter createSourceSectionFilter(final URI uri, final SourceSection sourceSection, final String name) {
        // @formatter:off
        return SourceSectionFilter.newBuilder()
                        .tagIs(StatementTag.class, ExpressionTag.class)
                        .lineStartsIn(IndexRange.between(sourceSection.getStartLine(), sourceSection.getStartLine() + 1))
                        .lineEndsIn(IndexRange.between(sourceSection.getEndLine(), sourceSection.getEndLine() + 1))
                        .columnStartsIn(IndexRange.between(sourceSection.getStartColumn(), sourceSection.getStartColumn() + 1))
                        .columnEndsIn(IndexRange.between(sourceSection.getEndColumn(), sourceSection.getEndColumn() + 1))
                        .sourceIs(source -> source.getURI().equals(uri) || source.getName().equals(name)).build();
        // @formatter:on
    }

    private void fillCompletionsWithLocals(final TextDocumentSurrogate surrogate, Node nearestNode, CompletionList completions, VirtualFrame frame) {
        fillCompletionsWithScopesValues(surrogate, completions, () -> this.env.findLocalScopes(nearestNode, frame), CompletionItemKind.Variable, SORTING_PRIORITY_LOCALS);
    }

    private void fillCompletionsWithGlobals(final TextDocumentSurrogate surrogate, CompletionList completions) {
        fillCompletionsWithScopesValues(surrogate, completions, () -> this.env.findTopScopes(surrogate.getLangId()), null, SORTING_PRIORITY_GLOBALS);
    }

    private void fillCompletionsWithScopesValues(TextDocumentSurrogate surrogate, CompletionList completions, Supplier<Iterable<Scope>> scopesSupplier, CompletionItemKind completionItemKindDefault,
                    int displayPriority) {
        doWithGlobalInnerContext(() -> {
            String langId = surrogate.getLangId();
            LinkedHashMap<String, Map<Object, Object>> scopeMap = scopesToObjectMap(scopesSupplier.get());
            // Filter duplicates
            String[] existingCompletions = completions.getItems().stream().map((item) -> item.getLabel()).toArray(String[]::new);
            Set<String> completionKeys = new HashSet<>(Arrays.asList(existingCompletions));
            int scopeCounter = 0;
            for (Entry<String, Map<Object, Object>> scopeEntry : scopeMap.entrySet()) {
                ++scopeCounter;
                for (Entry<Object, Object> entry : scopeEntry.getValue().entrySet()) {
                    String key = entry.getKey().toString();
                    if (completionKeys.contains(key)) {
                        // Scopes are provided from inner to outer, so we need to detect duplicate keys and only take
                        // those from the most inner scope
                        continue;
                    } else {
                        completionKeys.add(key);
                    }

                    Object object;
                    try {
                        object = entry.getValue();
                    } catch (Exception e) {
                        continue;
                    }
                    CompletionItem completion = new CompletionItem(key);
                    // Inner scopes should be displayed first, so sort by priority and scopeCounter (the innermost scope
                    // has the lowest counter)
                    completion.setSortText(String.format("%d.%04d.%s", displayPriority, scopeCounter, key));
                    CompletionItemKind completionItemKind = findCompletionItemKind(object);
                    completion.setKind(completionItemKind != null ? completionItemKind : completionItemKindDefault);
                    completion.setDetail(createCompletionDetail(object, langId, completion.getKind() == null));
                    completion.setDocumentation("in " + scopeEntry.getKey());
                    completions.getItems().add(completion);
                }
            }
        });
    }

    private static CompletionItemKind findCompletionItemKind(Object object) {
        if (object instanceof TruffleObject) {
            TruffleObject truffleObjVal = (TruffleObject) object;
            boolean isExecutable = ForeignAccess.sendIsExecutable(IS_EXECUTABLE, truffleObjVal);
            boolean isInstatiatable = ForeignAccess.sendIsInstantiable(IS_INSTANTIABLE, truffleObjVal);
            if (isInstatiatable) {
                return CompletionItemKind.Class;
            }
            if (isExecutable) {
                return CompletionItemKind.Function;
            }
        }

        return null;
    }

    protected boolean fillCompletionsFromTruffleObject(CompletionList completions, String langId, Object object) {
        return fillCompletionsFromTruffleObject(completions, langId, object, getMetaObject(langId, object));
    }

    protected boolean fillCompletionsFromTruffleObject(CompletionList completions, String langId, Object object, Object metaObject) {
        Map<Object, Object> map = null;
        if (object instanceof TruffleObject) {
            map = ObjectStructures.asMap(new ObjectStructures.MessageNodes(), (TruffleObject) object);
        } else {
            Object boxedObject = LanguageSpecificHacks.getBoxedObject(object, langId);
            if (boxedObject instanceof TruffleObject) {
                map = ObjectStructures.asMap(new ObjectStructures.MessageNodes(), (TruffleObject) boxedObject);
            }
        }

        if (map == null) {
            return false;
        }

        for (Entry<Object, Object> entry : map.entrySet()) {
            Object value;
            try {
                value = entry.getValue();
            } catch (Exception e) {
                continue;
            }
            CompletionItem completion = new CompletionItem(entry.getKey().toString());
            CompletionItemKind kind = findCompletionItemKind(value);
            completion.setKind(kind != null ? kind : CompletionItemKind.Property);
            completion.setDetail(createCompletionDetail(value, langId, true));
            completions.getItems().add(completion);
            String documentation = "";

            documentation = LanguageSpecificHacks.getDocumentationForTruffleObject(langId, entry, completion, documentation);

            documentation += "in " + metaObject.toString(); // TODO(ds) is "in xyz" semantically correct? Because we send the KEYS message to the object and not
                                                            // to the meta object
            completion.setDocumentation(documentation);
        }

        return !map.isEmpty();
    }

    private String createCompletionDetail(Object obj, String langId, boolean includeClass) {
        Object metaObject = getMetaObject(langId, obj);
        String metaInfo = metaObject != null ? metaObject.toString() : null;

// if (obj instanceof TruffleObject) {
// TruffleObject truffleObj = (TruffleObject) obj;
// boolean isNull = ForeignAccess.sendIsExecutable(IS_NULL, truffleObj);
// }

        String detailText = "";
        if (metaInfo == null) {
            detailText = includeClass ? obj.getClass().getName() : "";
        } else {
            detailText = metaInfo;
        }
        if (!detailText.isEmpty()) {
            detailText += " -> ";
        }
        return detailText + obj;
    }

    protected Object getMetaObject(String langId, Object object) {
        LanguageInfo languageInfo = this.env.findLanguage(object);
        if (languageInfo == null) {
            languageInfo = this.env.getLanguages().get(langId);
        }

        Object metaObject = null;
        if (languageInfo != null) {
            metaObject = this.env.findMetaObject(languageInfo, object);
        }
        return metaObject;
    }

    // TODO(ds) implement a getTypeOfExpression(node) method (use GraalJS annotated stuff)

    private static boolean isLineValid(int line, Source source) {
        // line is zero-based, source line is one-based
        return line >= 0 && line < source.getLineCount();
    }

    private static int zeroBasedLineToOneBasedLine(int line, Source source) {
        if (line + 1 <= source.getLineCount()) {
            return line + 1;
        }

        String text = source.getCharacters().toString();
        boolean isNewlineEnd = text.charAt(text.length() - 1) == '\n';
        if (isNewlineEnd) {
            return line;
        }

        throw new IllegalStateException("Mismatch in line numbers. Source line count (one-based): " + source.getLineCount() + ", zero-based line count: " + line);
    }

    private static SourceFix fixSourceAtPosition(TextDocumentSurrogate surrogate, int line, int character) {
        Source originalSource = Source.newBuilder(surrogate.getCurrentText()).language(surrogate.getLangId()).name(surrogate.getUri().toString()).build();
        int oneBasedLineNumber = zeroBasedLineToOneBasedLine(line, originalSource);
        String textAtCaretLine = originalSource.getCharacters(oneBasedLineNumber).toString();

        SourceFix sourceFix = LanguageSpecificHacks.fixSourceAtPosition(surrogate.getCurrentText(), surrogate.getLangId(), character,
                        originalSource, oneBasedLineNumber, textAtCaretLine);
        if (sourceFix != null) {
            return sourceFix;
        }

        final List<TextDocumentContentChangeEvent> changeEventsSinceLastSuccessfulParsing = surrogate.getChangeEventsSinceLastSuccessfulParsing();
        if (!changeEventsSinceLastSuccessfulParsing.isEmpty() && surrogate.getParsedSourceWrapper() != null && surrogate.getParsedSourceWrapper().isParsingSuccessful()) {
            String lastSuccessfullyParsedText = surrogate.getParsedSourceWrapper().getText();
            List<TextDocumentContentChangeEvent> allButLastChanges = changeEventsSinceLastSuccessfulParsing.subList(0, changeEventsSinceLastSuccessfulParsing.size() - 1);
            String fixedText = applyTextDocumentChanges(allButLastChanges, lastSuccessfullyParsedText);

            TextDocumentContentChangeEvent lastEvent = changeEventsSinceLastSuccessfulParsing.get(changeEventsSinceLastSuccessfulParsing.size() - 1);
            boolean isObjectPropertyCompletionCharacter = LanguageSpecificHacks.isObjectPropertyCompletionCharacter(lastEvent.getText(), surrogate.getLangId());

            return new SourceFix(fixedText, lastEvent.getRange().getEnd().getCharacter(), isObjectPropertyCompletionCharacter);
        }

        return null;
    }

    @SuppressWarnings("unused")
    public synchronized List<? extends DocumentHighlight> getHighlights(URI uri, int line, int character) {
        List<DocumentHighlight> highlights = new ArrayList<>();

        return highlights;
    }

    private static Range sourceSectionToRange(SourceSection section) {
        if (section == null) {
            return new Range(new Position(), new Position());
        }
        return new Range(
                        new Position(section.getStartLine() - 1, section.getStartColumn() - 1),
                        new Position(section.getEndLine() - 1, section.getEndColumn() /* -1 */));
    }

    private static URI extractCoverageScriptPath(TextDocumentSurrogate surrogate) throws InvalidCoverageScriptURI {
        String currentText = surrogate.getCurrentText();
        String firstLine;
        try {
            firstLine = new BufferedReader(new StringReader(currentText)).readLine();
        } catch (IOException e1) {
            throw new IllegalStateException(e1);
        }
        int startIndex = firstLine.indexOf(COVERAGE_SCRIPT);
        if (startIndex >= 0) {
            Path coverageScriptPath;
            try {
                coverageScriptPath = Paths.get(firstLine.substring(startIndex + COVERAGE_SCRIPT.length()));
                if (!coverageScriptPath.isAbsolute()) {
                    Path currentFile = Paths.get(surrogate.getUri());
                    coverageScriptPath = currentFile.resolveSibling(coverageScriptPath).normalize();
                }
            } catch (InvalidPathException e) {
                throw new InvalidCoverageScriptURI(e, startIndex + COVERAGE_SCRIPT.length(), firstLine.length());
            }
            if (!Files.exists(coverageScriptPath)) {
                throw new InvalidCoverageScriptURI(startIndex + COVERAGE_SCRIPT.length(), "File not found: " + coverageScriptPath.toString(), firstLine.length());
            }
            return coverageScriptPath.toUri();
        }
        return null;
    }

    public void runConverageAnalysis(final URI uri) {
        final TextDocumentSurrogate surrogateOfOpendFile = this.uri2TextDocumentSurrogate.get(uri);
        URI coverageUri;
        try {
            coverageUri = extractCoverageScriptPath(surrogateOfOpendFile);
        } catch (InvalidCoverageScriptURI e) {
            this.diagnosticsPublisher.addDiagnostics(uri,
                            new Diagnostic(new Range(new Position(0, e.getIndex()), new Position(0, e.getLength())), e.getReason(), DiagnosticSeverity.Error, "Coverage analysis"));
            return;
        }

        if (coverageUri == null) {
            this.diagnosticsPublisher.addDiagnostics(uri, new Diagnostic(new Range(new Position(), new Position()), "No COVERAGE_SCRIPT:<path> found anywhere in first line.", DiagnosticSeverity.Error,
                            "Coverage analysis"));
            return;
        }

        // Clean-up
        // TODO(ds) how to do this without dropping everything? If we have different tests, the coverage run
        // of one test will remove any coverage info provided from the other tests.
        this.uri2TextDocumentSurrogate.entrySet().stream().forEach(entry -> entry.getValue().getLocation2coverageUri().clear());

        try {
            // TODO(ds) can we always assume the same language for the source and its test?
            TextDocumentSurrogate surrogateOfTestFile = this.uri2TextDocumentSurrogate.computeIfAbsent(coverageUri, (_uri) -> new TextDocumentSurrogate(_uri, surrogateOfOpendFile.getLangId()));

            // Do code execution always in a fresh context
            try (TruffleContextWrapper context = createNewContextFromOtherThread()) {
                final CallTarget callTarget = parse(surrogateOfTestFile);
                EventBinding<ExecutionEventListener> coverageListener = env.getInstrumenter().attachExecutionEventListener(
                                SourceSectionFilter.newBuilder().tagIs(StatementTag.class, ExpressionTag.class).build(),
                                new ExecutionEventListener() {

                                    public void onReturnValue(EventContext eventContext, VirtualFrame frame, Object result) {
                                    }

                                    public void onReturnExceptional(EventContext eventContext, VirtualFrame frame, Throwable exception) {
                                    }

                                    public void onEnter(final EventContext eventContext, VirtualFrame frame) {
                                        final SourceSection section = eventContext.getInstrumentedSourceSection();
                                        if (section != null && section.isAvailable()) {
                                            putSection2Uri(section, () -> eventContext.getInstrumentedNode().getRootNode().getLanguageInfo().getId());
                                        }
                                    }

                                    @TruffleBoundary
                                    private void putSection2Uri(SourceSection section, Supplier<String> langIdSupplier) {
                                        URI sourceUri = section.getSource().getURI();
                                        if (!sourceUri.getScheme().equals("file")) {
                                            String name = section.getSource().getName();
                                            Path pathFromName = null;
                                            try {
                                                if (name != null) {
                                                    pathFromName = Paths.get(name);
                                                }
                                            } catch (InvalidPathException e) {
                                            }
                                            if (pathFromName == null || !Files.exists(pathFromName)) {
                                                return;
                                            }

                                            sourceUri = pathFromName.toUri();
                                        }
                                        // System.out.println(section);
                                        TextDocumentSurrogate surrogate = TruffleAdapter.this.uri2TextDocumentSurrogate.computeIfAbsent(sourceUri,
                                                        (_uri) -> new TextDocumentSurrogate(_uri, langIdSupplier.get()));
                                        surrogate.addLocationCoverage(SourceLocation.from(section), coverageUri);
                                    }
                                });
                try {
                    callTarget.call();
                } finally {
                    coverageListener.dispose();
                }
            }

            surrogateOfOpendFile.setCoverageAnalysisDone(true);

            showCoverage(uri);
        } catch (Exception e) {
            if (e instanceof TruffleException) {
                Node location = ((TruffleException) e).getLocation();
                URI uriOfErronousSource = null;
                if (location != null) {
                    SourceSection sourceSection = location.getEncapsulatingSourceSection();
                    if (sourceSection != null) {
                        uriOfErronousSource = sourceSection.getSource().getURI();
                    }
                }

                if (uriOfErronousSource == null) {
                    uriOfErronousSource = uri;
                }
                this.diagnosticsPublisher.addDiagnostics(uriOfErronousSource, new Diagnostic(getRangeFrom((TruffleException) e), e.getMessage(), DiagnosticSeverity.Error, "Coverage analysis"));
            } else {
                e.printStackTrace(this.err);
                this.err.flush();
            }
        }
    }

    private void doWithGlobalInnerContext(Runnable runnable) {
        doWithContext(getGlobalInnerContext(), runnable);
    }

    private static void doWithContext(TruffleContext context, Runnable runnable) {
        Object contextEnterObject = context.enter();
        try {
            runnable.run();
        } finally {
            context.leave(contextEnterObject);
        }
    }

    private <T> T doWithGlobalInnerContext(Supplier<T> supplier) {
        return doWithContext(getGlobalInnerContext(), supplier);
    }

    private static <T> T doWithContext(TruffleContext context, Supplier<T> runnable) {
        Object contextEnterObject = context.enter();
        try {
            return runnable.get();
        } finally {
            context.leave(contextEnterObject);
        }
    }

    public synchronized List<? extends Location> getDefinitions(URI uri, int line, int character) {
        List<Location> locations = new ArrayList<>();

        String langId = this.uri2TextDocumentSurrogate.get(uri).getLangId();
        if (this.sourceProvider.getLoadedSource(langId, uri) != null) {
            SourceWrapper wrapper = this.sourceProvider.getLoadedSource(langId, uri);
            Source source = wrapper.getSource();

            NearestSections nearestSections = NearestSectionsFinder.getNearestSections(source, env, zeroBasedLineToOneBasedLine(line, source), character);
            SourceSection containsSection = nearestSections.getContainsSourceSection();
            if (containsSection != null) {
                SourceSection definition = this.section2definition.get(containsSection);
                if (definition != null) {
                    locations.add(new Location(uri.toString(), sourceSectionToRange(definition)));
                }
            }
        }
        return locations;
    }

    protected SourceSection findSourceLocation(TruffleObject obj) {
        LanguageInfo lang = env.findLanguage(obj);

        if (lang != null) {
            return env.findSourceLocation(lang, obj);
        }

        return null;
    }

    public synchronized Hover getHover(URI uri, int line, int character) {
        List<Either<String, MarkedString>> contents = new ArrayList<>();

        String langId = this.uri2TextDocumentSurrogate.get(uri).getLangId();
        if (this.sourceProvider.getLoadedSource(langId, uri) != null) {
            SourceWrapper wrapper = this.sourceProvider.getLoadedSource(langId, uri);
            Source source = wrapper.getSource();
            NearestSections nearestSections = NearestSectionsFinder.getNearestSections(source, env, zeroBasedLineToOneBasedLine(line, source), character);
            SourceSection containsSection = nearestSections.getContainsSourceSection();
            if (containsSection != null) {
                SourceSection definition = this.section2definition.get(containsSection);
                if (definition != null) {
                    MarkedString markedString = new MarkedString(langId, definition.getCharacters().toString());
                    contents.add(Either.forRight(markedString));
                }
            }
        }
        return new Hover(contents);
    }

    private synchronized TruffleContext getGlobalInnerContext() {
        assert this.globalInnerContext != null : "Global inner Context not initialized yet";
        return this.globalInnerContext;
    }

    public String getSourceText(Path path) {
        TextDocumentSurrogate surrogate = this.uri2TextDocumentSurrogate.get(path.toUri());
        return surrogate != null ? surrogate.getCurrentText() : null;
    }

    public boolean isVirtualFile(Path path) {
        return this.uri2TextDocumentSurrogate.containsKey(path.toUri());
    }

    private TruffleContext createNewContext() {
        return this.contextProvider.apply(this);
    }

    private TruffleContextWrapper createNewContextFromOtherThread() {
        return TruffleContextWrapper.createAndEnter(doWithGlobalInnerContext(() -> this.contextProvider.apply(this)));
    }

    public void onContextCreated(TruffleContext truffleContext) {
    }

    public void onLanguageContextCreated(TruffleContext truffleContext, LanguageInfo language) {
    }

    public void onLanguageContextInitialized(TruffleContext truffleContext, LanguageInfo language) {
        if (this.globalInnerContext == null && !isContextBeingCreated) {
            this.isContextBeingCreated = true;
            this.globalInnerContext = createNewContext();
        }
    }

    public void onLanguageContextFinalized(TruffleContext truffleContext, LanguageInfo language) {
    }

    public void onLanguageContextDisposed(TruffleContext truffleContext, LanguageInfo language) {
    }

    public void onContextClosed(TruffleContext truffleContext) {
    }

    public synchronized void showCoverage(URI uri) {
        final TextDocumentSurrogate surrogate = this.uri2TextDocumentSurrogate.get(uri);
        if (surrogate.getParsedSourceWrapper() != null) {
            // @formatter:off
            Diagnostic[] coverageDiagnostics = surrogate.getParsedSourceWrapper().getNodes().stream()
                            .filter(node -> node instanceof InstrumentableNode && ((InstrumentableNode) node).hasTag(StatementTag.class))
                            .filter(node -> !surrogate.getLocation2coverageUri().containsKey(SourceLocation.from(node.getSourceSection())))
                            .map(node -> new Diagnostic(sourceSectionToRange(node.getSourceSection()),
                                                        "Not covered",
                                                        DiagnosticSeverity.Warning,
                                                        "Coverage Analysis"))
                            .toArray(Diagnostic[]::new);
            // @formatter:on
            this.diagnosticsPublisher.addDiagnostics(uri, coverageDiagnostics);
        } else {
            this.diagnosticsPublisher.addDiagnostics(uri,
                            new Diagnostic(new Range(new Position(), new Position()), "No coverage information available", DiagnosticSeverity.Error, "Coverage Analysis"));
        }
    }
}
