/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.substitutions;

import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.vm.InterpreterToVM;

@EspressoSubstitutions
public class Target_com_oracle_truffle_espresso_polyglot_Polyglot {
    @Substitution
    public static boolean isInteropObject(@Host(Object.class) StaticObject object) {
        return object.isInterop();
    }

    @Substitution
    public static @Host(Object.class) StaticObject cast(@Host(Class.class) StaticObject targetClass, @Host(Object.class) StaticObject value, @InjectMeta Meta meta) {
        if (StaticObject.isNull(value)) {
            Meta.throwExceptionWithMessage(meta.java_lang_NullPointerException, "Polyglot.cast: cannot cast null.");
        }
        if (value.isInterop()) {
            return StaticObject.createInterop(targetClass.getMirrorKlass(), value.rawInteropObject());
        } else {
            return InterpreterToVM.checkCast(value, targetClass.getMirrorKlass());
        }
    }

    @Substitution
    public static @Host(Object.class) StaticObject eval(@Host(String.class) StaticObject language, @Host(String.class) StaticObject code, @InjectMeta Meta meta) {
        Source source = Source.newBuilder(language.toString(), code.toString(), "(eval)").build();
        Object evalResult = meta.getContext().getEnv().parsePublic(source).call();
        if (evalResult instanceof StaticObject) {
            return (StaticObject) evalResult;
        }
        return createInteropObject(evalResult, meta);
    }

    public static @Host(Object.class) StaticObject getBinding(@Host(String.class) StaticObject name, @InjectMeta Meta meta) {
        if (!meta.getContext().getEnv().isPolyglotBindingsAccessAllowed()) {
            Meta.throwExceptionWithMessage(meta.java_lang_SecurityException,
                            "Polyglot bindings are not accessible for this language. Use --polyglot or allowPolyglotAccess when building the context.");
        }
        Object binding = meta.getContext().getEnv().importSymbol(name.toString());
        if (binding instanceof StaticObject) {
            return (StaticObject) binding;
        }
        return createInteropObject(binding, meta);
    }

    public static void setBinding(@Host(String.class) StaticObject name, @Host(Object.class) StaticObject value, @InjectMeta Meta meta) {
        if (!meta.getContext().getEnv().isPolyglotBindingsAccessAllowed()) {
            Meta.throwExceptionWithMessage(meta.java_lang_SecurityException,
                            "Polyglot bindings are not accessible for this language. Use --polyglot or allowPolyglotAccess when building the context.");
        }
        if (value.isInterop()) {
            meta.getContext().getEnv().exportSymbol(name.toString(), value.rawInteropObject());
        } else {
            meta.getContext().getEnv().exportSymbol(name.toString(), value);
        }
    }

    protected static StaticObject createInteropObject(Object object, Meta meta) {
        return StaticObject.createInterop(meta.java_lang_Object, object);
    }
}
