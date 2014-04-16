/*
 * Copyright (c) 2009, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.hsail;

import java.lang.reflect.*;

import com.amd.okra.*;
import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.asm.hsail.*;
import com.oracle.graal.hotspot.meta.*;

/**
 * This class contains routines to emit HSAIL assembly code.
 */
public class HSAILHotSpotAssembler extends HSAILAssembler {

    public HSAILHotSpotAssembler(TargetDescription target) {
        super(target);
    }

    @Override
    public final void mov(Register a, Constant src) {
        String regName = "$d" + a.encoding();
        // For a null object simply move 0x0 into the destination register.
        if (src.isNull()) {
            emitString("mov_b64 " + regName + ", 0x0;  // null object");
        } else {
            Object obj = HotSpotObjectConstant.asObject(src);
            // Get a JNI reference handle to the object.
            long refHandle = OkraUtil.getRefHandle(obj);
            // Get the clasname of the object for emitting a comment.
            Class<?> clazz = obj.getClass();
            String className = clazz.getName();
            String comment = "// handle for object of type " + className;
            // If the object is an array note the array length in the comment.
            if (className.startsWith("[")) {
                comment += ", length " + Array.getLength(obj);
            }
            // First move the reference handle into a register.
            emitString("mov_b64 " + regName + ", 0x" + Long.toHexString(refHandle) + ";    " + comment);
            // Next load the Object addressed by this reference handle into the destination reg.
            emitString("ld_global_u64 " + regName + ", [" + regName + "];");
        }
    }
}
