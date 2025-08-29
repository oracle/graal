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

import static com.oracle.truffle.espresso.descriptors.EspressoSymbols.Names;
import static com.oracle.truffle.espresso.jvmci.JVMCIUtils.LOGGER;
import static com.oracle.truffle.espresso.jvmci.JVMCIUtils.findType;
import static com.oracle.truffle.espresso.substitutions.jvmci.Target_com_oracle_truffle_espresso_jvmci_meta_EspressoMetaAccessProvider.toJVMCIType;
import static com.oracle.truffle.espresso.substitutions.jvmci.Target_jdk_vm_ci_runtime_JVMCI.checkJVMCIAvailable;

import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.nodes.bytecodes.InitCheck;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.substitutions.EspressoSubstitutions;
import com.oracle.truffle.espresso.substitutions.Inject;
import com.oracle.truffle.espresso.substitutions.JavaType;
import com.oracle.truffle.espresso.substitutions.Substitution;
import com.oracle.truffle.espresso.substitutions.SubstitutionNode;
import com.oracle.truffle.espresso.substitutions.standard.Target_sun_misc_Unsafe;

@EspressoSubstitutions
final class Target_com_oracle_truffle_espresso_jvmci_meta_EspressoResolvedJavaField {

    private Target_com_oracle_truffle_espresso_jvmci_meta_EspressoResolvedJavaField() {
    }

    @Substitution(hasReceiver = true)
    public static int getOffset(StaticObject self, @Inject EspressoContext context, @Inject EspressoLanguage language) {
        assert context.getLanguage().isInternalJVMCIEnabled();
        Meta meta = context.getMeta();
        Field field = (Field) meta.jvmci.HIDDEN_FIELD_MIRROR.getHiddenObject(self);
        return Target_sun_misc_Unsafe.getGuestFieldOffset(field, language);
    }

    @Substitution(hasReceiver = true)
    public static @JavaType(String.class) StaticObject getName0(StaticObject self, @Inject EspressoContext context) {
        assert context.getLanguage().isInternalJVMCIEnabled();
        Meta meta = context.getMeta();
        Field field = (Field) meta.jvmci.HIDDEN_FIELD_MIRROR.getHiddenObject(self);
        return meta.toGuestString(field.getName());
    }

    @Substitution(hasReceiver = true)
    public static int getFlags(StaticObject self, @Inject EspressoContext context) {
        assert context.getLanguage().isInternalJVMCIEnabled();
        Meta meta = context.getMeta();
        Field field = (Field) meta.jvmci.HIDDEN_FIELD_MIRROR.getHiddenObject(self);
        return field.getFlags();
    }

    @Substitution(hasReceiver = true)
    public static boolean equals0(StaticObject self, @JavaType(internalName = "Lcom/oracle/truffle/espresso/jvmci/meta/EspressoResolvedJavaField;") StaticObject that,
                    @Inject EspressoContext context) {
        assert context.getLanguage().isInternalJVMCIEnabled();
        Meta meta = context.getMeta();
        if (StaticObject.isNull(that)) {
            throw meta.throwNullPointerExceptionBoundary();
        }
        Field selfField = (Field) meta.jvmci.HIDDEN_FIELD_MIRROR.getHiddenObject(self);
        Field thatField = (Field) meta.jvmci.HIDDEN_FIELD_MIRROR.getHiddenObject(that);
        return selfField == thatField; // assumes Field.equals is Object.equals
    }

    @Substitution(hasReceiver = true)
    public static int hashCode(StaticObject self, @Inject EspressoContext context) {
        assert context.getLanguage().isInternalJVMCIEnabled();
        Meta meta = context.getMeta();
        Field field = (Field) meta.jvmci.HIDDEN_FIELD_MIRROR.getHiddenObject(self);
        return System.identityHashCode(field); // assumes Field.hashCode is Object.hashCode
    }

    @Substitution(hasReceiver = true)
    abstract static class GetType0 extends SubstitutionNode {
        abstract @JavaType(internalName = "Ljdk/vm/ci/meta/JavaType;") StaticObject execute(StaticObject self, @JavaType(internalName = "Ljdk/vm/ci/meta/UnresolvedJavaType;") StaticObject unresolved);

        @Specialization
        static StaticObject doDefault(StaticObject self, StaticObject unresolved,
                        @Bind("getContext()") EspressoContext context,
                        @Cached("create(context.getMeta().jvmci.EspressoResolvedInstanceType_init.getCallTarget())") DirectCallNode objectTypeConstructor,
                        @Cached("create(context.getMeta().jvmci.EspressoResolvedArrayType_init.getCallTarget())") DirectCallNode arrayTypeConstructor,
                        @Cached("create(context.getMeta().jvmci.EspressoResolvedPrimitiveType_forBasicType.getCallTarget())") DirectCallNode forBasicType,
                        @Cached("create(context.getMeta().jvmci.UnresolvedJavaType_create.getCallTarget())") DirectCallNode createUnresolved,
                        @Cached InitCheck initCheck) {
            assert context.getLanguage().isInternalJVMCIEnabled();
            Meta meta = context.getMeta();
            Field field = (Field) meta.jvmci.HIDDEN_FIELD_MIRROR.getHiddenObject(self);
            Klass klass = findType(field.getType(), field.getDeclaringKlass(), false, meta);
            if (klass != null) {
                LOGGER.finer(() -> "ERJF.getType0 found " + klass);
                return toJVMCIType(klass, objectTypeConstructor, arrayTypeConstructor, forBasicType, initCheck, context, meta);
            } else if (StaticObject.isNull(unresolved)) {
                return (StaticObject) createUnresolved.call(meta.toGuestString(field.getType()));
            } else {
                assert field.getType().toString().equals(meta.toHostString(meta.jvmci.UnresolvedJavaType_name.getObject(unresolved)));
                return unresolved;
            }
        }
    }

    @Substitution(hasReceiver = true)
    public static @JavaType(java.lang.reflect.Field.class) StaticObject getMirror0(StaticObject self,
                    @Inject EspressoContext context) {
        checkJVMCIAvailable(context.getLanguage());
        Meta meta = context.getMeta();
        Field field = (Field) meta.jvmci.HIDDEN_FIELD_MIRROR.getHiddenObject(self);
        return field.makeMirror(meta);
    }

    @Substitution(hasReceiver = true)
    public static int getConstantValueIndex(StaticObject self, @Inject EspressoContext context) {
        assert context.getLanguage().isInternalJVMCIEnabled();
        Meta meta = context.getMeta();
        Field field = (Field) meta.jvmci.HIDDEN_FIELD_MIRROR.getHiddenObject(self);
        return field.getConstantValueIndex();
    }

    @Substitution(hasReceiver = true)
    public static boolean hasAnnotations(StaticObject self, @Inject EspressoContext context) {
        assert context.getLanguage().isInternalJVMCIEnabled();
        Meta meta = context.getMeta();
        Field field = (Field) meta.jvmci.HIDDEN_FIELD_MIRROR.getHiddenObject(self);
        return field.getAttribute(Names.RuntimeVisibleAnnotations) != null;
    }
}
