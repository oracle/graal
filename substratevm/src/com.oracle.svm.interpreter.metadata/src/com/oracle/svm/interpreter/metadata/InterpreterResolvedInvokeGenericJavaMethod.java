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
package com.oracle.svm.interpreter.metadata;

import static com.oracle.svm.espresso.classfile.Constants.REF_invokeVirtual;

import com.oracle.svm.core.graal.code.PreparedSignature;
import com.oracle.svm.core.invoke.Target_java_lang_invoke_MemberName;
import com.oracle.svm.core.methodhandles.Target_java_lang_invoke_MethodHandleNatives;
import com.oracle.svm.espresso.classfile.descriptors.Name;
import com.oracle.svm.espresso.classfile.descriptors.Signature;
import com.oracle.svm.espresso.classfile.descriptors.Symbol;
import com.oracle.svm.espresso.shared.meta.SignaturePolymorphicIntrinsic;

import jdk.vm.ci.meta.JavaKind;

/**
 * Resolved representation of a signature-polymorphic
 * {@link SignaturePolymorphicIntrinsic#InvokeGeneric} method after
 * {@link Target_java_lang_invoke_MethodHandleNatives#linkMethod} has produced the direct invoker
 * and optional appendix needed to invoke this method.
 * <p>
 * Instances of this class only ever appear as {@linkplain InterpreterConstantPool#resolvedMethodAt
 * resolved method constants} in runtime constant pools of run-time-loaded types.
 */
public final class InterpreterResolvedInvokeGenericJavaMethod extends InterpreterResolvedJavaMethod {
    private final InterpreterResolvedJavaMethod invoker;
    private final Object appendix;
    private final JavaKind unbasicTo;

    private InterpreterResolvedInvokeGenericJavaMethod(Symbol<Name> name, int maxLocals, int flags,
                    InterpreterResolvedObjectType declaringClass, InterpreterUnresolvedSignature signature, Symbol<Signature> signatureSymbol,
                    int vtableIndex, int gotOffset, int enterStubOffset, int methodId, InterpreterResolvedJavaMethod invoker, Object appendix, JavaKind unbasicTo,
                    PreparedSignature preparedSignature) {
        super(name, maxLocals, flags, declaringClass, signature, signatureSymbol, vtableIndex, gotOffset, enterStubOffset, methodId, SignaturePolymorphicIntrinsic.InvokeGeneric, preparedSignature);
        this.invoker = invoker;
        this.appendix = appendix;
        this.unbasicTo = unbasicTo;
    }

    public static InterpreterResolvedInvokeGenericJavaMethod linkInvokeGeneric(InterpreterResolvedJavaMethod invokeGeneric, InterpreterResolvedObjectType accessingClass) {
        assert invokeGeneric.getSignaturePolymorphicIntrinsic() == SignaturePolymorphicIntrinsic.InvokeGeneric;
        Object[] appendixBox = new Object[1];
        Target_java_lang_invoke_MemberName invokerMemberName = Target_java_lang_invoke_MethodHandleNatives.linkMethod(accessingClass.getJavaClass(), REF_invokeVirtual,
                        invokeGeneric.getDeclaringClass().getJavaClass(), invokeGeneric.getName(), invokeGeneric.getSymbolicSignature().toString(), appendixBox);
        InterpreterResolvedJavaMethod invoker = InterpreterResolvedJavaMethod.fromMemberName(invokerMemberName);
        JavaKind unbasicTo = null;
        if (invoker.getSignature().getReturnKind() != invokeGeneric.getSignature().getReturnKind()) {
            unbasicTo = invokeGeneric.getSignature().getReturnKind();
        }
        return new InterpreterResolvedInvokeGenericJavaMethod(invokeGeneric.getSymbolicName(), invokeGeneric.getMaxLocals(), invokeGeneric.getFlags(),
                        invokeGeneric.getDeclaringClass(), invokeGeneric.getSignature(), invokeGeneric.getSymbolicSignature(),
                        invokeGeneric.getVTableIndex(), invokeGeneric.getGOTOffset(), invokeGeneric.getEnterStubOffset(), invokeGeneric.getMethodId(), invoker, appendixBox[0], unbasicTo,
                        invokeGeneric.getPreparedSignature());
    }

    public InterpreterResolvedJavaMethod getInvoker() {
        return invoker;
    }

    public Object getAppendix() {
        return appendix;
    }

    public JavaKind unbasicTo() {
        return unbasicTo;
    }
}
