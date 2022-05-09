package com.oracle.truffle.api.operation;

import java.util.List;
import java.util.function.Consumer;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.source.Source;

public abstract class OperationNodes {
    private final Consumer<? extends OperationBuilder> parse;
    @CompilationFinal(dimensions = 1) private OperationNode[] nodes;
    @CompilationFinal(dimensions = 1) Source[] sources;
    @CompilationFinal private boolean hasInstrumentation;

    protected OperationNodes(Consumer<? extends OperationBuilder> parse) {
        this.parse = parse;
    }

    public List<OperationNode> getNodes() {
        return List.of(nodes);
    }

    void setNodes(OperationNode[] nodes) {
        this.nodes = nodes;
    }

    public boolean hasSources() {
        return sources != null;
    }

    void setSources(Source[] sources) {
        this.sources = sources;
    }

    public boolean hasInstrumentation() {
        return hasInstrumentation;
    }

    private boolean checkNeedsWork(OperationConfig config) {
        if (config.isWithSource() && !hasSources()) {
            return true;
        }
        if (config.isWithInstrumentation() && !hasInstrumentation()) {
            return true;
        }
        return false;
    }

    public boolean updateConfiguration(OperationConfig config) {
        if (!checkNeedsWork(config)) {
            return false;
        }

        reparse(config);

        return true;
    }

    /**
     * Should look like:
     *
     * <pre>
     * BuilderImpl builder = new BuilderImpl(this, true, config);
     * parse.accept(builder);
     * </pre>
     */
    @SuppressWarnings({"rawtypes", "hiding"})
    protected abstract void reparseImpl(OperationConfig config, Consumer<?> parse, OperationNode[] nodes);

    void reparse(OperationConfig config) {
        reparseImpl(config, parse, nodes);
    }

    /**
     * Checks if the sources are present, and if not tries to reparse to get them.
     */
    final void ensureSources() {
        if (sources == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            reparse(OperationConfig.WITH_SOURCE);
        }
    }
}
