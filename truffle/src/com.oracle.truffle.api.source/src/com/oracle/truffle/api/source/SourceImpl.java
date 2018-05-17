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

final class SourceImpl extends Source {

    private final Key key;

    private SourceImpl(Key key) {
        this.key = key;
    }

    @Override
    public CharSequence getCharacters() {
        return key.characters;
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

    static final class Key {

        final CharSequence characters;
        final URI uri;
        final URL url;
        final String name;
        final String mimeType;
        final String language;
        final String path;
        final boolean internal;
        final boolean interactive;

        Key(CharSequence characters, String mimeType, String languageId, URL url, URI uri, String name, String path, boolean internal, boolean interactive) {
            this.characters = characters;
            this.mimeType = mimeType;
            this.language = languageId;
            this.name = name;
            this.path = path;
            this.internal = internal;
            this.interactive = interactive;
            this.url = url;
            this.uri = uri;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((characters == null) ? 0 : characters.hashCode());
            result = prime * result + (interactive ? 1231 : 1237);
            result = prime * result + (internal ? 1231 : 1237);
            result = prime * result + ((language == null) ? 0 : language.hashCode());
            result = prime * result + ((mimeType == null) ? 0 : mimeType.hashCode());
            result = prime * result + ((name == null) ? 0 : name.hashCode());
            result = prime * result + ((path == null) ? 0 : path.hashCode());
            result = prime * result + ((uri == null) ? 0 : uri.hashCode());
            result = prime * result + ((url == null) ? 0 : url.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            } else if (getClass() != obj.getClass()) {
                return false;
            }
            assert characters != null;
            Key other = (Key) obj;
            return compareCharacters(other) &&
                            Objects.equals(language, other.language) && //
                            Objects.equals(mimeType, other.mimeType) && //
                            Objects.equals(name, other.name) && //
                            Objects.equals(path, other.path) && //
                            Objects.equals(uri, other.uri) && //
                            Objects.equals(url, other.url) && //
                            interactive == other.interactive && //
                            internal == other.internal;
        }

        private boolean compareCharacters(Key other) {
            if (characters == other.characters) {
                return true;
            } else if (characters == null) {
                return false;
            } else {
                assert other.characters != null;
                return Objects.equals(characters.toString(), other.characters.toString());
            }
        }

        SourceImpl toSource() {
            return new SourceImpl(this);
        }
    }

}
