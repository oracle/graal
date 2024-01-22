/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2024, 2024, Alibaba Group Holding Limited. All rights reserved.
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

package com.oracle.svm.common;

import com.oracle.svm.util.ReflectionUtil;

import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.function.Function;

public class VarHandleInitializer {
    private static final Field varHandleVFormField = ReflectionUtil.lookupField(VarHandle.class, "vform");
    private static final Method varFormInitMethod = ReflectionUtil.lookupMethod(ReflectionUtil.lookupClass(false, "java.lang.invoke.VarForm"), "getMethodType_V", int.class);
    private static final Method varHandleGetMethodHandleMethod = ReflectionUtil.lookupMethod(VarHandle.class, "getMethodHandle", int.class);

    public static void eagerlyInitializeVarHandle(VarHandle varHandle, Function<ReflectiveOperationException, RuntimeException> errorHandler) {
        try {
            /*
             * The field VarHandle.vform.methodType_V_table is a @Stable field but initialized
             * lazily on first access. Therefore, constant folding can happen only after
             * initialization has happened. We force initialization by invoking the method
             * VarHandle.vform.getMethodType_V(0).
             */
            Object varForm = varHandleVFormField.get(varHandle);
            varFormInitMethod.invoke(varForm, 0);

            /*
             * The AccessMode used for the access that we are going to intrinsify is hidden in a
             * AccessDescriptor object that is also passed in as a parameter to the intrinsified
             * method. Initializing all AccessMode enum values is easier than trying to extract the
             * actual AccessMode.
             */
            for (VarHandle.AccessMode accessMode : VarHandle.AccessMode.values()) {
                /*
                 * Force initialization of the @Stable field VarHandle.vform.memberName_table.
                 */
                boolean isAccessModeSupported = varHandle.isAccessModeSupported(accessMode);
                /*
                 * Force initialization of the @Stable field
                 * VarHandle.typesAndInvokers.methodType_table.
                 */
                varHandle.accessModeType(accessMode);

                if (isAccessModeSupported) {
                    /* Force initialization of the @Stable field VarHandle.methodHandleTable. */
                    varHandleGetMethodHandleMethod.invoke(varHandle, accessMode.ordinal());
                }
            }
        } catch (ReflectiveOperationException ex) {
            throw errorHandler.apply(ex);
        }
    }
}
