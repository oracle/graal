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

import static com.oracle.truffle.espresso.substitutions.jvmci.Target_com_oracle_truffle_espresso_jvmci_meta_EspressoResolvedInstanceType.toJVMCIField;
import static com.oracle.truffle.espresso.substitutions.jvmci.Target_com_oracle_truffle_espresso_jvmci_meta_EspressoResolvedInstanceType.toJVMCIMethod;

import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.espresso.classfile.JavaKind;
import com.oracle.truffle.espresso.classfile.descriptors.ByteSequence;
import com.oracle.truffle.espresso.classfile.descriptors.Validation;
import com.oracle.truffle.espresso.impl.ArrayKlass;
import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.nodes.bytecodes.InitCheck;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.substitutions.EspressoSubstitutions;
import com.oracle.truffle.espresso.substitutions.JavaType;
import com.oracle.truffle.espresso.substitutions.Substitution;
import com.oracle.truffle.espresso.substitutions.SubstitutionNode;

@EspressoSubstitutions
final class Target_com_oracle_truffle_espresso_jvmci_meta_EspressoMetaAccessProvider {

    private Target_com_oracle_truffle_espresso_jvmci_meta_EspressoMetaAccessProvider() {
    }

    @Substitution(hasReceiver = true)
    abstract static class LookupJavaType extends SubstitutionNode {
        abstract @JavaType(internalName = "Lcom/oracle/truffle/espresso/jvmci/meta/EspressoResolvedInstanceType;") StaticObject execute(StaticObject self, @JavaType(Class.class) StaticObject clazz);

        @Specialization
        static StaticObject doDefault(@SuppressWarnings("unused") StaticObject self, @JavaType(Class.class) StaticObject clazz,
                        @Bind("getContext()") EspressoContext context,
                        @Cached("create(context.getMeta().jvmci.EspressoResolvedInstanceType_init.getCallTarget())") DirectCallNode objectTypeConstructor,
                        @Cached("create(context.getMeta().jvmci.EspressoResolvedArrayType_init.getCallTarget())") DirectCallNode arrayTypeConstructor,
                        @Cached("create(context.getMeta().jvmci.EspressoResolvedPrimitiveType_forBasicType.getCallTarget())") DirectCallNode forBasicType,
                        @Cached InitCheck initCheck) {
            assert context.getLanguage().isInternalJVMCIEnabled();
            Meta meta = context.getMeta();
            if (StaticObject.isNull(clazz)) {
                throw meta.throwIllegalArgumentExceptionBoundary("Class parameter was null");
            }
            Klass klass = clazz.getMirrorKlass(meta);
            return toJVMCIType(klass, objectTypeConstructor, arrayTypeConstructor, forBasicType, initCheck, context, meta);
        }
    }

    static StaticObject toJVMCIType(Klass klass, DirectCallNode objectTypeConstructor, DirectCallNode arrayTypeConstructor, DirectCallNode forBasicType, InitCheck initCheck, EspressoContext context,
                    Meta meta) {
        if (klass.isArray()) {
            StaticObject jvmciMirror = meta.jvmci.EspressoResolvedArrayType.allocateInstance(context);
            ArrayKlass arrayKlass = (ArrayKlass) klass;
            arrayTypeConstructor.call(jvmciMirror, toJVMCIElementalType(arrayKlass.getElementalType(), objectTypeConstructor, forBasicType, initCheck, context, meta), arrayKlass.getDimension(),
                            arrayKlass.mirror());
            return jvmciMirror;
        } else {
            return toJVMCIElementalType(klass, objectTypeConstructor, forBasicType, initCheck, context, meta);
        }
    }

    static StaticObject toJVMCIObjectType(Klass klass, DirectCallNode objectTypeConstructor, DirectCallNode arrayTypeConstructor, DirectCallNode forBasicType, InitCheck initCheck,
                    EspressoContext context,
                    Meta meta) {
        if (klass.isArray()) {
            StaticObject jvmciMirror = meta.jvmci.EspressoResolvedArrayType.allocateInstance(context);
            ArrayKlass arrayKlass = (ArrayKlass) klass;
            arrayTypeConstructor.call(jvmciMirror, toJVMCIElementalType(arrayKlass.getElementalType(), objectTypeConstructor, forBasicType, initCheck, context, meta), arrayKlass.getDimension(),
                            arrayKlass.mirror());
            return jvmciMirror;
        } else {
            return toJVMCIInstanceType((ObjectKlass) klass, objectTypeConstructor, context, meta);
        }
    }

    static StaticObject toJVMCIObjectType(Klass klass, Meta meta) {
        if (klass.isArray()) {
            StaticObject jvmciMirror = meta.jvmci.EspressoResolvedArrayType.allocateInstance(meta.getContext());
            ArrayKlass arrayKlass = (ArrayKlass) klass;
            meta.jvmci.EspressoResolvedArrayType_init.invokeDirectSpecial(jvmciMirror, toJVMCIElementalType(arrayKlass.getElementalType(), meta), arrayKlass.getDimension(), arrayKlass.mirror());
            return jvmciMirror;
        } else {
            return toJVMCIInstanceType((ObjectKlass) klass, meta);
        }
    }

    static StaticObject toJVMCIElementalType(Klass klass, DirectCallNode objectTypeConstructor, DirectCallNode forBasicType, InitCheck initCheck, EspressoContext context, Meta meta) {
        if (klass.isPrimitive()) {
            return toJVMCIPrimitiveType(klass.getJavaKind(), forBasicType, initCheck, meta);
        } else {
            return toJVMCIInstanceType((ObjectKlass) klass, objectTypeConstructor, context, meta);
        }
    }

    static StaticObject toJVMCIElementalType(Klass klass, Meta meta) {
        if (klass.isPrimitive()) {
            return toJVMCIPrimitiveType(klass.getJavaKind(), meta);
        } else {
            return toJVMCIInstanceType((ObjectKlass) klass, meta);
        }
    }

    static StaticObject toJVMCIPrimitiveType(JavaKind kind, DirectCallNode forBasicType, InitCheck initCheck, Meta meta) {
        initCheck.execute(meta.jvmci.EspressoResolvedPrimitiveType);
        StaticObject result = (StaticObject) forBasicType.call(kind.getBasicType());
        assert !StaticObject.isNull(result);
        return result;
    }

    static StaticObject toJVMCIPrimitiveType(JavaKind kind, Meta meta) {
        meta.jvmci.EspressoResolvedPrimitiveType.initialize();
        StaticObject result = (StaticObject) meta.jvmci.EspressoResolvedPrimitiveType_forBasicType.invokeDirectStatic(kind.getBasicType());
        assert !StaticObject.isNull(result);
        return result;
    }

    static StaticObject toJVMCIInstanceType(ObjectKlass klass, DirectCallNode objectTypeConstructor, EspressoContext context, Meta meta) {
        StaticObject jvmciMirror = meta.jvmci.EspressoResolvedInstanceType.allocateInstance(context);
        meta.jvmci.HIDDEN_OBJECTKLASS_MIRROR.setHiddenObject(jvmciMirror, klass);
        objectTypeConstructor.call(jvmciMirror);
        return jvmciMirror;
    }

    static StaticObject toJVMCIInstanceType(ObjectKlass klass, Meta meta) {
        StaticObject jvmciMirror = meta.jvmci.EspressoResolvedInstanceType.allocateInstance(meta.getContext());
        meta.jvmci.HIDDEN_OBJECTKLASS_MIRROR.setHiddenObject(jvmciMirror, klass);
        meta.jvmci.EspressoResolvedInstanceType_init.invokeDirectSpecial(jvmciMirror);
        return jvmciMirror;
    }

    static StaticObject toJVMCIUnresolvedType(ByteSequence symbol, DirectCallNode createUnresolved, Meta meta) {
        assert Validation.validTypeDescriptor(symbol, true);
        assert (symbol.byteAt(0) == 'L' && symbol.byteAt(symbol.length() - 1) == ';') || symbol.byteAt(0) == '[' : symbol;
        return (StaticObject) createUnresolved.call(meta.toGuestString(symbol));
    }

    static StaticObject toJVMCIUnresolvedType(ByteSequence symbol, Meta meta) {
        assert (symbol.byteAt(0) == 'L' && symbol.byteAt(symbol.length() - 1) == ';') || symbol.byteAt(0) == '[';
        return (StaticObject) meta.jvmci.UnresolvedJavaType_create.invokeDirectStatic(meta.toGuestString(symbol));
    }

    @Substitution(hasReceiver = true)
    abstract static class LookupJavaField extends SubstitutionNode {
        abstract @JavaType(internalName = "Lcom/oracle/truffle/espresso/jvmci/meta/EspressoResolvedJavaField;") StaticObject execute(StaticObject self,
                        @JavaType(java.lang.reflect.Field.class) StaticObject reflectionField);

        @Specialization
        static StaticObject doDefault(@SuppressWarnings("unused") StaticObject self, @JavaType(Class.class) StaticObject reflectionField,
                        @Bind("getContext()") EspressoContext context,
                        @Cached("create(context.getMeta().jvmci.EspressoResolvedJavaField_init.getCallTarget())") DirectCallNode fieldConstructor,
                        @Cached("create(context.getMeta().jvmci.EspressoResolvedInstanceType_init.getCallTarget())") DirectCallNode objectTypeConstructor) {
            assert context.getLanguage().isInternalJVMCIEnabled();
            Meta meta = context.getMeta();
            if (StaticObject.isNull(reflectionField)) {
                throw meta.throwNullPointerExceptionBoundary();
            }
            Field field = Field.getReflectiveFieldRoot(reflectionField, meta);
            StaticObject holderMirror = toJVMCIInstanceType(field.getDeclaringKlass(), objectTypeConstructor, context, meta);
            return toJVMCIField(field, holderMirror, fieldConstructor, context, meta);
        }
    }

    @Substitution(hasReceiver = true)
    abstract static class LookupMethod extends SubstitutionNode {
        abstract @JavaType(internalName = "Lcom/oracle/truffle/espresso/jvmci/meta/EspressoResolvedJavaMethod;") StaticObject execute(StaticObject self,
                        @JavaType(java.lang.reflect.Method.class) StaticObject reflectionObject);

        @Specialization
        static StaticObject doDefault(@SuppressWarnings("unused") StaticObject self, @JavaType(Class.class) StaticObject reflectionObject,
                        @Bind("getContext()") EspressoContext context,
                        @Cached("create(context.getMeta().jvmci.EspressoResolvedJavaMethod_init.getCallTarget())") DirectCallNode methodConstructor,
                        @Cached("create(context.getMeta().jvmci.EspressoResolvedInstanceType_init.getCallTarget())") DirectCallNode objectTypeConstructor) {
            assert context.getLanguage().isInternalJVMCIEnabled();
            Meta meta = context.getMeta();
            if (StaticObject.isNull(reflectionObject)) {
                throw meta.throwNullPointerExceptionBoundary();
            }
            Method method = Method.getHostReflectiveMethodRoot(reflectionObject, meta);
            StaticObject holderMirror = toJVMCIInstanceType(method.getDeclaringKlass(), objectTypeConstructor, context, meta);
            return toJVMCIMethod(method, holderMirror, methodConstructor, context, meta);
        }
    }

    @Substitution(hasReceiver = true)
    abstract static class LookupConstructor extends SubstitutionNode {
        abstract @JavaType(internalName = "Lcom/oracle/truffle/espresso/jvmci/meta/EspressoResolvedJavaMethod;") StaticObject execute(StaticObject self,
                        @JavaType(java.lang.reflect.Constructor.class) StaticObject reflectionObject);

        @Specialization
        static StaticObject doDefault(@SuppressWarnings("unused") StaticObject self, @JavaType(Class.class) StaticObject reflectionObject,
                        @Bind("getContext()") EspressoContext context,
                        @Cached("create(context.getMeta().jvmci.EspressoResolvedJavaMethod_init.getCallTarget())") DirectCallNode methodConstructor,
                        @Cached("create(context.getMeta().jvmci.EspressoResolvedInstanceType_init.getCallTarget())") DirectCallNode objectTypeConstructor) {
            assert context.getLanguage().isInternalJVMCIEnabled();
            Meta meta = context.getMeta();
            if (StaticObject.isNull(reflectionObject)) {
                throw meta.throwNullPointerExceptionBoundary();
            }
            Method method = Method.getHostReflectiveConstructorRoot(reflectionObject, meta);
            StaticObject holderMirror = toJVMCIInstanceType(method.getDeclaringKlass(), objectTypeConstructor, context, meta);
            return toJVMCIMethod(method, holderMirror, methodConstructor, context, meta);
        }
    }
}
