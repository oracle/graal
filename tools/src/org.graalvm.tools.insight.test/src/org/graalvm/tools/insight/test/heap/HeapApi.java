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

import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

public final class HeapApi {
    private HeapApi() {
    }

    public static Object invokeDump(Value heap, Integer depth, Object[] args) {
        return heap.invokeMember("dump", new Config("1.0", depth, args));
    }

    public static Object invokeFlush(Value heap) {
        return heap.invokeMember("flush");
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

        public StackEvent(Object stack) {
            this.stack = stack;
        }
    }

    public static final class StackElement extends Event {
        @HostAccess.Export public final Object at;
        @HostAccess.Export public final Object frame;

        public StackElement(Object at, Object frame) {
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

        public Source(String name, String mimeType, String language, String uri, String characters) {
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

        public At(String name, Source source, Integer line, Integer charIndex, Integer charLength) {
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
