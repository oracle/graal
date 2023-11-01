/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.truffle.espresso.classfile.attributes;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.espresso.classfile.RuntimeConstantPool;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.descriptors.Symbol.Signature;
import com.oracle.truffle.espresso.descriptors.Symbol.Type;
import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.Attribute;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;

public class RecordAttribute extends Attribute {
    public static final Symbol<Name> NAME = Name.Record;

    @CompilerDirectives.CompilationFinal(dimensions = 1) //
    private final RecordComponentInfo[] components;

    public RecordAttribute(Symbol<Name> name, RecordComponentInfo[] components) {
        super(name, null);
        this.components = components;
    }

    public static class RecordComponentInfo {
        final char name;
        final char descriptor;
        @CompilerDirectives.CompilationFinal(dimensions = 1)//
        final Attribute[] attributes;

        public RecordComponentInfo(int name, int descriptor, Attribute[] attributes) {
            this.name = (char) name;
            this.descriptor = (char) descriptor;
            this.attributes = attributes;
        }

        public StaticObject toGuestComponent(Meta meta, ObjectKlass klass) {
            assert meta.getJavaVersion().java16OrLater();
            RuntimeConstantPool pool = klass.getConstantPool();
            StaticObject component = meta.java_lang_reflect_RecordComponent.allocateInstance(meta.getContext());
            Symbol<Name> nameSymbol = pool.symbolAt(name);
            Symbol<Type> typeSymbol = pool.symbolAt(descriptor);
            Symbol<Signature> signature = meta.getSignatures().makeRaw(typeSymbol);
            meta.java_lang_reflect_RecordComponent_clazz.setObject(component, klass.mirror());
            meta.java_lang_reflect_RecordComponent_name.setObject(component, meta.toGuestString(nameSymbol));
            meta.java_lang_reflect_RecordComponent_type.setObject(component, meta.resolveSymbolAndAccessCheck(typeSymbol, klass).mirror());

            // Find and set accessor
            Method m = klass.lookupMethod(nameSymbol, signature);
            boolean validMethod = m != null && !m.isStatic() && !m.isConstructor();
            meta.java_lang_reflect_RecordComponent_accessor.setObject(component, validMethod ? m.makeMirror(meta) : StaticObject.NULL);

            // Find and set generic signature
            SignatureAttribute genericSignatureAttribute = (SignatureAttribute) getAttribute(SignatureAttribute.NAME);
            meta.java_lang_reflect_RecordComponent_signature.setObject(component,
                            genericSignatureAttribute != null ? meta.toGuestString(pool.symbolAt(genericSignatureAttribute.getSignatureIndex())) : StaticObject.NULL);

            // Find and set annotations
            doAnnotation(component, Name.RuntimeVisibleAnnotations, meta.java_lang_reflect_RecordComponent_annotations, meta);
            doAnnotation(component, Name.RuntimeVisibleTypeAnnotations, meta.java_lang_reflect_RecordComponent_typeAnnotations, meta);

            return component;
        }

        private void doAnnotation(StaticObject component, Symbol<Name> attrName, Field f, Meta meta) {
            Attribute attr = getAttribute(attrName);
            f.setObject(component, attr == null ? StaticObject.NULL : StaticObject.wrap(attr.getData(), meta));
        }

        public Attribute getAttribute(Symbol<Name> attributeName) {
            for (Attribute attr : attributes) {
                if (attr.getName().equals(attributeName)) {
                    return attr;
                }
            }
            return null;
        }
    }

    public RecordComponentInfo[] getComponents() {
        return components;
    }
}
