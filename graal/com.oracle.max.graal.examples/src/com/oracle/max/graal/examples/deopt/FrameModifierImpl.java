/*
 * Copyright (c) 2011, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.max.graal.examples.deopt;

import com.oracle.max.graal.extensions.*;
import com.sun.cri.ci.*;
import com.sun.cri.ri.*;


public class FrameModifierImpl implements FrameModifier {

    @Override
    public CiFrame getFrame(RiRuntime runtime, CiFrame frame) {
        try {
            DeoptHandler.class.getMethod("test", Integer.TYPE, Object[].class);
        } catch (Exception e) {
            e.printStackTrace();
            return frame;
        }
        if (frame.method.name().equals("testDeopt")) {
            RiType type = runtime.getType(DeoptHandler.class);
            RiMethod method = type.getMethod("test", "(I[Ljava/lang/Object;)I");
            System.out.println("Size: " + method.maxLocals() + " " + method.maxStackSize());
            RiType arrayType = runtime.getType(Object.class).arrayOf();
            CiValue[] values = new CiValue[frame.values.length];
            for (int i = 0; i < values.length; i++) {
                values[i] = CiVirtualObject.proxy(runtime, frame.values[i], i + 2);
            }
            CiVirtualObject local = CiVirtualObject.get(arrayType, values, 0);
            CiValue[] values2 = new CiValue[method.maxLocals()];
            values2[0] = CiConstant.forInt(frame.bci);
            values2[1] = local;
            for (int i = 2; i < values2.length; i++) {
                values2[i] = CiValue.IllegalValue;
            }
            return new CiFrame((CiFrame) frame.caller, method, 0, false, values2, method.maxLocals(), 0, 0);
        }
        return frame;
    }

}
