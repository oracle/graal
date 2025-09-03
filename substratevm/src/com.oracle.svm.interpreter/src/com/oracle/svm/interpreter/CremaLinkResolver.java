/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.interpreter;

import com.oracle.svm.espresso.classfile.descriptors.Name;
import com.oracle.svm.espresso.classfile.descriptors.Signature;
import com.oracle.svm.espresso.classfile.descriptors.Symbol;
import com.oracle.svm.espresso.classfile.descriptors.Type;
import com.oracle.svm.espresso.shared.resolver.CallSiteType;
import com.oracle.svm.espresso.shared.resolver.FieldAccessType;
import com.oracle.svm.espresso.shared.resolver.LinkResolver;
import com.oracle.svm.espresso.shared.resolver.ResolvedCall;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedJavaField;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedJavaMethod;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedJavaType;

public final class CremaLinkResolver {
    private CremaLinkResolver() {
        // no instance.
    }

    public static InterpreterResolvedJavaField resolveFieldSymbolOrThrow(CremaRuntimeAccess runtime, InterpreterResolvedJavaType accessingClass,
                    Symbol<Name> name, Symbol<Type> type, InterpreterResolvedJavaType symbolicHolder,
                    boolean accessCheck, boolean loadingConstraints) {
        return LinkResolver.resolveFieldSymbolOrThrow(runtime, accessingClass, name, type, symbolicHolder, accessCheck, loadingConstraints);
    }

    public static InterpreterResolvedJavaField resolveFieldSymbolOrNull(CremaRuntimeAccess runtime, InterpreterResolvedJavaType accessingClass,
                    Symbol<Name> name, Symbol<Type> type, InterpreterResolvedJavaType symbolicHolder,
                    boolean accessCheck, boolean loadingConstraints) {
        return LinkResolver.resolveFieldSymbolOrNull(runtime, accessingClass, name, type, symbolicHolder, accessCheck, loadingConstraints);
    }

    public static void checkFieldAccessOrThrow(CremaRuntimeAccess runtime, InterpreterResolvedJavaField symbolicResolution, FieldAccessType fieldAccessType, InterpreterResolvedJavaType currentClass,
                    InterpreterResolvedJavaMethod currentMethod) {
        LinkResolver.checkFieldAccessOrThrow(runtime, symbolicResolution, fieldAccessType, currentClass, currentMethod);
    }

    public static boolean checkFieldAccess(CremaRuntimeAccess runtime, InterpreterResolvedJavaField symbolicResolution, FieldAccessType fieldAccessType, InterpreterResolvedJavaType currentClass,
                    InterpreterResolvedJavaMethod currentMethod) {
        return LinkResolver.checkFieldAccess(runtime, symbolicResolution, fieldAccessType, currentClass, currentMethod);
    }

    public static InterpreterResolvedJavaMethod resolveMethodSymbol(CremaRuntimeAccess runtime, InterpreterResolvedJavaType accessingClass,
                    Symbol<Name> name, Symbol<Signature> signature, InterpreterResolvedJavaType symbolicHolder,
                    boolean interfaceLookup,
                    boolean accessCheck, boolean loadingConstraints) {
        return LinkResolver.resolveMethodSymbol(runtime, accessingClass, name, signature, symbolicHolder, interfaceLookup, accessCheck, loadingConstraints);
    }

    public static InterpreterResolvedJavaMethod resolveMethodSymbolOrNull(CremaRuntimeAccess runtime, InterpreterResolvedJavaType accessingClass,
                    Symbol<Name> name, Symbol<Signature> signature, InterpreterResolvedJavaType symbolicHolder,
                    boolean interfaceLookup,
                    boolean accessCheck, boolean loadingConstraints) {
        return LinkResolver.resolveMethodSymbolOrNull(runtime, accessingClass, name, signature, symbolicHolder, interfaceLookup, accessCheck, loadingConstraints);
    }

    public static ResolvedCall<InterpreterResolvedJavaType, InterpreterResolvedJavaMethod, InterpreterResolvedJavaField> resolveCallSiteOrThrow(
                    CremaRuntimeAccess runtime, InterpreterResolvedJavaType currentClass, InterpreterResolvedJavaMethod symbolicResolution, CallSiteType callSiteType,
                    InterpreterResolvedJavaType symbolicHolder) {
        return LinkResolver.resolveCallSiteOrThrow(runtime, currentClass, symbolicResolution, callSiteType, symbolicHolder);
    }

    public static ResolvedCall<InterpreterResolvedJavaType, InterpreterResolvedJavaMethod, InterpreterResolvedJavaField> resolveCallSiteOrNull(CremaRuntimeAccess runtime,
                    InterpreterResolvedJavaType currentClass, InterpreterResolvedJavaMethod symbolicResolution, CallSiteType callSiteType, InterpreterResolvedJavaType symbolicHolder) {
        return LinkResolver.resolveCallSiteOrNull(runtime, currentClass, symbolicResolution, callSiteType, symbolicHolder);
    }
}
