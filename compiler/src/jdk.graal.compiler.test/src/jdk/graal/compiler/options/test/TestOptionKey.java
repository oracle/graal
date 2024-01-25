/*
 * Copyright (c) 2013, 2018, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.options.test;

import static jdk.graal.compiler.options.OptionValues.asMap;
import static jdk.graal.compiler.options.OptionValues.newOptionMap;
import static jdk.graal.compiler.options.OptionsParser.parseOptionValue;
import static jdk.graal.compiler.options.test.TestOptionKey.Options.MyBooleanOption;
import static jdk.graal.compiler.options.test.TestOptionKey.Options.MyDebugOption;
import static jdk.graal.compiler.options.test.TestOptionKey.Options.MyDeprecatedOption;
import static jdk.graal.compiler.options.test.TestOptionKey.Options.MyDoubleOption;
import static jdk.graal.compiler.options.test.TestOptionKey.Options.MyFloatOption;
import static jdk.graal.compiler.options.test.TestOptionKey.Options.MyIntegerOption;
import static jdk.graal.compiler.options.test.TestOptionKey.Options.MyLongOption;
import static jdk.graal.compiler.options.test.TestOptionKey.Options.MyMultiEnumOption;
import static jdk.graal.compiler.options.test.TestOptionKey.Options.MyOption;
import static jdk.graal.compiler.options.test.TestOptionKey.Options.MyOtherOption;
import static jdk.graal.compiler.options.test.TestOptionKey.Options.MySecondOption;
import static jdk.graal.compiler.options.test.TestOptionKey.TestEnum.Value2;
import static jdk.graal.compiler.options.test.TestOptionKey.TestEnum.Value3;
import static jdk.graal.compiler.options.test.TestOptionKey.TestEnum.Value4;
import static org.junit.Assert.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.EconomicSet;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import jdk.graal.compiler.options.EnumMultiOptionKey;
import jdk.graal.compiler.options.EnumOptionKey;
import jdk.graal.compiler.options.ModifiableOptionValues;
import jdk.graal.compiler.options.OptionDescriptor;
import jdk.graal.compiler.options.OptionDescriptors;
import jdk.graal.compiler.options.OptionDescriptorsMap;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionStability;
import jdk.graal.compiler.options.OptionType;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.options.OptionsParser;

@SuppressWarnings("try")
public class TestOptionKey {

    public enum TestEnum {
        Default,
        Value1,
        Value2,
        Value3,
        Value4
    }

    public static class Options {
        public static final OptionKey<String> MyOption = new OptionKey<>("original");
        public static final OptionKey<String> MySecondOption = new OptionKey<>("second");
        public static final OptionKey<Integer> MyIntegerOption = new OptionKey<>(42);
        public static final OptionKey<Long> MyLongOption = new OptionKey<>(4242L);
        public static final OptionKey<Boolean> MyBooleanOption = new OptionKey<>(false);
        public static final OptionKey<Float> MyFloatOption = new OptionKey<>(42.42F);
        public static final OptionKey<Double> MyDoubleOption = new OptionKey<>(4242.4242D);
        public static final OptionKey<String> MyOtherOption = new OptionKey<>("original");
        public static final EnumMultiOptionKey<TestEnum> MyMultiEnumOption = new EnumMultiOptionKey<>(TestEnum.class, null);
        public static final OptionKey<String> MyDeprecatedOption = new OptionKey<>("deprecated");
        public static final OptionKey<String> MyDebugOption = new OptionKey<>("debug");
    }

    private static final OptionDescriptors OPTION_DESCRIPTORS;
    static {
        EconomicMap<String, OptionDescriptor> map = EconomicMap.create();
        map.put("MyOption", OptionDescriptor.create("MyOption", OptionType.User, String.class, "", Options.class, "MyOption", MyOption));
        map.put("MyDebugOption", OptionDescriptor.create("MyDebugOption", OptionType.Debug, String.class, "", Options.class, "MyDebugOption", MyDebugOption));
        map.put("MyIntegerOption", OptionDescriptor.create("MyIntegerOption", OptionType.User, Integer.class, "", Options.class, "MyIntegerOption", MyIntegerOption));
        map.put("MyLongOption", OptionDescriptor.create("MyLongOption", OptionType.User, Long.class, "", Options.class, "MyLongOption", MyLongOption));
        map.put("MyBooleanOption", OptionDescriptor.create("MyBooleanOption", OptionType.User, Boolean.class, "", Options.class, "MyBooleanOption", MyBooleanOption));
        map.put("MyFloatOption", OptionDescriptor.create("MyFloatOption", OptionType.User, Float.class, "", Options.class, "MyFloatOption", MyFloatOption));
        map.put("MyDoubleOption", OptionDescriptor.create("MyDoubleOption", OptionType.User, Double.class, "", Options.class, "MyDoubleOption", MyDoubleOption));
        map.put("MySecondOption", OptionDescriptor.create("MySecondOption", OptionType.User, String.class, "", Options.class, "MySecondOption", MySecondOption));
        map.put("MyMultiEnumOption", OptionDescriptor.create("MyMultiEnumOption", OptionType.User, EconomicSet.class, "", Options.class, "MyMultiEnumOption", MyMultiEnumOption));
        map.put("MyDeprecatedOption", OptionDescriptor.create(
        // @formatter:off
            /*name*/ "MyDeprecatedOption",
            /*optionType*/ OptionType.User,
            /*optionValueType*/ String.class,
            /*help*/ "Help for MyDeprecatedOption with",
            /*extraHelp*/ new String[] {
                 "some extra text on",
                 "following lines.",
            },
            /*declaringClass*/ Options.class,
            /*fieldName*/ "MyDeprecatedOption",
            /*option*/ MyDeprecatedOption,
            /*stability*/ OptionStability.EXPERIMENTAL,
            /*deprecated*/ true,
            /*deprecationMessage*/ "Some deprecation message"));
        // @formatter:on

        OPTION_DESCRIPTORS = new OptionDescriptorsMap(map);
    }

    @Test
    public void testGetValueOrDefault() {
        EconomicMap<OptionKey<?>, Object> map = newOptionMap();
        Assert.assertEquals("original", MyOption.getValueOrDefault(map));
        MyOption.putIfAbsent(map, "new value 1");
        Assert.assertEquals("new value 1", MyOption.getValueOrDefault(map));
    }

    @Test
    public void testToString() throws IOException {
        Assert.assertEquals("MyOption", MyOption.toString());
        EconomicMap<OptionKey<?>, Object> map = asMap(MyOption, "new value 1");
        OptionValues optionsValues = new OptionValues(map);
        assertEquals("{MyOption=new value 1}", optionsValues.toString());

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Iterable<OptionDescriptors> loader = List.of(OPTION_DESCRIPTORS);
            optionsValues.printHelp(loader, new PrintStream(baos), "prefix.", false);
            String help = baos.toString();
            Assert.assertNotEquals(help.length(), 0);
            Assert.assertTrue(help, help.contains("prefix.MyDeprecatedOption = \"deprecated\""));
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Iterable<OptionDescriptors> loader = List.of(OPTION_DESCRIPTORS);
            optionsValues.printHelp(loader, new PrintStream(baos), "prefix.", true);
            String help = baos.toString();
            Assert.assertNotEquals(help.length(), 0);
            Assert.assertTrue(help, help.contains("prefix.MyDebugOption = \"debug\""));
        }
    }

    @Test
    public void testOptionsParser() {
        OptionDescriptors descs = OPTION_DESCRIPTORS;
        Assert.assertEquals(1001, parseOptionValue(descs.get("MyIntegerOption"), 1001));
        Assert.assertEquals(1001, parseOptionValue(descs.get("MyIntegerOption"), "1001"));
        Assert.assertEquals(10011001L, parseOptionValue(descs.get("MyLongOption"), 10011001L));
        Assert.assertEquals(10011001L, parseOptionValue(descs.get("MyLongOption"), "10011001"));
        Assert.assertEquals(1001F, parseOptionValue(descs.get("MyFloatOption"), 1001F));
        Assert.assertEquals(1001F, parseOptionValue(descs.get("MyFloatOption"), "1001"));
        Assert.assertEquals(10011001D, parseOptionValue(descs.get("MyDoubleOption"), 10011001D));
        Assert.assertEquals(10011001D, parseOptionValue(descs.get("MyDoubleOption"), "10011001"));
        Assert.assertEquals(false, parseOptionValue(descs.get("MyBooleanOption"), false));
        Assert.assertEquals(false, parseOptionValue(descs.get("MyBooleanOption"), "false"));
        Assert.assertEquals(true, parseOptionValue(descs.get("MyBooleanOption"), true));
        Assert.assertEquals(true, parseOptionValue(descs.get("MyBooleanOption"), "true"));

        OptionKey<Exception> exceptionOption = new OptionKey<>(null);
        OptionDescriptor desc = OptionDescriptor.create("exceptionOption", OptionType.User, Exception.class, "", Options.class, "exceptionOption", exceptionOption);
        try {
            parseOptionValue(desc, "impossible value");
            Assert.fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // Expected
        }

        Assert.assertEquals(11L * 1024, parseOptionValue(descs.get("MyLongOption"), "11k"));
        Assert.assertEquals(11L * 1024 * 1024, parseOptionValue(descs.get("MyLongOption"), "11m"));
        Assert.assertEquals(11L * 1024 * 1024 * 1024, parseOptionValue(descs.get("MyLongOption"), "11g"));
        Assert.assertEquals(11L * 1024 * 1024 * 1024 * 1024, parseOptionValue(descs.get("MyLongOption"), "11t"));

        OptionsParser.parseOptionSettingTo("MyOption=a value", EconomicMap.create());
        try {
            OptionsParser.parseOptionSettingTo("MyOption:a value", null);
            Assert.fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // Expected
        }

        EconomicMap<String, String> optionSettings = EconomicMap.create();
        optionSettings.put("MyOption", "value 1");
        optionSettings.put("MyIntegerOption", "1001");
        EconomicMap<OptionKey<?>, Object> values = OptionValues.newOptionMap();
        Iterable<OptionDescriptors> loader = List.of(descs);
        OptionsParser.parseOptions(optionSettings, values, loader);

        try {
            optionSettings.put("MyOptionXX", "value 1");
            OptionsParser.parseOptions(optionSettings, values, loader);
            Assert.fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // Expected
        }
        try {
            optionSettings.put("MyIntegerOption", "one thousand and one");
            OptionsParser.parseOptions(optionSettings, values, loader);
            Assert.fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // Expected
        }
        try {
            OptionsParser.parseOptionValue(descs.get("MyIntegerOption"), 1001F);
            Assert.fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // Expected
        }
        try {
            OptionsParser.parseOptionValue(descs.get("MyIntegerOption"), "");
            Assert.fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // Expected
        }
        try {
            OptionsParser.parseOptionValue(descs.get("MyBooleanOption"), "not true or false");
            Assert.fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // Expected
        }
    }

    @Test
    public void testStability() {
        for (OptionStability s : OptionStability.values()) {
            Assert.assertTrue(s.name().equals("STABLE") || s.name().equals("EXPERIMENTAL"));
        }
    }

    @Test
    public void missingDescriptorTest() {
        OptionValues options = new OptionValues(OptionValues.newOptionMap());
        Assume.assumeTrue(OptionValues.class.desiredAssertionStatus() == true);
        boolean sawAssertionError = false;
        try {
            MyOtherOption.getValue(options);
        } catch (AssertionError e) {
            sawAssertionError = true;
        }
        Assert.assertTrue(sawAssertionError);
    }

    /**
     * Tests that initial values are properly copied.
     */
    @Test
    public void testDerived() {
        OptionValues initialOptions = new ModifiableOptionValues(asMap(MyOption, "new value 1"));
        OptionValues derivedOptions = new OptionValues(initialOptions, MyOtherOption, "ignore");
        Assert.assertEquals("new value 1", MyOption.getValue(derivedOptions));

        initialOptions = new OptionValues(asMap(MyOption, "new value 1"));
        derivedOptions = new OptionValues(initialOptions, MyOtherOption, "ignore");
        Assert.assertEquals("new value 1", MyOption.getValue(derivedOptions));

    }

    @Test
    public void testEnumOptionValidValue() {
        EnumOptionKey<TestEnum> option = new EnumOptionKey<>(TestEnum.Default);
        for (TestEnum e : TestEnum.values()) {
            option.valueOf(e.name());
        }

        EconomicMap<OptionKey<?>, Object> map = OptionValues.newOptionMap();

        try {
            option.update(map, "other 1");
            Assert.fail("expected ClassCastException");
        } catch (ClassCastException e) {
            // Expected
        }

        // Reset the map since the update above actually modifies the map
        // before the exception is thrown.
        map = OptionValues.newOptionMap();
        option.update(map, TestEnum.Value3);
        OptionValues values = new OptionValues(map);
        OptionDescriptor.create("myEnumOption", OptionType.User, String.class, "", Options.class, "myEnumOption", option);
        Assert.assertEquals(option.getValue(values), TestEnum.Value3);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testEnumOptionInvalidValue() {
        EnumOptionKey<TestEnum> option = new EnumOptionKey<>(TestEnum.Default);
        option.valueOf("invalid");
    }

    @SuppressWarnings("unused")
    @Test(expected = IllegalArgumentException.class)
    public void testEnumOptionNullDefault() {
        new EnumOptionKey<>(null);
    }

    @SuppressWarnings({"varargs", "unchecked"})
    private static <T> EconomicSet<T> setOf(T... elements) {
        EconomicSet<T> res = EconomicSet.create();
        res.addAll(List.of(elements));
        return res;
    }

    private static void assertStringEquals(Object expected, Object actual) {
        Assert.assertEquals(String.valueOf(expected), String.valueOf(actual));
    }

    @Test
    public void testEnumMultiOptionValidValue() {
        EnumMultiOptionKey<TestEnum> option = MyMultiEnumOption;
        Assert.assertEquals(option.getEnumClass(), TestEnum.class);
        Assert.assertNull(option.getDefaultValue());

        Assert.assertEquals(option.getAllValues(), EnumSet.allOf(TestEnum.class));

        OptionDescriptors descs = OPTION_DESCRIPTORS;
        assertStringEquals(setOf(), parseOptionValue(descs.get("MyMultiEnumOption"), ""));
        assertStringEquals(setOf(Value3), parseOptionValue(descs.get("MyMultiEnumOption"), "Value3"));
        assertStringEquals(setOf(Value4, Value2), parseOptionValue(descs.get("MyMultiEnumOption"), "Value4,Value2"));

        for (TestEnum e : TestEnum.values()) {
            option.valueOf(e.name());
        }

        String allValues = Stream.of(TestEnum.values()).map(Object::toString).collect(Collectors.joining(","));

        // Test parsing of a valid value
        option.valueOf("");
        option.valueOf(allValues);

        // Test parsing of invalid values
        for (String badValue : new String[]{allValues.replace(',', ' '), "invalid"}) {
            try {
                option.valueOf(badValue);
                Assert.fail("expected IllegalArgumentException");
            } catch (IllegalArgumentException e) {
                // Expected
            }
        }
    }

    @Test
    public void testModifiableOptionValues() {
        EconomicMap<OptionKey<?>, Object> map = asMap(MyOption, "value 1", MySecondOption, "other 1");
        ModifiableOptionValues values = new ModifiableOptionValues(map);
        Assert.assertTrue(MyOption.hasBeenSet(values));
        Assert.assertTrue(MySecondOption.hasBeenSet(values));
        Assert.assertEquals(MyOption.getValue(values), "value 1");
        Assert.assertEquals(MySecondOption.getValue(values), "other 1");
        values.update(MyOption, "value 2");
        Assert.assertEquals(MyOption.getValue(values), "value 2");
        Assert.assertTrue(MyOption.hasBeenSet(values));
        Assert.assertEquals(MySecondOption.getValue(values), "other 1");
        values.update(MyOption, ModifiableOptionValues.UNSET_KEY);
        Assert.assertFalse(MyOption.hasBeenSet(values));

        values.update(newOptionMap());
        values.update(asMap(MyOption, "value 3"));
        Assert.assertTrue(MyOption.hasBeenSet(values));
        Assert.assertEquals(MyOption.getValue(values), "value 3");
        values.update(asMap(MyOption, ModifiableOptionValues.UNSET_KEY));
        Assert.assertFalse(MyOption.hasBeenSet(values));
    }

    @Test
    public void testOptionDescriptors() {
        OptionDescriptor desc = MyDeprecatedOption.getDescriptor();
        Assert.assertTrue(desc.isDeprecated());
        Assert.assertEquals(desc.getOptionType(), OptionType.User);
        Assert.assertEquals(desc.getDeprecationMessage(), "Some deprecation message");
        Assert.assertEquals(desc.getHelp(), "Help for MyDeprecatedOption with");
        Assert.assertEquals(desc.getExtraHelp(), List.of("some extra text on", "following lines."));
        Assert.assertEquals(desc.getLocation(), Options.class.getName() + ".MyDeprecatedOption");

        EconomicMap<String, OptionDescriptor> map = EconomicMap.create();
        map.put("MyOption", MyOption.getDescriptor());
        map.put("MySecondOption", MySecondOption.getDescriptor());
        map.put("MyDeprecatedOption", MyDeprecatedOption.getDescriptor());
        OptionDescriptorsMap descriptors = new OptionDescriptorsMap(map);
        for (OptionDescriptor d : descriptors) {
            Assert.assertEquals(descriptors.get(d.getName()), d);
        }
    }
}
