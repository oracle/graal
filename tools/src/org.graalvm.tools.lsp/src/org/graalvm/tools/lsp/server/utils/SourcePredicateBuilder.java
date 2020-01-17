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
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import org.graalvm.options.OptionValues;
import org.graalvm.tools.lsp.instrument.LSPInstrument;

import com.oracle.truffle.api.instrumentation.SourceSectionFilter.SourcePredicate;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.source.Source;

public final class SourcePredicateBuilder {

    private final List<Predicate<Source>> predicates = new ArrayList<>();

    private SourcePredicateBuilder() {
    }

    /**
     * A special method to filter for either the URI of a {@link Source} or via the name. Some
     * languages create sources in a way that no URI is set, only the {@link Source}'s name which is
     * the path of the source.
     *
     * @param uri
     */
    public SourcePredicateBuilder uriOrTruffleName(URI uri) {
        SourcePredicate predicate = src -> src.getURI().equals(uri) || (src.getURI().getScheme().equals("truffle") && src.getName().equals(uri.getPath()));
        this.predicates.add(predicate);
        return this;
    }

    public SourcePredicateBuilder language(LanguageInfo languageInfo) {
        SourcePredicate predicate = src -> languageInfo.getId().equals(src.getLanguage()) ||
                        (src.getMimeType() != null && languageInfo.getMimeTypes().contains(src.getMimeType()));
        this.predicates.add(predicate);
        return this;
    }

    public SourcePredicateBuilder excludeInternal(OptionValues options) {
        boolean includeInternal = options.get(LSPInstrument.Internal);
        if (!includeInternal) {
            SourcePredicate predicate = src -> !src.isInternal();
            this.predicates.add(predicate);
        }
        return this;
    }

    public SourcePredicateBuilder newestSource(TextDocumentSurrogateMap surrogateMap) {
        this.predicates.add(src -> surrogateMap.isSourceNewestInSurrogate(src));
        return this;
    }

    public SourcePredicate build() {
        Predicate<Source> predicate = predicates.stream().reduce((predicateA, predicateB) -> predicateA.and(predicateB)).get();
        return src -> predicate.test(src);
    }

    public static SourcePredicateBuilder newBuilder() {
        return new SourcePredicateBuilder();
    }
}
