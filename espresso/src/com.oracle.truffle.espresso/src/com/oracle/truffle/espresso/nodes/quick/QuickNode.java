/*
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.nodes.quick;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.espresso.runtime.StaticObject;

public abstract class QuickNode extends BaseQuickNode {

    public static final QuickNode[] EMPTY_ARRAY = new QuickNode[0];

    @CompilationFinal private boolean exceptionProfile;

    protected final void enterExceptionProfile() {
        if (!exceptionProfile) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            exceptionProfile = true;
        }
    }

    protected final int top;

    private final int callerBCI;

    protected QuickNode(int top, int callerBCI) {
        this.top = top;
        this.callerBCI = callerBCI;
        this.exceptionProfile = false;
    }

    @Override
    public abstract int execute(VirtualFrame frame, long[] primitives, Object[] refs);

    protected final StaticObject nullCheck(StaticObject value) {
        if (StaticObject.isNull(value)) {
            enterExceptionProfile();
            throw getBytecodeNode().getMeta().throwNullPointerException();
        }
        return value;
    }

    @Override
    public int getBci(@SuppressWarnings("unused") Frame frame) {
        return callerBCI;
    }

    @Override
    public SourceSection getSourceSection() {
        return getBytecodeNode().getSourceSectionAtBCI(callerBCI);
    }
}
