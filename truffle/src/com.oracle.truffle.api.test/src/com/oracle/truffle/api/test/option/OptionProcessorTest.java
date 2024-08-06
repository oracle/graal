/*
 * Copyright (c) 2017, 2024, Oracle and/or its affiliates. All rights reserved.
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.Iterator;

import org.graalvm.options.OptionCategory;
import org.graalvm.options.OptionDescriptor;
import org.graalvm.options.OptionDescriptors;
import org.graalvm.options.OptionKey;
import org.graalvm.options.OptionMap;
import org.graalvm.options.OptionStability;
import org.graalvm.options.OptionType;
import org.graalvm.options.OptionValues;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.SandboxPolicy;
import org.junit.Test;

import com.oracle.truffle.api.Option;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.TruffleLanguage.Registration;
import com.oracle.truffle.api.TruffleOptionDescriptors;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.test.ExpectError;
import com.oracle.truffle.api.test.polyglot.AbstractPolyglotTest;
import com.oracle.truffle.tck.tests.TruffleTestAssumptions;

public class OptionProcessorTest {

    @Test
    public void testTestLang() {
        TruffleTestAssumptions.assumeWeakEncapsulation();
        Engine engine = createEngineBuilder().build();
        OptionDescriptors descriptors = engine.getLanguages().get("optiontestlang1").getOptions();

        OptionDescriptor descriptor;
        OptionDescriptor descriptor1;
        OptionDescriptor descriptor2;
        OptionDescriptor descriptor3;
        OptionDescriptor descriptor4;
        OptionDescriptor descriptor5;
        OptionDescriptor descriptor6;

        descriptor1 = descriptor = descriptors.get("optiontestlang1.StringOption1");
        assertNotNull(descriptor);
        assertTrue(descriptor.isDeprecated());
        assertEquals(String.format("Deprecation message%nwith newline"), descriptor.getDeprecationMessage());
        assertFalse(descriptor.isOptionMap());
        assertSame(OptionCategory.USER, descriptor.getCategory());
        assertSame(OptionStability.EXPERIMENTAL, descriptor.getStability());
        assertEquals("StringOption1 help", descriptor.getHelp());
        assertSame(OptionTestLang1.StringOption1, descriptor.getKey());

        descriptor2 = descriptor = descriptors.get("optiontestlang1.StringOption2");
        assertNotNull(descriptor);
        assertEquals(String.format("StringOption2 help%nwith newline"), descriptor.getHelp());
        assertFalse(descriptor.isDeprecated());
        assertFalse(descriptor.isOptionMap());
        assertSame(OptionCategory.EXPERT, descriptor.getCategory());
        assertSame(OptionTestLang1.StringOption2, descriptor.getKey());

        descriptor3 = descriptor = descriptors.get("optiontestlang1.lowerCaseOption");
        assertNotNull(descriptor);
        assertEquals("Help for lowerCaseOption", descriptor.getHelp());
        assertTrue(descriptor.isDeprecated());
        assertFalse(descriptor.isOptionMap());
        assertSame(OptionCategory.INTERNAL, descriptor.getCategory());
        assertSame(OptionTestLang1.LOWER_CASE_OPTION, descriptor.getKey());

        descriptor4 = descriptor = descriptors.get("optiontestlang1.StableOption");
        assertNotNull(descriptor);
        assertEquals("Stable Option Help", descriptor.getHelp());
        assertFalse(descriptor.isDeprecated());
        assertFalse(descriptor.isOptionMap());
        assertSame(OptionCategory.USER, descriptor.getCategory());
        assertSame(OptionStability.STABLE, descriptor.getStability());
        assertSame(OptionTestLang1.StableOption, descriptor.getKey());

        descriptor5 = descriptor = descriptors.get("optiontestlang1.Properties.NotKnownBeforehand");
        assertNotNull(descriptor);
        assertEquals("User-defined properties", descriptor.getHelp());
        assertTrue(descriptor.isOptionMap());
        assertSame(OptionTestLang1.Properties, descriptor.getKey());

        descriptor6 = descriptor = descriptors.get("optiontestlang1.ZEnumTest");
        OptionType<?> type = descriptor.getKey().getType();
        assertSame(EnumValue.defaultValue, type.convert(EnumValue.defaultValue.toString()));
        assertSame(EnumValue.otherValue, type.convert(EnumValue.otherValue.toString()));
        AbstractPolyglotTest.assertFails(() -> type.convert("invalid"), IllegalArgumentException.class, (e) -> {
            assertEquals("Invalid option value 'invalid'. Valid options values are: 'defaultValue', 'otherValue'", e.getMessage());
        });
        AbstractPolyglotTest.assertFails(() -> type.convert(null), IllegalArgumentException.class);

        // The options are sorted alphabetically
        Iterator<OptionDescriptor> iterator = descriptors.iterator();
        assertTrue(iterator.hasNext());
        assertEquals(descriptor5, iterator.next());
        assertTrue(iterator.hasNext());
        assertEquals(descriptor4, iterator.next());
        assertTrue(iterator.hasNext());
        assertEquals(descriptor1, iterator.next());
        assertTrue(iterator.hasNext());
        assertEquals(descriptor2, iterator.next());
        assertTrue(iterator.hasNext());
        assertEquals(descriptor6, iterator.next());
        assertTrue(iterator.hasNext());
        assertEquals(descriptor3, iterator.next());
        assertFalse(iterator.hasNext());

        assertNull(descriptors.get("optiontestlang1.StringOption3"));
    }

    @Test
    public void testOptionsInstrument() {
        TruffleTestAssumptions.assumeWeakEncapsulation();
        Engine engine = createEngineBuilder().build();
        OptionDescriptors descriptors = engine.getInstruments().get("optiontestinstr1").getOptions();

        OptionDescriptor descriptor;
        OptionDescriptor descriptor1;
        OptionDescriptor descriptor2;
        OptionDescriptor descriptor3;
        OptionDescriptor descriptor4;

        descriptor1 = descriptor = descriptors.get("optiontestinstr1.StringOption1");
        assertNotNull(descriptor);
        assertTrue(descriptor.isDeprecated());
        assertEquals(String.format("Deprecation message%nwith newline"), descriptor.getDeprecationMessage());
        assertFalse(descriptor.isOptionMap());
        assertSame(OptionCategory.USER, descriptor.getCategory());
        assertEquals("StringOption1 help", descriptor.getHelp());
        assertSame(OptionTestInstrument1.StringOption1, descriptor.getKey());

        descriptor2 = descriptor = descriptors.get("optiontestinstr1.StringOption2");
        assertNotNull(descriptor);
        assertEquals(String.format("StringOption2 help%nwith newline"), descriptor.getHelp());
        assertFalse(descriptor.isDeprecated());
        assertFalse(descriptor.isOptionMap());
        assertSame(OptionCategory.EXPERT, descriptor.getCategory());
        assertSame(OptionTestInstrument1.StringOption2, descriptor.getKey());

        descriptor3 = descriptor = descriptors.get("optiontestinstr1.Thresholds._");
        assertNotNull(descriptor);
        assertEquals("Instrument user-defined thresholds", descriptor.getHelp());
        assertFalse(descriptor.isDeprecated());
        assertTrue(descriptor.isOptionMap());
        assertSame(OptionCategory.EXPERT, descriptor.getCategory());
        assertSame(OptionTestInstrument1.Thresholds, descriptor.getKey());

        descriptor4 = descriptor = descriptors.get("optiontestinstr1.ThresholdsSamePrefix");

        Iterator<OptionDescriptor> iterator = descriptors.iterator();
        assertTrue(iterator.hasNext());
        assertEquals(descriptor1, iterator.next());
        assertTrue(iterator.hasNext());
        assertEquals(descriptor2, iterator.next());
        assertTrue(iterator.hasNext());
        assertEquals(descriptor3, iterator.next());
        assertTrue(iterator.hasNext());
        assertEquals(descriptor4, iterator.next());
        assertFalse(iterator.hasNext());

        assertNull(descriptors.get("optiontestinstr1.StringOption3"));
    }

    @Test
    public void testEmptyName() {
        OptionErrorOptionDescriptors descriptors = new OptionErrorOptionDescriptors();

        assertNotNull(descriptors.get("foobar"));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testOptionValues() {
        TruffleTestAssumptions.assumeWeakEncapsulation();
        Engine engine = createEngineBuilder().build();
        OptionDescriptors descriptors = engine.getInstruments().get("optiontestinstr1").getOptions();
        OptionValues optionValues = engine.getInstruments().get("optiontestinstr1").lookup(OptionValues.class);
        assertSame(descriptors, optionValues.getDescriptors());
        assertFalse(optionValues.hasSetOptions());
        OptionKey<?> optionKey1 = descriptors.get("optiontestinstr1.StringOption1").getKey();
        OptionKey<?> optionKey2 = descriptors.get("optiontestinstr1.StringOption2").getKey();
        assertFalse(optionValues.hasBeenSet(optionKey1));
        assertEquals("defaultValue", optionValues.get(optionKey1));
        assertEquals("defaultValue", optionValues.get(optionKey2));

        engine = createEngineBuilder().option("optiontestinstr1.StringOption1", "test").build();
        optionValues = engine.getInstruments().get("optiontestinstr1").lookup(OptionValues.class);
        assertTrue(optionValues.hasSetOptions());
        optionKey1 = descriptors.get("optiontestinstr1.StringOption1").getKey();
        optionKey2 = descriptors.get("optiontestinstr1.StringOption2").getKey();
        assertTrue(optionValues.hasBeenSet(optionKey1));
        assertFalse(optionValues.hasBeenSet(optionKey2));
        assertEquals("test", optionValues.get(optionKey1));
        assertEquals("defaultValue", optionValues.get(optionKey2));

        engine = createEngineBuilder().allowExperimentalOptions(true).option("optiontestlang1.StringOption1", "testLang").build();
        optionValues = engine.getInstruments().get("optiontestinstr1").lookup(OptionValues.class);
        // A language option was set, not the instrument one. Instrument sees no option set:
        assertFalse(optionValues.hasSetOptions());

        engine = createEngineBuilder().allowExperimentalOptions(true).option("optiontestinstr1.Thresholds.MaxRetries", "123").option("optiontestinstr1.Thresholds.Capacity", "456").build();
        optionValues = engine.getInstruments().get("optiontestinstr1").lookup(OptionValues.class);
        assertTrue(optionValues.hasSetOptions());
        assertNull(descriptors.get("optiontestinstr1.ThresholdsDoesNotMatchPrefix"));
        optionKey1 = descriptors.get("optiontestinstr1.Thresholds").getKey();
        optionKey2 = descriptors.get("optiontestinstr1.Thresholds.key").getKey();
        // Option map keys point to the containing option map descriptor.
        assertSame(optionKey1, optionKey2);
        assertTrue(optionValues.hasBeenSet(optionKey1));
        OptionMap<Integer> thresholds = (OptionMap<Integer>) optionValues.get(optionKey1);
        assertEquals(123, (int) thresholds.get("MaxRetries"));
        assertEquals(456, (int) thresholds.get("Capacity"));
        assertNull(thresholds.get("undefined"));
    }

    @Test
    public void testDescriptorPrefixMatching() {
        TruffleTestAssumptions.assumeWeakEncapsulation();
        Engine engine = createEngineBuilder().build();
        OptionDescriptors descriptors = engine.getInstruments().get("optiontestinstr1").getOptions();

        OptionKey<?> optionKey1 = descriptors.get("optiontestinstr1.ThresholdsSamePrefix").getKey();
        OptionKey<?> optionKey2 = descriptors.get("optiontestinstr1.Thresholds").getKey();
        assertSame(OptionTestInstrument1.ThresholdsSamePrefix, optionKey1);
        assertSame(OptionTestInstrument1.Thresholds, optionKey2);

        optionKey1 = descriptors.get("optiontestinstr1.Thresholds.key1").getKey();
        optionKey2 = descriptors.get("optiontestinstr1.Thresholds.key2").getKey();
        assertSame(optionKey1, optionKey2);
        assertSame(OptionTestInstrument1.Thresholds, optionKey1);

        // Empty keys.
        optionKey1 = descriptors.get("optiontestinstr1.Thresholds.").getKey();
        optionKey2 = descriptors.get("optiontestinstr1.Thresholds").getKey();
        assertNotNull(optionKey1);
        assertNotNull(optionKey2);
        assertSame(optionKey1, optionKey2);
        assertSame(OptionTestInstrument1.Thresholds, optionKey1);

        // Incomplete property name.
        assertNull(descriptors.get("optiontestinstr1.ThresholdsSamePr"));
    }

    @Test
    public void testPrefixOptions() {
        OptionDescriptors descriptors = new PrefixOptionDescriptors();
        OptionDescriptor descriptor = descriptors.get("prefix.Prefix.DynamicPropertySetAtRuntimeWhoseNameIsNotKnown");
        assertNotNull(descriptor);
        assertTrue(descriptor.isOptionMap());
        assertEquals("prefix.Prefix", descriptor.getName());
        assertEquals("Prefix option help", descriptor.getHelp());
        assertEquals(OptionMap.empty(), descriptor.getKey().getDefaultValue());
    }

    @Test
    public void testSandboxPolicy() {
        TruffleOptionDescriptors descriptors = new SandboxOptionDescriptors();
        assertEquals(SandboxPolicy.TRUSTED, descriptors.getSandboxPolicy("sandbox.DefaultOption"));
        assertEquals(SandboxPolicy.TRUSTED, descriptors.getSandboxPolicy("sandbox.TrustedOption"));
        assertEquals(SandboxPolicy.CONSTRAINED, descriptors.getSandboxPolicy("sandbox.ConstrainedOption"));
        assertEquals(SandboxPolicy.ISOLATED, descriptors.getSandboxPolicy("sandbox.IsolatedOption"));
        assertEquals(SandboxPolicy.UNTRUSTED, descriptors.getSandboxPolicy("sandbox.UntrustedOption"));

        assertEquals(SandboxPolicy.TRUSTED, descriptors.getSandboxPolicy("sandbox.DefaultOptionMap"));
        assertEquals(SandboxPolicy.TRUSTED, descriptors.getSandboxPolicy("sandbox.TrustedOptionMap"));
        assertEquals(SandboxPolicy.CONSTRAINED, descriptors.getSandboxPolicy("sandbox.ConstrainedOptionMap"));
        assertEquals(SandboxPolicy.ISOLATED, descriptors.getSandboxPolicy("sandbox.IsolatedOptionMap"));
        assertEquals(SandboxPolicy.UNTRUSTED, descriptors.getSandboxPolicy("sandbox.UntrustedOptionMap"));

        assertEquals(SandboxPolicy.TRUSTED, descriptors.getSandboxPolicy("sandbox.DefaultOptionMap.Key"));
        assertEquals(SandboxPolicy.TRUSTED, descriptors.getSandboxPolicy("sandbox.TrustedOptionMap.Key"));
        assertEquals(SandboxPolicy.CONSTRAINED, descriptors.getSandboxPolicy("sandbox.ConstrainedOptionMap.Key"));
        assertEquals(SandboxPolicy.ISOLATED, descriptors.getSandboxPolicy("sandbox.IsolatedOptionMap.Key"));
        assertEquals(SandboxPolicy.UNTRUSTED, descriptors.getSandboxPolicy("sandbox.UntrustedOptionMap.Key"));

        AbstractPolyglotTest.assertFails(() -> descriptors.getSandboxPolicy("sandbox.UnknownOption"), AssertionError.class,
                        (ae) -> assertEquals("Unknown option sandbox.UnknownOption", ae.getMessage()));
        AbstractPolyglotTest.assertFails(() -> descriptors.getSandboxPolicy("sandbox.DefaultOptionMapKey"), AssertionError.class,
                        (ae) -> assertEquals("Unknown option sandbox.DefaultOptionMapKey", ae.getMessage()));

        TruffleOptionDescriptors descriptors2 = new SandboxSingleOptionOptionDescriptors();
        assertEquals(SandboxPolicy.CONSTRAINED, descriptors2.getSandboxPolicy("sandbox.SingleOption"));
        AbstractPolyglotTest.assertFails(() -> descriptors2.getSandboxPolicy("sandbox.UnknownOption"), AssertionError.class,
                        (ae) -> assertEquals("Unknown option sandbox.UnknownOption", ae.getMessage()));
    }

    @Test
    public void testOptionValueEqualsAndHashCode() {
        TruffleTestAssumptions.assumeWeakEncapsulation();
        // options are never equals if different engines are used.
        Context c0 = createContextBuilder().option("optiontestlang1.StableOption", "foo").build();
        Context c1 = createContextBuilder().option("optiontestlang1.StableOption", "foo").build();
        assertNotEquals(getOptionValues(c0), getOptionValues(c1));
        assertNotEquals(getOptionValues(c1), getOptionValues(c0));
        assertNotEquals(getOptionValues(c0).hashCode(), getOptionValues(c1).hashCode());
        c0.close();
        c1.close();

        // need to use the same engine to support comparing option values.
        Engine engine = createEngineBuilder().build();
        c0 = Context.newBuilder().engine(engine).option("optiontestlang1.StableOption", "foo").build();
        c1 = Context.newBuilder().engine(engine).option("optiontestlang1.StableOption", "foo").build();
        assertEquals(getOptionValues(c0), getOptionValues(c1));
        assertEquals(getOptionValues(c1), getOptionValues(c0));
        assertEquals(getOptionValues(c0).hashCode(), getOptionValues(c1).hashCode());
        c0.close();
        c1.close();

        c0 = Context.newBuilder().engine(engine).option("optiontestlang1.StableOption", "foo").build();
        c1 = Context.newBuilder().engine(engine).option("optiontestlang1.StableOption", "bar").build();
        assertNotEquals(getOptionValues(c0), getOptionValues(c1));
        assertNotEquals(getOptionValues(c1), getOptionValues(c0));
        assertNotEquals(getOptionValues(c0).hashCode(), getOptionValues(c1).hashCode());
        c0.close();
        c1.close();

        // an option not being set makes the option values not equal
        c0 = Context.newBuilder().engine(engine).option("optiontestlang1.StableOption", "stable").build();
        c1 = Context.newBuilder().engine(engine).build();
        assertNotEquals(getOptionValues(c0), getOptionValues(c1));
        assertNotEquals(getOptionValues(c1), getOptionValues(c0));
        assertNotEquals(getOptionValues(c0).hashCode(), getOptionValues(c1).hashCode());
        c0.close();
        c1.close();

        // an option not being set makes the option values not equal
        c0 = Context.newBuilder().engine(engine).option("optiontestlang1.StableOption", "stable").build();
        c1 = Context.newBuilder().engine(engine).option("optiontestlang1.StableOption", "stable").build();
        assertEquals(getOptionValues(c0), getOptionValues(c1));
        assertEquals(getOptionValues(c1), getOptionValues(c0));
        assertEquals(getOptionValues(c0).hashCode(), getOptionValues(c1).hashCode());
        c0.close();
        c1.close();

    }

    private static OptionValues getOptionValues(Context c) {
        c.enter();
        try {
            c.initialize(OptionTestLang1.ID);
            return OptionTestLang1.getCurrentContext().getOptions();
        } finally {
            c.leave();
        }
    }

    private static Engine.Builder createEngineBuilder() {
        return Engine.newBuilder().option("engine.WarnOptionDeprecation", "false");
    }

    private static Context.Builder createContextBuilder() {
        return Context.newBuilder().option("engine.WarnOptionDeprecation", "false");
    }

    @Option.Group("prefix")
    public static class Prefix {
        @Option(help = "Prefix option help", category = OptionCategory.USER) //
        static final OptionKey<OptionMap<String>> Prefix = OptionKey.mapOf(String.class);
    }

    @Option.Group("foobar")
    public static class OptionError {

        @ExpectError("Option field must be static") //
        @Option(help = "", deprecated = true, category = OptionCategory.USER) //
        final OptionKey<String> error1 = new OptionKey<>("defaultValue");

        @ExpectError("Option field cannot be private") //
        @Option(help = "", deprecated = true, category = OptionCategory.USER) //
        private static final OptionKey<String> Error2 = new OptionKey<>("defaultValue");

        @ExpectError("Option field type java.lang.Object is not a subclass of org.graalvm.options.OptionKey<T>") //
        @Option(help = "", deprecated = true, category = OptionCategory.USER) //
        static final Object Error3 = new OptionKey<>("defaultValue");

        @ExpectError("Option field must be of type org.graalvm.options.OptionKey") //
        @Option(help = "", deprecated = true, category = OptionCategory.USER) //
        static final int Error4 = 42;

        @ExpectError("Option help text must start with upper case letter") //
        @Option(help = "a", deprecated = true, category = OptionCategory.USER) //
        static final OptionKey<String> Error5 = new OptionKey<>("defaultValue");

        @ExpectError("Two options with duplicated resolved descriptor name 'foobar.Duplicate' found.") //
        @Option(help = "A", name = "Duplicate", deprecated = true, category = OptionCategory.USER) //
        static final OptionKey<String> Error7 = new OptionKey<>("defaultValue");

        @ExpectError("Two options with duplicated resolved descriptor name 'foobar.Duplicate' found.") //
        @Option(help = "A", name = "Duplicate", deprecated = true, category = OptionCategory.USER) //
        static final OptionKey<String> Error8 = new OptionKey<>("defaultValue");

        @ExpectError("Option (maps) cannot contain a '.' in the name") //
        @Option(help = "A", name = "Category.SubCategory", deprecated = true, category = OptionCategory.USER) //
        static final OptionKey<OptionMap<String>> Error9 = OptionKey.mapOf(String.class);

        @ExpectError("Deprecation message can be specified only for deprecated options.") //
        @Option(help = "A", category = OptionCategory.USER, deprecationMessage = "Deprecated with no replacement.") //
        static final OptionKey<String> Error10 = new OptionKey<>("defaultValue");

        @ExpectError("Option deprecation message must start with upper case letter.") //
        @Option(help = "A", category = OptionCategory.USER, deprecated = true, deprecationMessage = "deprecated with no replacement.") //
        static final OptionKey<String> Error11 = new OptionKey<>("defaultValue");

        @Option(help = "A", name = "", deprecated = true, category = OptionCategory.USER) //
        static final OptionKey<String> EmptyNameAllowed = new OptionKey<>("defaultValue");

        @Option(help = "A", category = OptionCategory.USER, deprecated = true, deprecationMessage = "Deprecated with no replacement.") //
        static final OptionKey<String> ValidDeprecationMessage = new OptionKey<>("defaultValue");

    }

    @Option.Group("sandbox")
    public static final class Sandbox {

        @Option(category = OptionCategory.EXPERT, stability = OptionStability.STABLE, help = "Option with default policy")//
        static final OptionKey<Boolean> DefaultOption = new OptionKey<>(false);
        @Option(category = OptionCategory.EXPERT, stability = OptionStability.STABLE, help = "Option with trusted policy", sandbox = SandboxPolicy.TRUSTED)//
        static final OptionKey<Boolean> TrustedOption = new OptionKey<>(false);

        @Option(category = OptionCategory.EXPERT, stability = OptionStability.STABLE, help = "Option with constrained policy", sandbox = SandboxPolicy.CONSTRAINED)//
        static final OptionKey<Boolean> ConstrainedOption = new OptionKey<>(false);

        @Option(category = OptionCategory.EXPERT, stability = OptionStability.STABLE, help = "Option with isolated policy", sandbox = SandboxPolicy.ISOLATED)//
        static final OptionKey<Boolean> IsolatedOption = new OptionKey<>(false);

        @Option(category = OptionCategory.EXPERT, stability = OptionStability.STABLE, help = "Option with untrusted policy", sandbox = SandboxPolicy.UNTRUSTED)//
        static final OptionKey<Boolean> UntrustedOption = new OptionKey<>(false);

        @Option(category = OptionCategory.EXPERT, stability = OptionStability.STABLE, help = "Option map with default policy")//
        static final OptionKey<OptionMap<String>> DefaultOptionMap = OptionKey.mapOf(String.class);

        @Option(category = OptionCategory.EXPERT, stability = OptionStability.STABLE, help = "Option map with trusted policy", sandbox = SandboxPolicy.TRUSTED)//
        static final OptionKey<OptionMap<String>> TrustedOptionMap = OptionKey.mapOf(String.class);

        @Option(category = OptionCategory.EXPERT, stability = OptionStability.STABLE, help = "Option map with constrained policy", sandbox = SandboxPolicy.CONSTRAINED)//
        static final OptionKey<OptionMap<String>> ConstrainedOptionMap = OptionKey.mapOf(String.class);

        @Option(category = OptionCategory.EXPERT, stability = OptionStability.STABLE, help = "Option map with isolated policy", sandbox = SandboxPolicy.ISOLATED)//
        static final OptionKey<OptionMap<String>> IsolatedOptionMap = OptionKey.mapOf(String.class);

        @Option(category = OptionCategory.EXPERT, stability = OptionStability.STABLE, help = "Option map with untrusted policy", sandbox = SandboxPolicy.UNTRUSTED)//
        static final OptionKey<OptionMap<String>> UntrustedOptionMap = OptionKey.mapOf(String.class);
    }

    @Option.Group("sandbox")
    public static final class SandboxSingleOption {
        @Option(category = OptionCategory.EXPERT, stability = OptionStability.STABLE, help = "Option with constrained policy", sandbox = SandboxPolicy.CONSTRAINED)//
        static final OptionKey<Boolean> SingleOption = new OptionKey<>(false);
    }

    public enum EnumValue {
        defaultValue,
        otherValue;
    }

    @Registration(id = OptionTestLang1.ID, version = "1.0", name = OptionTestLang1.ID)
    public static class OptionTestLang1 extends TruffleLanguage<Env> {

        public static final String ID = "optiontestlang1";

        @Option(help = "StringOption1 help", deprecated = true, deprecationMessage = "Deprecation message%nwith newline", category = OptionCategory.USER) //
        static final OptionKey<String> StringOption1 = new OptionKey<>("defaultValue");

        @Option(help = "StringOption2 help%nwith newline", deprecated = false, category = OptionCategory.EXPERT, stability = OptionStability.EXPERIMENTAL) //
        public static final OptionKey<String> StringOption2 = new OptionKey<>("defaultValue");

        // The variable name differs from the option name on purpose, to test they can be different
        @Option(help = "Help for lowerCaseOption", name = "lowerCaseOption", deprecated = true, category = OptionCategory.INTERNAL) //
        static final OptionKey<String> LOWER_CASE_OPTION = new OptionKey<>("defaultValue");

        @Option(help = "Stable Option Help", category = OptionCategory.USER, stability = OptionStability.STABLE) //
        public static final OptionKey<String> StableOption = new OptionKey<>("stable");

        @Option(help = "User-defined properties", category = OptionCategory.USER) //
        static final OptionKey<OptionMap<String>> Properties = OptionKey.mapOf(String.class);

        @Option(help = "User-defined enum.", category = OptionCategory.USER) //
        static final OptionKey<EnumValue> ZEnumTest = new OptionKey<>(EnumValue.defaultValue);

        @Override
        protected OptionDescriptors getOptionDescriptors() {
            return new OptionTestLang1OptionDescriptors();
        }

        @Override
        protected Env createContext(Env env) {
            return env;
        }

        private static final ContextReference<Env> REFERENCE = ContextReference.create(OptionTestLang1.class);

        public static Env getCurrentContext() {
            return REFERENCE.get(null);
        }

    }

    @TruffleInstrument.Registration(id = "optiontestinstr1", services = OptionValues.class)
    public static class OptionTestInstrument1 extends TruffleInstrument {

        @Option(help = "StringOption1 help", deprecated = true, deprecationMessage = "Deprecation message%nwith newline", category = OptionCategory.USER, stability = OptionStability.STABLE) //
        public static final OptionKey<String> StringOption1 = new OptionKey<>("defaultValue");

        @Option(help = "StringOption2 help%nwith newline", deprecated = false, category = OptionCategory.EXPERT) //
        public static final OptionKey<String> StringOption2 = new OptionKey<>("defaultValue");

        @Option(help = "Instrument user-defined thresholds", deprecated = false, category = OptionCategory.EXPERT) //
        public static final OptionKey<OptionMap<Integer>> Thresholds = OptionKey.mapOf(Integer.class);

        @Option(help = "Option with common prefix", deprecated = false, category = OptionCategory.EXPERT) //
        public static final OptionKey<OptionMap<Integer>> ThresholdsSamePrefix = OptionKey.mapOf(Integer.class);

        @Override
        protected void onCreate(Env env) {
            env.registerService(env.getOptions());
        }

        @Override
        protected OptionDescriptors getOptionDescriptors() {
            return new OptionTestInstrument1OptionDescriptors();
        }

    }

}
