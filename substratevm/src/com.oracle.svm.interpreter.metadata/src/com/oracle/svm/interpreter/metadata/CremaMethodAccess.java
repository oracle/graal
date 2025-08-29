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
package com.oracle.svm.interpreter.metadata;

import java.util.List;

import com.oracle.svm.espresso.classfile.attributes.LineNumberTableAttribute;
import com.oracle.svm.espresso.classfile.descriptors.ByteSequence;
import com.oracle.svm.espresso.classfile.descriptors.Signature;
import com.oracle.svm.espresso.classfile.descriptors.SignatureSymbols;
import com.oracle.svm.espresso.classfile.descriptors.Symbol;
import com.oracle.svm.espresso.classfile.descriptors.Type;
import com.oracle.svm.espresso.classfile.descriptors.TypeSymbols;
import com.oracle.svm.espresso.shared.meta.MethodAccess;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.LineNumberTable;
import jdk.vm.ci.meta.UnresolvedJavaType;

public interface CremaMethodAccess extends WithModifiers, MethodAccess<InterpreterResolvedJavaType, InterpreterResolvedJavaMethod, InterpreterResolvedJavaField> {
    static LineNumberTable toJVMCI(LineNumberTableAttribute parserTable) {
        List<LineNumberTableAttribute.Entry> entries = parserTable.getEntries();
        int[] bcis = new int[entries.size()];
        int[] lineNumbers = new int[entries.size()];
        for (int i = 0; i < entries.size(); i++) {
            LineNumberTableAttribute.Entry entry = entries.get(i);
            bcis[i] = entry.getBCI();
            lineNumbers[i] = entry.getLineNumber();
        }
        return new LineNumberTable(lineNumbers, bcis);
    }

    static InterpreterUnresolvedSignature toJVMCI(Symbol<Signature> parserSignature, TypeSymbols typeSymbols) {
        Symbol<Type>[] parsed = SignatureSymbols.parse(typeSymbols, parserSignature);
        assert parsed.length > 0;
        int parameterCount = SignatureSymbols.parameterCount(parsed);
        JavaType[] parameters = new JavaType[parameterCount];
        for (int i = 0; i < parameterCount; i++) {
            parameters[i] = toJavaType(SignatureSymbols.parameterType(parsed, i));
        }
        JavaType returnType = toJavaType(SignatureSymbols.returnType(parsed));
        return InterpreterUnresolvedSignature.create(returnType, parameters);
    }

    static Symbol<Signature> toSymbol(InterpreterUnresolvedSignature jvmciSignature, SignatureSymbols signatures) {
        StringBuilder sb = new StringBuilder();
        sb.append('(');
        for (int i = 0; i < jvmciSignature.getParameterCount(false); i++) {
            sb.append(jvmciSignature.getParameterType(i, null).getName());
        }
        sb.append(')');
        sb.append(jvmciSignature.getReturnType(null).getName());
        return signatures.getOrCreateValidSignature(ByteSequence.create(sb.toString()));
    }

    static JavaType toJavaType(Symbol<Type> typeSymbol) {
        if (TypeSymbols.isPrimitive(typeSymbol)) {
            return InterpreterResolvedPrimitiveType.fromKind(JavaKind.fromPrimitiveOrVoidTypeChar((char) typeSymbol.byteAt(0)));
        } else {
            return UnresolvedJavaType.create(typeSymbol.toString());
        }
    }
}
