/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.espresso.impl.Method.MethodVersion;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.vm.VM;

/**
 * Base node for all implementations of Java methods.
 */
abstract class EspressoInstrumentableRootNodeImpl extends EspressoInstrumentableRootNode {

    private final MethodVersion methodVersion;
    private volatile SourceSection sourceSection;

    EspressoInstrumentableRootNodeImpl(MethodVersion methodVersion) {
        this.methodVersion = methodVersion;
    }

    @Override
    final MethodVersion getMethodVersion() {
        return methodVersion;
    }

    @Override
    boolean isTrivial() {
        return false;
    }

    @Override
    public boolean canSplit() {
        return false;
    }

    // Overridden in children which support splitting
    @Override
    public EspressoInstrumentableRootNode split() {
        throw EspressoError.shouldNotReachHere();
    }

    @TruffleBoundary
    @Override
    public final SourceSection getSourceSection() {
        Source s = getSource();
        if (s == null) {
            return null;
        }

        if (sourceSection == null) {
            SourceSection localSourceSection = methodVersion.getWholeMethodSourceSection();
            synchronized (this) {
                if (sourceSection == null) {
                    sourceSection = localSourceSection;
                }
            }
        }
        return sourceSection;
    }

    public final Source getSource() {
        return getMethodVersion().getSource();
    }

    @Override
    public boolean hasTag(Class<? extends Tag> tag) {
        if (tag == StandardTags.RootTag.class) {
            return true;
        }
        if (tag == StandardTags.RootBodyTag.class) {
            return true;
        }
        return false;
    }

    @Override
    public int getBci(Frame frame) {
        return VM.EspressoStackElement.NATIVE_BCI;
    }
}
