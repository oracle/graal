package de.hpi.swa.trufflelsp;

import java.net.URI;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.eclipse.lsp4j.Diagnostic;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Context.Builder;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Instrument;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;

import de.hpi.swa.trufflelsp.filesystem.LSPFileSystem;
import de.hpi.swa.trufflelsp.instrument.TruffleAdapterProvider;
import de.hpi.swa.trufflelsp.server.DiagnosticsPublisher;

public abstract class TruffleLSPTest implements DiagnosticsPublisher {

    private static AtomicInteger globalCounter;
    protected Map<URI, List<Diagnostic>> diagnostics = new LinkedHashMap<>();
    protected Engine engine;
    protected TruffleAdapter truffleAdapter;
    protected Context context;

    public void addDiagnostics(URI uri, Diagnostic... diagnosticsParam) {
        if (!diagnostics.containsKey(uri)) {
            diagnostics.put(uri, new ArrayList<>());
        }
        diagnostics.get(uri).addAll(Arrays.asList(diagnosticsParam));
    }

    public void reportCollectedDiagnostics(String documentUri) {
    }

    public void reportCollectedDiagnostics() {
    }

    @BeforeClass
    public static void classSetup() {
        globalCounter = new AtomicInteger();
    }

    @Before
    public void setup() {
        engine = Engine.newBuilder().option("lspTestInstrument", "true").build();
        Instrument instrument = engine.getInstruments().get("lspTestInstrument");
        VirtualLSPFileProvider lspFileProvider = instrument.lookup(VirtualLSPFileProvider.class);

        Builder contextBuilder = Context.newBuilder();
        contextBuilder.allowAllAccess(true);
        contextBuilder.fileSystem(LSPFileSystem.newFullIOFileSystem(Paths.get("."), lspFileProvider));
        contextBuilder.engine(engine);
        context = contextBuilder.build();
        context.enter();

        NestedEvaluatorRegistry registry = instrument.lookup(NestedEvaluatorRegistry.class);
        NestedEvaluator evaluator = new NestedEvaluator() {

            public <T> Future<T> executeWithDefaultContext(Supplier<T> taskWithResult) {
                return CompletableFuture.completedFuture(taskWithResult.get());
            }

            public <T> Future<T> executeWithNestedContext(Supplier<T> taskWithResult) {
                try (Context newContext = contextBuilder.build()) {
                    newContext.enter();
                    try {
                        return CompletableFuture.completedFuture(taskWithResult.get());
                    } finally {
                        newContext.leave();
                    }
                }
            }

            public void shutdown() {
            }
        };
        registry.register(evaluator);

        truffleAdapter = instrument.lookup(TruffleAdapterProvider.class).geTruffleAdapter();
        truffleAdapter.setDiagnosticsPublisher(this);
    }

    @After
    public void tearDown() {
        context.leave();
        context.close();
    }

    public URI createDummyFileUri() {
        return URI.create("file:///tmp/truffle-lsp-test-file-" + globalCounter.incrementAndGet());
    }
}
