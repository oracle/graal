package de.hpi.swa.trufflelsp.server.request;

import java.io.PrintWriter;
import java.net.URI;
import java.util.Map;

import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

import de.hpi.swa.trufflelsp.api.ContextAwareExecutorWrapper;
import de.hpi.swa.trufflelsp.server.utils.NearestSectionsFinder;
import de.hpi.swa.trufflelsp.server.utils.NearestSectionsFinder.NearestSections;
import de.hpi.swa.trufflelsp.server.utils.SourceUtils;
import de.hpi.swa.trufflelsp.server.utils.SourceWrapper;
import de.hpi.swa.trufflelsp.server.utils.TextDocumentSurrogate;

public class AbstractRequestHandler {

    protected final TruffleInstrument.Env env;
    protected final Map<URI, TextDocumentSurrogate> uri2TextDocumentSurrogate;
    protected final PrintWriter err;
    protected final ContextAwareExecutorWrapper contextAwareExecutor;

    public AbstractRequestHandler(TruffleInstrument.Env env, Map<URI, TextDocumentSurrogate> uri2TextDocumentSurrogate, ContextAwareExecutorWrapper contextAwareExecutor) {
        this.env = env;
        this.err = new PrintWriter(env.err(), true);
        this.uri2TextDocumentSurrogate = uri2TextDocumentSurrogate;
        this.contextAwareExecutor = contextAwareExecutor;
    }

    public InstrumentableNode findNodeAtCaret(TextDocumentSurrogate surrogate, int line, int character) {
        return findNodeAtCaret(surrogate, line, character, null);
    }

    public InstrumentableNode findNodeAtCaret(TextDocumentSurrogate surrogate, int line, int character, Class<?> tag) {
        SourceWrapper sourceWrapper = surrogate.getSourceWrapper();
        if (sourceWrapper.isParsingSuccessful()) {
            Source source = sourceWrapper.getSource();
            if (SourceUtils.isLineValid(line, source)) {
                int oneBasedLineNumber = SourceUtils.zeroBasedLineToOneBasedLine(line, source);
                NearestSections nearestSections = NearestSectionsFinder.getNearestSections(source, env, oneBasedLineNumber, character, tag);
                SourceSection definitionSearchSection = nearestSections.getContainsSourceSection();
                InstrumentableNode definitionSearchNode = nearestSections.getContainsNode();
                if (definitionSearchSection == null && nearestSections.getNextSourceSection() != null) {
                    SourceSection nextNodeSection = nearestSections.getNextSourceSection();
                    if (nextNodeSection.getStartLine() == oneBasedLineNumber && nextNodeSection.getStartColumn() == character + 1) {
                        // nextNodeSection is directly before the caret, so we use that one as
                        // fallback
                        return nearestSections.getNextNode();
                    }
                }
                return definitionSearchNode;
            }
        }
        return null;
    }
}
