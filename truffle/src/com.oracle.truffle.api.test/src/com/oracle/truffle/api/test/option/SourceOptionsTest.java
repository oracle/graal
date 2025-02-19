/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.test.option;

import static com.oracle.truffle.api.test.polyglot.AbstractPolyglotTest.assertFails;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.util.Set;

import org.graalvm.options.OptionCategory;
import org.graalvm.options.OptionDescriptor;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionKey;
import org.graalvm.options.OptionStability;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Language;
import org.graalvm.polyglot.PolyglotAccess;
import org.graalvm.polyglot.SandboxPolicy;
import org.graalvm.polyglot.Source;
import org.junit.Assume;
import org.junit.Test;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Option;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.tck.tests.TruffleTestAssumptions;

public class SourceOptionsTest {

    static final String STABLE_OPTION_NAME = SourceOptionsTestLanguage.ID + ".StableOption";
    static final String DEPRECATED_OPTION_NAME = SourceOptionsTestLanguage.ID + ".DeprecatedOption";
    static final String EXPERIMENTAL_OPTION_NAME = SourceOptionsTestLanguage.ID + ".ExperimentalOption";

    static final String OTHER_STABLE_OPTION_NAME = SourceOptionsOtherLanguage.ID + ".StableOption";
    static final String OTHER_DEPRECATED_OPTION_NAME = SourceOptionsOtherLanguage.ID + ".DeprecatedOption";
    static final String OTHER_EXPERIMENTAL_OPTION_NAME = SourceOptionsOtherLanguage.ID + ".ExperimentalOption";

    static final String INSTRUMENT_STABLE_OPTION_NAME = SourceOptionsTestInstrument.ID + ".StableOption";

    @Test
    public void testStableOption() {
        try (Context c = Context.create(SourceOptionsTestLanguage.ID)) {
            assertEquals(42, c.eval(Source.newBuilder(SourceOptionsTestLanguage.ID, STABLE_OPTION_NAME, "test").buildLiteral()).asInt());
            assertEquals(43, c.eval(Source.newBuilder(SourceOptionsTestLanguage.ID, STABLE_OPTION_NAME, "test").option(STABLE_OPTION_NAME, "43").buildLiteral()).asInt());
        }
    }

    @Test
    public void testGetLanguageOptions() {
        try (Context c = Context.create(SourceOptionsTestLanguage.ID)) {
            Language l = c.getEngine().getLanguages().get(SourceOptionsTestLanguage.ID);

            OptionDescriptor descriptor = l.getSourceOptions().get(STABLE_OPTION_NAME);
            assertEquals(STABLE_OPTION_NAME, descriptor.getName());
            assertEquals(OptionCategory.EXPERT, descriptor.getCategory());
            assertEquals(OptionStability.STABLE, descriptor.getStability());
        }
    }

    @Test
    public void testOtherLanguageOption() {
        try (Context c = Context.create()) {
            assertEquals(43, c.eval(Source.newBuilder(SourceOptionsOtherLanguage.ID, OTHER_STABLE_OPTION_NAME, "test").option(OTHER_STABLE_OPTION_NAME, "43").buildLiteral()).asInt());
            assertEquals(42, c.eval(Source.newBuilder(SourceOptionsOtherLanguage.ID, OTHER_STABLE_OPTION_NAME, "test").option(STABLE_OPTION_NAME, "43").buildLiteral()).asInt());

            assertFails(() -> c.eval(Source.newBuilder(SourceOptionsOtherLanguage.ID, OTHER_STABLE_OPTION_NAME, "test").option(STABLE_OPTION_NAME, "not-a-number").buildLiteral()),
                            IllegalArgumentException.class, (e) -> {
                                assertEquals("""
                                                Failed to parse source option 'SourceOptionsTest_SourceOptionsTestLanguage.StableOption=not-a-number': For input string: "not-a-number"
                                                """.trim(), e.getMessage());
                            });

            assertFails(() -> c.eval(Source.newBuilder(SourceOptionsOtherLanguage.ID, OTHER_STABLE_OPTION_NAME, "test").option("invalid-component.option", "43").buildLiteral()),
                            IllegalArgumentException.class, (e) -> {
                                assertEquals("""
                                                Failed to parse source option 'invalid-component.option=43': Could not find option with name invalid-component.option.
                                                """.trim(), e.getMessage());
                            });

            assertFails(() -> c.eval(Source.newBuilder(SourceOptionsOtherLanguage.ID, OTHER_STABLE_OPTION_NAME, "test").option(SourceOptionsTestLanguage.ID + ".invalid-option", "43").buildLiteral()),
                            IllegalArgumentException.class, (e) -> {
                                assertEquals("""
                                                Failed to parse source option 'SourceOptionsTest_SourceOptionsTestLanguage.invalid-option=43': Could not find option with name SourceOptionsTest_SourceOptionsTestLanguage.invalid-option.
                                                Did you mean one of the following?
                                                    SourceOptionsTest_SourceOptionsOtherLanguage.DeprecatedOption=<Integer>
                                                    SourceOptionsTest_SourceOptionsOtherLanguage.ExperimentalOption=<Integer>
                                                    SourceOptionsTest_SourceOptionsOtherLanguage.StableOption=<Integer>
                                                    SourceOptionsTest_SourceOptionsTestLanguage.DeprecatedOption=<Integer>
                                                    SourceOptionsTest_SourceOptionsTestLanguage.ExperimentalOption=<Integer>
                                                    SourceOptionsTest_SourceOptionsTestLanguage.StableOption=<Integer>
                                                    SourceOptionsTest_SourceOptionsTestInstrument.StableOption=<Integer>
                                                """.trim().replace(
                                                "\n", System.lineSeparator()),
                                                e.getMessage());
                            });
        }
    }

    @Test
    public void testInstrumentSourceOption() {
        TruffleTestAssumptions.assumeWeakEncapsulation();

        try (Context c = Context.create()) {
            c.enter();

            TruffleInstrument.Env env = c.getEngine().getInstruments().get(SourceOptionsTestInstrument.ID).lookup(SourceOptionsTestInstrument.class).env;
            var source = com.oracle.truffle.api.source.Source.newBuilder("", "", "").build();
            assertEquals(42, (int) TestSourceInstrumentOptions.StableOption.getValue(env.getOptions(source)));

            source = com.oracle.truffle.api.source.Source.newBuilder("", "", "").option(INSTRUMENT_STABLE_OPTION_NAME, "43").build();
            assertEquals(43, (int) TestSourceInstrumentOptions.StableOption.getValue(env.getOptions(source)));

            source = com.oracle.truffle.api.source.Source.newBuilder("", "", "").option(STABLE_OPTION_NAME, "not-a-number").build();
            // other options are not parsed - expected to succeed even though the option is invalid
            assertEquals(42, (int) TestSourceInstrumentOptions.StableOption.getValue(env.getOptions(source)));

            source = com.oracle.truffle.api.source.Source.newBuilder("", "", "").option("invalid-component.invalid-name", "not-a-number").build();
            // other options are not parsed - expected to succeed even though the option is invalid
            assertEquals(42, (int) TestSourceInstrumentOptions.StableOption.getValue(env.getOptions(source)));

            source = com.oracle.truffle.api.source.Source.newBuilder("", "", "").option("arbitrary-text-as-key", "not-a-number").build();
            // other options are not parsed - expected to succeed even though the option is invalid
            assertEquals(42, (int) TestSourceInstrumentOptions.StableOption.getValue(env.getOptions(source)));

            var source2 = com.oracle.truffle.api.source.Source.newBuilder("", "", "").option(INSTRUMENT_STABLE_OPTION_NAME, "not-a-number").build();
            // other options are not parsed - expected to succeed even though the option is invalid
            assertFails(() -> env.getOptions(source2),
                            IllegalArgumentException.class, (e) -> {
                                assertEquals("""
                                                Failed to parse source option 'SourceOptionsTest_SourceOptionsTestInstrument.StableOption=not-a-number': For input string: "not-a-number"
                                                """.trim(), e.getMessage());
                            });
        }
    }

    @Test
    public void testLanguageSourceOption() {
        TruffleTestAssumptions.assumeWeakEncapsulation();

        try (Context c = Context.create()) {
            c.enter();
            c.initialize(SourceOptionsTestLanguage.ID);

            SourceOptionsTestLanguage lang = SourceOptionsTestLanguage.LANGUAGE_REF.get(null);
            var source = com.oracle.truffle.api.source.Source.newBuilder("", "", "").build();
            assertEquals(42, (int) TestSourceOptions.StableOption.getValue(source.getOptions(lang)));

            source = com.oracle.truffle.api.source.Source.newBuilder("", "", "").option(STABLE_OPTION_NAME, "43").build();
            assertEquals(43, (int) TestSourceOptions.StableOption.getValue(source.getOptions(lang)));

            source = com.oracle.truffle.api.source.Source.newBuilder("", "", "").option(OTHER_STABLE_OPTION_NAME, "not-a-number").build();
            // other options are not parsed - expected to succeed even though the option is invalid
            assertEquals(42, (int) TestSourceOptions.StableOption.getValue(source.getOptions(lang)));

            source = com.oracle.truffle.api.source.Source.newBuilder("", "", "").option("invalid-component.invalid-name", "not-a-number").build();
            // other options are not parsed - expected to succeed even though the option is invalid
            assertEquals(42, (int) TestSourceOptions.StableOption.getValue(source.getOptions(lang)));

            source = com.oracle.truffle.api.source.Source.newBuilder("", "", "").option("arbitrary-text-as-key", "not-a-number").build();
            // other options are not parsed - expected to succeed even though the option is invalid
            assertEquals(42, (int) TestSourceOptions.StableOption.getValue(source.getOptions(lang)));

            var source2 = com.oracle.truffle.api.source.Source.newBuilder("", "", "").option(STABLE_OPTION_NAME, "not-a-number").build();
            // other options are not parsed - expected to succeed even though the option is invalid
            assertFails(() -> source2.getOptions(lang),
                            IllegalArgumentException.class, (e) -> {
                                assertEquals("""
                                                Failed to parse source option 'SourceOptionsTest_SourceOptionsTestLanguage.StableOption=not-a-number': For input string: "not-a-number"
                                                """.trim(), e.getMessage());
                            });
        }
    }

    @Test
    public void testExperimentalOption() {
        try (Context c = Context.newBuilder(SourceOptionsTestLanguage.ID).allowExperimentalOptions(true).build()) {
            assertEquals(42, c.eval(Source.newBuilder(SourceOptionsTestLanguage.ID, EXPERIMENTAL_OPTION_NAME, "test").buildLiteral()).asInt());
            assertEquals(43,
                            c.eval(Source.newBuilder(SourceOptionsTestLanguage.ID, EXPERIMENTAL_OPTION_NAME, "test").option(EXPERIMENTAL_OPTION_NAME, "43").buildLiteral()).asInt());
        }

        try (Engine e = Engine.newBuilder(SourceOptionsTestLanguage.ID).allowExperimentalOptions(true).build()) {
            try (Context c = Context.newBuilder(SourceOptionsTestLanguage.ID).engine(e).allowExperimentalOptions(true).build()) {
                assertEquals(42, c.eval(Source.newBuilder(SourceOptionsTestLanguage.ID, EXPERIMENTAL_OPTION_NAME, "test").buildLiteral()).asInt());
                assertEquals(43,
                                c.eval(Source.newBuilder(SourceOptionsTestLanguage.ID, EXPERIMENTAL_OPTION_NAME, "test").option(EXPERIMENTAL_OPTION_NAME, "43").buildLiteral()).asInt());
            }
        }

        // can't perform this test if experimental options are allowed globally
        Assume.assumeFalse(Boolean.getBoolean("polyglot.engine.AllowExperimentalOptions"));

        String error = "Failed to parse source option 'SourceOptionsTest_SourceOptionsTestLanguage.ExperimentalOption=experimental': " +
                        "Option 'SourceOptionsTest_SourceOptionsTestLanguage.ExperimentalOption' is experimental and must be enabled with allowExperimentalOptions(boolean) " +
                        "in Context.Builder or Engine.Builder. Do not use experimental options in production environments.";
        try (Context c = Context.newBuilder(SourceOptionsTestLanguage.ID).allowExperimentalOptions(false).build()) {
            Source s1 = Source.newBuilder(SourceOptionsTestLanguage.ID, EXPERIMENTAL_OPTION_NAME, "test").option(EXPERIMENTAL_OPTION_NAME, "experimental").buildLiteral();
            assertFails(() -> c.eval(s1), IllegalArgumentException.class, (e) -> {
                assertEquals(error, e.getMessage());
            });
        }
    }

    @Test
    public void testDeprecatedOption() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        String expectedWarning = "[engine] WARNING: Option 'SourceOptionsTest_SourceOptionsTestLanguage.DeprecatedOption' is deprecated: TestMessage. " +
                        "Please update the option or suppress this warning using the option 'engine.WarnOptionDeprecation=false'.";
        try (Context c = Context.newBuilder(SourceOptionsTestLanguage.ID).logHandler(out).build()) {
            assertEquals(43,
                            c.eval(Source.newBuilder(SourceOptionsTestLanguage.ID, DEPRECATED_OPTION_NAME, "test").option(DEPRECATED_OPTION_NAME, "43").buildLiteral()).asInt());
            Set<String> log = Set.of(new String(out.toByteArray()).split(System.lineSeparator()));
            assertTrue(log.toString(), log.contains(expectedWarning));

            // test warn only once
            out.reset();
            assertEquals(43,
                            c.eval(Source.newBuilder(SourceOptionsTestLanguage.ID, DEPRECATED_OPTION_NAME, "test").option(DEPRECATED_OPTION_NAME, "43").buildLiteral()).asInt());
            log = Set.of(new String(out.toByteArray()).split(System.lineSeparator()));
            assertFalse(log.toString(), log.contains(expectedWarning));

        }

        // test warnings disabled
        out = new ByteArrayOutputStream();
        try (Context c = Context.newBuilder(SourceOptionsTestLanguage.ID).logHandler(out).option("engine.WarnOptionDeprecation", "false").build()) {
            assertEquals(43,
                            c.eval(Source.newBuilder(SourceOptionsTestLanguage.ID, DEPRECATED_OPTION_NAME, "test").option(DEPRECATED_OPTION_NAME, "43").buildLiteral()).asInt());
            Set<String> log = Set.of(new String(out.toByteArray()).split(System.lineSeparator()));
            assertFalse(log.toString(), log.contains(expectedWarning));
        }
    }

    /*
     * Internal use of experimental source options is always independent of the embedding config.
     * Otherwise experimental options would effectively never be usable internally.
     */
    @Test
    public void testExperimentalOptionInternalUse() {
        TruffleTestAssumptions.assumeWeakEncapsulation();
        try (Context c = Context.newBuilder().allowExperimentalOptions(false).allowPolyglotAccess(PolyglotAccess.ALL).build()) {
            c.enter();
            c.initialize(SourceOptionsTestLanguage.ID);
            Env env = SourceOptionsTestLanguage.REF.get(null);

            var source = com.oracle.truffle.api.source.Source.newBuilder(SourceOptionsTestLanguage.ID, EXPERIMENTAL_OPTION_NAME, "test").option(EXPERIMENTAL_OPTION_NAME, "42").build();
            assertEquals(42, env.parseInternal(source).call());

            source = com.oracle.truffle.api.source.Source.newBuilder(SourceOptionsOtherLanguage.ID, OTHER_EXPERIMENTAL_OPTION_NAME, "test").option(OTHER_EXPERIMENTAL_OPTION_NAME, "42").build();
            assertEquals(42, env.parseInternal(source).call());
        }
    }

    /*
     * Internal use of sandbox options are always allowed for internal parses independent of
     * embedding config. Otherwise non-sandbox options would effectively never be usable internally.
     */
    @Test
    public void testSandboxOptionInternalUse() {
        TruffleTestAssumptions.assumeWeakEncapsulation();

        // optimizing runtime fails as some options don't support sandboxing there.
        TruffleTestAssumptions.assumeFallbackRuntime();
        try (Context c = Context.newBuilder(SourceOptionsTestLanguage.ID, SourceOptionsOtherLanguage.ID) //
                        .out(OutputStream.nullOutputStream()).err(OutputStream.nullOutputStream()).sandbox(SandboxPolicy.CONSTRAINED).allowPolyglotAccess(PolyglotAccess.ALL).build()) {
            c.enter();
            c.initialize(SourceOptionsTestLanguage.ID);
            Env env = SourceOptionsTestLanguage.REF.get(null);

            var source = com.oracle.truffle.api.source.Source.newBuilder(SourceOptionsTestLanguage.ID, EXPERIMENTAL_OPTION_NAME, "test").option(EXPERIMENTAL_OPTION_NAME, "42").build();
            assertEquals(42, env.parseInternal(source).call());

            source = com.oracle.truffle.api.source.Source.newBuilder(SourceOptionsOtherLanguage.ID, OTHER_EXPERIMENTAL_OPTION_NAME, "test").option(OTHER_EXPERIMENTAL_OPTION_NAME, "42").build();
            assertEquals(42, env.parseInternal(source).call());
        }
    }

    @TruffleLanguage.Registration(id = SourceOptionsTestLanguage.ID, sandbox = SandboxPolicy.CONSTRAINED)
    public static final class SourceOptionsTestLanguage extends TruffleLanguage<Env> {

        public static final String ID = "SourceOptionsTest_SourceOptionsTestLanguage";

        public static final ContextReference<Env> REF = ContextReference.create(SourceOptionsTestLanguage.class);
        public static final LanguageReference<SourceOptionsTestLanguage> LANGUAGE_REF = LanguageReference.create(SourceOptionsTestLanguage.class);

        @Override
        protected OptionDescriptors getSourceOptionDescriptors() {
            return new TestSourceOptionsOptionDescriptors();
        }

        @Override
        protected Env createContext(Env env) {
            return env;
        }

        @Override
        protected CallTarget parse(ParsingRequest request) throws Exception {
            OptionKey<Integer> key;
            switch (request.getSource().getCharacters().toString()) {
                case STABLE_OPTION_NAME:
                    key = TestSourceOptions.StableOption;
                    break;
                case DEPRECATED_OPTION_NAME:
                    key = TestSourceOptions.DeprecatedOption;
                    break;
                case EXPERIMENTAL_OPTION_NAME:
                    key = TestSourceOptions.ExperimentalOption;
                    break;
                default:
                    throw new AssertionError();
            }
            return RootNode.createConstantNode(request.getOptionValues().get(key)).getCallTarget();
        }

    }

    @TruffleInstrument.Registration(id = SourceOptionsTestInstrument.ID, services = SourceOptionsTestInstrument.class)
    public static final class SourceOptionsTestInstrument extends TruffleInstrument {

        public static final String ID = "SourceOptionsTest_SourceOptionsTestInstrument";

        private TruffleInstrument.Env env;

        @Override
        protected OptionDescriptors getSourceOptionDescriptors() {
            return new TestSourceInstrumentOptionsOptionDescriptors();
        }

        @Override
        protected void onCreate(@SuppressWarnings("hiding") Env env) {
            this.env = env;
            env.registerService(this);
        }

    }

    @Option.Group(SourceOptionsTestLanguage.ID)
    static class TestSourceOptions {

        @Option(category = OptionCategory.EXPERT, stability = OptionStability.STABLE, help = "Test option") //
        static final OptionKey<Integer> StableOption = new OptionKey<>(42);

        @Option(category = OptionCategory.EXPERT, stability = OptionStability.STABLE, deprecated = true, deprecationMessage = "TestMessage", help = "Test option") //
        static final OptionKey<Integer> DeprecatedOption = new OptionKey<>(42);

        @Option(category = OptionCategory.EXPERT, stability = OptionStability.EXPERIMENTAL, help = "Test option") //
        static final OptionKey<Integer> ExperimentalOption = new OptionKey<>(42);

    }

    @Option.Group(SourceOptionsTestInstrument.ID)
    static class TestSourceInstrumentOptions {

        @Option(category = OptionCategory.EXPERT, stability = OptionStability.STABLE, help = "Test option") //
        static final OptionKey<Integer> StableOption = new OptionKey<>(42);

        @Option(category = OptionCategory.EXPERT, stability = OptionStability.STABLE, deprecated = true, deprecationMessage = "TestMessage", help = "Test option") //
        static final OptionKey<Integer> DeprecatedOption = new OptionKey<>(42);

        @Option(category = OptionCategory.EXPERT, stability = OptionStability.EXPERIMENTAL, help = "Test option") //
        static final OptionKey<Integer> ExperimentalOption = new OptionKey<>(42);

    }

    @TruffleLanguage.Registration(id = SourceOptionsOtherLanguage.ID, sandbox = SandboxPolicy.CONSTRAINED)
    public static final class SourceOptionsOtherLanguage extends TruffleLanguage<Env> {

        public static final String ID = "SourceOptionsTest_SourceOptionsOtherLanguage";

        @Override
        protected OptionDescriptors getSourceOptionDescriptors() {
            return new OtherSourceOptionsOptionDescriptors();
        }

        @Override
        protected Env createContext(Env env) {
            return env;
        }

        @Override
        protected CallTarget parse(ParsingRequest request) throws Exception {
            OptionKey<Integer> key;
            switch (request.getSource().getCharacters().toString()) {
                case OTHER_STABLE_OPTION_NAME:
                    key = OtherSourceOptions.StableOption;
                    break;
                case OTHER_DEPRECATED_OPTION_NAME:
                    key = OtherSourceOptions.DeprecatedOption;
                    break;
                case OTHER_EXPERIMENTAL_OPTION_NAME:
                    key = OtherSourceOptions.ExperimentalOption;
                    break;
                default:
                    throw new AssertionError();
            }
            return RootNode.createConstantNode(request.getOptionValues().get(key)).getCallTarget();
        }

    }

    @Option.Group(SourceOptionsOtherLanguage.ID)
    static class OtherSourceOptions {

        @Option(category = OptionCategory.EXPERT, stability = OptionStability.STABLE, help = "Test option") //
        static final OptionKey<Integer> StableOption = new OptionKey<>(42);

        @Option(category = OptionCategory.EXPERT, stability = OptionStability.STABLE, deprecated = true, deprecationMessage = "TestMessage", help = "Test option") //
        static final OptionKey<Integer> DeprecatedOption = new OptionKey<>(42);

        @Option(category = OptionCategory.EXPERT, stability = OptionStability.EXPERIMENTAL, help = "Test option") //
        static final OptionKey<Integer> ExperimentalOption = new OptionKey<>(42);

    }

}
