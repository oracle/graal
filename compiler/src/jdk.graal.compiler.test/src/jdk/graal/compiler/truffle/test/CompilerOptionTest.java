/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.truffle.test;

import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

import org.graalvm.options.OptionDescriptor;
import org.graalvm.polyglot.Engine;
import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.test.polyglot.AbstractPolyglotTest;

public class CompilerOptionTest {

    @Test
    public void testListCompilerOptions() {
        try (Engine engine = Engine.create()) {
            int count = 0;
            for (OptionDescriptor descriptor : engine.getOptions()) {
                if (descriptor.getName().startsWith("compiler.")) {
                    count++;
                }
            }
            assertTrue("No compiler options found.", count > 0);
        }
    }

    @Test
    public void testCompilerOptionNotExisting() {
        Engine.Builder b = Engine.newBuilder().option("compiler.Inlinin", "false");
        AbstractPolyglotTest.assertFails(() -> b.build(), IllegalArgumentException.class,
                        (e) -> {
                            Assert.assertTrue(e.getMessage(), e.getMessage().contains("Could not find option with name compiler.Inlinin"));
                        });
    }

    @Test
    public void testCompilerInvalidOption() {
        Engine.Builder b = Engine.newBuilder().option("compiler.Inlining", "asdf");
        AbstractPolyglotTest.assertFails(() -> b.build(), IllegalArgumentException.class,
                        (e) -> {
                            Assert.assertEquals("Boolean option 'compiler.Inlining' must have value \"true\" or \"false\", not \"asdf\"", e.getMessage());
                        });
    }

    @Test
    public void testLegacyOption() {
        Engine.Builder b = Engine.newBuilder().option("engine.Inlining", "true");
        List<LogRecord> log = new ArrayList<>();
        b.logHandler(new Handler() {
            @Override
            public synchronized void publish(LogRecord record) {
                log.add(record);
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() throws SecurityException {

            }
        });
        Engine e = b.build();

        assertSingleRecordFound(log, (record) -> {
            return record.getLoggerName().equals("engine") && record.getMessage().startsWith("Option 'engine.Inlining' is deprecated:");
        });

        e.close();

    }

    private static void assertSingleRecordFound(List<LogRecord> log, Predicate<LogRecord> test) {
        boolean found = false;
        for (LogRecord record : log) {
            if (test.test(record)) {
                if (found) {
                    throw new AssertionError("Duplicate log records found: " + record);
                }
                record.getMessage().contains("");
                found = true;
            }
        }
        if (!found) {
            throw new AssertionError("No log record found. Other records found: " + log);
        }
    }

}
