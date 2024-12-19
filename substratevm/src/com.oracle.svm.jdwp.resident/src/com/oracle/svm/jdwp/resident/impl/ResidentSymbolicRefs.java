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
package com.oracle.svm.jdwp.resident.impl;

import java.util.OptionalInt;

import com.oracle.svm.interpreter.DebuggerSupport;
import com.oracle.svm.interpreter.metadata.InterpreterUniverse;
import com.oracle.svm.jdwp.bridge.ErrorCode;
import com.oracle.svm.jdwp.bridge.JDWPException;
import com.oracle.svm.jdwp.bridge.SymbolicRefs;
import com.oracle.svm.jdwp.resident.JDWPBridgeImpl;

import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

public final class ResidentSymbolicRefs implements SymbolicRefs {

    @Override
    public long toTypeRef(ResolvedJavaType resolvedJavaType) {
        if (resolvedJavaType == null) {
            return 0L;
        }
        InterpreterUniverse universe = DebuggerSupport.singleton().getUniverse();
        OptionalInt typeIndexFor = universe.getTypeIndexFor(resolvedJavaType);
        int typeIndex = typeIndexFor.orElseThrow(IllegalArgumentException::new);
        return JDWPBridgeImpl.getIds().getIdOrCreateWeak(universe.getTypeAtIndex(typeIndex));
    }

    @Override
    public long toFieldRef(ResolvedJavaField resolvedJavaField) {
        if (resolvedJavaField == null) {
            return 0L;
        }
        InterpreterUniverse universe = DebuggerSupport.singleton().getUniverse();
        OptionalInt fieldIndexFor = universe.getFieldIndexFor(resolvedJavaField);
        int fieldIndex = fieldIndexFor.orElseThrow(IllegalArgumentException::new);
        return JDWPBridgeImpl.getIds().getIdOrCreateWeak(universe.getFieldAtIndex(fieldIndex));
    }

    @Override
    public long toMethodRef(ResolvedJavaMethod resolvedJavaMethod) {
        if (resolvedJavaMethod == null) {
            return 0L;
        }
        InterpreterUniverse universe = DebuggerSupport.singleton().getUniverse();
        OptionalInt methodIndexFor = universe.getMethodIndexFor(resolvedJavaMethod);
        int methodIndex = methodIndexFor.orElseThrow(IllegalArgumentException::new);
        return JDWPBridgeImpl.getIds().getIdOrCreateWeak(universe.getMethodAtIndex(methodIndex));
    }

    @Override
    public ResolvedJavaType toResolvedJavaType(long typeRefId) throws JDWPException {
        if (typeRefId == 0) {
            return null;
        }
        ResolvedJavaType resolvedJavaType;
        try {
            resolvedJavaType = JDWPBridgeImpl.getIds().toObject(typeRefId, ResolvedJavaType.class);
        } catch (ClassCastException e) {
            throw JDWPException.raise(ErrorCode.INVALID_CLASS);
        }
        if (resolvedJavaType == null) {
            throw JDWPException.raise(ErrorCode.INVALID_OBJECT);
        }
        InterpreterUniverse universe = DebuggerSupport.singleton().getUniverse();
        OptionalInt typeIndexFor = universe.getTypeIndexFor(resolvedJavaType);
        if (typeIndexFor.isEmpty()) {
            throw JDWPException.raise(ErrorCode.INVALID_CLASS);
        }
        return resolvedJavaType;
    }

    @Override
    public ResolvedJavaField toResolvedJavaField(long fieldRefId) throws JDWPException {
        if (fieldRefId == 0) {
            return null;
        }
        ResolvedJavaField resolvedJavaField;
        try {
            resolvedJavaField = JDWPBridgeImpl.getIds().toObject(fieldRefId, ResolvedJavaField.class);
        } catch (ClassCastException e) {
            throw JDWPException.raise(ErrorCode.INVALID_FIELDID);
        }
        if (resolvedJavaField == null) {
            throw JDWPException.raise(ErrorCode.INVALID_OBJECT);
        }
        InterpreterUniverse universe = DebuggerSupport.singleton().getUniverse();
        OptionalInt typeIndexFor = universe.getFieldIndexFor(resolvedJavaField);
        if (typeIndexFor.isEmpty()) {
            throw JDWPException.raise(ErrorCode.INVALID_FIELDID);
        }
        return resolvedJavaField;
    }

    @Override
    public ResolvedJavaMethod toResolvedJavaMethod(long methodRefId) throws JDWPException {
        if (methodRefId == 0) {
            return null;
        }
        ResolvedJavaMethod resolvedJavaMethod;
        try {
            resolvedJavaMethod = JDWPBridgeImpl.getIds().toObject(methodRefId, ResolvedJavaMethod.class);
        } catch (ClassCastException e) {
            throw JDWPException.raise(ErrorCode.INVALID_METHODID);
        }
        if (resolvedJavaMethod == null) {
            throw JDWPException.raise(ErrorCode.INVALID_OBJECT);
        }
        InterpreterUniverse universe = DebuggerSupport.singleton().getUniverse();
        OptionalInt methodIndexFor = universe.getMethodIndexFor(resolvedJavaMethod);
        if (methodIndexFor.isEmpty()) {
            throw JDWPException.raise(ErrorCode.INVALID_METHODID);
        }
        return resolvedJavaMethod;
    }
}
