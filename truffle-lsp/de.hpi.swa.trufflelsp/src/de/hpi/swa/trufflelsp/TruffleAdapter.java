package de.hpi.swa.trufflelsp;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
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
import com.oracle.truffle.api.debug.Breakpoint;
import com.oracle.truffle.api.debug.DebugValue;
import com.oracle.truffle.api.debug.Debugger;
import com.oracle.truffle.api.debug.DebuggerSession;
import com.oracle.truffle.api.debug.SourceElement;
import com.oracle.truffle.api.debug.SuspendAnchor;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.ContextsListener;
import com.oracle.truffle.api.instrumentation.EventBinding;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.ExecutionEventListener;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.SourceFilter;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter.SourcePredicate;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.ExecutableNode;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeAccessHelper;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

import de.hpi.swa.trufflelsp.NearestSectionsFinder.NearestSections;
import de.hpi.swa.trufflelsp.NearestSectionsFinder.NodeLocationType;
import de.hpi.swa.trufflelsp.server.DiagnosticsPublisher;

public class TruffleAdapter implements ContextsListener {
    private static final int SORTING_PRIORITY_LOCALS = 1;
    private static final int SORTING_PRIORITY_GLOBALS = 2;
    private static final String TYPE_HARVESTING_URI = "TYPE_HARVESTING_URI:";
    public static final String NO_TYPES_HARVESTED = "NO_TYPES_HARVESTED";
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
    private TruffleContext context;
    private boolean isContextInitialized = false;
    private Supplier<TruffleContext> sup;

    class SourceUriFilter implements SourcePredicate {

        public boolean test(Source source) {
            return source.getName().startsWith("file://"); // TODO(ds) how to filter? tag is STATEMENT && source.isAvailable()?
        }
    }

    public TruffleAdapter(TruffleInstrument.Env env, Supplier<TruffleContext> sup) {
        assert env != null;
        this.env = env;
        this.sup = sup;
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

    public synchronized void parse(final String text, final String langId, final URI uri) {
        TextDocumentSurrogate surrogate = this.uri2TextDocumentSurrogate.computeIfAbsent(uri, (_uri) -> new TextDocumentSurrogate(_uri, langId));
        surrogate.setCurrentText(text);

        parse(surrogate);
    }

    protected synchronized CallTarget parse(final TextDocumentSurrogate surrogate) {
        List<Diagnostic> diagnostics = new ArrayList<>();

        CallTarget callTarget = null;
        try {
            callTarget = parseInternal(surrogate);
        } catch (IllegalStateException e) {
            this.err.println(e.getLocalizedMessage());
            this.err.flush();
        } catch (RuntimeException e) {
            if (e instanceof TruffleException) {
                diagnostics.add(new Diagnostic(getRangeFrom((TruffleException) e), e.getMessage(), DiagnosticSeverity.Error, "Truffle"));
            } else {
                throw new RuntimeException(e);
            }
        }

// if (!surrogate.getTypeHarvestingDone()) {
// diagnostics.add(new Diagnostic(new Range(new Position(), new Position()), "No types harvested
// yet. Code completion is done via statical analysis only.", DiagnosticSeverity.Hint,
// "Truffle",
// NO_TYPES_HARVESTED));
// }

        this.diagnosticsPublisher.addDiagnostics(diagnostics);

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
        return doWithContext(() -> {
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
        });
    }

    public synchronized void processChangesAndParse(List<? extends TextDocumentContentChangeEvent> list, URI uri) {
        if (list.isEmpty()) {
            return;
        }

        TextDocumentSurrogate surrogate = this.uri2TextDocumentSurrogate.get(uri);
        surrogate.getChangeEventsSinceLastSuccessfulParsing().addAll(list);
        surrogate.setCurrentText(applyTextDocumentChanges(list, surrogate.getCurrentText()));

        parse(surrogate);
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

    public synchronized CompletionList getCompletions(URI uri, int line, int originalCharacter) {
        CompletionList completions = new CompletionList();
        completions.setIsIncomplete(false);

        TextDocumentSurrogate textDocumentSurrogate = this.uri2TextDocumentSurrogate.get(uri);
        String langId = textDocumentSurrogate.getLangId();
        boolean includeGlobalsAndLocalsInCompletion = true;
        boolean isObjectPropertyCompletion = false;

        int character = originalCharacter;
        SourceWrapper sourceWrapper = this.sourceProvider.getLoadedSource(langId, uri);
        if (sourceWrapper == null || !sourceWrapper.isParsingSuccessful()) {
            // Parsing failed, now try to fix simple syntax errors
            System.out.println("No source wrapper found, fixing source.");
            SourceFix sourceFix = fixSourceAtPosition(textDocumentSurrogate, line, character);
            if (sourceFix != null) {
                try {
                    URI fixedUri = URI.create(uri.toString() + "__FIX__");
// String fixedUri = uri;
                    TextDocumentSurrogate surrogateFixed = new TextDocumentSurrogate(fixedUri, langId, sourceFix.text);
                    this.uri2TextDocumentSurrogate.put(fixedUri, surrogateFixed);
                    parseInternal(surrogateFixed);
                    sourceWrapper = this.sourceProvider.getLoadedSource(langId, fixedUri);
                    character = sourceFix.character;
                    includeGlobalsAndLocalsInCompletion = !sourceFix.isObjectPropertyCompletion;
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

        if (sourceWrapper != null) {
            Source source = sourceWrapper.getSource();

            if (isLineValid(line, source)) {
                int oneBasedLineNumber = zeroBasedLineToOneBasedLine(line, source);

                NearestSections nearestSections = NearestSectionsFinder.getNearestSections(source, env, oneBasedLineNumber, character);
                SourceSection containsSection = nearestSections.getContainsSourceSection();

                Node nearestNode;
                NodeLocationType locationType;
                if (containsSection == null) {
                    // We are not in a local scope, so only top scope objects possible
                    nearestNode = null;
                    locationType = null;
                } else if (isEndOfSectionMatchingCaretPosition(oneBasedLineNumber, character, containsSection)) {
                    // Our caret is directly behind the containing section, so we can simply use that one to find local
                    // scope objects
                    nearestNode = (Node) nearestSections.getContainsNode();
                    locationType = NodeLocationType.CONTAINS_END;
                } else if (nodeIsInChildHirarchyOf((Node) nearestSections.getNextNode(), (Node) nearestSections.getContainsNode())) {
                    // Great, the nextNode is a (indirect) sibling of our containing node, so it is in the same scope as
                    // we are and we can use it to get local scope objects
                    nearestNode = (Node) nearestSections.getNextNode();
                    locationType = NodeLocationType.NEXT;
                } else if (nodeIsInChildHirarchyOf((Node) nearestSections.getPreviousNode(), (Node) nearestSections.getContainsNode())) {
                    // In this case we want call findLocalScopes() with BEHIND-flag, i.e. give me the local scope
                    // objects which are valid behind that node
                    nearestNode = (Node) nearestSections.getPreviousNode();
                    locationType = NodeLocationType.PREVIOUS;
                } else {
                    // No next or previous node is in the same scope like us, so we can only take our containing node to
                    // get local scope objects
                    nearestNode = (Node) nearestSections.getContainsNode();
                    locationType = NodeLocationType.CONTAINS;
                }

                System.out.println("nearestNode: " +
                                (nearestNode != null ? nearestNode.getClass().getSimpleName() : "--NULL--") + "\t-" + locationType + "-\t" +
                                (nearestNode != null ? nearestNode.getSourceSection() : ""));

                if (nearestNode instanceof InstrumentableNode) {
                    InstrumentableNode instrumentableNode = (InstrumentableNode) nearestNode;
                    if (instrumentableNode.isInstrumentable()) {
                        Map<SourceSection, MaterializedFrame> section2frame = textDocumentSurrogate.getSection2frame();
                        VirtualFrame frame = section2frame.get(nearestNode.getSourceSection());
                        if (frame == null) {
                            // TODO(ds) this is hacky and is not always correct
                            SourceSection sourceSection = nearestNode.getSourceSection();
                            Optional<Entry<SourceSection, MaterializedFrame>> optEntry = section2frame.entrySet().stream().filter(
                                            (f) -> f.getKey().getCharIndex() == sourceSection.getCharIndex() && f.getKey().getCharEndIndex() == sourceSection.getCharEndIndex()).findFirst();
                            if (optEntry.isPresent()) {
                                frame = optEntry.get().getValue();
                            }
                        }

                        // TODO(ds) isObjectPropertyCompletion and includeGlobalsAndLocalsInCompletion are deeply related,
                        // check which one is needed
                        if (isObjectPropertyCompletion && locationType == NodeLocationType.CONTAINS_END) {

                            Object nearestNodeReturnValue = executeToSection(nearestNode, sourceWrapper);
                            if (nearestNodeReturnValue != null) {
                                boolean areOjectPropertiesPresent = nearestNodeReturnValue instanceof DebugValue
                                                ? doWithContext(() -> fillCompletionsFromTruffleObject(completions, (DebugValue) nearestNodeReturnValue, langId))
                                                : doWithContext(() -> fillCompletionsFromTruffleObject(completions, langId, nearestNodeReturnValue));
                                if (areOjectPropertiesPresent) {
                                    // If there are object properties available for code completion, then we do not want to display any
                                    // globals or local variables
                                    includeGlobalsAndLocalsInCompletion = false;
                                }
                            } else {
                                // Here we cannot use any nearestNode instance, because this can also be the nextNode or
                                // previousNode. We need the node directly before our caret.
                                boolean arePojectPropertiesPresent = fillCompletionsWithObjectProperties(completions, frame, nearestNode, langId);
                                if (arePojectPropertiesPresent) {
                                    // If there are object properties available for code completion, then we do not want to display any
                                    // globals or local variables
                                    includeGlobalsAndLocalsInCompletion = false;
                                } else if (frame == null) {
                                    // No frame is available for the current source section and no properties could be derived
                                    this.diagnosticsPublisher.addDiagnostics(
                                                    Arrays.asList(new Diagnostic(sourceSectionToRange(nearestNode.getSourceSection()), "No types harvested for this source section yet.",
                                                                    DiagnosticSeverity.Information, "Truffle")));
                                }
                            }
                        }

                        if (includeGlobalsAndLocalsInCompletion) {
                            fillCompletionsWithLocals(textDocumentSurrogate, nearestNode, completions, frame);
                        }
                    }
                }
            } // isLineValid
        } else {
            // TODO(ds) remove that when solved
            System.out.println("!!! Cannot lookup Source for local scoping. No parsed Node found for URI: " + uri);
        }

        if (includeGlobalsAndLocalsInCompletion) {
            fillCompletionsWithGlobals(textDocumentSurrogate, completions);
        }

        return completions;
    }

    private Object executeToSection(final Node nearestNode, final SourceWrapper sourceWrapper) {
        if (nearestNode instanceof InstrumentableNode && ((InstrumentableNode) nearestNode).hasTag(StandardTags.StatementTag.class)) {
            Debugger debugger = this.env.lookup(this.env.getInstruments().get("debugger"), Debugger.class);
            TypeHarvestingSuspendCallback callback = new TypeHarvestingSuspendCallback();
            // We do not want to step, so an empty SourceElement-array avoids wrapping of nodes
            try (DebuggerSession ds = debugger.startSession(callback, new SourceElement[0])) {
                ds.install(Breakpoint.newBuilder(nearestNode.getSourceSection()).suspendAnchor(SuspendAnchor.BEFORE).build());

                try {
// doWithContext(() -> sourceWrapper.getCallTarget().call());
                    exec(sourceWrapper.getSource().getURI());
                } catch (Exception e) {
                    e.printStackTrace(); // TODO(ds)
                }
            }

            return callback.getEvalResult();
        } else {
            return doWithContext(() -> {
                try {
                    CallTarget target = this.env.parse(
                                    Source.newBuilder(nearestNode.getSourceSection().getCharacters()).internal().name("internal global eval").language(
                                                    sourceWrapper.getSource().getLanguage()).build());
                    return target.call();
                } catch (Throwable e) {
                    return null;
                }
            });
        }
    }

    private void fillCompletionsWithLocals(final TextDocumentSurrogate surrogate, Node nearestNode, CompletionList completions, VirtualFrame frame) {
        fillCompletionsWithScopesValues(surrogate, completions, () -> this.env.findLocalScopes(nearestNode, frame), CompletionItemKind.Variable, SORTING_PRIORITY_LOCALS);
    }

    private void fillCompletionsWithGlobals(final TextDocumentSurrogate surrogate, CompletionList completions) {
        fillCompletionsWithScopesValues(surrogate, completions, () -> this.env.findTopScopes(surrogate.getLangId()), null, SORTING_PRIORITY_GLOBALS);
    }

    private void fillCompletionsWithScopesValues(TextDocumentSurrogate surrogate, CompletionList completions, Supplier<Iterable<Scope>> scopesSupplier, CompletionItemKind completionItemKindDefault,
                    int displayPriority) {
        doWithContext(() -> {
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

    private static CompletionItemKind findCompletionItemKind(DebugValue value) {

        // TODO(ds) object.isInstantiable()

        if (value.isReadable() && value.canExecute()) {
            return CompletionItemKind.Function;
        }

        return null;
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

    private boolean fillCompletionsWithObjectProperties(CompletionList completions, VirtualFrame frame, Node nodeBeforeCaret, String langId) {
        return doWithContext(() -> {
            Object object;
            Object metaObject;

            try {
                if (frame != null) {
                    ExecutableNode executableNode = this.env.parseInline(
                                    Source.newBuilder(nodeBeforeCaret.getEncapsulatingSourceSection().getCharacters()).language(langId).name("dummy inline eval").build(),
                                    nodeBeforeCaret,
                                    frame.materialize());
                    if (executableNode != null) {
                        NodeAccessHelper.insertNode(nodeBeforeCaret, executableNode);
                        object = executableNode.execute(frame);
                        metaObject = getMetaObject(langId, object);
                    } else {
                        return LanguageSpecificHacks.fillCompletions(this, completions, frame, nodeBeforeCaret, langId);
                    }
                } else {
                    // No frame available. Try evaluation of the code snippet in global scope.
                    CallTarget callTarget = this.env.parse(Source.newBuilder(nodeBeforeCaret.getEncapsulatingSourceSection().getCharacters()).language(langId).name("dummy eval").build());
                    object = callTarget.call();
                    metaObject = getMetaObject(langId, object);
                }
            } catch (Exception e) {
                return false;
            }

            return fillCompletionsFromTruffleObject(completions, langId, object, metaObject);
        });

    }

    @SuppressWarnings("unused")
    protected boolean fillCompletionsFromTruffleObject(CompletionList completions, DebugValue object, String langId) {
        Collection<DebugValue> properties = null;
        DebugValue metaObject = object.getMetaObject();
        if (object.isReadable()) {
            properties = object.getProperties();
        }

        if (properties == null) {
            Object value = LanguageSpecificHacks.getDebugValueValue(object);
            if (value == null)
                return false;
            return fillCompletionsFromTruffleObject(completions, langId, value);
        }

        // TODO(ds) this check is not good. We do not want the properties of the meta-object, we want the
        // boxed representation of object.get(), but this is not accessible here :/
        if (properties == null && metaObject != null && metaObject.isReadable()) {
            properties = metaObject.getProperties();
        }

        if (properties == null) {
            return false;
        }

        for (Iterator<DebugValue> iterator = properties.iterator(); iterator.hasNext();) {
            DebugValue debugValue;
            try {
                debugValue = iterator.next();
            } catch (Exception e) {
                continue;
            }
            CompletionItem completion = new CompletionItem(debugValue.getName());
            CompletionItemKind kind = findCompletionItemKind(debugValue);
            completion.setKind(kind != null ? kind : CompletionItemKind.Property);
            completion.setDetail(createCompletionDetail(debugValue, true));
            completions.getItems().add(completion);
            String documentation = "";

            if (metaObject != null) {
                documentation += "in " + metaObject.as(String.class);
            }
            completion.setDocumentation(documentation);
        }

        return !properties.isEmpty();
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

    private static String createCompletionDetail(DebugValue obj, boolean includeClass) {
        DebugValue metaInfo = obj.getMetaObject();
        String detailText = "";
        if (metaInfo == null) {
            detailText = includeClass ? obj.getClass().getName() : "";
        } else {
            detailText = metaInfo.as(String.class);
        }
        if (!detailText.isEmpty()) {
            detailText += " -> ";
        }
        return detailText + (obj.isReadable() ? obj.as(String.class) : "");
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
        if (object instanceof DebugValue) {
            return ((DebugValue) object).getMetaObject();
        }

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

    private static boolean nodeIsInChildHirarchyOf(Node node, Node potentialParent) {
        if (node == null) {
            return false;
        }
        Node parent = node.getParent();
        while (parent != null) {
            if (parent.equals(potentialParent)) {
                return true;
            }
            parent = parent.getParent();
        }
        return false;
    }

    private static boolean isEndOfSectionMatchingCaretPosition(int line, int character, SourceSection section) {
        return section.getEndLine() == line && section.getEndColumn() == character;
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

    public void exec(final URI uri) {
        final TextDocumentSurrogate surrogateOfOpendFile = TruffleAdapter.this.uri2TextDocumentSurrogate.get(uri);
        String currentText = surrogateOfOpendFile.getCurrentText();
        String firstLine;
        try {
            firstLine = new BufferedReader(new StringReader(currentText)).readLine();
        } catch (IOException e1) {
            throw new IllegalStateException(e1);
        }
        int startIndex = firstLine.indexOf(TYPE_HARVESTING_URI);
        URI typeHarvestingUri;
        try {
            typeHarvestingUri = (startIndex >= 0) ? new URI(firstLine.substring(startIndex + TYPE_HARVESTING_URI.length()).trim()) : uri;
        } catch (URISyntaxException e1) {
            this.diagnosticsPublisher.addDiagnostics(Arrays.asList(new Diagnostic(new Range(new Position(0, startIndex), new Position(0, firstLine.length() - 1)), e1.getLocalizedMessage())));
            return;
        }

        // TODO(ds) disabled
        typeHarvestingUri = uri;

        SourceSectionFilter filter = SourceSectionFilter.newBuilder().tagIs(StandardTags.StatementTag.class).sourceFilter(
                        SourceFilter.newBuilder().sourceIs(source -> uri.equals(source.getURI())).build()).build();
        EventBinding<ExecutionEventListener> listener = env.getInstrumenter().attachExecutionEventListener(filter, new ExecutionEventListener() {

            @TruffleBoundary
            public void onReturnValue(EventContext eventContext, VirtualFrame frame, Object result) {
                // TODO(ds) this is only a hacky solution. Not all expressions emit a result (for example
                // SLReadLocalVariableNode onReturnValue(frame, result) has a result != null if the read is a child
                // node of a assignment or return statement, otherwise excuteVoid() is called and a null-result is
                // emitted)
                if (result instanceof TruffleObject) {
                    SourceSection sourceLocation = TruffleAdapter.this.findSourceLocation((TruffleObject) result);
                    if (sourceLocation != null && eventContext.getInstrumentedSourceSection() != null && eventContext.getInstrumentedSourceSection().isAvailable()) {
                        TruffleAdapter.this.section2definition.put(eventContext.getInstrumentedSourceSection(), sourceLocation);
                    }
                }
            }

            public void onReturnExceptional(EventContext eventContext, VirtualFrame frame, Throwable exception) {
            }

            public void onEnter(EventContext eventContext, VirtualFrame frame) {
                final SourceSection section = eventContext.getInstrumentedSourceSection();
                if (section != null) {
                    putSection2frame(section, frame.materialize());
                }
            }

            @TruffleBoundary
            private void putSection2frame(SourceSection section, MaterializedFrame frame) {
                surrogateOfOpendFile.getSection2frame().put(section, frame);
            }
        });

        final URI coverageUri = typeHarvestingUri;
        EventBinding<ExecutionEventListener> coverageListener = env.getInstrumenter().attachExecutionEventListener(filter, new ExecutionEventListener() {

            @TruffleBoundary
            public void onReturnValue(EventContext eventContext, VirtualFrame frame, Object result) {
            }

            public void onReturnExceptional(EventContext eventContext, VirtualFrame frame, Throwable exception) {
            }

            public void onEnter(EventContext eventContext, VirtualFrame frame) {
                final SourceSection section = eventContext.getInstrumentedSourceSection();
                if (section != null) {
                    putSection2Uri(section);
                }
            }

            @TruffleBoundary
            private void putSection2Uri(SourceSection section) {
                surrogateOfOpendFile.getSection2coverageUri().put(section, coverageUri);
            }
        });

        List<Diagnostic> diagnostics = new ArrayList<>();
        try {
            CallTarget callTarget;

            // TODO(ds) can we always assume the same language for the source and its test?
            TextDocumentSurrogate surrogateOfTestFile = this.uri2TextDocumentSurrogate.computeIfAbsent(typeHarvestingUri, (_uri) -> new TextDocumentSurrogate(_uri, surrogateOfOpendFile.getLangId()));

            SourceWrapper sourceWrapper = surrogateOfTestFile.getParsedSourceWrapper();
            if (sourceWrapper != null && sourceWrapper.isParsingSuccessful() && surrogateOfOpendFile.getChangeEventsSinceLastSuccessfulParsing().isEmpty()) {
                // We have already parsed the file which will be executed to harvest types and there are no changes
                // since last successful parsing. Just use the call target.
                callTarget = sourceWrapper.getCallTarget();
            } else {
                callTarget = parse(surrogateOfTestFile);
            }

            doWithContext(() -> callTarget.call());

            surrogateOfOpendFile.setTypeHarvestingDone(true);
        } catch (RuntimeException e) {
            if (e instanceof TruffleException) {
                diagnostics.add(new Diagnostic(getRangeFrom((TruffleException) e), e.getMessage(), DiagnosticSeverity.Warning, "Truffle Type Harvester"));
            } else {
                e.printStackTrace(this.err);
                this.err.flush();
            }
        } finally {
            listener.dispose();
            coverageListener.dispose();
            this.diagnosticsPublisher.addDiagnostics(diagnostics);
        }
    }

    private void doWithContext(Runnable runnable) {
        Object contextEnterObject = this.context.enter();
        try {
            runnable.run();
        } finally {
            this.context.leave(contextEnterObject);
        }
    }

    private <T> T doWithContext(Supplier<T> runnable) {
        Object contextEnterObject = this.context.enter();
        try {
            return runnable.get();
        } finally {
            this.context.leave(contextEnterObject);
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

    public void onContextCreated(TruffleContext truffleContext) {
// this.context = truffleContext;
        System.out.println("My context: " + this.context + " onContextCreated: " + truffleContext);
// this.context = this.sup.get();
    }

    public void onLanguageContextCreated(TruffleContext truffleContext, LanguageInfo language) {
    }

    public void onLanguageContextInitialized(TruffleContext truffleContext, LanguageInfo language) {
        if (this.context == null && !isContextInitialized) {
            this.isContextInitialized = true;
            this.context = this.sup.get();
        }
    }

    public void onLanguageContextFinalized(TruffleContext truffleContext, LanguageInfo language) {
    }

    public void onLanguageContextDisposed(TruffleContext truffleContext, LanguageInfo language) {
    }

    public void onContextClosed(TruffleContext truffleContext) {
    }
}
