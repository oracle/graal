/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.nodes;

import static com.oracle.max.asm.target.amd64.AMD64.*;

import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.max.cri.ci.*;

public final class HotSpotExceptionObject extends ExceptionObjectNode {

    private final int threadExceptionOopOffset;
    private final int threadExceptionPcOffset;

    public HotSpotExceptionObject(int threadExceptionOopOffset, int threadExceptionPcOffset) {
        this.threadExceptionOopOffset = threadExceptionOopOffset;
        this.threadExceptionPcOffset = threadExceptionPcOffset;
    }

    @Override
    public void generate(LIRGeneratorTool gen) {
        CiRegisterValue thread = r15.asValue();
        CiAddress exceptionAddress = new CiAddress(CiKind.Object, thread, threadExceptionOopOffset);
        CiAddress pcAddress = new CiAddress(CiKind.Long, thread, threadExceptionPcOffset);
        CiValue exception = gen.emitLoad(exceptionAddress, false);
        CiRegisterValue raxException = rax.asValue(CiKind.Object);
        gen.emitMove(exception, raxException);
        gen.emitStore(exceptionAddress, CiConstant.NULL_OBJECT, false);
        gen.emitStore(pcAddress, CiConstant.LONG_0, false);
        gen.setResult(this, raxException);
    }
}
