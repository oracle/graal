/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.polyglot;

import java.util.Map;
import java.util.Set;

import org.graalvm.polyglot.TypeLiteral;
import org.graalvm.polyglot.Value;

/**
 * A special implementation for polyglot bindings, exposed to the embedder. The difference to a
 * normal polyglot value is that it preserves the language information for each member.
 */
final class PolyglotBindingsValue extends PolyglotValue {

    final Value delegateBindings;
    final Map<String, Value> values;

    PolyglotBindingsValue(PolyglotLanguageContext context) {
        super(context);
        this.values = context.context.polyglotBindings;
        this.delegateBindings = context.asValue(new PolyglotBindings(context, values));
    }

    @Override
    public Value getMember(Object receiver, String key) {
        return values.get(key);
    }

    @Override
    public Set<String> getMemberKeys(Object receiver) {
        return values.keySet();
    }

    @Override
    public boolean removeMember(Object receiver, String key) {
        Value result = values.remove(key);
        return result != null;
    }

    @Override
    public void putMember(Object receiver, String key, Object member) {
        values.put(key, languageContext.context.asValue(member));
    }

    @Override
    public boolean hasMembers(Object receiver) {
        return true;
    }

    @Override
    public boolean hasMember(Object receiver, String key) {
        return values.containsKey(key);
    }

    /*
     * It would be very hard to implement the #as(Class) semantics again here. So we just delegate
     * to an interop value in such a case. This also means that we loose language information for
     * members.
     */
    @Override
    public <T> T as(Object receiver, Class<T> targetType) {
        return delegateBindings.as(targetType);
    }

    @Override
    public <T> T as(Object receiver, TypeLiteral<T> targetType) {
        return delegateBindings.as(targetType);
    }

    @Override
    public String toString(Object receiver) {
        return delegateBindings.toString();
    }

    @Override
    public Value getMetaObject(Object receiver) {
        return delegateBindings.getMetaObject();
    }
}
