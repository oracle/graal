/*
 * Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.polyglot;

import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.graalvm.polyglot.Value;

/**
 * A special implementation for polyglot bindings, exposed to the embedder. The difference to a
 * normal polyglot value is that it preserves the language information for each member.
 */
final class PolyglotBindingsValue extends PolyglotValueDispatch {

    final Object delegateBindings;
    final Map<String, Object> values;

    PolyglotBindingsValue(PolyglotLanguageContext context, PolyglotBindings bindings) {
        super(context.getImpl(), context.getLanguageInstance());
        this.values = context.context.polyglotBindings;
        this.delegateBindings = context.asValue(bindings);
    }

    @Override
    public Object getMember(Object context, Object receiver, Object key) {
        String keyName = getKeyName(context, receiver, key);
        return values.get(keyName);
    }

    @Override
    public Set<String> getMemberKeys(Object context, Object receiver) {
        return values.keySet();
    }

    @Override
    public Object getMembers(Object context, Object receiver) {
        return impl.getAPIAccess().newValue(new MemberList(impl, context, values.keySet()), context, receiver);
    }

    @Override
    public boolean removeMember(Object context, Object receiver, Object key) {
        Object result = values.remove(key);
        return result != null;
    }

    @Override
    public void putMember(Object context, Object receiver, Object key, Object member) {
        String keyName = getKeyName(context, receiver, key);
        values.put(keyName, ((PolyglotLanguageContext) context).context.asValue(member));
    }

    private static String getKeyName(Object context, Object receiver, Object key) {
        if (key instanceof String) {
            return (String) key;
        }
        if (key instanceof Value) {
            return ((Value) key).getMemberSimpleName();
        }
        throw PolyglotValueDispatch.nonWritableMemberKey((PolyglotLanguageContext) context, receiver, Objects.toString(key));
    }

    @Override
    public boolean hasMembers(Object context, Object receiver) {
        return true;
    }

    @Override
    public boolean hasMember(Object context, Object receiver, Object key) {
        return values.containsKey(key);
    }

    /*
     * It would be very hard to implement the #as(Class) semantics again here. So we just delegate
     * to an interop value in such a case. This also means that we loose language information for
     * members.
     */
    @Override
    public <T> T asClass(Object context, Object receiver, Class<T> targetType) {
        return impl.getAPIAccess().callValueAs(delegateBindings, targetType);
    }

    @Override
    public <T> T asTypeLiteral(Object context, Object receiver, Class<T> rawType, Type type) {
        return impl.getAPIAccess().callValueAs(delegateBindings, rawType, type);
    }

    @Override
    public String toStringImpl(PolyglotLanguageContext context, Object receiver) {
        return delegateBindings.toString();
    }

    @Override
    public Object getMetaObjectImpl(PolyglotLanguageContext context, Object receiver) {
        return impl.getAPIAccess().callValueGetMetaObject(delegateBindings);
    }

    private Object getStringMemberObject(Object context, String name) {
        return impl.getAPIAccess().newValue(new MemberValue(impl, ((PolyglotLanguageContext) context).getLanguageInstance(), name), context, name);
    }

    @SuppressWarnings("unused")
    private final class MemberList extends PolyglotValueDispatch {

        private final String[] names;

        MemberList(PolyglotImpl impl, Object context, Set<String> names) {
            super(impl, ((PolyglotLanguageContext) context).getLanguageInstance());
            this.names = names.toArray(new String[names.size()]);
        }

        @Override
        public boolean hasArrayElements(Object languageContext, Object receiver) {
            return true;
        }

        @Override
        public long getArraySize(Object languageContext, Object receiver) {
            return names.length;
        }

        @Override
        public Object getArrayElement(Object languageContext, Object receiver, long index) {
            String name = names[(int) index];
            return getStringMemberObject(languageContext, name);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T asClass(Object languageContext, Object receiver, Class<T> targetType) {
            if (String.class.equals(targetType)) {
                return (T) Arrays.toString(names);
            }
            return null;
        }

        @Override
        public <T> T asTypeLiteral(Object languageContext, Object receiver, Class<T> rawType, Type genericType) {
            return asClass(languageContext, receiver, rawType);
        }
    }

    private static class MemberValue extends PolyglotValueDispatch {

        private final String name;

        MemberValue(PolyglotImpl impl, PolyglotLanguageInstance languageInstance, String name) {
            super(impl, languageInstance);
            this.name = name;
        }

        @Override
        public boolean isMember(Object context, Object receiver) {
            return true;
        }

        @Override
        public String getMemberSimpleName(Object languageContext, Object receiver) {
            return name;
        }

        @Override
        public String getMemberQualifiedName(Object languageContext, Object receiver) {
            return name;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T asClass(Object context, Object receiver, Class<T> targetType) {
            if (String.class.equals(targetType)) {
                return (T) name;
            }
            return null;
        }

        @Override
        public <T> T asTypeLiteral(Object context, Object receiver, Class<T> rawType, Type genericType) {
            return asClass(context, receiver, rawType);
        }
    }
}
