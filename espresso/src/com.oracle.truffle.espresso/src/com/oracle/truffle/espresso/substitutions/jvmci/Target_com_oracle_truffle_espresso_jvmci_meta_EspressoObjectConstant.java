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

import static com.oracle.truffle.espresso.substitutions.jvmci.Target_com_oracle_truffle_espresso_jvmci_meta_EspressoMetaAccessProvider.toJVMCIObjectType;

import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.nodes.bytecodes.InitCheck;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.substitutions.EspressoSubstitutions;
import com.oracle.truffle.espresso.substitutions.Inject;
import com.oracle.truffle.espresso.substitutions.JavaType;
import com.oracle.truffle.espresso.substitutions.Substitution;
import com.oracle.truffle.espresso.substitutions.SubstitutionNode;

@EspressoSubstitutions
final class Target_com_oracle_truffle_espresso_jvmci_meta_EspressoObjectConstant {

    private Target_com_oracle_truffle_espresso_jvmci_meta_EspressoObjectConstant() {
    }

    @Substitution(hasReceiver = true)
    abstract static class GetType extends SubstitutionNode {
        abstract @JavaType(internalName = "Lcom/oracle/truffle/espresso/jvmci/meta/EspressoResolvedObjectType;") StaticObject execute(StaticObject self);

        @Specialization
        static StaticObject doDefault(StaticObject self,
                        @Bind("getContext()") EspressoContext context,
                        @Cached("create(context.getMeta().jvmci.EspressoResolvedInstanceType_init.getCallTarget())") DirectCallNode objectTypeConstructor,
                        @Cached("create(context.getMeta().jvmci.EspressoResolvedArrayType_init.getCallTarget())") DirectCallNode arrayTypeConstructor,
                        @Cached("create(context.getMeta().jvmci.EspressoResolvedPrimitiveType_forBasicType.getCallTarget())") DirectCallNode forBasicType,
                        @Cached InitCheck initCheck) {
            assert context.getLanguage().isInternalJVMCIEnabled();
            Meta meta = context.getMeta();
            StaticObject object = (StaticObject) meta.jvmci.HIDDEN_OBJECT_CONSTANT.getHiddenObject(self);
            return toJVMCIObjectType(object.getKlass(), objectTypeConstructor, arrayTypeConstructor, forBasicType, initCheck, context, meta);
        }
    }

    @Substitution(hasReceiver = true)
    public static boolean equals0(StaticObject self, @JavaType(internalName = "Lcom/oracle/truffle/espresso/jvmci/meta/EspressoObjectConstant;") StaticObject that, @Inject EspressoContext context) {
        assert context.getLanguage().isInternalJVMCIEnabled();
        Meta meta = context.getMeta();
        if (StaticObject.isNull(that)) {
            throw meta.throwNullPointerExceptionBoundary();
        }
        StaticObject selfObject = (StaticObject) meta.jvmci.HIDDEN_OBJECT_CONSTANT.getHiddenObject(self);
        StaticObject thatObject = (StaticObject) meta.jvmci.HIDDEN_OBJECT_CONSTANT.getHiddenObject(that);
        return selfObject == thatObject;
    }

    @Substitution(hasReceiver = true)
    public static int hashCode(StaticObject self, @Inject EspressoContext context) {
        assert context.getLanguage().isInternalJVMCIEnabled();
        Meta meta = context.getMeta();
        StaticObject object = (StaticObject) meta.jvmci.HIDDEN_OBJECT_CONSTANT.getHiddenObject(self);
        return System.identityHashCode(object);
    }
}
