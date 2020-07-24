/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
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
import org.graalvm.options.OptionValues;
import org.graalvm.polyglot.Engine;
import org.junit.Test;

import com.oracle.truffle.api.Option;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.TruffleLanguage.Registration;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.test.ExpectError;

public class OptionProcessorTest {

    @Test
    public void testTestLang() {
        Engine engine = Engine.create();
        OptionDescriptors descriptors = engine.getLanguages().get("optiontestlang1").getOptions();

        OptionDescriptor descriptor;
        OptionDescriptor descriptor1;
        OptionDescriptor descriptor2;
        OptionDescriptor descriptor3;
        OptionDescriptor descriptor4;
        OptionDescriptor descriptor5;

        descriptor1 = descriptor = descriptors.get("optiontestlang1.StringOption1");
        assertNotNull(descriptor);
        assertTrue(descriptor.isDeprecated());
        assertFalse(descriptor.isOptionMap());
        assertSame(OptionCategory.USER, descriptor.getCategory());
        assertSame(OptionStability.EXPERIMENTAL, descriptor.getStability());
        assertEquals("StringOption1 help", descriptor.getHelp());
        assertSame(OptionTestLang1.StringOption1, descriptor.getKey());

        descriptor2 = descriptor = descriptors.get("optiontestlang1.StringOption2");
        assertNotNull(descriptor);
        assertEquals("StringOption2 help", descriptor.getHelp());
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
        assertEquals(descriptor3, iterator.next());
        assertFalse(iterator.hasNext());

        assertNull(descriptors.get("optiontestlang1.StringOption3"));
    }

    @Test
    public void testOptionsInstrument() {

        Engine engine = Engine.create();
        OptionDescriptors descriptors = engine.getInstruments().get("optiontestinstr1").getOptions();

        OptionDescriptor descriptor;
        OptionDescriptor descriptor1;
        OptionDescriptor descriptor2;
        OptionDescriptor descriptor3;
        OptionDescriptor descriptor4;

        descriptor1 = descriptor = descriptors.get("optiontestinstr1.StringOption1");
        assertNotNull(descriptor);
        assertTrue(descriptor.isDeprecated());
        assertFalse(descriptor.isOptionMap());
        assertSame(OptionCategory.USER, descriptor.getCategory());
        assertEquals("StringOption1 help", descriptor.getHelp());
        assertSame(OptionTestInstrument1.StringOption1, descriptor.getKey());

        descriptor2 = descriptor = descriptors.get("optiontestinstr1.StringOption2");
        assertNotNull(descriptor);
        assertEquals("StringOption2 help", descriptor.getHelp());
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
        Engine engine = Engine.create();
        OptionDescriptors descriptors = engine.getInstruments().get("optiontestinstr1").getOptions();
        OptionValues optionValues = engine.getInstruments().get("optiontestinstr1").lookup(OptionValues.class);
        assertSame(descriptors, optionValues.getDescriptors());
        assertFalse(optionValues.hasSetOptions());
        OptionKey<?> optionKey1 = descriptors.get("optiontestinstr1.StringOption1").getKey();
        OptionKey<?> optionKey2 = descriptors.get("optiontestinstr1.StringOption2").getKey();
        assertFalse(optionValues.hasBeenSet(optionKey1));
        assertEquals("defaultValue", optionValues.get(optionKey1));
        assertEquals("defaultValue", optionValues.get(optionKey2));

        engine = Engine.newBuilder().option("optiontestinstr1.StringOption1", "test").build();
        optionValues = engine.getInstruments().get("optiontestinstr1").lookup(OptionValues.class);
        assertTrue(optionValues.hasSetOptions());
        optionKey1 = descriptors.get("optiontestinstr1.StringOption1").getKey();
        optionKey2 = descriptors.get("optiontestinstr1.StringOption2").getKey();
        assertTrue(optionValues.hasBeenSet(optionKey1));
        assertFalse(optionValues.hasBeenSet(optionKey2));
        assertEquals("test", optionValues.get(optionKey1));
        assertEquals("defaultValue", optionValues.get(optionKey2));

        engine = Engine.newBuilder().allowExperimentalOptions(true).option("optiontestlang1.StringOption1", "testLang").build();
        optionValues = engine.getInstruments().get("optiontestinstr1").lookup(OptionValues.class);
        // A language option was set, not the instrument one. Instrument sees no option set:
        assertFalse(optionValues.hasSetOptions());

        engine = Engine.newBuilder().allowExperimentalOptions(true).option("optiontestinstr1.Thresholds.MaxRetries", "123").option("optiontestinstr1.Thresholds.Capacity", "456").build();
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
        Engine engine = Engine.create();
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

    @Registration(id = "optiontestlang1", version = "1.0", name = "optiontestlang1")
    public static class OptionTestLang1 extends TruffleLanguage<Env> {

        @Option(help = "StringOption1 help", deprecated = true, category = OptionCategory.USER) //
        static final OptionKey<String> StringOption1 = new OptionKey<>("defaultValue");

        @Option(help = "StringOption2 help", deprecated = false, category = OptionCategory.EXPERT, stability = OptionStability.EXPERIMENTAL) //
        public static final OptionKey<String> StringOption2 = new OptionKey<>("defaultValue");

        // The variable name differs from the option name on purpose, to test they can be different
        @Option(help = "Help for lowerCaseOption", name = "lowerCaseOption", deprecated = true, category = OptionCategory.INTERNAL) //
        static final OptionKey<String> LOWER_CASE_OPTION = new OptionKey<>("defaultValue");

        @Option(help = "Stable Option Help", category = OptionCategory.USER, stability = OptionStability.STABLE) //
        public static final OptionKey<String> StableOption = new OptionKey<>("stable");

        @Option(help = "User-defined properties", category = OptionCategory.USER) //
        static final OptionKey<OptionMap<String>> Properties = OptionKey.mapOf(String.class);

        @Override
        protected OptionDescriptors getOptionDescriptors() {
            return new OptionTestLang1OptionDescriptors();
        }

        @Override
        protected Env createContext(Env env) {
            return env;
        }

        public static Env getCurrentContext() {
            return getCurrentContext(OptionTestLang1.class);
        }

    }

    @TruffleInstrument.Registration(id = "optiontestinstr1", services = OptionValues.class)
    public static class OptionTestInstrument1 extends TruffleInstrument {

        @Option(help = "StringOption1 help", deprecated = true, category = OptionCategory.USER, stability = OptionStability.STABLE) //
        public static final OptionKey<String> StringOption1 = new OptionKey<>("defaultValue");

        @Option(help = "StringOption2 help", deprecated = false, category = OptionCategory.EXPERT) //
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
