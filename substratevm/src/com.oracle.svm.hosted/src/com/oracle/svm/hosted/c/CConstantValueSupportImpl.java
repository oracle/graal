/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.c;

import org.graalvm.nativeimage.c.constant.CConstant;
import org.graalvm.nativeimage.impl.CConstantValueSupport;

import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.c.info.ConstantInfo;

import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public final class CConstantValueSupportImpl implements CConstantValueSupport {
    private final NativeLibraries nativeLibraries;
    private final MetaAccessProvider metaAccess;

    public CConstantValueSupportImpl(NativeLibraries nativeLibraries, MetaAccessProvider metaAccess) {
        this.nativeLibraries = nativeLibraries;
        this.metaAccess = metaAccess;
    }

    @Override
    public <T> T getCConstantValue(Class<?> declaringClass, String methodName, Class<T> returnType) {
        ResolvedJavaMethod method;
        try {
            method = metaAccess.lookupJavaMethod(declaringClass.getMethod(methodName));
        } catch (NoSuchMethodException | SecurityException e) {
            throw VMError.shouldNotReachHere("Method not found: " + declaringClass.getName() + "." + methodName);
        }
        if (method.getAnnotation(CConstant.class) == null) {
            throw VMError.shouldNotReachHere("Method " + declaringClass.getName() + "." + methodName + " is not annotated with @" + CConstant.class.getSimpleName());
        }

        ConstantInfo constantInfo = (ConstantInfo) nativeLibraries.findElementInfo(method);
        Object value = constantInfo.getValueInfo().getProperty();
        switch (constantInfo.getKind()) {
            case INTEGER:
            case POINTER:
                Long longValue = (Long) value;
                if (returnType == Boolean.class) {
                    return returnType.cast(Boolean.valueOf(longValue.longValue() != 0));
                } else if (returnType == Integer.class) {
                    return returnType.cast(Integer.valueOf((int) longValue.longValue()));
                } else if (returnType == Long.class) {
                    return returnType.cast(value);
                }
                break;

            case FLOAT:
                if (returnType == Double.class) {
                    return returnType.cast(value);
                }
                break;

            case STRING:
                if (returnType == String.class) {
                    return returnType.cast(value);
                }
                break;

            case BYTEARRAY:
                if (returnType == byte[].class) {
                    return returnType.cast(value);
                }
                break;
        }

        throw VMError.shouldNotReachHere("Unexpected returnType: " + returnType.getName());
    }
}
