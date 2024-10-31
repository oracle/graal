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

import jdk.graal.compiler.util.args.Command;
import jdk.graal.compiler.util.args.CommandParsingException;
import jdk.graal.compiler.util.args.Flag;
import jdk.graal.compiler.util.args.HelpRequestedException;
import jdk.graal.compiler.util.args.IntegerValue;
import jdk.graal.compiler.util.args.InvalidArgumentException;
import jdk.graal.compiler.util.args.MissingArgumentException;
import jdk.graal.compiler.util.args.OptionValue;
import jdk.graal.compiler.util.args.StringValue;

public class CommandTest {

    @Test
    public void testSimpleCommand() throws HelpRequestedException, CommandParsingException {
        Command c = new Command("test", "");
        OptionValue<String> arg1 = c.addPositional(new StringValue("ARG1", ""));
        OptionValue<String> arg2 = c.addPositional(new StringValue("ARG2", ""));
        OptionValue<String> arg3 = c.addNamed("--arg3", new StringValue("ARG3", ""));
        OptionValue<String> arg4 = c.addNamed("--unset", new StringValue("ARG4", "default", ""));
        String[] args = new String[]{"a", "--arg3", "c", "b"};
        int parsed = c.parse(args, 0);
        Assert.assertEquals(args.length, parsed);
        Assert.assertEquals("a", arg1.getValue());
        Assert.assertEquals("b", arg2.getValue());
        Assert.assertEquals("c", arg3.getValue());
        Assert.assertEquals("default", arg4.getValue());
    }

    @Test
    public void testInvalidCommand() {
        Command c = new Command("test", "");
        c.addPositional(new StringValue("ARG1", ""));
        c.addPositional(new StringValue("ARG2", ""));
        c.addNamed("--arg3", new StringValue("ARG3", ""));
        c.addNamed("--unset", new StringValue("ARG4", "default", ""));
        String[] args;
        // No arguments
        try {
            args = new String[]{};
            c.parse(args, 0);
            Assert.fail("Expected missing argument exception");
        } catch (CommandParsingException e) {
            Assert.assertTrue("Expected missing argument exception", e.getCause() instanceof MissingArgumentException);
        } catch (Exception e) {
            Assert.fail("Expected missing argument exception");
        }
        // Missing required named argument
        try {
            args = new String[]{"a", "b"};
            c.parse(args, 0);
            Assert.fail("Expected missing argument exception");
        } catch (CommandParsingException e) {
            Assert.assertTrue("Expected missing argument exception", e.getCause() instanceof MissingArgumentException);
        } catch (Exception e) {
            Assert.fail("Expected missing argument exception");
        }
        // No value provided to named option
        try {
            args = new String[]{"a", "b", "--arg3"};
            c.parse(args, 0);
            Assert.fail("Expected InvalidArgumentException");
        } catch (CommandParsingException e) {
            Assert.assertTrue("Expected InvalidArgumentException", e.getCause() instanceof InvalidArgumentException);
        } catch (Exception e) {
            Assert.fail("Expected InvalidArgumentException");
        }
    }

    @Test
    public void testNamedValueEquals() throws HelpRequestedException, CommandParsingException {
        Command c = new Command("test", "");
        OptionValue<String> arg1 = c.addNamed("--arg1", new StringValue("ARG1", ""));
        OptionValue<String> arg2 = c.addNamed("--arg2", new StringValue("ARG2", ""));
        OptionValue<String> arg3 = c.addPositional(new StringValue("ARG3", ""));
        String[] args = new String[]{"--arg1=a", "--arg2=b", "c"};
        int parsed = c.parse(args, 0);
        Assert.assertEquals(args.length, parsed);
        Assert.assertEquals("a", arg1.getValue());
        Assert.assertEquals("b", arg2.getValue());
        Assert.assertEquals("c", arg3.getValue());
    }

    @Test
    public void testFlags() throws HelpRequestedException, CommandParsingException {
        Command c = new Command("test", "");
        OptionValue<Boolean> flag = c.addNamed("--flag", new Flag(""));
        OptionValue<String> positional = c.addPositional(new StringValue("ARG", ""));
        String[] args;

        // FLag followed by value
        args = new String[]{"--flag", "value"};
        int parsed = c.parse(args, 0);
        Assert.assertEquals(args.length, parsed);
        Assert.assertTrue(flag.getValue());
        Assert.assertEquals("value", positional.getValue());
        flag.clear();
        positional.clear();

        // Flag with no following argument
        args = new String[]{"value", "--flag"};
        parsed = c.parse(args, 0);
        Assert.assertEquals(args.length, parsed);
        Assert.assertTrue(flag.getValue());
        Assert.assertEquals("value", positional.getValue());
    }

    @Test
    public void testPositionalList() throws HelpRequestedException, CommandParsingException {
        Command c = new Command("test", "");
        OptionValue<List<Integer>> arg1 = c.addPositional(new IntegerValue("ARG1", "").repeated());
        OptionValue<String> arg2 = c.addPositional(new StringValue("ARG2", "default", ""));
        OptionValue<String> arg3 = c.addNamed("--name", new StringValue("NAME", "default2", ""));

        // Fully parsed list
        String[] args = new String[]{"1", "2", "3"};
        int parsed = c.parse(args, 0);
        Assert.assertEquals(args.length, parsed);
        Assert.assertEquals(List.of(1, 2, 3), arg1.getValue());
        Assert.assertEquals("default", arg2.getValue());
        Assert.assertEquals("default2", arg3.getValue());
        arg1.clear();
        arg2.clear();
        arg3.clear();

        // List interrupted by named option
        args = new String[]{"1", "2", "--name", "3", "4"};
        parsed = c.parse(args, 0);
        Assert.assertEquals(args.length, parsed);
        Assert.assertEquals(List.of(1, 2), arg1.getValue());
        Assert.assertEquals("4", arg2.getValue());
        Assert.assertEquals("3", arg3.getValue());
        arg1.clear();
        arg2.clear();
        arg3.clear();

        // List interrupted by separator
        args = new String[]{"1", "2", "--", "3"};
        parsed = c.parse(args, 0);
        Assert.assertEquals(args.length, parsed);
        Assert.assertEquals(List.of(1, 2), arg1.getValue());
        Assert.assertEquals("3", arg2.getValue());
        Assert.assertFalse(arg3.isSet());
        arg1.clear();
        arg2.clear();
        arg3.clear();

        // List interrupted by parsing error
        args = new String[]{"1", "2", "string"};
        parsed = c.parse(args, 0);
        Assert.assertEquals(args.length, parsed);
        Assert.assertEquals(List.of(1, 2), arg1.getValue());
        Assert.assertEquals("string", arg2.getValue());
        Assert.assertFalse(arg3.isSet());
    }

    @Test
    public void testHelpOption() {
        Command c = new Command("test", "");
        c.addPositional(new StringValue("ARG1", ""));
        c.addPositional(new StringValue("ARG2", "").repeated());
        String[] args;
        try {
            args = new String[]{"--help"};
            c.parse(args, 0);
            Assert.fail("Expected HelpRequestedException");
        } catch (HelpRequestedException e) {
            // expected
        } catch (Exception e) {
            Assert.fail("Expected HelpRequestedException");
        }
        // Help in the middle of a list
        try {
            args = new String[]{"a", "b", "--help", "c"};
            c.parse(args, 0);
            Assert.fail("Expected HelpRequestedException");
        } catch (HelpRequestedException e) {
            // expected
        } catch (Exception e) {
            Assert.fail("Expected HelpRequestedException");
        }
    }
}
