/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.cenum;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.graalvm.nativeimage.c.constant.CEnumLookup;

import com.oracle.graal.pointsto.infrastructure.SubstitutionProcessor;
import com.oracle.svm.hosted.c.NativeLibraries;

import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Substitutes methods declared as {@code native} with {@link CEnumLookup} annotation with a
 * synthetic graph that calls the appropriate EnumRuntimeData.convertCToJava(long) method.
 */
public class CEnumLookupCallWrapperSubstitutionProcessor extends SubstitutionProcessor {

    private final Map<ResolvedJavaMethod, CEnumLookupCallWrapperMethod> callWrappers = new ConcurrentHashMap<>();

    public CEnumLookupCallWrapperSubstitutionProcessor() {
        super();
    }

    public void setNativeLibraries(NativeLibraries nativeLibraries) {
        for (CEnumLookupCallWrapperMethod m : callWrappers.values()) {
            m.setNativeLibraries(nativeLibraries);
        }
    }

    @Override
    public ResolvedJavaMethod lookup(ResolvedJavaMethod method) {
        if (method.getAnnotation(CEnumLookup.class) != null) {
            return callWrappers.computeIfAbsent(method, CEnumLookupCallWrapperMethod::new);
        } else {
            return method;
        }
    }

    @Override
    public ResolvedJavaMethod resolve(ResolvedJavaMethod method) {
        if (method instanceof CEnumLookupCallWrapperMethod) {
            return ((CEnumLookupCallWrapperMethod) method).getOriginal();
        }
        return method;
    }
}
