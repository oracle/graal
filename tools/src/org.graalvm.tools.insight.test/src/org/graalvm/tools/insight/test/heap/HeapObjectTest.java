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
import org.junit.Before;
import org.junit.Test;

public class HeapObjectTest {
    private Value heap;

    @Before
    public void prepareHeap() throws Exception {
        Context.Builder b = Context.newBuilder();
        b.option("heap.dump", "x.hprof");
        b.allowIO(true);
        Context ctx = InsightObjectFactory.newContext(b);
        heap = InsightObjectFactory.readObject(ctx, "heap");
        assertFalse("Heap object is defined", heap.isNull());
    }

    private Object invokeDump(Integer depth, Object[] args) {
        return heap.invokeMember("dump", new Config("1.0", depth, args));
    }

    @Test
    public void noEvents() throws Exception {
        invokeDump(null, new Event[0]);
    }

    @Test
    public void noEventsAndDepth() throws Exception {
        invokeDump(10, new Event[0]);
    }

    @Test
    public void noArguments() throws Exception {
        try {
            heap.invokeMember("dump");
            fail("Exception shall be raised");
        } catch (IllegalArgumentException ex) {
            assertMessage(ex, "Instructive error message provided", " Use as dump({ format: '', events: []})");
        }
    }

    @Test
    public void stackMustBeThere() throws Exception {
        try {
            invokeDump(Integer.MAX_VALUE, new Event[]{
                            new UnrelatedEvent()
            });
            fail("Should fail");
        } catch (PolyglotException ex) {
            assertMessage(ex, "Member stack must be present", "'stack' among [a, b, c]");
        }
    }

    @Test
    public void stackNeedsToBeAnArray() throws Exception {
        try {
            invokeDump(1, new Event[]{
                            new StackEvent("any")
            });
            fail("Should fail");
        } catch (PolyglotException ex) {
            assertMessage(ex, "Member stack must be an array", "'stack' shall be an array");
        }
    }

    @Test
    public void atMustBePresent() throws Exception {
        invokeDump(1, new Event[]{
                        new StackEvent(new Object[0])
        });
        try {
            invokeDump(1, new Event[]{
                            new StackEvent(new Object[]{new UnrelatedEvent()})
            });
            fail("Should fail");
        } catch (PolyglotException ex) {
            assertMessage(ex, "Expecting 'at' ", "'at' among [a, b, c]");
        }
    }

    @Test
    public void nonNullAt() throws Exception {
        try {
            invokeDump(1, new Event[]{
                            new StackEvent(new StackElement[]{new StackElement(null, null)})
            });
            fail("Expeting failure");
        } catch (PolyglotException ex) {
            assertMessage(ex, "Expecting non-null 'at' ", "'at' should be defined");
        }
    }

    @Test
    public void everythingIsOK() throws Exception {
        Source nullSource = new Source(null, null, null, null, null);
        invokeDump(1, new Event[]{
                        new StackEvent(new StackElement[]{new StackElement(new At(null, nullSource, 1, 0, 5), new HashMap<>())})
        });

        Source source = new Source("a.text", "application/x-test", "test", "file://a.test", "aaaaa");
        invokeDump(1, new Event[]{
                        new StackEvent(new StackElement[]{new StackElement(new At("a", source, null, null, null), new HashMap<>())})
        });
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

    public static final class Config {
        @HostAccess.Export public final String format;
        @HostAccess.Export public final Integer depth;
        @HostAccess.Export public final Object[] events;

        Config(String format, Integer depth, Object... events) {
            this.format = format;
            this.depth = depth;
            this.events = events;
        }
    }

    public static final class NoDepthConfig {
        @HostAccess.Export public final String format;
        @HostAccess.Export public final Object[] events;

        NoDepthConfig(String format, Object[] events) {
            this.format = format;
            this.events = events;
        }
    }
}
