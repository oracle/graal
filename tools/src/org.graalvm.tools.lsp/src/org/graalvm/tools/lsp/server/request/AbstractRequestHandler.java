package org.graalvm.tools.lsp.server.request;

import java.io.PrintWriter;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.graalvm.tools.lsp.api.ContextAwareExecutor;
import org.graalvm.tools.lsp.server.utils.CoverageData;
import org.graalvm.tools.lsp.server.utils.NearestSectionsFinder;
import org.graalvm.tools.lsp.server.utils.SourcePredicateBuilder;
import org.graalvm.tools.lsp.server.utils.SourceUtils;
import org.graalvm.tools.lsp.server.utils.SourceWrapper;
import org.graalvm.tools.lsp.server.utils.TextDocumentSurrogate;
import org.graalvm.tools.lsp.server.utils.TextDocumentSurrogateMap;
import org.graalvm.tools.lsp.server.utils.NearestSectionsFinder.NearestSections;

import com.oracle.truffle.api.Scope;
import com.oracle.truffle.api.TruffleException;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

public class AbstractRequestHandler {

    protected final TruffleInstrument.Env env;
    protected final TextDocumentSurrogateMap surrogateMap;
    protected final PrintWriter err;
    protected final ContextAwareExecutor contextAwareExecutor;

    public AbstractRequestHandler(TruffleInstrument.Env env, TextDocumentSurrogateMap surrogateMap, ContextAwareExecutor contextAwareExecutor) {
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
                NearestSections nearestSections = NearestSectionsFinder.findNearestSections(source, env, oneBasedLineNumber, character, true, tag);
                if (nearestSections.getNextSourceSection() != null) {
                    SourceSection nextNodeSection = nearestSections.getNextSourceSection();
                    if (nextNodeSection.getStartLine() == oneBasedLineNumber && nextNodeSection.getStartColumn() == character + 1) {
                        // nextNodeSection is directly before the caret, so we use that one
                        return nearestSections.getInstrumentableNextNode();
                    }
                }
                return nearestSections.getInstrumentableContainsNode();
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

    protected LinkedList<Scope> getScopesOuterToInner(TextDocumentSurrogate surrogate, InstrumentableNode node) {
        List<CoverageData> coverageData = surrogate.getCoverageData(((Node) node).getSourceSection());
        VirtualFrame frame = null;
        if (coverageData != null) {
            CoverageData data = coverageData.stream().findFirst().orElse(null);
            if (data != null) {
                frame = data.getFrame();
            }
        }
        Iterable<Scope> scopesInnerToOuter = env.findLocalScopes((Node) node, frame);
        LinkedList<Scope> scopesOuterToInner = new LinkedList<>();
        for (Scope scope : scopesInnerToOuter) {
            scopesOuterToInner.addFirst(scope);
        }
        return scopesOuterToInner;
    }

    protected SourcePredicateBuilder newDefaultSourcePredicateBuilder() {
        return SourcePredicateBuilder.newBuilder().excludeInternal(env.getOptions()).newestSource(surrogateMap);
    }
}
