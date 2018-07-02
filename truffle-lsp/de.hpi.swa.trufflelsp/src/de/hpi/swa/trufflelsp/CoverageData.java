package de.hpi.swa.trufflelsp;

import java.net.URI;

import com.oracle.truffle.api.frame.MaterializedFrame;

public class CoverageData {
    private final MaterializedFrame frame;
    private final InlineEvalEventNode inlineEvalEventNode;
    private final URI coverageUri;

    public CoverageData(URI coverageUri, MaterializedFrame frame, InlineEvalEventNode inlineEvalEventNode) {
        this.coverageUri = coverageUri;
        this.frame = frame;
        this.inlineEvalEventNode = inlineEvalEventNode;
    }

    public MaterializedFrame getFrame() {
        return frame;
    }

    public URI getCovarageUri() {
        return coverageUri;
    }

    public InlineEvalEventNode getInlineEvalEventNode() {
        return inlineEvalEventNode;
    }
}
