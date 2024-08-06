/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

import jdk.graal.compiler.debug.LogStream;
import org.junit.Assert;
import org.junit.Test;

public class LogStreamTest {

    @Test
    public void testLogStream() {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        LogStream logStream = new LogStream(outputStream);

        Assert.assertEquals(0, logStream.position());

        logStream.enableIndentation();
        logStream.adjustIndentation(1);
        logStream.setIndentation('*');
        logStream.print(42);
        Assert.assertEquals(3, logStream.position()); // "*42"
        logStream.println();
        Assert.assertEquals('*', logStream.indentation());
        Assert.assertEquals(0, logStream.position());

        logStream.print(66);
        logStream.print('\n');

        logStream.flush();
        String printedStream = new String(outputStream.toByteArray(), StandardCharsets.UTF_8);
        assertEquals("*42" + System.lineSeparator() + "*66\n", printedStream);
    }

    @Test
    public void testLogStreamConsumer() {
        StringBuilder sb = new StringBuilder();
        LogStream logStream = new LogStream(s -> {
            sb.append(s);
        });

        PrintStream ps = logStream.out();
        ps.println("ab");
        ps.println('c');
        ps.println(new char[]{'d', 'e'});
        ps.println(true);
        ps.println(42);
        ps.println(42L);
        ps.println(42.5f);
        ps.println(42.5d);
        ps.println();

        ps.print("fg");
        ps.print('h');
        ps.print(new char[]{'i', 'j'});
        ps.print(false);
        ps.print(1);
        ps.print(2L);
        ps.print(3.5f);
        ps.print(4.5d);

        ps.flush();

        String sep = System.lineSeparator();
        Assert.assertEquals("ab" + sep + "c" + sep + "de" + sep + "true" + sep + "42" + sep + "42" + sep + "42.5" + sep + "42.5" + sep + sep + "fghijfalse123.54.5", sb.toString());
    }
}
