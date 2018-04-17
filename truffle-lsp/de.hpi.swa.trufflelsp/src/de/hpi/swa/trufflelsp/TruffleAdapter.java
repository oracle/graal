package de.hpi.swa.trufflelsp;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

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
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Instrument;
import org.graalvm.polyglot.PolyglotException;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Scope;
import com.oracle.truffle.api.TruffleException;
import com.oracle.truffle.api.debug.LocationFinderHelper;
import com.oracle.truffle.api.debug.SourceElement;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.EventBinding;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.ExecutionEventListener;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter.SourcePredicate;
import com.oracle.truffle.api.instrumentation.TruffleInstrument.Env;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeAccessHelper;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

public class TruffleAdapter {
    public static final String NO_TYPES_HARVESTED = "NO_TYPES_HARVESTED";
    private static final Node HAS_SIZE = Message.HAS_SIZE.createNode();
    private static final Node KEYS = Message.KEYS.createNode();
    private static final Node IS_INSTANTIABLE = Message.IS_INSTANTIABLE.createNode();
    private static final Node IS_EXECUTABLE = Message.IS_EXECUTABLE.createNode();

    private static final boolean enableNodeCopyIfCaretBehindNode = false;

    private Server server;
    private Context context;
    private Map<String, String> uri2LangId = new HashMap<>();
    private Map<String, String> uri2Text = new HashMap<>();
    private Map<String, Boolean> uri2HarvestedTypes = new HashMap<>();
    protected Map<SourceSection, SourceSection> section2definition = new HashMap<>();
    protected Map<SourceSection, MaterializedFrame> section2frame = new HashMap<>();

    protected Context getContext() {
        if (this.context == null) {
            this.context = Context.newBuilder().allowAllAccess(false).allowNativeAccess(true).build(); // TODO(ds) load all languages?
        }
        return this.context;
    }

    public void connect(@SuppressWarnings("hiding") final Server server) {
        this.server = server;
    }

    private Env getEnv() {
        Instrument envInstrument = this.getContext().getEngine().getInstruments().get(EnvironmentProvider.ID);
        EnvironmentProvider envProvider = envInstrument.lookup(EnvironmentProvider.class);
        Env env = envProvider.getEnv();
        return env;
    }

    private SourceSectionProvider getSourceSectionProvider() {
        Instrument ssInstrument = this.getContext().getEngine().getInstruments().get(SourceSectionProvider.ID);
        SourceSectionProvider ssProvider = ssInstrument.lookup(SourceSectionProvider.class);
        return ssProvider;
    }

    public synchronized List<Diagnostic> parse(String text, String langId, String uri) {
        List<Diagnostic> diagnostics = new ArrayList<>();

        SourceSectionProvider ssProvider = getSourceSectionProvider();

        this.getContext().enter();
        try {
            Instrument envInstrument = this.getContext().getEngine().getInstruments().get(EnvironmentProvider.ID);
            EnvironmentProvider envProvider = envInstrument.lookup(EnvironmentProvider.class);
            Env env = envProvider.getEnv();

            this.uri2LangId.putIfAbsent(uri, langId);
            ssProvider.initLang(langId);

            this.uri2Text.put(uri, text);

            this.uri2HarvestedTypes.remove(uri);

            ssProvider.remove(langId, uri);

            Source source = Source.newBuilder(text).name(uri).language(langId).build();
            CallTarget target = env.parse(source);

// System.out.println(target);
// this.getContext().eval(Source.newBuilder(langId, text, documentUri).build());
        } catch (IllegalStateException | IOException e) {
            e.printStackTrace(ServerLauncher.errWriter());
        } catch (RuntimeException e) {
            if (e instanceof TruffleException) {
                TruffleException te = (TruffleException) e;
                Range range = new Range(new Position(), new Position());
                SourceSection sourceLocation = te.getSourceLocation() != null ? te.getSourceLocation()
                                : (te.getLocation() != null ? te.getLocation().getEncapsulatingSourceSection() : null);
                if (sourceLocation != null && sourceLocation.isAvailable()) {
                    range = sourceSectionToRange(sourceLocation);
                }

                diagnostics.add(new Diagnostic(range, e.getMessage(), DiagnosticSeverity.Error, "Truffle"));
            } else {
                throw new RuntimeException(e);
            }
        } finally {
            this.getContext().leave();
        }

        if (!this.uri2HarvestedTypes.containsKey(uri)) {
            diagnostics.add(new Diagnostic(new Range(new Position(), new Position()), "No types harvested yet. Code completion is done via statical analysis only.", DiagnosticSeverity.Hint,
                            "Truffle",
                            NO_TYPES_HARVESTED));
        }

        return diagnostics;
    }

    public synchronized List<? extends SymbolInformation> getSymbolInfo(String uri) {
        List<SymbolInformation> symbolInformation = new ArrayList<>();

        Env env = getEnv();
        SourceSectionProvider ssProvider = getSourceSectionProvider();

        this.getContext().enter();
        try {

            if (ssProvider.containsKey(uri)) {
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
        } finally {
            this.getContext().leave();
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
                        System.out.println("No size!!!");
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

    public synchronized CompletionList getCompletions(String uri, int line, int character) {
        this.getContext().enter();
        SourceSectionProvider ssProvider = getSourceSectionProvider();
        try {
            Env env = getEnv();

            CompletionList completions = new CompletionList();
            completions.setIsIncomplete(false);

            String langId = this.uri2LangId.get(uri);
            boolean isCaretBehindNode = false;
            if (ssProvider.getLoadedSource(langId, uri) != null) {
                SourceWrapper wrapper = ssProvider.getLoadedSource(langId, uri);
                Source source = wrapper.getSource();
                Node node = LocationFinderHelper.findNearest(source,
                                SourceElement.values(), line + 1, character, env);
                Node nodeForLocalScoping = node;
                if (enableNodeCopyIfCaretBehindNode && node != null) {
                    int offset = source.getLineStartOffset(line + 1); // TODO(ds) this +1 causes line out of bounds at
                                                                      // com.oracle.truffle.api.source.TextMap.lineStartOffset(TextMap.java:204)
                    if (character > 0) {
                        offset += character - 1;
                    }
                    isCaretBehindNode = node.getSourceSection().getCharEndIndex() <= offset;
                    if (isCaretBehindNode) {
                        // This case can only happen, if there is no other sibling behind the caret, i.e. we need to
                        // duplicate the current node to provide a valid scope.

                        // TODO(ds) we need to duplicate a node, in case that it is the last node in a scope and would
                        // otherwise report no variables if the user moves the caret behind the last node in the scope and
                        // asks for completion. -> But this is not working correctly, we cannot detect, if the cursor is
                        // behind the last statement or still in it (before a semicolon).
                        // Assumption: we are behind!
                        Node parentCopy = node.getParent().copy();
                        Node copy = node.copy();
                        NodeAccessHelper.insertChild(copy, parentCopy);
                        nodeForLocalScoping = copy;
                    }
                    System.out.println("nearest: " + node.getClass().getSimpleName() + " " + node.getSourceSection());
                }

                if (node instanceof InstrumentableNode) {
                    InstrumentableNode instrumentableNode = (InstrumentableNode) node;
                    if (instrumentableNode.isInstrumentable()) {
// VirtualFrame frame = Truffle.getRuntime().createVirtualFrame(new Object[0], new
// FrameDescriptor());
                        VirtualFrame frame = null;
// Here we do not use the original node, but the (potential) copy
                        MaterializedFrame materializedFrame = this.section2frame.get(nodeForLocalScoping.getSourceSection());
                        if (materializedFrame != null) {
                            frame = materializedFrame;
                        }
                        Iterable<Scope> localScopes = env.findLocalScopes(nodeForLocalScoping, frame);
                        Map<String, Map<Object, Object>> scopesMap = Collections.emptyMap();
                        if (materializedFrame != null) {
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

                                    System.out.println(node.getClass().getSimpleName() + "\tin " + scope.getName() + (isCaretBehindNode ? "\t-copy-"
                                                    : "\t") + "\t" + ObjectStructures.asList(new ObjectStructures.MessageNodes(), keys) + " " +
                                                    node.getSourceSection().getCharacters());
                                    for (Object obj : ObjectStructures.asList(new ObjectStructures.MessageNodes(), keys)) {
                                        // TODO(ds) check obj type
                                        CompletionItem completion = new CompletionItem(obj.toString());
                                        completion.setKind(CompletionItemKind.Variable);
// completion.setDetail(obj.toString());
                                        String documentation = "in " + scope.getName();
                                        if (scopesMap.containsKey(scope.getName())) {
                                            Map<Object, Object> map = scopesMap.get(scope.getName());
                                            if (map.containsKey(obj)) {
// documentation += "\nHarvested type: " + map.get(obj).getClass().getName();
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
                    }
                }

            } else {
                System.out.println("!!! No parsed Node found for URI: " + uri);
            }

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
            return completions;
        } finally {
            this.getContext().leave();
        }
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

    private static Range sourceSectionToRange(org.graalvm.polyglot.SourceSection section) {
        return new Range(
                        new Position(section.getStartLine() - 1, section.getStartColumn() - 1),
                        new Position(section.getEndLine() - 1, section.getEndColumn() /* -1 */));
    }

    private static Range sourceSectionToRange(SourceSection section) {
        return new Range(
                        new Position(section.getStartLine() - 1, section.getStartColumn() - 1),
                        new Position(section.getEndLine() - 1, section.getEndColumn() /* -1 */));
    }

    public void exec(String uri) {
        try {
            final Env env = getEnv();
            EventBinding<ExecutionEventListener> listener = env.getInstrumenter().attachExecutionEventListener(SourceSectionFilter.newBuilder().sourceIs(new SourcePredicate() {

                public boolean test(Source source) {
                    return uri.equals(source.getName());
                }
            }).build(), new ExecutionEventListener() {

                public void onReturnValue(EventContext context, VirtualFrame frame, Object result) {
                    System.out.println(((result instanceof TruffleObject) ? ">>> " : "") + "onReturnValue " + context.getInstrumentedNode().getClass().getSimpleName() + " " + result + " " +
                                    context.getInstrumentedSourceSection());
                    if (result instanceof TruffleObject) {
                        SourceSection sourceLocation = TruffleAdapter.this.findSourceLocation((TruffleObject) result);
                        if (sourceLocation != null && context.getInstrumentedSourceSection() != null && context.getInstrumentedSourceSection().isAvailable()) {
                            TruffleAdapter.this.section2definition.put(context.getInstrumentedSourceSection(), sourceLocation);
                        }
                    }
                    Iterable<Scope> localScopes = env.findLocalScopes(context.getInstrumentedNode(), frame);
                    Map<String, Map<Object, Object>> scopesMap = scopesToObjectMap(localScopes);
                    System.out.println(scopesMap);
                    // TODO (ds) ist hier was nützliches drin? Nur return-types eines Node
                }

                public void onReturnExceptional(EventContext context, VirtualFrame frame, Throwable exception) {
                    // TODO Auto-generated method stub

                }

                public void onEnter(EventContext context, VirtualFrame frame) {
                    System.out.println("onEnter " + context.getInstrumentedNode().getClass().getSimpleName() + " " + context.getInstrumentedSourceSection());
                    TruffleAdapter.this.section2frame.put(context.getInstrumentedSourceSection(), frame.materialize());
                }

                // TODO (ds) onInputValue
            });

            List<Diagnostic> diagnostics = new ArrayList<>();
            try {
// ssProvider.remove(this.uri2LangId.get(uri), uri);

                org.graalvm.polyglot.Source source = org.graalvm.polyglot.Source.newBuilder(this.uri2LangId.get(uri), this.uri2Text.get(uri), uri).build();
                this.getContext().eval(source);

                this.uri2HarvestedTypes.put(uri, true);
            } catch (PolyglotException e) {
                Range range = new Range(new Position(), new Position());
                org.graalvm.polyglot.SourceSection sourceLocation = e.getSourceLocation();
                if (sourceLocation != null && sourceLocation.isAvailable()) {
                    range = sourceSectionToRange(sourceLocation);
                }
                diagnostics.add(new Diagnostic(range, e.getMessage(), DiagnosticSeverity.Warning, "Truffle Type Harvester"));
            } finally {
                listener.dispose();
                this.server.reportDiagnostics(diagnostics, uri);
            }
        } catch (IOException e) {
            e.printStackTrace(ServerLauncher.errWriter());
        }
    }

    public List<? extends Location> getDefinitions(String uri, int line, int character) {
        Env env = getEnv();
        SourceSectionProvider ssProvider = getSourceSectionProvider();

        List<Location> locations = new ArrayList<>();

        String langId = this.uri2LangId.get(uri);
        if (ssProvider.getLoadedSource(langId, uri) != null) {
            SourceWrapper wrapper = ssProvider.getLoadedSource(langId, uri);
            Source source = wrapper.getSource();
            Node node = LocationFinderHelper.findNearest(source,
                            SourceElement.values(), line + 1, character, env);
            SourceSection definition = this.section2definition.get(node.getSourceSection());
            if (definition != null) {
                locations.add(new Location(uri, sourceSectionToRange(definition)));
            }
        }
        return locations;
    }

    protected SourceSection findSourceLocation(TruffleObject obj) {
        Env env = getEnv();
        LanguageInfo lang = env.findLanguage(obj);

        if (lang != null) {
            return env.findSourceLocation(lang, obj);
        }

        return null;
    }

    public synchronized Hover getHover(String uri, int line, int character) {
        List<Either<String, MarkedString>> contents = new ArrayList<>();

        Env env = getEnv();
        SourceSectionProvider ssProvider = getSourceSectionProvider();

        String langId = this.uri2LangId.get(uri);
        if (ssProvider.getLoadedSource(langId, uri) != null) {
            SourceWrapper wrapper = ssProvider.getLoadedSource(langId, uri);
            Source source = wrapper.getSource();
            Node node = LocationFinderHelper.findNearest(source,
                            SourceElement.values(), line + 1, character, env);
            SourceSection definition = this.section2definition.get(node.getSourceSection());
            if (definition != null) {
                MarkedString markedString = new MarkedString(this.uri2LangId.get(uri), definition.getCharacters().toString());
                contents.add(Either.forRight(markedString));
            }
        }
        return new Hover(contents);
    }
}
