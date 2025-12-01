/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.methodhandles;

import java.lang.invoke.MethodHandle;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.invoke.Target_java_lang_invoke_MemberName;
import com.oracle.svm.core.util.VMError;

public final class MethodHandleInterpreterUtils {
    private MethodHandleInterpreterUtils() {
    }

    public static Target_java_lang_invoke_MemberName extractVMEntry(MethodHandle handle) {
        return extractVMEntry(SubstrateUtil.cast(handle, Target_java_lang_invoke_MethodHandle.class));
    }

    public static Target_java_lang_invoke_MemberName extractVMEntry(Target_java_lang_invoke_MethodHandle handle) {
        Target_java_lang_invoke_LambdaForm lform = handle.internalForm();
        if (lform.vmentry == null) {
            // if the form comes from the image, its entry might have been reset
            VMError.guarantee(Heap.getHeap().isInImageHeap(lform));
            lform.prepare();
            VMError.guarantee(lform.vmentry != null);
        }
        return lform.vmentry;
    }
}
