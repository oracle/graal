/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2022, 2022, Alibaba Group Holding Limited. All rights reserved.
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

package com.oracle.graal.pointsto.standalone.replacements;

import com.oracle.graal.pointsto.infrastructure.SubstitutionProcessor;
import com.oracle.graal.pointsto.standalone.StandaloneAnalysisClassLoader;
import com.oracle.graal.pointsto.util.AnalysisError;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * This class process the substitution of java.security.AccessController#doPrivileged methods for
 * standalone pointsto. These native methods will eventually invoke the code defined in the
 * PrivilegedAction parameter, so we replace the doPrivileged function with its parameter function.
 */
public class AccessControllerSubstitutionProcessor extends SubstitutionProcessor {

    public static final String DO_PRIVILEGED = "doPrivileged";
    private MetaAccessProvider metaAccess;
    private StandaloneAnalysisClassLoader aClassLoader;
    private final Class<?> accessControllerSubstitutionClass;
    private Map<ResolvedJavaMethod, ResolvedJavaMethod> substitutions;

    public AccessControllerSubstitutionProcessor(MetaAccessProvider metaAccess, StandaloneAnalysisClassLoader aClassLoader) {
        this.metaAccess = metaAccess;
        this.aClassLoader = aClassLoader;
        accessControllerSubstitutionClass = aClassLoader.defineClassFromOtherClassLoader(AccessControllerSubstitution.class);
        setUpSubstitutionMap();
    }

    private void setUpSubstitutionMap() {
        substitutions = new HashMap<>();
        try {
            Class<?> originalClass = Class.forName("java.security.AccessController", false, aClassLoader);
            Arrays.stream(originalClass.getDeclaredMethods()).filter(method -> method.getName().equals(DO_PRIVILEGED)).forEach(
                            method -> {
                                try {
                                    Method substituteExecutable = accessControllerSubstitutionClass.getDeclaredMethod(DO_PRIVILEGED, method.getParameterTypes());
                                    ResolvedJavaMethod original = metaAccess.lookupJavaMethod(method);
                                    ResolvedJavaMethod substitute = metaAccess.lookupJavaMethod(substituteExecutable);
                                    substitutions.put(original, substitute);
                                } catch (NoSuchMethodException e) {
                                    // ignore, we didn't substitute all doPrivileged functions
                                }
                            });
        } catch (ClassNotFoundException e) {
            AnalysisError.shouldNotReachHere(e);
        }
    }

    @Override
    public ResolvedJavaMethod lookup(ResolvedJavaMethod method) {
        ResolvedJavaMethod ret = substitutions.get(method);
        return ret == null ? method : ret;
    }
}
