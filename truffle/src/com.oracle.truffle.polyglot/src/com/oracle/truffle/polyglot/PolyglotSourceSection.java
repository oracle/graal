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

import org.graalvm.polyglot.impl.AbstractPolyglotImpl;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.AbstractSourceSectionImpl;

import com.oracle.truffle.api.source.SourceSection;

class PolyglotSourceSection extends AbstractSourceSectionImpl {

    protected PolyglotSourceSection(AbstractPolyglotImpl engineImpl) {
        super(engineImpl);
    }

    @Override
    public boolean isAvailable(Object impl) {
        SourceSection section = (SourceSection) impl;
        return section.isAvailable();
    }

    @Override
    public int getStartLine(Object impl) {
        SourceSection section = (SourceSection) impl;
        return section.getStartLine();
    }

    @Override
    public int getStartColumn(Object impl) {
        SourceSection section = (SourceSection) impl;
        return section.getStartColumn();
    }

    @Override
    public int getEndLine(Object impl) {
        SourceSection section = (SourceSection) impl;
        return section.getEndLine();
    }

    @Override
    public int getEndColumn(Object impl) {
        SourceSection section = (SourceSection) impl;
        return section.getEndColumn();
    }

    @Override
    public int getCharIndex(Object impl) {
        SourceSection section = (SourceSection) impl;
        return section.getCharIndex();
    }

    @Override
    public int getCharLength(Object impl) {
        SourceSection section = (SourceSection) impl;
        return section.getCharLength();
    }

    @Override
    public int getCharEndIndex(Object impl) {
        SourceSection section = (SourceSection) impl;
        return section.getCharEndIndex();
    }

    @Override
    public CharSequence getCode(Object impl) {
        SourceSection section = (SourceSection) impl;
        return section.getCharacters();
    }

    @Override
    public String toString(Object impl) {
        SourceSection section = (SourceSection) impl;
        return section.toString();
    }

    @Override
    public int hashCode(Object impl) {
        SourceSection section = (SourceSection) impl;
        return section.hashCode();
    }

    @Override
    public boolean equals(Object impl, Object obj) {
        SourceSection section = (SourceSection) impl;
        return section.equals(obj);
    }

}
