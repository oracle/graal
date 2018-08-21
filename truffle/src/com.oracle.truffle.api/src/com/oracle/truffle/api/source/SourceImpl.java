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

import java.net.URI;
import java.net.URL;
import java.util.Objects;

import org.graalvm.polyglot.io.ByteSequence;

final class SourceImpl extends Source {

    private final Key key;
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
        return key.path;
    }

    @Override
    public boolean isInternal() {
        return key.internal;
    }

    @Override
    boolean isLegacy() {
        return key.legacy;
    }

    @Override
    public boolean isInteractive() {
        return key.interactive;
    }

    @Override
    public URL getURL() {
        return key.url;
    }

    @Override
    public URI getOriginalURI() {
        return key.uri;
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

    static final class Key {

        final Object content;
        final URI uri;
        final URL url;
        final String name;
        final String mimeType;
        final String language;
        final String path;
        final boolean internal;
        final boolean interactive;
        final boolean cached;
        // TODO remove legacy field with deprecated Source builders.
        final boolean legacy;

        Key(Object content, String mimeType, String languageId, URL url, URI uri, String name, String path, boolean internal, boolean interactive, boolean cached, boolean legacy) {
            this.content = content;
            this.mimeType = mimeType;
            this.language = languageId;
            this.name = name;
            this.path = path;
            this.internal = internal;
            this.interactive = interactive;
            this.cached = cached;
            this.url = url;
            this.uri = uri;
            this.legacy = legacy;
        }

        @Override
        public int hashCode() {
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
            assert content != null;
            Key other = (Key) obj;
            /*
             * Compare characters last as it is likely the most expensive comparison in the worst
             * case.
             */
            return Objects.equals(language, other.language) && //
                            Objects.equals(mimeType, other.mimeType) && //
                            Objects.equals(name, other.name) && //
                            Objects.equals(path, other.path) && //
                            Objects.equals(uri, other.uri) && //
                            Objects.equals(url, other.url) && //
                            interactive == other.interactive && //
                            internal == other.internal &&
                            cached == other.cached &&
                            compareContent(other);
        }

        private boolean compareContent(Key other) {
            Object otherContent = other.content;
            if (content instanceof CharSequence && otherContent instanceof CharSequence) {
                return compareCharacters((CharSequence) content, (CharSequence) otherContent);
            } else if (content instanceof ByteSequence && otherContent instanceof ByteSequence) {
                return compareBytes((ByteSequence) content, (ByteSequence) otherContent);
            } else {
                return false;
            }
        }

        private static boolean compareBytes(ByteSequence bytes, ByteSequence other) {
            if (bytes == other) {
                return true;
            } else if (bytes == null) {
                return false;
            } else if (bytes.length() != other.length()) {
                return false;
            } else {
                // trusted class
                return bytes.equals(other);
            }
        }

        private static boolean compareCharacters(CharSequence characters, CharSequence other) {
            if (characters == other) {
                return true;
            } else if (characters == null) {
                return false;
            } else if (characters.length() != other.length()) {
                return false;
            } else {
                assert other != null;
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

}
