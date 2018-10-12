package de.hpi.swa.trufflelsp.server.request;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolInformation;

import com.oracle.truffle.api.Scope;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter.SourcePredicate;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.TruffleInstrument.Env;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeVisitor;
import com.oracle.truffle.api.source.SourceSection;

import de.hpi.swa.trufflelsp.api.ContextAwareExecutorWrapper;
import de.hpi.swa.trufflelsp.instrument.LSOptions;
import de.hpi.swa.trufflelsp.interop.ObjectStructures;
import de.hpi.swa.trufflelsp.interop.ObjectStructures.MessageNodes;
import de.hpi.swa.trufflelsp.server.utils.CoverageData;
import de.hpi.swa.trufflelsp.server.utils.EvaluationResult;
import de.hpi.swa.trufflelsp.server.utils.InteropUtils;
import de.hpi.swa.trufflelsp.server.utils.SourceUtils;
import de.hpi.swa.trufflelsp.server.utils.SurrogateMap;
import de.hpi.swa.trufflelsp.server.utils.TextDocumentSurrogate;

public class DefinitionRequestHandler extends AbstractRequestHandler {

    private final SourceCodeEvaluator sourceCodeEvaluator;
    private final SymbolRequestHandler symbolHandler;

    public DefinitionRequestHandler(Env env, SurrogateMap surrogateMap, ContextAwareExecutorWrapper contextAwareExecutor, SourceCodeEvaluator evaluator,
                    SymbolRequestHandler documentSymbolHandler) {
        super(env, surrogateMap, contextAwareExecutor);
        this.sourceCodeEvaluator = evaluator;
        this.symbolHandler = documentSymbolHandler;
    }

    public List<? extends Location> definitionWithEnteredContext(URI uri, int line, int character) {
        TextDocumentSurrogate surrogate = surrogateMap.get(uri);
        InstrumentableNode definitionSearchNode = findNodeAtCaret(surrogate, line, character, StandardTags.CallTag.class, StandardTags.ReadVariableTag.class);
        if (definitionSearchNode != null) {
            SourceSection definitionSearchSection = ((Node) definitionSearchNode).getSourceSection();
            System.out.println(definitionSearchNode.getClass().getSimpleName() + " " + definitionSearchSection);

            if (definitionSearchNode.hasTag(StandardTags.CallTag.class)) {
                return definitionOfCallTagedNode(surrogate, definitionSearchNode, definitionSearchSection);
            } else if (definitionSearchNode.hasTag(StandardTags.ReadVariableTag.class)) {
                return definitionOfVariableNode(surrogate, definitionSearchNode);
            }
        }
        return Collections.emptyList();
    }

    private List<? extends Location> definitionOfVariableNode(TextDocumentSurrogate surrogate, InstrumentableNode definitionSearchNode) {
        String readVariableName = getName(definitionSearchNode);
        if (readVariableName != null) {
            List<CoverageData> coverageData = surrogate.getCoverageData(((Node) definitionSearchNode).getSourceSection());
            List<Node> writeNodes = new ArrayList<>();
            VirtualFrame frame = null;
            if (coverageData != null) {
                CoverageData data = coverageData.stream().findFirst().orElse(null);
                if (data != null) {
                    frame = data.getFrame();
                }
            }
            Iterable<Scope> scopesInnerToOuter = env.findLocalScopes((Node) definitionSearchNode, frame);
            LinkedList<Scope> scopesOuterToInner = new LinkedList<>();
            for (Scope scope : scopesInnerToOuter) {
                scopesOuterToInner.addFirst(scope);
            }
            for (Scope scope : scopesOuterToInner) {
                Node scopeRoot = scope.getNode();
                if (scopeRoot != null) {
                    scopeRoot.accept(new NodeVisitor() {

                        public boolean visit(Node node) {
                            if (node instanceof InstrumentableNode) {
                                if (((InstrumentableNode) node).hasTag(StandardTags.WriteVariableTag.class)) {
                                    String name = getName((InstrumentableNode) node);
                                    if (name.equals(readVariableName)) {
                                        writeNodes.add(node);
                                    }
                                }
                            }
                            return true;
                        }
                    });
                }
            }

            List<Location> locations = new ArrayList<>();
            // Interpret the first write node of the most outer scope as declaration
            if (!writeNodes.isEmpty()) {
                Node node = writeNodes.get(0);
                SourceSection sourceSection = node.getSourceSection();
                if (isValidSourceSection(sourceSection)) {
                    Range range = SourceUtils.sourceSectionToRange(sourceSection);
                    URI definitionUri = SourceUtils.getOrFixFileUri(sourceSection.getSource());
                    locations.add(new Location(definitionUri.toString(), range));
                }
                return locations;
            }
        }
        return Collections.emptyList();
    }

    private List<? extends Location> definitionOfCallTagedNode(TextDocumentSurrogate surrogate, InstrumentableNode definitionSearchNode, SourceSection definitionSearchSection) {
        System.out.println("Trying run-to-section eval...");

        SourceSectionFilter.Builder builder = SourceUtils.createSourceSectionFilter(surrogate, definitionSearchSection);
        SourceSectionFilter eventFilter = builder.tagIs(StandardTags.CallTag.class).build();
        SourceSectionFilter inputFilter = SourceSectionFilter.ANY;
        Callable<EvaluationResult> taskWithResult = () -> sourceCodeEvaluator.runToSectionAndEval(surrogate, definitionSearchSection, eventFilter, inputFilter);

        Future<EvaluationResult> future = contextAwareExecutor.executeWithNestedContext(taskWithResult, true);
        EvaluationResult evalResult = getFutureResultOrHandleExceptions(future);
        if (evalResult != null && evalResult.isEvaluationDone() && !evalResult.isError()) {
            SourceSection sourceSection = SourceUtils.findSourceLocation(env, surrogate.getLangId(), evalResult.getResult());
            List<Location> locations = new ArrayList<>();
            if (isValidSourceSection(sourceSection)) {
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

    String getName(InstrumentableNode node) {
        Object nodeObject = node.getNodeObject();
        if (nodeObject instanceof TruffleObject) {
            Map<Object, Object> map = ObjectStructures.asMap(new MessageNodes(), (TruffleObject) nodeObject);
            if (map.containsKey("name")) {
                return map.get("name").toString();
            }
        }
        return null;
    }

    private boolean isValidSourceSection(SourceSection sourceSection) {
        boolean includeInternal = env.getOptions().get(LSOptions.IncludeInternlSourcesInDefinitionSearch);
        return sourceSection != null && sourceSection.isAvailable() && (includeInternal || !sourceSection.getSource().isInternal());
    }

    private List<Location> findMatchingSymbols(TextDocumentSurrogate surrogate, String symbol) {
        List<Location> locations = new ArrayList<>();
        SourcePredicate srcPredicate = SourceUtils.createLanguageFilterPredicate(surrogate.getLanguageInfo());
        List<? extends SymbolInformation> docSymbols = symbolHandler.symbolWithEnteredContext(srcPredicate);
        for (SymbolInformation symbolInfo : docSymbols) {
            if (symbol.equals(symbolInfo.getName())) {
                locations.add(symbolInfo.getLocation());
            }
        }
        return locations;
    }
}
