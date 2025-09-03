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

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.List;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Context.Builder;
import org.graalvm.polyglot.io.IOAccess;
import org.graalvm.tools.lsp.server.ContextAwareExecutor;
import org.graalvm.tools.lsp.exceptions.DiagnosticsNotification;
import org.graalvm.tools.lsp.instrument.EnvironmentProvider;
import org.graalvm.tools.lsp.server.LSPFileSystem;
import org.graalvm.tools.lsp.server.TruffleAdapter;
import org.graalvm.tools.lsp.server.types.Range;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Instrument;
import org.junit.After;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public abstract class TruffleLSPTest {

    @Parameters(name = "useBytecode={0}")
    public static List<Boolean> getParameters() {
        return List.of(false, true);
    }

    @Parameter(0) public Boolean useBytecode;

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

    protected Engine engine;
    protected TruffleAdapter truffleAdapter;
    protected Context context;

    @Before
    public void setup() {
        engine = Engine.newBuilder().allowExperimentalOptions(true).build();
        Instrument instrument = engine.getInstruments().get("lsp");
        EnvironmentProvider envProvider = instrument.lookup(EnvironmentProvider.class);

        truffleAdapter = new TruffleAdapter(envProvider.getEnvironment(), true);

        Builder contextBuilder = Context.newBuilder();
        contextBuilder.allowAllAccess(true);
        contextBuilder.allowIO(IOAccess.newBuilder().fileSystem(LSPFileSystem.newReadOnlyFileSystem(truffleAdapter)).build());
        contextBuilder.engine(engine);
        contextBuilder.option("sl.UseBytecode", useBytecode.toString());
        context = contextBuilder.build();
        context.enter();

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
                    newContext.initialize("sl");
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
            public <T> Future<T> executeWithNestedContext(Callable<T> taskWithResult, int timeoutMillis, Callable<T> onTimeoutTask) {
                if (timeoutMillis <= 0) {
                    return executeWithNestedContext(taskWithResult, false);
                } else {
                    CompletableFuture<Future<T>> future = CompletableFuture.supplyAsync(() -> executeWithNestedContext(taskWithResult, false));
                    try {
                        return future.get(timeoutMillis, TimeUnit.MILLISECONDS);
                    } catch (TimeoutException e) {
                        future.cancel(true);
                        try {
                            return CompletableFuture.completedFuture(onTimeoutTask.call());
                        } catch (Exception timeoutTaskException) {
                            CompletableFuture<T> cf = new CompletableFuture<>();
                            cf.completeExceptionally(timeoutTaskException);
                            return cf;
                        }
                    } catch (InterruptedException | ExecutionException e) {
                        CompletableFuture<T> cf = new CompletableFuture<>();
                        cf.completeExceptionally(e);
                        return cf;
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

        truffleAdapter.register(envProvider.getEnvironment(), executorWrapper);
    }

    @After
    public void tearDown() {
        context.leave();
        context.close();
    }

    public URI createDummyFileUriForSL() {
        try {
            File dummy = File.createTempFile("truffle-lsp-test-file-", ".sl");
            dummy.deleteOnExit();
            return dummy.getCanonicalFile().toPath().toUri();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected DiagnosticsNotification getDiagnosticsNotification(ExecutionException e) {
        if (e.getCause() instanceof DiagnosticsNotification) {
            return (DiagnosticsNotification) e.getCause();
        } else {
            throw new RuntimeException(e);
        }
    }

    protected boolean rangeCheck(Range orig, Range range) {
        return rangeCheck(orig.getStart().getLine(), orig.getStart().getCharacter(), orig.getEnd().getLine(), orig.getEnd().getCharacter(), range);
    }

    protected boolean rangeCheck(int startLine, int startColumn, int endLine, int endColumn, Range range) {
        return startLine == range.getStart().getLine() && startColumn == range.getStart().getCharacter() && endLine == range.getEnd().getLine() && endColumn == range.getEnd().getCharacter();
    }
}
