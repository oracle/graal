/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.jdwp.server.impl;

import com.oracle.svm.jdwp.bridge.JDWPException;
import com.oracle.svm.jdwp.bridge.SymbolicRefs;
import com.oracle.svm.jdwp.server.ClassUtils;

import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

public final class ServerSymbolicRefs implements SymbolicRefs {

    @Override
    public long toTypeRef(ResolvedJavaType resolvedJavaType) {
        if (resolvedJavaType == null) {
            return 0L;
        }
        int typeRefIndex = ClassUtils.UNIVERSE.getTypeIndexFor(resolvedJavaType).orElseThrow(IllegalArgumentException::new);
        return ServerJDWP.BRIDGE.typeRefIndexToId(typeRefIndex);
    }

    @Override
    public long toFieldRef(ResolvedJavaField resolvedJavaField) {
        if (resolvedJavaField == null) {
            return 0L;
        }
        int fieldRefIndex = ClassUtils.UNIVERSE.getFieldIndexFor(resolvedJavaField).orElseThrow(IllegalArgumentException::new);
        return ServerJDWP.BRIDGE.fieldRefIndexToId(fieldRefIndex);
    }

    @Override
    public long toMethodRef(ResolvedJavaMethod resolvedJavaMethod) {
        if (resolvedJavaMethod == null) {
            return 0L;
        }
        int fieldRefIndex = ClassUtils.UNIVERSE.getMethodIndexFor(resolvedJavaMethod).orElseThrow(IllegalArgumentException::new);
        return ServerJDWP.BRIDGE.methodRefIndexToId(fieldRefIndex);
    }

    @Override
    public ResolvedJavaType toResolvedJavaType(long typeRefId) throws JDWPException {
        if (typeRefId == 0L) {
            return null;
        }
        int typeRefIndex = ServerJDWP.BRIDGE.typeRefIdToIndex(typeRefId);
        return ClassUtils.UNIVERSE.getTypeAtIndex(typeRefIndex);
    }

    @Override
    public ResolvedJavaField toResolvedJavaField(long fieldRefId) throws JDWPException {
        if (fieldRefId == 0L) {
            return null;
        }
        int fieldRefIndex = ServerJDWP.BRIDGE.fieldRefIdToIndex(fieldRefId);
        return ClassUtils.UNIVERSE.getFieldAtIndex(fieldRefIndex);
    }

    @Override
    public ResolvedJavaMethod toResolvedJavaMethod(long methodRefId) throws JDWPException {
        if (methodRefId == 0L) {
            return null;
        }
        int methodRefIndex = ServerJDWP.BRIDGE.methodRefIdToIndex(methodRefId);
        return ClassUtils.UNIVERSE.getMethodAtIndex(methodRefIndex);
    }
}
