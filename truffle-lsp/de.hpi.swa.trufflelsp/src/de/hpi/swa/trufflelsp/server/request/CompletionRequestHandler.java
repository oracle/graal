package de.hpi.swa.trufflelsp.server.request;

import java.net.URI;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.MarkupContent;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Scope;
import com.oracle.truffle.api.TruffleException;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
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

import de.hpi.swa.trufflelsp.api.ContextAwareExecutorWrapper;
import de.hpi.swa.trufflelsp.exceptions.DiagnosticsNotification;
import de.hpi.swa.trufflelsp.hacks.LanguageSpecificHacks;
import de.hpi.swa.trufflelsp.interop.GetSignature;
import de.hpi.swa.trufflelsp.interop.ObjectStructures;
import de.hpi.swa.trufflelsp.server.utils.EvaluationResult;
import de.hpi.swa.trufflelsp.server.utils.NearestNodeHolder;
import de.hpi.swa.trufflelsp.server.utils.NearestSectionsFinder;
import de.hpi.swa.trufflelsp.server.utils.SourceUtils;
import de.hpi.swa.trufflelsp.server.utils.SourceWrapper;
import de.hpi.swa.trufflelsp.server.utils.TextDocumentSurrogate;
import de.hpi.swa.trufflelsp.server.utils.NearestSectionsFinder.NodeLocationType;
import de.hpi.swa.trufflelsp.server.utils.SourceUtils.SourceFix;

public class CompletionRequestHandler extends AbstractRequestHandler {

    private static boolean isInstrumentable(Node node) {
        return node instanceof InstrumentableNode && ((InstrumentableNode) node).isInstrumentable();
    }

    private static enum CompletionKind {
        UNKOWN,
        OBJECT_PROPERTY,
        GLOBALS_AND_LOCALS
    }

    private static final Node HAS_SIZE = Message.HAS_SIZE.createNode();
    private static final Node KEYS = Message.KEYS.createNode();
    private static final Node IS_INSTANTIABLE = Message.IS_INSTANTIABLE.createNode();
    private static final Node IS_EXECUTABLE = Message.IS_EXECUTABLE.createNode();
    private static final Node GET_SIGNATURE = GetSignature.INSTANCE.createNode();
    private static final Node INVOKE = Message.createInvoke(0).createNode();

    private static final int SORTING_PRIORITY_LOCALS = 1;
    private static final int SORTING_PRIORITY_GLOBALS = 2;

    private final SourceCodeEvaluator sourceCodeEvaluator;

    public CompletionRequestHandler(TruffleInstrument.Env env, Map<URI, TextDocumentSurrogate> uri2TextDocumentSurrogate, ContextAwareExecutorWrapper executor,
                    SourceCodeEvaluator sourceCodeEvaluator) {
        super(env, uri2TextDocumentSurrogate, executor);
        this.sourceCodeEvaluator = sourceCodeEvaluator;
    }

    public List<String> getCompletionTriggerCharactersWithEnteredContext() {
        //@formatter:off
        return env.getLanguages().values().stream()
                        .filter(lang -> !lang.isInternal())
                        .flatMap(info -> env.getCompletionTriggerCharacters(info.getId()).stream())
                        .distinct()
                        .collect(Collectors.toList());
        //@formatter:on
    }

    public List<String> getCompletionTriggerCharactersWithEnteredContext(String langId) {
        return env.getCompletionTriggerCharacters(langId);
    }

    public CompletionList completionWithEnteredContext(final URI uri, int line, int originalCharacter) throws DiagnosticsNotification {
        TextDocumentSurrogate surrogate = uri2TextDocumentSurrogate.get(uri);
        Source source = surrogate.getSource();
        CompletionKind completionKind = getCompletionKind(source, SourceUtils.zeroBasedLineToOneBasedLine(line, source), originalCharacter, surrogate.getCompletionTriggerCharacters());
        if (surrogate.isSourceCodeReadyForCodeCompletion() && !completionKind.equals(CompletionKind.OBJECT_PROPERTY)) {
            return createCompletions(surrogate, line, originalCharacter, completionKind);
        } else {
            // Try fixing the source code, parse again, then create the completions

            SourceFix sourceFix = SourceUtils.removeLastTextInsertion(surrogate, originalCharacter);
            if (sourceFix == null) {
                System.out.println("Unable to fix unparsable source code. No completion possible.");
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

            // We need to replace the original surrogate with the fixed one so that when a test
            // wants to import this fixed source, it will find the fixed surrogate via the custom
            // file system callback
            uri2TextDocumentSurrogate.put(uri, fixedSurrogate);
            try {
                return createCompletions(fixedSurrogate, line, sourceFix.characterIdx, getCompletionKind(sourceFix.removedCharacters, surrogate.getCompletionTriggerCharacters()));
            } finally {
                uri2TextDocumentSurrogate.put(uri, surrogate);
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
        fillCompletionsWithGlobals(surrogate, completions);

        SourceWrapper sourceWrapper = surrogate.getSourceWrapper();
        if (sourceWrapper.isParsingSuccessful() && SourceUtils.isLineValid(line, sourceWrapper.getSource())) {
            NearestNodeHolder nearestNodeHolder = NearestSectionsFinder.findNearestNode(sourceWrapper.getSource(), line, character, env);
            Node nearestNode = nearestNodeHolder.getNearestNode();

            if (isInstrumentable(nearestNode)) {
                fillCompletionsWithLocals(surrogate, nearestNode, completions, null);
            }
        }
    }

    private void fillCompletionsWithObjectProperties(TextDocumentSurrogate surrogate, int line, int character, CompletionList completions) throws DiagnosticsNotification {
        SourceWrapper sourceWrapper = surrogate.getSourceWrapper();
        if (!sourceWrapper.isParsingSuccessful() || !SourceUtils.isLineValid(line, sourceWrapper.getSource())) {
            return;
        }

        Source source = sourceWrapper.getSource();
        NearestNodeHolder nearestNodeHolder = NearestSectionsFinder.findNearestNode(source, line, character, env);
        Node nearestNode = nearestNodeHolder.getNearestNode();

        if (!isInstrumentable(nearestNode)) {
            return;
        }

        NodeLocationType locationType = nearestNodeHolder.getLocationType();
        if (locationType == NodeLocationType.CONTAINS_END) {
            EvaluationResult evalResult = sourceCodeEvaluator.tryDifferentEvalStrategies(surrogate, nearestNode);
            if (evalResult.isEvaluationDone()) {
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
            System.out.println("No object property completion possible. Caret is not directly at the end of a source section. Nearest section: " + nearestNode.getSourceSection());
        }
    }

    private static boolean isObjectPropertyCompletionCharacter(String text, List<String> completionTriggerCharacters) {
        return completionTriggerCharacters.contains(text);
    }

    private static CompletionKind getCompletionKind(String text, List<String> completionTriggerCharacters) {
        return isObjectPropertyCompletionCharacter(text, completionTriggerCharacters) ? CompletionKind.OBJECT_PROPERTY : CompletionKind.GLOBALS_AND_LOCALS;
    }

    public static CompletionKind getCompletionKind(Source source, int oneBasedLineNumber, int character, List<String> completionTriggerCharacters) {
        int lineStartOffset;
        try {
            lineStartOffset = source.getLineStartOffset(oneBasedLineNumber);
        } catch (IllegalArgumentException e) {
            return CompletionKind.GLOBALS_AND_LOCALS;
        }

        String text = source.getCharacters().toString();
        try {
            char charAtOffset = text.charAt(lineStartOffset + character - 1);
            return getCompletionKind(String.valueOf(charAtOffset), completionTriggerCharacters);
        } catch (StringIndexOutOfBoundsException e) {
            return CompletionKind.GLOBALS_AND_LOCALS;
        }
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
        LinkedHashMap<String, Map<Object, Object>> scopeMap = scopesToObjectMap(scopes);
        String[] existingCompletions = completions.getItems().stream().map((item) -> item.getLabel()).toArray(String[]::new);
        // Filter duplicates
        Set<String> completionKeys = new HashSet<>(Arrays.asList(existingCompletions));
        int scopeCounter = 0;
        for (Entry<String, Map<Object, Object>> scopeEntry : scopeMap.entrySet()) {
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
                completion.setDocumentation(createDocumentation(object, langId, "in " + scopeEntry.getKey()));

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
        return fillCompletionsFromTruffleObject(completions, langId, object, getMetaObject(langId, object));
    }

    protected boolean fillCompletionsFromTruffleObject(CompletionList completions, String langId, Object object, Object metaObject) {
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
                System.out.println("Result is no TruffleObject: " + object.getClass());
            }
        }

        if (map == null) {
            System.out.println("No completions found for object: " + object);
            return false;
        }

        for (Entry<Object, Object> entry : map.entrySet()) {
            Object value;
            try {
                value = entry.getValue();
            } catch (Exception e) {
                continue;
            }
            CompletionItem completion = new CompletionItem(entry.getKey().toString());
            CompletionItemKind kind = findCompletionItemKind(value);
            completion.setKind(kind != null ? kind : CompletionItemKind.Property);
            completion.setDetail(createCompletionDetail(entry.getKey(), value, langId));
            completion.setDocumentation(createDocumentation(value, langId, "of meta object: `" + metaObject.toString() + "`"));

            completions.getItems().add(completion);
        }

        return !map.isEmpty();
    }

    private MarkupContent createDocumentation(Object value, String langId, String scopeInformation) {
        String documentation = LanguageSpecificHacks.getDocumentation(getMetaObject(langId, value), langId);

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

        MarkupContent markup = new MarkupContent();
        markup.setValue(documentation);
        markup.setKind("markdown");
        return markup;
    }

    public String escapeMarkdown(String original) {
        return original.replaceAll("__", "\\\\_\\\\_");
    }

    public String createCompletionDetail(Object key, Object obj, String langId) {
        String detailText = "";

        TruffleObject truffleObj = null;
        if (obj instanceof TruffleObject) {
            truffleObj = (TruffleObject) obj;
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
        } catch (InteropException e) {
            e.printStackTrace(err);
        }
        return null;
    }

    protected Object getMetaObject(String langId, Object object) {
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

    public static LinkedHashMap<String, Map<Object, Object>> scopesToObjectMap(Iterable<Scope> scopes) {
        LinkedHashMap<String, Map<Object, Object>> map = new LinkedHashMap<>();
        for (Scope scope : scopes) {
            Object variables = scope.getVariables();
            if (variables instanceof TruffleObject) {
                TruffleObject truffleObj = (TruffleObject) variables;
                try {
                    TruffleObject keys = ForeignAccess.sendKeys(KEYS, truffleObj, true);
                    boolean hasSize = ForeignAccess.sendHasSize(HAS_SIZE, keys);
                    if (!hasSize) {
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

}
