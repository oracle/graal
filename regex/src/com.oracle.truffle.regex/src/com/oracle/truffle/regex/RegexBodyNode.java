/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.instrumentation.GenerateWrapper;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.nodes.ExecutableNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;

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

    @CompilerDirectives.TruffleBoundary
    @Override
    public SourceSection getSourceSection() {
        if (sourceSection == null) {
            String patternSrc = "/" + source.getPattern() + "/" + source.getFlags().toString();
            Source src = Source.newBuilder(RegexLanguage.ID, patternSrc, source.getPattern()).mimeType("application/js-regex").build();
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

    @Override
    @CompilerDirectives.TruffleBoundary
    public final String toString() {
        return "regex " + getEngineLabel() + ": " + source;
    }

    protected String getEngineLabel() {
        return "no_engine_label";
    }
}
