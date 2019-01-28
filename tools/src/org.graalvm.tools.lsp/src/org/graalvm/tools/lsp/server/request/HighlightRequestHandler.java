package org.graalvm.tools.lsp.server.request;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import org.eclipse.lsp4j.DocumentHighlight;
import org.eclipse.lsp4j.DocumentHighlightKind;
import org.eclipse.lsp4j.Range;
import org.graalvm.tools.lsp.api.ContextAwareExecutor;
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

public class HighlightRequestHandler extends AbstractRequestHandler {

    public HighlightRequestHandler(TruffleInstrument.Env env, TextDocumentSurrogateMap surrogateMap, ContextAwareExecutor executor) {
        super(env, surrogateMap, executor);
    }

    public List<? extends DocumentHighlight> highlightWithEnteredContext(URI uri, int line, int character) {
        TextDocumentSurrogate surrogate = surrogateMap.get(uri);
        InstrumentableNode nodeAtCaret = findNodeAtCaret(surrogate, line, character);
        if (nodeAtCaret != null) {
            if (nodeAtCaret.hasTag(StandardTags.ReadVariableTag.class) || nodeAtCaret.hasTag(StandardTags.WriteVariableTag.class)) {
                return findOtherReadOrWrites(surrogate, nodeAtCaret);
            }
        }
        return Collections.emptyList();
    }

    List<? extends DocumentHighlight> findOtherReadOrWrites(TextDocumentSurrogate surrogate, InstrumentableNode nodeAtCaret) {
        String variableName = InteropUtils.getNodeObjectName(nodeAtCaret);
        if (variableName != null) {
            LinkedList<Scope> scopesOuterToInner = getScopesOuterToInner(surrogate, nodeAtCaret);
            List<DocumentHighlight> highlights = new ArrayList<>();
            for (Scope scope : scopesOuterToInner) {
                Node scopeRoot = scope.getNode();
                if (scopeRoot != null) {
                    scopeRoot.accept(new NodeVisitor() {

                        public boolean visit(Node node) {
                            if (node instanceof InstrumentableNode) {
                                InstrumentableNode instrumentableNode = (InstrumentableNode) node;
                                if (instrumentableNode.hasTag(StandardTags.WriteVariableTag.class) ||
                                                instrumentableNode.hasTag(StandardTags.ReadVariableTag.class)) {
                                    String name = InteropUtils.getNodeObjectName(instrumentableNode);
                                    if (name.equals(variableName)) {
                                        SourceSection sourceSection = node.getSourceSection();
                                        if (SourceUtils.isValidSourceSection(sourceSection, env.getOptions())) {
                                            Range range = SourceUtils.sourceSectionToRange(sourceSection);
                                            DocumentHighlightKind kind = instrumentableNode.hasTag(StandardTags.WriteVariableTag.class) ? DocumentHighlightKind.Write : DocumentHighlightKind.Read;
                                            DocumentHighlight highlight = new DocumentHighlight(range, kind);
                                            highlights.add(highlight);
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
}
