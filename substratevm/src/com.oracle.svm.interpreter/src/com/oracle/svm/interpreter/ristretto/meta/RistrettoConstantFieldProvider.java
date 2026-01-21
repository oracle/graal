/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.interpreter.ristretto.meta;

import com.oracle.svm.graal.meta.SubstrateConstantFieldProvider;
import com.oracle.svm.interpreter.InterpreterToVM;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedJavaField;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedJavaType;

import jdk.graal.compiler.api.replacements.SnippetReflectionProvider;
import jdk.graal.compiler.debug.GraalError;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;

public class RistrettoConstantFieldProvider extends SubstrateConstantFieldProvider {
    private final SnippetReflectionProvider snippetReflectionProvider;

    public RistrettoConstantFieldProvider(MetaAccessProvider metaAccess, SnippetReflectionProvider snippetReflectionProvider) {
        super(metaAccess);
        this.snippetReflectionProvider = snippetReflectionProvider;
    }

    @Override
    public <T> T readConstantField(ResolvedJavaField javaField, ConstantFieldTool<T> tool) {
        InterpreterResolvedJavaField iField = null;
        if (javaField instanceof RistrettoField rField) {
            iField = rField.getInterpreterField();
        } else if (javaField instanceof InterpreterResolvedJavaField) {
            iField = (InterpreterResolvedJavaField) javaField;
        }
        if (iField != null && isFinalField(iField, tool) && isHolderInitialized(iField)) {
            final InterpreterResolvedJavaType declaringClass = iField.getDeclaringClass();
            JavaKind kind = iField.getJavaKind();
            Object receiver;
            if (iField.isStatic()) {
                receiver = iField.getDeclaringClass().getStaticStorage(kind.isPrimitive(), iField.getInstalledLayerNum());
            } else {
                receiver = snippetReflectionProvider.asObject(declaringClass.getJavaClass(), tool.getReceiver());
            }
            JavaConstant c = readConstant(kind, receiver, iField);
            if (isFinalFieldValueConstant(iField, c, tool)) {
                return tool.foldConstant(c);
            } else {
                return null;
            }
        }
        return super.readConstantField(javaField, tool);
    }

    private JavaConstant readConstant(JavaKind kind, Object receiver, InterpreterResolvedJavaField iField) {
        return switch (kind) {
            case Boolean -> JavaConstant.forBoolean(InterpreterToVM.getFieldBoolean(receiver, iField));
            case Byte -> JavaConstant.forByte(InterpreterToVM.getFieldByte(receiver, iField));
            case Short -> JavaConstant.forShort(InterpreterToVM.getFieldShort(receiver, iField));
            case Char -> JavaConstant.forChar(InterpreterToVM.getFieldChar(receiver, iField));
            case Int -> JavaConstant.forInt(InterpreterToVM.getFieldInt(receiver, iField));
            case Long -> JavaConstant.forLong(InterpreterToVM.getFieldLong(receiver, iField));
            case Float -> JavaConstant.forFloat(InterpreterToVM.getFieldFloat(receiver, iField));
            case Double -> JavaConstant.forDouble(InterpreterToVM.getFieldDouble(receiver, iField));
            case Object -> snippetReflectionProvider.forObject(InterpreterToVM.getFieldObject(receiver, iField));
            default -> throw GraalError.shouldNotReachHere("Unknown kind: " + kind);
        };
    }

    private static boolean isHolderInitialized(InterpreterResolvedJavaField iField) {
        return iField.getDeclaringClass().isInitialized();
    }
}
