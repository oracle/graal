/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.truffle.espresso.nodes;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.espresso.classfile.attributes.LineNumberTableAttribute;
import com.oracle.truffle.espresso.impl.ContextAccess;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.impl.Method.MethodVersion;
import com.oracle.truffle.espresso.runtime.EspressoContext;

/**
 * Base node for all implementations of Java methods.
 */
public abstract class EspressoMethodNode extends EspressoInstrumentableNode implements ContextAccess {

    private final MethodVersion method;

    EspressoMethodNode(MethodVersion method) {
        this.method = method;
    }

    public MethodVersion getMethodVersion() {
        return method;
    }

    public final Method getMethod() {
        return method.getMethod();
    }

    @Override
    public boolean hasTag(Class<? extends Tag> tag) {
        return tag == StandardTags.RootTag.class;
    }

    @TruffleBoundary
    @Override
    public final SourceSection getSourceSection() {
        Source s = getSource();
        if (s == null) {
            return null;
        }

        LineNumberTableAttribute lineNumberTable = method.getLineNumberTableAttribute();

        if (lineNumberTable != LineNumberTableAttribute.EMPTY) {
            LineNumberTableAttribute.Entry[] entries = lineNumberTable.getEntries();
            int startLine = Integer.MAX_VALUE;
            int endLine = 0;

            for (int i = 0; i < entries.length; i++) {
                int line = entries[i].getLineNumber();
                if (line > endLine) {
                    endLine = line;
                }
                if (line < startLine) {
                    startLine = line;
                }
            }

            return s.createSection(startLine, 1, endLine, 1);
        }
        // also this should be cached, at least not create a new source every time.
        return s.createSection(1);
    }

    public final Source getSource() {
        return getMethod().getSource();
    }

    @Override
    public final EspressoContext getContext() {
        return getMethod().getContext();
    }

}
