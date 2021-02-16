/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.graal.meta;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Signature;

public class SubstrateSignature implements Signature {

    private Object parameterTypes;
    private SubstrateType returnType;

    @Platforms(Platform.HOSTED_ONLY.class)
    public SubstrateSignature() {
        /* Types are initialized later with an explicit call to setTypes. */
    }

    public SubstrateSignature(SubstrateType[] parameterTypes, SubstrateType returnType) {
        setTypes(parameterTypes, returnType);
    }

    public void setTypes(SubstrateType[] parameterTypes, SubstrateType returnType) {
        if (parameterTypes.length == 0) {
            this.parameterTypes = null;
        } else if (parameterTypes.length == 1) {
            this.parameterTypes = parameterTypes[0];
        } else {
            this.parameterTypes = parameterTypes;
        }
        this.returnType = returnType;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public Object getRawParameterTypes() {
        return parameterTypes;
    }

    @Override
    public int getParameterCount(boolean withReceiver) {
        int result;
        if (parameterTypes == null) {
            result = 0;
        } else if (parameterTypes instanceof SubstrateType) {
            result = 1;
        } else {
            result = ((SubstrateType[]) parameterTypes).length;
        }

        if (withReceiver) {
            result++;
        }
        return result;
    }

    @Override
    public SubstrateType getParameterType(int index, ResolvedJavaType accessingClass) {
        if (parameterTypes instanceof SubstrateType) {
            assert index == 0;
            return (SubstrateType) parameterTypes;
        } else {
            return ((SubstrateType[]) parameterTypes)[index];
        }
    }

    @Override
    public SubstrateType getReturnType(ResolvedJavaType accessingClass) {
        return returnType;
    }
}
