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
package org.graalvm.tools.lsp.server.request;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.regex.Matcher;

import org.graalvm.tools.lsp.exceptions.DiagnosticsNotification;
import org.graalvm.tools.lsp.exceptions.EvaluationResultException;
import org.graalvm.tools.lsp.exceptions.UnknownLanguageException;
import org.graalvm.tools.lsp.server.ContextAwareExecutor;
import org.graalvm.tools.lsp.server.types.Diagnostic;
import org.graalvm.tools.lsp.server.types.DiagnosticSeverity;
import org.graalvm.tools.lsp.server.utils.CoverageData;
import org.graalvm.tools.lsp.server.utils.CoverageEventNode;
import org.graalvm.tools.lsp.server.utils.EvaluationResult;
import org.graalvm.tools.lsp.server.utils.InteropUtils;
import org.graalvm.tools.lsp.server.utils.SourcePredicateBuilder;
import org.graalvm.tools.lsp.server.utils.SourceUtils;
import org.graalvm.tools.lsp.server.utils.SourceWrapper;
import org.graalvm.tools.lsp.server.utils.TextDocumentSurrogate;
import org.graalvm.tools.lsp.server.utils.TextDocumentSurrogateMap;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Scope;
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
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.ExecutableNode;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeVisitor;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

public final class SourceCodeEvaluator extends AbstractRequestHandler {

    private static final InteropLibrary INTEROP = InteropLibrary.getFactory().getUncached();

    public SourceCodeEvaluator(TruffleInstrument.Env envMain, TruffleInstrument.Env env, TextDocumentSurrogateMap surrogateMap, ContextAwareExecutor executor) {
        super(envMain, env, surrogateMap, executor);
    }

    public CallTarget parse(final TextDocumentSurrogate surrogate) throws DiagnosticsNotification {
        if (!env.getLanguages().containsKey(surrogate.getLanguageId())) {
            throw new UnknownLanguageException("Unknown language: " + surrogate.getLanguageId() + ". Known languages are: " + env.getLanguages().keySet());
        }

        SourceWrapper sourceWrapper = surrogate.prepareParsing();
        CallTarget callTarget = null;
        try {
            logger.log(Level.FINE, "Parsing {0} {1}", new Object[]{surrogate.getLanguageId(), surrogate.getUri()});
            callTarget = env.parse(sourceWrapper.getSource());
            logger.log(Level.FINER, "Parsing done.");
        } catch (Exception e) {
            if (e instanceof TruffleException) {
                throw DiagnosticsNotification.create(surrogate.getUri(),
                                Diagnostic.create(SourceUtils.getRangeFrom((TruffleException) e), e.getMessage(), DiagnosticSeverity.Error, null, "Graal", null));
            } else {
                // TODO(ds) throw an Exception which the LSPServer can catch to send a client
                // notification
                throw new RuntimeException(e);
            }
        } finally {
            surrogate.notifyParsingDone(callTarget);
        }

        return callTarget;
    }

    public EvaluationResult tryDifferentEvalStrategies(TextDocumentSurrogate surrogate, Node nearestNode) throws DiagnosticsNotification {
        logger.fine("Trying literal eval...");
        EvaluationResult literalResult = evalLiteral(nearestNode);
        if (literalResult.isEvaluationDone() && !literalResult.isError()) {
            return literalResult;
        }

        EvaluationResult coverageEvalResult = evalWithCoverageData(surrogate, nearestNode);
        if (coverageEvalResult.isEvaluationDone() && !coverageEvalResult.isError()) {
            return coverageEvalResult;
        }

        logger.fine("Trying run-to-section eval...");
        EvaluationResult runToSectionEvalResult = runToSectionAndEval(surrogate, nearestNode);
        if (runToSectionEvalResult.isEvaluationDone()) {
            return runToSectionEvalResult;
        }

        logger.fine("Trying global eval...");
        EvaluationResult globalScopeEvalResult = evalInGlobalScope(surrogate.getLanguageId(), nearestNode);
        if (globalScopeEvalResult.isError()) {
            return EvaluationResult.createEvaluationSectionNotReached();
        }
        return globalScopeEvalResult;
    }

    private EvaluationResult evalLiteral(Node nearestNode) {
        Object nodeObject = ((InstrumentableNode) nearestNode).getNodeObject();
        if (nodeObject instanceof TruffleObject) {
            try {
                if (INTEROP.isMemberReadable(nodeObject, "literal")) {
                    Object result = INTEROP.readMember(nodeObject, "literal");
                    assert result instanceof TruffleObject || InteropUtils.isPrimitive(result);
                    return EvaluationResult.createResult(result);
                }
            } catch (UnknownIdentifierException | UnsupportedMessageException e) {
                logger.warning(e.getMessage());
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

        CoverageData coverageData = dataBeforeNode.get(dataBeforeNode.size() - 1);
        if (((InstrumentableNode) nearestNode).hasTag(StandardTags.ReadVariableTag.class)) {
            // Shortcut for variables
            InteropUtils.VariableInfo[] variables = InteropUtils.getNodeObjectVariables((InstrumentableNode) nearestNode);
            if (variables.length == 1) {
                InteropUtils.VariableInfo var = variables[0];
                for (Scope scope : env.findLocalScopes(nearestNode, coverageData.getFrame())) {
                    if (INTEROP.isMemberReadable(scope.getVariables(), var.getName())) {
                        logger.fine("Coverage-based variable look-up");
                        try {
                            Object value = INTEROP.readMember(scope.getVariables(), var.getName());
                            return EvaluationResult.createResult(value);
                        } catch (UnknownIdentifierException | UnsupportedMessageException ex) {
                            throw new AssertionError("Unexpected interop exception", ex);
                        }
                    }
                }
            }
        }

        LanguageInfo info = nearestNode.getRootNode().getLanguageInfo();
        String code = nearestNode.getSourceSection().getCharacters().toString();
        Source inlineEvalSource = Source.newBuilder(info.getId(), code, "in-line eval (hover request)").cached(false).build();
        ExecutableNode executableNode = env.parseInline(inlineEvalSource, nearestNode, coverageData.getFrame());

        CoverageEventNode coverageEventNode = coverageData.getCoverageEventNode();
        coverageEventNode.insertOrReplaceChild(executableNode);

        try {
            logger.fine("Trying coverage-based eval...");
            Object result = executableNode.execute(coverageData.getFrame());
            return EvaluationResult.createResult(result);
        } catch (Exception e) {
            e.printStackTrace();
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

                            @Override
                            public ExecutionEventNode create(EventContext context) {
                                return new ExecutionEventNode() {

                                    private String sourceSectionFormat(SourceSection section) {
                                        return "SourceSection(" + section.getCharacters().toString().replaceAll("\n", Matcher.quoteReplacement("\\n")) + ")";
                                    }

                                    @Override
                                    public void onReturnValue(VirtualFrame frame, Object result) {
                                        if (logger.isLoggable(Level.FINEST)) {
                                            logOnReturnValue(result);
                                        }

                                        if (!isInputFilterDefined) {
                                            CompilerDirectives.transferToInterpreter();
                                            throw new EvaluationResultException(result);
                                        }
                                    }

                                    @TruffleBoundary
                                    private void logOnReturnValue(Object result) {
                                        if (indent.length() > 1) {
                                            indent.setLength(indent.length() - 2);
                                        }
                                        logger.log(Level.FINEST, "{0}onReturnValue {1} {2} {3} {4}", new Object[]{indent, context.getInstrumentedNode().getClass().getSimpleName(),
                                                        sourceSectionFormat(context.getInstrumentedSourceSection()), result});
                                    }

                                    @Override
                                    public void onReturnExceptional(VirtualFrame frame, Throwable exception) {
                                        if (logger.isLoggable(Level.FINEST)) {
                                            logOnReturnExceptional();
                                        }
                                    }

                                    @TruffleBoundary
                                    private void logOnReturnExceptional() {
                                        indent.setLength(indent.length() - 2);
                                        logger.log(Level.FINEST, "{0}onReturnExceptional {1}", new Object[]{indent, sourceSectionFormat(context.getInstrumentedSourceSection())});
                                    }

                                    @Override
                                    public void onEnter(VirtualFrame frame) {
                                        if (logger.isLoggable(Level.FINEST)) {
                                            logOnEnter();
                                        }
                                    }

                                    @TruffleBoundary
                                    private void logOnEnter() {
                                        logger.log(Level.FINEST, "{0}onEnter {1} {2}", new Object[]{indent, context.getInstrumentedNode().getClass().getSimpleName(),
                                                        sourceSectionFormat(context.getInstrumentedSourceSection())});
                                        indent.append("  ");
                                    }

                                    @Override
                                    public void onInputValue(VirtualFrame frame, EventContext inputContext, int inputIndex, Object inputValue) {
                                        if (logger.isLoggable(Level.FINEST)) {
                                            logOnInputValue(inputContext, inputIndex, inputValue);
                                        }
                                        CompilerDirectives.transferToInterpreter();
                                        throw new EvaluationResultException(inputValue);
                                    }

                                    @TruffleBoundary
                                    private void logOnInputValue(EventContext inputContext, int inputIndex, Object inputValue) {
                                        indent.setLength(indent.length() - 2);
                                        Object view = env.getLanguageView(inputContext.getInstrumentedNode().getRootNode().getLanguageInfo(), inputValue);
                                        String metaObject;
                                        try {
                                            metaObject = INTEROP.hasMetaObject(view) ? INTEROP.asString(INTEROP.toDisplayString(INTEROP.getMetaObject(view))) : null;
                                        } catch (UnsupportedMessageException e) {
                                            CompilerDirectives.transferToInterpreter();
                                            throw new AssertionError(e);
                                        }
                                        logger.log(Level.FINEST, "{0}onInputValue idx:{1} {2} {3} {4} {5} {6}",
                                                        new Object[]{indent, inputIndex, inputContext.getInstrumentedNode().getClass().getSimpleName(),
                                                                        sourceSectionFormat(context.getInstrumentedSourceSection()),
                                                                        sourceSectionFormat(inputContext.getInstrumentedSourceSection()), inputValue,
                                                                        metaObject});
                                        indent.append("  ");
                                    }
                                };
                            }

                        });

        try {
            callTarget.call();
        } catch (EvaluationResultException e) {
            return e.isError() ? EvaluationResult.createError(e.getResult()) : EvaluationResult.createResult(e.getResult());
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

    public TextDocumentSurrogate createSurrogateForTestFile(TextDocumentSurrogate surrogateOfOpenedFile, URI runScriptUriFallback) {
        URI runScriptUri = runScriptUriFallback != null ? runScriptUriFallback : surrogateOfOpenedFile.getUri();
        final String langIdOfTestFile = findLanguageOfTestFile(runScriptUri, surrogateOfOpenedFile.getLanguageId());
        LanguageInfo languageInfo = env.getLanguages().get(langIdOfTestFile);
        assert languageInfo != null;
        TextDocumentSurrogate surrogateOfTestFile = surrogateMap.getOrCreateSurrogate(runScriptUri, languageInfo);
        return surrogateOfTestFile;

    }

    private static String findLanguageOfTestFile(URI runScriptUri, String fallbackLangId) {
        try {
            return Source.findLanguage(runScriptUri.toURL());
        } catch (IOException e) {
            return fallbackLangId;
        }
    }

    private EvaluationResult evalInGlobalScope(String langId, Node nearestNode) {
        SourceSection section = nearestNode.getSourceSection();
        if (section == null || !section.isAvailable()) {
            return EvaluationResult.createUnknownExecutionTarget();
        }

        try {
            CallTarget callTarget = env.parse(
                            Source.newBuilder(langId, section.getCharacters(), "eval in global scope").cached(false).build());
            Object result = callTarget.call();
            return EvaluationResult.createResult(result);
        } catch (Exception e) {
            return EvaluationResult.createError(e);
        }
    }

    /**
     * A special method to create a {@link SourceSectionFilter} which filters for a specific source
     * section during source code evaluation. We cannot simply filter with
     * {@link Builder#sourceIs(Source...)} and {@link Builder#sourceSectionEquals(SourceSection...)}
     * , because we are possibly not the creator of the Source and do not know which properties are
     * set. The source which is evaluated could have been created by the language. For example by a
     * Python import statement. Therefore we need to filter via URI (or name if the URI is a
     * generated truffle-schema-URI).
     *
     * @param uri to filter sources for
     * @param sourceSection to filter for with same start and end indices
     * @return a builder to add further filter options
     */
    static SourceSectionFilter.Builder createSourceSectionFilter(URI uri, SourceSection sourceSection) {
        return SourceSectionFilter.newBuilder() //
                        .lineStartsIn(IndexRange.between(sourceSection.getStartLine(), sourceSection.getStartLine() + 1)) //
                        .lineEndsIn(IndexRange.between(sourceSection.getEndLine(), sourceSection.getEndLine() + 1)) //
                        .columnStartsIn(IndexRange.between(sourceSection.getStartColumn(), sourceSection.getStartColumn() + 1)) //
                        .columnEndsIn(IndexRange.between(sourceSection.getEndColumn(), sourceSection.getEndColumn() + 1)) //
                        .sourceIs(SourcePredicateBuilder.newBuilder().uriOrTruffleName(uri).build());
    }

    static List<CoverageData> findCoverageDataBeforeNode(TextDocumentSurrogate surrogate, Node targetNode) {
        List<CoverageData> coveragesBeforeNode = new ArrayList<>();
        targetNode.getRootNode().accept(new NodeVisitor() {
            boolean found = false;

            @Override
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
