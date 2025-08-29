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

import com.oracle.truffle.espresso.libs.libjava.LibJava;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.substitutions.EspressoSubstitutions;
import com.oracle.truffle.espresso.substitutions.Inject;
import com.oracle.truffle.espresso.substitutions.JavaType;
import com.oracle.truffle.espresso.substitutions.Substitution;
import com.oracle.truffle.espresso.vm.VM;

@EspressoSubstitutions(type = "Ljdk/internal/loader/BootLoader;", group = LibJava.class)
public final class Target_jdk_internal_loader_BootLoader {
    @Substitution
    public static void setBootLoaderUnnamedModule0(@JavaType(Module.class) StaticObject module, @Inject EspressoContext ctx) {
        ctx.getVM().JVM_SetBootLoaderUnnamedModule(module);
    }

    @Substitution
    public static @JavaType(String[].class) StaticObject getSystemPackageNames(@Inject VM vm, @Inject Meta meta) {
        return vm.JVM_GetSystemPackages(meta);
    }

    /**
     * Returns the location of the package of the given name, if defined by the boot loader;
     * otherwise {@code null} is returned. The location may be a module from the runtime image or
     * exploded image, or from the boot class append path (i.e. -Xbootclasspath/a or BOOT-CLASS-PATH
     * attribute specified in java agent).
     */
    @Substitution
    public static @JavaType(String.class) StaticObject getSystemPackageLocation(@JavaType(String[].class) StaticObject name, @Inject VM vm, @Inject Meta meta) {
        return vm.JVM_GetSystemPackage(name, meta);
    }
}
