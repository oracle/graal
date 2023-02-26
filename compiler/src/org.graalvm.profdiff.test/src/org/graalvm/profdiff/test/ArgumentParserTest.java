/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.profdiff.command.Command;
import org.graalvm.profdiff.core.HotCompilationUnitPolicy;
import org.graalvm.profdiff.parser.args.ArgumentParser;
import org.graalvm.profdiff.parser.args.CommandGroup;
import org.graalvm.profdiff.parser.args.DoubleArgument;
import org.graalvm.profdiff.parser.args.EnumArgument;
import org.graalvm.profdiff.parser.args.FlagArgument;
import org.graalvm.profdiff.parser.args.IntegerArgument;
import org.graalvm.profdiff.parser.args.InvalidArgumentException;
import org.graalvm.profdiff.parser.args.MissingArgumentException;
import org.graalvm.profdiff.parser.args.ProgramArgumentParser;
import org.graalvm.profdiff.parser.args.StringArgument;
import org.graalvm.profdiff.parser.args.UnknownArgumentException;
import org.graalvm.profdiff.util.Writer;
import org.junit.Test;

public class ArgumentParserTest {
    private static final double DELTA = 0.000001;

    private enum TestEnum {
        FOO,
        BAR
    }

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

        @Override
        public void setHotCompilationUnitPolicy(HotCompilationUnitPolicy hotCompilationUnitPolicy) {

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

        @Override
        public void setHotCompilationUnitPolicy(HotCompilationUnitPolicy hotCompilationUnitPolicy) {

        }
    }

    private static class ProgramArguments {
        private static final double DEFAULT_DOUBLE = 3.14;

        private static final int DEFAULT_INT = 42;

        private static final TestEnum DEFAULT_ENUM = TestEnum.FOO;

        private final ProgramArgumentParser argumentParser;

        private final DoubleArgument doubleArgument;

        private final IntegerArgument integerArgument;

        private final FlagArgument flagArgument;

        private final StringArgument stringArgument;

        private final EnumArgument<TestEnum> enumArgument;

        private final CommandGroup commandGroup;

        private final CommandFoo commandFoo;

        private final CommandBar commandBar;

        ProgramArguments() {
            argumentParser = new ProgramArgumentParser("program", "Program description.");
            doubleArgument = argumentParser.addDoubleArgument("--double", DEFAULT_DOUBLE, "A double argument.");
            integerArgument = argumentParser.addIntegerArgument("--int", DEFAULT_INT, "An integer argument.");
            flagArgument = argumentParser.addFlagArgument("--flag", "A flag argument.");
            stringArgument = argumentParser.addStringArgument("string", "A string argument.");
            enumArgument = argumentParser.addEnumArgument("--enum", DEFAULT_ENUM, "An enum argument.");
            commandGroup = argumentParser.addCommandGroup("command", "Commands.");
            commandFoo = new CommandFoo();
            commandGroup.addCommand(commandFoo);
            commandBar = new CommandBar();
            commandGroup.addCommand(commandBar);
        }
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
        assertEquals(ProgramArguments.DEFAULT_ENUM, programArguments.enumArgument.getValue());
        assertEquals(programArguments.commandFoo, programArguments.commandGroup.getSelectedCommand());
    }

    @Test
    public void parseProvidedValues() throws UnknownArgumentException, InvalidArgumentException, MissingArgumentException {
        ProgramArguments programArguments = new ProgramArguments();
        String[] args = new String[]{"--int", "123", "str", "--double", "1.23", "--flag", "--enum", TestEnum.BAR.toString(), "bar", "--bar-flag"};
        programArguments.argumentParser.parse(args);
        assertEquals(args[2], programArguments.stringArgument.getValue());
        assertEquals(1.23, programArguments.doubleArgument.getValue(), DELTA);
        assertEquals(123, programArguments.integerArgument.getValue().intValue());
        assertEquals(TestEnum.BAR, programArguments.enumArgument.getValue());
        assertTrue(programArguments.flagArgument.getValue());
        assertEquals(programArguments.commandBar, programArguments.commandGroup.getSelectedCommand());
        assertTrue(programArguments.commandBar.flagArgument.getValue());
    }

    @Test
    public void equalSignNotation() throws UnknownArgumentException, InvalidArgumentException, MissingArgumentException {
        ProgramArguments programArguments = new ProgramArguments();
        String[] args = new String[]{"--int=123", "foo", "--double=1.23", "--flag", "--enum=" + TestEnum.BAR, "foo"};
        programArguments.argumentParser.parse(args);
        assertEquals(args[1], programArguments.stringArgument.getValue());
        assertEquals(1.23, programArguments.doubleArgument.getValue(), DELTA);
        assertEquals(123, programArguments.integerArgument.getValue().intValue());
        assertEquals(TestEnum.BAR, programArguments.enumArgument.getValue());
        assertTrue(programArguments.flagArgument.getValue());
        assertEquals(programArguments.commandFoo, programArguments.commandGroup.getSelectedCommand());
    }

    @Test
    public void enumArgumentCaseInsensitive() throws UnknownArgumentException, InvalidArgumentException, MissingArgumentException {
        ProgramArguments programArguments = new ProgramArguments();
        String[] args = new String[]{"foo", "--enum", TestEnum.BAR.toString().toLowerCase(), "foo"};
        programArguments.argumentParser.parse(args);
        assertEquals(TestEnum.BAR, programArguments.enumArgument.getValue());
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
}
