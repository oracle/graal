package de.hpi.swa.trufflelsp.server.request;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.TruffleException;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.EventBinding;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.ExecutionEventNode;
import com.oracle.truffle.api.instrumentation.ExecutionEventNodeFactory;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter.Builder;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter.IndexRange;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.KeyInfo;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.ExecutableNode;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeVisitor;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

import de.hpi.swa.trufflelsp.api.ContextAwareExecutorWrapper;
import de.hpi.swa.trufflelsp.exceptions.DiagnosticsNotification;
import de.hpi.swa.trufflelsp.exceptions.EvaluationResultException;
import de.hpi.swa.trufflelsp.exceptions.InlineParsingNotSupportedException;
import de.hpi.swa.trufflelsp.exceptions.InvalidCoverageScriptURI;
import de.hpi.swa.trufflelsp.exceptions.UnknownLanguageException;
import de.hpi.swa.trufflelsp.server.utils.CoverageData;
import de.hpi.swa.trufflelsp.server.utils.CoverageEventNode;
import de.hpi.swa.trufflelsp.server.utils.EvaluationResult;
import de.hpi.swa.trufflelsp.server.utils.InteropUtils;
import de.hpi.swa.trufflelsp.server.utils.RunScriptUtils;
import de.hpi.swa.trufflelsp.server.utils.SourcePredicateBuilder;
import de.hpi.swa.trufflelsp.server.utils.SourceUtils;
import de.hpi.swa.trufflelsp.server.utils.SourceWrapper;
import de.hpi.swa.trufflelsp.server.utils.SurrogateMap;
import de.hpi.swa.trufflelsp.server.utils.TextDocumentSurrogate;

public class SourceCodeEvaluator extends AbstractRequestHandler {

    public SourceCodeEvaluator(TruffleInstrument.Env env, SurrogateMap surrogateMap, ContextAwareExecutorWrapper executor) {
        super(env, surrogateMap, executor);
    }

    public CallTarget parse(final TextDocumentSurrogate surrogate) throws DiagnosticsNotification {
        if (!env.getLanguages().containsKey(surrogate.getLangId())) {
            throw new UnknownLanguageException("Unknown language: " + surrogate.getLangId() + ". Known languages are: " + env.getLanguages().keySet());
        }

        SourceWrapper sourceWrapper = surrogate.prepareParsing();
        CallTarget callTarget = null;
        try {
            System.out.println("Parsing " + surrogate.getLangId() + " " + surrogate.getUri());
            callTarget = env.parse(sourceWrapper.getSource());
            System.out.println("Parsing done.");
            surrogate.notifyParsingSuccessful(callTarget);
        } catch (Exception e) {
            if (e instanceof TruffleException) {
                throw DiagnosticsNotification.create(surrogate.getUri(), new Diagnostic(SourceUtils.getRangeFrom((TruffleException) e), e.getMessage(), DiagnosticSeverity.Error, "Graal"));
            } else {
                // TODO(ds) throw an Exception which the LSPServer can catch to send a client
                // notification
                throw new RuntimeException(e);
            }
        }

        return callTarget;
    }

    public EvaluationResult tryDifferentEvalStrategies(TextDocumentSurrogate surrogate, Node nearestNode) throws DiagnosticsNotification {
        System.out.println("Trying literal eval...");
        EvaluationResult literalResult = evalLiteral(nearestNode);
        if (literalResult.isEvaluationDone() && !literalResult.isError()) {
            return literalResult;
        }

        EvaluationResult coverageEvalResult = evalWithCoverageData(surrogate, nearestNode);
        if (coverageEvalResult.isEvaluationDone() && !coverageEvalResult.isError()) {
            return coverageEvalResult;
        }

        System.out.println("Trying run-to-section eval...");
        EvaluationResult runToSectionEvalResult = runToSectionAndEval(surrogate, nearestNode);
        if (runToSectionEvalResult.isEvaluationDone()) {
            return runToSectionEvalResult;
        }

        System.out.println("Trying global eval...");
        EvaluationResult globalScopeEvalResult = evalInGlobalScope(surrogate.getLangId(), nearestNode);
        if (globalScopeEvalResult.isError()) {
            return EvaluationResult.createEvaluationSectionNotReached();
        }
        return globalScopeEvalResult;
    }

    private static EvaluationResult evalLiteral(Node nearestNode) {
        Object nodeObject = ((InstrumentableNode) nearestNode).getNodeObject();
        if (nodeObject instanceof TruffleObject) {
            try {
                if (KeyInfo.isReadable(ForeignAccess.sendKeyInfo(Message.KEY_INFO.createNode(), (TruffleObject) nodeObject, "literal"))) {
                    Object result = ForeignAccess.sendRead(Message.READ.createNode(), (TruffleObject) nodeObject, "literal");
                    if (result instanceof TruffleObject || InteropUtils.isPrimitive(result)) {
                        return EvaluationResult.createResult(result);
                    } else {
                        System.out.println("Literal is no TruffleObject or primitive: " + result.getClass());
                    }
                }
            } catch (UnknownIdentifierException | UnsupportedMessageException e) {
                e.printStackTrace();
                return EvaluationResult.createError(e);
            }
        }
        return EvaluationResult.createEvaluationSectionNotReached();
    }

    private EvaluationResult evalWithCoverageData(TextDocumentSurrogate textDocumentSurrogate, Node nearestNode) {
        if (!textDocumentSurrogate.hasCoverageData()) {
            return EvaluationResult.createEvaluationSectionNotReached();
        }

        List<CoverageData> dataBeforeNode = findCoverageDataBeforeNode(textDocumentSurrogate, nearestNode);
        if (dataBeforeNode == null || dataBeforeNode.isEmpty()) {
            return EvaluationResult.createEvaluationSectionNotReached();
        }

        LanguageInfo info = nearestNode.getRootNode().getLanguageInfo();
        String code = nearestNode.getSourceSection().getCharacters().toString();
        Source inlineEvalSource = Source.newBuilder(info.getId(), code, "in-line eval (hover request)").cached(false).build();
        CoverageData coverageData = dataBeforeNode.get(dataBeforeNode.size() - 1);
        ExecutableNode executableNode = env.parseInline(inlineEvalSource, nearestNode, coverageData.getFrame());

        CoverageEventNode coverageEventNode = coverageData.getCoverageEventNode();
        coverageEventNode.insertOrReplaceChild(executableNode);

        try {
            System.out.println("Trying coverage-based eval...");
            Object result = executableNode.execute(coverageData.getFrame());
            return EvaluationResult.createResult(result);
        } catch (Exception e) {
        } finally {
            coverageEventNode.clearChild();
        }
        return EvaluationResult.createEvaluationSectionNotReached();
    }

    public EvaluationResult runToSectionAndEval(final TextDocumentSurrogate surrogate, final Node nearestNode) throws DiagnosticsNotification {
        if (!(nearestNode instanceof InstrumentableNode) || !((InstrumentableNode) nearestNode).isInstrumentable()) {
            return EvaluationResult.createEvaluationSectionNotReached();
        }

        SourceSectionFilter eventFilter = createSourceSectionFilter(surrogate.getUri(), nearestNode.getSourceSection()).build();
        return runToSectionAndEval(surrogate, nearestNode.getSourceSection(), eventFilter, null);
    }

    public EvaluationResult runToSectionAndEval(final TextDocumentSurrogate surrogate, final SourceSection sourceSection, SourceSectionFilter eventFilter, SourceSectionFilter inputFilter)
                    throws DiagnosticsNotification {
        Set<URI> coverageUris = surrogate.getCoverageUris(sourceSection);
        URI runScriptUriFallback = coverageUris == null ? null : coverageUris.stream().findFirst().orElseGet(() -> null);
        TextDocumentSurrogate surrogateOfTestFile = createSurrogateForTestFile(surrogate, runScriptUriFallback);
        final CallTarget callTarget = parse(surrogateOfTestFile);
        final boolean isInputFilterDefined = inputFilter != null;

        EventBinding<ExecutionEventNodeFactory> binding = env.getInstrumenter().attachExecutionEventFactory(
                        eventFilter,
                        inputFilter,
                        new ExecutionEventNodeFactory() {
                            StringBuilder indent = new StringBuilder("");

                            public ExecutionEventNode create(EventContext context) {
                                return new ExecutionEventNode() {

                                    private String sourceSectionFormat(SourceSection section) {
                                        return "SourceSection(" + section.getCharacters().toString().replaceAll("\n", Matcher.quoteReplacement("\\n")) + ") ";
                                    }

                                    @Override
                                    public void onReturnValue(VirtualFrame frame, Object result) {
                                        indent.setLength(indent.length() - 2);
                                        System.out.println(indent + "onReturnValue " + context.getInstrumentedNode().getClass().getSimpleName() + " " +
                                                        sourceSectionFormat(context.getInstrumentedSourceSection()) + result);

                                        if (!isInputFilterDefined) {
                                            throw new EvaluationResultException(result);
                                        }
                                    }

                                    @Override
                                    public void onReturnExceptional(VirtualFrame frame, Throwable exception) {
                                        indent.setLength(indent.length() - 2);
                                        System.out.println(indent + "onReturnExceptional " + sourceSectionFormat(context.getInstrumentedSourceSection()));
                                    }

                                    @Override
                                    public void onEnter(VirtualFrame frame) {
                                        System.out.println(indent + "onEnter " + context.getInstrumentedNode().getClass().getSimpleName() + " " +
                                                        sourceSectionFormat(context.getInstrumentedSourceSection()));
                                        indent.append("  ");
                                    }

                                    @Override
                                    public void onInputValue(VirtualFrame frame, EventContext inputContext, int inputIndex, Object inputValue) {
                                        indent.setLength(indent.length() - 2);
                                        System.out.println(indent + "onInputValue idx:" + inputIndex + " " +
                                                        inputContext.getInstrumentedNode().getClass().getSimpleName() + " " +
                                                        sourceSectionFormat(context.getInstrumentedSourceSection()) +
                                                        sourceSectionFormat(inputContext.getInstrumentedSourceSection()) +
                                                        inputValue + " " +
                                                        env.findMetaObject(inputContext.getInstrumentedNode().getRootNode().getLanguageInfo(),
                                                                        inputValue));
                                        indent.append("  ");
// if (inputContext.getInstrumentedSourceSection().equals(context.getInstrumentedSourceSection())) {
// // This is a fix for GraalJS, because
// // GraalJS provides the result of a previous execution
// // again as input value which we are not interested in
// // here. See class JSTaggedTargetableExecutionNode.
// return;
// }

                                        throw new EvaluationResultException(inputValue);
                                    }
                                };
                            }

                        });

        try {
            callTarget.call();
        } catch (EvaluationResultException e) {
            return e.isError() ? EvaluationResult.createError(e.getResult()) : EvaluationResult.createResult(e.getResult());
        } catch (InlineParsingNotSupportedException e) {
            err.println("Inline parsing not supported for language " + surrogate.getLangId());
            return EvaluationResult.createEvaluationSectionNotReached();
        } catch (RuntimeException e) {
            if (e instanceof TruffleException) {
                if (((TruffleException) e).isExit()) {
                    return EvaluationResult.createEvaluationSectionNotReached();
                } else {
                    return EvaluationResult.createError(e);
                }
            }
        } finally {
            binding.dispose();
        }
        return EvaluationResult.createEvaluationSectionNotReached();
    }

    public TextDocumentSurrogate createSurrogateForTestFile(TextDocumentSurrogate surrogateOfOpenedFile, URI runScriptUriFallback) throws DiagnosticsNotification {
        URI runScriptUri;
        try {
            runScriptUri = RunScriptUtils.extractScriptPath(surrogateOfOpenedFile);
        } catch (InvalidCoverageScriptURI e) {
            throw DiagnosticsNotification.create(surrogateOfOpenedFile.getUri(),
                            new Diagnostic(new Range(new Position(0, e.getIndex()), new Position(0, e.getLength())), e.getReason(), DiagnosticSeverity.Error,
                                            "Graal LSP"));
        }

        if (runScriptUri == null) {
            runScriptUri = runScriptUriFallback != null ? runScriptUriFallback : surrogateOfOpenedFile.getUri();
        }

        final String langIdOfTestFile = findLanguageOfTestFile(runScriptUri, surrogateOfOpenedFile.getLangId());
        LanguageInfo languageInfo = env.getLanguages().get(langIdOfTestFile);
        assert languageInfo != null;
        TextDocumentSurrogate surrogateOfTestFile = surrogateMap.getOrCreateSurrogate(runScriptUri, languageInfo);
        return surrogateOfTestFile;

    }

    private static String findLanguageOfTestFile(URI runScriptUri, String fallbackLangId) {
        try {
            return org.graalvm.polyglot.Source.findLanguage(new File(runScriptUri));
        } catch (IOException e) {
            return fallbackLangId;
        }
    }

    private EvaluationResult evalInGlobalScope(String langId, Node nearestNode) {
        try {
            CallTarget callTarget = env.parse(
                            Source.newBuilder(langId, nearestNode.getEncapsulatingSourceSection().getCharacters(), "eval in global scope").cached(false).build());
            Object result = callTarget.call();
            return EvaluationResult.createResult(result);
        } catch (Exception e) {
            return EvaluationResult.createError(e);
        }
    }

    /**
     * A special method to create a {@link SourceSectionFilter} which filters for a specific source
     * section during source code evaluation. We cannot simply filter with
     * {@link Builder#sourceIs(Source...)} and
     * {@link Builder#sourceSectionEquals(SourceSection...)}, because we are possibly not the
     * creator of the Source and do not know which properties are set. The source which is evaluated
     * could have been created by the language. For example by a Python import statement. Therefore
     * we need to filter via URI (or name if the URI is a generated truffle-schema-URI).
     *
     * @param uri to filter sources for
     * @param sourceSection to filter for with same start and end indices
     * @return a builder to add further filter options
     */
    static SourceSectionFilter.Builder createSourceSectionFilter(URI uri, SourceSection sourceSection) {
        // @formatter:off
        return SourceSectionFilter.newBuilder()
                        .lineStartsIn(IndexRange.between(sourceSection.getStartLine(), sourceSection.getStartLine() + 1))
                        .lineEndsIn(IndexRange.between(sourceSection.getEndLine(), sourceSection.getEndLine() + 1))
                        .columnStartsIn(IndexRange.between(sourceSection.getStartColumn(), sourceSection.getStartColumn() + 1))
                        .columnEndsIn(IndexRange.between(sourceSection.getEndColumn(), sourceSection.getEndColumn() + 1))
                        .sourceIs(SourcePredicateBuilder.newBuilder().uriOrTruffleName(uri).build());
        // @formatter:on
    }

    static List<CoverageData> findCoverageDataBeforeNode(TextDocumentSurrogate surrogate, Node targetNode) {
        List<CoverageData> coveragesBeforeNode = new ArrayList<>();
        targetNode.getRootNode().accept(new NodeVisitor() {
            boolean found = false;

            public boolean visit(Node node) {
                if (found) {
                    return false;
                }

                if (node.equals(targetNode)) {
                    found = true;
                    return false;
                }

                SourceSection sourceSection = node.getSourceSection();
                if (sourceSection != null && sourceSection.isAvailable()) {
                    List<CoverageData> coverageData = surrogate.getCoverageData(sourceSection);
                    if (coverageData != null) {
                        coveragesBeforeNode.addAll(coverageData);
                    }
                }

                return true;
            }
        });
        return coveragesBeforeNode;
    }
}
