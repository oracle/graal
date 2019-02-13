/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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
    public boolean hasLines(Object impl) {
        SourceSection section = (SourceSection) impl;
        return section.hasLines();
    }

    @Override
    public boolean hasColumns(Object impl) {
        SourceSection section = (SourceSection) impl;
        return section.hasColumns();
    }

    @Override
    public boolean hasCharIndex(Object impl) {
        SourceSection section = (SourceSection) impl;
        return section.hasCharIndex();
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
