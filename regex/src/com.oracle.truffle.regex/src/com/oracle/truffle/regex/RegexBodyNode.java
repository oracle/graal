/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex;

import static com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.instrumentation.GenerateWrapper;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.nodes.ExecutableNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.regex.tregex.string.Encodings;

@GenerateWrapper
public abstract class RegexBodyNode extends ExecutableNode implements InstrumentableNode {

    protected final RegexSource source;
    private final RegexLanguage language;

    private SourceSection sourceSection;

    protected RegexBodyNode(RegexLanguage language, RegexSource source) {
        super(language);
        this.source = source;
        this.language = language;
    }

    protected RegexBodyNode(RegexBodyNode copy) {
        this(copy.language, copy.source);
    }

    public RegexSource getSource() {
        return source;
    }

    public RegexLanguage getRegexLanguage() {
        return language;
    }

    public Encodings.Encoding getEncoding() {
        return source.getEncoding();
    }

    public boolean isBooleanMatch() {
        boolean booleanMatch = source.getOptions().isBooleanMatch();
        CompilerAsserts.partialEvaluationConstant(booleanMatch);
        return booleanMatch;
    }

    @TruffleBoundary
    @Override
    public SourceSection getSourceSection() {
        if (sourceSection == null) {
            String patternSrc = source.toStringEscaped();
            String name = patternSrc.length() > 30 ? patternSrc.substring(0, 30) + "..." : patternSrc;
            Source src = Source.newBuilder(RegexLanguage.ID, patternSrc, name).internal(true).mimeType("application/js-regex").build();
            sourceSection = src.createSection(0, patternSrc.length());
        }
        return sourceSection;
    }

    @Override
    public boolean hasTag(Class<? extends Tag> tag) {
        return tag == StandardTags.RootTag.class;
    }

    @Override
    public boolean isInstrumentable() {
        return true;
    }

    @Override
    public WrapperNode createWrapper(ProbeNode probe) {
        return new RegexBodyNodeWrapper(this, this, probe);
    }

    @TruffleBoundary
    @Override
    public final String toString() {
        String src = source.toStringEscaped();
        return "tregex " + source.getSource().getName() + " " + getEngineLabel() + ": " + (src.length() > 30 ? src.substring(0, 30) + "..." : src);
    }

    protected String getEngineLabel() {
        return "no_engine_label";
    }
}
