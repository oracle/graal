package de.hpi.swa.trufflelsp.server;

import java.net.URI;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.lsp4j.Diagnostic;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Context.Builder;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Instrument;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;

import de.hpi.swa.trufflelsp.api.ContextAwareExecutorWrapper;
import de.hpi.swa.trufflelsp.api.ContextAwareExecutorWrapperRegistry;
import de.hpi.swa.trufflelsp.api.VirtualLanguageServerFileProvider;
import de.hpi.swa.trufflelsp.filesystem.LSPFileSystem;
import de.hpi.swa.trufflelsp.instrument.TruffleAdapterProvider;
import de.hpi.swa.trufflelsp.server.TruffleAdapter;

public abstract class TruffleLSPTest {

    private static AtomicInteger globalCounter;
    protected Map<URI, List<Diagnostic>> diagnostics = new LinkedHashMap<>();
    protected Engine engine;
    protected TruffleAdapter truffleAdapter;
    protected Context context;

    @BeforeClass
    public static void classSetup() {
        globalCounter = new AtomicInteger();
    }

    @Before
    public void setup() {
        engine = Engine.newBuilder().option("lspTestInstrument", "true").build();
        Instrument instrument = engine.getInstruments().get("lspTestInstrument");
        VirtualLanguageServerFileProvider lspFileProvider = instrument.lookup(VirtualLanguageServerFileProvider.class);

        Builder contextBuilder = Context.newBuilder();
        contextBuilder.allowAllAccess(true);
        contextBuilder.fileSystem(LSPFileSystem.newFullIOFileSystem(Paths.get("."), lspFileProvider));
        contextBuilder.engine(engine);
        context = contextBuilder.build();
        context.enter();

        ContextAwareExecutorWrapperRegistry registry = instrument.lookup(ContextAwareExecutorWrapperRegistry.class);
        ContextAwareExecutorWrapper executorWrapper = new ContextAwareExecutorWrapper() {

            public <T> Future<T> executeWithDefaultContext(Callable<T> taskWithResult) {
                try {
                    return CompletableFuture.completedFuture(taskWithResult.call());
                } catch (Exception e) {
                    if (e instanceof RuntimeException) {
                        throw (RuntimeException) e;
                    }
                    throw new RuntimeException(e);
                }
            }

            public <T> Future<T> executeWithNestedContext(Callable<T> taskWithResult) {
                try (Context newContext = contextBuilder.build()) {
                    newContext.enter();
                    try {
                        return CompletableFuture.completedFuture(taskWithResult.call());
                    } catch (Exception e) {
                        if (e instanceof RuntimeException) {
                            throw (RuntimeException) e;
                        }
                        throw new RuntimeException(e);
                    } finally {
                        newContext.leave();
                    }
                }
            }

            public void shutdown() {
            }
        };
        registry.register(executorWrapper);

        truffleAdapter = instrument.lookup(TruffleAdapterProvider.class).getTruffleAdapter();
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
