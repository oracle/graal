/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.polyglot;

import static com.oracle.truffle.api.CompilerDirectives.shouldNotReachHere;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.net.URI;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.AbstractSourceImpl;
import org.graalvm.polyglot.io.ByteSequence;
import org.graalvm.polyglot.io.FileSystem;

import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.source.Source.SourceBuilder;

class PolyglotSource extends AbstractSourceImpl {

    private volatile Object defaultFileSystemContext;

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
        return new ByteArrayInputStream(getCharacters(impl).toString().getBytes());
    }

    @Override
    public int getLength(Object impl) {
        com.oracle.truffle.api.source.Source source = (com.oracle.truffle.api.source.Source) impl;

        return source.getLength();
    }

    @Override
    public CharSequence getCharacters(Object impl) {
        com.oracle.truffle.api.source.Source source = (com.oracle.truffle.api.source.Source) impl;

        return source.getCharacters();
    }

    @Override
    public CharSequence getCharacters(Object impl, int lineNumber) {
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
        TruffleFile truffleFile = EngineAccessor.LANGUAGE.getTruffleFile(file.toPath().toString(), getDefaultFileSystemContext());
        return truffleFile.detectMimeType();
    }

    @Override
    public String findMimeType(URL url) throws IOException {
        Objects.requireNonNull(url);
        return EngineAccessor.SOURCE.findMimeType(url, getDefaultFileSystemContext());
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
    public Source build(String language, Object origin, URI uri, String name, String mimeType, Object content, boolean interactive, boolean internal, boolean cached, Charset encoding)
                    throws IOException {
        assert language != null;
        SourceBuilder builder;
        if (origin instanceof File) {
            builder = EngineAccessor.SOURCE.newBuilder(language, (File) origin);
        } else if (origin instanceof CharSequence) {
            builder = com.oracle.truffle.api.source.Source.newBuilder(language, ((CharSequence) origin), name);
        } else if (origin instanceof ByteSequence) {
            builder = com.oracle.truffle.api.source.Source.newBuilder(language, ((ByteSequence) origin), name);
        } else if (origin instanceof Reader) {
            builder = com.oracle.truffle.api.source.Source.newBuilder(language, (Reader) origin, name);
        } else if (origin instanceof URL) {
            builder = com.oracle.truffle.api.source.Source.newBuilder(language, (URL) origin);
        } else {
            throw shouldNotReachHere();
        }

        if (origin instanceof File || origin instanceof URL) {
            EngineAccessor.SOURCE.setFileSystemContext(builder, getDefaultFileSystemContext());
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
        builder.encoding(encoding);

        try {
            com.oracle.truffle.api.source.Source truffleSource = builder.build();
            Source polyglotSource = engineImpl.getAPIAccess().newSource(language, truffleSource);
            EngineAccessor.SOURCE.setPolyglotSource(truffleSource, polyglotSource);
            return polyglotSource;
        } catch (IOException e) {
            throw e;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw shouldNotReachHere(e);
        }
    }

    private Object getDefaultFileSystemContext() {
        Object res = defaultFileSystemContext;
        if (res == null) {
            synchronized (this) {
                res = defaultFileSystemContext;
                if (res == null) {
                    EmbedderFileSystemContext context = new EmbedderFileSystemContext();
                    res = EngineAccessor.LANGUAGE.createFileSystemContext(context, context.fileSystem);
                    defaultFileSystemContext = res;
                }
            }
        }
        return res;
    }

    static final class EmbedderFileSystemContext {

        final FileSystem fileSystem = FileSystems.newDefaultFileSystem();
        final Map<String, LanguageCache> cachedLanguages = LanguageCache.languages();
        final Supplier<Map<String, Collection<? extends TruffleFile.FileTypeDetector>>> fileTypeDetectors = FileSystems.newFileTypeDetectorsSupplier(cachedLanguages.values());

    }

}
