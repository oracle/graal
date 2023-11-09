/*
 * Copyright (c) 2016, 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.debug.test;

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;

import jdk.graal.compiler.debug.CSVUtil;
import jdk.graal.compiler.debug.LogStream;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Enclosed.class)
public class CSVUtilTest {

    @RunWith(Parameterized.class)
    public static class FormatStringBuilder {
        /** Some interesting values. */
        private static final Object[][] values = {
                        {"", ""},
                        {"%s", "%s"},
                        {"%s,%s", "%s;%s"},
        };

        @Parameters(name = " [{0}] to \"{1}\" ")
        public static Collection<Object[]> data() {
            return Arrays.asList(values);
        }

        @Parameter(value = 0) public String input;
        @Parameter(value = 1) public String expected;

        @Test
        public void testBuildFormatString() {
            assertEquals(expected, CSVUtil.buildFormatString(input.split(",")));
        }
    }

    @RunWith(Parameterized.class)
    public static class Escape {

        /** Some interesting values. */
        private static final Object[][] values = {
                        {"XXX\"YYY", "\"XXX\\\"YYY\""},
                        {"X\\XX\"YYY", "\"X\\\\XX\\\"YYY\""},
        };

        @Parameters(name = "''{0}'' to ''{1}''")
        public static Collection<Object[]> data() {
            return Arrays.asList(values);
        }

        @Parameter(value = 0) public String input;
        @Parameter(value = 1) public String expected;

        @Test
        public void testEscape() {
            assertEquals(expected, CSVUtil.Escape.escapeRaw(input));
        }

    }

    @RunWith(Parameterized.class)
    public static class Formatter {
        /** Some interesting values. */
        private static final Object[][] values = {
                        {"%s;%s", "XXX,YYY", "XXX;YYY"},
                        {"%s;%s", "XXX,Y\"YY", "XXX;Y\"YY"},
                        {"%s;%s", "XXX,Y;YY", "XXX;\"Y;YY\""},
                        {"%s;%s", "XXX,Y\"Y;Y", "XXX;\"Y\\\"Y;Y\""},
        };

        @Parameters(name = "format=''{0}'' args=''{1}'' output=''{2}''")
        public static Collection<Object[]> data() {
            return Arrays.asList(values);
        }

        @Parameter(value = 0) public String format;
        @Parameter(value = 1) public String args;
        @Parameter(value = 2) public String expected;

        @Test
        public void testFormatter() {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            // call the method under test
            CSVUtil.Escape.println(new PrintStream(outputStream), format, toObjectArray(args));
            // get the actual string
            String printedStream = new String(outputStream.toByteArray(), StandardCharsets.UTF_8);
            // add newline to the expected string
            assertEquals(expected + System.lineSeparator(), printedStream);

            // same, but with LogStream
            ByteArrayOutputStream outputStream2 = new ByteArrayOutputStream();
            LogStream logStream = new LogStream(outputStream2);
            CSVUtil.Escape.println(logStream, format, toObjectArray(args));
            logStream.flush();
            String printedStream2 = new String(outputStream2.toByteArray(), StandardCharsets.UTF_8);
            assertEquals(expected + System.lineSeparator(), printedStream2);
        }

        private static Object[] toObjectArray(String args) {
            String[] split = args.split(",");
            Object[] obj = new Object[split.length];
            for (int i = 0; i < split.length; i++) {
                obj[i] = split[i];
            }
            return obj;
        }
    }

    public static class Other {
        @Test
        public void testBuildFormatString() {
            Assert.assertEquals("%s-%s-%s", CSVUtil.buildFormatString("%s", '-', 3));
        }

        @Test
        public void testDefaultEscape() {
            Assert.assertEquals("ab", CSVUtil.Escape.escape("ab"));
            Assert.assertEquals("\"ab;_\\\\_\\\"_cd\"", CSVUtil.Escape.escape("ab;_\\_\"_cd"));
        }

        @Test
        public void testDefaultEscapeArgs() {
            Object[] result = CSVUtil.Escape.escapeArgs("ab", "ab;_\\_\"_cd");
            Assert.assertEquals("ab", result[0]);
            Assert.assertEquals("\"ab;_\\\\_\\\"_cd\"", result[1]);
        }
    }

}
