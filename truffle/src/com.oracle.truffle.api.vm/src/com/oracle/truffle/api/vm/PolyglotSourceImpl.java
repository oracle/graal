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
package com.oracle.truffle.api.vm;

import java.io.File;
import java.io.InputStream;
import java.io.Reader;
import java.net.URI;
import java.net.URL;

import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.AbstractSourceImpl;

class PolyglotSourceImpl extends AbstractSourceImpl {

    protected PolyglotSourceImpl(AbstractPolyglotImpl engineImpl) {
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
    public String getCode(Object impl) {
        com.oracle.truffle.api.source.Source source = (com.oracle.truffle.api.source.Source) impl;

        return source.getCode();
    }

    @Override
    public String getCode(Object impl, int lineNumber) {
        com.oracle.truffle.api.source.Source source = (com.oracle.truffle.api.source.Source) impl;

        return source.getCode(lineNumber);
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
    public int hashCode(Object impl) {
        return impl.hashCode();
    }

    @Override
    public boolean equals(Object impl, Object otherImpl) {
        return impl.equals(otherImpl);
    }

    @Override
    public Source build(Object origin, URI uri, String name, String content, boolean interactive, boolean internal) {
        com.oracle.truffle.api.source.Source.Builder<?, ?, ?> builder;
        boolean needsName = false;
        if (origin instanceof File) {
            builder = com.oracle.truffle.api.source.Source.newBuilder((File) origin);
        } else if (origin instanceof CharSequence) {
            // TODO add support for real CharSequence sources.
            builder = com.oracle.truffle.api.source.Source.newBuilder(((CharSequence) origin).toString());
            needsName = true;
        } else if (origin instanceof Reader) {
            builder = com.oracle.truffle.api.source.Source.newBuilder((Reader) origin);
            needsName = true;
        } else if (origin instanceof URL) {
            builder = com.oracle.truffle.api.source.Source.newBuilder((URL) origin);
        } else {
            throw new AssertionError();
        }

        if (uri != null) {
            builder.uri(uri);
        }

        if (name != null) {
            builder.name(name);
        } else if (needsName) {
            builder.name("Unnamed");
        }

        if (content != null) {
            builder.content(content);
        }

        if (internal) {
            builder.internal();
        }

        if (interactive) {
            builder.interactive();
        }

        builder.mimeType("x-unknown");

        try {
            return engineImpl.getAPIAccess().newSource(builder.build());
        } catch (Exception e) {
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            } else {
                throw new RuntimeException(e);
            }
        }
    }

}
