package de.hpi.swa.trufflelsp.server.request;

import java.io.PrintWriter;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import com.oracle.truffle.api.TruffleException;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

import de.hpi.swa.trufflelsp.api.ContextAwareExecutorWrapper;
import de.hpi.swa.trufflelsp.server.utils.NearestSectionsFinder;
import de.hpi.swa.trufflelsp.server.utils.NearestSectionsFinder.NearestSections;
import de.hpi.swa.trufflelsp.server.utils.SourceUtils;
import de.hpi.swa.trufflelsp.server.utils.SourceWrapper;
import de.hpi.swa.trufflelsp.server.utils.SurrogateMap;
import de.hpi.swa.trufflelsp.server.utils.TextDocumentSurrogate;

public class AbstractRequestHandler {

    protected final TruffleInstrument.Env env;
    protected final SurrogateMap surrogateMap;
    protected final PrintWriter err;
    protected final ContextAwareExecutorWrapper contextAwareExecutor;

    public AbstractRequestHandler(TruffleInstrument.Env env, SurrogateMap surrogateMap, ContextAwareExecutorWrapper contextAwareExecutor) {
        this.env = env;
        this.err = new PrintWriter(env.err(), true);
        this.surrogateMap = surrogateMap;
        this.contextAwareExecutor = contextAwareExecutor;
    }

    public InstrumentableNode findNodeAtCaret(TextDocumentSurrogate surrogate, int line, int character, Class<?>... tag) {
        SourceWrapper sourceWrapper = surrogate.getSourceWrapper();
        if (sourceWrapper.isParsingSuccessful()) {
            Source source = sourceWrapper.getSource();
            if (SourceUtils.isLineValid(line, source)) {
                int oneBasedLineNumber = SourceUtils.zeroBasedLineToOneBasedLine(line, source);
                NearestSections nearestSections = NearestSectionsFinder.getNearestSections(source, env, oneBasedLineNumber, character, tag);
                if (nearestSections.getNextSourceSection() != null) {
                    SourceSection nextNodeSection = nearestSections.getNextSourceSection();
                    if (nextNodeSection.getStartLine() == oneBasedLineNumber && nextNodeSection.getStartColumn() == character + 1) {
                        // nextNodeSection is directly before the caret, so we use that one
                        return nearestSections.getNextNode();
                    }
                }
                return nearestSections.getContainsNode();
            }
        }
        return null;
    }

    protected <T> T getFutureResultOrHandleExceptions(Future<T> future) {
        try {
            return future.get();
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RuntimeException && e instanceof TruffleException) {
                throw (RuntimeException) e.getCause();
            } else {
                e.printStackTrace(err);
            }
        } catch (InterruptedException e) {
        }
        return null;
    }
}
