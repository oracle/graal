package de.hpi.swa.trufflelsp;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.DocumentHighlight;
import org.eclipse.lsp4j.DocumentHighlightKind;
import org.eclipse.lsp4j.InsertTextFormat;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.SymbolKind;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.services.LanguageClient;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Instrument;
import org.graalvm.polyglot.Language;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.SourceSection;
import org.truffleruby.language.LazyRubyRootNode;

import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Scope;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleException;
import com.oracle.truffle.api.debug.LocationFinderHelper;
import com.oracle.truffle.api.debug.SourceElement;
import com.oracle.truffle.api.debug.SuspendAnchor;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.LoadSourceSectionEvent;
import com.oracle.truffle.api.instrumentation.LoadSourceSectionListener;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter.SourcePredicate;
import com.oracle.truffle.api.instrumentation.StandardTags.ExpressionTag;
import com.oracle.truffle.api.instrumentation.StandardTags.StatementTag;
import com.oracle.truffle.api.instrumentation.TruffleInstrument.Env;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeAccessHelper;
import com.oracle.truffle.api.nodes.NodeVisitor;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.sl.SLLanguage;

import de.hpi.swa.trufflelsp.helper.Pair;

public class TruffleAdapter {
    private static final Node HAS_SIZE = Message.HAS_SIZE.createNode();
    private static final Node KEYS = Message.KEYS.createNode();
    private static final Node IS_INSTANTIABLE = Message.IS_INSTANTIABLE.createNode();
    private static final Node IS_EXECUTABLE = Message.IS_EXECUTABLE.createNode();

    public static final String SOURCE_SECTION_ID = "TruffleLSPTestSection";
    public static final String SOURCE_SECTION_ID_RUBY = "TruffleLSPTestSectionRuby";
    public static final String SOURCE_SECTION_DUMMY = "TruffleLSPDummySection";
    public static final URI RUBY_DUMMY_URI = URI.create("truffle:rubytestABC.rb");

    private LanguageClient client;
    private Workspace workspace;
    private Context context;
    private Map<String, String> uri2LangId = new HashMap<>();

    protected Context getContext() {
        if (this.context == null) {
            this.context = Context.create(); // TODO(ds) load all languages?
        }
        return this.context;
    }

    public void connect(final LanguageClient client, Workspace workspace) {
        this.client = client;
        this.workspace = workspace;
    }

    public void reportDiagnostics(final List<Diagnostic> diagnostics, final String documentUri) {
        if (diagnostics != null) {
            PublishDiagnosticsParams result = new PublishDiagnosticsParams();
            result.setDiagnostics(diagnostics);
            result.setUri(documentUri);
            client.publishDiagnostics(result);
        }
    }

    public void parseExample(com.oracle.truffle.api.instrumentation.TruffleInstrument.Env env) throws IOException {
        CallTarget targetPy = env.parse(com.oracle.truffle.api.source.Source.newBuilder("2+2").name("PythonSampleSection").mimeType("application/x-python").build());
        System.out.println("python " + targetPy);
        CallTarget targetRb = env.parse(com.oracle.truffle.api.source.Source.newBuilder("2+2").name("RubySampleSection").mimeType("application/x-ruby").build());
        System.out.println("ruby " + targetRb);
        CallTarget targetSl = env.parse(com.oracle.truffle.api.source.Source.newBuilder("function main() {\n  return 2+2;\n}").name("SLSampleSection").mimeType("application/x-sl").build());
        System.out.println("sl " + targetSl);

        RootCallTarget rctPy = (RootCallTarget) targetPy;
        RootCallTarget rctRb = (RootCallTarget) targetRb;
        RootCallTarget rctSl = (RootCallTarget) targetSl;

        RootNode rootNodePy = rctPy.getRootNode();

        rootNodePy.accept(new NodeVisitor() {

            public boolean visit(Node node) {
                System.out.println("python " + node);
                return true;
            }
        });

        RootNode rootNodeRb = rctRb.getRootNode();

        rootNodeRb.accept(new NodeVisitor() {

            public boolean visit(Node node) {
                System.out.println("ruby " + node);
                return true;
            }
        });

        RootNode rootNodeSl = rctSl.getRootNode();

        rootNodeSl.accept(new NodeVisitor() {

            public boolean visit(Node node) {
                System.out.println("sl " + node);
                return true;
            }
        });

        System.out.println("dummy");
    }

    public synchronized List<Diagnostic> parse(String text, String langId, String documentUri) {
        List<Diagnostic> diagnostics = new ArrayList<>();

        this.getContext().enter();
        try {
            Instrument envInstrument = this.getContext().getEngine().getInstruments().get(EnvironmentProvider.ID);
            EnvironmentProvider envProvider = envInstrument.lookup(EnvironmentProvider.class);
            Env env = envProvider.getEnv();

            Instrument ssInstrument = this.getContext().getEngine().getInstruments().get(SourceSectionProvider.ID);
            SourceSectionProvider ssProvider = ssInstrument.lookup(SourceSectionProvider.class);

            this.uri2LangId.putIfAbsent(documentUri, langId);
            ssProvider.initLang(langId);

            // TODO(ds) Clean-up does not clean SL built-in functions
            ssProvider.remove(langId, documentUri);

            CallTarget target = env.parse(com.oracle.truffle.api.source.Source.newBuilder(text).name(documentUri).language(langId).build());
// System.out.println(target);
// this.getContext().eval(Source.newBuilder(langId, text, documentUri).build());
        } catch (IllegalStateException | IOException e) {
            e.printStackTrace(ServerLauncher.errWriter());
        } catch (RuntimeException e) {
            if (e instanceof TruffleException) {
                TruffleException te = (TruffleException) e;
                Range range = new Range(new Position(), new Position());
                com.oracle.truffle.api.source.SourceSection sourceLocation = te.getSourceLocation() != null ? te.getSourceLocation()
                                : (te.getLocation() != null ? te.getLocation().getEncapsulatingSourceSection() : null);
                if (sourceLocation != null && sourceLocation.isAvailable()) {
                    range = sourceSectionToRange(te.getSourceLocation());
                }

                diagnostics.add(new Diagnostic(range, e.getMessage(), DiagnosticSeverity.Error, "Truffle"));
            } else {
                throw new RuntimeException(e);
            }
        } finally {
            this.getContext().leave();
        }
        return diagnostics;
    }

    public synchronized List<Diagnostic> parseExperimental(String text, String langId, String documentUri) {
        List<Diagnostic> diagnostics = new ArrayList<>();
// this.uri2nodes.remove(documentUri);
        try {
            if (langId.equals("truffle-python")) {
                String lang = "python";
// Source source = Source.newBuilder(lang, text, documentUri).build();
// Context context = Context.create(lang);
                Context context = Context.create();
                Instrument instrument = context.getEngine().getInstruments().get(GlobalsInstrument.ID);
                System.out.println(instrument);

                GlobalsInstrument globalsInstrument = instrument.lookup(GlobalsInstrument.class);
                System.out.println(globalsInstrument.getEnv());
// Value value = context.eval(Source.newBuilder(lang, "globvar = 1\n2+2\ndef abc():\n myLocal = 3\n
// return myLocal\nabc()+globvar", SOURCE_SECTION_ID).build());
// context.eval(Source.newBuilder(lang, "1", SOURCE_SECTION_DUMMY).build());
                context.enter();

                System.out.println(globalsInstrument.getEnv());
                try {
                    CallTarget callTarget = globalsInstrument.getEnv().parse(
                                    com.oracle.truffle.api.source.Source.newBuilder(text).name(SOURCE_SECTION_ID).mimeType("application/x-python").build());
// System.out.println(callTarget);
// callTarget.call();

                    System.out.println("Result: " + context.eval(Source.newBuilder(lang, text, SOURCE_SECTION_ID).build()));
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (Exception e) {
                    Range range = extractRange(e.getMessage());
                    diagnostics.add(new Diagnostic(range, e.getMessage(), DiagnosticSeverity.Error, lang));
                }
                context.leave();
            } else if (langId.equals("truffle-ruby")) {
                String lang = "ruby";
// Context context = Context.create(lang);
                Context context = Context.create();
                Instrument instrument = context.getEngine().getInstruments().get(GlobalsInstrument.ID);
                GlobalsInstrument globalsInstrument = instrument.lookup(GlobalsInstrument.class);

                context.enter();
                try {
                    CallTarget callTarget = globalsInstrument.getEnv().parse(
                                    com.oracle.truffle.api.source.Source.newBuilder(text).name(SOURCE_SECTION_ID_RUBY).language(lang).build());

                    parseExample(globalsInstrument.getEnv());
//
// CallTarget callTarget = globalsInstrument.getEnv().parse(
// com.oracle.truffle.api.source.Source.newBuilder(text).uri(RUBY_DUMMY_URI).name(SOURCE_SECTION_ID_RUBY).mimeType("application/x-ruby").build());
//// com.oracle.truffle.api.source.Source.newBuilder(new File(URI.create(documentUri))).build());
// System.out.println(globalsInstrument.uris);
// if (callTarget instanceof RootCallTarget) {
// System.out.println(callTarget);
// RootCallTarget rct = (RootCallTarget) callTarget;
// System.out.println(rct.getRootNode());
// System.out.println(rct.getRootNode().getName());
// }
// System.out.println("Result: " + context.eval(Source.newBuilder(lang, text,
// SOURCE_SECTION_ID).build()));
                } catch (Exception e) {
// ((PolyglotException)e).getSourceLocation()
                    Range range = extractRange(e.getMessage());
                    diagnostics.add(new Diagnostic(range, e.getMessage(), DiagnosticSeverity.Error, lang));
                }
                context.leave();
            } else if (langId.equals("simplelanguage")) {
                String lang = SLLanguage.ID;
                String mimeType = SLLanguage.MIME_TYPE;
                Context context = this.getContext();
                Instrument envInstrument = context.getEngine().getInstruments().get(EnvironmentProvider.ID);
                EnvironmentProvider envProvider = envInstrument.lookup(EnvironmentProvider.class);
                Env env = envProvider.getEnv();

                Instrument ssInstrument = context.getEngine().getInstruments().get(SourceSectionProvider.ID);
                SourceSectionProvider ssProvider = ssInstrument.lookup(SourceSectionProvider.class);

                // Clean-up does not clean SL built-in functions
                ssProvider.remove(langId, documentUri);

                context.enter();
                CallTarget callTarget = env.parse(com.oracle.truffle.api.source.Source.newBuilder(text).name(documentUri).mimeType(mimeType).build());

                Iterable<Scope> findTopScopes = env.findTopScopes("sl");
                scopesToObjectMap(findTopScopes);

                SourceWrapper sourceWrapper = ssProvider.getLoadedSource(langId, documentUri);
                for (Node node : sourceWrapper.getNodes()) {
                    if (node instanceof InstrumentableNode) {
                        InstrumentableNode instrumentedNode = (InstrumentableNode) node;
                        if (instrumentedNode.isInstrumentable()) {
                            Iterable<Scope> localScopes = env.findLocalScopes(node, null);
                            Map<Object, Object> map = new HashMap<>();
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

// System.out.println(node.getClass().getSimpleName() + "\t" + ObjectStructures.asList(new
// ObjectStructures.MessageNodes(), keys) + " " +
// node.getSourceSection().getCharacters());
                                    } catch (UnsupportedMessageException e) {
                                        throw new RuntimeException(e);
                                    }
                                }
                            }
                            for (Entry<Object, Object> entry : map.entrySet()) {
                                if (entry.getValue() instanceof TruffleObject) {
                                    TruffleObject truffleObjVal = (TruffleObject) entry.getValue();
                                    boolean isExecutable = ForeignAccess.sendIsExecutable(IS_EXECUTABLE, truffleObjVal);
                                    System.out.println(entry.getKey() + " " + entry.getValue() + " isExecutable: " + isExecutable);
// SymbolInformation si = new SymbolInformation(entry.getKey().toString(), isExecutable ?
// SymbolKind.Function : SymbolKind.Variable,
// new Location(uri, new Range(new Position(), new Position())));
// symbolInformation.add(si);
                                }
                            }
                        }
                    }
                }

// System.out.println("Result: " + context.eval(Source.newBuilder(lang, text,
// documentUri).build()));
                context.leave();
            }
        } catch (IllegalStateException | IOException e) {
            e.printStackTrace(ServerLauncher.errWriter());
        } catch (PolyglotException e) {
            if (this.workspace.isVerbose()) {
                e.printStackTrace(ServerLauncher.errWriter());
            }

            Range range = new Range(new Position(), new Position());

            if (e.isSyntaxError() && (e.getSourceLocation() == null || !e.getSourceLocation().isAvailable())) {
                range = extractRange(e.getMessage());
            }

            if (e.getSourceLocation() != null && e.getSourceLocation().isAvailable()) {
                SourceSection ss = e.getSourceLocation();
                range = sourceSectionToRange(ss);
            }
            diagnostics.add(new Diagnostic(range, e.getMessage(), DiagnosticSeverity.Error, "Polyglot"));
        } catch (RuntimeException e) {
            if (e instanceof TruffleException) {
                TruffleException te = (TruffleException) e;
                Range range = new Range(new Position(), new Position());
                com.oracle.truffle.api.source.SourceSection sourceLocation = te.getSourceLocation() != null ? te.getSourceLocation()
                                : (te.getLocation() != null ? te.getLocation().getEncapsulatingSourceSection() : null);
                if (sourceLocation != null && sourceLocation.isAvailable()) {
                    range = sourceSectionToRange(te.getSourceLocation());
                }

                diagnostics.add(new Diagnostic(range, e.getMessage(), DiagnosticSeverity.Error, "Truffle"));
            } else {
                throw new RuntimeException(e);
            }
        }
        return diagnostics;
    }

    public static Range extractRange(String message) {
        Range range = new Range(new Position(), new Position());

        Pattern pattern = Pattern.compile(", line: (\\d+), index: (\\d+),");
        Matcher matcher = pattern.matcher(message);
        if (matcher.find() && matcher.groupCount() == 2) {
            String lineText = matcher.group(1);
            lineText = matcher.group(1);
            String indexText = matcher.group(2);
            int line = Integer.parseInt(lineText);
            int index = Integer.parseInt(indexText);
            range = new Range(
                            new Position(line - 1, index),
                            new Position(line - 1, index));
        }

        return range;
    }

    public static String docUriToNormalizedPath(final String documentUri) throws URISyntaxException {
        URI uri = new URI(documentUri).normalize();
        return uri.getPath();
    }

    public synchronized List<? extends SymbolInformation> getSymbolInfo(String uri) {
        List<SymbolInformation> symbolInformation = new ArrayList<>();

        String lang = "sl";
        Instrument instrument = this.getContext().getEngine().getInstruments().get(EnvironmentProvider.ID);
        EnvironmentProvider envProvider = instrument.lookup(EnvironmentProvider.class);
        Env env = envProvider.getEnv();

        Instrument ssInstrument = this.getContext().getEngine().getInstruments().get(SourceSectionProvider.ID);
        SourceSectionProvider ssProvider = ssInstrument.lookup(SourceSectionProvider.class);

        this.getContext().enter();

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

        Iterable<Scope> topScopes = env.findTopScopes(lang);
        Map<Object, Object> map = scopesToObjectMap(topScopes);
        for (Entry<Object, Object> entry : map.entrySet()) {
            if (entry.getValue() instanceof TruffleObject) {
                TruffleObject truffleObjVal = (TruffleObject) entry.getValue();
                boolean isExecutable = ForeignAccess.sendIsExecutable(IS_EXECUTABLE,
                                truffleObjVal);
                SymbolInformation si = new SymbolInformation(entry.getKey().toString(), isExecutable ? SymbolKind.Function : SymbolKind.Variable,
                                new Location(uri, new Range(new Position(), new Position())));
                symbolInformation.add(si);
            }
        }

        this.getContext().leave(); // TODO(ds) finally

        return symbolInformation;
    }

    public static Map<Object, Object> scopesToObjectMap(Iterable<Scope> scopes) {
        Map<Object, Object> map = new HashMap<>();
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

                    map.putAll(ObjectStructures.asMap(new ObjectStructures.MessageNodes(), truffleObj));
                } catch (UnsupportedMessageException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        return map;
    }

    public synchronized CompletionList getCompletions(String uri, int line, int character) {
        this.getContext().enter();
        try {
            Instrument envInstrument = this.getContext().getEngine().getInstruments().get(EnvironmentProvider.ID);
            EnvironmentProvider envProvider = envInstrument.lookup(EnvironmentProvider.class);
            Env env = envProvider.getEnv();

            Instrument ssInstrument = this.getContext().getEngine().getInstruments().get(SourceSectionProvider.ID);
            SourceSectionProvider ssProvider = ssInstrument.lookup(SourceSectionProvider.class);

            String langId = this.uri2LangId.get(uri);
            boolean isCaretBehindNode = false;
            if (ssProvider.getLoadedSource(langId, uri) != null) {
                SourceWrapper sourceWrapper = ssProvider.getLoadedSource(langId, uri);
                com.oracle.truffle.api.source.Source source = sourceWrapper.getSource();
                Node node = LocationFinderHelper.findNearest(source,
                                SourceElement.values(), line + 1, character, env);
                Node nodeForLocalScoping = node;
                if (node != null) {
                    int offset = source.getLineStartOffset(line + 1);
                    if (character > 0) {
                        offset += character - 1;
                    }
                    isCaretBehindNode = node.getSourceSection().getCharEndIndex() <= offset;
                    if (isCaretBehindNode) {
                        // This case can only happen, if there is no other sibling behind the caret, i.e. we need to
                        // duplicate the current node to provide a valid scope.

                        // TODO(ds) we need to duplicate a node, in case that it is the last node in a scope and would
                        // otherwise report no variables if the user moves the caret behind the last node in the scope and
                        // asks for completion. -> But this is not working correctly, when cannot detect, if the cursor is
                        // behind the last statement or still in it (before a semicolon).
                        // Assumption: we are behind!
                        Node parentCopy = node.getParent().copy();
                        Node copy = node.copy();
                        NodeAccessHelper.insertChild(copy, parentCopy);
                        nodeForLocalScoping = copy;
                    }
                    System.out.println("nearest: " + node.getClass().getSimpleName() + " " + node.getSourceSection());
                }

                CompletionList completions = new CompletionList();
                completions.setIsIncomplete(false);

                if (node instanceof InstrumentableNode) {
                    InstrumentableNode instrumentableNode = (InstrumentableNode) node;
                    if (instrumentableNode.isInstrumentable()) {
// VirtualFrame frame = Truffle.getRuntime().createVirtualFrame(new Object[0], new
// FrameDescriptor());
                        VirtualFrame frame = null;
// Here we do not use the original node, but the (potential) copy
                        Iterable<Scope> localScopes = env.findLocalScopes(nodeForLocalScoping, frame);
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

                                    System.out.println(node.getClass().getSimpleName() + (isCaretBehindNode ? "\t-copy-"
                                                    : "\t") + "\t" + ObjectStructures.asList(new ObjectStructures.MessageNodes(), keys) + " " +
                                                    node.getSourceSection().getCharacters());
                                    for (Object obj : ObjectStructures.asList(new ObjectStructures.MessageNodes(), keys)) {
                                        // TODO(ds) check obj type
                                        CompletionItem completion = new CompletionItem(obj.toString());
                                        completion.setKind(CompletionItemKind.Variable);
                                        completions.getItems().add(completion);
                                    }
                                } catch (UnsupportedMessageException e) {
                                    throw new RuntimeException(e);
                                }
                            }
                        }
                    }
                }

                Iterable<Scope> topScopes = env.findTopScopes(this.uri2LangId.get(uri));
                Map<Object, Object> map = scopesToObjectMap(topScopes);
                for (Entry<Object, Object> entry : map.entrySet()) {
                    if (entry.getValue() instanceof TruffleObject) {
                        TruffleObject truffleObjVal = (TruffleObject) entry.getValue();
                        boolean isExecutable = ForeignAccess.sendIsExecutable(IS_EXECUTABLE, truffleObjVal);
                        boolean isInstatiatable = ForeignAccess.sendIsExecutable(IS_INSTANTIABLE, truffleObjVal);
                        CompletionItem completion = new CompletionItem(entry.getKey().toString());
                        if (isExecutable) {
                            completion.setKind(CompletionItemKind.Function);
                        }
                        if (isInstatiatable) {
                            completion.setKind(CompletionItemKind.Class);
                        }
                        completions.getItems().add(completion);
                    }
                }

                return completions;
            } else {
                throw new RuntimeException("No parsed Node found for URI: " + uri);
            }
        } finally {
            this.getContext().leave();
        }
    }

    public synchronized CompletionList getCompletionsExperimental(String uri, int line, int character) {
        this.getContext().enter();
        try {
            Instrument envInstrument = this.getContext().getEngine().getInstruments().get(EnvironmentProvider.ID);
            EnvironmentProvider envProvider = envInstrument.lookup(EnvironmentProvider.class);
            Env env = envProvider.getEnv();

            Instrument ssInstrument = this.getContext().getEngine().getInstruments().get(SourceSectionProvider.ID);
            SourceSectionProvider ssProvider = ssInstrument.lookup(SourceSectionProvider.class);

            String langId = this.uri2LangId.get(uri);
            if (ssProvider.getLoadedSource(langId, uri) != null) {
// RootNode rootNode = this.uri2nodes.get(uri);
// String lang = rootNode.getLanguageInfo().getId();
                String lang = "sl";
// {
// Pair<Node, Node> nodeAtCaretAndBefore = findNodeAt(ssProvider.getLoadedSource(uri).getNodes(),
// line + 1,
// character);
// Node node = nodeAtCaretAndBefore.getFirst() == null ? nodeAtCaretAndBefore.getSecond() :
// nodeAtCaretAndBefore.getFirst();
// System.out.println("isLast: " + nodeAtCaretAndBefore.getFirst());
// }
                SourceWrapper sourceWrapper = ssProvider.getLoadedSource(langId, uri);
                com.oracle.truffle.api.source.Source source = sourceWrapper.getSource();
                Node node = LocationFinderHelper.findNearest(source,
                                SourceElement.values(), line + 1, character, env);
                if (node != null) {
                    System.out.println("nearest: " + node.getClass().getSimpleName() + " " + node.getSourceSection());
                }

// if (loadedNode instanceof InstrumentableNode) {
// InstrumentableNode instrumentableNode = (InstrumentableNode) loadedNode;
// int offset = line > 0 ? source.getLineStartOffset(line) : 0;
// if (character > 0) {
// offset += character - 1;
// }
// Node nearestNodeAt = instrumentableNode.findNearestNodeAt(offset, new
// HashSet<>(Arrays.asList(StatementTag.class, ExpressionTag.class)));
// if (nearestNodeAt != null) {
//// System.out.println("+" + nearestNodeAt.getClass().getSimpleName() + " " +
//// nearestNodeAt.getSourceSection().getCharacters());
// }
// }

                CompletionList completions = new CompletionList();
                completions.setIsIncomplete(false);

                if (node instanceof InstrumentableNode) {
                    InstrumentableNode instrumentableNode = (InstrumentableNode) node;
                    if (instrumentableNode.isInstrumentable()) {
// boolean[] isLastChild = {false};
// node.getParent().getChildren().forEach(n -> {
// isLastChild[0] = (n == node);
// System.out.println("+" + n.getSourceSection());
// });
                        Node nodeForLocalScoping = node;
// if (isLastChild[0]) {
// // TODO(ds) we need to duplicate a node, in case that it is the last node in a scope and would
// // otherwise report no variables if the user moves the caret behind the last node in the scope
// and
// // asks for completion. -> But this is not working correctly, when cannot detect, if the cursor
// is
// // behind the last statement or still in it (before a semicolon).
// Node parentCopy = node.getParent().copy();
// Node copy = node.copy();
// NodeAccessHelper.insertChild(copy, parentCopy);
// nodeForLocalScoping = copy;
// }
                        Iterable<Scope> localScopes = env.findLocalScopes(nodeForLocalScoping, null);
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

                                    System.out.println(node.getClass().getSimpleName() + "\t" + ObjectStructures.asList(new ObjectStructures.MessageNodes(), keys) + " " +
                                                    node.getSourceSection().getCharacters());
                                    for (Object obj : ObjectStructures.asList(new ObjectStructures.MessageNodes(), keys)) {
                                        // TODO(ds) check obj type
                                        CompletionItem completion = new CompletionItem(obj.toString());
                                        completion.setKind(CompletionItemKind.Variable);
                                        completions.getItems().add(completion);
                                    }
                                } catch (UnsupportedMessageException e) {
                                    throw new RuntimeException(e);
                                }
                            }
                        }
                    }
                }

                Iterable<Scope> topScopes = env.findTopScopes(lang);
                Map<Object, Object> map = scopesToObjectMap(topScopes);
                for (Entry<Object, Object> entry : map.entrySet()) {
                    if (entry.getValue() instanceof TruffleObject) {
                        TruffleObject truffleObjVal = (TruffleObject) entry.getValue();
                        boolean isExecutable = ForeignAccess.sendIsExecutable(IS_EXECUTABLE,
                                        truffleObjVal);
                        CompletionItem completion = new CompletionItem(entry.getKey().toString());
                        if (isExecutable) {
                            completion.setKind(CompletionItemKind.Function);
                            // completion.setInsertTextFormat(InsertTextFormat.Snippet);
                            // completion.setInsertText(completion.getLabel() + "($1)");
                        }
                        completions.getItems().add(completion);
                    }
                }

                return completions;
            } else {
                throw new RuntimeException("No parsed Node found for URI: " + uri);
            }
        } finally {
            this.getContext().leave();
        }
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

    private static Range sourceSectionToRange(SourceSection section) {
        return new Range(
                        new Position(section.getStartLine() - 1, section.getStartColumn() - 1),
                        new Position(section.getEndLine() - 1, section.getEndColumn() /* -1 */));
    }

    private static Range sourceSectionToRange(com.oracle.truffle.api.source.SourceSection section) {
        return new Range(
                        new Position(section.getStartLine() - 1, section.getStartColumn() - 1),
                        new Position(section.getEndLine() - 1, section.getEndColumn() /* -1 */));
    }

    private static Pair<Node, Node> findNodeAt(List<Node> loadedNodes, int line, int character) {
        Node bestMatchedNode = null;
        Node nodeBeforeCaret = null;
        for (Node node : loadedNodes) {
            com.oracle.truffle.api.source.SourceSection sourceSection = node.getSourceSection();
            if (sourceSection == null || !sourceSection.isAvailable()) {
                continue;
            }
            if (sourceSection.getStartLine() == line && line == sourceSection.getEndLine() && sourceSection.getStartColumn() <= character && character <= sourceSection.getEndColumn()) {
                if (bestMatchedNode == null || sourceSection.getCharLength() < bestMatchedNode.getSourceSection().getCharLength()) {
                    bestMatchedNode = node;
                }
            }
            if (sourceSection.getEndLine() <= line) {
                if (nodeBeforeCaret == null ||
                                nodeBeforeCaret.getSourceSection().getEndLine() < sourceSection.getEndLine() ||
                                (nodeBeforeCaret.getSourceSection().getEndLine() == sourceSection.getEndLine() && nodeBeforeCaret.getSourceSection().getEndColumn() < sourceSection.getEndColumn())) {
                    nodeBeforeCaret = node;
                }
            }
        }
        return new Pair<>(bestMatchedNode, nodeBeforeCaret);
    }

    private static CharSequence findTokenAt(RootNode rootNode, int line, int character) {
        com.oracle.truffle.api.source.SourceSection[] bestMatchedSection = {null};
        rootNode.accept(new NodeVisitor() {

            public boolean visit(Node node) {
                com.oracle.truffle.api.source.SourceSection sourceSection = node.getEncapsulatingSourceSection();
                if (sourceSection.getStartLine() == line && line == sourceSection.getEndLine() && sourceSection.getStartColumn() <= character && character <= sourceSection.getEndColumn()) {
                    if (bestMatchedSection[0] == null || sourceSection.getCharLength() < bestMatchedSection[0].getCharLength()) {
                        bestMatchedSection[0] = sourceSection;
                    }
                }
                return true;
            }
        });

        return bestMatchedSection[0] == null ? null : bestMatchedSection[0].getCharacters();
    }
}
