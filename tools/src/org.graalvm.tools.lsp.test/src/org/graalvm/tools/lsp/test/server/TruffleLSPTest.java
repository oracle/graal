package org.graalvm.tools.lsp.test.server;

import java.net.URI;
import java.nio.file.Paths;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Context.Builder;
import org.graalvm.tools.lsp.api.ContextAwareExecutor;
import org.graalvm.tools.lsp.api.ContextAwareExecutorRegistry;
import org.graalvm.tools.lsp.api.VirtualLanguageServerFileProvider;
import org.graalvm.tools.lsp.exceptions.DiagnosticsNotification;
import org.graalvm.tools.lsp.launcher.filesystem.LSPFileSystem;
import org.graalvm.tools.lsp.server.TruffleAdapter;
import org.graalvm.tools.lsp.test.instrument.TruffleAdapterProvider;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Instrument;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;

public abstract class TruffleLSPTest {

    private static AtomicInteger globalCounter;
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
        contextBuilder.fileSystem(LSPFileSystem.newReadOnlyFileSystem(Paths.get("."), lspFileProvider));
        contextBuilder.engine(engine);
        context = contextBuilder.build();
        context.enter();

        ContextAwareExecutorRegistry registry = instrument.lookup(ContextAwareExecutorRegistry.class);
        ContextAwareExecutor executorWrapper = new ContextAwareExecutor() {

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

            public <T> Future<T> executeWithNestedContext(Callable<T> taskWithResult, boolean cached) {
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

            public void resetContextCache() {
            }
        };
        registry.register(executorWrapper);

        truffleAdapter = instrument.lookup(TruffleAdapterProvider.class).getTruffleAdapter();
        truffleAdapter.initialize();
    }

    @After
    public void tearDown() {
        context.leave();
        context.close();
    }

    public URI createDummyFileUriForSL() {
        return URI.create("file:///tmp/truffle-lsp-test-file-" + globalCounter.incrementAndGet() + ".sl");
    }

    protected DiagnosticsNotification getDiagnosticsNotification(RuntimeException e) {
        if (e.getCause() instanceof DiagnosticsNotification) {
            return (DiagnosticsNotification) e.getCause();
        } else {
            throw e;
        }
    }

    protected Range range(int startLine, int startColumn, int endLine, int endColumn) {
        return new Range(new Position(startLine, startColumn), new Position(endLine, endColumn));
    }
}
