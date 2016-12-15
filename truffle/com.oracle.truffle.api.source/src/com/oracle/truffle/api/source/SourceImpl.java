/*
 * Copyright (c) 2013, 2016, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.ref.WeakReference;
import java.util.Objects;

import com.oracle.truffle.api.source.impl.SourceAccessor;
import java.net.URI;

final class SourceImpl extends Source implements Cloneable {
    private static Ref SOURCES = null;

    SourceImpl(Content content) {
        this(content, null, null, null, false, false);
    }

    SourceImpl(Content content, String mimeType, URI uri, String name, boolean internal, boolean interactive) {
        super(content, mimeType, uri, name, internal, interactive);
        registerSource(this);
    }

    @Override
    protected SourceImpl clone() throws CloneNotSupportedException {
        SourceImpl clone = (SourceImpl) super.clone();
        registerSource(clone);
        return clone;
    }

    private static long nextCheck;

    static synchronized void registerSource(SourceImpl source) {
        long now = System.currentTimeMillis();
        if (nextCheck < now) {
            findSource(null);
            nextCheck = now + 1000;
        }
        SOURCES = new Ref(source, SOURCES);
    }

    static synchronized Source findSource(String name) {
        Ref prev = null;
        Ref now = SOURCES;
        while (now != null) {
            SourceImpl source = now.get();
            if (source == null) {
                if (prev == null) {
                    SOURCES = now.next;
                } else {
                    prev.next = now.next;
                }
            } else {
                prev = now;
                if (Objects.equals(source.getName(), name)) {
                    return source;
                }
            }
            now = now.next;
        }
        return null;
    }

    @Override
    public boolean equals(Object obj) {
        return EqualCall.INSTANCE.invoke(this, obj);
    }

    final boolean equalsImpl(Object obj) {
        if (obj instanceof Source) {
            Source other = (Source) obj;
            return content().equals(other.content()) && equalAttributes(other);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return content().hashCode();
    }

    private static final class Ref extends WeakReference<SourceImpl> {
        Ref next;

        Ref(SourceImpl source, Ref next) {
            super(source);
            this.next = next;
        }
    }

    private static final class EqualCall extends SourceAccessor.TruffleBoundaryCall {
        static final EqualCall INSTANCE = new EqualCall();

        @Override
        public boolean call(Object thiz, Object obj) {
            return ((SourceImpl) thiz).equalsImpl(obj);
        }
    }
}
