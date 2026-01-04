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

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;

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
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import com.oracle.truffle.api.Option;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleOptions;
import com.oracle.truffle.api.impl.DefaultTruffleRuntime;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.tck.tests.TruffleTestAssumptions;

@RunWith(Parameterized.class)
public class SandboxPolicyTest {

    private static final String MISSING_ISOLATE_LIBRARY_MESSAGE = "No native isolate library is available for the requested language(s)";
    private static final String UNSUPPORTED_ISOLATE_POLICY_MESSAGE = "but the current Truffle runtime only supports the TRUSTED or CONSTRAINED sandbox policies.";

    private final Configuration configuration;

    // Workaround for issue GR-31197: Compiler tests are passing engine.DynamicCompilationThresholds
    // option from command line.
    private static String originalDynamicCompilationThresholds;

    public SandboxPolicyTest(Configuration configuration) {
        this.configuration = Objects.requireNonNull(configuration);
    }

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Configuration> createParameters() {
        boolean supportsIsolates = supportsPolyglotIsolates();
        boolean supportsSandboxInstrument = supportsSandboxInstrument();
        boolean hasIsolateLibrary = supportsIsolates && tryToLoadIsolateLibrary();
        assertTrue("Invalid language library. Truffle isolate must support sandbox instrument.", !hasIsolateLibrary || supportsSandboxInstrument);
        return List.of(
                        new Configuration(SandboxPolicy.TRUSTED, supportsIsolates, supportsSandboxInstrument, hasIsolateLibrary),
                        new Configuration(SandboxPolicy.CONSTRAINED, supportsIsolates, supportsSandboxInstrument, hasIsolateLibrary),
                        new Configuration(SandboxPolicy.ISOLATED, supportsIsolates, supportsSandboxInstrument, hasIsolateLibrary),
                        new Configuration(SandboxPolicy.UNTRUSTED, supportsIsolates, supportsSandboxInstrument, hasIsolateLibrary));
    }

    @BeforeClass
    public static void setUpClass() {
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

    /**
     * A mirror of the method {@link SandboxInstrument#isInterpreterCallStackHeadRoomSupported()}
     * taking into account the possibility to run in polyglot isolate spawned form HotSpot.
     */
    private static boolean isInterpreterCallStackHeadRoomSupported() {
        Runtime.Version jdkVersion = Runtime.version();
        return (TruffleTestAssumptions.isIsolateEncapsulation() || TruffleOptions.AOT) && jdkVersion.feature() >= 23;
    }

    @Before
    public void setUp() {
        Assume.assumeFalse("Restricted Truffle compiler options are specified on the command line.",
                        configuration.sandboxPolicy != SandboxPolicy.TRUSTED && executedWithXCompOptions());
        Assume.assumeTrue(configuration.sandboxPolicy != SandboxPolicy.UNTRUSTED || isInterpreterCallStackHeadRoomSupported());
    }

    private static boolean executedWithXCompOptions() {
        Properties props = System.getProperties();
        return props.containsKey("polyglot.engine.CompileImmediately") || props.containsKey("polyglot.engine.BackgroundCompilation");
    }

    private static boolean supportsPolyglotIsolates() {
        try (Engine engine = Engine.create()) {
            OptionDescriptors optionDescriptors = engine.getOptions();
            return optionDescriptors.get("engine.SpawnIsolate") != null;
        }
    }

    private static boolean supportsSandboxInstrument() {
        try (Engine engine = Engine.create()) {
            // Polyglot sandbox limits can only be used with runtimes that support enterprise
            // extensions.
            return engine.getInstruments().containsKey("sandbox") && TruffleTestAssumptions.isEnterpriseRuntime();
        }
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
        OutputStream nullOut = OutputStream.nullOutputStream();
        try (Engine engine = newEngineWithIsolateOptions(UntrustedLanguage.ID).sandbox(configuration.sandboxPolicy).build()) {
            assertAtMost(SandboxPolicy.TRUSTED, configuration.sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            if (filterUnsupportedIsolate(configuration, iae)) {
                assertSandboxPolicyException(iae, "Builder uses the standard output stream, but the output must be redirected.");
                assertAtLeast(SandboxPolicy.CONSTRAINED, configuration.sandboxPolicy);
            }
        }
        try (Engine engine = newEngineWithIsolateOptions(UntrustedLanguage.ID).sandbox(configuration.sandboxPolicy).err(nullOut).build()) {
            assertAtMost(SandboxPolicy.TRUSTED, configuration.sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            if (filterUnsupportedIsolate(configuration, iae)) {
                assertSandboxPolicyException(iae, "Builder uses the standard output stream, but the output must be redirected.");
                assertAtLeast(SandboxPolicy.CONSTRAINED, configuration.sandboxPolicy);
            }
        }
        try (Engine engine = newEngineWithIsolateOptions(UntrustedLanguage.ID).sandbox(configuration.sandboxPolicy).out(nullOut).build()) {
            assertAtMost(SandboxPolicy.TRUSTED, configuration.sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            if (filterUnsupportedIsolate(configuration, iae)) {
                assertSandboxPolicyException(iae, "Builder uses the standard error stream, but the error output must be redirected.");
                assertAtLeast(SandboxPolicy.CONSTRAINED, configuration.sandboxPolicy);
            }
        }
        try (Engine engine = newEngineWithIsolateOptions(UntrustedLanguage.ID).sandbox(configuration.sandboxPolicy).in(System.in).out(nullOut).err(nullOut).build()) {
            assertAtMost(SandboxPolicy.TRUSTED, configuration.sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            if (filterUnsupportedIsolate(configuration, iae)) {
                assertSandboxPolicyException(iae, "Builder uses the standard input stream, but the input must be redirected.");
                assertAtLeast(SandboxPolicy.CONSTRAINED, configuration.sandboxPolicy);
            }
        }
        try (Engine engine = newEngineWithIsolateOptions(UntrustedLanguage.ID).sandbox(configuration.sandboxPolicy).out(nullOut).err(nullOut).build()) {
            assertAtMost(SandboxPolicy.UNTRUSTED, configuration.sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            if (filterUnsupportedIsolate(configuration, iae)) {
                throw iae;
            }
        }
        try (Engine engine = newEngineWithIsolateOptions(UntrustedLanguage.ID).sandbox(configuration.sandboxPolicy).out(nullOut).err(nullOut).in(InputStream.nullInputStream()).build()) {
            assertAtMost(SandboxPolicy.UNTRUSTED, configuration.sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            if (filterUnsupportedIsolate(configuration, iae)) {
                throw iae;
            }
        }
    }

    private Engine.Builder newEngineWithIsolateOptions(String... permittedLanguages) {
        Engine.Builder builder = Engine.newBuilder(permittedLanguages);
        if (configuration.sandboxPolicy.isStricterOrEqual(SandboxPolicy.ISOLATED) && configuration.supportsIsolatedPolicy) {
            builder.option("engine.MaxIsolateMemory", "4GB");
        }
        return builder;
    }

    @Test
    @SuppressWarnings("try")
    public void testEngineMessageTransport() {
        try (Engine engine = newEngineBuilder(ConstrainedLanguage.ID).sandbox(configuration.sandboxPolicy).serverTransport(new MockMessageTransport()).build()) {
            assertAtMost(SandboxPolicy.TRUSTED, configuration.sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            if (filterUnsupportedIsolate(configuration, iae)) {
                assertSandboxPolicyException(iae, "Builder.serverTransport(MessageTransport) is set, but must not be set.");
                assertAtLeast(SandboxPolicy.CONSTRAINED, configuration.sandboxPolicy);
            }
        }
        try (Engine engine = newEngineBuilder(UntrustedLanguage.ID).sandbox(configuration.sandboxPolicy).build()) {
            assertAtMost(SandboxPolicy.UNTRUSTED, configuration.sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            if (filterUnsupportedIsolate(configuration, iae)) {
                throw iae;
            }
        }
    }

    @Test
    @SuppressWarnings("try")
    public void testSandboxInheritedFromEngine() {
        try (Engine engine = newEngineBuilder(UntrustedLanguage.ID).sandbox(configuration.sandboxPolicy).build()) {
            try (Context context = newContextBuilder(engine).build()) {
                assertAtMost(SandboxPolicy.UNTRUSTED, configuration.sandboxPolicy);
            }
            try (Context context = newContextBuilder(engine).sandbox(configuration.sandboxPolicy).build()) {
                assertAtMost(SandboxPolicy.UNTRUSTED, configuration.sandboxPolicy);
            }
            Set<SandboxPolicy> otherSandboxPolicies = EnumSet.allOf(SandboxPolicy.class);
            otherSandboxPolicies.remove(configuration.sandboxPolicy);
            for (SandboxPolicy otherSandboxPolicy : otherSandboxPolicies) {
                AbstractPolyglotTest.assertFails(() -> newContextBuilder(engine).sandbox(otherSandboxPolicy).build(), IllegalArgumentException.class, (iae) -> {
                    assertSandboxPolicyException(iae, "The engine and context must have the same SandboxPolicy.");
                });
            }
        } catch (IllegalArgumentException iae) {
            if (filterUnsupportedIsolate(configuration, iae)) {
                throw iae;
            }
        }
    }

    @Test
    @SuppressWarnings("try")
    public void testContextPermittedLanguages() {
        try (Context context = newContextBuilder(null).sandbox(configuration.sandboxPolicy).build()) {
            assertAtMost(SandboxPolicy.TRUSTED, configuration.sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            if (filterUnsupportedIsolate(configuration, iae)) {
                assertSandboxPolicyException(iae, "Builder does not have a list of permitted languages.");
                assertAtLeast(SandboxPolicy.CONSTRAINED, configuration.sandboxPolicy);
            }
        }

        try (Context context = newContextBuilder(null, UntrustedLanguage.ID).sandbox(configuration.sandboxPolicy).build()) {
            assertAtMost(SandboxPolicy.UNTRUSTED, configuration.sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            if (filterUnsupportedIsolate(configuration, iae)) {
                throw iae;
            }
        }

        try (Engine engine = newEngineBuilder().sandbox(configuration.sandboxPolicy).build()) {
            assertAtMost(SandboxPolicy.TRUSTED, configuration.sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            if (filterUnsupportedIsolate(configuration, iae)) {
                assertSandboxPolicyException(iae, "Builder does not have a list of permitted languages.");
                assertAtLeast(SandboxPolicy.CONSTRAINED, configuration.sandboxPolicy);
            }
        }

        try (Engine engine = newEngineBuilder(UntrustedLanguage.ID).sandbox(configuration.sandboxPolicy).build()) {
            Context context = newContextBuilder(engine).sandbox(configuration.sandboxPolicy).build();
            context.close();
        } catch (IllegalArgumentException iae) {
            if (filterUnsupportedIsolate(configuration, iae)) {
                throw iae;
            }
        }
    }

    @Test
    @SuppressWarnings("try")
    public void testContextWithTrustedLanguage() {
        try (Context context = newContextBuilder(null, TrustedLanguage.ID).sandbox(configuration.sandboxPolicy).build()) {
            assertAtMost(SandboxPolicy.TRUSTED, configuration.sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            if (filterUnsupportedIsolate(configuration, iae)) {
                assertSandboxPolicyException(iae, "The language %s can only be used up to the %s sandbox policy.", TrustedLanguage.ID, SandboxPolicy.TRUSTED);
                assertAtLeast(SandboxPolicy.CONSTRAINED, configuration.sandboxPolicy);
            }
        }
    }

    @Test
    @SuppressWarnings("try")
    public void testContextWithConstrainedLanguage() {
        try (Context context = newContextBuilder(null, ConstrainedLanguage.ID).sandbox(configuration.sandboxPolicy).build()) {
            assertAtMost(SandboxPolicy.CONSTRAINED, configuration.sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            if (filterUnsupportedIsolate(configuration, iae)) {
                assertSandboxPolicyException(iae, "The language %s can only be used up to the %s sandbox policy.", ConstrainedLanguage.ID, SandboxPolicy.CONSTRAINED);
                assertAtLeast(SandboxPolicy.ISOLATED, configuration.sandboxPolicy);
            }
        }
    }

    @Test
    @SuppressWarnings("try")
    public void testContextWithIsolatedLanguage() {
        try (Context context = newContextBuilder(null, IsolatedLanguage.ID).sandbox(configuration.sandboxPolicy).build()) {
            assertAtMost(SandboxPolicy.ISOLATED, configuration.sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            if (filterUnsupportedIsolate(configuration, iae)) {
                assertSandboxPolicyException(iae, "The language %s can only be used up to the %s sandbox policy.", IsolatedLanguage.ID, SandboxPolicy.ISOLATED);
                assertAtLeast(SandboxPolicy.UNTRUSTED, configuration.sandboxPolicy);
            }
        }
    }

    @Test
    @SuppressWarnings("try")
    public void testContextWithUntrustedLanguage() {
        try (Context context = newContextBuilder(null, UntrustedLanguage.ID).sandbox(configuration.sandboxPolicy).build()) {
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
        try (Context context = newContextBuilder(null, UntrustedLanguage.ID).option(TrustedInstrument.ID + ".Enable", "true").sandbox(configuration.sandboxPolicy).build()) {
            assertAtMost(SandboxPolicy.TRUSTED, configuration.sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            if (filterUnsupportedIsolate(configuration, iae)) {
                assertSandboxPolicyException(iae, "The instrument %s can only be used up to the %s sandbox policy.", TrustedInstrument.ID, SandboxPolicy.TRUSTED);
                assertAtLeast(SandboxPolicy.CONSTRAINED, configuration.sandboxPolicy);
            }
        }
    }

    @Test
    @SuppressWarnings("try")
    public void testContextWithConstrainedInstrument() {
        try (Context context = newContextBuilder(null, UntrustedLanguage.ID).option(ConstrainedInstrument.ID + ".Enable", "true").sandbox(configuration.sandboxPolicy).build()) {
            assertAtMost(SandboxPolicy.CONSTRAINED, configuration.sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            if (filterUnsupportedIsolate(configuration, iae)) {
                assertSandboxPolicyException(iae, "The instrument %s can only be used up to the %s sandbox policy.", ConstrainedInstrument.ID, SandboxPolicy.CONSTRAINED);
                assertAtLeast(SandboxPolicy.ISOLATED, configuration.sandboxPolicy);
            }
        }
    }

    @Test
    @SuppressWarnings("try")
    public void testContextWithIsolatedInstrument() {
        try (Context context = newContextBuilder(null, UntrustedLanguage.ID).option(IsolatedInstrument.ID + ".Enable", "true").sandbox(configuration.sandboxPolicy).build()) {
            assertAtMost(SandboxPolicy.ISOLATED, configuration.sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            if (filterUnsupportedIsolate(configuration, iae)) {
                assertSandboxPolicyException(iae, "The instrument %s can only be used up to the %s sandbox policy.", IsolatedInstrument.ID, SandboxPolicy.ISOLATED);
                assertAtLeast(SandboxPolicy.UNTRUSTED, configuration.sandboxPolicy);
            }
        }
    }

    @Test
    @SuppressWarnings("try")
    public void testContextWithUntrustedInstrument() {
        try (Context context = newContextBuilder(null, UntrustedLanguage.ID).option(UntrustedInstrument.ID + ".Enable", "true").sandbox(configuration.sandboxPolicy).build()) {
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
        try (Context context = newContextBuilder(null, UntrustedLanguage.ID).option(UntrustedLanguage.ID + ".ConstrainedOption", "true").sandbox(configuration.sandboxPolicy).build()) {
            assertAtMost(SandboxPolicy.CONSTRAINED, configuration.sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            if (filterUnsupportedIsolate(configuration, iae)) {
                assertSandboxPolicyException(iae, "The option %s can only be used up to the %s sandbox policy.", UntrustedLanguage.ID + ".ConstrainedOption", SandboxPolicy.CONSTRAINED);
                assertAtLeast(SandboxPolicy.ISOLATED, configuration.sandboxPolicy);
            }
        }
        try (Context context = newContextBuilder(null, UntrustedLanguage.ID).option(UntrustedLanguage.ID + ".UntrustedOption", "true").sandbox(configuration.sandboxPolicy).build()) {
            assertAtMost(SandboxPolicy.UNTRUSTED, configuration.sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            if (filterUnsupportedIsolate(configuration, iae)) {
                throw iae;
            }
        }
        try (Context context = newContextBuilder(null, UntrustedLanguage.ID).option(UntrustedLanguage.ID + ".TrustedOption", "true").sandbox(configuration.sandboxPolicy).build()) {
            assertAtMost(SandboxPolicy.TRUSTED, configuration.sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            if (filterUnsupportedIsolate(configuration, iae)) {
                assertSandboxPolicyException(iae, "The option %s can only be used up to the %s sandbox policy.", UntrustedLanguage.ID + ".TrustedOption", SandboxPolicy.TRUSTED);
                assertAtLeast(SandboxPolicy.CONSTRAINED, configuration.sandboxPolicy);
            }
        }
        try (Context context = newContextBuilder(null, UntrustedLanguage.ID).option(UntrustedLanguage.ID + ".ConstrainedOptionMap", "true").sandbox(configuration.sandboxPolicy).build()) {
            assertAtMost(SandboxPolicy.CONSTRAINED, configuration.sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            if (filterUnsupportedIsolate(configuration, iae)) {
                assertSandboxPolicyException(iae, "The option %s can only be used up to the %s sandbox policy.", UntrustedLanguage.ID + ".ConstrainedOptionMap", SandboxPolicy.CONSTRAINED);
                assertAtLeast(SandboxPolicy.ISOLATED, configuration.sandboxPolicy);
            }
        }
        try (Context context = newContextBuilder(null, UntrustedLanguage.ID).option(UntrustedLanguage.ID + ".UntrustedOptionMap", "true").sandbox(configuration.sandboxPolicy).build()) {
            assertAtMost(SandboxPolicy.UNTRUSTED, configuration.sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            if (filterUnsupportedIsolate(configuration, iae)) {
                throw iae;
            }
        }
        try (Context context = newContextBuilder(null, UntrustedLanguage.ID).option(UntrustedLanguage.ID + ".TrustedOptionMap", "true").sandbox(configuration.sandboxPolicy).build()) {
            assertAtMost(SandboxPolicy.TRUSTED, configuration.sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            if (filterUnsupportedIsolate(configuration, iae)) {
                assertSandboxPolicyException(iae, "The option %s can only be used up to the %s sandbox policy.", UntrustedLanguage.ID + ".TrustedOptionMap", SandboxPolicy.TRUSTED);
                assertAtLeast(SandboxPolicy.CONSTRAINED, configuration.sandboxPolicy);
            }
        }
    }

    @Test
    @SuppressWarnings("try")
    public void testInstrumentOptions() {
        try (Context context = newContextBuilder(null, UntrustedLanguage.ID).option(UntrustedInstrument.ID + ".Enable", "true").option(UntrustedInstrument.ID + ".ConstrainedOption",
                        "true").sandbox(
                                        configuration.sandboxPolicy).build()) {
            assertAtMost(SandboxPolicy.CONSTRAINED, configuration.sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            if (filterUnsupportedIsolate(configuration, iae)) {
                assertSandboxPolicyException(iae, "The option %s can only be used up to the %s sandbox policy.", UntrustedInstrument.ID + ".ConstrainedOption", SandboxPolicy.CONSTRAINED);
                assertAtLeast(SandboxPolicy.ISOLATED, configuration.sandboxPolicy);
            }
        }
        try (Context context = newContextBuilder(null, UntrustedLanguage.ID).option(UntrustedInstrument.ID + ".Enable", "true").option(UntrustedInstrument.ID + ".UntrustedOption",
                        "true").sandbox(
                                        configuration.sandboxPolicy).build()) {
            assertAtMost(SandboxPolicy.UNTRUSTED, configuration.sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            if (filterUnsupportedIsolate(configuration, iae)) {
                throw iae;
            }
        }
        try (Context context = newContextBuilder(null, UntrustedLanguage.ID).option(UntrustedInstrument.ID + ".Enable", "true").option(UntrustedInstrument.ID + ".TrustedOption",
                        "true").sandbox(
                                        configuration.sandboxPolicy).build()) {
            assertAtMost(SandboxPolicy.TRUSTED, configuration.sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            if (filterUnsupportedIsolate(configuration, iae)) {
                assertSandboxPolicyException(iae, "The option %s can only be used up to the %s sandbox policy.", UntrustedInstrument.ID + ".TrustedOption", SandboxPolicy.TRUSTED);
                assertAtLeast(SandboxPolicy.CONSTRAINED, configuration.sandboxPolicy);
            }
        }
    }

    @Test
    @SuppressWarnings("try")
    public void testInstrumentContextOptions() {
        try (Context context = newContextBuilder(null, UntrustedLanguage.ID).option(UntrustedInstrument.ID + ".Enable", "true").option(UntrustedInstrument.ID + ".ContextConstrainedOption",
                        "true").sandbox(
                                        configuration.sandboxPolicy).build()) {
            assertAtMost(SandboxPolicy.CONSTRAINED, configuration.sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            if (filterUnsupportedIsolate(configuration, iae)) {
                assertSandboxPolicyException(iae, "The option %s can only be used up to the %s sandbox policy.", UntrustedInstrument.ID + ".ContextConstrainedOption", SandboxPolicy.CONSTRAINED);
                assertAtLeast(SandboxPolicy.ISOLATED, configuration.sandboxPolicy);
            }
        }
        try (Context context = newContextBuilder(null, UntrustedLanguage.ID).option(UntrustedInstrument.ID + ".Enable", "true").option(UntrustedInstrument.ID + ".ContextUntrustedOption",
                        "true").sandbox(
                                        configuration.sandboxPolicy).build()) {
            assertAtMost(SandboxPolicy.UNTRUSTED, configuration.sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            if (filterUnsupportedIsolate(configuration, iae)) {
                throw iae;
            }
        }
        try (Context context = newContextBuilder(null, UntrustedLanguage.ID).option(UntrustedInstrument.ID + ".Enable", "true").option(UntrustedInstrument.ID + ".ContextTrustedOption",
                        "true").sandbox(
                                        configuration.sandboxPolicy).build()) {
            assertAtMost(SandboxPolicy.TRUSTED, configuration.sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            if (filterUnsupportedIsolate(configuration, iae)) {
                assertSandboxPolicyException(iae, "The option %s can only be used up to the %s sandbox policy.", UntrustedInstrument.ID + ".ContextTrustedOption", SandboxPolicy.TRUSTED);
                assertAtLeast(SandboxPolicy.CONSTRAINED, configuration.sandboxPolicy);
            }
        }
    }

    @Test
    @SuppressWarnings("try")
    public void testContextAllAccess() {
        try (Context context = newContextBuilder(null, ConstrainedLanguage.ID).sandbox(configuration.sandboxPolicy).allowAllAccess(true).build()) {
            assertAtMost(SandboxPolicy.TRUSTED, configuration.sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            if (filterUnsupportedIsolate(configuration, iae)) {
                assertSandboxPolicyException(iae, "Builder.allowAllAccess(boolean) is set to true, but must not be set to true.");
                assertAtLeast(SandboxPolicy.CONSTRAINED, configuration.sandboxPolicy);
            }
        }
        try (Context context = newContextBuilder(null, UntrustedLanguage.ID).sandbox(configuration.sandboxPolicy).build()) {
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
        try (Context context = newContextBuilder(null, ConstrainedLanguage.ID).sandbox(configuration.sandboxPolicy).allowNativeAccess(true).build()) {
            assertAtMost(SandboxPolicy.TRUSTED, configuration.sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            if (filterUnsupportedIsolate(configuration, iae)) {
                assertSandboxPolicyException(iae, "Builder.allowNativeAccess(boolean) is set to true, but must not be set to true.");
                assertAtLeast(SandboxPolicy.CONSTRAINED, configuration.sandboxPolicy);
            }
        }
        try (Context context = newContextBuilder(null, UntrustedLanguage.ID).sandbox(configuration.sandboxPolicy).build()) {
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
        try (Context context = newContextBuilder(null, ConstrainedLanguage.ID).sandbox(configuration.sandboxPolicy).allowHostClassLoading(true).build()) {
            assertAtMost(SandboxPolicy.TRUSTED, configuration.sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            if (filterUnsupportedIsolate(configuration, iae)) {
                assertSandboxPolicyException(iae, "Builder.allowHostClassLoading(boolean) is set to true, but must not be set to true.");
                assertAtLeast(SandboxPolicy.CONSTRAINED, configuration.sandboxPolicy);
            }
        }
        try (Context context = newContextBuilder(null, UntrustedLanguage.ID).sandbox(configuration.sandboxPolicy).build()) {
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
        try (Context context = newContextBuilder(null, ConstrainedLanguage.ID).sandbox(configuration.sandboxPolicy).allowCreateProcess(true).build()) {
            assertAtMost(SandboxPolicy.TRUSTED, configuration.sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            if (filterUnsupportedIsolate(configuration, iae)) {
                assertSandboxPolicyException(iae, "Builder.allowCreateProcess(boolean) is set to true, but must not be set to true.");
                assertAtLeast(SandboxPolicy.CONSTRAINED, configuration.sandboxPolicy);
            }
        }
        try (Context context = newContextBuilder(null, UntrustedLanguage.ID).sandbox(configuration.sandboxPolicy).build()) {
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
        try (Context context = newContextBuilder(null, ConstrainedLanguage.ID).sandbox(configuration.sandboxPolicy).useSystemExit(true).build()) {
            assertAtMost(SandboxPolicy.TRUSTED, configuration.sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            if (filterUnsupportedIsolate(configuration, iae)) {
                assertSandboxPolicyException(iae, "Builder.useSystemExit(boolean) is set to true, but must not be set to true.");
                assertAtLeast(SandboxPolicy.CONSTRAINED, configuration.sandboxPolicy);
            }
        }
        try (Context context = newContextBuilder(null, UntrustedLanguage.ID).sandbox(configuration.sandboxPolicy).build()) {
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
        try (Engine engine = newEngineBuilder(UntrustedLanguage.ID).sandbox(configuration.sandboxPolicy).build()) {
            try (Context context = newContextBuilder(engine, UntrustedLanguage.ID).sandbox(configuration.sandboxPolicy).in(System.in).build()) {
                assertAtMost(SandboxPolicy.TRUSTED, configuration.sandboxPolicy);
            } catch (IllegalArgumentException iae) {
                assertSandboxPolicyException(iae, "Builder uses the standard input stream, but the input must be redirected.");
                assertAtLeast(SandboxPolicy.CONSTRAINED, configuration.sandboxPolicy);
            }
            try (Context context = newContextBuilder(engine, UntrustedLanguage.ID).sandbox(configuration.sandboxPolicy).out(System.out).build()) {
                assertAtMost(SandboxPolicy.TRUSTED, configuration.sandboxPolicy);
            } catch (IllegalArgumentException iae) {
                assertSandboxPolicyException(iae, "Builder uses the standard output stream, but the output must be redirected.");
                assertAtLeast(SandboxPolicy.CONSTRAINED, configuration.sandboxPolicy);
            }
            try (Context context = newContextBuilder(engine, UntrustedLanguage.ID).sandbox(configuration.sandboxPolicy).err(System.err).build()) {
                assertAtMost(SandboxPolicy.TRUSTED, configuration.sandboxPolicy);
            } catch (IllegalArgumentException iae) {
                assertSandboxPolicyException(iae, "Builder uses the standard error stream, but the error output must be redirected.");
                assertAtLeast(SandboxPolicy.CONSTRAINED, configuration.sandboxPolicy);
            }
            Context context = newContextBuilder(engine, UntrustedLanguage.ID).sandbox(configuration.sandboxPolicy).build();
            context.close();
            context = newContextBuilder(engine, UntrustedLanguage.ID).sandbox(configuration.sandboxPolicy).out(OutputStream.nullOutputStream()).err(
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
        try (Context context = newContextBuilder(null, ConstrainedLanguage.ID).sandbox(configuration.sandboxPolicy).allowIO(true).build()) {
            assertAtMost(SandboxPolicy.TRUSTED, configuration.sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            if (filterUnsupportedIsolate(configuration, iae)) {
                assertSandboxPolicyException(iae, "Builder.allowIO(boolean) is set to true, but must not be set to true.");
                assertAtLeast(SandboxPolicy.CONSTRAINED, configuration.sandboxPolicy);
            }
        }
        try (Context context = newContextBuilder(null, UntrustedLanguage.ID).sandbox(configuration.sandboxPolicy).allowIO(false).build()) {
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
        try (Context context = newContextBuilder(null, ConstrainedLanguage.ID).sandbox(configuration.sandboxPolicy).allowIO(IOAccess.ALL).build()) {
            assertAtMost(SandboxPolicy.TRUSTED, configuration.sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            if (filterUnsupportedIsolate(configuration, iae)) {
                assertSandboxPolicyException(iae,
                                "Builder.allowIO(IOAccess) is set to an IOAccess, which allows access to the host file system, but access to the host file system must be disabled.");
                assertAtLeast(SandboxPolicy.CONSTRAINED, configuration.sandboxPolicy);
            }
        }
        try (Context context = newContextBuilder(null, ConstrainedLanguage.ID).sandbox(configuration.sandboxPolicy).allowIO(IOAccess.newBuilder().allowHostFileAccess(true).build()).build()) {
            assertAtMost(SandboxPolicy.TRUSTED, configuration.sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            if (filterUnsupportedIsolate(configuration, iae)) {
                assertSandboxPolicyException(iae,
                                "Builder.allowIO(IOAccess) is set to an IOAccess, which allows access to the host file system, but access to the host file system must be disabled.");
                assertAtLeast(SandboxPolicy.CONSTRAINED, configuration.sandboxPolicy);
            }
        }
        try (Context context = newContextBuilder(null, ConstrainedLanguage.ID).sandbox(configuration.sandboxPolicy).allowIO(
                        IOAccess.newBuilder().allowHostSocketAccess(true).build()).build()) {
            assertAtMost(SandboxPolicy.TRUSTED, configuration.sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            if (filterUnsupportedIsolate(configuration, iae)) {
                assertSandboxPolicyException(iae, "Builder.allowIO(IOAccess) is set to an IOAccess, which allows access to host sockets, but access to host sockets must be disabled.");
                assertAtLeast(SandboxPolicy.CONSTRAINED, configuration.sandboxPolicy);
            }
        }
        try (Context context = newContextBuilder(null, ConstrainedLanguage.ID).sandbox(configuration.sandboxPolicy).allowIO(
                        IOAccess.newBuilder().fileSystem(FileSystem.newReadOnlyFileSystem(FileSystem.newDefaultFileSystem())).build()).build()) {
            assertAtMost(SandboxPolicy.TRUSTED, configuration.sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            if (filterUnsupportedIsolate(configuration, iae)) {
                assertSandboxPolicyException(iae,
                                "Builder.allowIO(IOAccess) is set to an IOAccess, which has a custom file system that allows access to the host file system, but access to the host file system must be disabled.");
                assertAtLeast(SandboxPolicy.CONSTRAINED, configuration.sandboxPolicy);
            }
        }
        try (Context context = newContextBuilder(null, UntrustedLanguage.ID).sandbox(configuration.sandboxPolicy).allowIO(
                        IOAccess.newBuilder().fileSystem(new MemoryFileSystem("/tmp")).build()).build()) {
            assertAtMost(SandboxPolicy.UNTRUSTED, configuration.sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            if (filterUnsupportedIsolate(configuration, iae)) {
                throw iae;
            }
        }
        try (Context context = newContextBuilder(null, UntrustedLanguage.ID).sandbox(configuration.sandboxPolicy).allowIO(IOAccess.NONE).build()) {
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
        try (Context context = newContextBuilder(null, ConstrainedLanguage.ID).sandbox(configuration.sandboxPolicy).allowEnvironmentAccess(EnvironmentAccess.INHERIT).build()) {
            assertAtMost(SandboxPolicy.TRUSTED, configuration.sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            if (filterUnsupportedIsolate(configuration, iae)) {
                assertSandboxPolicyException(iae, "Builder.allowEnvironmentAccess(EnvironmentAccess) is set to INHERIT, but must be set to EnvironmentAccess.NONE.");
                assertAtLeast(SandboxPolicy.CONSTRAINED, configuration.sandboxPolicy);
            }
        }
        try (Context context = newContextBuilder(null, UntrustedLanguage.ID).sandbox(configuration.sandboxPolicy).allowEnvironmentAccess(EnvironmentAccess.NONE).build()) {
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
        try (Context context = newContextBuilder(null, ConstrainedLanguage.ID).sandbox(configuration.sandboxPolicy).allowHostAccess(true).build()) {
            assertAtMost(SandboxPolicy.TRUSTED, configuration.sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            if (filterUnsupportedIsolate(configuration, iae)) {
                assertSandboxPolicyException(iae, "Builder.allowHostAccess(boolean) is set to true, but must not be set to true.");
                assertAtLeast(SandboxPolicy.CONSTRAINED, configuration.sandboxPolicy);
            }
        }
        HostAccess hostAccess = HostAccess.newBuilder().allowPublicAccess(true).build();
        try (Context context = newContextBuilder(null, ConstrainedLanguage.ID).sandbox(configuration.sandboxPolicy).allowHostAccess(hostAccess).build()) {
            assertAtMost(SandboxPolicy.TRUSTED, configuration.sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            if (filterUnsupportedIsolate(configuration, iae)) {
                assertSandboxPolicyException(iae,
                                "Builder.allowHostAccess(HostAccess) is set to a HostAccess which was created with HostAccess.Builder.allowPublicAccess(boolean) set to true");
                assertAtLeast(SandboxPolicy.CONSTRAINED, configuration.sandboxPolicy);
            }
        }
        hostAccess = HostAccess.newBuilder().allowAccessInheritance(true).build();
        try (Context context = newContextBuilder(null, ConstrainedLanguage.ID).sandbox(configuration.sandboxPolicy).allowHostAccess(hostAccess).build()) {
            assertAtMost(SandboxPolicy.TRUSTED, configuration.sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            if (filterUnsupportedIsolate(configuration, iae)) {
                assertSandboxPolicyException(iae,
                                "Builder.allowHostAccess(HostAccess) is set to a HostAccess which was created with HostAccess.Builder.allowAccessInheritance(boolean) set to true");
                assertAtLeast(SandboxPolicy.CONSTRAINED, configuration.sandboxPolicy);
            }
        }
        hostAccess = HostAccess.newBuilder().allowMutableTargetMappings(HostAccess.MutableTargetMapping.EXECUTABLE_TO_JAVA_INTERFACE).build();
        try (Context context = newContextBuilder(null, ConstrainedLanguage.ID).sandbox(configuration.sandboxPolicy).allowHostAccess(hostAccess).build()) {
            assertAtMost(SandboxPolicy.TRUSTED, configuration.sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            if (filterUnsupportedIsolate(configuration, iae)) {
                assertSandboxPolicyException(iae,
                                "Builder.allowHostAccess(HostAccess) is set to a HostAccess which allows host object mappings of mutable target types, but it must not be enabled.");
                assertAtLeast(SandboxPolicy.CONSTRAINED, configuration.sandboxPolicy);
            }
        }
        hostAccess = HostAccess.newBuilder().allowAccessAnnotatedBy(HostAccess.Export.class).allowImplementationsAnnotatedBy(HostAccess.Implementable.class).allowMutableTargetMappings().build();
        try (Context context = newContextBuilder(null, ConstrainedLanguage.ID).sandbox(configuration.sandboxPolicy).allowHostAccess(hostAccess).build()) {
            assertAtMost(SandboxPolicy.CONSTRAINED, configuration.sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            if (filterUnsupportedIsolate(configuration, iae)) {
                assertSandboxPolicyException(iae, "Builder.allowHostAccess(HostAccess) is set to a HostAccess which has no HostAccess.Builder.methodScoping(boolean) set to true");
                assertAtLeast(SandboxPolicy.ISOLATED, configuration.sandboxPolicy);
            }
        }
        try (Context context = newContextBuilder(null, UntrustedLanguage.ID).sandbox(configuration.sandboxPolicy).allowHostAccess(null).build()) {
            assertAtMost(SandboxPolicy.UNTRUSTED, configuration.sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            if (filterUnsupportedIsolate(configuration, iae)) {
                throw iae;
            }
        }
        hostAccess = HostAccess.newBuilder().allowAccessAnnotatedBy(HostAccess.Export.class).allowMutableTargetMappings().methodScoping(true).build();
        try (Context context = newContextBuilder(null, UntrustedLanguage.ID).sandbox(configuration.sandboxPolicy).allowHostAccess(hostAccess).build()) {
            assertAtMost(SandboxPolicy.UNTRUSTED, configuration.sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            if (filterUnsupportedIsolate(configuration, iae)) {
                throw iae;
            }
        }
        hostAccess = HostAccess.newBuilder().allowAllClassImplementations(true).build();
        try (Context context = newContextBuilder(null, ConstrainedLanguage.ID).sandbox(configuration.sandboxPolicy).allowHostAccess(hostAccess).build()) {
            assertAtMost(SandboxPolicy.TRUSTED, configuration.sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            if (filterUnsupportedIsolate(configuration, iae)) {
                assertSandboxPolicyException(iae,
                                "Builder.allowHostAccess(HostAccess) is set to a HostAccess which was created with HostAccess.Builder.allowAllClassImplementations(boolean) set to true");
                assertAtLeast(SandboxPolicy.CONSTRAINED, configuration.sandboxPolicy);
            }
        }
        hostAccess = HostAccess.newBuilder().allowAllImplementations(true).build();
        try (Context context = newContextBuilder(null, ConstrainedLanguage.ID).sandbox(configuration.sandboxPolicy).allowHostAccess(hostAccess).build()) {
            assertAtMost(SandboxPolicy.TRUSTED, configuration.sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            if (filterUnsupportedIsolate(configuration, iae)) {
                assertSandboxPolicyException(iae,
                                "Builder.allowHostAccess(HostAccess) is set to a HostAccess which was created with HostAccess.Builder.allowAllImplementations(boolean) set to true");
                assertAtLeast(SandboxPolicy.CONSTRAINED, configuration.sandboxPolicy);
            }
        }
        hostAccess = HostAccess.newBuilder().allowImplementationsAnnotatedBy(FunctionalInterface.class).build();
        try (Context context = newContextBuilder(null, ConstrainedLanguage.ID).sandbox(configuration.sandboxPolicy).allowHostAccess(hostAccess).build()) {
            assertAtMost(SandboxPolicy.TRUSTED, configuration.sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            if (filterUnsupportedIsolate(configuration, iae)) {
                assertSandboxPolicyException(iae, "Builder.allowHostAccess(HostAccess) is set to a HostAccess which allows FunctionalInterface implementations");
                assertAtLeast(SandboxPolicy.CONSTRAINED, configuration.sandboxPolicy);
            }
        }
        // HostAccess.EXPLICIT fails for sandboxPolicy >= CONSTRAINED because it allows mutable
        // target mappings, allows FunctionalInterface implementations and has no method scoping
        try (Context context = newContextBuilder(null, ConstrainedLanguage.ID).sandbox(configuration.sandboxPolicy).allowHostAccess(HostAccess.EXPLICIT).build()) {
            assertAtMost(SandboxPolicy.TRUSTED, configuration.sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            if (filterUnsupportedIsolate(configuration, iae)) {
                assertSandboxPolicyException(iae, "Builder.allowHostAccess(HostAccess) is set to a HostAccess which allows FunctionalInterface implementations");
                assertAtLeast(SandboxPolicy.CONSTRAINED, configuration.sandboxPolicy);
            }
        }
        // HostAccess.NONE fails for sandboxPolicy >= CONSTRAINED because it allows mutable target
        // mappings and has no method scoping
        try (Context context2 = newContextBuilder(null, ConstrainedLanguage.ID).sandbox(configuration.sandboxPolicy).allowHostAccess(HostAccess.NONE).build()) {
            assertAtMost(SandboxPolicy.TRUSTED, configuration.sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            if (filterUnsupportedIsolate(configuration, iae)) {
                assertSandboxPolicyException(iae,
                                "Builder.allowHostAccess(HostAccess) is set to a HostAccess which allows host object mappings of mutable target types, but it must not be enabled.");
                assertAtLeast(SandboxPolicy.CONSTRAINED, configuration.sandboxPolicy);
            }
        }
        hostAccess = HostAccess.newBuilder(HostAccess.NONE).allowMutableTargetMappings().build();
        try (Context context = newContextBuilder(null, ConstrainedLanguage.ID).sandbox(configuration.sandboxPolicy).allowHostAccess(hostAccess).build()) {
            assertAtMost(SandboxPolicy.CONSTRAINED, configuration.sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            if (filterUnsupportedIsolate(configuration, iae)) {
                assertSandboxPolicyException(iae, "Builder.allowHostAccess(HostAccess) is set to a HostAccess which has no HostAccess.Builder.methodScoping(boolean) set to true");
                assertAtLeast(SandboxPolicy.ISOLATED, configuration.sandboxPolicy);
            }
        }
        hostAccess = HostAccess.newBuilder(HostAccess.NONE).allowMutableTargetMappings().methodScoping(true).build();
        try (Context context = newContextBuilder(null, UntrustedLanguage.ID).sandbox(configuration.sandboxPolicy).allowHostAccess(hostAccess).build()) {
            assertAtMost(SandboxPolicy.UNTRUSTED, configuration.sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            if (filterUnsupportedIsolate(configuration, iae)) {
                throw iae;
            }
        }
        hostAccess = HostAccess.UNTRUSTED;
        try (Context context = newContextBuilder(null, UntrustedLanguage.ID).sandbox(configuration.sandboxPolicy).allowHostAccess(hostAccess).build()) {
            assertAtMost(SandboxPolicy.UNTRUSTED, configuration.sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            if (filterUnsupportedIsolate(configuration, iae)) {
                throw iae;
            }
        }
        hostAccess = HostAccess.newBuilder(HostAccess.UNTRUSTED).allowImplementationsAnnotatedBy(HostAccess.Implementable.class).build();
        try (Context context = newContextBuilder(null, UntrustedLanguage.ID).sandbox(configuration.sandboxPolicy).allowHostAccess(hostAccess).build()) {
            assertAtMost(SandboxPolicy.ISOLATED, configuration.sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            if (filterUnsupportedIsolate(configuration, iae)) {
                assertSandboxPolicyException(iae, "Builder.allowHostAccess(HostAccess) is set to a HostAccess which allows implementations of types annotated by Implementable");
                assertAtLeast(SandboxPolicy.UNTRUSTED, configuration.sandboxPolicy);
            }
        }
        hostAccess = HostAccess.newBuilder(HostAccess.UNTRUSTED).allowArrayAccess(true).build();
        try (Context context = newContextBuilder(null, UntrustedLanguage.ID).sandbox(configuration.sandboxPolicy).allowHostAccess(hostAccess).build()) {
            assertAtMost(SandboxPolicy.ISOLATED, configuration.sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            if (filterUnsupportedIsolate(configuration, iae)) {
                assertSandboxPolicyException(iae,
                                "Builder.allowHostAccess(HostAccess) is set to a HostAccess which was created with HostAccess.Builder.allowArrayAccess(boolean) set to true");
                assertAtLeast(SandboxPolicy.UNTRUSTED, configuration.sandboxPolicy);
            }
        }
        hostAccess = HostAccess.newBuilder(HostAccess.UNTRUSTED).allowListAccess(true).build();
        try (Context context = newContextBuilder(null, UntrustedLanguage.ID).sandbox(configuration.sandboxPolicy).allowHostAccess(hostAccess).build()) {
            assertAtMost(SandboxPolicy.ISOLATED, configuration.sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            if (filterUnsupportedIsolate(configuration, iae)) {
                assertSandboxPolicyException(iae,
                                "Builder.allowHostAccess(HostAccess) is set to a HostAccess which was created with HostAccess.Builder.allowListAccess(boolean) set to true");
                assertAtLeast(SandboxPolicy.UNTRUSTED, configuration.sandboxPolicy);
            }
        }
        hostAccess = HostAccess.newBuilder(HostAccess.UNTRUSTED).allowBufferAccess(true).build();
        try (Context context = newContextBuilder(null, UntrustedLanguage.ID).sandbox(configuration.sandboxPolicy).allowHostAccess(hostAccess).build()) {
            assertAtMost(SandboxPolicy.ISOLATED, configuration.sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            if (filterUnsupportedIsolate(configuration, iae)) {
                assertSandboxPolicyException(iae,
                                "Builder.allowHostAccess(HostAccess) is set to a HostAccess which was created with HostAccess.Builder.allowBufferAccess(boolean) set to true");
                assertAtLeast(SandboxPolicy.UNTRUSTED, configuration.sandboxPolicy);
            }
        }
        hostAccess = HostAccess.newBuilder(HostAccess.UNTRUSTED).allowIterableAccess(true).build();
        try (Context context = newContextBuilder(null, UntrustedLanguage.ID).sandbox(configuration.sandboxPolicy).allowHostAccess(hostAccess).build()) {
            assertAtMost(SandboxPolicy.ISOLATED, configuration.sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            if (filterUnsupportedIsolate(configuration, iae)) {
                assertSandboxPolicyException(iae,
                                "Builder.allowHostAccess(HostAccess) is set to a HostAccess which was created with HostAccess.Builder.allowIterableAccess(boolean) set to true");
                assertAtLeast(SandboxPolicy.UNTRUSTED, configuration.sandboxPolicy);
            }
        }
        hostAccess = HostAccess.newBuilder(HostAccess.UNTRUSTED).allowIteratorAccess(true).build();
        try (Context context = newContextBuilder(null, UntrustedLanguage.ID).sandbox(configuration.sandboxPolicy).allowHostAccess(hostAccess).build()) {
            assertAtMost(SandboxPolicy.ISOLATED, configuration.sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            if (filterUnsupportedIsolate(configuration, iae)) {
                assertSandboxPolicyException(iae,
                                "Builder.allowHostAccess(HostAccess) is set to a HostAccess which was created with HostAccess.Builder.allowIteratorAccess(boolean) set to true");
                assertAtLeast(SandboxPolicy.UNTRUSTED, configuration.sandboxPolicy);
            }
        }
        hostAccess = HostAccess.newBuilder(HostAccess.UNTRUSTED).allowMapAccess(true).build();
        try (Context context = newContextBuilder(null, UntrustedLanguage.ID).sandbox(configuration.sandboxPolicy).allowHostAccess(hostAccess).build()) {
            assertAtMost(SandboxPolicy.ISOLATED, configuration.sandboxPolicy);
        } catch (IllegalArgumentException iae) {
            if (filterUnsupportedIsolate(configuration, iae)) {
                assertSandboxPolicyException(iae,
                                "Builder.allowHostAccess(HostAccess) is set to a HostAccess which was created with HostAccess.Builder.allowMapAccess(boolean) set to true");
                assertAtLeast(SandboxPolicy.UNTRUSTED, configuration.sandboxPolicy);
            }
        }
    }

    @Test
    @SuppressWarnings("try")
    public void testCompilationFailureAction() {
        Assume.assumeTrue(configuration.hasIsolateLibrary() || !(Truffle.getRuntime() instanceof DefaultTruffleRuntime));
        if (configuration.sandboxPolicy.isStricterOrEqual(SandboxPolicy.CONSTRAINED) &&
                        (SandboxPolicy.ISOLATED.isStricterThan(configuration.sandboxPolicy) || configuration.hasIsolateLibrary())) {
            for (String exceptionAction : new String[]{"Silent", "Print", "Throw", "Diagnose", "ExitVM"}) {
                try (Engine engine = newEngineBuilder(UntrustedLanguage.ID).sandbox(configuration.sandboxPolicy).option("engine.CompilationFailureAction",
                                exceptionAction).build()) {
                    assertTrue("Silent".equals(exceptionAction) || "Print".equals(exceptionAction));
                } catch (IllegalArgumentException iae) {
                    assertTrue(!"Silent".equals(exceptionAction) && !"Print".equals(exceptionAction));
                    assertTrue(iae.getMessage().contains("is set to " + exceptionAction + ", but must be set to Silent or Print"));
                }
            }
        }
    }

    @Test
    @SuppressWarnings({"try"})
    public void testCompilerThreads() {
        Assume.assumeTrue(configuration.hasIsolateLibrary() || !(Truffle.getRuntime() instanceof DefaultTruffleRuntime));
        if (configuration.sandboxPolicy.isStricterOrEqual(SandboxPolicy.CONSTRAINED) &&
                        (SandboxPolicy.ISOLATED.isStricterThan(configuration.sandboxPolicy) || configuration.hasIsolateLibrary())) {
            try (Engine engine = newEngineBuilder(UntrustedLanguage.ID).sandbox(configuration.sandboxPolicy).option("engine.CompilerThreads", "1").build()) {
                // deliberately empty
            }
        }
    }

    private Engine.Builder newEngineBuilder(String... permittedLanguages) {
        Engine.Builder builder = Engine.newBuilder(permittedLanguages).out(OutputStream.nullOutputStream()).err(OutputStream.nullOutputStream());
        if (configuration.sandboxPolicy.isStricterOrEqual(SandboxPolicy.ISOLATED) && configuration.supportsIsolatedPolicy) {
            builder.option("engine.MaxIsolateMemory", "4GB");
        }
        return builder;
    }

    private Context.Builder newContextBuilder(Engine explicitEngine, String... permittedLanguages) {
        Context.Builder builder = Context.newBuilder(permittedLanguages).out(OutputStream.nullOutputStream()).err(OutputStream.nullOutputStream());
        if (explicitEngine != null) {
            builder.engine(explicitEngine);
        }
        if (explicitEngine == null && configuration.sandboxPolicy.isStricterOrEqual(SandboxPolicy.ISOLATED) && configuration.supportsIsolatedPolicy) {
            builder.option("engine.MaxIsolateMemory", "4GB");
        }
        if (configuration.supportsSandboxInstrument) {
            builder.option("sandbox.MaxHeapMemory", "1GB");
            builder.option("sandbox.MaxCPUTime", "100s");
            builder.option("sandbox.MaxASTDepth", "1000");
            builder.option("sandbox.MaxStackFrames", "50");
            builder.option("sandbox.MaxThreads", "1");
            builder.option("sandbox.MaxOutputStreamSize", "0B");
            builder.option("sandbox.MaxErrorStreamSize", "0B");
        }
        return builder;
    }

    private static void assertAtLeast(SandboxPolicy expectedMinimalPolicy, SandboxPolicy actualPolicy) {
        assertTrue(actualPolicy.isStricterOrEqual(expectedMinimalPolicy));

    }

    private static void assertAtMost(SandboxPolicy expectedMaximalPolicy, SandboxPolicy actualPolicy) {
        assertTrue(expectedMaximalPolicy.isStricterOrEqual(actualPolicy));
    }

    private static void assertSandboxPolicyException(IllegalArgumentException exception, String expectedTextFormat, Object... formatArguments) {
        var expectedText = String.format(expectedTextFormat, formatArguments);
        var exceptionMessage = exception.getMessage();
        var message = String.format("The exception message '%s' does not contain '%s'", exceptionMessage, expectedText);
        assertTrue(message, exceptionMessage.contains(expectedText));
    }

    private static boolean filterUnsupportedIsolate(Configuration configuration, IllegalArgumentException exception) {
        if (configuration.sandboxPolicy.isStricterOrEqual(SandboxPolicy.ISOLATED)) {
            if (!configuration.supportsIsolatedPolicy && exception.getMessage().contains(String.format(UNSUPPORTED_ISOLATE_POLICY_MESSAGE, configuration.sandboxPolicy))) {
                return false;
            } else if (!configuration.hasIsolateLibrary && exception.getMessage().startsWith(MISSING_ISOLATE_LIBRARY_MESSAGE)) {
                return false;
            }
        }
        return true;
    }

    record Configuration(SandboxPolicy sandboxPolicy, boolean supportsIsolatedPolicy, boolean supportsSandboxInstrument, boolean hasIsolateLibrary) {
        @Override
        public String toString() {
            return sandboxPolicy.name();
        }
    }

    @TruffleLanguage.Registration(id = TrustedLanguage.ID, name = TrustedLanguage.ID, characterMimeTypes = TrustedLanguage.MIME)
    static final class TrustedLanguage extends TruffleLanguage<TruffleLanguage.Env> {
        static final String ID = "lang_sandbox_trusted";
        static final String MIME = "text/x-" + ID;

        @Override
        protected Env createContext(Env env) {
            return env;
        }
    }

    @TruffleLanguage.Registration(id = ConstrainedLanguage.ID, name = ConstrainedLanguage.ID, characterMimeTypes = ConstrainedLanguage.MIME, sandbox = SandboxPolicy.CONSTRAINED)
    static final class ConstrainedLanguage extends TruffleLanguage<TruffleLanguage.Env> {
        static final String ID = "lang_sandbox_constrained";
        static final String MIME = "text/x-" + ID;

        @Override
        protected Env createContext(Env env) {
            return env;
        }
    }

    @TruffleLanguage.Registration(id = IsolatedLanguage.ID, name = IsolatedLanguage.ID, characterMimeTypes = IsolatedLanguage.MIME, sandbox = SandboxPolicy.ISOLATED)
    static final class IsolatedLanguage extends TruffleLanguage<TruffleLanguage.Env> {
        static final String ID = "lang_sandbox_isolated";
        static final String MIME = "text/x-" + ID;

        @Override
        protected Env createContext(Env env) {
            return env;
        }
    }

    @TruffleLanguage.Registration(id = UntrustedLanguage.ID, name = UntrustedLanguage.ID, characterMimeTypes = UntrustedLanguage.MIME, sandbox = SandboxPolicy.UNTRUSTED)
    static final class UntrustedLanguage extends TruffleLanguage<TruffleLanguage.Env> {
        static final String ID = "lang_sandbox_untrusted";
        static final String MIME = "text/x-" + ID;

        @Option(category = OptionCategory.EXPERT, stability = OptionStability.STABLE, help = "Option allowed only in trusted policy")//
        static final OptionKey<Boolean> TrustedOption = new OptionKey<>(false);

        @Option(category = OptionCategory.EXPERT, stability = OptionStability.STABLE, help = "Option allowed only in trusted and constrained policy", sandbox = SandboxPolicy.CONSTRAINED)//
        static final OptionKey<Boolean> ConstrainedOption = new OptionKey<>(false);

        @Option(category = OptionCategory.EXPERT, stability = OptionStability.STABLE, help = "Option allowed in all policies", sandbox = SandboxPolicy.UNTRUSTED)//
        static final OptionKey<Boolean> UntrustedOption = new OptionKey<>(false);

        @Option(category = OptionCategory.EXPERT, stability = OptionStability.STABLE, help = "OptionMap allowed only in trusted policy")//
        static final OptionKey<OptionMap<String>> TrustedOptionMap = OptionKey.mapOf(String.class);

        @Option(category = OptionCategory.EXPERT, stability = OptionStability.STABLE, help = "OptionMap allowed only in trusted and constrained policy", sandbox = SandboxPolicy.CONSTRAINED)//
        static final OptionKey<OptionMap<String>> ConstrainedOptionMap = OptionKey.mapOf(String.class);

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

        static final String ID = "tool_sandbox_trusted";

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

    @TruffleInstrument.Registration(id = ConstrainedInstrument.ID, name = ConstrainedInstrument.ID, sandbox = SandboxPolicy.CONSTRAINED)
    static final class ConstrainedInstrument extends TruffleInstrument {

        static final String ID = "tool_sandbox_constrained";

        @Option(category = OptionCategory.EXPERT, stability = OptionStability.STABLE, help = "Enables instrument", sandbox = SandboxPolicy.UNTRUSTED)//
        static final OptionKey<Boolean> Enable = new OptionKey<>(false);

        @Override
        protected void onCreate(Env env) {
        }

        @Override
        protected OptionDescriptors getOptionDescriptors() {
            return new ConstrainedInstrumentOptionDescriptors();
        }
    }

    @TruffleInstrument.Registration(id = IsolatedInstrument.ID, name = IsolatedInstrument.ID, sandbox = SandboxPolicy.ISOLATED)
    static final class IsolatedInstrument extends TruffleInstrument {

        static final String ID = "tool_sandbox_isolated";

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

    @TruffleInstrument.Registration(id = UntrustedInstrument.ID, name = UntrustedInstrument.ID, sandbox = SandboxPolicy.UNTRUSTED)
    static final class UntrustedInstrument extends TruffleInstrument {

        static final String ID = "tool_sandbox_untrusted";

        @Option(category = OptionCategory.EXPERT, stability = OptionStability.STABLE, help = "Enables instrument", sandbox = SandboxPolicy.UNTRUSTED)//
        static final OptionKey<Boolean> Enable = new OptionKey<>(false);

        @Option(category = OptionCategory.EXPERT, stability = OptionStability.STABLE, help = "Option allowed only in trusted policy")//
        static final OptionKey<Boolean> TrustedOption = new OptionKey<>(false);

        @Option(category = OptionCategory.EXPERT, stability = OptionStability.STABLE, help = "Option allowed only in trusted and constrained policy", sandbox = SandboxPolicy.CONSTRAINED)//
        static final OptionKey<Boolean> ConstrainedOption = new OptionKey<>(false);

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

        @Option(category = OptionCategory.EXPERT, stability = OptionStability.STABLE, help = "Option allowed only in trusted and constrained policy", sandbox = SandboxPolicy.CONSTRAINED)//
        static final OptionKey<Boolean> ContextConstrainedOption = new OptionKey<>(false);

        @Option(category = OptionCategory.EXPERT, stability = OptionStability.STABLE, help = "Option allowed in all policies", sandbox = SandboxPolicy.UNTRUSTED)//
        static final OptionKey<Boolean> ContextUntrustedOption = new OptionKey<>(false);
    }

    private static final class MockMessageTransport implements MessageTransport {
        @Override
        public MessageEndpoint open(URI uri, MessageEndpoint peerEndpoint) {
            throw new UnsupportedOperationException();
        }
    }
}
