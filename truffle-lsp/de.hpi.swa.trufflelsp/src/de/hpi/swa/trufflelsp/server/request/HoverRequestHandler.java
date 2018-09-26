package de.hpi.swa.trufflelsp.server.request;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.MarkedString;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

import com.oracle.truffle.api.TruffleException;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.TruffleInstrument.Env;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.ControlFlowException;
import com.oracle.truffle.api.nodes.ExecutableNode;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

import de.hpi.swa.trufflelsp.api.ContextAwareExecutorWrapper;
import de.hpi.swa.trufflelsp.server.utils.CoverageData;
import de.hpi.swa.trufflelsp.server.utils.CoverageEventNode;
import de.hpi.swa.trufflelsp.server.utils.NearestSectionsFinder;
import de.hpi.swa.trufflelsp.server.utils.NearestSectionsFinder.NearestSections;
import de.hpi.swa.trufflelsp.server.utils.SourceUtils;
import de.hpi.swa.trufflelsp.server.utils.SourceWrapper;
import de.hpi.swa.trufflelsp.server.utils.TextDocumentSurrogate;

public class HoverRequestHandler extends AbstractRequestHandler {

    private CompletionRequestHandler completionHandler;

    public HoverRequestHandler(Env env, Map<URI, TextDocumentSurrogate> uri2TextDocumentSurrogate, ContextAwareExecutorWrapper contextAwareExecutor, CompletionRequestHandler completionHandler) {
        super(env, uri2TextDocumentSurrogate, contextAwareExecutor);
        this.completionHandler = completionHandler;
    }

    public Hover hoverWithEnteredContext(URI uri, int line, int column) {
        TextDocumentSurrogate surrogate = uri2TextDocumentSurrogate.get(uri);
        SourceWrapper sourceWrapper = surrogate.getSourceWrapper();
        if (sourceWrapper.isParsingSuccessful() && surrogate.hasCoverageData()) {
            Source source = sourceWrapper.getSource();
            NearestSections nearestSections = NearestSectionsFinder.getNearestSections(source, env, SourceUtils.zeroBasedLineToOneBasedLine(line, source), column + 1);
            SourceSection containsSection = nearestSections.getContainsSourceSection();
            if (containsSection != null) {
                System.out.println("hover: SourceSection(" + containsSection.getCharacters() + ")");
                List<CoverageData> coverages = surrogate.getCoverageData(containsSection);
                if (coverages != null) {
                    return evalHoverInfos(coverages, containsSection, surrogate.getLangId());
                }
            }
        }
        return new Hover(new ArrayList<>());
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

        final LanguageInfo info = coverageData.getCoverageEventNode().getRootNode().getLanguageInfo();
        final Source inlineEvalSource = Source.newBuilder(textAtHoverPosition).name("inline eval").language(info.getId()).mimeType("content/unknown").cached(false).build();
        ExecutableNode executableNode = env.parseInline(inlineEvalSource, coverageData.getCoverageEventNode(), coverageData.getFrame());
        if (executableNode == null) {
            return null;
        }

        CoverageEventNode coverageEventNode = coverageData.getCoverageEventNode();
        coverageEventNode.insertOrReplaceChild(executableNode);
        Object evalResult = null;
        try {
            System.out.println("Trying coverage-based eval...");
            evalResult = executableNode.execute(coverageData.getFrame());
        } catch (Exception e) {
            if (!(e instanceof TruffleException) || !(e instanceof ControlFlowException)) {
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
    }

    private Hover trySignature(SourceSection hoverSection, String langId, TruffleObject evalResult) {
        String formattedSignature = completionHandler.getFormattedSignature(evalResult);
        if (formattedSignature != null) {
            List<Either<String, MarkedString>> contents = new ArrayList<>();
            contents.add(Either.forRight(new MarkedString(langId, formattedSignature)));
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
        if (!textAtHoverPosition.equals(evalResultObject.toString())) {
            String resultObjectString = evalResultObject instanceof String ? "\"" + evalResultObject + "\"" : evalResultObject.toString();
            contents.add(Either.forRight(new MarkedString(langId, resultObjectString)));
        }
        String detailText = completionHandler.createCompletionDetail(textAtHoverPosition, evalResultObject, langId);
        contents.add(Either.forLeft("meta-object: " + detailText));
        return contents;
    }
}
