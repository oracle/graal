package de.hpi.swa.trufflelsp;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.services.LanguageClient;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Instrument;
import org.graalvm.polyglot.Language;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.SourceSection;
import org.truffleruby.language.LazyRubyRootNode;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.TruffleException;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter.SourcePredicate;
import com.oracle.truffle.api.instrumentation.TruffleInstrument.Env;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeVisitor;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.sl.SLLanguage;

public class TruffleAdapter {
    public static final String SOURCE_SECTION_ID = "TruffleLSPTestSection";
    public static final String SOURCE_SECTION_ID_RUBY = "TruffleLSPTestSectionRuby";
    public static final String SOURCE_SECTION_DUMMY = "TruffleLSPDummySection";
    public static final URI RUBY_DUMMY_URI = URI.create("truffle:rubytestABC.rb");

    private LanguageClient client;
    private Workspace workspace;

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

                    if (callTarget instanceof RootCallTarget) {
                        // TODO(ds)
                    }

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
                    parseExample(globalsInstrument.getEnv());
//
// CallTarget callTarget = globalsInstrument.getEnv().parse(
// com.oracle.truffle.api.source.Source.newBuilder(text).uri(RUBY_DUMMY_URI).name(SOURCE_SECTION_ID_RUBY).mimeType("application/x-ruby").build());
//// com.oracle.truffle.api.source.Source.newBuilder(new File(URI.create(documentUri))).build());
// System.out.println(globalsInstrument.uris);
// if (callTarget instanceof RootCallTarget) {
// // TODO(ds)
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
                Context context = Context.create(lang);
                Instrument instrument = context.getEngine().getInstruments().get(EnvironmentProvider.ID);
                EnvironmentProvider envProvider = instrument.lookup(EnvironmentProvider.class);
                Env env = envProvider.getEnv();

                context.enter();
                env.parse(com.oracle.truffle.api.source.Source.newBuilder(text).name(documentUri).mimeType(mimeType).build());

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
                range = new Range(
                                new Position(ss.getStartLine() - 1, ss.getStartColumn() - 1),
                                new Position(ss.getEndLine() - 1, ss.getEndColumn() /* -1 */));
            }
            diagnostics.add(new Diagnostic(range, e.getMessage(), DiagnosticSeverity.Error, "Polyglot"));
        } catch (RuntimeException e) {
            if (e instanceof TruffleException) {
                TruffleException te = (TruffleException) e;
                Range range = new Range(new Position(), new Position());

                if (te.getLocation() != null && te.getLocation().getEncapsulatingSourceSection().isAvailable()) {
                    com.oracle.truffle.api.source.SourceSection ss = te.getLocation().getEncapsulatingSourceSection();
                    range = new Range(
                                    new Position(ss.getStartLine() - 1, ss.getStartColumn() - 1),
                                    new Position(ss.getEndLine() - 1, ss.getEndColumn() /* -1 */));
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

}
