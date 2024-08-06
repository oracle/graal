/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.truffle.hotspot;

import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;

import jdk.graal.compiler.truffle.KnownTruffleTypes;

import com.oracle.truffle.compiler.TruffleCompilerRuntime;

import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

public final class HotSpotKnownTruffleTypes extends KnownTruffleTypes {

    // Checkstyle: stop field name check

    // java.base
    public final ResolvedJavaType WeakReference = lookupType(WeakReference.class);
    public final ResolvedJavaType SoftReference = lookupType(SoftReference.class);

    // jvmci
    public final ResolvedJavaType InstalledCode = lookupTypeCached("jdk.vm.ci.code.InstalledCode");
    public final ResolvedJavaField InstalledCode_entryPoint = findField(InstalledCode, "entryPoint");

    // truffle.runtime.hotspot
    public final ResolvedJavaType HotSpotThreadLocalHandshake = lookupTypeCached("com.oracle.truffle.runtime.hotspot.HotSpotThreadLocalHandshake");
    public final ResolvedJavaMethod HotSpotThreadLocalHandshake_doHandshake = findMethod(HotSpotThreadLocalHandshake, "doHandshake", java_lang_Object);

    public final ResolvedJavaType HotSpotOptimizedCallTarget = lookupTypeCached("com.oracle.truffle.runtime.hotspot.HotSpotOptimizedCallTarget");
    public final ResolvedJavaField HotSpotOptimizedCallTarget_installedCode = findField(HotSpotOptimizedCallTarget, "installedCode");

    public HotSpotKnownTruffleTypes(TruffleCompilerRuntime runtime, MetaAccessProvider metaAccess, ConstantReflectionProvider constantReflection) {
        super(runtime, metaAccess, constantReflection);
    }

    // Checkstyle: resume field name check

}
