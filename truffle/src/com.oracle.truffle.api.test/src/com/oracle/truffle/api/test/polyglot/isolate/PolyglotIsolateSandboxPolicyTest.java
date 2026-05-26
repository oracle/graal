/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.api.test.polyglot.isolate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.util.concurrent.Callable;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.impl.DefaultTruffleRuntime;
import com.oracle.truffle.api.test.TestAPIAccessor;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.SandboxPolicy;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

import com.oracle.truffle.api.TruffleOptions;
import com.oracle.truffle.api.test.polyglot.AbstractPolyglotTest;
import com.oracle.truffle.tck.tests.TruffleTestAssumptions;

public class PolyglotIsolateSandboxPolicyTest {

    // Workaround for issue GR-31197: Compiler tests are passing engine.DynamicCompilationThresholds
    // option from command line.
    private static String originalDynamicCompilationThresholds;

    @BeforeClass
    public static void assumeTestLanguageLibrary() {
        // needs to be provided by the test run
        Assume.assumeNotNull(System.getProperty("polyglot.engine.IsolateLibrary"));
        // Workaround for issue GR-31197: Compiler tests are passing
        // engine.DynamicCompilationThresholds option from command line.
        originalDynamicCompilationThresholds = System.getProperty("polyglot.engine.DynamicCompilationThresholds");
        if (originalDynamicCompilationThresholds != null) {
            System.getProperties().remove("polyglot.engine.DynamicCompilationThresholds");
        }
    }

    @AfterClass
    public static void tearDownClass() {
        if (originalDynamicCompilationThresholds != null) {
            System.setProperty("polyglot.engine.DynamicCompilationThresholds", originalDynamicCompilationThresholds);
        }
    }

    @Test
    public void testSandboxPolicyDefault() {
        if (!TruffleTestAssumptions.isExternalIsolate()) {
            try (Context context = Context.newBuilder("triste").//
                            option("engine.SpawnIsolate", "true").//
                            build()) {
                assertHostCallStackHeadRoomConfigured(context.getEngine(), 0L);
            }
            try (Context context = Context.newBuilder("triste").//
                            option("engine.SpawnIsolate", "true").//
                            option("engine.HostCallStackHeadRoom", "256KB").//
                            build()) {
                assertHostCallStackHeadRoomConfigured(context.getEngine(), 256L * 1024L);
            }
        }
    }

    @Test
    public void testSandboxPolicyConstrained() {
        TruffleTestAssumptions.assumeNoIsolateEncapsulation();
        AbstractPolyglotTest.assertFails(() -> {
            newConstrainedContextBuilder("triste").sandbox(SandboxPolicy.CONSTRAINED) //
                            .option("engine.MaxIsolateMemory", "128MB") //
                            .build();
        }, IllegalArgumentException.class, (iae) -> {
            assertTrue(iae.getMessage().contains("The isolated heap is not enabled, but isolate specific option engine.MaxIsolateMemory is set. "));
        });
    }

    @Test
    public void testSandboxPolicyIsolated() {
        try (Context context = newIsolatedContextBuilder("triste").sandbox(SandboxPolicy.ISOLATED) //
                        .option("engine.SpawnIsolate", "true").build()) {
            assertTrue(context.eval("triste", "test(isIsolated(),1)").asBoolean());
        }
        try (Context context = newIsolatedContextBuilder("triste").sandbox(SandboxPolicy.ISOLATED).build()) {
            assertTrue(context.eval("triste", "test(isIsolated(),1)").asBoolean());
        }
        try (Context context = newIsolatedContextBuilder("triste").sandbox(SandboxPolicy.ISOLATED) //
                        .option("engine.UntrustedCodeMitigation", "none").build()) {
            assertTrue(context.eval("triste", "test(isIsolated(),1)").asBoolean());
        }
        try (Context context = newIsolatedContextBuilder("triste").sandbox(SandboxPolicy.ISOLATED) //
                        .option("engine.UntrustedCodeMitigation", "software").build()) {
            assertTrue(context.eval("triste", "test(isIsolated(),1)").asBoolean());
            boolean hasCompilerMitigation = context.eval("triste", "test(hasCompilerMitigation(),1)").asBoolean();
            assertTrue(TruffleTestAssumptions.isExternalIsolate() != hasCompilerMitigation);
        }
        try (Context context = newIsolatedContextBuilder("triste").sandbox(SandboxPolicy.ISOLATED) //
                        .option("engine.UntrustedCodeMitigation", "hardware") //
                        .build()) {
            assertTrue(context.eval("triste", "test(isIsolated(),1)").asBoolean());
            assertFalse(context.eval("triste", "test(hasCompilerMitigation(),1)").asBoolean());
            boolean mpk = context.eval("triste", "test(isMemoryProtected(),1)").asBoolean();
            assertTrue(TruffleTestAssumptions.isExternalIsolate() != mpk);
        } catch (IllegalStateException ise) {
            if (!isUnsupportedMemoryProtectionException(ise)) {
                throw ise;
            }
        }
        AbstractPolyglotTest.assertFails(() -> {
            newIsolatedContextBuilder("triste").sandbox(SandboxPolicy.ISOLATED) //
                            .option("engine.UntrustedCodeMitigation", "hardware") //
                            .option("engine.IsolateMemoryProtection", "false") //
                            .build();
        }, IllegalArgumentException.class, (iae) -> {
            assertTrue(iae.getMessage().contains("The engine.UntrustedCodeMitigation is set to hardware, but the engine.IsolateMemoryProtection is set to false."));
        });
        AbstractPolyglotTest.assertFails(() -> {
            newIsolatedContextBuilder("triste").sandbox(SandboxPolicy.ISOLATED) //
                            .option("engine.HostCallStackHeadRoom", "64KB").build();
        }, IllegalArgumentException.class, (iae) -> {
            assertTrue(iae.getMessage().contains("The engine.HostCallStackHeadRoom option is set to 65536B, but must be set to at least 128KB."));
        });
        if (!TruffleTestAssumptions.isExternalIsolate()) {
            try (Context context = newIsolatedContextBuilder("triste").sandbox(SandboxPolicy.ISOLATED).build()) {
                assertHostCallStackHeadRoomConfigured(context.getEngine(), 128L * 1024L);
            }
            try (Context context = newIsolatedContextBuilder("triste").//
                            sandbox(SandboxPolicy.ISOLATED).//
                            option("engine.HostCallStackHeadRoom", "256KB").//
                            build()) {
                assertHostCallStackHeadRoomConfigured(context.getEngine(), 256L * 1024L);
            }
        }
        AbstractPolyglotTest.assertFails(() -> {
            newIsolatedContextBuilder("triste").sandbox(SandboxPolicy.ISOLATED) //
                            .option("engine.IsolateOption.Xmx", "1GB") //
                            .build();
        }, IllegalArgumentException.class, (iae) -> {
            assertTrue(iae.getMessage().contains("The option engine.IsolateOption can only be used up to the CONSTRAINED sandbox policy."));
        });
        AbstractPolyglotTest.assertFails(() -> {
            newIsolatedContextBuilder("triste").sandbox(SandboxPolicy.ISOLATED) //
                            .option("engine.SpawnIsolate", "false") //
                            .build();
        }, IllegalArgumentException.class, (iae) -> {
            assertTrue(iae.getMessage().contains("The engine.SpawnIsolate option is set to false, but must be set to true or to the set of languages that should be initialized."));
        });
    }

    @Test
    public void testSandboxPolicyUntrusted() {
        if (isInterpreterCallStackHeadRoomSupported()) {
            try (Context context = newUntrustedContextBuilder("triste").sandbox(SandboxPolicy.UNTRUSTED).build()) {
                assertTrue(context.eval("triste", "test(isIsolated(),1)").asBoolean());
                boolean hasCompilerMitigation = context.eval("triste", "test(hasCompilerMitigation(),1)").asBoolean();
                assertTrue(TruffleTestAssumptions.isExternalIsolate() != hasCompilerMitigation);
            }
            try (Context context = newUntrustedContextBuilder("triste").sandbox(SandboxPolicy.UNTRUSTED) //
                            .option("engine.UntrustedCodeMitigation", "software") //
                            .build()) {
                assertTrue(context.eval("triste", "test(isIsolated(),1)").asBoolean());
                boolean hasCompilerMitigation = context.eval("triste", "test(hasCompilerMitigation(),1)").asBoolean();
                assertTrue(TruffleTestAssumptions.isExternalIsolate() != hasCompilerMitigation);
            }
        }
        AbstractPolyglotTest.assertFails(() -> {
            newUntrustedContextBuilder("triste").sandbox(SandboxPolicy.UNTRUSTED) //
                            .option("engine.IsolateOption.Xmx", "1GB") //
                            .build();
        }, IllegalArgumentException.class, (iae) -> {
            assertTrue(iae.getMessage().contains("The option engine.IsolateOption can only be used up to the CONSTRAINED sandbox policy."));
        });
        if (TruffleTestAssumptions.isExternalIsolate()) {
            newUntrustedContextBuilder("triste").sandbox(SandboxPolicy.UNTRUSTED) //
                            .option("engine.UntrustedCodeMitigation", "none") //
                            .build().close();
            try {
                Context ctx = newUntrustedContextBuilder("triste").sandbox(SandboxPolicy.UNTRUSTED) //
                                .option("engine.UntrustedCodeMitigation", "hardware") //
                                .build();
                ctx.close();
            } catch (IllegalStateException ise) {
                if (!isUnsupportedMemoryProtectionException(ise)) {
                    throw ise;
                }
            }
        } else {
            AbstractPolyglotTest.assertFails(() -> {
                newUntrustedContextBuilder("triste").sandbox(SandboxPolicy.UNTRUSTED) //
                                .option("engine.UntrustedCodeMitigation", "none") //
                                .build();
            }, IllegalArgumentException.class, (iae) -> {
                assertTrue(iae.getMessage().contains("The engine.UntrustedCodeMitigation option is set to none, but must be set to software."));
            });
            AbstractPolyglotTest.assertFails(() -> {
                newUntrustedContextBuilder("triste").sandbox(SandboxPolicy.UNTRUSTED) //
                                .option("engine.UntrustedCodeMitigation", "hardware") //
                                .build();
            }, IllegalArgumentException.class, (iae) -> {
                assertTrue(iae.getMessage().contains("The engine.UntrustedCodeMitigation option is set to hardware, but must be set to software."));
            });
            try (Context context = newUntrustedContextBuilder("triste").sandbox(SandboxPolicy.UNTRUSTED).build()) {
                assertHostCallStackHeadRoomConfigured(context.getEngine(), 128L * 1024L);
            }
            try (Context context = newUntrustedContextBuilder("triste").//
                            sandbox(SandboxPolicy.UNTRUSTED).//
                            option("engine.HostCallStackHeadRoom", "256KB").//
                            build()) {
                assertHostCallStackHeadRoomConfigured(context.getEngine(), 256L * 1024L);
            }
        }
    }

    private static boolean isUnsupportedMemoryProtectionException(IllegalStateException illegalStateException) {
        String message = illegalStateException.getMessage();
        return message.contains("Setting the protection of the heap memory failed") || message.contains("Memory Protection not available");
    }

    @Test
    @SuppressWarnings("try")
    public void testSandboxPolicyIsolatedInstrumentOptions() {
        AbstractPolyglotTest.assertFails(() -> {
            newConstrainedContextBuilder("triste").sandbox(SandboxPolicy.ISOLATED) //
                            .option("engine.MaxIsolateMemory", "4GB") //
                            .build();
        }, PolyglotException.class, (iae) -> {
            assertTrue(iae.getMessage().contains("The sandbox.MaxCPUTime option is not set, but must be set."));
        });
        try (Context context = newConstrainedContextBuilder("triste").sandbox(SandboxPolicy.ISOLATED) //
                        .option("engine.MaxIsolateMemory", "4GB") //
                        .option("sandbox.MaxCPUTime", "100s") //
                        .build()) {
            assertTrue(context.eval("triste", "test(isIsolated(),1)").asBoolean());
        }
    }

    /**
     * A mirror of the method {@code SandboxInstrument#isInterpreterCallStackHeadRoomSupported()}
     * taking into account the possibility to run in polyglot isolate spawned form HotSpot.
     */
    private static boolean isInterpreterCallStackHeadRoomSupported() {
        Runtime.Version jdkVersion = Runtime.version();
        return TruffleTestAssumptions.isIsolateEncapsulation() || ((TruffleOptions.AOT && !(Truffle.getRuntime() instanceof DefaultTruffleRuntime)) && jdkVersion.feature() >= 23);
    }

    @Test
    @SuppressWarnings("try")
    public void testSandboxPolicyUntrustedInstrumentOptions() throws Exception {
        if (isInterpreterCallStackHeadRoomSupported()) {
            AbstractPolyglotTest.assertFails(() -> {
                newIsolatedContextBuilder("triste").sandbox(SandboxPolicy.UNTRUSTED) //
                                .option("sandbox.MaxASTDepth", "1000") //
                                .option("sandbox.MaxStackFrames", "50") //
                                .option("sandbox.MaxThreads", "1") //
                                .option("sandbox.MaxOutputStreamSize", "0B") //
                                .option("sandbox.MaxErrorStreamSize", "0B") //
                                .build();
            }, PolyglotException.class, (iae) -> {
                assertTrue(iae.getMessage().contains("The sandbox.MaxHeapMemory option is not set, but must be set."));
            });
            AbstractPolyglotTest.assertFails(() -> {
                newConstrainedContextBuilder("triste").sandbox(SandboxPolicy.UNTRUSTED) //
                                .option("engine.MaxIsolateMemory", "4GB") //
                                .option("sandbox.MaxHeapMemory", "1GB") //
                                .option("sandbox.MaxASTDepth", "1000") //
                                .option("sandbox.MaxStackFrames", "50") //
                                .option("sandbox.MaxThreads", "1") //
                                .option("sandbox.MaxOutputStreamSize", "0B") //
                                .option("sandbox.MaxErrorStreamSize", "0B") //
                                .build();
            }, PolyglotException.class, (iae) -> {
                assertTrue(iae.getMessage().contains("The sandbox.MaxCPUTime option is not set, but must be set."));
            });
        }
        AbstractPolyglotTest.assertFails(() -> {
            newIsolatedContextBuilder("triste").sandbox(SandboxPolicy.UNTRUSTED) //
                            .option("sandbox.MaxHeapMemory", "1GB") //
                            .option("sandbox.MaxStackFrames", "50") //
                            .option("sandbox.MaxThreads", "1") //
                            .option("sandbox.MaxOutputStreamSize", "0B") //
                            .option("sandbox.MaxErrorStreamSize", "0B") //
                            .build();
        }, PolyglotException.class, (iae) -> {
            assertTrue(iae.getMessage().contains("The sandbox.MaxASTDepth option is not set, but must be set."));
        });
        if (isInterpreterCallStackHeadRoomSupported()) {
            Callable<Context> untrustedContextWithoutMaxStackFrames = () -> newIsolatedContextBuilder("triste").sandbox(SandboxPolicy.UNTRUSTED) //
                            .option("sandbox.MaxHeapMemory", "1GB") //
                            .option("sandbox.MaxASTDepth", "1000") //
                            .option("sandbox.MaxThreads", "1") //
                            .option("sandbox.MaxOutputStreamSize", "0B") //
                            .option("sandbox.MaxErrorStreamSize", "0B") //
                            .build();
            untrustedContextWithoutMaxStackFrames.call().close();
            AbstractPolyglotTest.assertFails(() -> {
                newIsolatedContextBuilder("triste").sandbox(SandboxPolicy.UNTRUSTED) //
                                .option("sandbox.MaxHeapMemory", "1GB") //
                                .option("sandbox.MaxASTDepth", "1000") //
                                .option("sandbox.MaxStackFrames", "50") //
                                .option("sandbox.MaxOutputStreamSize", "0B") //
                                .option("sandbox.MaxErrorStreamSize", "0B") //
                                .build();
            }, PolyglotException.class, (iae) -> {
                assertTrue(iae.getMessage().contains("The sandbox.MaxThreads option is not set, but must be set."));
            });

            AbstractPolyglotTest.assertFails(() -> {
                newIsolatedContextBuilder("triste").sandbox(SandboxPolicy.UNTRUSTED) //
                                .option("sandbox.MaxHeapMemory", "1GB") //
                                .option("sandbox.MaxASTDepth", "1000") //
                                .option("sandbox.MaxStackFrames", "50") //
                                .option("sandbox.MaxThreads", "1") //
                                .option("sandbox.MaxErrorStreamSize", "0B") //
                                .build();
            }, PolyglotException.class, (iae) -> {
                assertTrue(iae.getMessage().contains("The sandbox.MaxOutputStreamSize option is not set, but must be set."));
            });
            AbstractPolyglotTest.assertFails(() -> {
                newIsolatedContextBuilder("triste").sandbox(SandboxPolicy.UNTRUSTED) //
                                .option("sandbox.MaxHeapMemory", "1GB") //
                                .option("sandbox.MaxASTDepth", "1000") //
                                .option("sandbox.MaxStackFrames", "50") //
                                .option("sandbox.MaxThreads", "1") //
                                .option("sandbox.MaxOutputStreamSize", "0B") //
                                .build();
            }, PolyglotException.class, (iae) -> {
                assertTrue(iae.getMessage().contains("The sandbox.MaxErrorStreamSize option is not set, but must be set."));
            });
            try (Context context = newConstrainedContextBuilder("triste").sandbox(SandboxPolicy.UNTRUSTED) //
                            .option("engine.MaxIsolateMemory", "4GB") //
                            .option("sandbox.MaxCPUTime", "100s") //
                            .option("sandbox.MaxStackFrames", "50") //
                            .option("sandbox.MaxHeapMemory", "1GB") //
                            .option("sandbox.MaxASTDepth", "1000") //
                            .option("sandbox.MaxThreads", "1") //
                            .option("sandbox.MaxOutputStreamSize", "0B") //
                            .option("sandbox.MaxErrorStreamSize", "0B") //
                            .build()) {
                assertTrue(context.eval("triste", "test(isIsolated(),1)").asBoolean());
            }
        }
    }

    @Test
    public void testLogging() {
        Context.Builder builder = Context.newBuilder("triste");
        String stringErr = executeLogging(builder);
        assertLogged(stringErr);
        builder = newConstrainedContextBuilder("triste").sandbox(SandboxPolicy.CONSTRAINED);
        stringErr = executeLogging(builder);
        assertLogged(stringErr);
        builder = newIsolatedContextBuilder("triste").sandbox(SandboxPolicy.ISOLATED);
        stringErr = executeLogging(builder);
        assertLogged(stringErr);
        if (isInterpreterCallStackHeadRoomSupported()) {
            builder = newUntrustedContextBuilder("triste").sandbox(SandboxPolicy.UNTRUSTED);
            stringErr = executeLogging(builder);
            assertNotLogged(stringErr);
        }
    }

    private static String executeLogging(Context.Builder builder) {
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        try (Context context = builder.err(err).build()) {
            context.eval("triste", "log(test(Logged message 1),1)");
            context.eval("triste", "log(test(Logged message 2),1)");
        }
        return err.toString();
    }

    private static void assertLogged(String stringErr) {
        // Messages should be logged
        assertTrue(stringErr.contains("Logged message 1"));
        assertTrue(stringErr.contains("Logged message 2"));
        // No warning should be printed
        assertFalse(stringErr.contains("Logging to context error output stream is not enabled for the sandbox policy ISOLATED."));
    }

    private static void assertNotLogged(String stringErr) {
        // No messages should be logged
        assertFalse(stringErr.contains("Logged message 1"));
        assertFalse(stringErr.contains("Logged message 2"));
        // But there should be a warning
        int index = stringErr.indexOf("Logging to context error output stream is not enabled for the sandbox policy UNTRUSTED.");
        assertTrue(index >= 0);
        // The warning should be printed once
        index = stringErr.indexOf("Logging to context error output stream is not enabled for the sandbox policy UNTRUSTED.", index + 1);
        assertFalse(index >= 0);
    }

    @Test
    public void testInterpreterRecursion() {
        if (isInterpreterCallStackHeadRoomSupported()) {
            int maxRecDepth1 = getMaxRecursionDepth(false, -1, null);
            int maxRecDepth2 = getMaxRecursionDepth(true, 100, null);
            int maxRecDepth3 = getMaxRecursionDepth(true, 1000, null);
            int maxRecDepth4 = getMaxRecursionDepth(true, 1000, 512 * 1024);
            assertTrue("Zero interpreter call stack headroom should give the deepest recursion.", maxRecDepth1 > maxRecDepth2);
            assertTrue("Higher MaxASTDepth should give higher interpreter call stack headroom which should further limit max recursion depth.", maxRecDepth2 > maxRecDepth3);
            assertTrue("Setting higher interpreter call stack headroom should limit max recursion depth even further.", maxRecDepth3 > maxRecDepth4);
            AbstractPolyglotTest.assertFails(() -> getMaxRecursionDepth(true, 1000, 1024), IllegalArgumentException.class, (iae) -> {
                assertTrue(iae.getMessage().contains("The engine.InterpreterCallStackHeadRoom option is set too low, minimum engine.InterpreterCallStackHeadRoom for sandbox.MaxASTDepth 1000 is"));
            });
        }
        if (!isInterpreterCallStackHeadRoomSupported()) {
            AbstractPolyglotTest.assertFails(() -> Context.newBuilder("triste").option("engine.InterpreterCallStackHeadRoom", "42B").build(), IllegalArgumentException.class, (iae) -> {
                String message = iae.getMessage();
                boolean incompatibleVm = message.contains("The engine.InterpreterCallStackHeadRoom option is set to a non-zero value, but the option is not supported on the current VM.");
                boolean incompatibleRuntime = message.contains(
                                "The engine.InterpreterCallStackHeadRoom option is set to a non-zero value, but the option is not supported on the fallback Truffle runtime.");
                assertTrue(incompatibleVm || incompatibleRuntime);
                assertTrue(!incompatibleRuntime || Truffle.getRuntime() instanceof DefaultTruffleRuntime);
            });
        } else {
            Context.newBuilder("triste").option("engine.InterpreterCallStackHeadRoom", "42B").build().close();
        }
    }

    private static void assertHostCallStackHeadRoomConfigured(Engine engine, long expected) {
        long headRoom = TestAPIAccessor.ISOLATE.getHostStackHeadRoom(engine);
        assertEquals("Expected engine.HostCallStackHeadRoom to default to " + expected + "B but was " + headRoom + "B.", expected, headRoom);
    }

    private static int getMaxRecursionDepth(boolean untrusted, int maxAstDepth, Integer interpreterCallStackHeadRoomInBytes) {
        Context.Builder builder = Context.newBuilder("triste");
        builder.option("engine.SpawnIsolate", "true");
        if (interpreterCallStackHeadRoomInBytes != null) {
            builder.option("engine.InterpreterCallStackHeadRoom", interpreterCallStackHeadRoomInBytes + "B");
        }
        if (untrusted) {
            builder.sandbox(SandboxPolicy.UNTRUSTED);
            builder.out(OutputStream.nullOutputStream());
            builder.err(OutputStream.nullOutputStream());
            builder.option("engine.MaxIsolateMemory", "4GB");
            builder.option("sandbox.MaxCPUTime", "100s");
            builder.option("sandbox.MaxHeapMemory", "1GB");
            builder.option("sandbox.MaxASTDepth", String.valueOf(maxAstDepth));
            builder.option("sandbox.MaxThreads", "1");
            builder.option("sandbox.MaxOutputStreamSize", "0B");
            builder.option("sandbox.MaxErrorStreamSize", "0B");
        }
        int low = 256;
        try (Context context = builder.build()) {
            // Depth 256 must not throw StackOverflowError.
            context.eval("triste", "interpreterRecursion(depth(" + low + "),1)");
            Integer high = null;
            do {
                int current;
                if (high == null) {
                    current = 2 * low;
                } else {
                    current = (low + high) / 2;
                }
                try {
                    context.eval("triste", "interpreterRecursion(depth(" + current + "),1)");
                    low = current;
                } catch (PolyglotException pe) {
                    if (!pe.isResourceExhausted()) {
                        throw pe;
                    }
                    if (!"Resource exhausted: Stack overflow".equals(pe.getMessage())) {
                        throw pe;
                    }
                    high = current;
                }

            } while (high == null || low + 1 < high);
        }
        return low;
    }

    private static Context.Builder newConstrainedContextBuilder(String... permittedLanguages) {
        Context.Builder builder = Context.newBuilder(permittedLanguages) //
                        .out(OutputStream.nullOutputStream()) //
                        .err(OutputStream.nullOutputStream());
        return builder;
    }

    private static Context.Builder newIsolatedContextBuilder(String... permittedLanguages) {
        Context.Builder builder = newConstrainedContextBuilder(permittedLanguages);
        builder.option("engine.MaxIsolateMemory", "4GB");
        builder.option("sandbox.MaxCPUTime", "100s");
        return builder;
    }

    private static Context.Builder newUntrustedContextBuilder(String... permittedLanguages) {
        Context.Builder builder = newIsolatedContextBuilder(permittedLanguages);
        builder.option("sandbox.MaxHeapMemory", "1GB");
        builder.option("sandbox.MaxASTDepth", "1000");
        builder.option("sandbox.MaxStackFrames", "50");
        builder.option("sandbox.MaxThreads", "1");
        builder.option("sandbox.MaxOutputStreamSize", "0B");
        builder.option("sandbox.MaxErrorStreamSize", "0B");
        return builder;
    }
}
