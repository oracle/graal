/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.tools.insight.test.heap;

import java.util.HashMap;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.graalvm.tools.insight.test.InsightObjectFactory;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;
import org.junit.Test;

public class HeapDumpTest {
    @Test
    public void heapDumpOption() throws Exception {
        Context.Builder b = Context.newBuilder();
        b.option("heap.dump", "x.hprof");
        b.allowIO(true);
        Context ctx = InsightObjectFactory.newContext(b);
        Value heap = InsightObjectFactory.readObject(ctx, "heap");
        assertFalse("Heap object is defined", heap.isNull());
        heap.invokeMember("record", (Object) new Event[0]);
        heap.invokeMember("record", new Event[0], 10);
        try {
            heap.invokeMember("record");
            fail("Exception shall be raised");
        } catch (IllegalArgumentException ex) {
            assertMessage(ex, "Instructive error message provided", " Use as record(obj: [], depth?: number)");
        }
        try {
            heap.invokeMember("record", new Event[0], null);
            fail("Exception shall be raised");
        } catch (IllegalArgumentException ex) {
            assertMessage(ex, "Second argument must be a 32-bit number", " Use as record(obj: [], depth?: number)");
        }
        try {
            heap.invokeMember("record", new Event[]{
                            new UnrelatedEvent()
            }, Integer.MAX_VALUE);
            fail("Should fail");
        } catch (PolyglotException ex) {
            assertMessage(ex, "Member stack must be present", "'stack' among [a, b, c]");
        }
        try {
            heap.invokeMember("record", new Event[]{
                            new StackEvent("any")
            }, 1);
            fail("Should fail");
        } catch (PolyglotException ex) {
            assertMessage(ex, "Member stack must be an array", "'stack' shall be an array");
        }
        heap.invokeMember("record", new Event[]{
                        new StackEvent(new Object[0])
        }, 1);
        try {
            heap.invokeMember("record", new Event[]{
                            new StackEvent(new Object[]{new UnrelatedEvent()})
            }, 1);
            fail("Should fail");
        } catch (PolyglotException ex) {
            assertMessage(ex, "Expecting 'at' ", "'at' among [a, b, c]");
        }
        try {
            heap.invokeMember("record", new Event[]{
                            new StackEvent(new StackElement[]{new StackElement(null, null)})
            }, 1);
            fail("Expeting failure");
        } catch (PolyglotException ex) {
            assertMessage(ex, "Expecting non-null 'at' ", "'at' should be defined");
        }
        Source nullSource = new Source(null, null, null, null, null);
        heap.invokeMember("record", new Event[]{
                        new StackEvent(new StackElement[]{new StackElement(new At(null, nullSource, 1, 0, 5), new HashMap<Object, Object>())})
        }, 1);

        Source source = new Source("a.text", "application/x-test", "test", "file://a.test", "aaaaa");
        heap.invokeMember("record", new Event[]{
                        new StackEvent(new StackElement[]{new StackElement(new At("a", source, null, null, null), new HashMap<Object, Object>())})
        }, 1);
    }

    private static void assertMessage(Throwable ex, String msg, String exp) {
        int found = ex.getMessage().indexOf(exp);
        if (found == -1) {
            fail(msg +
                            "\nexpecting:" + exp +
                            "\nwas      : " + ex.getMessage());
        }
    }

    public abstract static class Event {
    }

    public static final class UnrelatedEvent extends Event {
        @HostAccess.Export public final int a = 3;
        @HostAccess.Export public final int b = 4;
        @HostAccess.Export public final int c = 5;
    }

    public static final class StackEvent extends Event {
        @HostAccess.Export public final Object stack;

        StackEvent(Object stack) {
            this.stack = stack;
        }
    }

    public static final class StackElement extends Event {
        @HostAccess.Export public final Object at;
        @HostAccess.Export public final Object frame;

        StackElement(Object at, Object frame) {
            this.at = at;
            this.frame = frame;
        }
    }

    public static final class Source {
        @HostAccess.Export public final String name;
        @HostAccess.Export public final String mimeType;
        @HostAccess.Export public final String language;
        @HostAccess.Export public final String uri;
        @HostAccess.Export public final String characters;

        Source(String name, String mimeType, String language, String uri, String characters) {
            this.name = name;
            this.mimeType = mimeType;
            this.language = language;
            this.uri = uri;
            this.characters = characters;
        }
    }

    public static final class At {
        @HostAccess.Export public final String name;
        @HostAccess.Export public final Source source;
        @HostAccess.Export public final Integer line;
        @HostAccess.Export public final Integer charIndex;
        @HostAccess.Export public final Integer charLength;

        At(String name, Source source, Integer line, Integer charIndex, Integer charLength) {
            this.name = name;
            this.source = source;
            this.line = line;
            this.charIndex = charIndex;
            this.charLength = charLength;
        }
    }
}
