/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.truffle.espresso.substitutions.jvmci;

import static com.oracle.truffle.espresso.jvmci.JVMCIUtils.LOGGER;
import static com.oracle.truffle.espresso.substitutions.jvmci.Target_com_oracle_truffle_espresso_jvmci_meta_EspressoMetaAccessProvider.toJVMCIInstanceType;
import static com.oracle.truffle.espresso.substitutions.jvmci.Target_com_oracle_truffle_espresso_jvmci_meta_EspressoResolvedInstanceType.toJVMCIMethod;

import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.MethodHandleIntrinsics;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.substitutions.EspressoSubstitutions;
import com.oracle.truffle.espresso.substitutions.Inject;
import com.oracle.truffle.espresso.substitutions.JavaType;
import com.oracle.truffle.espresso.substitutions.Substitution;
import com.oracle.truffle.espresso.vm.InterpreterToVM;

@EspressoSubstitutions
final class Target_com_oracle_truffle_espresso_jvmci_meta_EspressoMethodHandleAccessProvider {

    private Target_com_oracle_truffle_espresso_jvmci_meta_EspressoMethodHandleAccessProvider() {
    }

    @Substitution(hasReceiver = true)
    public static @JavaType(internalName = "Ljdk/vm/ci/meta/MethodHandleAccessProvider$IntrinsicMethod;") StaticObject lookupMethodHandleIntrinsic0(
                    @SuppressWarnings("unused") StaticObject self,
                    @JavaType(internalName = "Lcom/oracle/truffle/espresso/jvmci/meta/EspressoResolvedJavaMethod;") StaticObject jvmciMethod,
                    @Inject EspressoContext context) {
        assert context.getLanguage().isInternalJVMCIEnabled();
        Meta meta = context.getMeta();
        if (StaticObject.isNull(jvmciMethod)) {
            meta.throwNullPointerExceptionBoundary();
        }
        Method method = (Method) meta.jvmci.HIDDEN_METHOD_MIRROR.getHiddenObject(jvmciMethod);
        return switch (MethodHandleIntrinsics.getId(method)) {
            case None, InvokeGeneric -> StaticObject.NULL;
            case InvokeBasic -> meta.jvmci.IntrinsicMethod_INVOKE_BASIC;
            case LinkToVirtual -> meta.jvmci.IntrinsicMethod_LINK_TO_VIRTUAL;
            case LinkToStatic -> meta.jvmci.IntrinsicMethod_LINK_TO_STATIC;
            case LinkToSpecial -> meta.jvmci.IntrinsicMethod_LINK_TO_SPECIAL;
            case LinkToInterface -> meta.jvmci.IntrinsicMethod_LINK_TO_INTERFACE;
            case LinkToNative -> meta.jvmci.IntrinsicMethod_LINK_TO_NATIVE == null ? StaticObject.NULL : meta.jvmci.IntrinsicMethod_LINK_TO_NATIVE;
        };
    }

    @Substitution(hasReceiver = true)
    public static @JavaType(internalName = "Lcom/oracle/truffle/espresso/jvmci/meta/EspressoResolvedJavaMethod;") StaticObject resolveInvokeBasicTarget0(@SuppressWarnings("unused") StaticObject self,
                    @JavaType(internalName = "Lcom/oracle/truffle/espresso/jvmci/meta/EspressoObjectConstant;") StaticObject methodHandleMirror, boolean forceBytecodeGeneration,
                    @Inject EspressoContext context) {
        assert context.getLanguage().isInternalJVMCIEnabled();
        Meta meta = context.getMeta();
        if (StaticObject.isNull(methodHandleMirror)) {
            meta.throwNullPointerExceptionBoundary();
        }
        StaticObject methodHandle = (StaticObject) meta.jvmci.HIDDEN_OBJECT_CONSTANT.getHiddenObject(methodHandleMirror);
        if (!InterpreterToVM.instanceOf(methodHandle, meta.java_lang_invoke_MethodHandle)) {
            LOGGER.info(() -> "EMHAP.resolveInvokeBasicTarget0 not a MethodHandle");
            return StaticObject.NULL;
        }
        StaticObject form = meta.java_lang_invoke_MethodHandle_form.getObject(methodHandle);
        if (StaticObject.isNull(form)) {
            LOGGER.fine(() -> "EMHAP.resolveInvokeBasicTarget0 no form");
            return StaticObject.NULL;
        }
        StaticObject memberName = meta.java_lang_invoke_LambdaForm_vmentry.getObject(form);
        if (StaticObject.isNull(memberName)) {
            if (forceBytecodeGeneration) {
                LOGGER.fine(() -> "EMHAP.resolveInvokeBasicTarget0 compiling vmentry");
                meta.java_lang_invoke_LambdaForm_compileToBytecode.invokeDirectVirtual(form);
                memberName = meta.java_lang_invoke_LambdaForm_vmentry.getObject(form);
            } else {
                LOGGER.fine(() -> "EMHAP.resolveInvokeBasicTarget0 no vmentry");
                return StaticObject.NULL;
            }
        }
        Method target = (Method) meta.HIDDEN_VMTARGET.getHiddenObject(memberName);
        LOGGER.finer(() -> "EMHAP.resolveInvokeBasicTarget0 found " + target);
        StaticObject holder = toJVMCIInstanceType(target.getDeclaringKlass(), meta);
        return toJVMCIMethod(target, holder, meta);
    }

    @Substitution(hasReceiver = true)
    public static @JavaType(internalName = "Lcom/oracle/truffle/espresso/jvmci/meta/EspressoResolvedJavaMethod;") StaticObject resolveLinkToTarget0(@SuppressWarnings("unused") StaticObject self,
                    @JavaType(internalName = "Lcom/oracle/truffle/espresso/jvmci/meta/EspressoObjectConstant;") StaticObject memberNameMirror,
                    @Inject EspressoContext context) {
        assert context.getLanguage().isInternalJVMCIEnabled();
        Meta meta = context.getMeta();
        if (StaticObject.isNull(memberNameMirror)) {
            meta.throwNullPointerExceptionBoundary();
        }
        StaticObject memberName = (StaticObject) meta.jvmci.HIDDEN_OBJECT_CONSTANT.getHiddenObject(memberNameMirror);
        if (!InterpreterToVM.instanceOf(memberName, meta.java_lang_invoke_MemberName)) {
            throw meta.throwIllegalArgumentExceptionBoundary("Constant is not a MemberName");
        }
        Method target = (Method) meta.HIDDEN_VMTARGET.getHiddenObject(memberName);
        LOGGER.finer(() -> "EMHAP.resolveLinkToTarget0 found " + target);
        StaticObject holder = toJVMCIInstanceType(target.getDeclaringKlass(), meta);
        return toJVMCIMethod(target, holder, meta);
    }
}
