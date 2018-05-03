package de.hpi.swa.trufflelsp;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
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

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.objects.function.Arity;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.function.PythonCallable;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.type.PythonClass;
import com.oracle.graal.python.nodes.frame.ReadGlobalOrBuiltinNode;
import com.oracle.graal.python.nodes.frame.ReadLocalVariableNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Scope;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleException;
import com.oracle.truffle.api.debug.LocationFinderHelper;
import com.oracle.truffle.api.debug.SourceElement;
import com.oracle.truffle.api.debug.SuspendableLocationFinder;
import com.oracle.truffle.api.debug.SuspendableLocationFinder.NearestSections;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.EventBinding;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.ExecutionEventListener;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter.SourcePredicate;
import com.oracle.truffle.api.instrumentation.TruffleInstrument.Env;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.js.nodes.JavaScriptNode;
import com.oracle.truffle.js.nodes.access.GlobalObjectNode;
import com.oracle.truffle.js.nodes.access.JSReadFrameSlotNode;
import com.oracle.truffle.js.nodes.access.PropertyNode;
import com.oracle.truffle.sl.nodes.local.SLReadLocalVariableNode;

public class TruffleAdapter {
    public static final String NO_TYPES_HARVESTED = "NO_TYPES_HARVESTED";
    private static final Node HAS_SIZE = Message.HAS_SIZE.createNode();
    private static final Node KEYS = Message.KEYS.createNode();
    private static final Node IS_INSTANTIABLE = Message.IS_INSTANTIABLE.createNode();
    private static final Node IS_EXECUTABLE = Message.IS_EXECUTABLE.createNode();

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

            System.out.println("\nParsing " + langId + " " + uri);
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

                if (isLineValid(line, source)) {
                    int oneBasedLineNumber = zeroBasedLineToOneBasedLine(line, source);

                    NearestSections nearestSections = SuspendableLocationFinder.getNearestSections(source, env, oneBasedLineNumber, character);
                    SourceSection containsSection = nearestSections.getContainsSourceSection();

                    Node nodeForLocalScoping;
                    String debugDetails = "";
                    if (containsSection == null) {
                        // We are not in a local scope, so only top scope objects possible
                        nodeForLocalScoping = null;
                    } else if (isEndOfSectionMatchingCaretPosition(oneBasedLineNumber, character, containsSection)) {
                        // Our caret is directly behind the containing section, so we can simply use that one to find local
                        // scope objects
                        nodeForLocalScoping = (Node) nearestSections.getContainsNode();
                        debugDetails += "-containsEnd-";
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
// VirtualFrame frame = Truffle.getRuntime().createVirtualFrame(new Object[0], new
// FrameDescriptor());
                            VirtualFrame frame = null;
// Here we do not use the original node, but the (potential) copy
                            MaterializedFrame materializedFrame = this.section2frame.get(nodeForLocalScoping.getSourceSection());
                            if (materializedFrame != null) {
                                frame = materializedFrame;
                            }
                            fillCompletionsWithObjectProperties(completions, frame, nodeForLocalScoping, langId);
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

                                        System.out.println(nodeForLocalScoping.getClass().getSimpleName() + "\tin scope " + scope.getName() + (isCaretBehindNode ? "-copy-"
                                                        : "") + "\t" + ObjectStructures.asList(new ObjectStructures.MessageNodes(), keys) + " \"" +
                                                        nodeForLocalScoping.getSourceSection().getCharacters() + "\"\t" + debugDetails);
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
                } // isLineValid
            } else {
                // TODO(ds) remove that when solved
                System.out.println("!!! Cannot lookup Source for local scoping. No parsed Node found for URI: " + uri);
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

    private void fillCompletionsWithObjectProperties(CompletionList completions, VirtualFrame frame, Node nodeForLocalScoping, String langId) {
        if (langId.equals("sl") && frame != null) {
            if (nodeForLocalScoping instanceof SLReadLocalVariableNode) {
                Object object = ((SLReadLocalVariableNode) nodeForLocalScoping).executeGeneric(frame);
                if (object instanceof TruffleObject) {
                    fillCompletionsFromTruffleObject(completions, nodeForLocalScoping.getSourceSection().getCharacters().toString(), langId, (TruffleObject) object);
                }
            }
        } else if (langId.equals("js") && frame != null) {
            // TODO(ds) for js use getNodeObject()
            if (nodeForLocalScoping instanceof InstrumentableNode) {
                Object nodeObject = ((InstrumentableNode) nodeForLocalScoping).getNodeObject();
                System.out.println(nodeObject);
            }
            System.out.println();
            if (nodeForLocalScoping instanceof JSReadFrameSlotNode) {
                JSReadFrameSlotNode readFrameSlotNode = (JSReadFrameSlotNode) nodeForLocalScoping;
                Object object = readFrameSlotNode.execute(frame);
                if (object instanceof TruffleObject) {
                    fillCompletionsFromTruffleObject(completions, readFrameSlotNode.getIdentifier().toString(), langId, (TruffleObject) object);
                }
            }
// if (nodeForLocalScoping instanceof PropertyNode) {
// Object object = ((PropertyNode) nodeForLocalScoping).execute(frame);
// Map<Object, Object> map = ObjectStructures.asMap(new ObjectStructures.MessageNodes(),
// (TruffleObject) object);
// JavaScriptNode target = ((PropertyNode) nodeForLocalScoping).getTarget();
// if (target instanceof GlobalObjectNode) {
// DynamicObject globalObj = ((GlobalObjectNode) target).executeDynamicObject();
// fillCompletionsFromTruffleObject(completions, nodeForLocalScoping, langId, globalObj);
// }
// }
        } else if (langId.equals("python")) {
            if (nodeForLocalScoping instanceof ReadGlobalOrBuiltinNode) {
                VirtualFrame vFrame = frame;
                if (frame == null) {
                    vFrame = Truffle.getRuntime().createVirtualFrame(PArguments.withGlobals(PythonLanguage.getContext().getMainModule()), new FrameDescriptor());
                }
                ReadGlobalOrBuiltinNode readNode = (ReadGlobalOrBuiltinNode) nodeForLocalScoping;
                try {
                    Object object = readNode.execute(vFrame);
                    if (object instanceof TruffleObject) {
                        fillCompletionsFromTruffleObject(completions, readNode.getAttributeId(), langId, (TruffleObject) object);
                    }
                } catch (PException e) {
                }
            } else if (nodeForLocalScoping instanceof ReadLocalVariableNode && frame != null) {
                ReadLocalVariableNode readNode = (ReadLocalVariableNode) nodeForLocalScoping;
                Object object = readNode.execute(frame);
                if (object instanceof TruffleObject) {
                    fillCompletionsFromTruffleObject(completions, readNode.getSlot().getIdentifier().toString(), langId, (TruffleObject) object);
                }
            }
        }
    }

    private void fillCompletionsFromTruffleObject(CompletionList completions, String nodeIdentifier, String langId, TruffleObject object) {
        Map<Object, Object> map = ObjectStructures.asMap(new ObjectStructures.MessageNodes(), object);
        if (map.isEmpty()) {
            if (langId.equals("python")) {
                if (object instanceof PythonObject) {
                    PythonClass pythonClass = ((PythonObject) object).getPythonClass();
                    map = ObjectStructures.asMap(new ObjectStructures.MessageNodes(), pythonClass);
                }
            }
        }
        for (Entry<Object, Object> entry : map.entrySet()) {
            CompletionItem completion = new CompletionItem(nodeIdentifier + "." + entry.getKey().toString());
            completion.setKind(CompletionItemKind.Property); // TODO check type of value
            completion.setDetail(createCompletionDetail(entry.getValue(), this.getEnv(), langId, true));
            completions.getItems().add(completion);

            if (langId.equals("python")) {
                if (entry.getValue() instanceof PythonCallable) {
                    completion.setKind(CompletionItemKind.Method);
                    PythonCallable callable = (PythonCallable) entry.getValue();
                    Arity arity = callable.getArity();
                    completion.setDocumentation(arity.getFunctionName() + "(" + arity.getMaxNumOfArgs() + " argument" + (arity.getMaxNumOfArgs() == 1 ? "" : "s") + ")");
                    if (arity.getParameterIds().length > 0) {
                        String paramsString = Arrays.toString(arity.getParameterIds());
                        completion.setDocumentation(arity.getFunctionName() + "(" + paramsString.substring(1, paramsString.length() - 1) + ")");
                    }
                }

            }
        }
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
                            SourceElement.values(), zeroBasedLineToOneBasedLine(line, source), character, env);
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
                            SourceElement.values(), zeroBasedLineToOneBasedLine(line, source), character, env);
            SourceSection definition = this.section2definition.get(node.getSourceSection());
            if (definition != null) {
                MarkedString markedString = new MarkedString(this.uri2LangId.get(uri), definition.getCharacters().toString());
                contents.add(Either.forRight(markedString));
            }
        }
        return new Hover(contents);
    }
}
