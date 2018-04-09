package de.hpi.swa.trufflelsp;

import java.util.ArrayList;
import java.util.List;

import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.Source;

public class SourceWrapper {
    private List<Node> nodes = new ArrayList<>();
    private Source source;

    public SourceWrapper(Source source) {
        this.setSource(source);
    }

    public List<Node> getNodes() {
        return nodes;
    }

    public void setNodes(List<Node> nodes) {
        this.nodes = nodes;
    }

    public Source getSource() {
        return source;
    }

    public void setSource(Source source) {
        this.source = source;
    }
}
