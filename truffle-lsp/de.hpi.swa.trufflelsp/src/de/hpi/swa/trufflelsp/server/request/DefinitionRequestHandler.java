package de.hpi.swa.trufflelsp.server.request;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolInformation;

import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.LoadSourceEvent;
import com.oracle.truffle.api.instrumentation.LoadSourceListener;
import com.oracle.truffle.api.instrumentation.SourceFilter;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.StandardTags.DeclarationTag;
import com.oracle.truffle.api.instrumentation.TruffleInstrument.Env;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

import de.hpi.swa.trufflelsp.api.ContextAwareExecutorWrapper;
import de.hpi.swa.trufflelsp.exceptions.DiagnosticsNotification;
import de.hpi.swa.trufflelsp.hacks.LanguageSpecificHacks;
import de.hpi.swa.trufflelsp.interop.ObjectStructures;
import de.hpi.swa.trufflelsp.interop.ObjectStructures.MessageNodes;
import de.hpi.swa.trufflelsp.server.utils.EvaluationResult;
import de.hpi.swa.trufflelsp.server.utils.NearestSectionsFinder;
import de.hpi.swa.trufflelsp.server.utils.NearestSectionsFinder.NearestSections;
import de.hpi.swa.trufflelsp.server.utils.SourceUtils;
import de.hpi.swa.trufflelsp.server.utils.SourceWrapper;
import de.hpi.swa.trufflelsp.server.utils.TextDocumentSurrogate;

public class DefinitionRequestHandler extends AbstractRequestHandler {

    private final SourceCodeEvaluator sourceCodeEvaluator;
    private final DocumentSymbolRequestHandler documentSymbolHandler;

    public DefinitionRequestHandler(Env env, Map<URI, TextDocumentSurrogate> uri2TextDocumentSurrogate, ContextAwareExecutorWrapper contextAwareExecutor, SourceCodeEvaluator evaluator,
                    DocumentSymbolRequestHandler documentSymbolHandler) {
        super(env, uri2TextDocumentSurrogate, contextAwareExecutor);
        this.sourceCodeEvaluator = evaluator;
        this.documentSymbolHandler = documentSymbolHandler;
    }

    public List<? extends Location> definitionWithEnteredContext(URI uri, int line, int character) throws DiagnosticsNotification {
        List<Location> locations = new ArrayList<>();

        TextDocumentSurrogate surrogate = uri2TextDocumentSurrogate.get(uri);
        SourceWrapper sourceWrapper = surrogate.getSourceWrapper();
        if (sourceWrapper.isParsingSuccessful()) {
            Source source = sourceWrapper.getSource();
            int oneBasedLineNumber = SourceUtils.zeroBasedLineToOneBasedLine(line, source);
            NearestSections nearestSections = NearestSectionsFinder.getNearestSections(source, env, oneBasedLineNumber, character, StandardTags.CallTag.class);
            SourceSection definitionSearchSection = nearestSections.getContainsSourceSection();
            InstrumentableNode definitionSearchNode = nearestSections.getContainsNode();
            if (definitionSearchSection == null && nearestSections.getNextSourceSection() != null) {
                SourceSection nextNodeSection = nearestSections.getNextSourceSection();
                if (nextNodeSection.getStartLine() == oneBasedLineNumber && nextNodeSection.getStartColumn() == character + 1) {
                    // nextNodeSection is directly before the caret, so we use that one as fallback
                    definitionSearchSection = nextNodeSection;
                    definitionSearchNode = nearestSections.getNextNode();
                }
            }
            if (definitionSearchSection != null) {
                System.out.println(definitionSearchNode.getClass().getSimpleName() + " " + definitionSearchSection);

                System.out.println("Trying run-to-section eval...");

                SourceSectionFilter.Builder builder = SourceUtils.createSourceSectionFilter(surrogate, definitionSearchSection);
                SourceSectionFilter eventFilter = builder.tagIs(StandardTags.CallTag.class).build();
                SourceSectionFilter inputFilter = SourceSectionFilter.ANY;
                EvaluationResult evalResult = sourceCodeEvaluator.runToSectionAndEval(surrogate, definitionSearchSection, eventFilter, inputFilter);
                if (evalResult.isEvaluationDone() && !evalResult.isError()) {
                    SourceSection sourceSection = SourceUtils.findSourceLocation(env, surrogate.getLangId(), evalResult.getResult());
                    if (sourceSection != null && sourceSection.isAvailable()) {
                        Range range = SourceUtils.sourceSectionToRange(sourceSection);
                        URI definitionUri = SourceUtils.getOrFixFileUri(sourceSection.getSource());
                        locations.add(new Location(definitionUri.toString(), range));
                    }
                }

                if (locations.isEmpty()) {
                    // Fallback: Static String-based name matching of symbols
                    System.out.println("Trying static symbol matching...");
                    String definitionSearchSymbol = definitionSearchSection.getCharacters().toString();
                    Object nodeObject = definitionSearchNode.getNodeObject();
                    if (!(nodeObject instanceof TruffleObject)) {
                        definitionSearchSymbol = LanguageSpecificHacks.normalizeSymbol(definitionSearchSymbol);
                    } else {
                        Map<Object, Object> map = ObjectStructures.asMap(new MessageNodes(), (TruffleObject) nodeObject);
                        if (map.containsKey(DeclarationTag.NAME)) {
                            String name = map.get(DeclarationTag.NAME).toString();
                            definitionSearchSymbol = name;
                        }
                    }

                    final List<Source> sources = new ArrayList<>();
                    SourceFilter filter = SourceFilter.newBuilder().sourceIs(SourceUtils.createLanguageFilterPredicate(surrogate.getLanguageInfo())).includeInternal(false).build();
                    env.getInstrumenter().attachLoadSourceListener(filter, new LoadSourceListener() {

                        public void onLoad(LoadSourceEvent event) {
                            sources.add(event.getSource());
                        }
                    }, true).dispose();

                    for (Source src : sources) {
                        List<? extends SymbolInformation> docSymbols = documentSymbolHandler.documentSymbolWithEnteredContext(src.getURI());
                        for (SymbolInformation symbolInfo : docSymbols) {
                            if (definitionSearchSymbol.equals(symbolInfo.getName())) {
                                locations.add(symbolInfo.getLocation());
                            }
                        }
                    }
                }
            }
        }
        return locations;
    }
}
