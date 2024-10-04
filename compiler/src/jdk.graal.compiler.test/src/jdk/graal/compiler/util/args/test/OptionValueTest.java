/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.util.args.test;

import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.util.args.BooleanValue;
import jdk.graal.compiler.util.args.Flag;
import jdk.graal.compiler.util.args.IntegerValue;
import jdk.graal.compiler.util.args.InvalidArgumentException;
import jdk.graal.compiler.util.args.MultiChoiceValue;
import jdk.graal.compiler.util.args.OptionValue;
import jdk.graal.compiler.util.args.StringValue;

public class OptionValueTest {

    @Test
    public void testIsRequired() {
        OptionValue<String> required = new StringValue("OPT1", "");
        OptionValue<String> optional = new StringValue("OPT2", "default", "");
        Assert.assertTrue(required.isRequired());
        Assert.assertFalse(optional.isRequired());
    }

    @Test
    public void testIsSet() throws InvalidArgumentException {
        OptionValue<String> option = new StringValue("ARG1", "");
        Assert.assertFalse(option.isSet());
        option.parseValue("value");
        Assert.assertTrue(option.isSet());
        Assert.assertEquals("value", option.getValue());
    }

    @Test
    public void testIntegerValue() throws InvalidArgumentException {
        OptionValue<Integer> option = new IntegerValue("ARG1", "");
        try {
            Assert.assertFalse(option.parseValue("value"));
            Assert.fail("Expected InvalidArgumentException");
        } catch (InvalidArgumentException e) {
            // expected
        }
        Assert.assertFalse(option.isSet());
        Assert.assertTrue(option.parseValue("42"));
        Assert.assertTrue(option.isSet());
        Assert.assertEquals(42, option.getValue().intValue());
        Assert.assertTrue(option.parseValue("-100"));
        Assert.assertEquals(-100, option.getValue().intValue());
    }

    @Test
    public void testBooleanValue() throws InvalidArgumentException {
        OptionValue<Boolean> option = new BooleanValue("ARG1", "");
        try {
            Assert.assertFalse(option.parseValue("value"));
            Assert.fail("Expected InvalidArgumentException");
        } catch (InvalidArgumentException e) {
            // expected
        }
        Assert.assertFalse(option.isSet());
        Assert.assertTrue(option.parseValue("true"));
        Assert.assertTrue(option.isSet());
        Assert.assertTrue(option.getValue());
        Assert.assertTrue(option.parseValue("FALSE"));
        Assert.assertFalse(option.getValue());
    }

    @Test
    public void testFlag() throws InvalidArgumentException {
        OptionValue<Boolean> option = new Flag("");
        Assert.assertFalse(option.isSet());
        Assert.assertFalse(option.getValue());
        Assert.assertFalse(option.parseValue(null));
        Assert.assertTrue(option.isSet());
        Assert.assertTrue(option.getValue());
    }

    enum TestEnum {
        OptionA,
        OptionB,
        OptionC,
        OptionD
    }

    @Test
    public void testEnum() throws InvalidArgumentException {
        MultiChoiceValue<TestEnum> option = new MultiChoiceValue<>("", "");
        option.addChoice("OptionA", TestEnum.OptionA, "");
        option.addChoice("OptionB", TestEnum.OptionB, "");
        option.addChoice("OptionD", TestEnum.OptionD, "");

        Assert.assertFalse(option.isSet());
        Assert.assertTrue(option.parseValue("OptionA"));
        Assert.assertTrue(option.isSet());
        Assert.assertEquals(TestEnum.OptionA, option.getValue());
        Assert.assertTrue(option.parseValue("OptionD"));
        Assert.assertTrue(option.isSet());
        Assert.assertEquals(TestEnum.OptionD, option.getValue());
        try {
            Assert.assertFalse(option.parseValue("OptionC"));
            Assert.fail("Expected InvalidArgumentException");
        } catch (InvalidArgumentException e) {
            // expected
        }
    }

    @Test
    public void testRepeated() throws InvalidArgumentException {
        OptionValue<List<String>> option = new StringValue("REPEATED", "").repeated();
        Assert.assertFalse(option.isSet());
        option.parseValue("a");
        Assert.assertTrue(option.isSet());
        Assert.assertEquals(List.of("a"), option.getValue());
        option.parseValue("b");
        Assert.assertTrue(option.isSet());
        Assert.assertEquals(List.of("a", "b"), option.getValue());
        option.parseValue("c");
        Assert.assertTrue(option.isSet());
        Assert.assertEquals(List.of("a", "b", "c"), option.getValue());
    }

    @Test
    public void testRepeatedDefault() throws InvalidArgumentException {
        OptionValue<List<String>> option = new StringValue("REPEATED", "default", "").repeated();
        Assert.assertEquals(List.of("default"), option.getValue());
        option.parseValue("a");
        option.parseValue("b");
        Assert.assertEquals(List.of("a", "b"), option.getValue());
        option.clear();
        Assert.assertEquals(List.of("default"), option.getValue());
    }
}
