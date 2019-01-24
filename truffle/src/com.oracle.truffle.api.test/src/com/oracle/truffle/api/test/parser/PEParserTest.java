/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.api.test.parser;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.test.parser.PEParser.BasicNode;

public class PEParserTest {

    static com.sun.management.ThreadMXBean threadMXBean;

    @TruffleBoundary
    static long getAllocatedBytes() {
        if (threadMXBean == null) {
            threadMXBean = (com.sun.management.ThreadMXBean) java.lang.management.ManagementFactory.getThreadMXBean();
        }
        return threadMXBean.getThreadAllocatedBytes(Thread.currentThread().getId());
    }

    public static final String test = "01 INPUT \"abcdefghijklmnopq\", A\n" +
                    "02 B = C*1+2*3\n" +
                    "03 IF D=E GOTO 01\n" +
                    "04 IF F>G PRINT \"abcdef\"\n" +
                    "05 IF H<I PRINT \"abcdef\"\n" +
                    "06 GOTO 02\n" +
                    "07 PRINT \"abcdefghijklmnopq\", J, \"bcdefg\"\n";

    @Test
    public void test() {
        PELexer lexer = PELexer.create(test);
        Assert.assertEquals(53, lexer.getTokenCount());
        PEParser parser = PEParser.create();
        Object result = parser.parse(lexer);
        Assert.assertNotNull(result);
    }

    @Test
    @Ignore
    public void longTest() {
        // create 10MB input
        StringBuilder str = new StringBuilder();
        while (str.length() < 10000000) {
            str.append(test);
        }
        String longTest = str.toString();

        System.out.println(PELexer.create(test).toString());

        for (int i = 0; i < 50; i++) {
            long t1 = System.nanoTime();
            long m1 = getAllocatedBytes();
            PELexer.create(longTest);
            long t2 = System.nanoTime();
            long m2 = getAllocatedBytes();
            double bps = longTest.length() / ((t2 - t1) / 1000000000.0);
            System.out.println("lexer: " + ((t2 - t1) / 1000000) + "  " + bps / 1000000 + " MB/s, allocated " + (m2 - m1) / 1024 / 1024 + "MB");
        }

        PELexer lexer = PELexer.create(test);
        PEParser parser = PEParser.create();
        BasicNode node = (BasicNode) parser.parse(lexer);
        node.print(0);

        long min = Long.MAX_VALUE;
        lexer = PELexer.create(longTest);
        for (int i = 0; i < 50; i++) {
            long t1 = System.nanoTime();
            long m1 = getAllocatedBytes();
            lexer.reset();
            parser.parse(lexer);
            long t2 = System.nanoTime();
            long m2 = getAllocatedBytes();
            long diff = t2 - t1;
            double bps = longTest.length() / (diff / 1000000000.0);
            System.out.println("parser: " + (diff / 1000000) + "  " + bps / 1000000 + " MB/s, allocated " + (m2 - m1) / 1024 / 1024 + "MB");
            if (diff < min) {
                min = diff;
                System.out.println("minimum: " + diff / 1000000);
            }
        }

        for (int i = 0; i < 50; i++) {
            long t1 = System.nanoTime();
            long m1 = getAllocatedBytes();
            lexer = PELexer.create(longTest);
            parser.parse(lexer);
            long t2 = System.nanoTime();
            long m2 = getAllocatedBytes();
            double bps = longTest.length() / ((t2 - t1) / 1000000000.0);
            System.out.println("lexer+parser: " + ((t2 - t1) / 1000000) + "  " + bps / 1000000 + " MB/s, allocated " + (m2 - m1) / 1024 / 1024 + "MB");
        }
    }
}
