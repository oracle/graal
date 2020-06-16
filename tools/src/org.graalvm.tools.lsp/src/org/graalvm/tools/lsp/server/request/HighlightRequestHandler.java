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
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.graalvm.tools.lsp.server.ContextAwareExecutor;
import org.graalvm.tools.lsp.server.types.DocumentHighlight;
import org.graalvm.tools.lsp.server.types.DocumentHighlightKind;
import org.graalvm.tools.lsp.server.types.Range;
import org.graalvm.tools.lsp.server.utils.InteropUtils;
import org.graalvm.tools.lsp.server.utils.SourceUtils;
import org.graalvm.tools.lsp.server.utils.TextDocumentSurrogate;
import org.graalvm.tools.lsp.server.utils.TextDocumentSurrogateMap;

import com.oracle.truffle.api.Scope;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeVisitor;
import com.oracle.truffle.api.source.SourceSection;

public final class HighlightRequestHandler extends AbstractRequestHandler {

    public HighlightRequestHandler(TruffleInstrument.Env envMain, TruffleInstrument.Env env, TextDocumentSurrogateMap surrogateMap, ContextAwareExecutor executor) {
        super(envMain, env, surrogateMap, executor);
    }

    public List<? extends DocumentHighlight> highlightWithEnteredContext(URI uri, int line, int character) {
        TextDocumentSurrogate surrogate = surrogateMap.get(uri);
        InstrumentableNode nodeAtCaret = findNodeAtCaret(surrogate, line, character);
        if (nodeAtCaret != null) {
            if (nodeAtCaret.hasTag(StandardTags.ReadVariableTag.class) || nodeAtCaret.hasTag(StandardTags.WriteVariableTag.class)) {
                return findOtherReadOrWrites(surrogate, nodeAtCaret, line, character);
            }
        }
        return Collections.emptyList();
    }

    List<? extends DocumentHighlight> findOtherReadOrWrites(TextDocumentSurrogate surrogate, InstrumentableNode nodeAtCaret, int line, int character) {
        InteropUtils.VariableInfo[] caretVariables = InteropUtils.getNodeObjectVariables(nodeAtCaret);
        if (caretVariables.length > 0) {
            Set<String> variableNames = new HashSet<>();
            for (InteropUtils.VariableInfo varInfo : caretVariables) {
                if (contains(varInfo.getSourceSection(), line, character)) {
                    variableNames.add(varInfo.getName());
                }
            }
            LinkedList<Scope> scopesOuterToInner = getScopesOuterToInner(surrogate, nodeAtCaret);
            List<DocumentHighlight> highlights = new ArrayList<>();
            for (Scope scope : scopesOuterToInner) {
                Node scopeRoot = scope.getNode();
                if (scopeRoot != null) {
                    scopeRoot.accept(new NodeVisitor() {

                        @Override
                        public boolean visit(Node node) {
                            if (node instanceof InstrumentableNode) {
                                InstrumentableNode instrumentableNode = (InstrumentableNode) node;
                                if (instrumentableNode.hasTag(StandardTags.WriteVariableTag.class) ||
                                                instrumentableNode.hasTag(StandardTags.ReadVariableTag.class)) {
                                    InteropUtils.VariableInfo[] variables = InteropUtils.getNodeObjectVariables(instrumentableNode);
                                    assert variables.length > 0 : instrumentableNode.getClass().getCanonicalName() + ": " + instrumentableNode.toString();
                                    for (InteropUtils.VariableInfo varInfo : variables) {
                                        if (variableNames.contains(varInfo.getName())) {
                                            SourceSection sourceSection = varInfo.getSourceSection();
                                            if (SourceUtils.isValidSourceSection(sourceSection, env.getOptions())) {
                                                Range range = SourceUtils.sourceSectionToRange(sourceSection);
                                                DocumentHighlightKind kind = instrumentableNode.hasTag(StandardTags.WriteVariableTag.class) ? DocumentHighlightKind.Write : DocumentHighlightKind.Read;
                                                DocumentHighlight highlight = DocumentHighlight.create(range, kind);
                                                highlights.add(highlight);
                                            }
                                        }
                                    }
                                }
                            }
                            return true;
                        }
                    });
                }
            }
            return highlights;
        }
        return Collections.emptyList();
    }

    private static boolean contains(SourceSection sourceSection, int zeroBasedLineNumber, int zeroBasedColumnNumber) {
        int line = SourceUtils.zeroBasedLineToOneBasedLine(zeroBasedLineNumber, sourceSection.getSource());
        int column = SourceUtils.zeroBasedColumnToOneBasedColumn(zeroBasedLineNumber, line, zeroBasedColumnNumber, sourceSection.getSource());
        int startLine = sourceSection.getStartLine();
        int endLine = sourceSection.getEndLine();
        return (startLine < line || startLine == line && sourceSection.getStartColumn() <= column) &&
                        (line < endLine || line == endLine && column <= sourceSection.getEndColumn());
    }
}
