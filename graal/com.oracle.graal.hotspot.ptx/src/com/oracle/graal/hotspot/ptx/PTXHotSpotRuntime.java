/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.ptx;

import static com.oracle.graal.ptx.PTX.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.graph.Node;
import com.oracle.graal.nodes.calc.ConvertNode;

public class PTXHotSpotRuntime extends HotSpotRuntime {

    public PTXHotSpotRuntime(HotSpotVMConfig config, HotSpotGraalRuntime graalRuntime) {
        super(config, graalRuntime);

    }

    @Override
    public void lower(Node n, LoweringTool tool) {
        if (n instanceof ConvertNode) {
            // PTX has a cvt instruction that "takes a variety of
            // operand types and sizes, as its job is to convert from
            // nearly any data type to any other data type (and
            // size)." [Section 6.2 of PTX ISA manual]
            // So, there is no need to lower the operation.
            return;
        } else {
            super.lower(n, tool);
        }
    }

    @Override
    public void registerReplacements(Replacements replacements) {
        //TODO: Do we need to implement this functionality for PTX?
    }

    // PTX code does not use stack or stack pointer
    @Override
    public Register stackPointerRegister() {
        return Register.None;
    }

    // PTX code does not have heap register
    @Override
    public Register heapBaseRegister() {
        return Register.None;
    }

    // Thread register is %tid.
    @Override
    public Register threadRegister() {
        return tid;
    }

    @Override
    protected RegisterConfig createRegisterConfig() {
        return new PTXHotSpotRegisterConfig(graalRuntime.getTarget().arch);
    }
}
