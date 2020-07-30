/*
 * Copyright (c) 2013, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.source;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.TruffleFile;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.Objects;

import org.graalvm.polyglot.io.ByteSequence;

final class SourceImpl extends Source {

    final Key key;
    private final Object sourceId;

    private SourceImpl(Key key) {
        this.key = key;
        /*
         * SourceImpl instances are interned so a single instance can identify it. We cannot use
         * SourceImpl directly as the sourceId needs to be shared when a source is cloned.
         */
        this.sourceId = new SourceId(key.hashCode());
    }

    private SourceImpl(Key key, Object sourceId) {
        this.key = key;
        this.sourceId = sourceId;
    }

    @Override
    protected Object getSourceId() {
        return sourceId;
    }

    @Override
    public CharSequence getCharacters() {
        if (hasCharacters()) {
            return (CharSequence) key.content;
        } else {
            throw new UnsupportedOperationException();
        }
    }

    @Override
    public ByteSequence getBytes() {
        if (hasBytes()) {
            return (ByteSequence) key.content;
        } else {
            throw new UnsupportedOperationException();
        }
    }

    @Override
    public boolean hasBytes() {
        return key.content instanceof ByteSequence;
    }

    @Override
    public boolean hasCharacters() {
        return key.content instanceof CharSequence;
    }

    @Override
    Source copy() {
        return new SourceImpl(key, sourceId);
    }

    @Override
    public boolean isCached() {
        return key.cached;
    }

    @Override
    public String getName() {
        return key.name;
    }

    @Override
    public String getPath() {
        return key.getPath();
    }

    @Override
    public boolean isInternal() {
        return key.internal;
    }

    @Override
    public boolean isInteractive() {
        return key.interactive;
    }

    @Override
    public URL getURL() {
        return key.getURL();
    }

    @Override
    public URI getOriginalURI() {
        return key.getURI();
    }

    @Override
    public String getMimeType() {
        return key.mimeType;
    }

    @Override
    public String getLanguage() {
        return key.language;
    }

    Key toKey() {
        return key;
    }

    private static final class SourceId {

        /*
         * We store the hash of the key to have stable source hashCode for each run.
         */
        final int hash;

        SourceId(int hash) {
            this.hash = hash;
        }

        @Override
        public boolean equals(Object obj) {
            return this == obj;
        }

        @Override
        public int hashCode() {
            return hash;
        }

    }

    abstract static class Key {

        final Object content;
        final String name;
        final String mimeType;
        final String language;
        final boolean internal;
        final boolean interactive;
        final boolean cached;
        // TODO remove legacy field with deprecated Source builders.
        volatile Integer cachedHashCode;

        Key(Object content, String mimeType, String languageId, String name, boolean internal, boolean interactive, boolean cached) {
            this.content = content;
            this.mimeType = mimeType;
            this.language = languageId;
            this.name = name;
            this.internal = internal;
            this.interactive = interactive;
            this.cached = cached;
        }

        abstract String getPath();

        abstract URI getURI();

        abstract URL getURL();

        @Override
        public int hashCode() {
            Integer hashCode = cachedHashCode;
            if (hashCode == null) {
                hashCode = hashCodeImpl(content, mimeType, language, getURL(), getURI(), name, getPath(), internal, interactive, cached);
                cachedHashCode = hashCode;
            }
            return hashCode;
        }

        static int hashCodeImpl(Object content, String mimeType, String language, URL url, URI uri, String name, String path, boolean internal, boolean interactive,
                        boolean cached) {
            int result = 31 * 1 + ((content == null) ? 0 : content.hashCode());
            result = 31 * result + (interactive ? 1231 : 1237);
            result = 31 * result + (internal ? 1231 : 1237);
            result = 31 * result + (cached ? 1231 : 1237);
            result = 31 * result + ((language == null) ? 0 : language.hashCode());
            result = 31 * result + ((mimeType == null) ? 0 : mimeType.hashCode());
            result = 31 * result + ((name == null) ? 0 : name.hashCode());
            result = 31 * result + ((path == null) ? 0 : path.hashCode());
            result = 31 * result + ((uri == null) ? 0 : uri.hashCode());
            result = 31 * result + ((url == null) ? 0 : url.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            } else if (!(obj instanceof Key)) {
                return false;
            }
            Key other = (Key) obj;
            /*
             * Compare characters last as it is likely the most expensive comparison in the worst
             * case.
             */
            return Objects.equals(language, other.language) && //
                            Objects.equals(mimeType, other.mimeType) && //
                            Objects.equals(name, other.name) && //
                            Objects.equals(getPath(), other.getPath()) && //
                            Objects.equals(getURI(), other.getURI()) && //
                            Objects.equals(getURL(), other.getURL()) && //
                            interactive == other.interactive && //
                            internal == other.internal &&
                            cached == other.cached &&
                            compareContent(other);
        }

        void invalidateAfterPreinitialiation() {
        }

        private boolean compareContent(Key other) {
            Object otherContent = other.content;
            if (content == other.content) {
                return true;
            } else if (content instanceof CharSequence && otherContent instanceof CharSequence) {
                return compareCharacters((CharSequence) content, (CharSequence) otherContent);
            } else if (content instanceof ByteSequence && otherContent instanceof ByteSequence) {
                return compareBytes((ByteSequence) content, (ByteSequence) otherContent);
            } else {
                return false;
            }
        }

        private static boolean compareBytes(ByteSequence bytes, ByteSequence other) {
            if (bytes == null || bytes.length() != other.length()) {
                return false;
            } else {
                // trusted class
                return bytes.equals(other);
            }
        }

        private static boolean compareCharacters(CharSequence characters, CharSequence other) {
            if (characters == null || characters.length() != other.length()) {
                return false;
            } else {
                return Objects.equals(characters.toString(), other.toString());
            }
        }

        SourceImpl toSourceInterned() {
            assert cached;
            return new SourceImpl(this);
        }

        SourceImpl toSourceNotInterned() {
            assert !cached;
            return new SourceImpl(this, this);
        }

    }

    static final class ImmutableKey extends Key {

        private final URI uri;
        private final URL url;
        private final String path;

        /**
         * Creates an {@link ImmutableKey}. The {@code relativePathInLanguageHome} has to be given
         * for a file under the language home. For the file under the language home the hash code
         * must be equal to {@link ReinitializableKey}'s hash code, so it's based on the relative
         * path in the language home and does not include {@code url} nor {@code uri} as they
         * contain absolute paths.
         */
        ImmutableKey(Object content, String mimeType, String languageId, URL url, URI uri, String name, String path, boolean internal, boolean interactive, boolean cached,
                        String relativePathInLanguageHome) {
            super(content, mimeType, languageId, name, internal, interactive, cached);
            this.uri = uri;
            this.url = url;
            this.path = path;
            if (relativePathInLanguageHome != null) {
                this.cachedHashCode = hashCodeImpl(content, mimeType, language, null, null, name, relativePathInLanguageHome, internal, interactive, cached);
            }
        }

        @Override
        String getPath() {
            return path;
        }

        @Override
        URI getURI() {
            return uri;
        }

        @Override
        URL getURL() {
            return url;
        }
    }

    /**
     * A {@link Key} used for files under the language homes in the time of context
     * pre-initialization. The {@code uri}, {@code url} and {@code path} of the
     * {@link ReinitializableKey} are reset at the end of the context pre-initialization and
     * recomputed from the given {@link TruffleFile} in image execution time.
     */
    static final class ReinitializableKey extends Key {

        private static final Object INVALID = new Object();

        private TruffleFile truffleFile;
        private Object uri;
        private Object url;
        private Object path;

        /**
         * Creates an {@link ReinitializableKey} for a file under the language home. The hash code
         * is based on the relative path in language home and does not include {@code url} nor
         * {@code uri} as they contain absolute paths.
         */
        ReinitializableKey(TruffleFile truffleFile, Object content, String mimeType, String languageId, URL url, URI uri, String name, String path, boolean internal, boolean interactive,
                        boolean cached, String relativePathInLanguageHome) {
            super(content, mimeType, languageId, name, internal, interactive, cached);
            Objects.requireNonNull(truffleFile, "TruffleFile must be non null.");
            this.truffleFile = truffleFile;
            this.uri = uri;
            this.url = url;
            this.path = path;
            this.cachedHashCode = hashCodeImpl(content, mimeType, language, null, null, name, relativePathInLanguageHome, internal, interactive, cached);
        }

        @Override
        void invalidateAfterPreinitialiation() {
            if (Objects.equals(path, truffleFile.getPath())) {
                path = INVALID;
            }
            if (Objects.equals(uri, truffleFile.toUri())) {
                this.uri = INVALID;
            }
            try {
                if (url != null && truffleFile.toUri().toURL().toExternalForm().equals(((URL) url).toExternalForm())) {
                    this.url = INVALID;
                }
            } catch (MalformedURLException mue) {
                // Should never be thrown as the truffleFile.toUri() returns absolute URI
                throw new AssertionError(mue);
            }
        }

        @Override
        @CompilerDirectives.TruffleBoundary
        String getPath() {
            if (path == INVALID) {
                path = SourceAccessor.getReinitializedPath(truffleFile);
            }
            return (String) path;
        }

        @Override
        @CompilerDirectives.TruffleBoundary
        URI getURI() {
            if (uri == INVALID) {
                uri = SourceAccessor.getReinitializedURI(truffleFile);
            }
            return (URI) uri;
        }

        @Override
        @CompilerDirectives.TruffleBoundary
        URL getURL() {
            if (url == INVALID) {
                try {
                    URI localUri = getURI();
                    url = new URL(localUri.getScheme(), localUri.getHost(), localUri.getPort(), localUri.getRawPath());
                } catch (MalformedURLException e) {
                    // Never thrown
                    throw new AssertionError(e);
                }
            }
            return (URL) url;
        }
    }

}
