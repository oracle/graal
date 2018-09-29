package de.hpi.swa.trufflelsp.server.request;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolInformation;

import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter.SourcePredicate;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.TruffleInstrument.Env;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.SourceSection;

import de.hpi.swa.trufflelsp.api.ContextAwareExecutorWrapper;
import de.hpi.swa.trufflelsp.exceptions.DiagnosticsNotification;
import de.hpi.swa.trufflelsp.server.utils.EvaluationResult;
import de.hpi.swa.trufflelsp.server.utils.InteropUtils;
import de.hpi.swa.trufflelsp.server.utils.SourceUtils;
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
        TextDocumentSurrogate surrogate = uri2TextDocumentSurrogate.get(uri);
        InstrumentableNode definitionSearchNode = findNodeAtCaret(surrogate, line, character, StandardTags.CallTag.class);
        if (definitionSearchNode != null) {
            SourceSection definitionSearchSection = ((Node) definitionSearchNode).getSourceSection();

            System.out.println(definitionSearchNode.getClass().getSimpleName() + " " + definitionSearchSection);
            System.out.println("Trying run-to-section eval...");

            SourceSectionFilter.Builder builder = SourceUtils.createSourceSectionFilter(surrogate, definitionSearchSection);
            SourceSectionFilter eventFilter = builder.tagIs(StandardTags.CallTag.class).build();
            SourceSectionFilter inputFilter = SourceSectionFilter.ANY;
            EvaluationResult evalResult = sourceCodeEvaluator.runToSectionAndEval(surrogate, definitionSearchSection, eventFilter, inputFilter);
            if (evalResult.isEvaluationDone() && !evalResult.isError()) {
                SourceSection sourceSection = SourceUtils.findSourceLocation(env, surrogate.getLangId(), evalResult.getResult());
                List<Location> locations = new ArrayList<>();
                if (sourceSection != null && sourceSection.isAvailable()) {
                    Range range = SourceUtils.sourceSectionToRange(sourceSection);
                    URI definitionUri = SourceUtils.getOrFixFileUri(sourceSection.getSource());
                    locations.add(new Location(definitionUri.toString(), range));
                }
                if (!locations.isEmpty()) {
                    return locations;
                }
            }

            // Fallback: Static String-based name matching of symbols
            System.out.println("Trying static symbol matching...");
            String definitionSearchSymbol = definitionSearchSection.getCharacters().toString();
            definitionSearchSymbol = InteropUtils.getNormalizedSymbolName(definitionSearchNode.getNodeObject(), definitionSearchSymbol);

            return findMatchingSymbols(surrogate, definitionSearchSymbol);
        }
        return Collections.emptyList();
    }

    private List<Location> findMatchingSymbols(TextDocumentSurrogate surrogate, String symbol) {
        List<Location> locations = new ArrayList<>();
        SourcePredicate srcPredicate = SourceUtils.createLanguageFilterPredicate(surrogate.getLanguageInfo());
        List<? extends SymbolInformation> docSymbols = documentSymbolHandler.documentSymbolWithEnteredContext(srcPredicate);
        for (SymbolInformation symbolInfo : docSymbols) {
            if (symbol.equals(symbolInfo.getName())) {
                locations.add(symbolInfo.getLocation());
            }
        }
        return locations;
    }
}
