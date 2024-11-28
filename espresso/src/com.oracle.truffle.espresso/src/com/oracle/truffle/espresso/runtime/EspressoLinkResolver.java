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

import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.shared.descriptors.Symbol;
import com.oracle.truffle.espresso.shared.resolver.CallSiteType;
import com.oracle.truffle.espresso.shared.resolver.FieldAccessType;
import com.oracle.truffle.espresso.shared.resolver.LinkResolver;
import com.oracle.truffle.espresso.shared.resolver.ResolvedCall;

public final class EspressoLinkResolver {
    public static Field resolveFieldSymbol(EspressoContext ctx, Klass accessingKlass,
                    Symbol<Symbol.Name> name, Symbol<Symbol.Type> type, Klass symbolicHolder,
                    boolean accessCheck, boolean loadingConstraints) {
        return LinkResolver.resolveFieldSymbol(ctx, accessingKlass, name, type, symbolicHolder, accessCheck, loadingConstraints);
    }

    public static Field resolveFieldAccess(EspressoContext ctx, Field symbolicResolution, FieldAccessType fieldAccessType, Klass currentKlass, Method currentMethod) {
        return LinkResolver.resolveFieldAccess(ctx, symbolicResolution, fieldAccessType, currentKlass, currentMethod);
    }

    public static Method resolveMethodSymbol(EspressoContext ctx, Klass accessingKlass,
                    Symbol<Symbol.Name> name, Symbol<Symbol.Signature> signature, Klass symbolicHolder,
                    boolean interfaceLookup,
                    boolean accessCheck, boolean loadingConstraints) {
        return LinkResolver.resolveMethodSymbol(ctx, accessingKlass, name, signature, symbolicHolder, interfaceLookup, accessCheck, loadingConstraints);
    }

    public static ResolvedCall<Klass, Method, Field> resolveCallSite(EspressoContext ctx, Klass currentKlass, Method symbolicResolution, CallSiteType callSiteType, Klass symbolicHolder) {
        return LinkResolver.resolveCallSite(ctx, currentKlass, symbolicResolution, callSiteType, symbolicHolder);
    }

    private EspressoLinkResolver() {
        // no instance.
    }
}
