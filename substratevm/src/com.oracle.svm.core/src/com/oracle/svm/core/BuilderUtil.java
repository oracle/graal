/*
 * Copyright (c) 2012, 2023, Oracle and/or its affiliates. All rights reserved.
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
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.UUID;

import com.oracle.svm.core.util.HostedSubstrateUtil;
import com.oracle.svm.shared.util.SubstrateUtil;

import com.oracle.svm.shared.util.ReflectionUtil;
import com.oracle.svm.shared.util.VMError;
import jdk.graal.compiler.graph.Node.NodeIntrinsic;
import jdk.graal.compiler.java.LambdaUtils;
import jdk.graal.compiler.nodes.BreakpointNode;
import jdk.graal.compiler.util.Digest;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Signature;

public class BuilderUtil {

    /**
     * Emits a node that triggers a breakpoint in debuggers.
     *
     * Eventually, this should be moved to guest code (GR-73917).
     *
     * @param arg0 value to inspect when the breakpoint hits
     * @see BreakpointNode how to use breakpoints and inspect breakpoint values in the debugger
     */
    @NodeIntrinsic(BreakpointNode.class)
    public static native void breakpoint(Object arg0);

    /**
     * Convenience method that unwraps the method details and delegates to the currently registered
     * UniqueShortNameProvider image singleton with the significant exception that it always passes
     * null for the class loader.
     *
     * @param m a method whose unique short name is required
     * @return a unique short name for the method
     */
    public static String uniqueShortName(ResolvedJavaMethod m) {
        return UniqueShortNameProvider.singleton().uniqueShortName(null, m.getDeclaringClass(), m.getName(), m.getSignature(), m.isConstructor());
    }

    /**
     * Delegate to the corresponding method of the currently registered UniqueShortNameProvider
     * image singleton.
     *
     * @param loader the class loader for the method's owning class
     * @param declaringClass the method's declaring class
     * @param methodName the method's name
     * @param methodSignature the method's signature
     * @param isConstructor true if the method is a constructor otherwise false
     * @return a unique short name for the method
     */
    public static String uniqueShortName(ClassLoader loader, ResolvedJavaType declaringClass, String methodName, Signature methodSignature, boolean isConstructor) {
        return UniqueShortNameProvider.singleton().uniqueShortName(loader, declaringClass, methodName, methodSignature, isConstructor);
    }

    /**
     * Delegate to the corresponding method of the currently registered UniqueShortNameProvider
     * image singleton.
     *
     * @param m a member whose unique short name is required
     * @return a unique short name for the member
     */
    public static String uniqueShortName(Member m) {
        return UniqueShortNameProvider.singleton().uniqueShortName(m);
    }

    /**
     * Generate a unique short name to be used as the selector for a stub method which invokes the
     * supplied target method. Note that the returned name must be derived using the name and class
     * of the target method even though the stub method will be owned to another class. This ensures
     * that any two stubs which target corresponding methods whose selector name is identical will
     * end up with different stub names.
     *
     * @param m a stub target method for which a unique stub method selector name is required
     * @return a unique stub name for the method
     */
    public static String uniqueStubName(ResolvedJavaMethod m) {
        return defaultUniqueShortName("", m.getDeclaringClass(), m.getName(), m.getSignature(), m.isConstructor());
    }

    public static String defaultUniqueShortName(String loaderNameAndId, ResolvedJavaType declaringClass, String methodName, Signature methodSignature, boolean isConstructor) {
        StringBuilder sb = new StringBuilder(loaderNameAndId);
        sb.append(declaringClass.toClassName()).append(".").append(methodName).append("(");
        for (int i = 0; i < methodSignature.getParameterCount(false); i++) {
            sb.append(methodSignature.getParameterType(i, null).toClassName()).append(",");
        }
        sb.append(')');
        if (!isConstructor) {
            sb.append(methodSignature.getReturnType(null).toClassName());
        }

        return shortenClassName(stripPackage(declaringClass.toJavaName())) + "_" +
                        (isConstructor ? "" : stripExistingDigest(methodName) + "_") +
                        Digest.digest(sb.toString());
    }

    public static String defaultUniqueShortName(Member m) {
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

        return shortenClassName(stripPackage(m.getDeclaringClass().getTypeName())) + "_" +
                        (m instanceof Constructor ? "" : stripExistingDigest(m.getName()) + "_") +
                        Digest.digest(fullName.toString());
    }

    /**
     * Returns a unique identifier for a class loader that can be folded into the unique short name
     * of methods where needed in order to disambiguate name collisions that can arise when the same
     * class bytecode is loaded by more than one loader.
     *
     * @param loader The loader whose identifier is to be returned.
     * @return A unique identifier for the classloader or the empty string when the loader is one of
     *         the special set whose method names do not need qualification.
     */
    public static String runtimeClassLoaderNameAndId(ClassLoader loader) {
        ClassLoader runtimeClassLoader = SubstrateUtil.HOSTED ? HostedSubstrateUtil.getRuntimeClassLoader(loader) : loader;

        if (runtimeClassLoader == null) {
            return "";
        }
        try {
            return (String) classLoaderNameAndId.get(runtimeClassLoader);
        } catch (IllegalAccessException e) {
            throw VMError.shouldNotReachHere("Cannot reflectively access ClassLoader.nameAndId");
        }
    }

    public static int arrayTypeDimension(ResolvedJavaType arrayType) {
        int dimension = 0;
        ResolvedJavaType componentType = arrayType;
        while (componentType.isArray()) {
            componentType = componentType.getComponentType();
            dimension++;
        }
        return dimension;
    }

    private static Field classLoaderNameAndId = ReflectionUtil.lookupField(ClassLoader.class, "nameAndId");

    public static String stripPackage(String qualifiedClassName) {
        /* Anonymous classes can contain a '/' which can lead to an invalid binary name. */
        return qualifiedClassName.substring(qualifiedClassName.lastIndexOf(".") + 1).replace("/", "");
    }

    public static UUID getUUIDFromString(String value) {
        return Digest.digestAsUUID(value);
    }

    /**
     * Shorten lambda class names, as well as excessively long class names that can happen with
     * deeply nested inner classes. We keep the end of the class name, because the innermost classes
     * are the most interesting part of the name.
     */
    private static String shortenClassName(String className) {
        String result = className;

        /*
         * Lambda classes have a 32-byte digest (because hex encoding is required), so with the
         * prefix just the Lambda part is already longer than our desired maximum name. We keep only
         * the first part of the digest, which is sufficent to distinguish multiple lambdas defined
         * by the same holder class.
         */
        int lambdaStart = result.indexOf(LambdaUtils.LAMBDA_CLASS_NAME_SUBSTRING);
        if (lambdaStart != -1) {
            int start = lambdaStart + LambdaUtils.LAMBDA_CLASS_NAME_SUBSTRING.length() + LambdaUtils.ADDRESS_PREFIX.length();
            int keepHashLen = 8;
            if (result.length() > start + keepHashLen) {
                result = result.substring(0, lambdaStart) + "$$L" + result.substring(start, start + keepHashLen);
            }
        }

        int maxLen = 40;
        if (result.length() > maxLen) {
            result = result.substring(result.length() - maxLen, result.length());
        }
        return result;
    }

    /**
     * Strip off a potential {@link Digest} from the end of the name. Note that this is a heuristic
     * only, and can remove the tail of a name on accident if a separator char happens to be at the
     * same place where usually the digest separator character is expected. That is OK because the
     * shorter name does not need to be unique.
     */
    private static String stripExistingDigest(String name) {
        int digestLength = Digest.DIGEST_SIZE + 1;
        if (name.length() > digestLength && name.charAt(name.length() - digestLength) == '_') {
            return name.substring(0, name.length() - digestLength);
        }
        return name;
    }

}
