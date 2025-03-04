/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.substitutions.polyglot;

import java.lang.reflect.Type;

import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.espresso.descriptors.EspressoSymbols.Names;
import com.oracle.truffle.espresso.descriptors.EspressoSymbols.Signatures;
import com.oracle.truffle.espresso.impl.EspressoType;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.impl.generics.reflectiveObjects.ParameterizedTypeImpl;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.nodes.interop.GetTypeLiteralNode;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.substitutions.EspressoSubstitutions;
import com.oracle.truffle.espresso.substitutions.JavaType;
import com.oracle.truffle.espresso.substitutions.Substitution;
import com.oracle.truffle.espresso.substitutions.SubstitutionNode;

@EspressoSubstitutions
public final class Target_com_oracle_truffle_espresso_polyglot_TypeLiteral {

    @Substitution
    public abstract static class GetReifiedType extends SubstitutionNode {

        abstract @JavaType(internalName = "Lcom/oracle/truffle/espresso/polyglot/TypeLiteral;") StaticObject execute(
                        @JavaType(Object.class) StaticObject foreignObject,
                        int typeArgumentIndex);

        @Specialization
        @JavaType(internalName = "Lcom/oracle/truffle/espresso/polyglot/TypeLiteral;")
        static StaticObject doCached(
                        @JavaType(Object.class) StaticObject foreignObject,
                        int typeArgumentIndex,
                        @Bind Node node,
                        @Cached GetTypeLiteralNode getTypeLiteralNode,
                        @Cached InlinedBranchProfile notValidForeign,
                        @Cached InlinedBranchProfile invalidIndex) {
            EspressoContext context = EspressoContext.get(node);
            if (StaticObject.isNull(foreignObject)) {
                notValidForeign.enter(node);
                throw context.getMeta().throwNullPointerException();
            }
            if (!foreignObject.isForeignObject()) {
                notValidForeign.enter(node);
                throw context.getMeta().throwExceptionWithMessage(context.getMeta().java_lang_IllegalArgumentException, "Called getReifiedType on a non-foreign object");
            }
            EspressoType[] typeArguments = foreignObject.getTypeArguments(context.getLanguage());
            if (typeArguments == null) {
                return StaticObject.NULL;
            }
            if (0 > typeArgumentIndex || typeArgumentIndex >= typeArguments.length) {
                invalidIndex.enter(node);
                throw context.getMeta().throwExceptionWithMessage(context.getMeta().java_lang_IllegalArgumentException,
                                "invalid index %d for type literal in foreign object with %d type arguments",
                                typeArgumentIndex, typeArguments.length);
            }
            if (!context.isGenericTypeHintsEnabled()) {
                return getTypeLiteralNode.genericObjectTypeLiteral(context);
            }
            EspressoType type = typeArguments[typeArgumentIndex];
            return getTypeLiteralNode.execute(type);
        }
    }

    @Substitution(hasReceiver = true)
    abstract static class ExtractLiteralType extends SubstitutionNode {

        static final int LIMIT = 2;

        abstract void execute(
                        @JavaType(Object.class) StaticObject receiver,
                        @JavaType(Type.class) StaticObject type);

        @Specialization
        void doCached(
                        @JavaType(Object.class) StaticObject receiver,
                        @JavaType(Type.class) StaticObject type,
                        @Bind("getContext()") EspressoContext context) {
            assert type != null;
            context.getMeta().polyglot.HIDDEN_TypeLiteral_internalType.setHiddenObject(receiver, extractEspressoType(type, context));
        }

        private EspressoType extractEspressoType(StaticObject type, EspressoContext context) {
            Meta meta = context.getMeta();
            if (meta.java_lang_reflect_ParameterizedType.isAssignableFrom(type.getKlass())) {
                Klass rawType = rawType(type);
                if (!meta.getContext().isGenericTypeHintsEnabled()) {
                    return rawType;
                }
                // lookup type arguments and convert to EspressoTypes
                EspressoType[] typeArguments = typeArguments(type, context);
                return ParameterizedTypeImpl.make(rawType, typeArguments, null);
            }
            if (type.isMirrorKlass()) {
                return type.getMirrorKlass();
            }
            throw meta.throwExceptionWithMessage(meta.java_lang_IllegalArgumentException, "unsupported type passed");
        }

        private static Klass rawType(StaticObject type) {
            Method method = type.getKlass().lookupDeclaredMethod(Names.getRawType, Signatures.Class, Klass.LookupMode.INSTANCE_ONLY);
            assert method != null;
            StaticObject rawGuestClass = (StaticObject) method.invokeDirectVirtual(type);
            return rawGuestClass.getMirrorKlass();
        }

        private EspressoType[] typeArguments(StaticObject type, EspressoContext context) {
            Method method = type.getKlass().lookupDeclaredMethod(Names.getActualTypeArguments, Signatures.Type_array, Klass.LookupMode.INSTANCE_ONLY);
            assert method != null;
            StaticObject typesArray = (StaticObject) method.invokeDirectVirtual(type);
            StaticObject[] types = typesArray.unwrap(context.getLanguage());
            EspressoType[] result = new EspressoType[types.length];
            for (int i = 0; i < types.length; i++) {
                result[i] = extractEspressoType(types[i], context);
            }
            return result;
        }
    }
}
