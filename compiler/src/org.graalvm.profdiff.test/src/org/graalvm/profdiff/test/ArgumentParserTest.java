/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.profdiff.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.graalvm.profdiff.args.BooleanArgument;
import org.graalvm.profdiff.command.Command;
import org.graalvm.profdiff.args.ArgumentParser;
import org.graalvm.profdiff.args.CommandGroup;
import org.graalvm.profdiff.args.DoubleArgument;
import org.graalvm.profdiff.args.FlagArgument;
import org.graalvm.profdiff.args.IntegerArgument;
import org.graalvm.profdiff.args.InvalidArgumentException;
import org.graalvm.profdiff.args.MissingArgumentException;
import org.graalvm.profdiff.args.ProgramArgumentParser;
import org.graalvm.profdiff.args.StringArgument;
import org.graalvm.profdiff.args.UnknownArgumentException;
import org.graalvm.profdiff.core.Writer;
import org.junit.Test;

public class ArgumentParserTest {
    private static final double DELTA = 0.000001;

    private static final class CommandFoo implements Command {
        private final ArgumentParser argumentParser = new ArgumentParser();

        @Override
        public String getName() {
            return "foo";
        }

        @Override
        public String getDescription() {
            return "Command foo.";
        }

        @Override
        public ArgumentParser getArgumentParser() {
            return argumentParser;
        }

        @Override
        public void invoke(Writer writer) {

        }
    }

    private static final class CommandBar implements Command {
        private final ArgumentParser argumentParser = new ArgumentParser();

        private final FlagArgument flagArgument;

        private CommandBar() {
            flagArgument = argumentParser.addFlagArgument("--bar-flag", "A flag argument for the bar command.");
        }

        @Override
        public String getName() {
            return "bar";
        }

        @Override
        public String getDescription() {
            return "Command foo.";
        }

        @Override
        public ArgumentParser getArgumentParser() {
            return argumentParser;
        }

        @Override
        public void invoke(Writer writer) {

        }
    }

    private static class ProgramArguments {
        private static final double DEFAULT_DOUBLE = 3.14;

        private static final int DEFAULT_INT = 42;

        private final ProgramArgumentParser argumentParser;

        private final DoubleArgument doubleArgument;

        private final IntegerArgument integerArgument;

        private final FlagArgument flagArgument;

        private final StringArgument stringArgument;

        private final CommandGroup commandGroup;

        private final CommandFoo commandFoo;

        private final CommandBar commandBar;

        ProgramArguments() {
            argumentParser = new ProgramArgumentParser("program", "Program description.");
            doubleArgument = argumentParser.addDoubleArgument("--double", DEFAULT_DOUBLE, "A double argument.");
            integerArgument = argumentParser.addIntegerArgument("--int", DEFAULT_INT, "An integer argument.");
            flagArgument = argumentParser.addFlagArgument("--flag", "A flag argument.");
            stringArgument = argumentParser.addStringArgument("string", "A string argument.");
            commandGroup = argumentParser.addCommandGroup("command", "Commands.");
            commandFoo = new CommandFoo();
            commandGroup.addCommand(commandFoo);
            commandBar = new CommandBar();
            commandGroup.addCommand(commandBar);
        }
    }

    @Test
    public void formatPositionalUsageForCommand() {
        var parser = new ProgramArgumentParser("program", "Program description.");
        parser.addStringArgument("string", "String argument.");
        var commandGroup = parser.addCommandGroup("command", "Commands.");
        var foo = new CommandFoo();
        commandGroup.addCommand(foo);
        assertEquals("STRING foo", parser.formatPositionalUsage(foo));
    }

    @Test
    public void formatHelpForCommand() {
        var parser = new ProgramArgumentParser("program", "Program description.");
        var commandGroup = parser.addCommandGroup("command", "Commands.");
        var foo = new CommandFoo();
        commandGroup.addCommand(foo);
        foo.argumentParser.addStringArgument("--string", "A string argument.");
        String help = parser.formatHelp(foo);
        assertTrue(help.contains("program foo --string STRING"));
        assertTrue(help.contains("A string argument."));
    }

    @Test
    public void requiredOptionHelp() {
        var parser = new ProgramArgumentParser("program", "Program description.");
        parser.addStringArgument("--string", "String argument.");
        String help = parser.formatOptionHelp();
        assertTrue(help.contains("--string"));
        assertFalse(help.contains("null"));
    }

    @Test
    public void parseDefaultValues() throws UnknownArgumentException, InvalidArgumentException, MissingArgumentException {
        ProgramArguments programArguments = new ProgramArguments();
        String[] args = new String[]{"str", "foo"};
        programArguments.argumentParser.parse(args);
        assertEquals(args[0], programArguments.stringArgument.getValue());
        assertEquals(ProgramArguments.DEFAULT_DOUBLE, programArguments.doubleArgument.getValue(), DELTA);
        assertEquals(ProgramArguments.DEFAULT_INT, programArguments.integerArgument.getValue().intValue());
        assertFalse(programArguments.flagArgument.getValue());
        assertEquals(programArguments.commandFoo, programArguments.commandGroup.getSelectedCommand());
    }

    @Test
    public void parseProvidedValues() throws UnknownArgumentException, InvalidArgumentException, MissingArgumentException {
        ProgramArguments programArguments = new ProgramArguments();
        String[] args = new String[]{"--int", "123", "str", "--double", "1.23", "--flag", "bar", "--bar-flag"};
        programArguments.argumentParser.parse(args);
        assertEquals(args[2], programArguments.stringArgument.getValue());
        assertEquals(1.23, programArguments.doubleArgument.getValue(), DELTA);
        assertEquals(123, programArguments.integerArgument.getValue().intValue());
        assertTrue(programArguments.flagArgument.getValue());
        assertEquals(programArguments.commandBar, programArguments.commandGroup.getSelectedCommand());
        assertTrue(programArguments.commandBar.flagArgument.getValue());
    }

    @Test
    public void equalSignNotation() throws UnknownArgumentException, InvalidArgumentException, MissingArgumentException {
        ProgramArguments programArguments = new ProgramArguments();
        String[] args = new String[]{"--int=123", "foo", "--double=1.23", "--flag", "foo"};
        programArguments.argumentParser.parse(args);
        assertEquals(args[1], programArguments.stringArgument.getValue());
        assertEquals(1.23, programArguments.doubleArgument.getValue(), DELTA);
        assertEquals(123, programArguments.integerArgument.getValue().intValue());
        assertTrue(programArguments.flagArgument.getValue());
        assertEquals(programArguments.commandFoo, programArguments.commandGroup.getSelectedCommand());
    }

    @Test(expected = MissingArgumentException.class)
    public void missingPositionalArgument() throws UnknownArgumentException, InvalidArgumentException, MissingArgumentException {
        ProgramArguments programArguments = new ProgramArguments();
        String[] args = new String[]{"--int", "123"};
        programArguments.argumentParser.parse(args);
    }

    @Test(expected = InvalidArgumentException.class)
    public void argumentValueMissing() throws UnknownArgumentException, InvalidArgumentException, MissingArgumentException {
        ProgramArguments programArguments = new ProgramArguments();
        String[] args = new String[]{"str", "--int"};
        programArguments.argumentParser.parse(args);
    }

    @Test(expected = UnknownArgumentException.class)
    public void tooManyPositionalArguments() throws UnknownArgumentException, InvalidArgumentException, MissingArgumentException {
        ProgramArguments programArguments = new ProgramArguments();
        String[] args = new String[]{"str", "foo", "extra"};
        programArguments.argumentParser.parse(args);
    }

    @Test(expected = UnknownArgumentException.class)
    public void unknownOption() throws UnknownArgumentException, InvalidArgumentException, MissingArgumentException {
        ProgramArguments programArguments = new ProgramArguments();
        String[] args = new String[]{"str", "foo", "--extra"};
        programArguments.argumentParser.parse(args);
    }

    @Test(expected = InvalidArgumentException.class)
    public void invalidCommand() throws UnknownArgumentException, InvalidArgumentException, MissingArgumentException {
        ProgramArguments programArguments = new ProgramArguments();
        String[] args = new String[]{"str", "baz"};
        programArguments.argumentParser.parse(args);
    }

    @Test(expected = MissingArgumentException.class)
    public void missingOptionArgument() throws UnknownArgumentException, InvalidArgumentException, MissingArgumentException {
        ProgramArgumentParser parser = new ProgramArgumentParser("program", "Program description.");
        parser.addStringArgument("--string", "A string argument");
        parser.parse(new String[]{});
    }

    @Test
    public void getEmptyCommandGroup() {
        ProgramArgumentParser parser = new ProgramArgumentParser("program", "Program description.");
        assertTrue(parser.getCommandGroup().isEmpty());
    }

    @Test(expected = RuntimeException.class)
    public void onlyOneSubparserGroup() {
        ProgramArgumentParser parser = new ProgramArgumentParser("program", "Program description.");
        parser.addCommandGroup("foo", "Foo group.");
        parser.addCommandGroup("bar", "Bar group.");
    }

    @Test(expected = RuntimeException.class)
    public void optionalPositionalFollowedByRequired() {
        ProgramArgumentParser parser = new ProgramArgumentParser("program", "Program description.");
        parser.addStringArgument("argument1", "", "Argument 1.");
        parser.addStringArgument("argument2", "Argument 2.");
    }

    @Test(expected = RuntimeException.class)
    public void commandGroupMustBePositional() {
        ProgramArgumentParser parser = new ProgramArgumentParser("program", "Program description.");
        parser.addCommandGroup("--foo", "Foo group.");
    }

    @Test(expected = IllegalStateException.class)
    public void formatUsageForCommandWithoutCommandGroup() {
        ProgramArgumentParser parser = new ProgramArgumentParser("program", "Program description.");
        parser.formatPositionalUsage(new CommandFoo());
    }

    @Test
    public void falseBooleanArgument() throws UnknownArgumentException, InvalidArgumentException, MissingArgumentException {
        ProgramArgumentParser parser = new ProgramArgumentParser("program", "Program description.");
        BooleanArgument argument = parser.addBooleanArgument("--bool", true, "A boolean argument.");
        parser.parse(new String[]{"--bool", "falsE"});
        assertFalse(argument.getValue());
    }

    @Test(expected = InvalidArgumentException.class)
    public void invalidBooleanArgument() throws UnknownArgumentException, InvalidArgumentException, MissingArgumentException {
        ProgramArgumentParser parser = new ProgramArgumentParser("program", "Program description.");
        parser.addBooleanArgument("--bool", true, "A boolean argument.");
        parser.parse(new String[]{"--bool", "invalid"});
    }

    @Test(expected = InvalidArgumentException.class)
    public void invalidIntegerArgument() throws UnknownArgumentException, InvalidArgumentException, MissingArgumentException {
        ProgramArgumentParser parser = new ProgramArgumentParser("program", "Program description.");
        parser.addIntegerArgument("--int", 0, "An integer argument.");
        parser.parse(new String[]{"--int", "1.5"});
    }

    @Test(expected = InvalidArgumentException.class)
    public void invalidDoubleArgument() throws UnknownArgumentException, InvalidArgumentException, MissingArgumentException {
        ProgramArgumentParser parser = new ProgramArgumentParser("program", "Program description.");
        parser.addDoubleArgument("--double", 0, "A double argument.");
        parser.parse(new String[]{"--double", "null"});
    }
}
