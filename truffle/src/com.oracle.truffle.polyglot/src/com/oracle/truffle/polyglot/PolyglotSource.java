/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.polyglot;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.net.URI;
import java.net.URL;
import java.util.Objects;

import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.AbstractSourceImpl;
import org.graalvm.polyglot.io.ByteSequence;

import com.oracle.truffle.api.source.Source.SourceBuilder;

class PolyglotSource extends AbstractSourceImpl {

    protected PolyglotSource(AbstractPolyglotImpl engineImpl) {
        super(engineImpl);
    }

    @Override
    public String getName(Object impl) {
        com.oracle.truffle.api.source.Source source = (com.oracle.truffle.api.source.Source) impl;
        return source.getName();
    }

    @Override
    public String getPath(Object impl) {
        com.oracle.truffle.api.source.Source source = (com.oracle.truffle.api.source.Source) impl;
        return source.getPath();
    }

    @Override
    public boolean isInteractive(Object impl) {
        com.oracle.truffle.api.source.Source source = (com.oracle.truffle.api.source.Source) impl;
        return source.isInteractive();
    }

    @Override
    public boolean isInternal(Object impl) {
        com.oracle.truffle.api.source.Source source = (com.oracle.truffle.api.source.Source) impl;
        return source.isInternal();
    }

    @Override
    public URL getURL(Object impl) {
        com.oracle.truffle.api.source.Source source = (com.oracle.truffle.api.source.Source) impl;
        return source.getURL();
    }

    @Override
    public URI getURI(Object impl) {
        com.oracle.truffle.api.source.Source source = (com.oracle.truffle.api.source.Source) impl;
        return source.getURI();
    }

    @Override
    public Reader getReader(Object impl) {
        com.oracle.truffle.api.source.Source source = (com.oracle.truffle.api.source.Source) impl;
        return source.getReader();
    }

    @SuppressWarnings("deprecation")
    @Override
    public InputStream getInputStream(Object impl) {
        com.oracle.truffle.api.source.Source source = (com.oracle.truffle.api.source.Source) impl;
        return source.getInputStream();
    }

    @Override
    public int getLength(Object impl) {
        com.oracle.truffle.api.source.Source source = (com.oracle.truffle.api.source.Source) impl;

        return source.getLength();
    }

    @Override
    public CharSequence getCode(Object impl) {
        com.oracle.truffle.api.source.Source source = (com.oracle.truffle.api.source.Source) impl;

        return source.getCharacters();
    }

    @Override
    public CharSequence getCode(Object impl, int lineNumber) {
        com.oracle.truffle.api.source.Source source = (com.oracle.truffle.api.source.Source) impl;

        return source.getCharacters(lineNumber);
    }

    @Override
    public int getLineCount(Object impl) {
        com.oracle.truffle.api.source.Source source = (com.oracle.truffle.api.source.Source) impl;

        return source.getLineCount();
    }

    @Override
    public int getLineNumber(Object impl, int offset) {
        com.oracle.truffle.api.source.Source source = (com.oracle.truffle.api.source.Source) impl;

        return source.getLineNumber(offset);
    }

    @Override
    public int getColumnNumber(Object impl, int offset) {

        com.oracle.truffle.api.source.Source source = (com.oracle.truffle.api.source.Source) impl;

        return source.getColumnNumber(offset);
    }

    @Override
    public int getLineStartOffset(Object impl, int lineNumber) {
        com.oracle.truffle.api.source.Source source = (com.oracle.truffle.api.source.Source) impl;
        return source.getLineStartOffset(lineNumber);
    }

    @Override
    public int getLineLength(Object impl, int lineNumber) {
        com.oracle.truffle.api.source.Source source = (com.oracle.truffle.api.source.Source) impl;

        return source.getLineLength(lineNumber);
    }

    @Override
    public String toString(Object impl) {
        com.oracle.truffle.api.source.Source source = (com.oracle.truffle.api.source.Source) impl;

        return source.toString();
    }

    @Override
    public String getMimeType(Object impl) {
        com.oracle.truffle.api.source.Source source = (com.oracle.truffle.api.source.Source) impl;
        return source.getMimeType();
    }

    @Override
    public String findLanguage(File file) throws IOException {
        Objects.requireNonNull(file);
        String mimeType = findMimeType(file);
        if (mimeType != null) {
            return findLanguage(mimeType);
        } else {
            return null;
        }
    }

    @Override
    public String findLanguage(URL url) throws IOException {
        String mimeType = findMimeType(url);
        if (mimeType != null) {
            return findLanguage(mimeType);
        } else {
            return null;
        }
    }

    @Override
    public String findMimeType(File file) throws IOException {
        Objects.requireNonNull(file);
        return VMAccessor.SOURCE.findMimeType(file);
    }

    @Override
    public String findMimeType(URL url) throws IOException {
        Objects.requireNonNull(url);
        return VMAccessor.SOURCE.findMimeType(url);
    }

    @Override
    public String findLanguage(String mimeType) {
        Objects.requireNonNull(mimeType);
        LanguageCache cache = LanguageCache.languageMimes().get(mimeType);
        if (cache != null) {
            return cache.getId();
        }
        return null;
    }

    @Override
    public int hashCode(Object impl) {
        return impl.hashCode();
    }

    @Override
    public boolean equals(Object impl, Object otherImpl) {
        return impl.equals(otherImpl);
    }

    @Override
    public ByteSequence getBytes(Object impl) {
        com.oracle.truffle.api.source.Source source = (com.oracle.truffle.api.source.Source) impl;
        return source.getBytes();
    }

    @Override
    public boolean hasBytes(Object impl) {
        com.oracle.truffle.api.source.Source source = (com.oracle.truffle.api.source.Source) impl;
        return source.hasBytes();
    }

    @Override
    public boolean hasCharacters(Object impl) {
        com.oracle.truffle.api.source.Source source = (com.oracle.truffle.api.source.Source) impl;
        return source.hasCharacters();
    }

    @Override
    public Source build(String language, Object origin, URI uri, String name, String mimeType, Object content, boolean interactive, boolean internal, boolean cached) throws IOException {
        assert language != null;
        SourceBuilder builder;
        if (origin instanceof File) {
            builder = VMAccessor.SOURCE.newBuilder(language, (File) origin);
        } else if (origin instanceof CharSequence) {
            builder = com.oracle.truffle.api.source.Source.newBuilder(language, ((CharSequence) origin), name);
        } else if (origin instanceof ByteSequence) {
            builder = com.oracle.truffle.api.source.Source.newBuilder(language, ((ByteSequence) origin), name);
        } else if (origin instanceof Reader) {
            builder = com.oracle.truffle.api.source.Source.newBuilder(language, (Reader) origin, name);
        } else if (origin instanceof URL) {
            builder = com.oracle.truffle.api.source.Source.newBuilder(language, (URL) origin);
        } else {
            throw new AssertionError();
        }

        if (content instanceof CharSequence) {
            builder.content((CharSequence) content);
        } else if (content instanceof ByteSequence) {
            builder.content((ByteSequence) content);
        }

        builder.uri(uri);
        builder.name(name);
        builder.internal(internal);
        builder.interactive(interactive);
        builder.mimeType(mimeType);
        builder.cached(cached);

        try {
            com.oracle.truffle.api.source.Source truffleSource = builder.build();
            Source polyglotSource = engineImpl.getAPIAccess().newSource(language, truffleSource);
            VMAccessor.SOURCE.setPolyglotSource(truffleSource, polyglotSource);
            return polyglotSource;
        } catch (IOException e) {
            throw e;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

}
