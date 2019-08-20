/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.regex.tregex.util.DebugUtil;
import com.oracle.truffle.regex.tregex.util.json.Json;
import com.oracle.truffle.regex.tregex.util.json.JsonConvertible;
import com.oracle.truffle.regex.tregex.util.json.JsonValue;

public final class RegexSource implements JsonConvertible {

    private final String pattern;
    private final String flags;
    private Source source;
    private boolean hashComputed = false;
    private int cachedHash;

    public RegexSource(String pattern, String flags) {
        this.pattern = pattern;
        this.flags = flags;
    }

    public RegexSource(String pattern) {
        this(pattern, "");
    }

    public String getPattern() {
        return pattern;
    }

    public String getFlags() {
        return flags;
    }

    public Source getSource() {
        if (source == null) {
            String text = toString();
            source = Source.newBuilder(RegexLanguage.ID, text, text).internal(true).name(text).mimeType(RegexLanguage.MIME_TYPE).build();
        }
        return source;
    }

    @Override
    public int hashCode() {
        if (!hashComputed) {
            final int prime = 31;
            cachedHash = 1;
            cachedHash = prime * cachedHash + pattern.hashCode();
            cachedHash = prime * cachedHash + flags.hashCode();
            hashComputed = true;
        }
        return cachedHash;
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj || obj instanceof RegexSource &&
                        pattern.equals(((RegexSource) obj).pattern) &&
                        flags.equals(((RegexSource) obj).flags);
    }

    @Override
    public String toString() {
        return "/" + pattern + "/" + flags;
    }

    public String toFileName() {
        StringBuilder sb = new StringBuilder(20);
        int i = 0;
        while (i < Math.min(pattern.length(), 20)) {
            int c = pattern.codePointAt(i);
            if (DebugUtil.isValidCharForFileName(c)) {
                sb.appendCodePoint(c);
            } else {
                sb.append('_');
            }
            if (c > 0xffff) {
                i += 2;
            } else {
                i++;
            }
        }
        if (!flags.isEmpty()) {
            sb.append('_').append(flags);
        }
        return sb.toString();
    }

    @TruffleBoundary
    @Override
    public JsonValue toJson() {
        return Json.obj(Json.prop("pattern", pattern),
                        Json.prop("flags", flags));
    }
}
