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

import org.graalvm.profdiff.parser.args.ArgumentParser;
import org.graalvm.profdiff.parser.args.DoubleArgument;
import org.graalvm.profdiff.parser.args.EnumArgument;
import org.graalvm.profdiff.parser.args.FlagArgument;
import org.graalvm.profdiff.parser.args.IntegerArgument;
import org.graalvm.profdiff.parser.args.InvalidArgumentException;
import org.graalvm.profdiff.parser.args.MissingArgumentException;
import org.graalvm.profdiff.parser.args.StringArgument;
import org.graalvm.profdiff.parser.args.UnknownArgumentException;
import org.junit.Test;

public class ArgumentParserTest {
    private static final double DELTA = 0.000001;

    private enum TestEnum {
        FOO,
        BAR
    }

    private static class ProgramArguments {
        static final double DEFAULT_DOUBLE = 3.14;

        static final int DEFAULT_INT = 42;

        static final TestEnum DEFAULT_ENUM = TestEnum.FOO;

        ArgumentParser argumentParser;

        DoubleArgument doubleArgument;

        IntegerArgument integerArgument;

        FlagArgument flagArgument;

        StringArgument stringArgument;

        EnumArgument<TestEnum> enumArgument;

        ProgramArguments() {
            argumentParser = new ArgumentParser("program", "Program description.");
            doubleArgument = argumentParser.addDoubleArgument("--double", DEFAULT_DOUBLE, "A double argument.");
            integerArgument = argumentParser.addIntegerArgument("--int", DEFAULT_INT, "An integer argument.");
            flagArgument = argumentParser.addFlagArgument("--flag", "A flag argument.");
            stringArgument = argumentParser.addStringArgument("string", "A string argument.");
            enumArgument = argumentParser.addEnumArgument("--enum", DEFAULT_ENUM, "An enum argument.");
        }
    }

    @Test
    public void parsesDefaultValues() throws UnknownArgumentException, InvalidArgumentException, MissingArgumentException {
        ProgramArguments programArguments = new ProgramArguments();
        String[] args = new String[]{"foo"};
        programArguments.argumentParser.parse(args);
        assertEquals(args[0], programArguments.stringArgument.getValue());
        assertEquals(ProgramArguments.DEFAULT_DOUBLE, programArguments.doubleArgument.getValue(), DELTA);
        assertEquals(ProgramArguments.DEFAULT_INT, programArguments.integerArgument.getValue().intValue());
        assertFalse(programArguments.flagArgument.getValue());
        assertEquals(ProgramArguments.DEFAULT_ENUM, programArguments.enumArgument.getValue());
    }

    @Test
    public void parsesProvidedValues() throws UnknownArgumentException, InvalidArgumentException, MissingArgumentException {
        ProgramArguments programArguments = new ProgramArguments();
        String[] args = new String[]{"--int", "123", "foo", "--double", "1.23", "--flag", "--enum", TestEnum.BAR.toString()};
        programArguments.argumentParser.parse(args);
        assertEquals(args[2], programArguments.stringArgument.getValue());
        assertEquals(1.23, programArguments.doubleArgument.getValue(), DELTA);
        assertEquals(123, programArguments.integerArgument.getValue().intValue());
        assertEquals(TestEnum.BAR, programArguments.enumArgument.getValue());
        assertTrue(programArguments.flagArgument.getValue());
    }

    @Test
    public void equalSignNotation() throws UnknownArgumentException, InvalidArgumentException, MissingArgumentException {
        ProgramArguments programArguments = new ProgramArguments();
        String[] args = new String[]{"--int=123", "foo", "--double=1.23", "--flag", "--enum=" + TestEnum.BAR};
        programArguments.argumentParser.parse(args);
        assertEquals(args[1], programArguments.stringArgument.getValue());
        assertEquals(1.23, programArguments.doubleArgument.getValue(), DELTA);
        assertEquals(123, programArguments.integerArgument.getValue().intValue());
        assertEquals(TestEnum.BAR, programArguments.enumArgument.getValue());
        assertTrue(programArguments.flagArgument.getValue());
    }

    @Test
    public void enumArgumentCaseInsensitive() throws UnknownArgumentException, InvalidArgumentException, MissingArgumentException {
        ProgramArguments programArguments = new ProgramArguments();
        String[] args = new String[]{"foo", "--enum", TestEnum.BAR.toString().toLowerCase()};
        programArguments.argumentParser.parse(args);
        assertEquals(TestEnum.BAR, programArguments.enumArgument.getValue());
    }

    @Test(expected = MissingArgumentException.class)
    public void testMissingPositional() throws UnknownArgumentException, InvalidArgumentException, MissingArgumentException {
        ProgramArguments programArguments = new ProgramArguments();
        String[] args = new String[]{"--int", "123"};
        programArguments.argumentParser.parse(args);
    }

    @Test(expected = UnknownArgumentException.class)
    public void testUnknownArgument() throws UnknownArgumentException, InvalidArgumentException, MissingArgumentException {
        ProgramArguments programArguments = new ProgramArguments();
        String[] args = new String[]{"--bar", "123", "foo"};
        programArguments.argumentParser.parse(args);
    }

    @Test(expected = InvalidArgumentException.class)
    public void testArgumentValueMissing() throws UnknownArgumentException, InvalidArgumentException, MissingArgumentException {
        ProgramArguments programArguments = new ProgramArguments();
        String[] args = new String[]{"foo", "--int"};
        programArguments.argumentParser.parse(args);
    }

    @Test(expected = UnknownArgumentException.class)
    public void testTooManyArguments() throws UnknownArgumentException, InvalidArgumentException, MissingArgumentException {
        ProgramArguments programArguments = new ProgramArguments();
        String[] args = new String[]{"foo", "bar"};
        programArguments.argumentParser.parse(args);
    }
}
