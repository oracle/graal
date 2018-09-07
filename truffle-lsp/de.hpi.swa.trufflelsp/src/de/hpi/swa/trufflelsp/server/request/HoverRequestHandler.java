package de.hpi.swa.trufflelsp.server.request;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.MarkedString;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.instrumentation.TruffleInstrument.Env;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

import de.hpi.swa.trufflelsp.api.ContextAwareExecutorWrapper;
import de.hpi.swa.trufflelsp.server.utils.CoverageData;
import de.hpi.swa.trufflelsp.server.utils.NearestSectionsFinder;
import de.hpi.swa.trufflelsp.server.utils.SourceUtils;
import de.hpi.swa.trufflelsp.server.utils.SourceWrapper;
import de.hpi.swa.trufflelsp.server.utils.TextDocumentSurrogate;
import de.hpi.swa.trufflelsp.server.utils.NearestSectionsFinder.NearestSections;

public class HoverRequestHandler extends AbstractRequestHandler {

    private CompletionRequestHandler completionHandler;

    public HoverRequestHandler(Env env, Map<URI, TextDocumentSurrogate> uri2TextDocumentSurrogate, ContextAwareExecutorWrapper contextAwareExecutor, CompletionRequestHandler completionHandler) {
        super(env, uri2TextDocumentSurrogate, contextAwareExecutor);
        this.completionHandler = completionHandler;
    }

    public Hover hoverWithEnteredContext(URI uri, int line, int column) {
        List<Either<String, MarkedString>> contents = new ArrayList<>();

        TextDocumentSurrogate surrogate = uri2TextDocumentSurrogate.get(uri);
        SourceWrapper sourceWrapper = surrogate.getSourceWrapper();
        if (sourceWrapper.isParsingSuccessful()) {
            Source source = sourceWrapper.getSource();
            NearestSections nearestSections = NearestSectionsFinder.getNearestSections(source, env, SourceUtils.zeroBasedLineToOneBasedLine(line, source), column + 1);
            SourceSection containsSection = nearestSections.getContainsSourceSection();
            if (containsSection != null) {
                List<CoverageData> coverages = surrogate.getCoverageData(containsSection);
                if (coverages != null) {
                    String hoverInfo = extractHoverInfos(coverages, containsSection.getCharacters().toString(), surrogate.getLangId());
                    if (hoverInfo != null) {
                        contents.add(Either.forLeft(hoverInfo.toString()));
                    }
                }
            }
        }
        return new Hover(contents);
    }

    private String extractHoverInfos(List<CoverageData> coverages, String id, String langId) {
        for (CoverageData coverageData : coverages) {
            FrameSlot frameSlot = coverageData.getFrame().getFrameDescriptor().getSlots().stream().filter(slot -> slot.getIdentifier().equals(id)).findFirst().orElseGet(() -> null);
            if (frameSlot != null) {
                Object obj = coverageData.getFrame().getValue(frameSlot);
                return completionHandler.createCompletionDetail(null, obj, langId);
            }
        }
        return null;
    }
}
