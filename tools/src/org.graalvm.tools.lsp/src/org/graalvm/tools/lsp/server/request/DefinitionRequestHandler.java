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
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.logging.Level;

import org.graalvm.tools.lsp.api.ContextAwareExecutor;
import org.graalvm.tools.lsp.instrument.LSPInstrument;
import org.graalvm.tools.lsp.server.types.Location;
import org.graalvm.tools.lsp.server.types.Range;
import org.graalvm.tools.lsp.server.types.SymbolInformation;
import org.graalvm.tools.lsp.server.utils.EvaluationResult;
import org.graalvm.tools.lsp.server.utils.InteropUtils;
import org.graalvm.tools.lsp.server.utils.SourceUtils;
import org.graalvm.tools.lsp.server.utils.TextDocumentSurrogate;
import org.graalvm.tools.lsp.server.utils.TextDocumentSurrogateMap;

import com.oracle.truffle.api.Scope;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter.SourcePredicate;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.TruffleInstrument.Env;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeVisitor;
import com.oracle.truffle.api.source.SourceSection;

public final class DefinitionRequestHandler extends AbstractRequestHandler {
    private static final TruffleLogger LOG = TruffleLogger.getLogger(LSPInstrument.ID, DefinitionRequestHandler.class);

    final SourceCodeEvaluator sourceCodeEvaluator;
    private final SymbolRequestHandler symbolHandler;

    public DefinitionRequestHandler(Env env, TextDocumentSurrogateMap surrogateMap, ContextAwareExecutor contextAwareExecutor, SourceCodeEvaluator evaluator,
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
            LOG.log(Level.FINER, "Definition node: {0} {1}", new Object[]{definitionSearchNode.getClass().getSimpleName(), definitionSearchSection});

            if (definitionSearchNode.hasTag(StandardTags.CallTag.class)) {
                return definitionOfCallTaggedNode(surrogate, definitionSearchNode, definitionSearchSection);
            } else if (definitionSearchNode.hasTag(StandardTags.ReadVariableTag.class)) {
                return definitionOfVariableNode(surrogate, definitionSearchNode);
            }
        }
        return Collections.emptyList();
    }

    private List<? extends Location> definitionOfVariableNode(TextDocumentSurrogate surrogate, InstrumentableNode definitionSearchNode) {
        String readVariableName = InteropUtils.getNodeObjectName(definitionSearchNode);
        if (readVariableName != null) {
            LinkedList<Scope> scopesOuterToInner = getScopesOuterToInner(surrogate, definitionSearchNode);
            List<Node> writeNodes = new ArrayList<>();
            for (Scope scope : scopesOuterToInner) {
                Node scopeRoot = scope.getNode();
                if (scopeRoot != null) {
                    scopeRoot.accept(new NodeVisitor() {

                        public boolean visit(Node node) {
                            if (node instanceof InstrumentableNode) {
                                if (((InstrumentableNode) node).hasTag(StandardTags.WriteVariableTag.class)) {
                                    String name = InteropUtils.getNodeObjectName((InstrumentableNode) node);
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
                if (SourceUtils.isValidSourceSection(sourceSection, env.getOptions())) {
                    Range range = SourceUtils.sourceSectionToRange(sourceSection);
                    URI definitionUri = SourceUtils.getOrFixFileUri(sourceSection.getSource());
                    locations.add(Location.create(definitionUri.toString(), range));
                }
                return locations;
            }
        }
        return Collections.emptyList();
    }

    private List<? extends Location> definitionOfCallTaggedNode(TextDocumentSurrogate surrogate, InstrumentableNode definitionSearchNode, SourceSection definitionSearchSection) {
        LOG.fine("Trying run-to-section eval...");

        SourceSectionFilter.Builder builder = SourceCodeEvaluator.createSourceSectionFilter(surrogate.getUri(), definitionSearchSection);
        SourceSectionFilter eventFilter = builder.tagIs(StandardTags.CallTag.class).build();
        SourceSectionFilter inputFilter = SourceSectionFilter.ANY;
        Callable<EvaluationResult> taskWithResult = () -> sourceCodeEvaluator.runToSectionAndEval(surrogate, definitionSearchSection, eventFilter, inputFilter);

        Future<EvaluationResult> future = contextAwareExecutor.executeWithNestedContext(taskWithResult, true);
        EvaluationResult evalResult = getFutureResultOrHandleExceptions(future);
        if (evalResult != null && evalResult.isEvaluationDone() && !evalResult.isError()) {
            SourceSection sourceSection = SourceUtils.findSourceLocation(env, evalResult.getResult(), surrogate.getLanguageInfo());
            List<Location> locations = new ArrayList<>();
            if (SourceUtils.isValidSourceSection(sourceSection, env.getOptions())) {
                Range range = SourceUtils.sourceSectionToRange(sourceSection);
                URI definitionUri = SourceUtils.getOrFixFileUri(sourceSection.getSource());
                locations.add(Location.create(definitionUri.toString(), range));
            }
            if (!locations.isEmpty()) {
                return locations;
            }
        }

        // Fallback: Static String-based name matching of symbols
        LOG.fine("Trying static symbol matching...");
        String definitionSearchSymbol = definitionSearchSection.getCharacters().toString();
        definitionSearchSymbol = InteropUtils.getNormalizedSymbolName(definitionSearchNode.getNodeObject(), definitionSearchSymbol);
        return findMatchingSymbols(surrogate, definitionSearchSymbol);
    }

    List<Location> findMatchingSymbols(TextDocumentSurrogate surrogate, String symbol) {
        List<Location> locations = new ArrayList<>();
        SourcePredicate predicate = newDefaultSourcePredicateBuilder().language(surrogate.getLanguageInfo()).build();
        Collection<? extends SymbolInformation> docSymbols = symbolHandler.symbolWithEnteredContext(predicate);
        for (SymbolInformation symbolInfo : docSymbols) {
            if (symbol.equals(symbolInfo.getName())) {
                locations.add(symbolInfo.getLocation());
            }
        }
        return locations;
    }
}
