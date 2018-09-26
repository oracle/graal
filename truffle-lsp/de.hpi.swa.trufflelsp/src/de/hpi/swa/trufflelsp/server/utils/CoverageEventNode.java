package de.hpi.swa.trufflelsp.server.utils;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Function;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.ExecutionEventNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.SourceSection;

public class CoverageEventNode extends ExecutionEventNode {

    private final URI coverageUri;
    private final Node instrumentedNode;
    private final SourceSection instrumentedSection;
    private final Function<URI, TextDocumentSurrogate> surrogateProvider;
    private Node child;

    public CoverageEventNode(SourceSection instrumentedSection, Node instrumentedNode, URI coverageUri, Function<URI, TextDocumentSurrogate> func) {
        this.instrumentedSection = instrumentedSection;
        this.instrumentedNode = instrumentedNode;
        this.coverageUri = coverageUri;
        this.surrogateProvider = func;
    }

    public Node getInstrumentedNode() {
        return instrumentedNode;
    }

    @Override
    protected void onEnter(VirtualFrame frame) {
        putSection2Uri(frame.materialize());
    }

    @TruffleBoundary
    private void putSection2Uri(MaterializedFrame frame) {
        URI sourceUri = instrumentedSection.getSource().getURI();
        if (!sourceUri.getScheme().equals("file")) {
            String name = instrumentedSection.getSource().getName();
            Path pathFromName = null;
            try {
                if (name != null) {
                    pathFromName = Paths.get(name);
                }
            } catch (InvalidPathException e) {
            }
            if (pathFromName == null || !Files.exists(pathFromName)) {
                return;
            }

            sourceUri = pathFromName.toUri();
        }

        TextDocumentSurrogate surrogate = surrogateProvider.apply(sourceUri);
        surrogate.addLocationCoverage(SourceLocation.from(instrumentedSection), new CoverageData(coverageUri, frame, this));
    }

    public void insertOrReplaceChild(Node node) {
        if (child != null) {
            child.replace(node);
        } else {
            child = node;
            insert(node);
        }
    }

    public void clearChild() {
        child.replace(new ExecutionEventNode() {
        });
    }
}
