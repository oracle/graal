package de.hpi.swa.trufflelsp.server.request;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SymbolInformation;
import org.eclipse.lsp4j.SymbolKind;

import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.LoadSourceSectionEvent;
import com.oracle.truffle.api.instrumentation.LoadSourceSectionListener;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter.SourcePredicate;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.StandardTags.DeclarationTag;
import com.oracle.truffle.api.instrumentation.TruffleInstrument.Env;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.Node;

import de.hpi.swa.trufflelsp.api.ContextAwareExecutorWrapper;
import de.hpi.swa.trufflelsp.interop.ObjectStructures;
import de.hpi.swa.trufflelsp.interop.ObjectStructures.MessageNodes;
import de.hpi.swa.trufflelsp.server.utils.SourceUtils;
import de.hpi.swa.trufflelsp.server.utils.SurrogateMap;

public class SymbolRequestHandler extends AbstractRequestHandler {

    public SymbolRequestHandler(Env env, SurrogateMap surrogateMap, ContextAwareExecutorWrapper contextAwareExecutor) {
        super(env, surrogateMap, contextAwareExecutor);
    }

    public List<? extends SymbolInformation> documentSymbolWithEnteredContext(URI uri) {
        SourcePredicate srcPredicate = SourceUtils.createUriOrTruffleNameMatchingPredicate(uri);
        return symbolWithEnteredContext(src -> srcPredicate.test(src) && surrogateMap.isSourceNewestInSurrogate(src));
    }

    List<? extends SymbolInformation> symbolWithEnteredContext(SourcePredicate otherPedicate) {
        SourcePredicate srcPredicate = src -> otherPedicate.test(src) && surrogateMap.isSourceNewestInSurrogate(src);
        Set<SymbolInformation> symbolInformation = new LinkedHashSet<>();
        SourceSectionFilter filter = SourceSectionFilter.newBuilder().sourceIs(srcPredicate).tagIs(DeclarationTag.class).build();
        env.getInstrumenter().attachLoadSourceSectionListener(
                        filter,
                        new LoadSourceSectionListener() {

                            public void onLoad(LoadSourceSectionEvent event) {
                                Node node = event.getNode();
                                if (!(node instanceof InstrumentableNode)) {
                                    return;
                                }
                                InstrumentableNode instrumentableNode = (InstrumentableNode) node;
                                Object nodeObject = instrumentableNode.getNodeObject();
                                if (!(nodeObject instanceof TruffleObject)) {
                                    return;
                                }
                                Map<Object, Object> map = ObjectStructures.asMap(new MessageNodes(), (TruffleObject) nodeObject);
                                String name = map.get(DeclarationTag.NAME).toString();
                                SymbolKind kind = map.containsKey(DeclarationTag.KIND) ? declarationKindToSmybolKind(map.get(DeclarationTag.KIND)) : null;
                                Range range = SourceUtils.sourceSectionToRange(node.getSourceSection());
                                String container = map.containsKey(DeclarationTag.CONTAINER) ? map.get(DeclarationTag.CONTAINER).toString() : "";
                                URI fixedUri = SourceUtils.getOrFixFileUri(node.getSourceSection().getSource());
                                SymbolInformation si = new SymbolInformation(name, kind != null ? kind : SymbolKind.Null, new Location(fixedUri.toString(), range),
                                                container);
                                symbolInformation.add(si);
                            }

                            private SymbolKind declarationKindToSmybolKind(Object kind) {
                                if (kind == null) {
                                    return null;
                                }
                                Integer kindValue = (Integer) kind;
                                return SymbolKind.forValue(kindValue);
                            }
                        }, true).dispose();

        // Fallback: search for generic RootTags
        if (symbolInformation.isEmpty()) {
            env.getInstrumenter().attachLoadSourceSectionListener(
                            SourceSectionFilter.newBuilder().sourceIs(srcPredicate).tagIs(StandardTags.RootTag.class).build(),
                            new LoadSourceSectionListener() {

                                public void onLoad(LoadSourceSectionEvent event) {
                                    if (!event.getSourceSection().isAvailable()) {
                                        return;
                                    }

                                    Node node = event.getNode();
                                    SymbolKind kind = SymbolKind.Function;
                                    Range range = SourceUtils.sourceSectionToRange(node.getSourceSection());
                                    URI fixedUri = SourceUtils.getOrFixFileUri(node.getSourceSection().getSource());
                                    SymbolInformation si = new SymbolInformation(node.getRootNode().getName(), kind, new Location(fixedUri.toString(), range));
                                    symbolInformation.add(si);
                                }
                            }, true).dispose();
        }

        return new ArrayList<>(symbolInformation);
    }

    public List<? extends SymbolInformation> workspaceSymbolWithEnteredContext(@SuppressWarnings("unused") String query) {
        SourcePredicate srcPredicate = src -> !src.isInternal();
        return symbolWithEnteredContext(srcPredicate);
    }
}
