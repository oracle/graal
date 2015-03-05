/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.source;

/**
 * A general set of "properties" or "categories" that might be usefully attached to a particular
 * source of code, both for use by the language runtime and by external tools. This set of tags
 * includes some intended to be applied by default by {@link Source} factory methods or other
 * services built into the Truffle platform.
 * <p>
 * The need for additional tags is likely to arise, in some cases because of issue specific to a
 * Guest Language, but also for help configuring the behavior of particular tools.
 *
 * @see Source
 */
public enum StandardSourceTag implements SourceTag {

    /**
     * Builtin.
     */
    BUILTIN("builtin", "implementation of language builtins"),

    /**
     * From bytes.
     */
    FROM_BYTES("bytes", "read from bytes"),

    /**
     * Read from a file.
     */
    FROM_FILE("file", "read from a file"),

    /**
     * From literal text.
     */
    FROM_LITERAL("literal", "from literal text"),

    /**
     * From a {@linkplain java.io.Reader Reader}.
     */
    FROM_READER("reader", "read from a Java Reader"),

    /**
     * Read from a URL.
     */
    FROM_URL("URL", "read from a URL"),

    /**
     * Treat as LIBRARY code.
     */
    LIBRARY("library", "library code");

    private final String name;
    private final String description;

    private StandardSourceTag(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

}
