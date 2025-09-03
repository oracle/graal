/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.substitutions.standard;

import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.substitutions.EspressoSubstitutions;
import com.oracle.truffle.espresso.substitutions.Inject;
import com.oracle.truffle.espresso.substitutions.JavaType;
import com.oracle.truffle.espresso.substitutions.Substitution;
import com.oracle.truffle.espresso.substitutions.SubstitutionProfiler;
import com.oracle.truffle.espresso.vm.VM;

@EspressoSubstitutions
public final class Target_jdk_internal_reflect_Reflection {
    /**
     * This substitution is here because the VM method JVM_GetCallerClass has a different signature
     * between java8 and java11.
     * 
     * Since the linking mechanism for VM methods uses only the names, it is delicate to declare two
     * methods with different signatures (but same name) and dispatch them according to the java
     * version we are running.
     * 
     * Therefore, we are creating this java11 substitution as a workaround.
     */
    @Substitution
    public static @JavaType(Class.class) StaticObject getCallerClass(@Inject Meta meta, @Inject SubstitutionProfiler profiler) {
        return meta.getVM().JVM_GetCallerClass(VM.jvmCallerDepth(), profiler);
    }
}
