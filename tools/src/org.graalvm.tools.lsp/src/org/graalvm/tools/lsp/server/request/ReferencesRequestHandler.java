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
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.lsp4j.DocumentHighlight;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Range;
import org.graalvm.tools.lsp.api.ContextAwareExecutor;
import org.graalvm.tools.lsp.interop.ObjectStructures.MessageNodes;
import org.graalvm.tools.lsp.server.utils.InteropUtils;
import org.graalvm.tools.lsp.server.utils.SourceUtils;
import org.graalvm.tools.lsp.server.utils.TextDocumentSurrogate;
import org.graalvm.tools.lsp.server.utils.TextDocumentSurrogateMap;

import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.LoadSourceSectionEvent;
import com.oracle.truffle.api.instrumentation.LoadSourceSectionListener;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter.SourcePredicate;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.TruffleInstrument.Env;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.SourceSection;

public final class ReferencesRequestHandler extends AbstractRequestHandler {

    private final HighlightRequestHandler highlightHandler;
    private final MessageNodes messageNodes;

    public ReferencesRequestHandler(Env env, TextDocumentSurrogateMap surrogateMap, ContextAwareExecutor contextAwareExecutor, HighlightRequestHandler highlightHandler, MessageNodes messageNodes) {
        super(env, surrogateMap, contextAwareExecutor);
        this.highlightHandler = highlightHandler;
        this.messageNodes = messageNodes;
    }

    public List<? extends Location> referencesWithEnteredContext(URI uri, int line, int character) {
        TextDocumentSurrogate surrogate = surrogateMap.get(uri);
        InstrumentableNode nodeAtCaret = findNodeAtCaret(surrogate, line, character, StandardTags.CallTag.class, StandardTags.ReadVariableTag.class, StandardTags.WriteVariableTag.class);
        if (nodeAtCaret != null) {
            if (nodeAtCaret.hasTag(StandardTags.CallTag.class)) {
                return referencesForCallTaggedNode(surrogate, nodeAtCaret);
            } else if (nodeAtCaret.hasTag(StandardTags.ReadVariableTag.class) ||
                            nodeAtCaret.hasTag(StandardTags.WriteVariableTag.class)) {
                return referencesForVariableNode(surrogate, nodeAtCaret);
            }
        }
        return Collections.emptyList();
    }

    private List<? extends Location> referencesForCallTaggedNode(TextDocumentSurrogate surrogate, InstrumentableNode nodeAtCaret) {
        List<Location> locations = new ArrayList<>();
        SourceSection sourceSection = ((Node) nodeAtCaret).getSourceSection();
        String symbol = sourceSection.getCharacters().toString();
        String normalizedSymbolToFindRef = InteropUtils.getNormalizedSymbolName(nodeAtCaret.getNodeObject(), symbol, messageNodes);
        SourcePredicate srcPredicate = newDefaultSourcePredicateBuilder().language(surrogate.getLanguageInfo()).build();

        env.getInstrumenter().attachLoadSourceSectionListener(
                        SourceSectionFilter.newBuilder().sourceIs(srcPredicate).tagIs(StandardTags.CallTag.class).build(),
                        new LoadSourceSectionListener() {

                            public void onLoad(LoadSourceSectionEvent event) {
                                if (!event.getSourceSection().isAvailable()) {
                                    return;
                                }

                                Node node = event.getNode();
                                String sectionText = node.getSourceSection().getCharacters().toString();
                                String normalizedSymbol = InteropUtils.getNormalizedSymbolName(((InstrumentableNode) node).getNodeObject(), sectionText, messageNodes);
                                if (normalizedSymbolToFindRef.equals(normalizedSymbol)) {
                                    Range range = SourceUtils.sourceSectionToRange(node.getSourceSection());
                                    URI fixedUri = SourceUtils.getOrFixFileUri(node.getSourceSection().getSource());
                                    locations.add(new Location(fixedUri.toString(), range));
                                }
                            }
                        }, true).dispose();
        return locations;
    }

    private List<? extends Location> referencesForVariableNode(TextDocumentSurrogate surrogate, InstrumentableNode nodeAtCaret) {
        List<? extends DocumentHighlight> readOrWrites = highlightHandler.findOtherReadOrWrites(surrogate, nodeAtCaret);
        return readOrWrites.stream() //
                        .map(highlight -> new Location(surrogate.getUri().toString(), highlight.getRange())) //
                        .collect(Collectors.toList());
    }

}
