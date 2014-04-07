/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.lir.sparc;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.lir.*;

/**
 * SPARC specific frame map.
 *
 * This is the format of a SPARC stack frame:
 *
 * <pre>
 *   Base       Contents
 *
 *            :                                :  -----
 *   caller   | incoming overflow argument n   |    ^
 *   frame    :     ...                        :    | positive
 *            | incoming overflow argument 0   |    | offsets
 *   ---------+--------------------------------+---------------------------
 *            | spill slot 0                   |    | negative   ^      ^
 *            :     ...                        :    v offsets    |      |
 *            | spill slot n                   |  -----        total    |
 *            +--------------------------------+               frame    |
 *   current  | alignment padding              |               size     |
 *   frame    +--------------------------------+  -----          |      |
 *            | outgoing overflow argument n   |    ^            |    frame
 *            :     ...                        :    | positive   |    size
 *            | outgoing overflow argument 0   |    | offsets    |      |
 *            +--------------------------------+    |            |      |
 *            | return address                 |    |            |      |
 *            +--------------------------------+    |            |      |
 *            |                                |    |            |      |
 *            : callee save area               :    |            |      |
 *            |                                |    |            v      v
 *    %sp--&gt;  +--------------------------------+---------------------------
 *
 * </pre>
 *
 * The spill slot area also includes stack allocated memory blocks (ALLOCA blocks). The size of such
 * a block may be greater than the size of a normal spill slot or the word size.
 * <p>
 * A runtime can reserve space at the beginning of the overflow argument area. The calling
 * convention can specify that the first overflow stack argument is not at offset 0, but at a
 * specified offset. Use {@link CodeCacheProvider#getMinimumOutgoingSize()} to make sure that
 * call-free methods also have this space reserved. Then the VM can use the memory at offset 0
 * relative to the stack pointer.
 */
public final class SPARCFrameMap extends FrameMap {

    public SPARCFrameMap(CodeCacheProvider codeCache, RegisterConfig registerConfig) {
        super(codeCache, registerConfig);
        // offset relative to sp + total frame size
        initialSpillSize = 0;
        spillSize = initialSpillSize;
    }

    @Override
    public int totalFrameSize() {
        return frameSize();
    }

    @Override
    public int currentFrameSize() {
        return alignFrameSize(calleeSaveAreaSize() + returnAddressSize() + outgoingSize + spillSize);
    }

    @Override
    protected int alignFrameSize(int size) {
        int x = size + (target.stackAlignment - 1);
        return (x / target.stackAlignment) * target.stackAlignment;
    }

    @Override
    public int offsetToCalleeSaveArea() {
        return 0;
    }

    @Override
    protected StackSlot allocateNewSpillSlot(PlatformKind kind, int additionalOffset) {
        return StackSlot.get(kind, -spillSize + additionalOffset, true);
    }
}
