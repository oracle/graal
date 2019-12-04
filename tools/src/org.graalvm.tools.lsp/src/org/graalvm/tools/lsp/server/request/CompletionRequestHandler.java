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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.stream.Collectors;

import org.graalvm.tools.lsp.server.types.CompletionContext;
import org.graalvm.tools.lsp.server.types.CompletionItem;
import org.graalvm.tools.lsp.server.types.CompletionItemKind;
import org.graalvm.tools.lsp.server.types.CompletionList;
import org.graalvm.tools.lsp.server.types.CompletionTriggerKind;
import org.graalvm.tools.lsp.server.types.Diagnostic;
import org.graalvm.tools.lsp.server.types.DiagnosticSeverity;
import org.graalvm.tools.lsp.server.types.MarkupContent;
import org.graalvm.tools.lsp.server.types.MarkupKind;

import org.graalvm.tools.lsp.server.ContextAwareExecutor;
import org.graalvm.tools.lsp.exceptions.DiagnosticsNotification;
import org.graalvm.tools.lsp.instrument.LSPInstrument;
import org.graalvm.tools.lsp.interop.LSPLibrary;
import org.graalvm.tools.lsp.server.utils.CoverageData;
import org.graalvm.tools.lsp.server.utils.DeclarationData.Symbol;
import org.graalvm.tools.lsp.server.utils.EvaluationResult;
import org.graalvm.tools.lsp.server.utils.InteropUtils;
import org.graalvm.tools.lsp.server.utils.NearestNode;
import org.graalvm.tools.lsp.server.utils.NearestSectionsFinder;
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

    private enum CompletionKind {
        UNKOWN,
        OBJECT_PROPERTY,
        GLOBALS_AND_LOCALS
    }

    public final CompletionList emptyList = CompletionList.create(Collections.emptyList(), false);

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

    public CompletionList completionWithEnteredContext(final URI uri, int line, int column, CompletionContext completionContext) throws DiagnosticsNotification {
        LOG.log(Level.FINER, "Start finding completions for {0}:{1}:{2}", new Object[]{uri, line, column});

        TextDocumentSurrogate surrogate = surrogateMap.get(uri);
        if (surrogate == null) {
            LOG.info("Completion requested in an unknown document: " + uri);
            return emptyList;
        }
        Source source = surrogate.getSource();

        if (!SourceUtils.isLineValid(line, source) || !SourceUtils.isColumnValid(line, column, source)) {
            LOG.fine("line or column is out of range, line=" + line + ", column=" + column);
            return emptyList;
        }

        CompletionKind completionKind = getCompletionKind(source, SourceUtils.zeroBasedLineToOneBasedLine(line, source), column, surrogate.getCompletionTriggerCharacters(),
                        completionContext);
        if (surrogate.isSourceCodeReadyForCodeCompletion()) {
            return createCompletions(surrogate, line, column, completionKind);
        } else {
            // Try fixing the source code, parse again, then create the completions

            SourceFix sourceFix = SourceUtils.removeLastTextInsertion(surrogate, column);
            if (sourceFix == null) {
                LOG.fine("Unable to fix unparsable source code. No completion possible.");
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
                return createCompletions(fixedSurrogate, line, sourceFix.characterIdx, getCompletionKind(sourceFix.removedCharacters, surrogate.getCompletionTriggerCharacters()));
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
        NearestNode nearestNodeHolder = NearestSectionsFinder.findNearestNode(sourceWrapper.getSource(), line, column, env);
        return nearestNodeHolder.getNode();
    }

    private void fillCompletionsWithObjectDeclaredProperties(TextDocumentSurrogate surrogate, int line, int column, List<CompletionItem> completions) {
        SourceWrapper sourceWrapper = surrogate.getSourceWrapper();
        Source source = sourceWrapper.getSource();
        NearestNode nearestNodeHolder = NearestSectionsFinder.findNodeBeforePos(source, line, column, env);
        SourceSection nearestSection = nearestNodeHolder.getSourceSection();
        if (nearestSection == null) {
            LOG.fine("No object property completion possible. No section found before " + line + ":" + column);
            return;
        }

        Symbol symbol = findDeclaredSymbol(surrogate, nearestSection);
        if (symbol != null) {
            fillDeclaredSymbols(symbol.getChildren(), completions, SORTING_PRIORITY_LOCALS, 0);
        }
    }

    private Symbol findDeclaredSymbol(TextDocumentSurrogate surrogate, SourceSection section) {
        String text = section.getCharacters().toString().trim();
        List<String> splitText = splitByTriggerCharacters(text, surrogate.getCompletionTriggerCharacters());
        Symbol symbol = null;
        for (String s : splitText) {
            Collection<Symbol> declaredSymbols = (symbol == null) ? getDeclarationData().getDeclaredSymbols(section) : symbol.getChildren();
            symbol = null;
            for (Symbol ds : declaredSymbols) {
                if (ds.getName().equals(s)) {
                    symbol = ds;
                    break;
                }
            }
            if (symbol != null) {
                String type = symbol.getType();
                if (type != null) {
                    // The symbol has a type, we need to find a symbol that represents that type:
                    symbol = getDeclarationData().findType(type, section);
                }
            }
            if (symbol == null) {
                break;
            }
        }
        return symbol;
    }

    private static List<String> splitByTriggerCharacters(String text, List<String> triggerCharacters) {
        List<String> split = null;
        int start = 0;
        String triggerCharacter;
        do {
            triggerCharacter = null;
            int i = text.length();
            for (String tc : triggerCharacters) {
                int tci = text.indexOf(tc, start);
                if (tci > 0) {
                    i = Math.min(i, tci);
                    triggerCharacter = tc;
                }
            }
            if (triggerCharacter != null) {
                if (split == null) {
                    split = new ArrayList<>();
                }
                split.add(text.substring(start, i));
                start = i + triggerCharacter.length();
            } else {
                if (split == null) {
                    split = Collections.singletonList(text);
                } else {
                    split.add(text.substring(start));
                }
            }
        } while (triggerCharacter != null);
        return split;
    }

    private void fillCompletionsWithObjectProperties(TextDocumentSurrogate surrogate, int line, int column, List<CompletionItem> completions) throws DiagnosticsNotification {
        if (!surrogate.hasCoverageData()) {
            fillCompletionsWithObjectDeclaredProperties(surrogate, line, column, completions);
            return;
        }
        SourceWrapper sourceWrapper = surrogate.getSourceWrapper();
        Source source = sourceWrapper.getSource();
        NearestNode nearestNodeHolder = NearestSectionsFinder.findNodeBeforePos(source, line, column, env);
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
            LOG.fine("No object property completion possible. Caret is not directly at the end of a source section. Line: " + line + ", column: " + column);
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
        int scopeCounter = fillCompletionsWithScopesValues(surrogate, completions, env.findLocalScopes(nearestNode, frame), CompletionItemKind.Variable, SORTING_PRIORITY_LOCALS);
        fillDeclarationCompletion(nearestNode, completions, scopeCounter);
    }

    private void fillCompletionsWithGlobals(final TextDocumentSurrogate surrogate, List<CompletionItem> completions) {
        int scopeCounter = fillCompletionsWithScopesValues(surrogate, completions, env.findTopScopes(surrogate.getLanguageId()), null, SORTING_PRIORITY_GLOBALS);
        fillGlobalDeclarationCompletion(completions, scopeCounter);
    }

    private void fillDeclarationCompletion(Node nearestNode, List<CompletionItem> completions, int scopeCounter) {
        fillDeclaredSymbols(getDeclarationData().getDeclaredSymbols(nearestNode.getSourceSection()), completions, SORTING_PRIORITY_LOCALS, scopeCounter);
    }

    private void fillGlobalDeclarationCompletion(List<CompletionItem> completions, int scopeCounter) {
        fillDeclaredSymbols(getDeclarationData().getGlobalDeclaredSymbols(), completions, SORTING_PRIORITY_GLOBALS, scopeCounter);
    }

    private static void fillDeclaredSymbols(Collection<Symbol> declaredSymbols, List<CompletionItem> completions, int displayPriority, int lastScopeCounter) {
        String[] existingCompletions = completions.stream().map((item) -> item.getLabel()).toArray(String[]::new);
        // Filter duplicates
        Set<String> completionKeys = new HashSet<>(Arrays.asList(existingCompletions));
        int scopeCounter = lastScopeCounter;
        for (Symbol symbol : declaredSymbols) {
            ++scopeCounter;
            String name = symbol.getName();
            if (completionKeys.contains(name)) {
                continue;
            } else {
                completionKeys.add(name);
            }
            CompletionItem completion = CompletionItem.create(name);
            // Inner scopes should be displayed first, so sort by priority and scopeCounter
            // (the innermost scope has the lowest counter)
            completion.setSortText(String.format("%d.%04d.%s", displayPriority, scopeCounter, name));
            CompletionItemKind completionItemKind = CompletionItemKind.valueOf(symbol.getKind());
            completion.setKind(completionItemKind/*
                                                  * != null ? completionItemKind :
                                                  * completionItemKindDefault
                                                  */);
            completion.setDetail(symbol.getType());
            completion.setDocumentation(symbol.getDescription());
            completion.setDeprecated(symbol.isDeprecated());

            completions.add(completion);
        }
    }

    private int fillCompletionsWithScopesValues(TextDocumentSurrogate surrogate, List<CompletionItem> completions, Iterable<Scope> scopes,
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
                CompletionItem completion = CompletionItem.create(key);
                // Inner scopes should be displayed first, so sort by priority and scopeCounter
                // (the innermost scope has the lowest counter)
                completion.setSortText(String.format("%d.%04d.%s", displayPriority, scopeCounter, key));
                CompletionItemKind completionItemKind = findCompletionItemKind(object);
                completion.setKind(completionItemKind != null ? completionItemKind : completionItemKindDefault);
                completion.setDetail(createCompletionDetail(object, langInfo));
                completion.setDocumentation(createDocumentation(object, surrogate.getLanguageInfo(), "in " + scope.getName()));

                completions.add(completion);
            }
        }
        return scopeCounter;
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
        String metaObject = getMetaObject(langInfo, object);
        if (metaObject == null) {
            return false;
        }

        Object boxedObject;
        Object keys = null;
        if (InteropUtils.isPrimitive(object)) {
            boxedObject = env.boxPrimitive(langInfo, object);
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
            CompletionItem completion = CompletionItem.create(key);
            ++counter;
            // Keep the order in which the keys were provided
            completion.setSortText(String.format("%06d.%s", counter, key));
            CompletionItemKind kind = findCompletionItemKind(value);
            completion.setKind(kind != null ? kind : CompletionItemKind.Property);
            completion.setDetail(createCompletionDetail(value, langInfo));
            completion.setDocumentation(createDocumentation(value, langInfo, "of meta object: `" + metaObject + "`"));

            completions.add(completion);
        }

        return counter > 0;
    }

    private Object createDocumentation(Object value, LanguageInfo langInfo, String scopeInformation) {
        Object documentation = getDocumentation(value, langInfo);
        if (documentation == null) {
            String markupStr = escapeMarkdown(scopeInformation);

            SourceSection section = env.findSourceLocation(langInfo, value);
            if (section != null) {
                String code = section.getCharacters().toString();
                if (!code.isEmpty()) {
                    markupStr += "\n\n```\n" + section.getCharacters().toString() + "\n```";
                }
            }
            documentation = MarkupContent.create(MarkupKind.Markdown, markupStr);
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
            Object boxedObject = env.boxPrimitive(langInfo, obj);
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
                    MarkupKind kind;
                    try {
                        docu = INTEROP.invokeMember(markup, MarkupKind.Markdown.getStringValue());
                        kind = MarkupKind.Markdown;
                    } catch (InteropException e) {
                        docu = INTEROP.invokeMember(markup, MarkupKind.PlainText.getStringValue());
                        kind = MarkupKind.PlainText;
                    }
                    return MarkupContent.create(kind, env.toString(langInfo, docu));
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
