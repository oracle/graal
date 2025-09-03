/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.regex.tregex.string.Encodings.Encoding;
import com.oracle.truffle.regex.tregex.util.DebugUtil;
import com.oracle.truffle.regex.tregex.util.json.Json;
import com.oracle.truffle.regex.tregex.util.json.JsonConvertible;
import com.oracle.truffle.regex.tregex.util.json.JsonValue;

public final class RegexSource implements JsonConvertible {

    private final String pattern;
    private final String flags;
    private final RegexOptions options;
    private final Source source;
    private boolean hashComputed = false;
    private int cachedHash;

    public RegexSource(String pattern, String flags, RegexOptions options, Source source) {
        this.pattern = pattern;
        this.flags = flags;
        this.options = options;
        this.source = source;
    }

    public String getPattern() {
        return pattern;
    }

    public String getFlags() {
        return flags;
    }

    public RegexOptions getOptions() {
        return options;
    }

    public Encoding getEncoding() {
        return options.getEncoding();
    }

    public Source getSource() {
        return source;
    }

    public RegexSource withBooleanMatch() {
        return new RegexSource(pattern, flags, options.withBooleanMatch(), source);
    }

    public RegexSource withoutBooleanMatch() {
        return new RegexSource(pattern, flags, options.withoutBooleanMatch(), source);
    }

    @Override
    public int hashCode() {
        if (!hashComputed) {
            final int prime = 31;
            int hash = 1;
            hash = prime * hash + pattern.hashCode();
            hash = prime * hash + flags.hashCode();
            hash = prime * hash + options.hashCode();
            cachedHash = hash;
            hashComputed = true;
        }
        return cachedHash;
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj || obj instanceof RegexSource &&
                        pattern.equals(((RegexSource) obj).pattern) &&
                        flags.equals(((RegexSource) obj).flags) &&
                        options.equals(((RegexSource) obj).options);
    }

    @TruffleBoundary
    @Override
    public String toString() {
        return "/" + pattern + "/" + flags;
    }

    @TruffleBoundary
    public String toStringEscaped() {
        return DebugUtil.regexSourceEscape(pattern, flags);
    }

    @TruffleBoundary
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
