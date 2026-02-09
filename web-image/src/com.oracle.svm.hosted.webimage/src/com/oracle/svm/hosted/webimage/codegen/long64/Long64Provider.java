/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.hosted.webimage.codegen.long64;

import com.oracle.svm.hosted.meta.HostedField;
import com.oracle.svm.hosted.meta.HostedMetaAccess;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.util.JVMCIReflectionUtil;
import com.oracle.svm.webimage.longemulation.Long64;

import jdk.graal.compiler.core.common.type.ObjectStamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.core.common.type.TypeReference;
import jdk.vm.ci.meta.ResolvedJavaType;

public class Long64Provider {
    private final HostedMetaAccess metaAccess;
    private final ResolvedJavaType long64Type;

    public Long64Provider(HostedMetaAccess metaAccess) {
        this.metaAccess = metaAccess;
        this.long64Type = metaAccess.lookupJavaType(Long64.class);
    }

    public ResolvedJavaType getLong64Type() {
        return long64Type;
    }

    public HostedField getField(String name) {
        return (HostedField) JVMCIReflectionUtil.getUniqueDeclaredField(long64Type, name);
    }

    public HostedMethod getMethod(String name, Class<?>... parameterTypes) {
        return (HostedMethod) JVMCIReflectionUtil.getUniqueDeclaredMethod(metaAccess, long64Type, name, parameterTypes);
    }

    public ObjectStamp getLong64Stamp() {
        return StampFactory.objectNonNull(TypeReference.createExactTrusted(long64Type));
    }
}
