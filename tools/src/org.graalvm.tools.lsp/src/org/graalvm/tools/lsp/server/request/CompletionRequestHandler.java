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

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.logging.Level;

import org.graalvm.tools.api.lsp.LSPLibrary;
import org.graalvm.tools.lsp.exceptions.DiagnosticsNotification;
import org.graalvm.tools.lsp.server.ContextAwareExecutor;
import org.graalvm.tools.lsp.server.LanguageTriggerCharacters;
import org.graalvm.tools.lsp.server.types.CompletionContext;
import org.graalvm.tools.lsp.server.types.CompletionItem;
import org.graalvm.tools.lsp.server.types.CompletionItemKind;
import org.graalvm.tools.lsp.server.types.CompletionList;
import org.graalvm.tools.lsp.server.types.CompletionTriggerKind;
import org.graalvm.tools.lsp.server.types.Diagnostic;
import org.graalvm.tools.lsp.server.types.DiagnosticSeverity;
import org.graalvm.tools.lsp.server.types.MarkupContent;
import org.graalvm.tools.lsp.server.types.MarkupKind;
import org.graalvm.tools.lsp.server.utils.CoverageData;
import org.graalvm.tools.lsp.server.utils.EvaluationResult;
import org.graalvm.tools.lsp.server.utils.NearestNode;
import org.graalvm.tools.lsp.server.utils.NearestSectionsFinder;
import org.graalvm.tools.lsp.server.utils.SourceUtils;
import org.graalvm.tools.lsp.server.utils.SourceUtils.SourceFix;
import org.graalvm.tools.lsp.server.utils.SourceWrapper;
import org.graalvm.tools.lsp.server.utils.TextDocumentSurrogate;
import org.graalvm.tools.lsp.server.utils.TextDocumentSurrogateMap;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Scope;
import com.oracle.truffle.api.TruffleException;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

public final class CompletionRequestHandler extends AbstractRequestHandler {

    private static final InteropLibrary INTEROP = InteropLibrary.getFactory().getUncached();
    private static final LSPLibrary LSP_INTEROP = LSPLibrary.getFactory().getUncached();

    private enum CompletionKind {
        UNKOWN,
        OBJECT_PROPERTY,
        GLOBALS_AND_LOCALS
    }

    public final CompletionList emptyList = CompletionList.create(Collections.emptyList(), false);

    private static final int SORTING_PRIORITY_LOCALS = 1;
    private static final int SORTING_PRIORITY_GLOBALS = 2;

    private final SourceCodeEvaluator sourceCodeEvaluator;
    private final LanguageTriggerCharacters languageCompletionTriggerCharacters;

    public CompletionRequestHandler(TruffleInstrument.Env envMain, TruffleInstrument.Env env, TextDocumentSurrogateMap surrogateMap, ContextAwareExecutor executor,
                    SourceCodeEvaluator sourceCodeEvaluator, LanguageTriggerCharacters completionTriggerCharacters) {
        super(envMain, env, surrogateMap, executor);
        this.sourceCodeEvaluator = sourceCodeEvaluator;
        this.languageCompletionTriggerCharacters = completionTriggerCharacters;
    }

    public CompletionList completionWithEnteredContext(final URI uri, int line, int column, CompletionContext completionContext) throws DiagnosticsNotification {
        logger.log(Level.FINER, "Start finding completions for {0}:{1}:{2}", new Object[]{uri, line, column});

        TextDocumentSurrogate surrogate = surrogateMap.get(uri);
        if (surrogate == null) {
            logger.info("Completion requested in an unknown document: " + uri);
            return emptyList;
        }
        Source source = surrogate.getSource();

        if (!SourceUtils.isLineValid(line, source) || !SourceUtils.isColumnValid(line, column, source)) {
            logger.fine("line or column is out of range, line=" + line + ", column=" + column);
            return emptyList;
        }

        List<String> completionTriggerCharacters = languageCompletionTriggerCharacters.getTriggerCharacters(surrogate.getLanguageId());
        CompletionKind completionKind = getCompletionKind(source, SourceUtils.zeroBasedLineToOneBasedLine(line, source), column, completionTriggerCharacters,
                        completionContext);
        if (surrogate.isSourceCodeReadyForCodeCompletion()) {
            return createCompletions(surrogate, line, column, completionKind);
        } else {
            // Try fixing the source code, parse again, then create the completions

            SourceFix sourceFix = SourceUtils.removeLastTextInsertion(surrogate, column, logger);
            if (sourceFix == null) {
                logger.fine("Unable to fix unparsable source code. No completion possible.");
                return emptyList;
            }

            TextDocumentSurrogate fixedSurrogate = surrogate.copy();
            // TODO(ds) Should we reset coverage data etc? Or adjust the SourceLocations?
            fixedSurrogate.setEditorText(sourceFix.text);
            SourceWrapper sourceWrapper = fixedSurrogate.prepareParsing();
            CallTarget callTarget = null;
            try {
                callTarget = env.parse(sourceWrapper.getSource());
            } catch (Exception e) {
                err.println("Parsing a fixed source caused an exception: " + e.getClass().getSimpleName() + " > " + e.getLocalizedMessage());
                return emptyList;
            } finally {
                fixedSurrogate.notifyParsingDone(callTarget);
            }

            // We need to replace the original surrogate with the fixed one so that when a run
            // script wants to import this fixed source, it will find the fixed surrogate via the
            // custom file system callback
            surrogateMap.put(uri, fixedSurrogate);
            try {
                return createCompletions(fixedSurrogate, line, sourceFix.characterIdx, getCompletionKind(sourceFix.removedCharacters, completionTriggerCharacters));
            } finally {
                surrogateMap.put(uri, surrogate);
            }
        }
    }

    private CompletionList createCompletions(TextDocumentSurrogate surrogate, int line, int column, CompletionKind completionKind) throws DiagnosticsNotification {
        List<CompletionItem> completions = new ArrayList<>();

        if (completionKind == CompletionKind.GLOBALS_AND_LOCALS) {
            fillCompletionsWithGlobalsAndLocals(line, surrogate, column, completions);
        } else if (completionKind == CompletionKind.OBJECT_PROPERTY) {
            fillCompletionsWithObjectProperties(surrogate, line, column, completions);
        }

        return CompletionList.create(completions, false);
    }

    private void fillCompletionsWithGlobalsAndLocals(int line, TextDocumentSurrogate surrogate, int column, List<CompletionItem> completions) {
        Node nearestNode = findNearestNode(surrogate.getSourceWrapper(), line, column);

        if (nearestNode == null) {
            // Cannot locate a valid node near the caret position, therefore provide globals only
            fillCompletionsWithGlobals(surrogate, completions);
            return;
        }

        if (!surrogate.hasCoverageData()) {
            // No coverage data, so we simply display locals without specific frame information and
            // globals
            fillCompletionsWithLocals(surrogate, nearestNode, completions, null);
            fillCompletionsWithGlobals(surrogate, completions);
            return;
        }

        // We have coverage data, so we try to derive locals from coverage data

        List<CoverageData> coverages = surrogate.getCoverageData(nearestNode.getSourceSection());
        if (coverages == null || coverages.isEmpty()) {
            coverages = SourceCodeEvaluator.findCoverageDataBeforeNode(surrogate, nearestNode);
        }

        if (coverages != null && !coverages.isEmpty()) {
            CoverageData coverageData = coverages.get(coverages.size() - 1);
            MaterializedFrame frame = coverageData.getFrame();
            fillCompletionsWithLocals(surrogate, nearestNode, completions, frame);

            // Call again, regardless if it was called with a frame argument before,
            // because duplicates will be filter, but it will add missing local
            // variables which were dropped (because of null values) in the call above
            fillCompletionsWithLocals(surrogate, nearestNode, completions, null);
            fillCompletionsWithGlobals(surrogate, completions);
        } else {
            // No coverage data found for the designated source section, so use the default look-up
            // as fallback
            fillCompletionsWithGlobals(surrogate, completions);
            fillCompletionsWithLocals(surrogate, nearestNode, completions, null);
        }
    }

    private Node findNearestNode(SourceWrapper sourceWrapper, int line, int column) {
        NearestNode nearestNodeHolder = NearestSectionsFinder.findNearestNode(sourceWrapper.getSource(), line, column, env, logger);
        return nearestNodeHolder.getNode();
    }

    private void fillCompletionsWithObjectProperties(TextDocumentSurrogate surrogate, int line, int column, List<CompletionItem> completions) throws DiagnosticsNotification {
        SourceWrapper sourceWrapper = surrogate.getSourceWrapper();
        Source source = sourceWrapper.getSource();
        NearestNode nearestNodeHolder = NearestSectionsFinder.findExprNodeBeforePos(source, line, column, env);
        Node nearestNode = nearestNodeHolder.getNode();

        if (nearestNode != null) {
            Future<EvaluationResult> future = contextAwareExecutor.executeWithNestedContext(() -> sourceCodeEvaluator.tryDifferentEvalStrategies(surrogate, nearestNode), true);
            EvaluationResult evalResult = getFutureResultOrHandleExceptions(future);
            if (evalResult != null && evalResult.isEvaluationDone()) {
                if (!evalResult.isError()) {
                    fillCompletionsFromTruffleObject(completions, surrogate.getLanguageInfo(), evalResult.getResult());
                } else {
                    if (evalResult.getResult() instanceof TruffleException) {
                        TruffleException te = (TruffleException) evalResult.getResult();
                        throw DiagnosticsNotification.create(surrogate.getUri(),
                                        Diagnostic.create(SourceUtils.sourceSectionToRange(te.getSourceLocation()), "An error occurred during execution: " + te.toString(),
                                                        DiagnosticSeverity.Warning, null, "Graal", null));
                    } else {
                        ((Exception) evalResult.getResult()).printStackTrace(err);
                    }
                }
            } else {
                throw DiagnosticsNotification.create(surrogate.getUri(),
                                Diagnostic.create(SourceUtils.sourceSectionToRange(nearestNode.getSourceSection()), "No type information available for this source section.",
                                                DiagnosticSeverity.Information, null, "Graal", null));
            }
        } else {
            logger.fine("No object property completion possible. Caret is not directly at the end of a source section. Line: " + line + ", column: " + column);
        }
    }

    private static boolean isObjectPropertyCompletionCharacter(String text, List<String> completionTriggerCharacters) {
        return completionTriggerCharacters.contains(text);
    }

    private static CompletionKind getCompletionKind(String text, List<String> completionTriggerCharacters) {
        return isObjectPropertyCompletionCharacter(text, completionTriggerCharacters) ? CompletionKind.OBJECT_PROPERTY : CompletionKind.GLOBALS_AND_LOCALS;
    }

    public static CompletionKind getCompletionKind(Source source, int oneBasedLineNumber, int column, List<String> completionTriggerCharacters, CompletionContext completionContext) {
        if (completionContext != null && completionContext.getTriggerKind() == CompletionTriggerKind.TriggerCharacter && completionContext.getTriggerCharacter() != null) {
            if (isObjectPropertyCompletionCharacter(completionContext.getTriggerCharacter(), completionTriggerCharacters)) {
                return CompletionKind.OBJECT_PROPERTY;
            }

            // Completion was triggered by a character, which is a completion trigger character of
            // another language. Therefore we have to skip the current completion request.
            return CompletionKind.UNKOWN;
        }

        int lineStartOffset = source.getLineStartOffset(oneBasedLineNumber);
        if (lineStartOffset + column == 0) {
            return CompletionKind.GLOBALS_AND_LOCALS;
        }

        String text = source.getCharacters().toString();
        char charAtOffset = text.charAt(lineStartOffset + column - 1);
        return getCompletionKind(String.valueOf(charAtOffset), completionTriggerCharacters);
    }

    private void fillCompletionsWithLocals(final TextDocumentSurrogate surrogate, Node nearestNode, List<CompletionItem> completions, MaterializedFrame frame) {
        fillCompletionsWithScopesValues(surrogate, completions, env.findLocalScopes(nearestNode, frame), CompletionItemKind.Variable, SORTING_PRIORITY_LOCALS);
    }

    private void fillCompletionsWithGlobals(final TextDocumentSurrogate surrogate, List<CompletionItem> completions) {
        fillCompletionsWithScopesValues(surrogate, completions, env.findTopScopes(surrogate.getLanguageId()), null, SORTING_PRIORITY_GLOBALS);
    }

    private void fillCompletionsWithScopesValues(TextDocumentSurrogate surrogate, List<CompletionItem> completions, Iterable<Scope> scopes,
                    CompletionItemKind completionItemKindDefault, int displayPriority) {
        LanguageInfo langInfo = surrogate.getLanguageInfo();
        String[] existingCompletions = completions.stream().map((item) -> item.getLabel()).toArray(String[]::new);
        // Filter duplicates
        Set<String> completionKeys = new HashSet<>(Arrays.asList(existingCompletions));
        int scopeCounter = 0;
        for (Scope scope : scopes) {
            ++scopeCounter;
            Object variables = scope.getVariables();
            Object keys;
            long size;
            try {
                keys = INTEROP.getMembers(variables, false);
                boolean hasSize = INTEROP.hasArrayElements(keys);
                if (!hasSize) {
                    continue;
                }
                size = INTEROP.getArraySize(keys);
            } catch (Exception ex) {
                logger.log(Level.INFO, ex.getLocalizedMessage(), ex);
                continue;
            }
            for (long i = 0; i < size; i++) {
                String key;
                Object object;
                try {
                    key = INTEROP.readArrayElement(keys, i).toString();
                    if (completionKeys.contains(key)) {
                        // Scopes are provided from inner to outer, so we need to detect duplicate
                        // keys and only take those from the most inner scope
                        continue;
                    } else {
                        completionKeys.add(key);
                    }
                    object = INTEROP.readMember(variables, key);
                } catch (ThreadDeath td) {
                    throw td;
                } catch (Throwable t) {
                    logger.log(Level.CONFIG, variables.toString(), t);
                    continue;
                }
                CompletionItem completion = CompletionItem.create(key);
                // Inner scopes should be displayed first, so sort by priority and scopeCounter
                // (the innermost scope has the lowest counter)
                completion.setSortText(String.format("%s%d.%04d.%s", "+", displayPriority, scopeCounter, key));
                if (completionItemKindDefault != null) {
                    completion.setKind(completionItemKindDefault);
                } else {
                    completion.setKind(findCompletionItemKind(object));
                }
                completion.setDetail(createCompletionDetail(object, langInfo));
                completion.setDocumentation(createDocumentation(object, surrogate.getLanguageInfo(), "in " + scope.getName()));

                completions.add(completion);
            }
        }
    }

    private static CompletionItemKind findCompletionItemKind(Object object) {
        if (INTEROP.isInstantiable(object)) {
            return CompletionItemKind.Class;
        }
        if (INTEROP.isExecutable(object)) {
            return CompletionItemKind.Function;
        }
        return null;
    }

    protected boolean fillCompletionsFromTruffleObject(List<CompletionItem> completions, LanguageInfo langInfo, Object object) {
        if (object == null) {
            return false;
        }
        Object metaObject = getMetaObject(langInfo, object);
        if (metaObject == null) {
            return false;
        }

        Object languageView = env.getLanguageView(langInfo, object);
        Object members = null;
        if (INTEROP.hasMembers(languageView)) {
            try {
                members = INTEROP.getMembers(languageView);
            } catch (UnsupportedMessageException ex) {
                // No members
            }
        }

        if (members == null || !INTEROP.hasArrayElements(members)) {
            logger.fine("No completions found for object: " + languageView);
            return false;
        }

        int counter = 0;
        long size;
        try {
            size = INTEROP.getArraySize(members);
        } catch (UnsupportedMessageException ex) {
            size = 0;
        }
        for (long i = 0; i < size; i++) {
            String key;
            Object value;
            try {
                key = INTEROP.readArrayElement(members, i).toString();
                if (INTEROP.isMemberReadable(languageView, key)) {
                    value = INTEROP.readMember(languageView, key);
                } else {
                    value = null;
                }
            } catch (ThreadDeath td) {
                throw td;
            } catch (Throwable t) {
                logger.log(Level.CONFIG, languageView.toString(), t);
                continue;
            }
            CompletionItem completion = CompletionItem.create(key);
            ++counter;
            // Keep the order in which the keys were provided
            completion.setSortText(String.format("%s%06d.%s", "+", counter, key));
            completion.setKind(CompletionItemKind.Property);
            completion.setDetail(createCompletionDetail(value, langInfo));
            try {
                completion.setDocumentation(createDocumentation(value, langInfo, "of " + INTEROP.getMetaQualifiedName(metaObject)));
            } catch (UnsupportedMessageException e) {
                throw new AssertionError(e);
            }

            completions.add(completion);
        }

        return counter > 0;
    }

    private Object createDocumentation(Object value, LanguageInfo langInfo, String scopeInformation) {
        Object documentation = getDocumentation(value, langInfo);
        if (documentation == null) {
            String markupStr = escapeMarkdown(scopeInformation);

            Object view = env.getLanguageView(langInfo, value);
            if (INTEROP.hasSourceLocation(view)) {
                SourceSection section;
                try {
                    section = INTEROP.getSourceLocation(view);
                } catch (UnsupportedMessageException e) {
                    CompilerDirectives.transferToInterpreter();
                    throw new AssertionError(e);
                }
                String code = section.getCharacters().toString();
                if (!code.isEmpty()) {
                    markupStr += "\n\n```\n" + section.getCharacters().toString() + "\n```";
                }
            }
            documentation = MarkupContent.create(MarkupKind.Markdown, markupStr);
        }
        return documentation;
    }

    static String escapeMarkdown(String original) {
        return original.replaceAll("__", "\\\\_\\\\_");
    }

    String createCompletionDetail(Object obj, LanguageInfo langInfo) {
        String detailText = "";
        if (obj == null) {
            return detailText;
        }

        Object view = env.getLanguageView(langInfo, obj);
        if (INTEROP.isExecutable(view)) {
            String formattedSignature = getFormattedSignature(view, langInfo);
            detailText = formattedSignature != null ? formattedSignature : "";
        }

        Object metaObject = null;
        if (INTEROP.hasMetaObject(view)) {
            try {
                metaObject = INTEROP.getMetaObject(view);
            } catch (UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreter();
                throw new AssertionError("Unexpected unsupported message.", e);
            }
        }

        if (metaObject != null) {
            if (!detailText.isEmpty()) {
                detailText += " ";
            }
            try {
                detailText += INTEROP.asString(INTEROP.toDisplayString(metaObject));
            } catch (UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreter();
                throw new AssertionError("Unexpected unsupported message.", e);
            }
        }
        return detailText;
    }

    public Object getDocumentation(Object value, LanguageInfo langInfo) {
        if (!(value instanceof TruffleObject) || INTEROP.isNull(value)) {
            return null;
        }
        try {
            Object docu = LSP_INTEROP.getDocumentation(value);
            if (docu instanceof String && !((String) docu).isEmpty()) {
                return docu;
            } else {
                if (docu instanceof TruffleObject) {
                    TruffleObject markup = (TruffleObject) docu;
                    MarkupKind markupKind = null;
                    String text = null;
                    if (INTEROP.isMemberReadable(markup, "kind")) {
                        Object kind = INTEROP.readMember(markup, "kind");
                        if (kind instanceof String) {
                            markupKind = MarkupKind.get((String) kind);
                        }
                    }
                    if (markupKind == null) {
                        markupKind = MarkupKind.PlainText;
                    }
                    if (INTEROP.isMemberReadable(markup, "value")) {
                        Object v = INTEROP.readMember(markup, "value");
                        if (v instanceof String) {
                            text = (String) v;
                        }
                    }
                    assert text != null : "No documentation value is provided from " + docu;
                    if (text != null) {
                        return MarkupContent.create(markupKind, text);
                    } else {
                        return MarkupContent.create(markupKind, languageToString(langInfo, docu));
                    }
                }
            }
        } catch (UnsupportedMessageException e) {
            // GET_DOCUMENTATION message is not supported
        } catch (InteropException e) {
            e.printStackTrace(err);
        }
        return null;
    }

    private String languageToString(LanguageInfo langInfo, Object value) {
        try {
            return INTEROP.asString(INTEROP.toDisplayString(env.getLanguageView(langInfo, value)));
        } catch (UnsupportedMessageException e) {
            CompilerDirectives.transferToInterpreter();
            throw new AssertionError(e);
        }
    }

    public String getFormattedSignature(Object truffleObj, LanguageInfo langInfo) {
        try {
            Object signature = LSP_INTEROP.getSignature(truffleObj);
            return languageToString(langInfo, signature);
        } catch (UnsupportedMessageException e) {
            // GET_SIGNATURE message is not supported
        }
        return null;
    }

    private LanguageInfo getObjectLanguageInfo(LanguageInfo defaultInfo, Object object) {
        assert object != null;
        if (INTEROP.hasLanguage(object)) {
            try {
                return env.getLanguageInfo(INTEROP.getLanguage(object));
            } catch (UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreter();
                throw new AssertionError(e);
            }
        } else {
            return defaultInfo;
        }
    }

    private Object getMetaObject(LanguageInfo defaultInfo, Object object) {
        LanguageInfo langInfo = getObjectLanguageInfo(defaultInfo, object);
        Object view = env.getLanguageView(langInfo, object);
        if (INTEROP.hasMetaObject(view)) {
            try {
                return INTEROP.getMetaObject(view);
            } catch (UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreter();
                throw new AssertionError("Unexpected unsupported message.", e);
            }
        }
        return null;
    }

}
