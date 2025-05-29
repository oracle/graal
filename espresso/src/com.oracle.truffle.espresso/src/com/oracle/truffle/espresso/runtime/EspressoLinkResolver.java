/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.runtime;

import com.oracle.truffle.espresso.classfile.descriptors.Name;
import com.oracle.truffle.espresso.classfile.descriptors.Signature;
import com.oracle.truffle.espresso.classfile.descriptors.Symbol;
import com.oracle.truffle.espresso.classfile.descriptors.Type;
import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.shared.resolver.CallSiteType;
import com.oracle.truffle.espresso.shared.resolver.FieldAccessType;
import com.oracle.truffle.espresso.shared.resolver.LinkResolver;
import com.oracle.truffle.espresso.shared.resolver.ResolvedCall;

public final class EspressoLinkResolver {
    private EspressoLinkResolver() {
        // no instance.
    }

    public static Field resolveFieldSymbolOrThrow(EspressoContext ctx, Klass accessingKlass,
                    Symbol<Name> name, Symbol<Type> type, Klass symbolicHolder,
                    boolean accessCheck, boolean loadingConstraints) {
        return LinkResolver.resolveFieldSymbolOrThrow(ctx, accessingKlass, name, type, symbolicHolder, accessCheck, loadingConstraints);
    }

    public static Field resolveFieldSymbolOrNull(EspressoContext ctx, Klass accessingKlass,
                    Symbol<Name> name, Symbol<Type> type, Klass symbolicHolder,
                    boolean accessCheck, boolean loadingConstraints) {
        return LinkResolver.resolveFieldSymbolOrNull(ctx, accessingKlass, name, type, symbolicHolder, accessCheck, loadingConstraints);
    }

    public static void checkFieldAccessOrThrow(EspressoContext ctx, Field symbolicResolution, FieldAccessType fieldAccessType, Klass currentKlass, Method currentMethod) {
        LinkResolver.checkFieldAccessOrThrow(ctx, symbolicResolution, fieldAccessType, currentKlass, currentMethod);
    }

    public static boolean checkFieldAccess(EspressoContext ctx, Field symbolicResolution, FieldAccessType fieldAccessType, Klass currentKlass, Method currentMethod) {
        return LinkResolver.checkFieldAccess(ctx, symbolicResolution, fieldAccessType, currentKlass, currentMethod);
    }

    public static Method resolveMethodSymbol(EspressoContext ctx, Klass accessingKlass,
                    Symbol<Name> name, Symbol<Signature> signature, Klass symbolicHolder,
                    boolean interfaceLookup,
                    boolean accessCheck, boolean loadingConstraints) {
        return LinkResolver.resolveMethodSymbol(ctx, accessingKlass, name, signature, symbolicHolder, interfaceLookup, accessCheck, loadingConstraints);
    }

    public static Method resolveMethodSymbolOrNull(EspressoContext ctx, Klass accessingKlass,
                    Symbol<Name> name, Symbol<Signature> signature, Klass symbolicHolder,
                    boolean interfaceLookup,
                    boolean accessCheck, boolean loadingConstraints) {
        return LinkResolver.resolveMethodSymbolOrNull(ctx, accessingKlass, name, signature, symbolicHolder, interfaceLookup, accessCheck, loadingConstraints);
    }

    public static ResolvedCall<Klass, Method, Field> resolveCallSiteOrThrow(EspressoContext ctx, Klass currentKlass, Method symbolicResolution, CallSiteType callSiteType, Klass symbolicHolder) {
        return LinkResolver.resolveCallSiteOrThrow(ctx, currentKlass, symbolicResolution, callSiteType, symbolicHolder);
    }

    public static ResolvedCall<Klass, Method, Field> resolveCallSiteOrNull(EspressoContext ctx, Klass currentKlass, Method symbolicResolution, CallSiteType callSiteType, Klass symbolicHolder) {
        return LinkResolver.resolveCallSiteOrNull(ctx, currentKlass, symbolicResolution, callSiteType, symbolicHolder);
    }
}
