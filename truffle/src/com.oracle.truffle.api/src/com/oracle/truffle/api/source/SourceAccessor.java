/*
 * Copyright (c) 2013, 2018, Oracle and/or its affiliates. All rights reserved.
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

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Collection;

import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.impl.Accessor;
import com.oracle.truffle.api.source.Source.SourceBuilder;

final class SourceAccessor extends Accessor {

    static final SourceAccessor ACCESSOR = new SourceAccessor();

    protected SourceAccessor() {
    }

    @Override
    protected SourceSupport sourceSupport() {
        return new SourceSupportImpl();
    }

    @Override
    protected LanguageSupport languageSupport() {
        return super.languageSupport();
    }

    @Override
    protected EngineSupport engineSupport() {
        return super.engineSupport();
    }

    static Collection<ClassLoader> allLoaders() {
        return ACCESSOR.loaders();
    }

    static byte[] readTruffleFile(File file) throws IOException {
        return ACCESSOR.languageSupport().truffleFileContent(file);
    }

    static boolean isTruffleFile(File file) {
        return ACCESSOR.languageSupport().checkTruffleFile(file);
    }

    static File asFile(TruffleFile file) {
        return ACCESSOR.languageSupport().asFile(file);
    }

    static final class SourceSupportImpl extends Accessor.SourceSupport {

        @Override
        public Source copySource(Source source) {
            return source.copy();
        }

        @Override
        public Object getSourceIdentifier(Source source) {
            return source.getSourceId();
        }

        @Override
        public org.graalvm.polyglot.Source getPolyglotSource(Source source) {
            return source.polyglotSource;
        }

        @Override
        public void setPolyglotSource(Source source, org.graalvm.polyglot.Source polyglotSource) {
            source.polyglotSource = polyglotSource;
        }

        @Override
        public String findMimeType(File file) throws IOException {
            return Source.findMimeType(file.toPath(), null);
        }

        @Override
        public String findMimeType(URL url) throws IOException {
            return Source.findMimeType(url);
        }

        @Override
        public SourceBuilder newBuilder(String language, File origin) {
            return Source.newBuilder(language, origin);
        }

        @Override
        public boolean isLegacySource(Source source) {
            return source.isLegacy();
        }

    }
}
