package de.hpi.swa.trufflelsp.test;

import java.net.URI;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.function.Supplier;

import org.eclipse.lsp4j.Diagnostic;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Context.Builder;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Instrument;
import org.junit.Before;
import org.junit.Test;

import de.hpi.swa.trufflelsp.NestedEvaluator;
import de.hpi.swa.trufflelsp.NestedEvaluatorRegistry;
import de.hpi.swa.trufflelsp.TruffleAdapter;
import de.hpi.swa.trufflelsp.VirtualLSPFileProvider;
import de.hpi.swa.trufflelsp.filesystem.LSPFileSystem;
import de.hpi.swa.trufflelsp.server.DiagnosticsPublisher;

public class TruffleLSPTests implements DiagnosticsPublisher {

    private Map<URI, Diagnostic[]> diagnostics = new LinkedHashMap<>();

    @Before
    public void setup() {

    }

    @Test
    public void openFile() throws InterruptedException, ExecutionException {
        Engine engine = Engine.newBuilder().option("lspTestInstrument", "true").build();
        Instrument instrument = engine.getInstruments().get("lspTestInstrument");
        VirtualLSPFileProvider lspFileProvider = instrument.lookup(VirtualLSPFileProvider.class);

        Builder contextBuilder = Context.newBuilder();
        contextBuilder.allowAllAccess(true);
        contextBuilder.fileSystem(LSPFileSystem.newFullIOFileSystem(Paths.get("."), lspFileProvider));
        contextBuilder.engine(engine);
        Context context = contextBuilder.build();
        context.enter();

        NestedEvaluatorRegistry registry = instrument.lookup(NestedEvaluatorRegistry.class);
        NestedEvaluator evaluator = new NestedEvaluator() {

            public <T> Future<T> executeWithDefaultContext(Supplier<T> taskWithResult) {
                return CompletableFuture.completedFuture(taskWithResult.get());
            }

            public <T> Future<T> executeWithNestedContext(Supplier<T> taskWithResult) {
                return CompletableFuture.completedFuture(doWithNestedContext(taskWithResult));
            }

            public <T> T doWithNestedContext(Supplier<T> taskWithResult) {
                try (Context newContext = contextBuilder.build()) {
                    newContext.enter();
                    try {
                        return taskWithResult.get();
                    } finally {
                        newContext.leave();
                    }
                }
            }

            public void shutdown() {
            }
        };
        registry.register(evaluator);

        TruffleAdapter truffleAdapter = instrument.lookup(TruffleAdapterProvider.class).geTruffleAdapter();
        truffleAdapter.setDiagnosticsPublisher(this);
        truffleAdapter.parse("3+3", "python", URI.create("file:///openFile")).get();
        context.leave();
        context.close();
    }

    public void addDiagnostics(URI uri, @SuppressWarnings("hiding") Diagnostic... diagnostics) {
        this.diagnostics.put(uri, diagnostics);
    }

    public void reportCollectedDiagnostics(String documentUri) {

    }

    public void reportCollectedDiagnostics() {

    }
}
