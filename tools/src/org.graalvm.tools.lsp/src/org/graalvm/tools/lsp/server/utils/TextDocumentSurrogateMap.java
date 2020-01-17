/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.tools.lsp.server.utils;

import java.net.URI;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import com.oracle.truffle.api.instrumentation.TruffleInstrument;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.source.Source;

public final class TextDocumentSurrogateMap {
    private final TruffleInstrument.Env env;
    private final Map<URI, TextDocumentSurrogate> uri2TextDocumentSurrogate = new HashMap<>();

    public TextDocumentSurrogateMap(TruffleInstrument.Env env) {
        this.env = env;
    }

    public TextDocumentSurrogate get(URI uri) {
        return uri2TextDocumentSurrogate.get(uri);
    }

    public void put(URI uri, TextDocumentSurrogate surrogate) {
        uri2TextDocumentSurrogate.put(uri, surrogate);
    }

    public TextDocumentSurrogate getOrCreateSurrogate(URI uri, LanguageInfo languageInfo) {
        return uri2TextDocumentSurrogate.computeIfAbsent(uri,
                        (anUri) -> new TextDocumentSurrogate(env.getTruffleFile(anUri), languageInfo));
    }

    public TextDocumentSurrogate getOrCreateSurrogate(URI uri, Supplier<LanguageInfo> languageInfoSupplier) {
        return uri2TextDocumentSurrogate.computeIfAbsent(uri,
                        (anUri) -> {
                            LanguageInfo languageInfo = languageInfoSupplier.get();
                            return new TextDocumentSurrogate(env.getTruffleFile(anUri), languageInfo);
                        });
    }

    public Collection<TextDocumentSurrogate> getSurrogates() {
        return uri2TextDocumentSurrogate.values();
    }

    public boolean containsSurrogate(URI uri) {
        return uri2TextDocumentSurrogate.containsKey(uri);
    }

    public void remove(URI uri) {
        uri2TextDocumentSurrogate.remove(uri);
    }

    public boolean isSourceNewestInSurrogate(Source source) {
        TextDocumentSurrogate surrogate = get(source.getURI());
        if (surrogate != null) {
            return source.equals(surrogate.getSource());
        }
        return false;
    }

}
