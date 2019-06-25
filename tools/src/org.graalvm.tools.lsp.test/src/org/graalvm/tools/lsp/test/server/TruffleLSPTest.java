/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package org.graalvm.tools.lsp.test.server;

import java.net.URI;
import java.nio.file.Paths;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
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

    protected static final String PROG_OBJ = "" +
                    "function main() {\n" + // 0
                    "    abc();\n" +        // 1
                    "    x = abc();\n" +    // 2
                    "}\n" +                 // 3
                    "\n" +                  // 4
                    "function abc() {\n" +  // 5
                    "  obj = new();\n" +    // 6
                    "  obj.p = 1;\n" +      // 7
                    "  return obj;\n" +     // 8
                    "}\n";                  // 9

    protected static final String PROG_OBJ_NOT_CALLED = "" +
                    "function main() {\n" + // 0
                    "    x = abc();\n" +    // 1
                    "    return x;\n" +     // 2
                    "}\n" +                 // 3
                    "\n" +                  // 4
                    "function abc() {\n" +  // 5
                    "  obj = new();\n" +    // 6
                    "  obj.p = 1;\n" +      // 7
                    "  return obj;\n" +     // 8
                    "}\n" +                 // 9
                    "\n" +                  // 10
                    "function notCalled() {\n" + // 11
                    "  abc();\n" +          // 12
                    "  return abc();\n" +   // 13
                    "}\n";                  // 14

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
        engine = Engine.newBuilder().option("lspTestInstrument", "true").allowExperimentalOptions(true).build();
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

            @Override
            public <T> Future<T> executeWithDefaultContext(Callable<T> taskWithResult) {
                try {
                    return CompletableFuture.completedFuture(taskWithResult.call());
                } catch (Exception e) {
                    CompletableFuture<T> cf = new CompletableFuture<>();
                    cf.completeExceptionally(e);
                    return cf;
                }
            }

            @Override
            public <T> Future<T> executeWithNestedContext(Callable<T> taskWithResult, boolean cached) {
                try (Context newContext = contextBuilder.build()) {
                    newContext.enter();
                    try {
                        return CompletableFuture.completedFuture(taskWithResult.call());
                    } catch (Exception e) {
                        CompletableFuture<T> cf = new CompletableFuture<>();
                        cf.completeExceptionally(e);
                        return cf;
                    } finally {
                        newContext.leave();
                    }
                }
            }

            @Override
            public void shutdown() {
            }

            @Override
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

    protected DiagnosticsNotification getDiagnosticsNotification(ExecutionException e) {
        if (e.getCause() instanceof DiagnosticsNotification) {
            return (DiagnosticsNotification) e.getCause();
        } else {
            throw new RuntimeException(e);
        }
    }

    protected Range range(int startLine, int startColumn, int endLine, int endColumn) {
        return new Range(new Position(startLine, startColumn), new Position(endLine, endColumn));
    }
}
