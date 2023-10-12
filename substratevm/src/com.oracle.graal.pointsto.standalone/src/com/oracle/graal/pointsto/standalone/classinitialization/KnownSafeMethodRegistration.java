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

package com.oracle.graal.pointsto.standalone.classinitialization;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.standalone.MethodConfigReader;
import com.oracle.svm.util.ReflectionUtil;
import org.graalvm.compiler.serviceprovider.JavaVersionUtil;

import java.lang.reflect.Executable;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public final class KnownSafeMethodRegistration {
    private final Set<Executable> knownSafeMethods = new HashSet<>();
    private static final KnownSafeMethodRegistration instance = new KnownSafeMethodRegistration();

    public static KnownSafeMethodRegistration getInstance() {
        return instance;
    }

    private KnownSafeMethodRegistration() {
        registerSafeMethodsInObject();
        registerSafeMethodsInClass();
    }

    private void registerSafeMethodsInObject() {
        if (JavaVersionUtil.JAVA_SPEC < 17) {
            knownSafeMethods.add(ReflectionUtil.lookupMethod(Object.class, "registerNatives"));
        }
        // knownSafeMethods.add(metaAccess.lookupJavaMethod(ReflectionUtil.lookupMethod(Object.class,
        // "getClass")));
        // knownSafeMethods.add(metaAccess.lookupJavaMethod(ReflectionUtil.lookupMethod(Object.class,
        // "clone")));
        // knownSafeMethods.add(metaAccess.lookupJavaMethod(ReflectionUtil.lookupMethod(Object.class,
        // "hashCode")));
    }

    private void registerSafeMethodsInClass() {
        // The following native methods are safe
        knownSafeMethods.add(ReflectionUtil.lookupMethod(Class.class, "registerNatives"));
        knownSafeMethods.add(ReflectionUtil.lookupMethod(Class.class, "getPrimitiveClass", String.class));
        knownSafeMethods.add(ReflectionUtil.lookupMethod(Class.class, "desiredAssertionStatus0", Class.class));
        knownSafeMethods.add(ReflectionUtil.lookupMethod(Class.class, "initClassName"));
        knownSafeMethods.add(ReflectionUtil.lookupMethod(Class.class, "getSuperclass"));
        knownSafeMethods.add(ReflectionUtil.lookupMethod(Class.class, "getInterfaces0"));
        knownSafeMethods.add(ReflectionUtil.lookupMethod(Class.class, "getModifiers"));
        knownSafeMethods.add(ReflectionUtil.lookupMethod(Class.class, "getSigners"));
        knownSafeMethods.add(ReflectionUtil.lookupMethod(Class.class, "setSigners", Object[].class));
        knownSafeMethods.add(ReflectionUtil.lookupMethod(Class.class, "getEnclosingMethod0"));
        knownSafeMethods.add(ReflectionUtil.lookupMethod(Class.class, "getDeclaringClass0"));
        knownSafeMethods.add(ReflectionUtil.lookupMethod(Class.class, "getSimpleBinaryName0"));
        knownSafeMethods.add(ReflectionUtil.lookupMethod(Class.class, "getGenericSignature0"));
        knownSafeMethods.add(ReflectionUtil.lookupMethod(Class.class, "getRawAnnotations"));
        knownSafeMethods.add(ReflectionUtil.lookupMethod(Class.class, "getRawTypeAnnotations"));
        knownSafeMethods.add(ReflectionUtil.lookupMethod(Class.class, "getConstantPool"));
        knownSafeMethods.add(ReflectionUtil.lookupMethod(Class.class, "getDeclaredFields0", boolean.class));
        knownSafeMethods.add(ReflectionUtil.lookupMethod(Class.class, "getDeclaredMethods0", boolean.class));
        knownSafeMethods.add(ReflectionUtil.lookupMethod(Class.class, "getDeclaredConstructors0", boolean.class));
        knownSafeMethods.add(ReflectionUtil.lookupMethod(Class.class, "getDeclaredClasses0"));
        knownSafeMethods.add(ReflectionUtil.lookupMethod(Class.class, "getNestMembers0"));
        knownSafeMethods.add(ReflectionUtil.lookupMethod(Class.class, "getNestHost0"));

        // Class.desiredAssertionStatus is used to set up assertion status by JVM.
        knownSafeMethods.add(ReflectionUtil.lookupMethod(Class.class, "desiredAssertionStatus"));
    }

    public Set<AnalysisMethod> registerConfiguredMethods(String safeMethodsConfigFile, BigBang bigbang, ClassLoader classLoader) {
        Set<Executable> configuredSafeMethods = new HashSet<>();
        if (safeMethodsConfigFile != null && safeMethodsConfigFile.length() > 0) {
            MethodConfigReader.readMethodFromFile(safeMethodsConfigFile, bigbang, classLoader, m -> configuredSafeMethods.add(m.getJavaMethod()));
        }
        configuredSafeMethods.addAll(knownSafeMethods);
        return configuredSafeMethods.stream().map(method -> bigbang.getMetaAccess().lookupJavaMethod(method)).collect(Collectors.toSet());
    }
}
