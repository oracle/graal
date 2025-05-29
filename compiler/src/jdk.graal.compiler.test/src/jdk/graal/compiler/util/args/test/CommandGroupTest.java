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

import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.util.args.Command;
import jdk.graal.compiler.util.args.CommandGroup;
import jdk.graal.compiler.util.args.CommandParsingException;
import jdk.graal.compiler.util.args.HelpRequestedException;
import jdk.graal.compiler.util.args.InvalidArgumentException;
import jdk.graal.compiler.util.args.OptionValue;
import jdk.graal.compiler.util.args.StringValue;

public class CommandGroupTest {

    @Test
    public void testSimpleSubcommand() throws InvalidArgumentException, HelpRequestedException, CommandParsingException {
        CommandGroup<Command> group = new CommandGroup<>("subcommand", "");
        Command subA = group.addCommand(new Command("cmda", ""));
        Command subB = group.addCommand(new Command("cmdb", ""));
        Command subC = group.addCommand(new Command("cmdc", ""));
        Assert.assertNull(group.getSelectedCommand());

        String[] args = new String[]{"cmda"};
        int parsed = group.parse(args, 0);
        Assert.assertEquals(args.length, parsed);
        Assert.assertSame(subA, group.getSelectedCommand());
        group.clear();
        args = new String[]{"cmdb"};
        parsed = group.parse(args, 0);
        Assert.assertEquals(args.length, parsed);
        Assert.assertSame(subB, group.getSelectedCommand());
        group.clear();

        args = new String[]{"cmdc"};
        parsed = group.parse(args, 0);
        Assert.assertEquals(args.length, parsed);
        Assert.assertSame(subC, group.getSelectedCommand());
    }

    @Test
    public void testSubcommandSeparator() throws HelpRequestedException, CommandParsingException {
        Command outer = new Command("test", "");
        OptionValue<String> outerOption = outer.addNamed("--option", new StringValue("VAL", "default", ""));
        CommandGroup<Command> group = outer.addCommandGroup(new CommandGroup<>("subcommand", ""));
        Command inner = group.addCommand(new Command("sub", ""));
        OptionValue<String> innerOption = inner.addNamed("--option", new StringValue("VAL", "default", ""));

        String[] args = new String[]{"sub", "--option", "value"};
        int parsed = outer.parse(args, 0);
        Assert.assertEquals(args.length, parsed);
        Assert.assertFalse(outerOption.isSet());
        Assert.assertEquals("value", innerOption.getValue());
        group.clear();
        outerOption.clear();
        innerOption.clear();

        args = new String[]{"sub", "--", "--option", "value"};
        parsed = outer.parse(args, 0);
        Assert.assertEquals(args.length, parsed);
        Assert.assertFalse(innerOption.isSet());
        Assert.assertEquals("value", outerOption.getValue());
        group.clear();
        outerOption.clear();
        innerOption.clear();
    }

    @Test
    public void testPositionalAndSubcommand() throws HelpRequestedException, CommandParsingException {
        Command outer = new Command("test", "");
        OptionValue<String> positional = outer.addPositional(new StringValue("VAL", ""));
        CommandGroup<Command> group = outer.addCommandGroup(new CommandGroup<>("subcommand", ""));
        Command inner = group.addCommand(new Command("sub", ""));
        OptionValue<String> innerOption = inner.addNamed("--option", new StringValue("VAL", "default", ""));

        String[] args = new String[]{"value1", "sub", "--option", "value2"};
        int parsed = outer.parse(args, 0);
        Assert.assertEquals(args.length, parsed);
        Assert.assertTrue(positional.isSet());
        Assert.assertEquals("value1", positional.getValue());
        Assert.assertEquals("value2", innerOption.getValue());
    }

    @Test
    public void testSubcommandHelp() {
        Command c = new Command("test", "");
        CommandGroup<Command> inner = c.addCommandGroup(new CommandGroup<>("subcommand", ""));
        Command subcommand = inner.addCommand(new Command("sub", ""));

        // Help on outer command
        String[] args;
        try {
            args = new String[]{"--help"};
            c.parse(args, 0);
            Assert.fail("Expected HelpRequestedException");
        } catch (HelpRequestedException e) {
            Assert.assertSame(c, e.getCommand());
        } catch (Exception e) {
            Assert.fail("Expected HelpRequestedException");
        }
        // Help on subcommand
        try {
            args = new String[]{"sub", "--help"};
            c.parse(args, 0);
            Assert.fail("Expected HelpRequestedException");
        } catch (HelpRequestedException e) {
            Assert.assertSame(subcommand, e.getCommand());
        } catch (Exception e) {
            Assert.fail("Expected HelpRequestedException");
        }
    }
}
