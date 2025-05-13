/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.libs.libjava.impl;

import java.security.ProtectionDomain;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.classfile.descriptors.Symbol;
import com.oracle.truffle.espresso.classfile.descriptors.Type;
import com.oracle.truffle.espresso.libs.libjava.LibJava;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.substitutions.EspressoSubstitutions;
import com.oracle.truffle.espresso.substitutions.Inject;
import com.oracle.truffle.espresso.substitutions.JavaType;
import com.oracle.truffle.espresso.substitutions.Substitution;

@EspressoSubstitutions(value = ClassLoader.class, group = LibJava.class)
public final class Target_java_lang_ClassLoader {
    @Substitution
    public static void registerNatives() {
    }

    @Substitution(hasReceiver = true)
    @TruffleBoundary
    public static @JavaType(Class.class) StaticObject findLoadedClass0(@JavaType(ClassLoader.class) StaticObject self, @JavaType(String.class) StaticObject name,
                    @Inject EspressoContext ctx) {
        if (StaticObject.isNull(name)) {
            return StaticObject.NULL;
        }
        return ctx.getVM().JVM_FindLoadedClass(self, name);
    }

    @Substitution
    @TruffleBoundary
    public static @JavaType(Class.class) StaticObject findBootstrapClass(@JavaType(String.class) StaticObject name,
                    @Inject EspressoContext ctx) {
        if (StaticObject.isNull(name)) {
            return StaticObject.NULL;
        }
        String clName = ctx.getMeta().toHostString(name);
        assert clName != null;
        String slashName = clName.replace('.', '/');
        return ctx.getVM().findClassFromBootLoader(slashName);
    }

    @Substitution
    @TruffleBoundary
    public static @JavaType(Class.class) StaticObject defineClass0(
                    @JavaType(ClassLoader.class) @SuppressWarnings("unused") StaticObject loader,
                    @JavaType(Class.class) StaticObject lookup,
                    @JavaType(String.class) StaticObject name,
                    @JavaType(byte[].class) StaticObject b,
                    int off, int len,
                    @JavaType(ProtectionDomain.class) StaticObject pd,
                    boolean initialize,
                    int flags,
                    @JavaType(Object.class) StaticObject classData,
                    @Inject EspressoContext ctx, @Inject EspressoLanguage lang) {
        if (StaticObject.isNull(b)) {
            throw ctx.getMeta().throwNullPointerException();
        }
        if (len < 0) {
            throw ctx.getMeta().throwArrayIndexOutOfBounds(len);
        }
        if (off < 0) {
            throw ctx.getMeta().throwArrayIndexOutOfBounds(off);
        }
        byte[] bytes = b.unwrap(lang);
        if ((long) off + (long) len > bytes.length) { // prevent overflow
            throw ctx.getMeta().throwArrayIndexOutOfBounds(off + len, bytes.length);
        }
        Symbol<Type> type = null;
        if (StaticObject.notNull(name)) {
            type = ctx.getVM().nameToInternal(toSlashName(ctx.getMeta().toHostString(name)));
        }

        byte[] buf = new byte[len];
        System.arraycopy(bytes, off, buf, 0, len);

        return ctx.getVM().lookupDefineClass(lookup, type, buf, pd, initialize, flags, classData);
    }

    @Substitution
    @TruffleBoundary
    public static @JavaType(Class.class) StaticObject defineClass1(
                    @JavaType(ClassLoader.class) StaticObject loader, @JavaType(String.class) StaticObject name,
                    @JavaType(byte[].class) StaticObject data, int off, int len,
                    @JavaType(ProtectionDomain.class) StaticObject pd,
                    // TODO: source unused
                    @SuppressWarnings("unused") @JavaType(String.class) StaticObject source,
                    @Inject EspressoContext ctx, @Inject EspressoLanguage lang) {
        if (StaticObject.isNull(data)) {
            throw ctx.getMeta().throwNullPointerException();
        }
        if (len < 0) {
            throw ctx.getMeta().throwArrayIndexOutOfBounds(len);
        }
        if (off < 0) {
            throw ctx.getMeta().throwArrayIndexOutOfBounds(off);
        }
        byte[] bytes = data.unwrap(lang);
        if ((long) off + (long) len > bytes.length) { // prevent overflow
            throw ctx.getMeta().throwArrayIndexOutOfBounds(off + len, bytes.length);
        }
        Symbol<Type> type = null;
        if (StaticObject.notNull(name)) {
            type = ctx.getVM().nameToInternal(toSlashName(ctx.getMeta().toHostString(name)));
        }

        byte[] buf = new byte[len];
        System.arraycopy(bytes, off, buf, 0, len);

        return ctx.getVM().defineClass(type, loader, pd, buf);
    }

    private static String toSlashName(String name) {
        return name.replace('.', '/');
    }
}
