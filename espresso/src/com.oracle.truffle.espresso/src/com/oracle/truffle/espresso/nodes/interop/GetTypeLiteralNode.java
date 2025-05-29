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
package com.oracle.truffle.espresso.nodes.interop;

import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.espresso.impl.EspressoType;
import com.oracle.truffle.espresso.nodes.EspressoNode;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.substitutions.JavaType;

public abstract class GetTypeLiteralNode extends EspressoNode {
    static final int LIMIT = 3;

    GetTypeLiteralNode() {
    }

    public abstract @JavaType(internalName = "Lcom/oracle/truffle/espresso/polyglot/TypeLiteral;") StaticObject execute(EspressoType type);

    @Specialization(guards = "type == null")
    @SuppressWarnings("unused")
    @JavaType(internalName = "Lcom/oracle/truffle/espresso/polyglot/TypeLiteral;")
    StaticObject doNull(EspressoType type,
                    @Bind("getContext()") EspressoContext context,
                    @Cached("genericObjectTypeLiteral(context)") StaticObject result) {
        return result;
    }

    @Specialization(guards = {
                    "type != null",
                    "cachedType == type"
    }, limit = "LIMIT")
    @SuppressWarnings("unused")
    @JavaType(internalName = "Lcom/oracle/truffle/espresso/polyglot/TypeLiteral;")
    StaticObject doCached(EspressoType type,
                    @Cached("type") EspressoType cachedType,
                    @Cached("doUncached(cachedType)") StaticObject result) {
        return result;
    }

    @Specialization(replaces = "doCached", guards = "type != null")
    @JavaType(internalName = "Lcom/oracle/truffle/espresso/polyglot/TypeLiteral;")
    StaticObject doUncached(EspressoType type) {
        return type.getGuestTypeLiteral();
    }

    public StaticObject genericObjectTypeLiteral(EspressoContext context) {
        StaticObject result = context.getAllocator().createNew(context.getMeta().polyglot.TypeLiteral$InternalTypeLiteral);
        context.getMeta().polyglot.HIDDEN_TypeLiteral_internalType.setHiddenObject(result, context.getMeta().java_lang_Object);
        context.getMeta().polyglot.TypeLiteral_rawType.setObject(result, context.getMeta().java_lang_Object.mirror());
        return result;
    }
}
