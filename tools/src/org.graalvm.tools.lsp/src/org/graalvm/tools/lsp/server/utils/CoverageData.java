package org.graalvm.tools.lsp.server.utils;

import java.net.URI;

import com.oracle.truffle.api.frame.MaterializedFrame;

public class CoverageData {
    private final MaterializedFrame frame;
    private final CoverageEventNode coverageEventNode;
    private final URI coverageUri;

    public CoverageData(URI coverageUri, MaterializedFrame frame, CoverageEventNode coverageEventNode) {
        this.coverageUri = coverageUri;
        this.frame = frame;
        this.coverageEventNode = coverageEventNode;
    }

    public MaterializedFrame getFrame() {
        return frame;
    }

    public URI getCovarageUri() {
        return coverageUri;
    }

    public CoverageEventNode getCoverageEventNode() {
        return coverageEventNode;
    }
}
