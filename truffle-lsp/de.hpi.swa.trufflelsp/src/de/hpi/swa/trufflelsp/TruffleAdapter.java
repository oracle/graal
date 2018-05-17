package de.hpi.swa.trufflelsp;

import static de.hpi.swa.trufflelsp.LanguageSpecificHacks.languageSpecificFillCompletions;
import static de.hpi.swa.trufflelsp.LanguageSpecificHacks.languageSpecificFillCompletionsFromTruffleObject;
import static de.hpi.swa.trufflelsp.LanguageSpecificHacks.languageSpecificFixSourceAtPosition;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
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
import org.eclipse.lsp4j.jsonrpc.messages.Either;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Scope;
import com.oracle.truffle.api.TruffleContext;
import com.oracle.truffle.api.TruffleException;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.ContextsListener;
import com.oracle.truffle.api.instrumentation.EventBinding;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.ExecutionEventListener;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.SourceFilter;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter.SourcePredicate;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.instrumentation.TruffleInstrument.Env;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.ExecutableNode;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeAccessHelper;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

import de.hpi.swa.trufflelsp.NearestSectionsFinder.NearestSections;
import de.hpi.swa.trufflelsp.server.DiagnosticsPublisher;

public class TruffleAdapter implements ContextsListener {
    public static final String NO_TYPES_HARVESTED = "NO_TYPES_HARVESTED";
    private static final Node HAS_SIZE = Message.HAS_SIZE.createNode();
    private static final Node KEYS = Message.KEYS.createNode();
    private static final Node IS_INSTANTIABLE = Message.IS_INSTANTIABLE.createNode();
    private static final Node IS_EXECUTABLE = Message.IS_EXECUTABLE.createNode();

    private Map<String, String> uri2LangId = new HashMap<>();
    private Map<String, String> uri2Text = new HashMap<>();
    private Map<String, Boolean> uri2HarvestedTypes = new HashMap<>();
    protected Map<SourceSection, SourceSection> section2definition = new HashMap<>();
    protected Map<SourceSection, MaterializedFrame> section2frame = new HashMap<>();

    private final TruffleInstrument.Env env;
    private final PrintWriter err;
// private final PrintWriter info;
    private final SourceProvider sourceProvider;
    private DiagnosticsPublisher diagnosticsPublisher;
    private TruffleContext context;

    class SourceUriFilter implements SourcePredicate {

        public boolean test(Source source) {
            return source.getName().startsWith("file://");
        }
    }

    public TruffleAdapter(TruffleInstrument.Env env) {
        assert env != null;
        this.env = env;
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

    public synchronized void parse(String text, String langId, String uri) {
        List<Diagnostic> diagnostics = new ArrayList<>();
        try {
            parseInternal(text, langId, uri);
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

        if (!this.uri2HarvestedTypes.containsKey(uri)) {
            diagnostics.add(new Diagnostic(new Range(new Position(), new Position()), "No types harvested yet. Code completion is done via statical analysis only.", DiagnosticSeverity.Hint,
                            "Truffle",
                            NO_TYPES_HARVESTED));
        }

        this.diagnosticsPublisher.addDiagnostics(diagnostics);
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

    private CallTarget parseInternal(String text, String langId, String uri) {
        return doWithContext(() -> {
            // TODO(ds) ist alles eigentlich im SourceSectionProvider enthalten... brauchen wir die extra maps?
            this.uri2LangId.putIfAbsent(uri, langId);
            this.uri2Text.put(uri, text);

            this.uri2HarvestedTypes.remove(uri);

            this.sourceProvider.remove(langId, uri);

            System.out.println("\nParsing " + langId + " " + uri);
            Source source = Source.newBuilder(text).name(uri).language(langId).build();
            try {
                CallTarget callTarget = env.parse(source);
                SourceWrapper sourceWrapper = this.sourceProvider.getLoadedSource(langId, uri);
                sourceWrapper.setParsingSuccessful(true);
                return callTarget;
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        });
    }

    public synchronized List<? extends SymbolInformation> getSymbolInfo(String uri) {
        List<SymbolInformation> symbolInformation = new ArrayList<>();

        if (this.sourceProvider.containsKey(uri)) {
// RootNode rootNode = ssProvider.getLoadedSource(uri);
// rootNode.accept(new NodeVisitor() {
//
// public boolean visit(Node node) {
// // How to find out which kind of node we have in a language agnostic way?
// // isTaggedWith() is "protected" ...
//// System.out.println("Tagged with Literal: " + NodeAccessHelper.isTaggedWith(node,
//// LSPTags.Literal.class) + " " + node.getClass().getSimpleName() + " " +
//// node.getEncapsulatingSourceSection());
// return true;
// }
// });

// Iterable<Scope> localScopes = env.findLocalScopes(rootNode, null);
// Map<Object, Object> map = scopesToObjectMap(localScopes);
// for (Entry<Object, Object> entry : map.entrySet()) {
// if (entry.getValue() instanceof TruffleObject) {
// TruffleObject truffleObjVal = (TruffleObject) entry.getValue();
// boolean isExecutable = ForeignAccess.sendIsExecutable(Message.IS_EXECUTABLE.createNode(),
// truffleObjVal);
// SymbolInformation si = new SymbolInformation(entry.getKey().toString(), isExecutable ?
// SymbolKind.Function : SymbolKind.Variable,
// new Location(uri, new Range(new Position(), new Position())));
// symbolInformation.add(si);
// }
// }
        }

        Iterable<Scope> topScopes = env.findTopScopes(this.uri2LangId.get(uri));
        Map<String, Map<Object, Object>> scopeMap = scopesToObjectMap(topScopes);
        for (Entry<String, Map<Object, Object>> scopeEntry : scopeMap.entrySet()) {
            for (Entry<Object, Object> entry : scopeEntry.getValue().entrySet()) {
                if (entry.getValue() instanceof TruffleObject) {
                    TruffleObject truffleObjVal = (TruffleObject) entry.getValue();

                    SourceSection sourceLocation = this.findSourceLocation(truffleObjVal);
                    if (sourceLocation != null && uri.equals(sourceLocation.getSource().getName())) {
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

                        SymbolInformation si = new SymbolInformation(entry.getKey().toString(), kind, new Location(uri, range), scopeEntry.getKey());
                        symbolInformation.add(si);
                    }
                }
            }
        }
        return symbolInformation;
    }

    public static Map<String, Map<Object, Object>> scopesToObjectMap(Iterable<Scope> scopes) {
        Map<String, Map<Object, Object>> map = new HashMap<>();
        for (Scope scope : scopes) {
            Object variables = scope.getVariables();
            if (variables instanceof TruffleObject) {
                TruffleObject truffleObj = (TruffleObject) variables;
                try {
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

    public synchronized CompletionList getCompletions(String uri, int line, int originalCharacter) {
        CompletionList completions = new CompletionList();
        completions.setIsIncomplete(false);

        String langId = this.uri2LangId.get(uri);
        boolean includeGlobalsAndLocalsInCompletion = true;

        boolean sourceWasFixed = false;
        int character = originalCharacter;
        SourceWrapper sourceWrapper = this.sourceProvider.getLoadedSource(langId, uri);
        if (sourceWrapper == null || !sourceWrapper.isParsingSuccessful()) {
            // parsing failed, now try to fix simple syntax errors
            String text = this.uri2Text.get(uri);
            System.out.println("No source wrapper found, fixing source.");
            SourceFix sourceFix = fixSourceAtPosition(text, langId, uri, line, character);
            try {
// String suffix = "[FIX]";
                String suffix = "";
                parseInternal(sourceFix.text, langId, uri + suffix);
                sourceWrapper = this.sourceProvider.getLoadedSource(langId, uri + suffix);
                character = sourceFix.character;
                includeGlobalsAndLocalsInCompletion = !sourceFix.isObjectPropertyCompletion;
                sourceWasFixed = true;
// exec(uri); //TODO(ds)
            } catch (IllegalStateException e) {
                this.err.println(e.getLocalizedMessage());
                this.err.flush();
            } catch (RuntimeException e) {

            }
        }

        if (sourceWrapper != null) {
            Source source = sourceWrapper.getSource();

            if (isLineValid(line, source)) {
                int oneBasedLineNumber = zeroBasedLineToOneBasedLine(line, source);

                NearestSections nearestSections = NearestSectionsFinder.getNearestSections(source, env, oneBasedLineNumber, character);
                SourceSection containsSection = nearestSections.getContainsSourceSection();

                Node nodeForLocalScoping;
                String debugDetails = "";
                boolean isCaretDirectlyBehindContainsNode = false;
                if (containsSection == null) {
                    // We are not in a local scope, so only top scope objects possible
                    nodeForLocalScoping = null;
                } else if (isEndOfSectionMatchingCaretPosition(oneBasedLineNumber, character, containsSection)) {
                    // Our caret is directly behind the containing section, so we can simply use that one to find local
                    // scope objects
                    nodeForLocalScoping = (Node) nearestSections.getContainsNode();
                    debugDetails += "-containsEnd-";
                    isCaretDirectlyBehindContainsNode = true;
                } else if (nodeIsInChildHirarchyOf((Node) nearestSections.getNextNode(), (Node) nearestSections.getContainsNode())) {
                    // Great, the nextNode is a (indirect) sibling of our containing node, so it is in the same scope as
                    // we are and we can use it to get local scope objects
                    nodeForLocalScoping = (Node) nearestSections.getNextNode();
                    debugDetails += "-next-";
                } else if (nodeIsInChildHirarchyOf((Node) nearestSections.getPreviousNode(), (Node) nearestSections.getContainsNode())) {
                    // In this case we want call findLocalScopes() with BEHIND-flag, i.e. give me the local scope
                    // objects which are valid behind that node
                    nodeForLocalScoping = (Node) nearestSections.getPreviousNode();
                    debugDetails += "-prev-";
                } else {
                    // No next or previous node is in the same scope like us, so we can only take our containing node to
                    // get local scope objects
                    nodeForLocalScoping = (Node) nearestSections.getContainsNode();
                    debugDetails += "-contains-";
                }

                if (nodeForLocalScoping instanceof InstrumentableNode) {
                    InstrumentableNode instrumentableNode = (InstrumentableNode) nodeForLocalScoping;
                    if (instrumentableNode.isInstrumentable()) {
                        VirtualFrame frame = this.section2frame.get(nodeForLocalScoping.getSourceSection());

                        if (isCaretDirectlyBehindContainsNode) {
                            // Here we cannot use any nodeForLocalScoping instance, because this can also be the nextNode or
                            // previousNode. We need the node directly before our caret.
                            boolean arePojectPropertiesPresent = fillCompletionsWithObjectProperties(completions, frame, nodeForLocalScoping, langId);
                            if (arePojectPropertiesPresent) {
                                // If there are object properties available for code completion, then we do not want to display any
                                // globals or local variables
                                includeGlobalsAndLocalsInCompletion = false;
                            } else if (frame == null) {
                                // No frame is available for the current source section and no properties could be derived
                                this.diagnosticsPublisher.addDiagnostics(
                                                Arrays.asList(new Diagnostic(sourceSectionToRange(nodeForLocalScoping.getSourceSection()), "No types harvested for this source section yet.",
                                                                DiagnosticSeverity.Information, "Truffle")));
                            }
                        }

                        if (includeGlobalsAndLocalsInCompletion) {
                            fillCompletionsWithLocals(completions, langId, nodeForLocalScoping, debugDetails, frame);
                        }
                    }
                }
            } // isLineValid
        } else {
            // TODO(ds) remove that when solved
            System.out.println("!!! Cannot lookup Source for local scoping. No parsed Node found for URI: " + uri);
        }

        if (includeGlobalsAndLocalsInCompletion) {
            fillCompletionsWithGlobals(uri, completions, langId);
        }

        if (sourceWasFixed) {
            // If we fixed a source, than we have to remove the wrapper object from our cache here, because
            // without the fix the code would not have been parsed correctly. If we keep it in the cache, it
            // looks like the code was parsed correctly, because a source wrapper exists for the provided URI.
            this.sourceProvider.remove(langId, uri);
        }

        return completions;
    }

    private void fillCompletionsWithLocals(CompletionList completions, String langId, Node nodeForLocalScoping, String debugDetails, VirtualFrame frame) {
        doWithContext(() -> {
            Iterable<Scope> localScopes = env.findLocalScopes(nodeForLocalScoping, frame);

            Map<String, Map<Object, Object>> scopesMap = Collections.emptyMap();
            if (frame != null) {
                scopesMap = scopesToObjectMap(localScopes);
                System.out.println(scopesMap);
            }
            for (Scope scope : localScopes) {
                Object variables = scope.getVariables();
                if (variables instanceof TruffleObject) {
                    TruffleObject truffleObj = (TruffleObject) variables;
                    try {
                        TruffleObject keys = ForeignAccess.sendKeys(KEYS, truffleObj, true);
                        boolean hasSize = ForeignAccess.sendHasSize(HAS_SIZE, keys);
                        if (!hasSize) {
                            System.out.println("No size!!!");
                            continue;
                        }

                        System.out.println(
                                        nodeForLocalScoping.getClass().getSimpleName() + "\tin scope " + scope.getName() + "\t" + ObjectStructures.asList(new ObjectStructures.MessageNodes(), keys) +
                                                        " \"" + nodeForLocalScoping.getSourceSection().getCharacters() + "\"\t" + debugDetails);
                        for (Object obj : ObjectStructures.asList(new ObjectStructures.MessageNodes(), keys)) {
                            CompletionItem completion = new CompletionItem(obj.toString());
                            completion.setKind(CompletionItemKind.Variable);
                            String documentation = "in " + scope.getName();
                            if (scopesMap.containsKey(scope.getName())) {
                                Map<Object, Object> map = scopesMap.get(scope.getName());
                                if (map.containsKey(obj)) {
                                    completion.setDetail(createCompletionDetail(map.get(obj), env, langId, true));
                                }
                            }
                            completion.setDocumentation(documentation);
                            completions.getItems().add(completion);
                        }
                    } catch (UnsupportedMessageException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        });
    }

    private void fillCompletionsWithGlobals(String uri, CompletionList completions, String langId) {
        doWithContext(() -> {
            Iterable<Scope> topScopes = env.findTopScopes(this.uri2LangId.get(uri));
            Map<String, Map<Object, Object>> scopeMap = scopesToObjectMap(topScopes);
            for (Entry<String, Map<Object, Object>> scopeEntry : scopeMap.entrySet()) {
                for (Entry<Object, Object> entry : scopeEntry.getValue().entrySet()) {
                    CompletionItem completion = new CompletionItem(entry.getKey().toString());
                    if (entry.getValue() instanceof TruffleObject) {
                        TruffleObject truffleObjVal = (TruffleObject) entry.getValue();
                        boolean isExecutable = ForeignAccess.sendIsExecutable(IS_EXECUTABLE, truffleObjVal);
                        boolean isInstatiatable = ForeignAccess.sendIsExecutable(IS_INSTANTIABLE, truffleObjVal);
                        if (isExecutable) {
                            completion.setKind(CompletionItemKind.Function);
                        }
                        if (isInstatiatable) {
                            completion.setKind(CompletionItemKind.Class);
                        }
                    }
                    completion.setDetail(createCompletionDetail(entry.getValue(), env, langId, completion.getKind() == null));
                    completion.setDocumentation("in " + scopeEntry.getKey());
// completion.setFilterText(scopeEntry.getKey() + " " + completion.getLabel());
                    completions.getItems().add(completion);
                }
            }
// CompletionItem completion = new CompletionItem("m.f");
// completions.getItems().add(completion);
        });
    }

    private static SourceFix fixSourceAtPosition(String text, String langId, String uri, int line, int character) {
        Source originalSource = Source.newBuilder(text).language(langId).name(uri).build();
        int oneBasedLineNumber = zeroBasedLineToOneBasedLine(line, originalSource);
        String textAtCaretLine = originalSource.getCharacters(oneBasedLineNumber).toString();

        SourceFix sourceFix = languageSpecificFixSourceAtPosition(text, langId, character, originalSource, oneBasedLineNumber, textAtCaretLine);
        return sourceFix != null ? sourceFix : new SourceFix(text, character, false);
    }

    private boolean fillCompletionsWithObjectProperties(CompletionList completions, VirtualFrame frame, Node nodeBeforeCaret, String langId) {
        return doWithContext(() -> {
            boolean areObjectPropertiesPresent = false;
            if (frame != null) {
                ExecutableNode executableNode = this.env.parseInline(
                                Source.newBuilder(nodeBeforeCaret.getEncapsulatingSourceSection().getCharacters()).language(langId).name("dummy inline eval").build(),
                                nodeBeforeCaret,
                                frame.materialize());
                if (executableNode != null) {
                    NodeAccessHelper.insertNode(nodeBeforeCaret, executableNode);
                    try {
                        Object object = executableNode.execute(frame);
                        Object metaObject = getMetaObject(langId, object);
                        areObjectPropertiesPresent = fillCompletionsFromTruffleObject(completions, langId, object, metaObject);
                    } catch (Exception e) {
                    }
                }
            } else {
                // No frame available. Try evaluation of the code snippet in global scope.
                try {
                    // TODO(ds) here we get sometimes the wrong nodeForLocalScoping, because for example in Python we
                    // use the node before the caret for local scoping instead of the containing node which would be
                    // useful here
                    CallTarget callTarget = this.env.parse(Source.newBuilder(nodeBeforeCaret.getEncapsulatingSourceSection().getCharacters()).language(langId).name("dummy eval").build());
                    Object object = callTarget.call();
                    Object metaObject = getMetaObject(langId, object);
                    areObjectPropertiesPresent = fillCompletionsFromTruffleObject(completions, langId, object, metaObject);
                } catch (Exception e) {
                }
            }

            areObjectPropertiesPresent = languageSpecificFillCompletions(this, completions, frame, nodeBeforeCaret, langId, areObjectPropertiesPresent);

            return areObjectPropertiesPresent;
        });

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

    protected boolean fillCompletionsFromTruffleObject(CompletionList completions, String langId, Object object, Object metaObject) {
        Map<Object, Object> map;
        if (object instanceof TruffleObject) {
            map = ObjectStructures.asMap(new ObjectStructures.MessageNodes(), (TruffleObject) object);
        } else if (metaObject instanceof TruffleObject) {
            map = ObjectStructures.asMap(new ObjectStructures.MessageNodes(), ((TruffleObject) metaObject));
        } else {
            return false;
        }
        for (Entry<Object, Object> entry : map.entrySet()) {
            CompletionItem completion = new CompletionItem(entry.getKey().toString());
            completion.setKind(CompletionItemKind.Property); // TODO check type of value
            completion.setDetail(createCompletionDetail(entry.getValue(), this.env, langId, true));
            completions.getItems().add(completion);
            String documentation = "";

            documentation = languageSpecificFillCompletionsFromTruffleObject(langId, entry, completion, documentation);

            documentation += "in " + metaObject.toString();
            completion.setDocumentation(documentation);
        }

        return !map.isEmpty();
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

    private static String createCompletionDetail(Object obj, Env env, String langId, boolean includeClass) {
        LanguageInfo languageInfo = env.findLanguage(obj);
        if (languageInfo == null) {
            languageInfo = env.getLanguages().get(langId);
        }
        String metaInfo = null;
        if (languageInfo != null) {
            Object metaObject = env.findMetaObject(languageInfo, obj);
            if (metaObject instanceof TruffleObject) {
                // TODO(ds)
// System.out.println("metaObject: " + metaObject);
// ObjectStructures.asList(new ObjectStructures.MessageNodes(), ((TruffleObject) metaObject));
// ObjectStructures.asMap(new ObjectStructures.MessageNodes(), ((TruffleObject) metaObject));
                metaInfo = metaObject.toString();
            } else if (metaObject instanceof String) {
                metaInfo = metaObject.toString();
            }
        }
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

    @SuppressWarnings("unused")
    public synchronized List<? extends DocumentHighlight> getHighlights(String uri, int line, int character) {
        List<DocumentHighlight> highlights = new ArrayList<>();

// if (this.uri2nodes.containsKey(uri)) {
// RootNode rootNode = this.uri2nodes.get(uri);
//
// CharSequence token = findTokenAt(rootNode, line + 1, character + 1);
// System.out.println("token: " + token);
// if (token == null) {
// return highlights;
// }
//
// Map<CharSequence, List<DocumentHighlight>> varName2highlight = new HashMap<>();
// rootNode.accept(new NodeVisitor() {
//
// public boolean visit(Node node) {
//// if (NodeAccessHelper.isTaggedWith(node, LSPTags.VariableAssignment.class)) {
//// String varName = node.getEncapsulatingSourceSection().getCharacters().toString().split(" ")[0];
//// if (!varName2highlight.containsKey(varName)) {
//// varName2highlight.put(varName, new ArrayList<>());
//// }
//// varName2highlight.get(varName).add(new
//// DocumentHighlight(sourceSectionToRange(node.getEncapsulatingSourceSection()),
//// DocumentHighlightKind.Write));
//// } else if (NodeAccessHelper.isTaggedWith(node, LSPTags.VariableRead.class)) {
//// String varName = node.getEncapsulatingSourceSection().getCharacters().toString();
//// if (!varName2highlight.containsKey(varName)) {
//// varName2highlight.put(varName, new ArrayList<>());
//// }
//// varName2highlight.get(varName).add(new
//// DocumentHighlight(sourceSectionToRange(node.getEncapsulatingSourceSection()),
//// DocumentHighlightKind.Read));
//// }
// return true;
// }
// });
//
//// varName2highlight.entrySet().forEach((e) -> highlights.add(e.getValue()));
// if (varName2highlight.containsKey(token)) {
// highlights.addAll(varName2highlight.get(token));
// }
// } else {
// throw new RuntimeException("No parsed Node found for URI: " + uri);
// }
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

    public void exec(String uri) {
        EventBinding<ExecutionEventListener> listener = env.getInstrumenter().attachExecutionEventListener(SourceSectionFilter.newBuilder().sourceIs(new SourcePredicate() {

            public boolean test(Source source) {
                return uri.equals(source.getName());
            }
        }).build(), new ExecutionEventListener() {

            public void onReturnValue(EventContext eventContext, VirtualFrame frame, Object result) {
                System.out.println(((result instanceof TruffleObject) ? ">>> " : "") + "onReturnValue " + eventContext.getInstrumentedNode().getClass().getSimpleName() + " " + result + " " +
                                eventContext.getInstrumentedSourceSection());
                if (result instanceof TruffleObject) {
                    SourceSection sourceLocation = TruffleAdapter.this.findSourceLocation((TruffleObject) result);
                    if (sourceLocation != null && eventContext.getInstrumentedSourceSection() != null && eventContext.getInstrumentedSourceSection().isAvailable()) {
                        TruffleAdapter.this.section2definition.put(eventContext.getInstrumentedSourceSection(), sourceLocation);
                    }
                }
// Iterable<Scope> localScopes = env.findLocalScopes(eventContext.getInstrumentedNode(), frame);
// Map<String, Map<Object, Object>> scopesMap = scopesToObjectMap(localScopes);
// System.out.println(scopesMap);
// TODO (ds) ist hier was nützliches drin? Nur return-types eines Node
            }

            public void onReturnExceptional(EventContext eventContext, VirtualFrame frame, Throwable exception) {
                // TODO Auto-generated method stub

            }

            public void onEnter(EventContext eventContext, VirtualFrame frame) {
                System.out.println("onEnter " + eventContext.getInstrumentedNode().getClass().getSimpleName() + " " + eventContext.getInstrumentedSourceSection());
                TruffleAdapter.this.section2frame.put(eventContext.getInstrumentedSourceSection(), frame.materialize());
            }

            // TODO (ds) onInputValue
        });

        List<Diagnostic> diagnostics = new ArrayList<>();
        try {
            CallTarget callTarget = parseInternal(this.uri2Text.get(uri), this.uri2LangId.get(uri), uri);

            doWithContext(() -> callTarget.call());

            this.uri2HarvestedTypes.put(uri, true);
        } catch (RuntimeException e) {
            if (e instanceof TruffleException) {
                diagnostics.add(new Diagnostic(getRangeFrom((TruffleException) e), e.getMessage(), DiagnosticSeverity.Warning, "Truffle Type Harvester"));
            } else {
                this.err.println(e.getLocalizedMessage());
                this.err.flush();
            }
        } finally {
            listener.dispose();
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

    public List<? extends Location> getDefinitions(String uri, int line, int character) {
        List<Location> locations = new ArrayList<>();

        String langId = this.uri2LangId.get(uri);
        if (this.sourceProvider.getLoadedSource(langId, uri) != null) {
            SourceWrapper wrapper = this.sourceProvider.getLoadedSource(langId, uri);
            Source source = wrapper.getSource();

            NearestSections nearestSections = NearestSectionsFinder.getNearestSections(source, env, zeroBasedLineToOneBasedLine(line, source), character);
            SourceSection containsSection = nearestSections.getContainsSourceSection();
            if (containsSection != null) {
                SourceSection definition = this.section2definition.get(containsSection);
                if (definition != null) {
                    locations.add(new Location(uri, sourceSectionToRange(definition)));
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

    public synchronized Hover getHover(String uri, int line, int character) {
        List<Either<String, MarkedString>> contents = new ArrayList<>();

        String langId = this.uri2LangId.get(uri);
        if (this.sourceProvider.getLoadedSource(langId, uri) != null) {
            SourceWrapper wrapper = this.sourceProvider.getLoadedSource(langId, uri);
            Source source = wrapper.getSource();
            NearestSections nearestSections = NearestSectionsFinder.getNearestSections(source, env, zeroBasedLineToOneBasedLine(line, source), character);
            SourceSection containsSection = nearestSections.getContainsSourceSection();
            if (containsSection != null) {
                SourceSection definition = this.section2definition.get(containsSection);
                if (definition != null) {
                    MarkedString markedString = new MarkedString(this.uri2LangId.get(uri), definition.getCharacters().toString());
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
        this.context = truffleContext;
    }

    public void onLanguageContextCreated(TruffleContext truffleContext, LanguageInfo language) {
    }

    public void onLanguageContextInitialized(TruffleContext truffleContext, LanguageInfo language) {
    }

    public void onLanguageContextFinalized(TruffleContext truffleContext, LanguageInfo language) {
    }

    public void onLanguageContextDisposed(TruffleContext truffleContext, LanguageInfo language) {
    }

    public void onContextClosed(TruffleContext truffleContext) {
    }
}
