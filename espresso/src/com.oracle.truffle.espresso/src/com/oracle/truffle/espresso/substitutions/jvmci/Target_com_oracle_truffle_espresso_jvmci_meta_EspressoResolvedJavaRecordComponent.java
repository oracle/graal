/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.espresso.substitutions.jvmci.Target_com_oracle_truffle_espresso_jvmci_meta_EspressoResolvedInstanceType.getRawAnnotationBytes;

import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.espresso.classfile.attributes.RecordAttribute;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.substitutions.EspressoSubstitutions;
import com.oracle.truffle.espresso.substitutions.JavaType;
import com.oracle.truffle.espresso.substitutions.Substitution;
import com.oracle.truffle.espresso.substitutions.SubstitutionNode;

@EspressoSubstitutions
final class Target_com_oracle_truffle_espresso_jvmci_meta_EspressoResolvedJavaRecordComponent {

    private Target_com_oracle_truffle_espresso_jvmci_meta_EspressoResolvedJavaRecordComponent() {
    }

    @Substitution
    abstract static class GetRawAnnotationBytes0 extends SubstitutionNode {
        abstract @JavaType(byte[].class) StaticObject execute(@JavaType(internalName = "Lcom/oracle/truffle/espresso/jvmci/meta/EspressoResolvedInstanceType;") StaticObject holder,
                        int index, int category);

        @Specialization
        static StaticObject doDefault(StaticObject holder, int index, int category,
                        @Bind("getContext()") EspressoContext context) {
            assert context.getLanguage().isInternalJVMCIEnabled();
            Meta meta = context.getMeta();
            ObjectKlass holderKlass = (ObjectKlass) meta.jvmci.HIDDEN_OBJECTKLASS_MIRROR.getHiddenObject(holder);
            RecordAttribute record = holderKlass.getAttribute(RecordAttribute.NAME, RecordAttribute.class);
            if (record == null) {
                throw meta.throwIllegalArgumentExceptionBoundary();
            }
            RecordAttribute.RecordComponentInfo[] components = record.getComponents();
            if (index < 0 || index >= components.length) {
                throw meta.throwIndexOutOfBoundsExceptionBoundary("Bad record index", index, components.length);
            }
            RecordAttribute.RecordComponentInfo component = components[index];
            return getRawAnnotationBytes(component, category, meta);
        }
    }
}
