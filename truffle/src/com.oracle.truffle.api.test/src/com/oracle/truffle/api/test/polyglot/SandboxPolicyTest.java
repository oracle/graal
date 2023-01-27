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

    private static final Predicate<String> CLASS_FILTER_DISABLE_LOADING = (cn) -> false;

    private final SandboxPolicy sandboxPolicy;

    public SandboxPolicyTest(SandboxPolicy sandboxPolicy) {
        this.sandboxPolicy = Objects.requireNonNull(sandboxPolicy);
    }

    @Parameterized.Parameters(name = "{0}")
    public static Collection<SandboxPolicy> createParameters() {
        return List.of(SandboxPolicy.TRUSTED, SandboxPolicy.RELAXED);
    }

    @Test
    @SuppressWarnings("try")
    public void testEngineStreams() {
        InputStream nullIn = InputStream.nullInputStream();
        OutputStream nullOut = OutputStream.nullOutputStream();
        try (Engine engine = Engine.newBuilder(RelaxedLanguage.ID).sandbox(sandboxPolicy).build()) {
            assertAtMost(SandboxPolicy.TRUSTED, sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            assertAtLeast(SandboxPolicy.RELAXED, sandboxPolicy);
        }
        try (Engine engine = Engine.newBuilder(RelaxedLanguage.ID).sandbox(sandboxPolicy).out(nullOut).err(nullOut).build()) {
            assertAtMost(SandboxPolicy.TRUSTED, sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            assertAtLeast(SandboxPolicy.RELAXED, sandboxPolicy);
        }
        try (Engine engine = Engine.newBuilder(RelaxedLanguage.ID).sandbox(sandboxPolicy).in(nullIn).err(nullOut).build()) {
            assertAtMost(SandboxPolicy.TRUSTED, sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            assertAtLeast(SandboxPolicy.RELAXED, sandboxPolicy);
        }
        try (Engine engine = Engine.newBuilder(RelaxedLanguage.ID).sandbox(sandboxPolicy).in(nullIn).out(nullOut).build()) {
            assertAtMost(SandboxPolicy.TRUSTED, sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            assertAtLeast(SandboxPolicy.RELAXED, sandboxPolicy);
        }
        Engine engine = Engine.newBuilder(RelaxedLanguage.ID).sandbox(sandboxPolicy).in(nullIn).out(nullOut).err(nullOut).build();
        engine.close();
    }

    @Test
    @SuppressWarnings("try")
    public void testEngineMessageTransport() {
        try (Engine engine = newRelaxedEngineBuilder(RelaxedLanguage.ID).sandbox(sandboxPolicy).serverTransport(new MockMessageTransport()).build()) {
            assertAtMost(SandboxPolicy.TRUSTED, sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            assertAtLeast(SandboxPolicy.RELAXED, sandboxPolicy);
        }
        Engine engine = newRelaxedEngineBuilder(RelaxedLanguage.ID).sandbox(sandboxPolicy).build();
        engine.close();
    }

    @Test
    @SuppressWarnings("try")
    public void testContextPermittedLanguages() {
        try (Context context = newRelaxedContextBuilder().sandbox(sandboxPolicy).build()) {
            assertAtMost(SandboxPolicy.TRUSTED, sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            assertAtLeast(SandboxPolicy.RELAXED, sandboxPolicy);
        }

        Context context = newRelaxedContextBuilder(RelaxedLanguage.ID).sandbox(sandboxPolicy).build();
        context.close();

        try (Engine engine = newRelaxedEngineBuilder().sandbox(sandboxPolicy).build()) {
            assertAtMost(SandboxPolicy.TRUSTED, sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            assertAtLeast(SandboxPolicy.RELAXED, sandboxPolicy);
        }

        try (Engine engine = newRelaxedEngineBuilder(RelaxedLanguage.ID).sandbox(sandboxPolicy).build()) {
            context = newRelaxedContextBuilder().engine(engine).sandbox(sandboxPolicy).build();
            context.close();
        }
    }

    @Test
    public void testContextWithTrustedLanguage() {
        try (Context context = newRelaxedContextBuilder(TrustedLanguage.ID).sandbox(sandboxPolicy).build()) {
            try {
                context.initialize(TrustedLanguage.ID);
                assertAtMost(SandboxPolicy.TRUSTED, sandboxPolicy);
            } catch (IllegalArgumentException iae) {
                assertAtLeast(SandboxPolicy.RELAXED, sandboxPolicy);
            }
        }
    }

    @Test
    public void testContextWithRelaxedLanguage() {
        try (Context context = newRelaxedContextBuilder(RelaxedLanguage.ID).sandbox(sandboxPolicy).build()) {
            context.initialize(RelaxedLanguage.ID);
            assertAtMost(SandboxPolicy.RELAXED, sandboxPolicy);
        }
    }

    @Test
    @SuppressWarnings("try")
    public void testContextWithTrustedInstrument() {
        try (Context context = newRelaxedContextBuilder(RelaxedLanguage.ID).option(TrustedInstrument.ID + ".Enable", "true").sandbox(sandboxPolicy).build()) {
            assertAtMost(SandboxPolicy.TRUSTED, sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            assertAtLeast(SandboxPolicy.RELAXED, sandboxPolicy);
        }
    }

    @Test
    @SuppressWarnings("try")
    public void testContextWithRelaxedInstrument() {
        try (Context context = newRelaxedContextBuilder(RelaxedLanguage.ID).option(RelaxedInstrument.ID + ".Enable", "true").sandbox(sandboxPolicy).build()) {
            assertAtMost(SandboxPolicy.RELAXED, sandboxPolicy);
        }
    }

    @Test
    @SuppressWarnings("try")
    public void testLanguageOptions() {
        try (Context context = newRelaxedContextBuilder(RelaxedLanguage.ID).option(RelaxedLanguage.ID + ".RelaxedOption", "true").sandbox(sandboxPolicy).build()) {
            assertAtMost(SandboxPolicy.RELAXED, sandboxPolicy);
        }
        try (Context context = newRelaxedContextBuilder(RelaxedLanguage.ID).option(RelaxedLanguage.ID + ".UntrustedOption", "true").sandbox(sandboxPolicy).build()) {
            assertAtMost(SandboxPolicy.RELAXED, sandboxPolicy);
        }
        try (Context context = newRelaxedContextBuilder(RelaxedLanguage.ID).option(RelaxedLanguage.ID + ".TrustedOption", "true").sandbox(sandboxPolicy).build()) {
            assertAtMost(SandboxPolicy.TRUSTED, sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            assertAtLeast(SandboxPolicy.RELAXED, sandboxPolicy);
        }
    }

    @Test
    @SuppressWarnings("try")
    public void testInstrumentOptions() {
        try (Context context = newRelaxedContextBuilder(RelaxedLanguage.ID).option(RelaxedInstrument.ID + ".Enable", "true").option(RelaxedInstrument.ID + ".RelaxedOption", "true").sandbox(
                        sandboxPolicy).build()) {
            assertAtMost(SandboxPolicy.RELAXED, sandboxPolicy);
        }
        try (Context context = newRelaxedContextBuilder(RelaxedLanguage.ID).option(RelaxedInstrument.ID + ".Enable", "true").option(RelaxedInstrument.ID + ".UntrustedOption", "true").sandbox(
                        sandboxPolicy).build()) {
            assertAtMost(SandboxPolicy.RELAXED, sandboxPolicy);
        }
        try (Context context = newRelaxedContextBuilder(RelaxedLanguage.ID).option(RelaxedInstrument.ID + ".Enable", "true").option(RelaxedInstrument.ID + ".TrustedOption", "true").sandbox(
                        sandboxPolicy).build()) {
            assertAtMost(SandboxPolicy.TRUSTED, sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            assertAtLeast(SandboxPolicy.RELAXED, sandboxPolicy);
        }
    }

    @Test
    @SuppressWarnings("try")
    public void testInstrumentContextOptions() {
        try (Context context = newRelaxedContextBuilder(RelaxedLanguage.ID).option(RelaxedInstrument.ID + ".Enable", "true").option(RelaxedInstrument.ID + ".ContextRelaxedOption", "true").sandbox(
                        sandboxPolicy).build()) {
            assertAtMost(SandboxPolicy.RELAXED, sandboxPolicy);
        }
        try (Context context = newRelaxedContextBuilder(RelaxedLanguage.ID).option(RelaxedInstrument.ID + ".Enable", "true").option(RelaxedInstrument.ID + ".ContextUntrustedOption", "true").sandbox(
                        sandboxPolicy).build()) {
            assertAtMost(SandboxPolicy.RELAXED, sandboxPolicy);
        }
        try (Context context = newRelaxedContextBuilder(RelaxedLanguage.ID).option(RelaxedInstrument.ID + ".Enable", "true").option(RelaxedInstrument.ID + ".ContextTrustedOption", "true").sandbox(
                        sandboxPolicy).build()) {
            assertAtMost(SandboxPolicy.TRUSTED, sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            assertAtLeast(SandboxPolicy.RELAXED, sandboxPolicy);
        }
    }

    @Test
    @SuppressWarnings("try")
    public void testContextAllAccess() {
        try (Context context = newRelaxedContextBuilder(RelaxedLanguage.ID).sandbox(sandboxPolicy).allowAllAccess(true).build()) {
            assertAtMost(SandboxPolicy.TRUSTED, sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            assertAtLeast(SandboxPolicy.RELAXED, sandboxPolicy);
        }
        Context context = newRelaxedContextBuilder(RelaxedLanguage.ID).sandbox(sandboxPolicy).build();
        context.close();
    }

    @Test
    @SuppressWarnings("try")
    public void testContextNativeAccess() {
        try (Context context = newRelaxedContextBuilder(RelaxedLanguage.ID).sandbox(sandboxPolicy).allowNativeAccess(true).build()) {
            assertAtMost(SandboxPolicy.TRUSTED, sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            assertAtLeast(SandboxPolicy.RELAXED, sandboxPolicy);
        }
        Context context = newRelaxedContextBuilder(RelaxedLanguage.ID).sandbox(sandboxPolicy).build();
        context.close();
    }

    @Test
    @SuppressWarnings("try")
    public void testContextHostClassLoading() {
        try (Context context = newRelaxedContextBuilder(RelaxedLanguage.ID).sandbox(sandboxPolicy).allowHostClassLoading(true).build()) {
            assertAtMost(SandboxPolicy.TRUSTED, sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            assertAtLeast(SandboxPolicy.RELAXED, sandboxPolicy);
        }
        Context context = newRelaxedContextBuilder(RelaxedLanguage.ID).sandbox(sandboxPolicy).build();
        context.close();
    }

    @Test
    @SuppressWarnings("try")
    public void testContextCreateProcess() {
        try (Context context = newRelaxedContextBuilder(RelaxedLanguage.ID).sandbox(sandboxPolicy).allowCreateProcess(true).build()) {
            assertAtMost(SandboxPolicy.TRUSTED, sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            assertAtLeast(SandboxPolicy.RELAXED, sandboxPolicy);
        }
        Context context = newRelaxedContextBuilder(RelaxedLanguage.ID).sandbox(sandboxPolicy).build();
        context.close();
    }

    @Test
    @SuppressWarnings("try")
    public void testContextSystemExit() {
        try (Context context = newRelaxedContextBuilder(RelaxedLanguage.ID).sandbox(sandboxPolicy).useSystemExit(true).build()) {
            assertAtMost(SandboxPolicy.TRUSTED, sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            assertAtLeast(SandboxPolicy.RELAXED, sandboxPolicy);
        }
        Context context = newRelaxedContextBuilder(RelaxedLanguage.ID).sandbox(sandboxPolicy).build();
        context.close();
    }

    @Test
    @SuppressWarnings("try")
    public void testContextStreams() {
        try (Engine engine = newRelaxedEngineBuilder(RelaxedLanguage.ID).sandbox(sandboxPolicy).build()) {
            try (Context context = newRelaxedContextBuilder(RelaxedLanguage.ID).sandbox(sandboxPolicy).in(System.in).build()) {
                assertAtMost(SandboxPolicy.TRUSTED, sandboxPolicy);
            } catch (IllegalArgumentException iae) {
                assertAtLeast(SandboxPolicy.RELAXED, sandboxPolicy);
            }
            try (Context context = newRelaxedContextBuilder(RelaxedLanguage.ID).sandbox(sandboxPolicy).out(System.out).build()) {
                assertAtMost(SandboxPolicy.TRUSTED, sandboxPolicy);
            } catch (IllegalArgumentException iae) {
                assertAtLeast(SandboxPolicy.RELAXED, sandboxPolicy);
            }
            try (Context context = newRelaxedContextBuilder(RelaxedLanguage.ID).sandbox(sandboxPolicy).err(System.err).build()) {
                assertAtMost(SandboxPolicy.TRUSTED, sandboxPolicy);
            } catch (IllegalArgumentException iae) {
                assertAtLeast(SandboxPolicy.RELAXED, sandboxPolicy);
            }
        }
    }

    @Test
    @SuppressWarnings({"try", "deprecation"})
    public void testContextAllowIO() {
        try (Context context = newRelaxedContextBuilder(RelaxedLanguage.ID).sandbox(sandboxPolicy).allowIO(true).build()) {
            assertAtMost(SandboxPolicy.TRUSTED, sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            assertAtLeast(SandboxPolicy.RELAXED, sandboxPolicy);
        }
    }

    @Test
    @SuppressWarnings({"try"})
    public void testContextIOAccess() throws IOException {
        try (Context context = newRelaxedContextBuilder(RelaxedLanguage.ID).sandbox(sandboxPolicy).allowIO(IOAccess.ALL).build()) {
            assertAtMost(SandboxPolicy.TRUSTED, sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            assertAtLeast(SandboxPolicy.RELAXED, sandboxPolicy);
        }
        try (Context context = newRelaxedContextBuilder(RelaxedLanguage.ID).sandbox(sandboxPolicy).allowIO(IOAccess.newBuilder().allowHostFileAccess(true).build()).build()) {
            assertAtMost(SandboxPolicy.TRUSTED, sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            assertAtLeast(SandboxPolicy.RELAXED, sandboxPolicy);
        }
        try (Context context = newRelaxedContextBuilder(RelaxedLanguage.ID).sandbox(sandboxPolicy).allowIO(IOAccess.newBuilder().allowHostSocketAccess(true).build()).build()) {
            assertAtMost(SandboxPolicy.TRUSTED, sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            assertAtLeast(SandboxPolicy.RELAXED, sandboxPolicy);
        }
        try (Context context = newRelaxedContextBuilder(RelaxedLanguage.ID).sandbox(sandboxPolicy).allowIO(
                        IOAccess.newBuilder().fileSystem(FileSystem.newReadOnlyFileSystem(FileSystem.newDefaultFileSystem())).build()).build()) {
            assertAtMost(SandboxPolicy.TRUSTED, sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            assertAtLeast(SandboxPolicy.RELAXED, sandboxPolicy);
        }
        Context context = newRelaxedContextBuilder(RelaxedLanguage.ID).sandbox(sandboxPolicy).allowIO(IOAccess.newBuilder().fileSystem(new MemoryFileSystem("/tmp")).build()).build();
        context.close();
        context = newRelaxedContextBuilder(RelaxedLanguage.ID).sandbox(sandboxPolicy).allowIO(IOAccess.NONE).build();
        context.close();
    }

    @Test
    @SuppressWarnings({"try"})
    public void testContextEnvironmentAccess() {
        try (Context context = newRelaxedContextBuilder(RelaxedLanguage.ID).sandbox(sandboxPolicy).allowEnvironmentAccess(EnvironmentAccess.INHERIT).build()) {
            assertAtMost(SandboxPolicy.TRUSTED, sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            assertAtLeast(SandboxPolicy.RELAXED, sandboxPolicy);
        }
        Context context = newRelaxedContextBuilder(RelaxedLanguage.ID).sandbox(sandboxPolicy).allowEnvironmentAccess(EnvironmentAccess.NONE).build();
        context.close();
    }

    @Test
    @SuppressWarnings({"try", "deprecation"})
    public void testHostAccess() {
        try (Context context = newRelaxedContextBuilder(RelaxedLanguage.ID).sandbox(sandboxPolicy).allowHostAccess(true).build()) {
            assertAtMost(SandboxPolicy.TRUSTED, sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            assertAtLeast(SandboxPolicy.RELAXED, sandboxPolicy);
        }
        HostAccess hostAccess = HostAccess.newBuilder().allowPublicAccess(true).build();
        try (Context context = newRelaxedContextBuilder(RelaxedLanguage.ID).sandbox(sandboxPolicy).allowHostAccess(hostAccess).build()) {
            assertAtMost(SandboxPolicy.TRUSTED, sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            assertAtLeast(SandboxPolicy.RELAXED, sandboxPolicy);
        }
        hostAccess = HostAccess.newBuilder().allowAccessInheritance(true).build();
        try (Context context = newRelaxedContextBuilder(RelaxedLanguage.ID).sandbox(sandboxPolicy).allowHostAccess(hostAccess).build()) {
            assertAtMost(SandboxPolicy.TRUSTED, sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            assertAtLeast(SandboxPolicy.RELAXED, sandboxPolicy);
        }
        hostAccess = HostAccess.newBuilder().allowMutableTargetMappings(HostAccess.MutableTargetMapping.EXECUTABLE_TO_JAVA_INTERFACE).build();
        try (Context context = newRelaxedContextBuilder(RelaxedLanguage.ID).sandbox(sandboxPolicy).allowHostAccess(hostAccess).build()) {
            assertAtMost(SandboxPolicy.TRUSTED, sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            assertAtLeast(SandboxPolicy.RELAXED, sandboxPolicy);
        }
        hostAccess = HostAccess.newBuilder().allowAccessAnnotatedBy(HostAccess.Export.class).allowImplementationsAnnotatedBy(HostAccess.Implementable.class).allowMutableTargetMappings().methodScoping(
                        true).build();

        Context context = newRelaxedContextBuilder(RelaxedLanguage.ID).sandbox(sandboxPolicy).allowHostAccess(hostAccess).build();
        context.close();
        // HostAccess.NONE fails for sandboxPolicy >= RELAXED because it for compatibility reasons
        // allows mutable target mappings.
        try (Context context2 = newRelaxedContextBuilder(RelaxedLanguage.ID).sandbox(sandboxPolicy).allowHostAccess(HostAccess.NONE).build()) {
            assertAtMost(SandboxPolicy.TRUSTED, sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            assertAtLeast(SandboxPolicy.RELAXED, sandboxPolicy);
        }
        hostAccess = HostAccess.newBuilder(HostAccess.NONE).allowMutableTargetMappings().build();
        context = newRelaxedContextBuilder(RelaxedLanguage.ID).sandbox(sandboxPolicy).allowHostAccess(hostAccess).build();
        context.close();
    }

    private static Engine.Builder newRelaxedEngineBuilder(String... permittedLanguages) {
        return Engine.newBuilder(permittedLanguages).in(InputStream.nullInputStream()).out(OutputStream.nullOutputStream()).err(OutputStream.nullOutputStream());
    }

    private static Context.Builder newRelaxedContextBuilder(String... permittedLanguages) {
        return Context.newBuilder(permittedLanguages).allowEnvironmentAccess(EnvironmentAccess.NONE).allowHostClassLookup(CLASS_FILTER_DISABLE_LOADING).in(InputStream.nullInputStream()).out(
                        OutputStream.nullOutputStream()).err(OutputStream.nullOutputStream());
    }

    private static void assertAtLeast(SandboxPolicy expectedMinimalPolicy, SandboxPolicy actualPolicy) {
        assertTrue(expectedMinimalPolicy.ordinal() <= actualPolicy.ordinal());
    }

    private static void assertAtMost(SandboxPolicy expectedMaximalPolicy, SandboxPolicy actualPolicy) {
        assertTrue(expectedMaximalPolicy.ordinal() >= actualPolicy.ordinal());
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

        @Option(category = OptionCategory.EXPERT, stability = OptionStability.STABLE, help = "Option allowed only in trusted policy") static final OptionKey<Boolean> TrustedOption = new OptionKey<>(
                        false);

        @Option(category = OptionCategory.EXPERT, stability = OptionStability.STABLE, help = "Option allowed only in trusted and relaxed policy", sandbox = SandboxPolicy.RELAXED) static final OptionKey<Boolean> RelaxedOption = new OptionKey<>(
                        false);

        @Option(category = OptionCategory.EXPERT, stability = OptionStability.STABLE, help = "Option allowed only in all policies", sandbox = SandboxPolicy.UNTRUSTED) static final OptionKey<Boolean> UntrustedOption = new OptionKey<>(
                        false);

        @Override
        protected Env createContext(Env env) {
            return env;
        }

        @Override
        protected OptionDescriptors getOptionDescriptors() {
            return new RelaxedLanguageOptionDescriptors();
        }
    }

    @TruffleInstrument.Registration(id = TrustedInstrument.ID, name = TrustedInstrument.ID)
    static final class TrustedInstrument extends TruffleInstrument {

        private static final String ID = "tool_sandbox_trusted";

        @Option(category = OptionCategory.EXPERT, stability = OptionStability.STABLE, help = "Enables instrument", sandbox = SandboxPolicy.RELAXED) static final OptionKey<Boolean> Enable = new OptionKey<>(
                        false);

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

        @Option(category = OptionCategory.EXPERT, stability = OptionStability.STABLE, help = "Enables instrument", sandbox = SandboxPolicy.RELAXED) static final OptionKey<Boolean> Enable = new OptionKey<>(
                        false);

        @Option(category = OptionCategory.EXPERT, stability = OptionStability.STABLE, help = "Option allowed only in trusted policy") static final OptionKey<Boolean> TrustedOption = new OptionKey<>(
                        false);

        @Option(category = OptionCategory.EXPERT, stability = OptionStability.STABLE, help = "Option allowed only in trusted and relaxed policy", sandbox = SandboxPolicy.RELAXED) static final OptionKey<Boolean> RelaxedOption = new OptionKey<>(
                        false);

        @Option(category = OptionCategory.EXPERT, stability = OptionStability.STABLE, help = "Option allowed only in all policies", sandbox = SandboxPolicy.UNTRUSTED) static final OptionKey<Boolean> UntrustedOption = new OptionKey<>(
                        false);

        @Override
        protected void onCreate(Env env) {
        }

        @Override
        protected OptionDescriptors getOptionDescriptors() {
            return new RelaxedInstrumentOptionDescriptors();
        }

        @Override
        protected OptionDescriptors getContextOptionDescriptors() {
            return new RelaxedInstrumentContextOptionsOptionDescriptors();
        }
    }

    @Option.Group(RelaxedInstrument.ID)
    static final class RelaxedInstrumentContextOptions {
        @Option(category = OptionCategory.EXPERT, stability = OptionStability.STABLE, help = "Option allowed only in trusted policy") static final OptionKey<Boolean> ContextTrustedOption = new OptionKey<>(
                        false);

        @Option(category = OptionCategory.EXPERT, stability = OptionStability.STABLE, help = "Option allowed only in trusted and relaxed policy", sandbox = SandboxPolicy.RELAXED) static final OptionKey<Boolean> ContextRelaxedOption = new OptionKey<>(
                        false);

        @Option(category = OptionCategory.EXPERT, stability = OptionStability.STABLE, help = "Option allowed only in all policies", sandbox = SandboxPolicy.UNTRUSTED) static final OptionKey<Boolean> ContextUntrustedOption = new OptionKey<>(
                        false);
    }

    private final class MockMessageTransport implements MessageTransport {
        @Override
        public MessageEndpoint open(URI uri, MessageEndpoint peerEndpoint) {
            throw new UnsupportedOperationException();
        }
    }
}
