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

import static com.oracle.truffle.espresso.substitutions.jvmci.Target_com_oracle_truffle_espresso_jvmci_meta_EspressoMetaAccessProvider.toJVMCIInstanceType;
import static com.oracle.truffle.espresso.substitutions.jvmci.Target_jdk_vm_ci_runtime_JVMCI.checkJVMCIAvailable;

import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.substitutions.EspressoSubstitutions;
import com.oracle.truffle.espresso.substitutions.Inject;
import com.oracle.truffle.espresso.substitutions.JavaType;
import com.oracle.truffle.espresso.substitutions.Substitution;
import com.oracle.truffle.espresso.substitutions.SubstitutionNode;
import com.oracle.truffle.espresso.vm.InterpreterToVM;

@EspressoSubstitutions
final class Target_com_oracle_truffle_espresso_jvmci_meta_EspressoConstantReflectionProvider {

    private Target_com_oracle_truffle_espresso_jvmci_meta_EspressoConstantReflectionProvider() {
    }

    @Substitution(hasReceiver = true)
    abstract static class Wrap extends SubstitutionNode {
        abstract @JavaType(internalName = "Ljdk/vm/ci/meta/JavaConstant;") StaticObject execute(StaticObject self, @JavaType(Object.class) StaticObject object);

        @Specialization
        static StaticObject doDefault(@SuppressWarnings("unused") StaticObject self, StaticObject object,
                        @Bind("getContext()") EspressoContext context,
                        @Cached("create(context.getMeta().jvmci.EspressoObjectConstant_init.getCallTarget())") DirectCallNode constantObjectConstructor) {
            assert context.getLanguage().isInternalJVMCIEnabled();
            Meta meta = context.getMeta();
            if (StaticObject.isNull(object)) {
                return meta.jvmci.JavaConstant_NULL_POINTER.getObject(meta.jvmci.JavaConstant.tryInitializeAndGetStatics());
            }
            StaticObject result = meta.jvmci.EspressoObjectConstant.allocateInstance(context);
            meta.jvmci.HIDDEN_OBJECT_CONSTANT.setHiddenObject(result, object);
            constantObjectConstructor.call(result);
            return result;
        }
    }

    static StaticObject wrapEspressoObjectConstant(StaticObject object, Meta meta) {
        if (StaticObject.isNull(object)) {
            return meta.jvmci.JavaConstant_NULL_POINTER.getObject(meta.jvmci.JavaConstant.tryInitializeAndGetStatics());
        }
        StaticObject result = meta.jvmci.EspressoObjectConstant.allocateInstance(meta.getContext());
        meta.jvmci.HIDDEN_OBJECT_CONSTANT.setHiddenObject(result, object);
        meta.jvmci.EspressoObjectConstant_init.invokeDirectSpecial(result);
        return result;
    }

    @Substitution(hasReceiver = true)
    public static @JavaType(Object.class) StaticObject unwrap(@SuppressWarnings("unused") StaticObject self,
                    @JavaType(internalName = "Lcom/oracle/truffle/espresso/jvmci/meta/EspressoObjectConstant;") StaticObject wrapped,
                    @Inject EspressoContext context) {
        checkJVMCIAvailable(context.getLanguage());
        Meta meta = context.getMeta();
        return (StaticObject) meta.jvmci.HIDDEN_OBJECT_CONSTANT.getHiddenObject(wrapped);
    }

    @Substitution(hasReceiver = true)
    public static boolean readInstanceBooleanFieldValue(@SuppressWarnings("unused") StaticObject self,
                    @JavaType(internalName = "Lcom/oracle/truffle/espresso/jvmci/meta/EspressoResolvedJavaField;") StaticObject fieldMirror,
                    @JavaType(Object.class) StaticObject receiver,
                    @Inject EspressoContext context) {
        checkJVMCIAvailable(context.getLanguage());
        Meta meta = context.getMeta();
        Field field = (Field) meta.jvmci.HIDDEN_FIELD_MIRROR.getHiddenObject(fieldMirror);
        assert InterpreterToVM.instanceOf(receiver, field.getDeclaringKlass());
        return field.getBoolean(receiver);
    }

    @Substitution(hasReceiver = true)
    public static byte readInstanceByteFieldValue(@SuppressWarnings("unused") StaticObject self,
                    @JavaType(internalName = "Lcom/oracle/truffle/espresso/jvmci/meta/EspressoResolvedJavaField;") StaticObject fieldMirror,
                    @JavaType(Object.class) StaticObject receiver,
                    @Inject EspressoContext context) {
        checkJVMCIAvailable(context.getLanguage());
        Meta meta = context.getMeta();
        Field field = (Field) meta.jvmci.HIDDEN_FIELD_MIRROR.getHiddenObject(fieldMirror);
        assert InterpreterToVM.instanceOf(receiver, field.getDeclaringKlass());
        return field.getByte(receiver);
    }

    @Substitution(hasReceiver = true)
    public static short readInstanceShortFieldValue(@SuppressWarnings("unused") StaticObject self,
                    @JavaType(internalName = "Lcom/oracle/truffle/espresso/jvmci/meta/EspressoResolvedJavaField;") StaticObject fieldMirror,
                    @JavaType(Object.class) StaticObject receiver,
                    @Inject EspressoContext context) {
        checkJVMCIAvailable(context.getLanguage());
        Meta meta = context.getMeta();
        Field field = (Field) meta.jvmci.HIDDEN_FIELD_MIRROR.getHiddenObject(fieldMirror);
        assert InterpreterToVM.instanceOf(receiver, field.getDeclaringKlass());
        return field.getShort(receiver);
    }

    @Substitution(hasReceiver = true)
    public static char readInstanceCharFieldValue(@SuppressWarnings("unused") StaticObject self,
                    @JavaType(internalName = "Lcom/oracle/truffle/espresso/jvmci/meta/EspressoResolvedJavaField;") StaticObject fieldMirror,
                    @JavaType(Object.class) StaticObject receiver,
                    @Inject EspressoContext context) {
        checkJVMCIAvailable(context.getLanguage());
        Meta meta = context.getMeta();
        Field field = (Field) meta.jvmci.HIDDEN_FIELD_MIRROR.getHiddenObject(fieldMirror);
        assert InterpreterToVM.instanceOf(receiver, field.getDeclaringKlass());
        return field.getChar(receiver);
    }

    @Substitution(hasReceiver = true)
    public static int readInstanceIntFieldValue(@SuppressWarnings("unused") StaticObject self,
                    @JavaType(internalName = "Lcom/oracle/truffle/espresso/jvmci/meta/EspressoResolvedJavaField;") StaticObject fieldMirror,
                    @JavaType(Object.class) StaticObject receiver,
                    @Inject EspressoContext context) {
        checkJVMCIAvailable(context.getLanguage());
        Meta meta = context.getMeta();
        Field field = (Field) meta.jvmci.HIDDEN_FIELD_MIRROR.getHiddenObject(fieldMirror);
        assert InterpreterToVM.instanceOf(receiver, field.getDeclaringKlass());
        return field.getInt(receiver);
    }

    @Substitution(hasReceiver = true)
    public static float readInstanceFloatFieldValue(@SuppressWarnings("unused") StaticObject self,
                    @JavaType(internalName = "Lcom/oracle/truffle/espresso/jvmci/meta/EspressoResolvedJavaField;") StaticObject fieldMirror,
                    @JavaType(Object.class) StaticObject receiver,
                    @Inject EspressoContext context) {
        checkJVMCIAvailable(context.getLanguage());
        Meta meta = context.getMeta();
        Field field = (Field) meta.jvmci.HIDDEN_FIELD_MIRROR.getHiddenObject(fieldMirror);
        assert InterpreterToVM.instanceOf(receiver, field.getDeclaringKlass());
        return field.getFloat(receiver);
    }

    @Substitution(hasReceiver = true)
    public static long readInstanceLongFieldValue(@SuppressWarnings("unused") StaticObject self,
                    @JavaType(internalName = "Lcom/oracle/truffle/espresso/jvmci/meta/EspressoResolvedJavaField;") StaticObject fieldMirror,
                    @JavaType(Object.class) StaticObject receiver,
                    @Inject EspressoContext context) {
        checkJVMCIAvailable(context.getLanguage());
        Meta meta = context.getMeta();
        Field field = (Field) meta.jvmci.HIDDEN_FIELD_MIRROR.getHiddenObject(fieldMirror);
        assert InterpreterToVM.instanceOf(receiver, field.getDeclaringKlass());
        return field.getLong(receiver);
    }

    @Substitution(hasReceiver = true)
    public static double readInstanceDoubleFieldValue(@SuppressWarnings("unused") StaticObject self,
                    @JavaType(internalName = "Lcom/oracle/truffle/espresso/jvmci/meta/EspressoResolvedJavaField;") StaticObject fieldMirror,
                    @JavaType(Object.class) StaticObject receiver,
                    @Inject EspressoContext context) {
        checkJVMCIAvailable(context.getLanguage());
        Meta meta = context.getMeta();
        Field field = (Field) meta.jvmci.HIDDEN_FIELD_MIRROR.getHiddenObject(fieldMirror);
        assert InterpreterToVM.instanceOf(receiver, field.getDeclaringKlass());
        return field.getDouble(receiver);
    }

    @Substitution(hasReceiver = true)
    public static @JavaType(Object.class) StaticObject readInstanceObjectFieldValue(@SuppressWarnings("unused") StaticObject self,
                    @JavaType(internalName = "Lcom/oracle/truffle/espresso/jvmci/meta/EspressoResolvedJavaField;") StaticObject fieldMirror, @JavaType(Object.class) StaticObject receiver,
                    @Inject EspressoContext context) {
        checkJVMCIAvailable(context.getLanguage());
        Meta meta = context.getMeta();
        Field field = (Field) meta.jvmci.HIDDEN_FIELD_MIRROR.getHiddenObject(fieldMirror);
        assert InterpreterToVM.instanceOf(receiver, field.getDeclaringKlass());
        return field.getObject(receiver);
    }

    @Substitution(hasReceiver = true)
    public static boolean readStaticBooleanFieldValue(@SuppressWarnings("unused") StaticObject self,
                    @JavaType(internalName = "Lcom/oracle/truffle/espresso/jvmci/meta/EspressoResolvedJavaField;") StaticObject fieldMirror,
                    @Inject EspressoContext context) {
        checkJVMCIAvailable(context.getLanguage());
        Meta meta = context.getMeta();
        Field field = (Field) meta.jvmci.HIDDEN_FIELD_MIRROR.getHiddenObject(fieldMirror);
        assert field.getDeclaringKlass().isInitialized();
        return field.getBoolean(field.getDeclaringKlass().getStatics());
    }

    @Substitution(hasReceiver = true)
    public static byte readStaticByteFieldValue(@SuppressWarnings("unused") StaticObject self,
                    @JavaType(internalName = "Lcom/oracle/truffle/espresso/jvmci/meta/EspressoResolvedJavaField;") StaticObject fieldMirror,
                    @Inject EspressoContext context) {
        checkJVMCIAvailable(context.getLanguage());
        Meta meta = context.getMeta();
        Field field = (Field) meta.jvmci.HIDDEN_FIELD_MIRROR.getHiddenObject(fieldMirror);
        assert field.getDeclaringKlass().isInitialized();
        return field.getByte(field.getDeclaringKlass().getStatics());
    }

    @Substitution(hasReceiver = true)
    public static short readStaticShortFieldValue(@SuppressWarnings("unused") StaticObject self,
                    @JavaType(internalName = "Lcom/oracle/truffle/espresso/jvmci/meta/EspressoResolvedJavaField;") StaticObject fieldMirror,
                    @Inject EspressoContext context) {
        checkJVMCIAvailable(context.getLanguage());
        Meta meta = context.getMeta();
        Field field = (Field) meta.jvmci.HIDDEN_FIELD_MIRROR.getHiddenObject(fieldMirror);
        assert field.getDeclaringKlass().isInitialized();
        return field.getShort(field.getDeclaringKlass().getStatics());
    }

    @Substitution(hasReceiver = true)
    public static char readStaticCharFieldValue(@SuppressWarnings("unused") StaticObject self,
                    @JavaType(internalName = "Lcom/oracle/truffle/espresso/jvmci/meta/EspressoResolvedJavaField;") StaticObject fieldMirror,
                    @Inject EspressoContext context) {
        checkJVMCIAvailable(context.getLanguage());
        Meta meta = context.getMeta();
        Field field = (Field) meta.jvmci.HIDDEN_FIELD_MIRROR.getHiddenObject(fieldMirror);
        assert field.getDeclaringKlass().isInitialized();
        return field.getChar(field.getDeclaringKlass().getStatics());
    }

    @Substitution(hasReceiver = true)
    public static int readStaticIntFieldValue(@SuppressWarnings("unused") StaticObject self,
                    @JavaType(internalName = "Lcom/oracle/truffle/espresso/jvmci/meta/EspressoResolvedJavaField;") StaticObject fieldMirror,
                    @Inject EspressoContext context) {
        checkJVMCIAvailable(context.getLanguage());
        Meta meta = context.getMeta();
        Field field = (Field) meta.jvmci.HIDDEN_FIELD_MIRROR.getHiddenObject(fieldMirror);
        assert field.getDeclaringKlass().isInitialized();
        return field.getInt(field.getDeclaringKlass().getStatics());
    }

    @Substitution(hasReceiver = true)
    public static float readStaticFloatFieldValue(@SuppressWarnings("unused") StaticObject self,
                    @JavaType(internalName = "Lcom/oracle/truffle/espresso/jvmci/meta/EspressoResolvedJavaField;") StaticObject fieldMirror,
                    @Inject EspressoContext context) {
        checkJVMCIAvailable(context.getLanguage());
        Meta meta = context.getMeta();
        Field field = (Field) meta.jvmci.HIDDEN_FIELD_MIRROR.getHiddenObject(fieldMirror);
        assert field.getDeclaringKlass().isInitialized();
        return field.getFloat(field.getDeclaringKlass().getStatics());
    }

    @Substitution(hasReceiver = true)
    public static long readStaticLongFieldValue(@SuppressWarnings("unused") StaticObject self,
                    @JavaType(internalName = "Lcom/oracle/truffle/espresso/jvmci/meta/EspressoResolvedJavaField;") StaticObject fieldMirror,
                    @Inject EspressoContext context) {
        checkJVMCIAvailable(context.getLanguage());
        Meta meta = context.getMeta();
        Field field = (Field) meta.jvmci.HIDDEN_FIELD_MIRROR.getHiddenObject(fieldMirror);
        assert field.getDeclaringKlass().isInitialized();
        return field.getLong(field.getDeclaringKlass().getStatics());
    }

    @Substitution(hasReceiver = true)
    public static double readStaticDoubleFieldValue(@SuppressWarnings("unused") StaticObject self,
                    @JavaType(internalName = "Lcom/oracle/truffle/espresso/jvmci/meta/EspressoResolvedJavaField;") StaticObject fieldMirror,
                    @Inject EspressoContext context) {
        checkJVMCIAvailable(context.getLanguage());
        Meta meta = context.getMeta();
        Field field = (Field) meta.jvmci.HIDDEN_FIELD_MIRROR.getHiddenObject(fieldMirror);
        assert field.getDeclaringKlass().isInitialized();
        return field.getDouble(field.getDeclaringKlass().getStatics());
    }

    @Substitution(hasReceiver = true)
    public static @JavaType(Object.class) StaticObject readStaticObjectFieldValue(@SuppressWarnings("unused") StaticObject self,
                    @JavaType(internalName = "Lcom/oracle/truffle/espresso/jvmci/meta/EspressoResolvedJavaField;") StaticObject fieldMirror,
                    @Inject EspressoContext context) {
        checkJVMCIAvailable(context.getLanguage());
        Meta meta = context.getMeta();
        Field field = (Field) meta.jvmci.HIDDEN_FIELD_MIRROR.getHiddenObject(fieldMirror);
        assert field.getDeclaringKlass().isInitialized();
        return field.getObject(field.getDeclaringKlass().getStatics());
    }

    @Substitution(hasReceiver = true)
    abstract static class GetTypeForStaticBase extends SubstitutionNode {
        abstract @JavaType(internalName = "Lcom/oracle/truffle/espresso/jvmci/meta/EspressoResolvedInstanceType;") StaticObject execute(StaticObject self,
                        @JavaType(internalName = "Lcom/oracle/truffle/espresso/jvmci/meta/EspressoObjectConstant;") StaticObject staticBaseMirror);

        @Specialization
        static StaticObject doDefault(@SuppressWarnings("unused") StaticObject self, StaticObject staticBaseMirror,
                        @Bind("getContext()") EspressoContext context,
                        @Cached("create(context.getMeta().jvmci.EspressoResolvedInstanceType_init.getCallTarget())") DirectCallNode objectTypeConstructor) {
            checkJVMCIAvailable(context.getLanguage());
            Meta meta = context.getMeta();
            StaticObject staticBase = (StaticObject) meta.jvmci.HIDDEN_OBJECT_CONSTANT.getHiddenObject(staticBaseMirror);
            if (!staticBase.isStaticStorage()) {
                return StaticObject.NULL;
            }
            ObjectKlass klass = (ObjectKlass) staticBase.getKlass();
            return toJVMCIInstanceType(klass, objectTypeConstructor, context, meta);
        }
    }
}
