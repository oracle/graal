package org.graalvm.tools.lsp.server.request;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.logging.Level;

import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.MarkedString;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.graalvm.tools.lsp.api.ContextAwareExecutor;
import org.graalvm.tools.lsp.instrument.LSOptions;
import org.graalvm.tools.lsp.instrument.LSPInstrument;
import org.graalvm.tools.lsp.server.utils.CoverageData;
import org.graalvm.tools.lsp.server.utils.CoverageEventNode;
import org.graalvm.tools.lsp.server.utils.SourceUtils;
import org.graalvm.tools.lsp.server.utils.TextDocumentSurrogate;
import org.graalvm.tools.lsp.server.utils.TextDocumentSurrogateMap;

import com.oracle.truffle.api.TruffleException;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.StandardTags.CallTag;
import com.oracle.truffle.api.instrumentation.StandardTags.DeclarationTag;
import com.oracle.truffle.api.instrumentation.StandardTags.ExpressionTag;
import com.oracle.truffle.api.instrumentation.StandardTags.ReadVariableTag;
import com.oracle.truffle.api.instrumentation.StandardTags.RootTag;
import com.oracle.truffle.api.instrumentation.StandardTags.StatementTag;
import com.oracle.truffle.api.instrumentation.StandardTags.WriteVariableTag;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.instrumentation.TruffleInstrument.Env;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.ControlFlowException;
import com.oracle.truffle.api.nodes.ExecutableNode;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

public class HoverRequestHandler extends AbstractRequestHandler {
    private static final TruffleLogger LOG = TruffleLogger.getLogger(LSPInstrument.ID, HoverRequestHandler.class);

    private CompletionRequestHandler completionHandler;

    public HoverRequestHandler(Env env, TextDocumentSurrogateMap surrogateMap, ContextAwareExecutor contextAwareExecutor, CompletionRequestHandler completionHandler) {
        super(env, surrogateMap, contextAwareExecutor);
        this.completionHandler = completionHandler;
    }

    public Hover hoverWithEnteredContext(URI uri, int line, int column) {
        TextDocumentSurrogate surrogate = surrogateMap.get(uri);
        InstrumentableNode nodeAtCaret = findNodeAtCaret(surrogate, line, column);
        if (nodeAtCaret != null) {
            SourceSection hoverSection = ((Node) nodeAtCaret).getSourceSection();
            LOG.log(Level.FINER, "Hover: SourceSection({0})", hoverSection.getCharacters());
            if (surrogate.hasCoverageData()) {
                List<CoverageData> coverages = surrogate.getCoverageData(hoverSection);
                if (coverages != null) {
                    return evalHoverInfos(coverages, hoverSection, surrogate.getLangId());
                }
            } else if (env.getOptions().get(LSOptions.LanguageDeveloperMode)) {
                String sourceText = hoverSection.getCharacters().toString();
                List<Either<String, MarkedString>> contents = new ArrayList<>();
                contents.add(Either.forRight(new MarkedString(surrogate.getLangId(), sourceText)));
                contents.add(Either.forLeft("Node class: " + nodeAtCaret.getClass().getSimpleName()));
                contents.add(Either.forLeft("Tags: " + getTags(nodeAtCaret)));
                return new Hover(contents, SourceUtils.sourceSectionToRange(hoverSection));
            }
        }
        return new Hover(new ArrayList<>());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static String getTags(InstrumentableNode nodeAtCaret) {
        List<String> tags = new ArrayList<>();
        for (Class<Tag> tagClass : new Class[]{StatementTag.class, CallTag.class, RootTag.class, ExpressionTag.class, DeclarationTag.class, ReadVariableTag.class, WriteVariableTag.class}) {
            if (nodeAtCaret.hasTag(tagClass)) {
                tags.add(Tag.getIdentifier(tagClass));
            }
        }
        return tags.toString();
    }

    private Hover evalHoverInfos(List<CoverageData> coverages, SourceSection hoverSection, String langId) {
        String textAtHoverPosition = hoverSection.getCharacters().toString();
        for (CoverageData coverageData : coverages) {
            Hover frameSlotHover = tryFrameSlot(coverageData.getFrame(), textAtHoverPosition, langId, hoverSection);
            if (frameSlotHover != null) {
                return frameSlotHover;
            }

            Hover coverageDataHover = tryCoverageDataEvaluation(hoverSection, langId, textAtHoverPosition, coverageData);
            if (coverageDataHover != null) {
                return coverageDataHover;
            }
        }
        return new Hover(new ArrayList<>());
    }

    private Hover tryCoverageDataEvaluation(SourceSection hoverSection, String langId, String textAtHoverPosition, CoverageData coverageData) {
        InstrumentableNode instrumentable = ((InstrumentableNode) coverageData.getCoverageEventNode().getInstrumentedNode());
        if (!instrumentable.hasTag(StandardTags.ExpressionTag.class)) {
            return null;
        }

        Future<Hover> future = contextAwareExecutor.executeWithNestedContext(() -> {
            final LanguageInfo info = coverageData.getCoverageEventNode().getRootNode().getLanguageInfo();
            final Source inlineEvalSource = Source.newBuilder(info.getId(), textAtHoverPosition, "in-line eval (hover request)").cached(false).build();
            ExecutableNode executableNode = null;
            try {
                executableNode = env.parseInline(inlineEvalSource, coverageData.getCoverageEventNode(), coverageData.getFrame());
            } catch (Exception e) {
                if (!(e instanceof TruffleException)) {
                    e.printStackTrace(err);
                }
            }
            if (executableNode == null) {
                return new Hover(new ArrayList<>());
            }

            CoverageEventNode coverageEventNode = coverageData.getCoverageEventNode();
            coverageEventNode.insertOrReplaceChild(executableNode);
            Object evalResult = null;
            try {
                LOG.fine("Trying coverage-based eval...");
                evalResult = executableNode.execute(coverageData.getFrame());
            } catch (Exception e) {
                if (!((e instanceof TruffleException) || (e instanceof ControlFlowException))) {
                    e.printStackTrace(err);
                }
                return new Hover(new ArrayList<>());
            } finally {
                coverageEventNode.clearChild();
            }

            if (evalResult instanceof TruffleObject) {
                Hover signatureHover = trySignature(hoverSection, langId, (TruffleObject) evalResult);
                if (signatureHover != null) {
                    return signatureHover;
                }
            }
            return new Hover(createDefaultHoverInfos(textAtHoverPosition, evalResult, langId), SourceUtils.sourceSectionToRange(hoverSection));
        }, true);

        return getFutureResultOrHandleExceptions(future);
    }

    private Hover trySignature(SourceSection hoverSection, String langId, TruffleObject evalResult) {
        String formattedSignature = completionHandler.getFormattedSignature(evalResult);
        if (formattedSignature != null) {
            List<Either<String, MarkedString>> contents = new ArrayList<>();
            contents.add(Either.forRight(new MarkedString(langId, formattedSignature)));

            String documentation = completionHandler.getDocumentation(evalResult);
            if (documentation != null) {
                contents.add(Either.forLeft(documentation));
            }
            return new Hover(contents, SourceUtils.sourceSectionToRange(hoverSection));
        }

        return null;
    }

    private Hover tryFrameSlot(MaterializedFrame frame, String textAtHoverPosition, String langId, SourceSection hoverSection) {
        FrameSlot frameSlot = frame.getFrameDescriptor().getSlots().stream().filter(slot -> slot.getIdentifier().equals(textAtHoverPosition)).findFirst().orElseGet(() -> null);
        if (frameSlot != null) {
            Object frameSlotValue = frame.getValue(frameSlot);
            return new Hover(createDefaultHoverInfos(textAtHoverPosition, frameSlotValue, langId), SourceUtils.sourceSectionToRange(hoverSection));
        }
        return null;
    }

    private List<Either<String, MarkedString>> createDefaultHoverInfos(String textAtHoverPosition, Object evalResultObject, String langId) {
        List<Either<String, MarkedString>> contents = new ArrayList<>();
        contents.add(Either.forRight(new MarkedString(langId, textAtHoverPosition)));
        String result = evalResultObject != null ? evalResultObject.toString() : "";
        if (!textAtHoverPosition.equals(result)) {
            String resultObjectString = evalResultObject instanceof String ? "\"" + result + "\"" : result;
            contents.add(Either.forRight(new MarkedString(langId, resultObjectString)));
        }
        String detailText = completionHandler.createCompletionDetail(textAtHoverPosition, evalResultObject, langId);
        contents.add(Either.forLeft("meta-object: " + detailText));

        if (evalResultObject instanceof TruffleObject) {
            String documentation = completionHandler.getDocumentation((TruffleObject) evalResultObject);
            if (documentation != null) {
                contents.add(Either.forLeft(documentation));
            }
        }
        return contents;
    }
}
