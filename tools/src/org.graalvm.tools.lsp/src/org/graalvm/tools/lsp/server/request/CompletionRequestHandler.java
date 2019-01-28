package org.graalvm.tools.lsp.server.request;

import java.net.URI;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
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
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.graalvm.tools.lsp.api.ContextAwareExecutor;
import org.graalvm.tools.lsp.exceptions.DiagnosticsNotification;
import org.graalvm.tools.lsp.hacks.LanguageSpecificHacks;
import org.graalvm.tools.lsp.instrument.LSPInstrument;
import org.graalvm.tools.lsp.interop.GetDocumentation;
import org.graalvm.tools.lsp.interop.GetSignature;
import org.graalvm.tools.lsp.interop.ObjectStructures;
import org.graalvm.tools.lsp.server.utils.CoverageData;
import org.graalvm.tools.lsp.server.utils.EvaluationResult;
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
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.InteropException;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

public class CompletionRequestHandler extends AbstractRequestHandler {
    private static final TruffleLogger LOG = TruffleLogger.getLogger(LSPInstrument.ID, CompletionRequestHandler.class);

    private static boolean isInstrumentable(Node node) {
        return node instanceof InstrumentableNode && ((InstrumentableNode) node).isInstrumentable();
    }

    private enum CompletionKind {
        UNKOWN,
        OBJECT_PROPERTY,
        GLOBALS_AND_LOCALS
    }

    private static final Node HAS_SIZE = Message.HAS_SIZE.createNode();
    private static final Node KEYS = Message.KEYS.createNode();
    private static final Node IS_INSTANTIABLE = Message.IS_INSTANTIABLE.createNode();
    private static final Node IS_EXECUTABLE = Message.IS_EXECUTABLE.createNode();
    private static final Node GET_SIGNATURE = GetSignature.INSTANCE.createNode();
    private static final Node GET_DOCUMENTATION = GetDocumentation.INSTANCE.createNode();
    private static final Node IS_NULL = Message.IS_NULL.createNode();
    private static final Node INVOKE = Message.INVOKE.createNode();

    private static final int SORTING_PRIORITY_LOCALS = 1;
    private static final int SORTING_PRIORITY_GLOBALS = 2;

    private final SourceCodeEvaluator sourceCodeEvaluator;

    public CompletionRequestHandler(TruffleInstrument.Env env, TextDocumentSurrogateMap surrogateMap, ContextAwareExecutor executor,
                    SourceCodeEvaluator sourceCodeEvaluator) {
        super(env, surrogateMap, executor);
        this.sourceCodeEvaluator = sourceCodeEvaluator;
    }

    public List<String> getCompletionTriggerCharactersWithEnteredContext() {
        //@formatter:off
        return env.getLanguages().values().stream()
                        .filter(lang -> !lang.isInternal())
                        .flatMap(info -> env.getCompletionTriggerCharacters(info).stream())
                        .distinct()
                        .collect(Collectors.toList());
        //@formatter:on
    }

    public CompletionList completionWithEnteredContext(final URI uri, int line, int originalCharacter, CompletionContext completionContext) throws DiagnosticsNotification {
        LOG.log(Level.FINER, "Start finding completions for {0}:{1}:{2}", new Object[]{uri, line, originalCharacter});

        TextDocumentSurrogate surrogate = surrogateMap.get(uri);
        Source source = surrogate.getSource();

        if (!SourceUtils.isLineValid(line, source) || !SourceUtils.isColumnValid(line, originalCharacter, source)) {
            err.println("line or column is out of range, line=" + line + ", column=" + originalCharacter);
            return new CompletionList();
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
                return new CompletionList();
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
                return new CompletionList();
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
        Class<?>[] tags = LanguageSpecificHacks.getSupportedTags(surrogate.getLangId());
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
                    fillCompletionsFromTruffleObject(completions, surrogate.getLangId(), evalResult.getResult());
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
        fillCompletionsWithScopesValues(surrogate, completions, env.findTopScopes(surrogate.getLangId()), null, SORTING_PRIORITY_GLOBALS);
    }

    private void fillCompletionsWithScopesValues(TextDocumentSurrogate surrogate, CompletionList completions, Iterable<Scope> scopes,
                    CompletionItemKind completionItemKindDefault, int displayPriority) {
        String langId = surrogate.getLangId();
        LinkedHashMap<Scope, Map<Object, Object>> scopeMap = scopesToObjectMap(scopes);
        String[] existingCompletions = completions.getItems().stream().map((item) -> item.getLabel()).toArray(String[]::new);
        // Filter duplicates
        Set<String> completionKeys = new HashSet<>(Arrays.asList(existingCompletions));
        int scopeCounter = 0;
        for (Entry<Scope, Map<Object, Object>> scopeEntry : scopeMap.entrySet()) {
            ++scopeCounter;
            for (Entry<Object, Object> entry : scopeEntry.getValue().entrySet()) {
                String key = entry.getKey().toString();
                if (completionKeys.contains(key)) {
                    // Scopes are provided from inner to outer, so we need to detect duplicate keys
                    // and only take those from the most inner scope
                    continue;
                } else {
                    completionKeys.add(key);
                }

                Object object;
                try {
                    object = entry.getValue();
                } catch (Exception e) {
                    continue;
                }
                CompletionItem completion = new CompletionItem(key);
                // Inner scopes should be displayed first, so sort by priority and scopeCounter
                // (the innermost scope has the lowest counter)
                completion.setSortText(String.format("%d.%04d.%s", displayPriority, scopeCounter, key));
                CompletionItemKind completionItemKind = findCompletionItemKind(object);
                completion.setKind(completionItemKind != null ? completionItemKind : completionItemKindDefault);
                completion.setDetail(createCompletionDetail(entry.getKey(), object, langId));
                completion.setDocumentation(createDocumentation(object, langId, "in " + scopeEntry.getKey().getName()));

                completions.getItems().add(completion);
            }
        }
    }

    private static CompletionItemKind findCompletionItemKind(Object object) {
        if (object instanceof TruffleObject) {
            TruffleObject truffleObjVal = (TruffleObject) object;
            boolean isExecutable = ForeignAccess.sendIsExecutable(IS_EXECUTABLE, truffleObjVal);
            boolean isInstatiatable = ForeignAccess.sendIsInstantiable(IS_INSTANTIABLE, truffleObjVal);
            if (isInstatiatable) {
                return CompletionItemKind.Class;
            }
            if (isExecutable) {
                return CompletionItemKind.Function;
            }
        }

        return null;
    }

    protected boolean fillCompletionsFromTruffleObject(CompletionList completions, String langId, Object object) {
        Object metaObject = getMetaObject(langId, object);
        if (metaObject == null) {
            return false;
        }

        Map<Object, Object> map = null;
        if (object instanceof TruffleObject) {
            map = ObjectStructures.asMap(new ObjectStructures.MessageNodes(), (TruffleObject) object);
        } else {
            Object boxedObject = env.boxPrimitive(langId, object);
            if (boxedObject instanceof TruffleObject) {
                map = ObjectStructures.asMap(new ObjectStructures.MessageNodes(), (TruffleObject) boxedObject);
            } else {
                LOG.fine("Result is no TruffleObject: " + object.getClass());
            }
        }

        if (map == null || map.isEmpty()) {
            LOG.fine("No completions found for object: " + object);
            return false;
        }

        int counter = 0;
        for (Entry<Object, Object> entry : map.entrySet()) {
            Object value;
            try {
                value = entry.getValue();
            } catch (Exception e) {
                continue;
            }
            String key = entry.getKey().toString();
            CompletionItem completion = new CompletionItem(key);
            ++counter;
            // Keep the order in which the keys were provided
            completion.setSortText(String.format("%06d.%s", counter, key));
            CompletionItemKind kind = findCompletionItemKind(value);
            completion.setKind(kind != null ? kind : CompletionItemKind.Property);
            completion.setDetail(createCompletionDetail(entry.getKey(), value, langId));
            completion.setDocumentation(createDocumentation(value, langId, "of meta object: `" + metaObject.toString() + "`"));

            completions.getItems().add(completion);
        }

        return !map.isEmpty();
    }

    private Either<String, MarkupContent> createDocumentation(Object value, String langId, String scopeInformation) {
        MarkupContent markup = new MarkupContent();
        String documentation = null;

        if (value instanceof TruffleObject && !ForeignAccess.sendIsNull(IS_NULL, (TruffleObject) value)) {
            documentation = getDocumentation((TruffleObject) value);
            return Either.forLeft(documentation);
        }

        if (documentation == null) {
            documentation = LanguageSpecificHacks.getDocumentation(getMetaObject(langId, value), langId);

            if (documentation == null) {
                documentation = escapeMarkdown(scopeInformation);
            }

            SourceSection section = SourceUtils.findSourceLocation(env, langId, value);
            if (section != null) {
                String code = section.getCharacters().toString();
                if (!code.isEmpty()) {
                    documentation += "\n\n```\n" + section.getCharacters().toString() + "\n```";
                }
            }
            markup.setKind("markdown");
        }

        markup.setValue(documentation);
        return Either.forRight(markup);
    }

    public String escapeMarkdown(String original) {
        return original.replaceAll("__", "\\\\_\\\\_");
    }

    public String createCompletionDetail(Object key, Object obj, String langId) {
        String detailText = "";

        TruffleObject truffleObj = null;
        if (obj instanceof TruffleObject) {
            truffleObj = (TruffleObject) obj;
            if (ForeignAccess.sendIsNull(IS_NULL, truffleObj)) {
                return "";
            }
        } else {
            Object boxedObject = env.boxPrimitive(langId, obj);
            if (boxedObject instanceof TruffleObject) {
                truffleObj = (TruffleObject) boxedObject;
            }
        }

        if (truffleObj != null && key != null) {
            String formattedSignature = getFormattedSignature(truffleObj);
            detailText = formattedSignature != null ? formattedSignature : "";
        }

        if (!detailText.isEmpty()) {
            detailText += " ";
        }

        Object metaObject = getMetaObject(langId, obj);
        String metaObjectString = LanguageSpecificHacks.formatMetaObject(metaObject, langId);
        if (metaObjectString == null) {
            metaObjectString = metaObject != null ? metaObject.toString() : "";
        }
        detailText += metaObjectString;

        return detailText;
    }

    public String getDocumentation(TruffleObject truffleObj) {
        try {
            Object docu = ForeignAccess.send(GET_DOCUMENTATION, truffleObj);
            if (docu instanceof String && !((String) docu).isEmpty()) {
                return (String) docu;
            }
            if (docu instanceof TruffleObject) {
                if (!ForeignAccess.sendIsNull(IS_NULL, (TruffleObject) docu)) {
                    return docu.toString();
                }
            }
        } catch (UnsupportedMessageException | UnsupportedTypeException e) {
            // GET_DOCUMENTATION message is not supported
        } catch (InteropException e) {
            e.printStackTrace(err);
        }
        return null;
    }

    public String getFormattedSignature(TruffleObject truffleObj) {
        try {
            Object signature = ForeignAccess.send(GET_SIGNATURE, truffleObj);
            if (signature instanceof TruffleObject) {
                try {
                    Object formattedString = ForeignAccess.sendInvoke(INVOKE, (TruffleObject) signature, "format");
                    return formattedString.toString();
                } catch (InteropException e) {
                    // Fallback if no format method is provided. Simply create a comma separated
                    // list of parameters.
                    boolean hasSize = ForeignAccess.sendHasSize(HAS_SIZE, (TruffleObject) signature);
                    if (hasSize) {
                        List<Object> params = ObjectStructures.asList(new ObjectStructures.MessageNodes(), (TruffleObject) signature);
                        if (params != null) {
                            return "Parameters: " + params.stream().reduce("", (a, b) -> a.toString() + (a.toString().isEmpty() ? "" : ", ") + b.toString());
                        }
                    }
                }
            }
        } catch (UnsupportedMessageException | UnsupportedTypeException e) {
            // GET_SIGNATURE message is not supported
        } catch (InteropException e) {
            e.printStackTrace(err);
        }
        return null;
    }

    protected Object getMetaObject(String langId, Object object) {
        if (object == null) {
            return null;
        }

        LanguageInfo languageInfo = env.findLanguage(object);
        if (languageInfo == null) {
            languageInfo = env.getLanguages().get(langId);
        }

        Object metaObject = null;
        if (languageInfo != null) {
            metaObject = env.findMetaObject(languageInfo, object);
        }
        return metaObject;
    }

    public static LinkedHashMap<Scope, Map<Object, Object>> scopesToObjectMap(Iterable<Scope> scopes) {
        LinkedHashMap<Scope, Map<Object, Object>> map = new LinkedHashMap<>();
        for (Scope scope : scopes) {
            Object variables = scope.getVariables();
            if (variables instanceof TruffleObject) {
                TruffleObject truffleObj = (TruffleObject) variables;
                try {
                    TruffleObject keys = ForeignAccess.sendKeys(KEYS, truffleObj, false);
                    boolean hasSize = ForeignAccess.sendHasSize(HAS_SIZE, keys);
                    if (!hasSize) {
                        continue;
                    }

                    map.put(scope, ObjectStructures.asMap(new ObjectStructures.MessageNodes(), truffleObj));
                } catch (UnsupportedMessageException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        return map;
    }
}
