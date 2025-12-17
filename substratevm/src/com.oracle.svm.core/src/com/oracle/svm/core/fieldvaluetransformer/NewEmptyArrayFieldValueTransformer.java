/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.fieldvaluetransformer;

import com.oracle.svm.core.util.VMError;
import com.oracle.svm.util.GraalAccess;
import com.oracle.svm.util.JVMCIFieldValueTransformer;
import com.oracle.svm.util.JVMCIReflectionUtil;

import jdk.graal.compiler.phases.util.Providers;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.MetaAccessProvider;

/**
 * Reset an array field to a new empty array of the same type and length.
 */
public final class NewEmptyArrayFieldValueTransformer implements JVMCIFieldValueTransformer {
    public static final JVMCIFieldValueTransformer INSTANCE = new NewEmptyArrayFieldValueTransformer();

    @Override
    public JavaConstant transform(JavaConstant receiver, JavaConstant originalValue) {
        if (originalValue.isNull()) {
            return JavaConstant.NULL_POINTER;
        }
        Providers originalProviders = GraalAccess.getOriginalProviders();
        MetaAccessProvider metaAccess = originalProviders.getMetaAccess();
        Integer originalLength = originalProviders.getConstantReflection().readArrayLength(originalValue);
        VMError.guarantee(originalLength != null, "Original value is not an array or the array length is not known");
        return JVMCIReflectionUtil.newArrayInstance(metaAccess.lookupJavaType(originalValue).getComponentType(), originalLength);
    }
}
