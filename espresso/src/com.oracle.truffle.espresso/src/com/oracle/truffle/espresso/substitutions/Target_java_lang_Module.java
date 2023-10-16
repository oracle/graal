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

package com.oracle.truffle.espresso.substitutions;

import static com.oracle.truffle.espresso.runtime.Classpath.JAVA_BASE;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.vm.ModulesHelperVM;

@EspressoSubstitutions
public final class Target_java_lang_Module {

    /*
     * As of JDK 15+, The native signature for these VM methods changed. These substitutions bypass
     * the native linking of these methods to their 'JVM_*' counterparts.
     */

    @Substitution
    @TruffleBoundary
    public static void addExports0(@JavaType(internalName = "Ljava/lang/Module;") StaticObject from,
                    @JavaType(String.class) StaticObject pn,
                    @JavaType(internalName = "Ljava/lang/Module;") StaticObject to,
                    @Inject Meta meta,
                    @Inject SubstitutionProfiler profiler) {
        if (StaticObject.isNull(pn)) {
            throw meta.throwNullPointerException();
        }
        ModulesHelperVM.addModuleExports(from, meta.toHostString(pn).replace('.', '/'), to, meta, profiler);
    }

    @Substitution
    @TruffleBoundary
    public static void addExportsToAll0(@JavaType(internalName = "Ljava/lang/Module;") StaticObject from,
                    @JavaType(String.class) StaticObject pn,
                    @Inject Meta meta,
                    @Inject SubstitutionProfiler profiler) {
        if (StaticObject.isNull(pn)) {
            throw meta.throwNullPointerException();
        }
        ModulesHelperVM.addModuleExports(from, meta.toHostString(pn).replace('.', '/'), StaticObject.NULL, meta, profiler);
    }

    @Substitution
    @TruffleBoundary
    public static void addExportsToAllUnnamed0(@JavaType(internalName = "Ljava/lang/Module;") StaticObject from,
                    @JavaType(String.class) StaticObject pn,
                    @Inject Meta meta,
                    @Inject SubstitutionProfiler profiler) {
        if (StaticObject.isNull(pn)) {
            throw meta.throwNullPointerException();
        }
        ModulesHelperVM.addModuleExportsToAllUnnamed(from, meta.toHostString(pn).replace('.', '/'), profiler, meta);
    }

    @Substitution
    @TruffleBoundary
    public static void defineModule0(@JavaType(internalName = "Ljava/lang/Module;") StaticObject module,
                    boolean isOpen,
                    @SuppressWarnings("unused") @JavaType(String.class) StaticObject version,
                    @SuppressWarnings("unused") @JavaType(String.class) StaticObject location,
                    @JavaType(Object[].class) StaticObject pns,
                    @Inject EspressoLanguage language,
                    @Inject Meta meta,
                    @Inject SubstitutionProfiler profiler) {
        if (StaticObject.isNull(module)) {
            profiler.profile(0);
            throw meta.throwNullPointerException();
        }
        if (!meta.java_lang_Module.isAssignableFrom(module.getKlass())) {
            profiler.profile(1);
            throw meta.throwExceptionWithMessage(meta.java_lang_IllegalArgumentException, "module is not an instance of java.lang.Module");
        }
        StaticObject guestName = meta.java_lang_Module_name.getObject(module);
        if (StaticObject.isNull(guestName)) {
            profiler.profile(2);
            throw meta.throwExceptionWithMessage(meta.java_lang_IllegalArgumentException, "module name cannot be null");
        }
        String hostName = meta.toHostString(guestName);
        String[] packages = toStringArray(language, pns, meta);
        if (hostName.equals(JAVA_BASE)) {
            profiler.profile(5);
            meta.getVM().defineJavaBaseModule(module, packages, profiler);
        } else {
            profiler.profile(6);
            meta.getVM().defineModule(module, hostName, isOpen, packages, profiler);
        }
    }

    private static String[] toStringArray(EspressoLanguage language, StaticObject packages, Meta meta) {
        String[] strs = new String[packages.length(language)];
        StaticObject[] unwrapped = packages.unwrap(language);
        for (int i = 0; i < unwrapped.length; i++) {
            StaticObject str = unwrapped[i];
            if (StaticObject.isNull(str)) {
                throw meta.throwNullPointerException();
            }
            if (meta.java_lang_String != str.getKlass()) {
                throw meta.throwExceptionWithMessage(meta.java_lang_IllegalArgumentException, "Package name array contains a non-string");
            }
            strs[i] = meta.toHostString(str).replace('.', '/');
        }
        return strs;
    }
}
