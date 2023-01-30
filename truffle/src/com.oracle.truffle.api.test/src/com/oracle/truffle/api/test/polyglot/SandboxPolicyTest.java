/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test.polyglot;

import com.oracle.truffle.api.Option;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import org.graalvm.options.OptionCategory;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionKey;
import org.graalvm.options.OptionMap;
import org.graalvm.options.OptionStability;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.EnvironmentAccess;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.SandboxPolicy;
import org.graalvm.polyglot.io.FileSystem;
import org.graalvm.polyglot.io.IOAccess;
import org.graalvm.polyglot.io.MessageEndpoint;
import org.graalvm.polyglot.io.MessageTransport;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

import static org.junit.Assert.assertTrue;

@RunWith(Parameterized.class)
public class SandboxPolicyTest {

    private static final String MISSING_ISOLATE_LIBRARY_MESSAGE = "No native isolate library found for language";
    private static final String UNSUPPORTED_ISOLATE_POLICY_MESSAGE = "The sandbox policy %s is not supported by the GraalVM community edition.";

    private static final Predicate<String> CLASS_FILTER_DISABLE_LOADING = (cn) -> false;

    private final Configuration configuration;

    public SandboxPolicyTest(Configuration configuration) {
        this.configuration = Objects.requireNonNull(configuration);
    }

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Configuration> createParameters() {
        boolean supportsIsolates = supportsPolyglotIsolates();
        boolean hasIsolateLibrary = supportsIsolates && tryToLoadIsolateLibrary();
        return List.of(
                        new Configuration(SandboxPolicy.TRUSTED, supportsIsolates, hasIsolateLibrary),
                        new Configuration(SandboxPolicy.RELAXED, supportsIsolates, hasIsolateLibrary),
                        new Configuration(SandboxPolicy.ISOLATED, supportsIsolates, hasIsolateLibrary),
                        new Configuration(SandboxPolicy.UNTRUSTED, supportsIsolates, hasIsolateLibrary));
    }

    private static boolean supportsPolyglotIsolates() {
        OptionDescriptors optionDescriptors = Engine.create().getOptions();
        return optionDescriptors.get("engine.SpawnIsolate") != null;
    }

    private static boolean tryToLoadIsolateLibrary() {
        try {
            Engine engine = Engine.newBuilder(TrustedLanguage.ID).option("engine.SpawnIsolate", "true").build();
            engine.close();
            return true;
        } catch (IllegalArgumentException iae) {
            if (iae.getMessage().startsWith(MISSING_ISOLATE_LIBRARY_MESSAGE)) {
                return false;
            } else {
                throw iae;
            }
        }
    }

    @Test
    @SuppressWarnings("try")
    public void testEngineStreams() {
        InputStream nullIn = InputStream.nullInputStream();
        OutputStream nullOut = OutputStream.nullOutputStream();
        try (Engine engine = Engine.newBuilder(RelaxedLanguage.ID).sandbox(configuration.sandboxPolicy).build()) {
            assertAtMost(SandboxPolicy.TRUSTED, configuration.sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            if (filterUnsupportedIsolate(configuration, iae)) {
                assertAtLeast(SandboxPolicy.RELAXED, configuration.sandboxPolicy);
            }
        }
        try (Engine engine = Engine.newBuilder(RelaxedLanguage.ID).sandbox(configuration.sandboxPolicy).out(nullOut).err(nullOut).build()) {
            assertAtMost(SandboxPolicy.TRUSTED, configuration.sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            if (filterUnsupportedIsolate(configuration, iae)) {
                assertAtLeast(SandboxPolicy.RELAXED, configuration.sandboxPolicy);
            }
        }
        try (Engine engine = Engine.newBuilder(RelaxedLanguage.ID).sandbox(configuration.sandboxPolicy).in(nullIn).err(nullOut).build()) {
            assertAtMost(SandboxPolicy.TRUSTED, configuration.sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            if (filterUnsupportedIsolate(configuration, iae)) {
                assertAtLeast(SandboxPolicy.RELAXED, configuration.sandboxPolicy);
            }
        }
        try (Engine engine = Engine.newBuilder(RelaxedLanguage.ID).sandbox(configuration.sandboxPolicy).in(nullIn).out(nullOut).build()) {
            assertAtMost(SandboxPolicy.TRUSTED, configuration.sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            if (filterUnsupportedIsolate(configuration, iae)) {
                assertAtLeast(SandboxPolicy.RELAXED, configuration.sandboxPolicy);
            }
        }
        try (Engine engine = Engine.newBuilder(RelaxedLanguage.ID).sandbox(configuration.sandboxPolicy).in(nullIn).out(nullOut).err(nullOut).build()) {
            assertAtMost(SandboxPolicy.UNTRUSTED, configuration.sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            if (filterUnsupportedIsolate(configuration, iae)) {
                throw iae;
            }
        }
    }

    @Test
    @SuppressWarnings("try")
    public void testEngineMessageTransport() {
        try (Engine engine = newUntrustedEngineBuilder(RelaxedLanguage.ID).sandbox(configuration.sandboxPolicy).serverTransport(new MockMessageTransport()).build()) {
            assertAtMost(SandboxPolicy.TRUSTED, configuration.sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            if (filterUnsupportedIsolate(configuration, iae)) {
                assertAtLeast(SandboxPolicy.RELAXED, configuration.sandboxPolicy);
            }
        }
        try (Engine engine = newUntrustedEngineBuilder(RelaxedLanguage.ID).sandbox(configuration.sandboxPolicy).build()) {
            assertAtMost(SandboxPolicy.UNTRUSTED, configuration.sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            if (filterUnsupportedIsolate(configuration, iae)) {
                throw iae;
            }
        }
    }

    @Test
    @SuppressWarnings("try")
    public void testContextPermittedLanguages() {
        try (Context context = newUntrustedContextBuilder().sandbox(configuration.sandboxPolicy).build()) {
            assertAtMost(SandboxPolicy.TRUSTED, configuration.sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            if (filterUnsupportedIsolate(configuration, iae)) {
                assertAtLeast(SandboxPolicy.RELAXED, configuration.sandboxPolicy);
            }
        }

        try (Context context = newUntrustedContextBuilder(RelaxedLanguage.ID).sandbox(configuration.sandboxPolicy).build()) {
            assertAtMost(SandboxPolicy.UNTRUSTED, configuration.sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            if (filterUnsupportedIsolate(configuration, iae)) {
                throw iae;
            }
        }

        try (Engine engine = newUntrustedEngineBuilder().sandbox(configuration.sandboxPolicy).build()) {
            assertAtMost(SandboxPolicy.TRUSTED, configuration.sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            if (filterUnsupportedIsolate(configuration, iae)) {
                assertAtLeast(SandboxPolicy.RELAXED, configuration.sandboxPolicy);
            }
        }

        try (Engine engine = newUntrustedEngineBuilder(RelaxedLanguage.ID).sandbox(configuration.sandboxPolicy).build()) {
            Context context = newUntrustedContextBuilder().engine(engine).sandbox(configuration.sandboxPolicy).build();
            context.close();
        } catch (IllegalArgumentException iae) {
            if (filterUnsupportedIsolate(configuration, iae)) {
                throw iae;
            }
        }
    }

    @Test
    public void testContextWithTrustedLanguage() {
        try (Context context = newUntrustedContextBuilder(TrustedLanguage.ID).sandbox(configuration.sandboxPolicy).build()) {
            try {
                context.initialize(TrustedLanguage.ID);
                assertAtMost(SandboxPolicy.TRUSTED, configuration.sandboxPolicy);
            } catch (IllegalArgumentException iae) {
                assertAtLeast(SandboxPolicy.RELAXED, configuration.sandboxPolicy);
            }
        } catch (IllegalArgumentException iae) {
            if (filterUnsupportedIsolate(configuration, iae)) {
                throw iae;
            }
        }
    }

    @Test
    public void testContextWithRelaxedLanguage() {
        try (Context context = newUntrustedContextBuilder(RelaxedLanguage.ID).sandbox(configuration.sandboxPolicy).build()) {
            try {
                context.initialize(RelaxedLanguage.ID);
                assertAtMost(SandboxPolicy.RELAXED, configuration.sandboxPolicy);
            } catch (IllegalArgumentException iae) {
                assertAtLeast(SandboxPolicy.ISOLATED, configuration.sandboxPolicy);
            }
        } catch (IllegalArgumentException iae) {
            if (filterUnsupportedIsolate(configuration, iae)) {
                throw iae;
            }
        }
    }

    @Test
    public void testContextWithIsolatedLanguage() {
        try (Context context = newUntrustedContextBuilder(IsolatedLanguage.ID).sandbox(configuration.sandboxPolicy).build()) {
            try {
                context.initialize(IsolatedLanguage.ID);
                assertAtMost(SandboxPolicy.ISOLATED, configuration.sandboxPolicy);
            } catch (IllegalArgumentException iae) {
                assertAtLeast(SandboxPolicy.UNTRUSTED, configuration.sandboxPolicy);
            }
        } catch (IllegalArgumentException iae) {
            if (filterUnsupportedIsolate(configuration, iae)) {
                throw iae;
            }
        }
    }

    @Test
    public void testContextWithUntrustedLanguage() {
        try (Context context = newUntrustedContextBuilder(UntrustedLanguage.ID).sandbox(configuration.sandboxPolicy).build()) {
            context.initialize(UntrustedLanguage.ID);
            assertAtMost(SandboxPolicy.UNTRUSTED, configuration.sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            if (filterUnsupportedIsolate(configuration, iae)) {
                throw iae;
            }
        }
    }

    @Test
    @SuppressWarnings("try")
    public void testContextWithTrustedInstrument() {
        try (Context context = newUntrustedContextBuilder(RelaxedLanguage.ID).option(TrustedInstrument.ID + ".Enable", "true").sandbox(configuration.sandboxPolicy).build()) {
            assertAtMost(SandboxPolicy.TRUSTED, configuration.sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            if (filterUnsupportedIsolate(configuration, iae)) {
                assertAtLeast(SandboxPolicy.RELAXED, configuration.sandboxPolicy);
            }
        }
    }

    @Test
    @SuppressWarnings("try")
    public void testContextWithRelaxedInstrument() {
        try (Context context = newUntrustedContextBuilder(RelaxedLanguage.ID).option(RelaxedInstrument.ID + ".Enable", "true").sandbox(configuration.sandboxPolicy).build()) {
            assertAtMost(SandboxPolicy.RELAXED, configuration.sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            if (filterUnsupportedIsolate(configuration, iae)) {
                assertAtLeast(SandboxPolicy.ISOLATED, configuration.sandboxPolicy);
            }
        }
    }

    @Test
    @SuppressWarnings("try")
    public void testContextWithIsolatedInstrument() {
        try (Context context = newUntrustedContextBuilder(RelaxedLanguage.ID).option(IsolatedInstrument.ID + ".Enable", "true").sandbox(configuration.sandboxPolicy).build()) {
            assertAtMost(SandboxPolicy.ISOLATED, configuration.sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            if (filterUnsupportedIsolate(configuration, iae)) {
                assertAtLeast(SandboxPolicy.UNTRUSTED, configuration.sandboxPolicy);
            }
        }
    }

    @Test
    @SuppressWarnings("try")
    public void testContextWithUntrustedInstrument() {
        try (Context context = newUntrustedContextBuilder(RelaxedLanguage.ID).option(UntrustedInstrument.ID + ".Enable", "true").sandbox(configuration.sandboxPolicy).build()) {
            assertAtMost(SandboxPolicy.UNTRUSTED, configuration.sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            if (filterUnsupportedIsolate(configuration, iae)) {
                throw iae;
            }
        }
    }

    @Test
    @SuppressWarnings("try")
    public void testLanguageOptions() {
        try (Context context = newUntrustedContextBuilder(UntrustedLanguage.ID).option(UntrustedLanguage.ID + ".RelaxedOption", "true").sandbox(configuration.sandboxPolicy).build()) {
            assertAtMost(SandboxPolicy.RELAXED, configuration.sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            if (filterUnsupportedIsolate(configuration, iae)) {
                assertAtLeast(SandboxPolicy.ISOLATED, configuration.sandboxPolicy);
            }
        }
        try (Context context = newUntrustedContextBuilder(UntrustedLanguage.ID).option(UntrustedLanguage.ID + ".UntrustedOption", "true").sandbox(configuration.sandboxPolicy).build()) {
            assertAtMost(SandboxPolicy.UNTRUSTED, configuration.sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            if (filterUnsupportedIsolate(configuration, iae)) {
                throw iae;
            }
        }
        try (Context context = newUntrustedContextBuilder(UntrustedLanguage.ID).option(UntrustedLanguage.ID + ".TrustedOption", "true").sandbox(configuration.sandboxPolicy).build()) {
            assertAtMost(SandboxPolicy.TRUSTED, configuration.sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            if (filterUnsupportedIsolate(configuration, iae)) {
                assertAtLeast(SandboxPolicy.RELAXED, configuration.sandboxPolicy);
            }
        }
        try (Context context = newUntrustedContextBuilder(UntrustedLanguage.ID).option(UntrustedLanguage.ID + ".RelaxedOptionMap", "true").sandbox(configuration.sandboxPolicy).build()) {
            assertAtMost(SandboxPolicy.RELAXED, configuration.sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            if (filterUnsupportedIsolate(configuration, iae)) {
                assertAtLeast(SandboxPolicy.ISOLATED, configuration.sandboxPolicy);
            }
        }
        try (Context context = newUntrustedContextBuilder(UntrustedLanguage.ID).option(UntrustedLanguage.ID + ".UntrustedOptionMap", "true").sandbox(configuration.sandboxPolicy).build()) {
            assertAtMost(SandboxPolicy.UNTRUSTED, configuration.sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            if (filterUnsupportedIsolate(configuration, iae)) {
                throw iae;
            }
        }
        try (Context context = newUntrustedContextBuilder(UntrustedLanguage.ID).option(UntrustedLanguage.ID + ".TrustedOptionMap", "true").sandbox(configuration.sandboxPolicy).build()) {
            assertAtMost(SandboxPolicy.TRUSTED, configuration.sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            if (filterUnsupportedIsolate(configuration, iae)) {
                assertAtLeast(SandboxPolicy.RELAXED, configuration.sandboxPolicy);
            }
        }
    }

    @Test
    @SuppressWarnings("try")
    public void testInstrumentOptions() {
        try (Context context = newUntrustedContextBuilder(UntrustedLanguage.ID).option(UntrustedInstrument.ID + ".Enable", "true").option(UntrustedInstrument.ID + ".RelaxedOption", "true").sandbox(
                        configuration.sandboxPolicy).build()) {
            assertAtMost(SandboxPolicy.RELAXED, configuration.sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            if (filterUnsupportedIsolate(configuration, iae)) {
                assertAtLeast(SandboxPolicy.ISOLATED, configuration.sandboxPolicy);
            }
        }
        try (Context context = newUntrustedContextBuilder(UntrustedLanguage.ID).option(UntrustedInstrument.ID + ".Enable", "true").option(UntrustedInstrument.ID + ".UntrustedOption", "true").sandbox(
                        configuration.sandboxPolicy).build()) {
            assertAtMost(SandboxPolicy.UNTRUSTED, configuration.sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            if (filterUnsupportedIsolate(configuration, iae)) {
                throw iae;
            }
        }
        try (Context context = newUntrustedContextBuilder(UntrustedLanguage.ID).option(UntrustedInstrument.ID + ".Enable", "true").option(UntrustedInstrument.ID + ".TrustedOption", "true").sandbox(
                        configuration.sandboxPolicy).build()) {
            assertAtMost(SandboxPolicy.TRUSTED, configuration.sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            if (filterUnsupportedIsolate(configuration, iae)) {
                assertAtLeast(SandboxPolicy.RELAXED, configuration.sandboxPolicy);
            }
        }
    }

    @Test
    @SuppressWarnings("try")
    public void testInstrumentContextOptions() {
        try (Context context = newUntrustedContextBuilder(UntrustedLanguage.ID).option(UntrustedInstrument.ID + ".Enable", "true").option(UntrustedInstrument.ID + ".ContextRelaxedOption",
                        "true").sandbox(
                                        configuration.sandboxPolicy).build()) {
            assertAtMost(SandboxPolicy.RELAXED, configuration.sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            if (filterUnsupportedIsolate(configuration, iae)) {
                assertAtLeast(SandboxPolicy.ISOLATED, configuration.sandboxPolicy);
            }
        }
        try (Context context = newUntrustedContextBuilder(UntrustedLanguage.ID).option(UntrustedInstrument.ID + ".Enable", "true").option(UntrustedInstrument.ID + ".ContextUntrustedOption",
                        "true").sandbox(
                                        configuration.sandboxPolicy).build()) {
            assertAtMost(SandboxPolicy.UNTRUSTED, configuration.sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            if (filterUnsupportedIsolate(configuration, iae)) {
                throw iae;
            }
        }
        try (Context context = newUntrustedContextBuilder(UntrustedLanguage.ID).option(UntrustedInstrument.ID + ".Enable", "true").option(UntrustedInstrument.ID + ".ContextTrustedOption",
                        "true").sandbox(
                                        configuration.sandboxPolicy).build()) {
            assertAtMost(SandboxPolicy.TRUSTED, configuration.sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            if (filterUnsupportedIsolate(configuration, iae)) {
                assertAtLeast(SandboxPolicy.RELAXED, configuration.sandboxPolicy);
            }
        }
    }

    @Test
    @SuppressWarnings("try")
    public void testContextAllAccess() {
        try (Context context = newUntrustedContextBuilder(RelaxedLanguage.ID).sandbox(configuration.sandboxPolicy).allowAllAccess(true).build()) {
            assertAtMost(SandboxPolicy.TRUSTED, configuration.sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            if (filterUnsupportedIsolate(configuration, iae)) {
                assertAtLeast(SandboxPolicy.RELAXED, configuration.sandboxPolicy);
            }
        }
        try (Context context = newUntrustedContextBuilder(RelaxedLanguage.ID).sandbox(configuration.sandboxPolicy).build()) {
            assertAtMost(SandboxPolicy.UNTRUSTED, configuration.sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            if (filterUnsupportedIsolate(configuration, iae)) {
                throw iae;
            }
        }
    }

    @Test
    @SuppressWarnings("try")
    public void testContextNativeAccess() {
        try (Context context = newUntrustedContextBuilder(RelaxedLanguage.ID).sandbox(configuration.sandboxPolicy).allowNativeAccess(true).build()) {
            assertAtMost(SandboxPolicy.TRUSTED, configuration.sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            if (filterUnsupportedIsolate(configuration, iae)) {
                assertAtLeast(SandboxPolicy.RELAXED, configuration.sandboxPolicy);
            }
        }
        try (Context context = newUntrustedContextBuilder(RelaxedLanguage.ID).sandbox(configuration.sandboxPolicy).build()) {
            assertAtMost(SandboxPolicy.UNTRUSTED, configuration.sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            if (filterUnsupportedIsolate(configuration, iae)) {
                throw iae;
            }
        }
    }

    @Test
    @SuppressWarnings("try")
    public void testContextHostClassLoading() {
        try (Context context = newUntrustedContextBuilder(RelaxedLanguage.ID).sandbox(configuration.sandboxPolicy).allowHostClassLoading(true).build()) {
            assertAtMost(SandboxPolicy.TRUSTED, configuration.sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            if (filterUnsupportedIsolate(configuration, iae)) {
                assertAtLeast(SandboxPolicy.RELAXED, configuration.sandboxPolicy);
            }
        }
        try (Context context = newUntrustedContextBuilder(RelaxedLanguage.ID).sandbox(configuration.sandboxPolicy).build()) {
            assertAtMost(SandboxPolicy.UNTRUSTED, configuration.sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            if (filterUnsupportedIsolate(configuration, iae)) {
                throw iae;
            }
        }
    }

    @Test
    @SuppressWarnings("try")
    public void testContextCreateProcess() {
        try (Context context = newUntrustedContextBuilder(RelaxedLanguage.ID).sandbox(configuration.sandboxPolicy).allowCreateProcess(true).build()) {
            assertAtMost(SandboxPolicy.TRUSTED, configuration.sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            if (filterUnsupportedIsolate(configuration, iae)) {
                assertAtLeast(SandboxPolicy.RELAXED, configuration.sandboxPolicy);
            }
        }
        try (Context context = newUntrustedContextBuilder(RelaxedLanguage.ID).sandbox(configuration.sandboxPolicy).build()) {
            assertAtMost(SandboxPolicy.UNTRUSTED, configuration.sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            if (filterUnsupportedIsolate(configuration, iae)) {
                throw iae;
            }
        }
    }

    @Test
    @SuppressWarnings("try")
    public void testContextSystemExit() {
        try (Context context = newUntrustedContextBuilder(RelaxedLanguage.ID).sandbox(configuration.sandboxPolicy).useSystemExit(true).build()) {
            assertAtMost(SandboxPolicy.TRUSTED, configuration.sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            if (filterUnsupportedIsolate(configuration, iae)) {
                assertAtLeast(SandboxPolicy.RELAXED, configuration.sandboxPolicy);
            }
        }
        try (Context context = newUntrustedContextBuilder(RelaxedLanguage.ID).sandbox(configuration.sandboxPolicy).build()) {
            assertAtMost(SandboxPolicy.UNTRUSTED, configuration.sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            if (filterUnsupportedIsolate(configuration, iae)) {
                throw iae;
            }
        }
    }

    @Test
    @SuppressWarnings("try")
    public void testContextStreams() {
        try (Engine engine = newUntrustedEngineBuilder(RelaxedLanguage.ID).sandbox(configuration.sandboxPolicy).build()) {
            try (Context context = newUntrustedContextBuilder(RelaxedLanguage.ID).sandbox(configuration.sandboxPolicy).engine(engine).in(System.in).build()) {
                assertAtMost(SandboxPolicy.TRUSTED, configuration.sandboxPolicy);
            } catch (IllegalArgumentException iae) {
                assertAtLeast(SandboxPolicy.RELAXED, configuration.sandboxPolicy);
            }
            try (Context context = newUntrustedContextBuilder(RelaxedLanguage.ID).sandbox(configuration.sandboxPolicy).engine(engine).out(System.out).build()) {
                assertAtMost(SandboxPolicy.TRUSTED, configuration.sandboxPolicy);
            } catch (IllegalArgumentException iae) {
                assertAtLeast(SandboxPolicy.RELAXED, configuration.sandboxPolicy);
            }
            try (Context context = newUntrustedContextBuilder(RelaxedLanguage.ID).sandbox(configuration.sandboxPolicy).engine(engine).err(System.err).build()) {
                assertAtMost(SandboxPolicy.TRUSTED, configuration.sandboxPolicy);
            } catch (IllegalArgumentException iae) {
                assertAtLeast(SandboxPolicy.RELAXED, configuration.sandboxPolicy);
            }
            Context context = newUntrustedContextBuilder(RelaxedLanguage.ID).sandbox(configuration.sandboxPolicy).engine(engine).build();
            context.close();
            context = newUntrustedContextBuilder(RelaxedLanguage.ID).sandbox(configuration.sandboxPolicy).engine(engine).in(InputStream.nullInputStream()).out(OutputStream.nullOutputStream()).err(
                            OutputStream.nullOutputStream()).build();
            context.close();
        } catch (IllegalArgumentException iae) {
            if (filterUnsupportedIsolate(configuration, iae)) {
                throw iae;
            }
        }
    }

    @Test
    @SuppressWarnings({"try", "deprecation"})
    public void testContextAllowIO() {
        try (Context context = newUntrustedContextBuilder(RelaxedLanguage.ID).sandbox(configuration.sandboxPolicy).allowIO(true).build()) {
            assertAtMost(SandboxPolicy.TRUSTED, configuration.sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            if (filterUnsupportedIsolate(configuration, iae)) {
                assertAtLeast(SandboxPolicy.RELAXED, configuration.sandboxPolicy);
            }
        }
        try (Context context = newUntrustedContextBuilder(RelaxedLanguage.ID).sandbox(configuration.sandboxPolicy).allowIO(false).build()) {
            assertAtMost(SandboxPolicy.UNTRUSTED, configuration.sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            if (filterUnsupportedIsolate(configuration, iae)) {
                throw iae;
            }
        }
    }

    @Test
    @SuppressWarnings({"try"})
    public void testContextIOAccess() throws IOException {
        try (Context context = newUntrustedContextBuilder(RelaxedLanguage.ID).sandbox(configuration.sandboxPolicy).allowIO(IOAccess.ALL).build()) {
            assertAtMost(SandboxPolicy.TRUSTED, configuration.sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            if (filterUnsupportedIsolate(configuration, iae)) {
                assertAtLeast(SandboxPolicy.RELAXED, configuration.sandboxPolicy);
            }
        }
        try (Context context = newUntrustedContextBuilder(RelaxedLanguage.ID).sandbox(configuration.sandboxPolicy).allowIO(IOAccess.newBuilder().allowHostFileAccess(true).build()).build()) {
            assertAtMost(SandboxPolicy.TRUSTED, configuration.sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            if (filterUnsupportedIsolate(configuration, iae)) {
                assertAtLeast(SandboxPolicy.RELAXED, configuration.sandboxPolicy);
            }
        }
        try (Context context = newUntrustedContextBuilder(RelaxedLanguage.ID).sandbox(configuration.sandboxPolicy).allowIO(IOAccess.newBuilder().allowHostSocketAccess(true).build()).build()) {
            assertAtMost(SandboxPolicy.TRUSTED, configuration.sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            if (filterUnsupportedIsolate(configuration, iae)) {
                assertAtLeast(SandboxPolicy.RELAXED, configuration.sandboxPolicy);
            }
        }
        try (Context context = newUntrustedContextBuilder(RelaxedLanguage.ID).sandbox(configuration.sandboxPolicy).allowIO(
                        IOAccess.newBuilder().fileSystem(FileSystem.newReadOnlyFileSystem(FileSystem.newDefaultFileSystem())).build()).build()) {
            assertAtMost(SandboxPolicy.TRUSTED, configuration.sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            if (filterUnsupportedIsolate(configuration, iae)) {
                assertAtLeast(SandboxPolicy.RELAXED, configuration.sandboxPolicy);
            }
        }
        try (Context context = newUntrustedContextBuilder(RelaxedLanguage.ID).sandbox(configuration.sandboxPolicy).allowIO(
                        IOAccess.newBuilder().fileSystem(new MemoryFileSystem("/tmp")).build()).build()) {
            assertAtMost(SandboxPolicy.UNTRUSTED, configuration.sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            if (filterUnsupportedIsolate(configuration, iae)) {
                throw iae;
            }
        }
        try (Context context = newUntrustedContextBuilder(RelaxedLanguage.ID).sandbox(configuration.sandboxPolicy).allowIO(IOAccess.NONE).build()) {
            assertAtMost(SandboxPolicy.UNTRUSTED, configuration.sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            if (filterUnsupportedIsolate(configuration, iae)) {
                throw iae;
            }
        }
    }

    @Test
    @SuppressWarnings({"try"})
    public void testContextEnvironmentAccess() {
        try (Context context = newUntrustedContextBuilder(RelaxedLanguage.ID).sandbox(configuration.sandboxPolicy).allowEnvironmentAccess(EnvironmentAccess.INHERIT).build()) {
            assertAtMost(SandboxPolicy.TRUSTED, configuration.sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            if (filterUnsupportedIsolate(configuration, iae)) {
                assertAtLeast(SandboxPolicy.RELAXED, configuration.sandboxPolicy);
            }
        }
        try (Context context = newUntrustedContextBuilder(RelaxedLanguage.ID).sandbox(configuration.sandboxPolicy).allowEnvironmentAccess(EnvironmentAccess.NONE).build()) {
            assertAtMost(SandboxPolicy.UNTRUSTED, configuration.sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            if (filterUnsupportedIsolate(configuration, iae)) {
                throw iae;
            }
        }
    }

    @Test
    @SuppressWarnings({"try", "deprecation"})
    public void testHostAccess() {
        try (Context context = newUntrustedContextBuilder(RelaxedLanguage.ID).sandbox(configuration.sandboxPolicy).allowHostAccess(true).build()) {
            assertAtMost(SandboxPolicy.TRUSTED, configuration.sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            if (filterUnsupportedIsolate(configuration, iae)) {
                assertAtLeast(SandboxPolicy.RELAXED, configuration.sandboxPolicy);
            }
        }
        HostAccess hostAccess = HostAccess.newBuilder().allowPublicAccess(true).build();
        try (Context context = newUntrustedContextBuilder(RelaxedLanguage.ID).sandbox(configuration.sandboxPolicy).allowHostAccess(hostAccess).build()) {
            assertAtMost(SandboxPolicy.TRUSTED, configuration.sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            if (filterUnsupportedIsolate(configuration, iae)) {
                assertAtLeast(SandboxPolicy.RELAXED, configuration.sandboxPolicy);
            }
        }
        hostAccess = HostAccess.newBuilder().allowAccessInheritance(true).build();
        try (Context context = newUntrustedContextBuilder(RelaxedLanguage.ID).sandbox(configuration.sandboxPolicy).allowHostAccess(hostAccess).build()) {
            assertAtMost(SandboxPolicy.TRUSTED, configuration.sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            if (filterUnsupportedIsolate(configuration, iae)) {
                assertAtLeast(SandboxPolicy.RELAXED, configuration.sandboxPolicy);
            }
        }
        hostAccess = HostAccess.newBuilder().allowMutableTargetMappings(HostAccess.MutableTargetMapping.EXECUTABLE_TO_JAVA_INTERFACE).build();
        try (Context context = newUntrustedContextBuilder(RelaxedLanguage.ID).sandbox(configuration.sandboxPolicy).allowHostAccess(hostAccess).build()) {
            assertAtMost(SandboxPolicy.TRUSTED, configuration.sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            if (filterUnsupportedIsolate(configuration, iae)) {
                assertAtLeast(SandboxPolicy.RELAXED, configuration.sandboxPolicy);
            }
        }
        hostAccess = HostAccess.newBuilder().allowAccessAnnotatedBy(HostAccess.Export.class).allowImplementationsAnnotatedBy(HostAccess.Implementable.class).allowMutableTargetMappings().build();
        try (Context context = newUntrustedContextBuilder(RelaxedLanguage.ID).sandbox(configuration.sandboxPolicy).allowHostAccess(hostAccess).build()) {
            assertAtMost(SandboxPolicy.RELAXED, configuration.sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            if (filterUnsupportedIsolate(configuration, iae)) {
                assertAtLeast(SandboxPolicy.ISOLATED, configuration.sandboxPolicy);
            }
        }
        try (Context context = newUntrustedContextBuilder(RelaxedLanguage.ID).sandbox(configuration.sandboxPolicy).allowHostAccess(null).build()) {
            assertAtMost(SandboxPolicy.UNTRUSTED, configuration.sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            if (filterUnsupportedIsolate(configuration, iae)) {
                throw iae;
            }
        }
        hostAccess = HostAccess.newBuilder().allowAccessAnnotatedBy(HostAccess.Export.class).allowImplementationsAnnotatedBy(HostAccess.Implementable.class).allowMutableTargetMappings().methodScoping(
                        true).build();
        try (Context context = newUntrustedContextBuilder(RelaxedLanguage.ID).sandbox(configuration.sandboxPolicy).allowHostAccess(hostAccess).build()) {
            assertAtMost(SandboxPolicy.UNTRUSTED, configuration.sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            if (filterUnsupportedIsolate(configuration, iae)) {
                throw iae;
            }
        }

        // HostAccess.NONE fails for sandboxPolicy >= RELAXED because it for compatibility reasons
        // allows mutable target mappings and has no method scoping
        try (Context context2 = newUntrustedContextBuilder(RelaxedLanguage.ID).sandbox(configuration.sandboxPolicy).allowHostAccess(HostAccess.NONE).build()) {
            assertAtMost(SandboxPolicy.TRUSTED, configuration.sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            if (filterUnsupportedIsolate(configuration, iae)) {
                assertAtLeast(SandboxPolicy.RELAXED, configuration.sandboxPolicy);
            }
        }
        hostAccess = HostAccess.newBuilder(HostAccess.NONE).allowMutableTargetMappings().build();
        try (Context context = newUntrustedContextBuilder(RelaxedLanguage.ID).sandbox(configuration.sandboxPolicy).allowHostAccess(hostAccess).build()) {
            assertAtMost(SandboxPolicy.RELAXED, configuration.sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            if (filterUnsupportedIsolate(configuration, iae)) {
                assertAtLeast(SandboxPolicy.ISOLATED, configuration.sandboxPolicy);
            }
        }
        hostAccess = HostAccess.newBuilder(HostAccess.NONE).allowMutableTargetMappings().methodScoping(true).build();
        try (Context context = newUntrustedContextBuilder(RelaxedLanguage.ID).sandbox(configuration.sandboxPolicy).allowHostAccess(hostAccess).build()) {
            assertAtMost(SandboxPolicy.UNTRUSTED, configuration.sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            if (filterUnsupportedIsolate(configuration, iae)) {
                throw iae;
            }
        }
    }

    private static Engine.Builder newUntrustedEngineBuilder(String... permittedLanguages) {
        return Engine.newBuilder(permittedLanguages).in(InputStream.nullInputStream()).out(OutputStream.nullOutputStream()).err(OutputStream.nullOutputStream());
    }

    private Context.Builder newUntrustedContextBuilder(String... permittedLanguages) {
        Context.Builder builder = Context.newBuilder(permittedLanguages).allowEnvironmentAccess(EnvironmentAccess.NONE).allowHostClassLookup(CLASS_FILTER_DISABLE_LOADING).in(
                        InputStream.nullInputStream()).out(OutputStream.nullOutputStream()).err(OutputStream.nullOutputStream());
        // Truffle isolate options are still experimental.
        builder.allowExperimentalOptions(true);
        if (configuration.hasIsolateLibrary) {
            builder.option("sandbox.MaxCPUTime", "100s");
            // GR-28085: ThreadMXBean.getThreadAllocatedBytes() is not supported or enabled by the
            // host VM but required for heap memory limit.
            // builder.option("sandbox.MaxHeapMemory", "1GB");
            builder.option("sandbox.MaxASTDepth", "1000");
            builder.option("sandbox.MaxStackFrames", "50");
            builder.option("sandbox.MaxThreads", "1");
        }
        return builder;
    }

    private static void assertAtLeast(SandboxPolicy expectedMinimalPolicy, SandboxPolicy actualPolicy) {
        assertTrue(expectedMinimalPolicy.ordinal() <= actualPolicy.ordinal());
    }

    private static void assertAtMost(SandboxPolicy expectedMaximalPolicy, SandboxPolicy actualPolicy) {
        assertTrue(expectedMaximalPolicy.ordinal() >= actualPolicy.ordinal());
    }

    private static boolean filterUnsupportedIsolate(Configuration configuration, IllegalArgumentException exception) {
        if (configuration.sandboxPolicy.ordinal() >= SandboxPolicy.ISOLATED.ordinal()) {
            if (!configuration.supportsIsolatedPolicy && exception.getMessage().startsWith(String.format(UNSUPPORTED_ISOLATE_POLICY_MESSAGE, configuration.sandboxPolicy))) {
                return false;
            } else if (!configuration.hasIsolateLibrary && exception.getMessage().startsWith(MISSING_ISOLATE_LIBRARY_MESSAGE)) {
                return false;
            }
        }
        return true;
    }

    static final class Configuration {
        final SandboxPolicy sandboxPolicy;
        final boolean supportsIsolatedPolicy;
        final boolean hasIsolateLibrary;

        Configuration(SandboxPolicy sandboxPolicy, boolean supportsIsolatedPolicy, boolean hasIsolateLibrary) {
            this.sandboxPolicy = Objects.requireNonNull(sandboxPolicy);
            this.supportsIsolatedPolicy = supportsIsolatedPolicy;
            this.hasIsolateLibrary = hasIsolateLibrary;
        }

        @Override
        public String toString() {
            return sandboxPolicy.name();
        }
    }

    @TruffleLanguage.Registration(id = TrustedLanguage.ID, name = TrustedLanguage.ID, characterMimeTypes = TrustedLanguage.MIME)
    static final class TrustedLanguage extends TruffleLanguage<TruffleLanguage.Env> {
        private static final String ID = "lang_sandbox_trusted";
        private static final String MIME = "text/x-" + ID;

        @Override
        protected Env createContext(Env env) {
            return env;
        }
    }

    @TruffleLanguage.Registration(id = RelaxedLanguage.ID, name = RelaxedLanguage.ID, characterMimeTypes = RelaxedLanguage.MIME, sandboxPolicy = SandboxPolicy.RELAXED)
    static final class RelaxedLanguage extends TruffleLanguage<TruffleLanguage.Env> {
        private static final String ID = "lang_sandbox_relaxed";
        private static final String MIME = "text/x-" + ID;

        @Override
        protected Env createContext(Env env) {
            return env;
        }
    }

    @TruffleLanguage.Registration(id = IsolatedLanguage.ID, name = IsolatedLanguage.ID, characterMimeTypes = IsolatedLanguage.MIME, sandboxPolicy = SandboxPolicy.ISOLATED)
    static final class IsolatedLanguage extends TruffleLanguage<TruffleLanguage.Env> {
        private static final String ID = "lang_sandbox_isolated";
        private static final String MIME = "text/x-" + ID;

        @Override
        protected Env createContext(Env env) {
            return env;
        }
    }

    @TruffleLanguage.Registration(id = UntrustedLanguage.ID, name = UntrustedLanguage.ID, characterMimeTypes = UntrustedLanguage.MIME, sandboxPolicy = SandboxPolicy.UNTRUSTED)
    static final class UntrustedLanguage extends TruffleLanguage<TruffleLanguage.Env> {
        private static final String ID = "lang_sandbox_untrusted";
        private static final String MIME = "text/x-" + ID;

        @Option(category = OptionCategory.EXPERT, stability = OptionStability.STABLE, help = "Option allowed only in trusted policy")//
        static final OptionKey<Boolean> TrustedOption = new OptionKey<>(false);

        @Option(category = OptionCategory.EXPERT, stability = OptionStability.STABLE, help = "Option allowed only in trusted and relaxed policy", sandbox = SandboxPolicy.RELAXED)//
        static final OptionKey<Boolean> RelaxedOption = new OptionKey<>(false);

        @Option(category = OptionCategory.EXPERT, stability = OptionStability.STABLE, help = "Option allowed in all policies", sandbox = SandboxPolicy.UNTRUSTED)//
        static final OptionKey<Boolean> UntrustedOption = new OptionKey<>(false);

        @Option(category = OptionCategory.EXPERT, stability = OptionStability.STABLE, help = "OptionMap allowed only in trusted policy")//
        static final OptionKey<OptionMap<String>> TrustedOptionMap = OptionKey.mapOf(String.class);

        @Option(category = OptionCategory.EXPERT, stability = OptionStability.STABLE, help = "OptionMap allowed only in trusted and relaxed policy", sandbox = SandboxPolicy.RELAXED)//
        static final OptionKey<OptionMap<String>> RelaxedOptionMap = OptionKey.mapOf(String.class);

        @Option(category = OptionCategory.EXPERT, stability = OptionStability.STABLE, help = "OptionMap allowed in all policies", sandbox = SandboxPolicy.UNTRUSTED)//
        static final OptionKey<OptionMap<String>> UntrustedOptionMap = OptionKey.mapOf(String.class);

        @Override
        protected Env createContext(Env env) {
            return env;
        }

        @Override
        protected OptionDescriptors getOptionDescriptors() {
            return new UntrustedLanguageOptionDescriptors();
        }
    }

    @TruffleInstrument.Registration(id = TrustedInstrument.ID, name = TrustedInstrument.ID)
    static final class TrustedInstrument extends TruffleInstrument {

        private static final String ID = "tool_sandbox_trusted";

        @Option(category = OptionCategory.EXPERT, stability = OptionStability.STABLE, help = "Enables instrument", sandbox = SandboxPolicy.UNTRUSTED)//
        static final OptionKey<Boolean> Enable = new OptionKey<>(false);

        @Override
        protected void onCreate(Env env) {
        }

        @Override
        protected OptionDescriptors getOptionDescriptors() {
            return new TrustedInstrumentOptionDescriptors();
        }
    }

    @TruffleInstrument.Registration(id = RelaxedInstrument.ID, name = RelaxedInstrument.ID, sandboxPolicy = SandboxPolicy.RELAXED)
    static final class RelaxedInstrument extends TruffleInstrument {

        private static final String ID = "tool_sandbox_relaxed";

        @Option(category = OptionCategory.EXPERT, stability = OptionStability.STABLE, help = "Enables instrument", sandbox = SandboxPolicy.UNTRUSTED)//
        static final OptionKey<Boolean> Enable = new OptionKey<>(false);

        @Override
        protected void onCreate(Env env) {
        }

        @Override
        protected OptionDescriptors getOptionDescriptors() {
            return new RelaxedInstrumentOptionDescriptors();
        }
    }

    @TruffleInstrument.Registration(id = IsolatedInstrument.ID, name = IsolatedInstrument.ID, sandboxPolicy = SandboxPolicy.ISOLATED)
    static final class IsolatedInstrument extends TruffleInstrument {

        private static final String ID = "tool_sandbox_isolated";

        @Option(category = OptionCategory.EXPERT, stability = OptionStability.STABLE, help = "Enables instrument", sandbox = SandboxPolicy.UNTRUSTED)//
        static final OptionKey<Boolean> Enable = new OptionKey<>(false);

        @Override
        protected void onCreate(Env env) {
        }

        @Override
        protected OptionDescriptors getOptionDescriptors() {
            return new IsolatedInstrumentOptionDescriptors();
        }
    }

    @TruffleInstrument.Registration(id = UntrustedInstrument.ID, name = UntrustedInstrument.ID, sandboxPolicy = SandboxPolicy.UNTRUSTED)
    static final class UntrustedInstrument extends TruffleInstrument {

        private static final String ID = "tool_sandbox_untrusted";

        @Option(category = OptionCategory.EXPERT, stability = OptionStability.STABLE, help = "Enables instrument", sandbox = SandboxPolicy.UNTRUSTED)//
        static final OptionKey<Boolean> Enable = new OptionKey<>(false);

        @Option(category = OptionCategory.EXPERT, stability = OptionStability.STABLE, help = "Option allowed only in trusted policy")//
        static final OptionKey<Boolean> TrustedOption = new OptionKey<>(false);

        @Option(category = OptionCategory.EXPERT, stability = OptionStability.STABLE, help = "Option allowed only in trusted and relaxed policy", sandbox = SandboxPolicy.RELAXED)//
        static final OptionKey<Boolean> RelaxedOption = new OptionKey<>(false);

        @Option(category = OptionCategory.EXPERT, stability = OptionStability.STABLE, help = "Option allowed in all policies", sandbox = SandboxPolicy.UNTRUSTED)//
        static final OptionKey<Boolean> UntrustedOption = new OptionKey<>(false);

        @Override
        protected void onCreate(Env env) {
        }

        @Override
        protected OptionDescriptors getOptionDescriptors() {
            return new UntrustedInstrumentOptionDescriptors();
        }

        @Override
        protected OptionDescriptors getContextOptionDescriptors() {
            return new UntrustedInstrumentContextOptionsOptionDescriptors();
        }
    }

    @Option.Group(UntrustedInstrument.ID)
    static final class UntrustedInstrumentContextOptions {
        @Option(category = OptionCategory.EXPERT, stability = OptionStability.STABLE, help = "Option allowed only in trusted policy")//
        static final OptionKey<Boolean> ContextTrustedOption = new OptionKey<>(false);

        @Option(category = OptionCategory.EXPERT, stability = OptionStability.STABLE, help = "Option allowed only in trusted and relaxed policy", sandbox = SandboxPolicy.RELAXED)//
        static final OptionKey<Boolean> ContextRelaxedOption = new OptionKey<>(false);

        @Option(category = OptionCategory.EXPERT, stability = OptionStability.STABLE, help = "Option allowed in all policies", sandbox = SandboxPolicy.UNTRUSTED)//
        static final OptionKey<Boolean> ContextUntrustedOption = new OptionKey<>(false);
    }

    private final class MockMessageTransport implements MessageTransport {
        @Override
        public MessageEndpoint open(URI uri, MessageEndpoint peerEndpoint) {
            throw new UnsupportedOperationException();
        }
    }
}
