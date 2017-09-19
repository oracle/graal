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

import java.net.URI;

import com.oracle.truffle.api.source.impl.SourceAccessor;

final class SourceImpl extends Source implements Cloneable {

    SourceImpl(Content content) {
        this(content, null, null, null, null, false, false);
    }

    SourceImpl(Content content, String mimeType, String language, URI uri, String name, boolean internal, boolean interactive) {
        super(content, mimeType, language, uri, name, internal, interactive);
    }

    @Override
    protected SourceImpl clone() throws CloneNotSupportedException {
        return (SourceImpl) super.clone();
    }

    @Override
    public boolean equals(Object obj) {
        SourceAccessor.neverPartOfCompilation("do not call Source.equals from compiled code");
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

}
