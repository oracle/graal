/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.function.BooleanSupplier;

import com.oracle.svm.core.feature.AutomaticallyRegisteredImageSingleton;

import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Signature;

/**
 * Default implementation for unique method and field short names which concatenates the
 * (unqualified) owner class name and method or field selector with an SHA1 digest of the fully
 * qualified Java name of the method or field. If a loader prefix is provided it is added as prefix
 * to the Java name before generating the SHA1 digest.
 */
@AutomaticallyRegisteredImageSingleton(value = UniqueShortNameProvider.class, onlyWith = UniqueShortNameProviderDefaultImpl.UseDefault.class)
public class UniqueShortNameProviderDefaultImpl implements UniqueShortNameProvider {
    @Override
    public String uniqueShortName(ClassLoader loader, ResolvedJavaType declaringClass, String methodName, Signature methodSignature, boolean isConstructor) {
        return SubstrateUtil.uniqueShortName(SubstrateUtil.classLoaderNameAndId(loader), declaringClass, methodName, methodSignature, isConstructor);
    }

    @Override
    public String uniqueShortName(Member m) {
        StringBuilder fullName = new StringBuilder();
        fullName.append(m.getDeclaringClass().getName()).append(".");
        if (m instanceof Constructor) {
            fullName.append("<init>");
        } else {
            fullName.append(m.getName());
        }
        if (m instanceof Executable) {
            fullName.append("(");
            for (Class<?> c : ((Executable) m).getParameterTypes()) {
                fullName.append(c.getName()).append(",");
            }
            fullName.append(')');
            if (m instanceof Method) {
                fullName.append(((Method) m).getReturnType().getName());
            }
        }

        return SubstrateUtil.stripPackage(m.getDeclaringClass().getTypeName()) + "_" +
                        (m instanceof Constructor ? "constructor" : m.getName()) + "_" +
                        SubstrateUtil.digest(fullName.toString());
    }

    @Override
    public String uniqueShortLoaderName(ClassLoader classLoader) {
        return SubstrateUtil.classLoaderNameAndId(classLoader);
    }

    public static class UseDefault implements BooleanSupplier {

        public static boolean useDefaultProvider() {
            return !OS.LINUX.isCurrent() || !SubstrateOptions.useDebugInfoGeneration();
        }

        @Override
        public boolean getAsBoolean() {
            return useDefaultProvider();
        }
    }
}
