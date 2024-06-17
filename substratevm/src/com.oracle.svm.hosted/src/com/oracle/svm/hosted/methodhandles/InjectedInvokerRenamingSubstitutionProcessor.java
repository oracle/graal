/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.methodhandles;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.oracle.graal.pointsto.infrastructure.SubstitutionProcessor;
import com.oracle.graal.pointsto.meta.BaseLayerType;

import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * This substitution replaces all injected invoker types with types that have a stable names. The
 * name is formed from the name of the class containing the injected invoker. The host class is
 * enough as the injected invoker is cached for each class and reused when needed.
 */

public class InjectedInvokerRenamingSubstitutionProcessor extends SubstitutionProcessor {
    public static final String INJECTED_INVOKER_CLASS_NAME_SUBSTRING = "$$InjectedInvoker";

    private final ConcurrentMap<ResolvedJavaType, InjectedInvokerSubstitutionType> typeSubstitutions = new ConcurrentHashMap<>();

    public static boolean isInjectedInvokerType(ResolvedJavaType type) {
        String name = type.getName();
        return name.contains(INJECTED_INVOKER_CLASS_NAME_SUBSTRING);
    }

    @Override
    public ResolvedJavaType lookup(ResolvedJavaType type) {
        if (!shouldReplace(type)) {
            return type;
        }
        return getSubstitution(type);
    }

    private static boolean shouldReplace(ResolvedJavaType type) {
        return !(type instanceof InjectedInvokerSubstitutionType) && !(type instanceof BaseLayerType) && isInjectedInvokerType(type);
    }

    private InjectedInvokerSubstitutionType getSubstitution(ResolvedJavaType original) {
        return typeSubstitutions.computeIfAbsent(original, key -> new InjectedInvokerSubstitutionType(key, getUniqueInjectedInvokerName(key.getName())));
    }

    public static String getUniqueInjectedInvokerName(String className) {
        return className.substring(0, className.lastIndexOf(INJECTED_INVOKER_CLASS_NAME_SUBSTRING) + INJECTED_INVOKER_CLASS_NAME_SUBSTRING.length()) + ";";
    }
}
