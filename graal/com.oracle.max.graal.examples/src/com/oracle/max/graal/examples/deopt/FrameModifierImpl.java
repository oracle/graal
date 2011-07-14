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

import java.util.*;

import com.oracle.max.graal.extensions.*;
import com.sun.cri.ci.*;
import com.sun.cri.ci.CiVirtualObject.*;
import com.sun.cri.ri.*;


public class FrameModifierImpl implements FrameModifier {
    private static DeoptHandler HANDLER = new DeoptHandler();

    @Override
    public CiFrame getFrame(RiRuntime runtime, CiFrame frame) {
        if (frame.method.name().equals("testDeopt")) {
            // get the handler method
            RiType type = runtime.getType(DeoptHandler.class);
            CiKind returnKind = frame.method.signature().returnKind();
            String methodName = "handle_" + returnKind;
            String methodSignature = "(Lcom/sun/cri/ri/RiMethod;I[Ljava/lang/Object;III)" + returnKind.signatureChar();
            RiMethod handlerMethod = type.getMethod(methodName, methodSignature);
            assert handlerMethod != null : methodName + " not found...";

            // put the current state (local vars, expressions, etc.) into an array
            CiVirtualObjectFactory factory = new CiVirtualObjectFactory(runtime);
            ArrayList<CiValue> originalValues = new ArrayList<CiValue>();
            for (int i = 0; i < frame.values.length; i += frame.values[i].kind.sizeInSlots()) {
                originalValues.add(factory.proxy(frame.values[i]));
            }
            CiValue boxedValues = factory.arrayProxy(runtime.getType(Object[].class), originalValues.toArray(new CiValue[originalValues.size()]));

            // build the list of arguments
            CiValue[] newValues = new CiValue[handlerMethod.maxLocals()];
            int p = 0;
            newValues[p++] = CiConstant.forObject(HANDLER);         // receiver
            newValues[p++] = CiConstant.forObject(frame.method);    // method that caused deoptimization
            newValues[p++] = CiConstant.forInt(frame.bci);          // bytecode index
            newValues[p++] = boxedValues;                           // original locals, expression stack and locks
            newValues[p++] = CiConstant.forInt(frame.numLocals);    // number of locals
            newValues[p++] = CiConstant.forInt(frame.numStack);     // size of expression stack
            newValues[p++] = CiConstant.forInt(frame.numLocks);     // number of locks

            // fill the rest of the local variables with zeros
            while (p < newValues.length) {
                newValues[p++] = CiValue.IllegalValue;
            }

            // ... and return a new frame that points to the start of the handler method
            return new CiFrame((CiFrame) frame.caller, handlerMethod, /*bci*/ 0, false, newValues, handlerMethod.maxLocals(), 0, 0);
        }
        return frame;
    }

}
