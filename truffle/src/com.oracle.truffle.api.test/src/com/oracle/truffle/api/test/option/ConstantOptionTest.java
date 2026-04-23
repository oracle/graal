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
package com.oracle.truffle.api.test.option;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.Option;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.test.ExpectError;
import com.oracle.truffle.api.test.common.AbstractExecutableTestLanguage;
import com.oracle.truffle.api.test.common.TestUtils;
import com.oracle.truffle.api.test.polyglot.AbstractPolyglotTest;
import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.options.ConstantOptionKey;
import org.graalvm.options.OptionCategory;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionKey;
import org.graalvm.options.OptionStability;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.SandboxPolicy;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Collection;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(Parameterized.class)
public class ConstantOptionTest {

    @BeforeClass
    public static void checkSystemProperty() {
        if (ImageInfo.inImageRuntimeCode()) {
            assertEquals(
                            "Test requires -Dpolyglot.ConstantOptionsLanguage.ConstantOption1=configuredValue is set during native-image build", //
                            "configuredValue", ConstantOptionsLanguage.ConstantOption1.getConstantValue());
            assertEquals(
                            "Test requires that -Dpolyglot.ConstantOptionsLanguage.ConstantOption2 is NOT set during native-image build", //
                            ConstantOptionsLanguage.ConstantOption2.getDefaultValue(), ConstantOptionsLanguage.ConstantOption2.getConstantValue());
            assertNull(
                            "Test requires that -Dpolyglot.ConstantOptionsLanguage.PreSetOption is NOT set", //
                            System.getProperty("polyglot.ConstantOptionsLanguage.PreSetOption"));
        } else {
            assertEquals(
                            "Test requires -Dpolyglot.ConstantOptionsLanguage.ConstantOption1=configuredValue", //
                            "configuredValue", System.getProperty("polyglot.ConstantOptionsLanguage.ConstantOption1"));
            assertNull(
                            "Test requires that -Dpolyglot.ConstantOptionsLanguage.ConstantOption2 is NOT set", //
                            System.getProperty("polyglot.ConstantOptionsLanguage.ConstantOption2"));
        }
    }

    public record Configuration(boolean useSystemProperties) {
    }

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Configuration> createParameters() {
        return List.of(new Configuration(true), new Configuration(false));
    }

    private final Configuration configuration;

    public ConstantOptionTest(Configuration configuration) {
        this.configuration = configuration;
    }

    @Option.Group("constant-error")
    public static class ConstantOptionError {

        @ExpectError("Option annotated with @Option.constant must use ConstantOptionKey, but found OptionKey. " +
                        "Either change the field type to ConstantOptionKey, or remove the @Option.constant attribute.") @Option(help = "", category = OptionCategory.USER, constant = true) //
        static final OptionKey<String> error1 = new OptionKey<>("defaultValue");

        @ExpectError("ConstantOptionKey can only be used with options annotated with @Option.constant, but this option is not constant. " +
                        "Either add the @Option.constant attribute, or change the field type to OptionKey.") @Option(help = "", category = OptionCategory.USER) //
        static final ConstantOptionKey<String> error2 = new ConstantOptionKey<>("defaultValue");
    }

    @Test
    public void testSetGetValue() {
        if (ImageInfo.inImageRuntimeCode()) {
            // Constant value cannot be set in image-runtime code
            ConstantOptionKey<String> key1 = new ConstantOptionKey<>("value");
            AbstractPolyglotTest.assertFails(() -> key1.setConstantValue("test"), IllegalStateException.class);
        } else {
            ConstantOptionKey<String> key1 = new ConstantOptionKey<>("value");
            key1.setConstantValue("test1");
            assertEquals("test1", key1.getConstantValue());

            ConstantOptionKey<String> key2 = new ConstantOptionKey<>("value");
            key2.setConstantValue("test1");
            // Constant value cannot be set overwritten
            AbstractPolyglotTest.assertFails(() -> key2.setConstantValue("test2"), IllegalStateException.class);
        }

        // Unset Constant value cannot be used
        ConstantOptionKey<String> key3 = new ConstantOptionKey<>("value");
        AbstractPolyglotTest.assertFails(key3::getConstantValue, IllegalStateException.class);
    }

    @Test
    public void testConstantOptionCannotBeOverridden() {
        String optionName = TestUtils.getDefaultLanguageId(ConstantOptionsLanguage.class) + ".ConstantOption1";
        Engine.Builder engineBuilder = Engine.newBuilder().//
                        useSystemProperties(configuration.useSystemProperties()).//
                        option("engine.WarnInterpreterOnly", "false").//
                        option(optionName, "test");
        AbstractPolyglotTest.assertFails(engineBuilder::build, IllegalArgumentException.class, (e) -> {
            assertTrue(e.getMessage().contains("is constant and is already fixed"));
        });

        Context.Builder builder = Context.newBuilder().//
                        option(optionName, "test");
        AbstractPolyglotTest.assertFails(builder::build, IllegalArgumentException.class, (e) -> {
            assertTrue(e.getMessage().contains("is constant and is already fixed"));
        });
    }

    @Test
    public void testConstantOptionCanBeSetToConstantValue() {
        Engine.Builder engineBuilder = Engine.newBuilder().//
                        useSystemProperties(configuration.useSystemProperties()).//
                        option("engine.WarnInterpreterOnly", "false");

        String optionName = TestUtils.getDefaultLanguageId(ConstantOptionsLanguage.class) + ".ConstantOption1";
        try (Engine engine = engineBuilder.build()) {
            try (Context context = Context.newBuilder().engine(engine).option(optionName, "configuredValue").build()) {
                // ConstantOption1 is set to configuredValue by mx_truffle.TruffleUnittestConfig
                AbstractExecutableTestLanguage.evalTestLanguage(context, ConstantOptionsLanguage.class, "", "ConstantOption1", "configuredValue");
            }
        }

        optionName = TestUtils.getDefaultLanguageId(ConstantOptionsLanguage.class) + ".ConstantOption2";
        try (Engine engine = engineBuilder.build()) {
            try (Context context = Context.newBuilder().engine(engine).option(optionName, "defaultValue").build()) {
                // ConstantOption2 is unset
                AbstractExecutableTestLanguage.evalTestLanguage(context, ConstantOptionsLanguage.class, "", "ConstantOption2", "defaultValue");
            }
        }

        optionName = TestUtils.getDefaultLanguageId(ConstantOptionsLanguage.class) + ".ConstantOption1";
        try (Context context = Context.newBuilder().option(optionName, "configuredValue").build()) {
            // ConstantOption1 is set to configuredValue by mx_truffle.TruffleUnittestConfig
            AbstractExecutableTestLanguage.evalTestLanguage(context, ConstantOptionsLanguage.class, "", "ConstantOption1", "configuredValue");
        }

        optionName = TestUtils.getDefaultLanguageId(ConstantOptionsLanguage.class) + ".ConstantOption2";
        try (Context context = Context.newBuilder().option(optionName, "defaultValue").build()) {
            // ConstantOption2 is unset
            AbstractExecutableTestLanguage.evalTestLanguage(context, ConstantOptionsLanguage.class, "", "ConstantOption2", "defaultValue");
        }
    }

    @Test
    public void testConstantValue() {
        Engine.Builder engineBuilder = Engine.newBuilder().//
                        useSystemProperties(configuration.useSystemProperties()).//
                        option("engine.WarnInterpreterOnly", "false");
        try (Engine engine = engineBuilder.build()) {
            try (Context context = Context.newBuilder().engine(engine).build()) {
                // ConstantOption1 is set to configuredValue by mx_truffle.TruffleUnittestConfig
                AbstractExecutableTestLanguage.evalTestLanguage(context, ConstantOptionsLanguage.class, "", "ConstantOption1", "configuredValue");
            }
        }

        try (Engine engine = engineBuilder.build()) {
            try (Context context = Context.newBuilder().engine(engine).build()) {
                // ConstantOption2 is unset
                AbstractExecutableTestLanguage.evalTestLanguage(context, ConstantOptionsLanguage.class, "", "ConstantOption2", "defaultValue");
            }
        }

        try (Context context = Context.newBuilder().build()) {
            // ConstantOption1 is set to configuredValue by mx_truffle.TruffleUnittestConfig
            AbstractExecutableTestLanguage.evalTestLanguage(context, ConstantOptionsLanguage.class, "", "ConstantOption1", "configuredValue");
        }

        try (Context context = Context.newBuilder().build()) {
            // ConstantOption2 is unset
            AbstractExecutableTestLanguage.evalTestLanguage(context, ConstantOptionsLanguage.class, "", "ConstantOption2", "defaultValue");
        }
    }

    @Test
    public void testPreSetOption() {
        Assume.assumeTrue("Pre-set polyglot options are supported only in native-image", ImageInfo.inImageRuntimeCode());

        Engine.Builder engineBuilder = Engine.newBuilder().//
                        useSystemProperties(configuration.useSystemProperties()).//
                        option("engine.WarnInterpreterOnly", "false");
        try (Engine engine = engineBuilder.build()) {
            try (Context context = Context.newBuilder().engine(engine).build()) {
                AbstractExecutableTestLanguage.evalTestLanguage(context, ConstantOptionsLanguage.class, "", "PreSetOption", "configuredValue");
            }
        }

        try (Context context = Context.newBuilder().build()) {
            AbstractExecutableTestLanguage.evalTestLanguage(context, ConstantOptionsLanguage.class, "", "PreSetOption", "configuredValue");
        }
    }

    @Test
    public void testPreSetOptionOverriddenByBuilder() {
        Assume.assumeTrue("Pre-set polyglot options are supported only in native-image", ImageInfo.inImageRuntimeCode());

        Engine.Builder engineBuilder = Engine.newBuilder().//
                        useSystemProperties(configuration.useSystemProperties()).//
                        option("engine.WarnInterpreterOnly", "false").option("ConstantOptionsLanguage.PreSetOption", "runtimeValue");
        try (Engine engine = engineBuilder.build()) {
            try (Context context = Context.newBuilder().engine(engine).build()) {
                AbstractExecutableTestLanguage.evalTestLanguage(context, ConstantOptionsLanguage.class, "", "PreSetOption", "runtimeValue");
            }
        }

        try (Context context = Context.newBuilder().//
                        option("ConstantOptionsLanguage.PreSetOption", "runtimeValue").build()) {
            AbstractExecutableTestLanguage.evalTestLanguage(context, ConstantOptionsLanguage.class, "", "PreSetOption", "runtimeValue");
        }
    }

    @Test
    public void testPreSetOptionOverriddenBySystemProperties() {
        Assume.assumeTrue("Pre-set polyglot options are supported only in native-image", ImageInfo.inImageRuntimeCode());

        String propertyName = "polyglot." + ConstantOptionsLanguage.ID + ".PreSetOption";
        String previousValue = System.getProperty(propertyName);
        try {
            System.setProperty(propertyName, "runtimeValue");
            Engine.Builder engineBuilder = Engine.newBuilder().//
                            useSystemProperties(configuration.useSystemProperties()).//
                            option("engine.WarnInterpreterOnly", "false");
            try (Engine engine = engineBuilder.build()) {
                try (Context context = Context.newBuilder().engine(engine).build()) {
                    AbstractExecutableTestLanguage.evalTestLanguage(context, ConstantOptionsLanguage.class, "", "PreSetOption",
                                    configuration.useSystemProperties() ? "runtimeValue" : "configuredValue");
                }
            }
        } finally {
            System.clearProperty(propertyName);
            if (previousValue != null) {
                System.setProperty(propertyName, previousValue);
            }
        }
    }

    @TruffleLanguage.Registration(id = ConstantOptionsLanguage.ID)
    public static final class ConstantOptionsLanguage extends AbstractExecutableTestLanguage {

        public static final String ID = "ConstantOptionsLanguage";

        @Option(category = OptionCategory.EXPERT, stability = OptionStability.STABLE, help = "Constant test option", constant = true, sandbox = SandboxPolicy.UNTRUSTED)//
        static final ConstantOptionKey<String> ConstantOption1 = new ConstantOptionKey<>("defaultValue");

        @Option(category = OptionCategory.EXPERT, stability = OptionStability.STABLE, help = "Constant test option", constant = true, sandbox = SandboxPolicy.UNTRUSTED)//
        static final ConstantOptionKey<String> ConstantOption2 = new ConstantOptionKey<>("defaultValue");

        @Option(category = OptionCategory.EXPERT, stability = OptionStability.STABLE, help = "Pre-set test option", sandbox = SandboxPolicy.UNTRUSTED)//
        static final OptionKey<String> PreSetOption = new OptionKey<>("defaultValue");

        @Override
        @CompilerDirectives.TruffleBoundary
        protected Object execute(RootNode node, Env env, Object[] contextArguments, Object[] frameArguments) throws Exception {
            String optionName = (String) contextArguments[0];
            String expectedOptionValue = (String) contextArguments[1];
            OptionKey<String> optionKey;
            String value = switch (optionName) {
                case "ConstantOption1" -> {
                    optionKey = ConstantOption1;
                    yield ConstantOption1.getConstantValue();
                }
                case "ConstantOption2" -> {
                    optionKey = ConstantOption2;
                    yield ConstantOption2.getConstantValue();
                }
                case "PreSetOption" -> {
                    optionKey = PreSetOption;
                    yield PreSetOption.getValue(env.getOptions());
                }
                default -> throw new IllegalArgumentException("Unknown option name: " + optionName);
            };
            assertEquals(expectedOptionValue, value);
            assertEquals(expectedOptionValue, env.getOptions().get(optionKey));
            return null;
        }

        @Override
        protected OptionDescriptors getOptionDescriptors() {
            return new ConstantOptionsLanguageOptionDescriptors();
        }
    }
}
