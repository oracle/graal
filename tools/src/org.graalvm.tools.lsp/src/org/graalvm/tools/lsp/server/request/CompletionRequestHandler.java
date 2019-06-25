/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.stream.Collectors;

import org.eclipse.lsp4j.CompletionContext;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.CompletionTriggerKind;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.MarkupKind;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

import org.graalvm.tools.lsp.api.ContextAwareExecutor;
import org.graalvm.tools.lsp.exceptions.DiagnosticsNotification;
import org.graalvm.tools.lsp.hacks.LanguageSpecificHacks;
import org.graalvm.tools.lsp.instrument.LSPInstrument;
import org.graalvm.tools.lsp.api.interop.LSPLibrary;
import org.graalvm.tools.lsp.server.utils.CoverageData;
import org.graalvm.tools.lsp.server.utils.EvaluationResult;
import org.graalvm.tools.lsp.server.utils.InteropUtils;
import org.graalvm.tools.lsp.server.utils.NearestNode;
import org.graalvm.tools.lsp.server.utils.NearestSectionsFinder;
import org.graalvm.tools.lsp.server.utils.NearestSectionsFinder.NodeLocationType;
import org.graalvm.tools.lsp.server.utils.SourceUtils;
import org.graalvm.tools.lsp.server.utils.SourceUtils.SourceFix;
import org.graalvm.tools.lsp.server.utils.SourceWrapper;
import org.graalvm.tools.lsp.server.utils.TextDocumentSurrogate;
import org.graalvm.tools.lsp.server.utils.TextDocumentSurrogateMap;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Scope;
import com.oracle.truffle.api.TruffleException;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

public final class CompletionRequestHandler extends AbstractRequestHandler {

    private static final TruffleLogger LOG = TruffleLogger.getLogger(LSPInstrument.ID, CompletionRequestHandler.class);
    private static final InteropLibrary INTEROP = InteropLibrary.getFactory().getUncached();
    private static final LSPLibrary LSP_INTEROP = LSPLibrary.getFactory().getUncached();

    private static boolean isInstrumentable(Node node) {
        return node instanceof InstrumentableNode && ((InstrumentableNode) node).isInstrumentable();
    }

    private enum CompletionKind {
        UNKOWN,
        OBJECT_PROPERTY,
        GLOBALS_AND_LOCALS
    }

    public final CompletionList emptyList = new CompletionList();

    private static final int SORTING_PRIORITY_LOCALS = 1;
    private static final int SORTING_PRIORITY_GLOBALS = 2;

    private final SourceCodeEvaluator sourceCodeEvaluator;

    public CompletionRequestHandler(TruffleInstrument.Env env, TextDocumentSurrogateMap surrogateMap, ContextAwareExecutor executor,
                    SourceCodeEvaluator sourceCodeEvaluator) {
        super(env, surrogateMap, executor);
        this.sourceCodeEvaluator = sourceCodeEvaluator;
    }

    public List<String> getCompletionTriggerCharactersWithEnteredContext() {
        return env.getLanguages().values().stream() //
                        .filter(lang -> !lang.isInternal()) //
                        .flatMap(info -> env.getCompletionTriggerCharacters(info).stream()) //
                        .distinct() //
                        .collect(Collectors.toList());
    }

    public CompletionList completionWithEnteredContext(final URI uri, int line, int originalCharacter, CompletionContext completionContext) throws DiagnosticsNotification {
        LOG.log(Level.FINER, "Start finding completions for {0}:{1}:{2}", new Object[]{uri, line, originalCharacter});

        TextDocumentSurrogate surrogate = surrogateMap.get(uri);
        Source source = surrogate.getSource();

        if (!SourceUtils.isLineValid(line, source) || !SourceUtils.isColumnValid(line, originalCharacter, source)) {
            LOG.fine("line or column is out of range, line=" + line + ", column=" + originalCharacter);
            return emptyList;
        }

        CompletionKind completionKind = getCompletionKind(source, SourceUtils.zeroBasedLineToOneBasedLine(line, source), originalCharacter, surrogate.getCompletionTriggerCharacters(),
                        completionContext);
        if (surrogate.isSourceCodeReadyForCodeCompletion() && !completionKind.equals(CompletionKind.OBJECT_PROPERTY)) {
            return createCompletions(surrogate, line, originalCharacter, completionKind);
        } else {
            // Try fixing the source code, parse again, then create the completions

            SourceFix sourceFix = SourceUtils.removeLastTextInsertion(surrogate, originalCharacter);
            if (sourceFix == null) {
                LOG.fine("Unable to fix unparsable source code. No completion possible.");
                return emptyList;
            }

            TextDocumentSurrogate fixedSurrogate = surrogate.copy();
            // TODO(ds) Should we reset coverage data etc? Or adjust the SourceLocations?
            fixedSurrogate.setEditorText(sourceFix.text);
            SourceWrapper sourceWrapper = fixedSurrogate.prepareParsing();
            CallTarget callTarget;
            try {
                callTarget = env.parse(sourceWrapper.getSource());
            } catch (Exception e) {
                err.println("Parsing a fixed source caused an exception: " + e.getClass().getSimpleName() + " > " + e.getLocalizedMessage());
                return emptyList;
            }
            fixedSurrogate.notifyParsingSuccessful(callTarget);

            // We need to replace the original surrogate with the fixed one so that when a run
            // script wants to import this fixed source, it will find the fixed surrogate via the
            // custom file system callback
            surrogateMap.put(uri, fixedSurrogate);
            try {
                return createCompletions(fixedSurrogate, line, sourceFix.characterIdx, getCompletionKind(sourceFix.removedCharacters, surrogate.getCompletionTriggerCharacters()));
            } finally {
                surrogateMap.put(uri, surrogate);
            }
        }
    }

    private CompletionList createCompletions(TextDocumentSurrogate surrogate, int line, int character, CompletionKind completionKind) throws DiagnosticsNotification {
        CompletionList completions = new CompletionList();
        completions.setIsIncomplete(false);

        if (completionKind == CompletionKind.GLOBALS_AND_LOCALS) {
            fillCompletionsWithGlobalsAndLocals(line, surrogate, character, completions);
        } else if (completionKind == CompletionKind.OBJECT_PROPERTY) {
            fillCompletionsWithObjectProperties(surrogate, line, character, completions);
        }

        return completions;
    }

    private void fillCompletionsWithGlobalsAndLocals(int line, TextDocumentSurrogate surrogate, int character, CompletionList completions) {
        Node nearestNode = findNearestNode(surrogate.getSourceWrapper(), line, character);

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

    private Node findNearestNode(SourceWrapper sourceWrapper, int line, int character) {
        NearestNode nearestNodeHolder = NearestSectionsFinder.findNearestNode(sourceWrapper.getSource(), line, character, env);
        return nearestNodeHolder.getNode();
    }

    private void fillCompletionsWithObjectProperties(TextDocumentSurrogate surrogate, int line, int character, CompletionList completions) throws DiagnosticsNotification {
        SourceWrapper sourceWrapper = surrogate.getSourceWrapper();
        Source source = sourceWrapper.getSource();
        Class<?>[] tags = LanguageSpecificHacks.getSupportedTags(surrogate.getLanguageInfo());
        NearestNode nearestNodeHolder = NearestSectionsFinder.findNearestNode(source, line, character, env, tags != null ? tags : new Class<?>[]{StandardTags.ExpressionTag.class});
        Node nearestNode = nearestNodeHolder.getNode();

        if (!isInstrumentable(nearestNode)) {
            return;
        }

        NodeLocationType locationType = nearestNodeHolder.getLocationType();
        if (locationType == NodeLocationType.CONTAINS_END) {
            Future<EvaluationResult> future = contextAwareExecutor.executeWithNestedContext(() -> sourceCodeEvaluator.tryDifferentEvalStrategies(surrogate, nearestNode), true);
            EvaluationResult evalResult = getFutureResultOrHandleExceptions(future);
            if (evalResult != null && evalResult.isEvaluationDone()) {
                if (!evalResult.isError()) {
                    fillCompletionsFromTruffleObject(completions, surrogate.getLanguageInfo(), evalResult.getResult());
                } else {
                    if (evalResult.getResult() instanceof TruffleException) {
                        TruffleException te = (TruffleException) evalResult.getResult();
                        throw DiagnosticsNotification.create(surrogate.getUri(),
                                        new Diagnostic(SourceUtils.sourceSectionToRange(te.getSourceLocation()), "An error occurred during execution: " + te.toString(),
                                                        DiagnosticSeverity.Warning, "Graal"));
                    } else {
                        ((Exception) evalResult.getResult()).printStackTrace(err);
                    }
                }
            } else {
                throw DiagnosticsNotification.create(surrogate.getUri(),
                                new Diagnostic(SourceUtils.sourceSectionToRange(nearestNode.getSourceSection()), "No type information available for this source section.",
                                                DiagnosticSeverity.Information, "Graal"));
            }
        } else {
            LOG.fine("No object property completion possible. Caret is not directly at the end of a source section. Nearest section: " + nearestNode.getSourceSection());
        }
    }

    private static boolean isObjectPropertyCompletionCharacter(String text, List<String> completionTriggerCharacters) {
        return completionTriggerCharacters.contains(text);
    }

    private static CompletionKind getCompletionKind(String text, List<String> completionTriggerCharacters) {
        return isObjectPropertyCompletionCharacter(text, completionTriggerCharacters) ? CompletionKind.OBJECT_PROPERTY : CompletionKind.GLOBALS_AND_LOCALS;
    }

    public static CompletionKind getCompletionKind(Source source, int oneBasedLineNumber, int character, List<String> completionTriggerCharacters, CompletionContext completionContext) {
        if (completionContext != null && completionContext.getTriggerKind() == CompletionTriggerKind.TriggerCharacter && completionContext.getTriggerCharacter() != null) {
            if (isObjectPropertyCompletionCharacter(completionContext.getTriggerCharacter(), completionTriggerCharacters)) {
                return CompletionKind.OBJECT_PROPERTY;
            }

            // Completion was triggered by a character, which is a completion trigger character of
            // another language. Therefore we have to skip the current completion request.
            return CompletionKind.UNKOWN;
        }

        int lineStartOffset = source.getLineStartOffset(oneBasedLineNumber);
        if (lineStartOffset + character == 0) {
            return CompletionKind.GLOBALS_AND_LOCALS;
        }

        String text = source.getCharacters().toString();
        char charAtOffset = text.charAt(lineStartOffset + character - 1);
        return getCompletionKind(String.valueOf(charAtOffset), completionTriggerCharacters);
    }

    private void fillCompletionsWithLocals(final TextDocumentSurrogate surrogate, Node nearestNode, CompletionList completions, VirtualFrame frame) {
        fillCompletionsWithScopesValues(surrogate, completions, env.findLocalScopes(nearestNode, frame), CompletionItemKind.Variable, SORTING_PRIORITY_LOCALS);
    }

    private void fillCompletionsWithGlobals(final TextDocumentSurrogate surrogate, CompletionList completions) {
        fillCompletionsWithScopesValues(surrogate, completions, env.findTopScopes(surrogate.getLanguageId()), null, SORTING_PRIORITY_GLOBALS);
    }

    private void fillCompletionsWithScopesValues(TextDocumentSurrogate surrogate, CompletionList completions, Iterable<Scope> scopes,
                    CompletionItemKind completionItemKindDefault, int displayPriority) {
        LanguageInfo langInfo = surrogate.getLanguageInfo();
        String[] existingCompletions = completions.getItems().stream().map((item) -> item.getLabel()).toArray(String[]::new);
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
                LOG.log(Level.INFO, ex.getLocalizedMessage(), ex);
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
                    LOG.log(Level.CONFIG, variables.toString(), t);
                    continue;
                }
                CompletionItem completion = new CompletionItem(key);
                // Inner scopes should be displayed first, so sort by priority and scopeCounter
                // (the innermost scope has the lowest counter)
                completion.setSortText(String.format("%d.%04d.%s", displayPriority, scopeCounter, key));
                CompletionItemKind completionItemKind = findCompletionItemKind(object);
                completion.setKind(completionItemKind != null ? completionItemKind : completionItemKindDefault);
                completion.setDetail(createCompletionDetail(object, langInfo));
                completion.setDocumentation(createDocumentation(object, surrogate.getLanguageInfo(), "in " + scope.getName()));

                completions.getItems().add(completion);
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

    protected boolean fillCompletionsFromTruffleObject(CompletionList completions, LanguageInfo langInfo, Object object) {
        if (object == null) {
            return false;
        }
        String metaObject = getMetaObject(langInfo, object);
        if (metaObject == null) {
            return false;
        }

        Object boxedObject;
        Object keys = null;
        if (InteropUtils.isPrimitive(object)) {
            boxedObject = env.boxPrimitive(langInfo.getId(), object);
            if (boxedObject == null) {
                LOG.fine("No completions for primitive: " + object + ", no boxed object in language " + langInfo.getId());
                return false;
            }
        } else {
            boxedObject = object;
        }
        try {
            keys = INTEROP.getMembers(boxedObject);
        } catch (UnsupportedMessageException ex) {
            // No members
        }

        if (keys == null || !INTEROP.hasArrayElements(keys)) {
            LOG.fine("No completions found for object: " + boxedObject);
            return false;
        }

        int counter = 0;
        long size;
        try {
            size = INTEROP.getArraySize(keys);
        } catch (UnsupportedMessageException ex) {
            size = 0;
        }
        for (long i = 0; i < size; i++) {
            String key;
            Object value;
            try {
                key = INTEROP.readArrayElement(keys, i).toString();
                value = INTEROP.readMember(boxedObject, key);
            } catch (ThreadDeath td) {
                throw td;
            } catch (Throwable t) {
                LOG.log(Level.CONFIG, boxedObject.toString(), t);
                continue;
            }
            CompletionItem completion = new CompletionItem(key);
            ++counter;
            // Keep the order in which the keys were provided
            completion.setSortText(String.format("%06d.%s", counter, key));
            CompletionItemKind kind = findCompletionItemKind(value);
            completion.setKind(kind != null ? kind : CompletionItemKind.Property);
            completion.setDetail(createCompletionDetail(value, langInfo));
            completion.setDocumentation(createDocumentation(value, langInfo, "of meta object: `" + metaObject + "`"));

            completions.getItems().add(completion);
        }

        return counter > 0;
    }

    private Either<String, MarkupContent> createDocumentation(Object value, LanguageInfo langInfo, String scopeInformation) {
        Either<String, MarkupContent> documentation = getDocumentation(value, langInfo);
        if (documentation == null) {
            MarkupContent markup = new MarkupContent();
            String markupStr = escapeMarkdown(scopeInformation);

            SourceSection section = env.findSourceLocation(langInfo, value);
            if (section != null) {
                String code = section.getCharacters().toString();
                if (!code.isEmpty()) {
                    markupStr += "\n\n```\n" + section.getCharacters().toString() + "\n```";
                }
            }
            markup.setKind(MarkupKind.MARKDOWN);
            markup.setValue(markupStr);
            documentation = Either.forRight(markup);
        }
        return documentation;
    }

    public static String escapeMarkdown(String original) {
        return original.replaceAll("__", "\\\\_\\\\_");
    }

    @SuppressWarnings("all") // The parameter langInfo should not be assigned
    public String createCompletionDetail(Object obj, LanguageInfo langInfo) {
        String detailText = "";

        TruffleObject truffleObj = null;
        if (obj instanceof TruffleObject) {
            truffleObj = (TruffleObject) obj;
            if (INTEROP.isNull(truffleObj)) {
                return "";
            }
            langInfo = getObjectLanguageInfo(langInfo, obj);
        } else {
            Object boxedObject = env.boxPrimitive(langInfo.getId(), obj);
            if (boxedObject instanceof TruffleObject) {
                truffleObj = (TruffleObject) boxedObject;
            }
        }

        if (truffleObj != null) {
            String formattedSignature = getFormattedSignature(truffleObj, langInfo);
            detailText = formattedSignature != null ? formattedSignature : "";
        }

        if (!detailText.isEmpty()) {
            detailText += " ";
        }

        Object metaObject = env.findMetaObject(langInfo, obj);
        if (metaObject != null) {
            detailText += env.toString(langInfo, metaObject);
        }
        return detailText;
    }

    public Either<String, MarkupContent> getDocumentation(Object value, LanguageInfo langInfo) {
        if (!(value instanceof TruffleObject) || INTEROP.isNull(value)) {
            return null;
        }
        try {
            Object docu = LSP_INTEROP.getDocumentation(value);
            if (docu instanceof String && !((String) docu).isEmpty()) {
                return Either.forLeft((String) docu);
            } else {
                if (docu instanceof TruffleObject) {
                    TruffleObject markup = (TruffleObject) docu;
                    MarkupContent content = new MarkupContent();
                    try {
                        docu = INTEROP.invokeMember(markup, MarkupKind.MARKDOWN);
                        content.setKind(MarkupKind.MARKDOWN);
                    } catch (InteropException e) {
                        docu = INTEROP.invokeMember(markup, MarkupKind.PLAINTEXT);
                        content.setKind(MarkupKind.PLAINTEXT);
                    }
                    content.setValue(env.toString(langInfo, docu));
                    return Either.forRight(content);
                }
            }
        } catch (UnsupportedMessageException | UnsupportedTypeException e) {
            // GET_DOCUMENTATION message is not supported
        } catch (InteropException e) {
            e.printStackTrace(err);
        }
        return null;
    }

    public String getFormattedSignature(TruffleObject truffleObj, LanguageInfo langInfo) {
        try {
            Object signature = LSP_INTEROP.getSignature(truffleObj);
            return env.toString(langInfo, signature);
        } catch (UnsupportedMessageException e) {
            // GET_SIGNATURE message is not supported
        }
        return null;
    }

    private LanguageInfo getObjectLanguageInfo(LanguageInfo defaultInfo, Object object) {
        assert object != null;
        LanguageInfo langInfo = env.findLanguage(object);
        if (langInfo == null) {
            langInfo = defaultInfo;
        }
        return langInfo;
    }

    private String getMetaObject(LanguageInfo defaultInfo, Object object) {
        LanguageInfo langInfo = getObjectLanguageInfo(defaultInfo, object);
        Object metaObject = env.findMetaObject(langInfo, object);
        if (metaObject == null) {
            return null;
        } else {
            return env.toString(langInfo, metaObject);
        }
    }

}
