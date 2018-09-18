package de.hpi.swa.trufflelsp.server.request;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.Range;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.TruffleException;
import com.oracle.truffle.api.instrumentation.EventBinding;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.ExecutionEventNode;
import com.oracle.truffle.api.instrumentation.ExecutionEventNodeFactory;
import com.oracle.truffle.api.instrumentation.LoadSourceSectionEvent;
import com.oracle.truffle.api.instrumentation.LoadSourceSectionListener;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.StandardTags.StatementTag;
import com.oracle.truffle.api.instrumentation.TruffleInstrument.Env;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.SourceSection;

import de.hpi.swa.trufflelsp.api.ContextAwareExecutorWrapper;
import de.hpi.swa.trufflelsp.exceptions.DiagnosticsNotification;
import de.hpi.swa.trufflelsp.exceptions.InvalidCoverageScriptURI;
import de.hpi.swa.trufflelsp.server.utils.CoverageEventNode;
import de.hpi.swa.trufflelsp.server.utils.RunScriptUtils;
import de.hpi.swa.trufflelsp.server.utils.SourceLocation;
import de.hpi.swa.trufflelsp.server.utils.SourceUtils;
import de.hpi.swa.trufflelsp.server.utils.TextDocumentSurrogate;

public class CoverageRequestHandler extends AbstractRequestHandler {

    private final SourceCodeEvaluator sourceCodeEvaluator;

    public CoverageRequestHandler(Env env, Map<URI, TextDocumentSurrogate> uri2TextDocumentSurrogate, ContextAwareExecutorWrapper contextAwareExecutor, SourceCodeEvaluator sourceCodeEvaluator) {
        super(env, uri2TextDocumentSurrogate, contextAwareExecutor);
        this.sourceCodeEvaluator = sourceCodeEvaluator;
    }

    public Boolean runCoverageAnalysisWithNestedEnteredContext(final URI uri) throws DiagnosticsNotification {
        final TextDocumentSurrogate surrogateOfOpendFile = uri2TextDocumentSurrogate.get(uri);
        URI tempRunScriptUri;
        try {
            tempRunScriptUri = RunScriptUtils.extractScriptPath(surrogateOfOpendFile);
        } catch (InvalidCoverageScriptURI e) {
            throw DiagnosticsNotification.create(uri,
                            new Diagnostic(new Range(new Position(0, e.getIndex()), new Position(0, e.getLength())), e.getReason(), DiagnosticSeverity.Error, "Coverage analysis"));
        }

        if (tempRunScriptUri == null) {
// throw DiagnosticsNotification.create(uri, new Diagnostic(new Range(new Position(), new
// Position()), "No RUN_SCRIPT_PATH:<path> found anywhere in first line.", DiagnosticSeverity.Error,
// "Coverage analysis"));
// return;
            tempRunScriptUri = uri;
        }

        final URI runScriptUri = tempRunScriptUri;

        // Clean-up TODO(ds) how to do this without dropping everything? If we have different tests,
        // the coverage run of one test will remove any coverage info provided from the other tests.
        uri2TextDocumentSurrogate.entrySet().stream().forEach(entry -> entry.getValue().clearCoverage());

        try {
            // TODO(ds) can we always assume the same language for the source and its test?
            TextDocumentSurrogate surrogateOfTestFile = uri2TextDocumentSurrogate.computeIfAbsent(runScriptUri,
                            (_uri) -> new TextDocumentSurrogate(_uri, surrogateOfOpendFile.getLangId(), env.getCompletionTriggerCharacters(surrogateOfOpendFile.getLangId())));

            final CallTarget callTarget = sourceCodeEvaluator.parse(surrogateOfTestFile);
            final String langId = surrogateOfTestFile.getLangId();
            EventBinding<ExecutionEventNodeFactory> eventFactoryBinding = env.getInstrumenter().attachExecutionEventFactory(
// SourceSectionFilter.newBuilder().tagIs(StatementTag.class, ExpressionTag.class).build(),
// SourceSectionFilter.ANY,
                            SourceSectionFilter.newBuilder().sourceIs(s -> langId.equals(s.getLanguage())).build(),
                            new ExecutionEventNodeFactory() {

                                public ExecutionEventNode create(final EventContext eventContext) {
                                    final SourceSection section = eventContext.getInstrumentedSourceSection();
                                    if (section != null && section.isAvailable()) {
                                        final Node instrumentedNode = eventContext.getInstrumentedNode();
                                        Function<URI, TextDocumentSurrogate> func = (sourceUri) -> uri2TextDocumentSurrogate.computeIfAbsent(
                                                        sourceUri,
                                                        (_uri) -> new TextDocumentSurrogate(_uri, instrumentedNode.getRootNode().getLanguageInfo().getId(),
                                                                        env.getCompletionTriggerCharacters(instrumentedNode.getRootNode().getLanguageInfo().getId())));

                                        return new CoverageEventNode(eventContext.getInstrumentedSourceSection(), runScriptUri, func);
                                    } else {
                                        return new ExecutionEventNode() {
                                        };
                                    }
                                }
                            });
            try {
                callTarget.call();
            } finally {
                eventFactoryBinding.dispose();
            }

            surrogateOfOpendFile.setCoverageAnalysisDone(true);

            return Boolean.TRUE;
        } catch (DiagnosticsNotification e) {
            throw e;
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
                throw DiagnosticsNotification.create(uriOfErronousSource,
                                new Diagnostic(SourceUtils.getRangeFrom((TruffleException) e), e.getMessage(), DiagnosticSeverity.Error, "Coverage analysis"));
            }

            throw e;
        }
    }

    public void showCoverageWithEnteredContext(URI uri) throws DiagnosticsNotification {
        final TextDocumentSurrogate surrogate = uri2TextDocumentSurrogate.get(uri);
        if (surrogate.getSourceWrapper() != null) {
            // @formatter:off
            SourceSectionFilter filter = SourceSectionFilter.newBuilder()
                            .sourceIs(surrogate.getSourceWrapper().getSource())
                            .tagIs(StatementTag.class)
                            .build();
            Set<SourceSection> duplicateFilter = new HashSet<>();
            Map<URI, PublishDiagnosticsParams> mapDiagnostics = new HashMap<>();
            env.getInstrumenter().attachLoadSourceSectionListener(filter, new LoadSourceSectionListener() {

                public void onLoad(LoadSourceSectionEvent event) {
                    SourceSection section = event.getSourceSection();
                    if (!surrogate.isLocationCovered(SourceLocation.from(section)) && !duplicateFilter.contains(section)) {
                        duplicateFilter.add(section);
                        Diagnostic diag = new Diagnostic(SourceUtils.sourceSectionToRange(section),
                                                         "Not covered",
                                                         DiagnosticSeverity.Warning,
                                                         "Coverage Analysis");
                        PublishDiagnosticsParams params = mapDiagnostics.computeIfAbsent(uri, _uri -> new PublishDiagnosticsParams(_uri.toString(), new ArrayList<>()));
                        params.getDiagnostics().add(diag);
                    }
                }
            }, true).dispose();
            throw new DiagnosticsNotification(mapDiagnostics.values());
            // @formatter:on
        } else {
            throw DiagnosticsNotification.create(uri,
                            new Diagnostic(new Range(new Position(), new Position()), "No coverage information available", DiagnosticSeverity.Error, "Coverage Analysis"));
        }
    }
}
