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

import com.oracle.svm.core.util.VMError;
import com.oracle.svm.util.ReflectionUtil;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Signature;
import org.graalvm.nativeimage.ImageSingletons;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;

/**
 * An abstract provider class that defines a protocol for generating unique short names for Java
 * methods and fields which are compatible for use as local linker symbols. An instance of this
 * class may be registered as an image singleton in order to ensure that a specific naming
 * convention is adopted.
 */
public abstract class UniqueShortNameProvider {
    private static UniqueShortNameProvider provider = null;

    public static synchronized UniqueShortNameProvider provider() {
        if (provider == null) {
            if (ImageSingletons.contains(UniqueShortNameProvider.class)) {
                provider = ImageSingletons.lookup(UniqueShortNameProvider.class);
            } else {
                provider = new DefaultUniqueShortNameProvider();
                ImageSingletons.add(UniqueShortNameProvider.class, provider);
            }
        }
        return provider;
    }

    /**
     * Returns a short, reasonably descriptive, but still unique name for the provided method which
     * can be used as a linker local symbol.
     */
    public abstract String uniqueShortName(ResolvedJavaMethod method);

    /**
     * Returns a short, reasonably descriptive, but still unique name for a method as characterized
     * by its loader name, owner class, method selector, signature and status as a method proper or
     * a constructor.
     */
    public abstract String uniqueShortName(String loaderName, ResolvedJavaType declaringClass, String methodName, Signature methodSignature, boolean isConstructor);

    /**
     * Returns a short, reasonably descriptive, but still unique name for the provided
     * {@link Method}, {@link Constructor}, or {@link Field}.
     */
    public abstract String uniqueShortName(Member m);

    /**
     * Returns a short, reasonably descriptive, but still unique name derived from the provided
     * method that can be used as the name of a stub method defined on some factory class that
     * invokes it. Note that although the value returned by this method is unique to the called
     * method it's purpose is to uniquely define a single component of the full name of the stub
     * method i.e. the method selector.
     */

    public abstract String uniqueStubName(ResolvedJavaMethod method);

    /**
     * Returns a unique identifier for a class loader that can be folded into the unique short name
     * of methods where needed in order to disambiguate name collisions that can arise when the same
     * class bytecode is loaded by more than one loader. For a few special loaders, such as the JDK
     * runtime's builtin loaders and GraalVM Native's top level loaders an empty string is returned.
     * There is no need to qualify the method name in this case since the method defined via these
     * loaders is taken to be the primary instance. This is safe so long as the delegation model for
     * this special set of loaders ensures that none of them will replicate a class loaded by
     * another loader in the set.
     * 
     * @param loader The loader whose identifier is to be returned.
     * @return A unique identifier for the classloader or the empty string when the loader is one of
     *         the special set whose method names do not need qualification.
     */
    public abstract String classLoaderNameAndId(ClassLoader loader);

    private static final Field classLoaderNameAndId = ReflectionUtil.lookupField(ClassLoader.class, "nameAndId");

    protected String lookupNameAndIdField(ClassLoader loader) {
        try {
            return (String) classLoaderNameAndId.get(loader);
        } catch (IllegalAccessException e) {
            throw VMError.shouldNotReachHere("Cannot reflectively access ClassLoader.nameAndId");
        }
    }

    /**
     * Default implementation for unique method and field short names which concatenates the
     * (unqualified) owner class name and method or field selector with an SHA1 digest of the fully
     * qualified Java name of the method or field. If a loader prefix is provided it is added as
     * prefix to the Java name before generating the SHA1 digest.
     */
    private static class DefaultUniqueShortNameProvider extends UniqueShortNameProvider {
        @Override
        public String uniqueShortName(ResolvedJavaMethod m) {
            return uniqueShortName("", m.getDeclaringClass(), m.getName(), m.getSignature(), m.isConstructor());
        }

        @Override
        public String uniqueShortName(String loaderName, ResolvedJavaType declaringClass, String methodName, Signature methodSignature, boolean isConstructor) {
            StringBuilder sb = new StringBuilder(loaderName);
            sb.append(declaringClass.toClassName()).append(".").append(methodName).append("(");
            for (int i = 0; i < methodSignature.getParameterCount(false); i++) {
                sb.append(methodSignature.getParameterType(i, null).toClassName()).append(",");
            }
            sb.append(')');
            if (!isConstructor) {
                sb.append(methodSignature.getReturnType(null).toClassName());
            }

            return stripPackage(declaringClass.toJavaName()) + "_" +
                            (isConstructor ? "constructor" : methodName) + "_" +
                            SubstrateUtil.digest(sb.toString());
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

            return stripPackage(m.getDeclaringClass().getTypeName()) + "_" +
                            (m instanceof Constructor ? "constructor" : m.getName()) + "_" +
                            SubstrateUtil.digest(fullName.toString());
        }

        @Override
        public String uniqueStubName(ResolvedJavaMethod m) {
            // the default short name works as a name for the stub method
            return uniqueShortName("", m.getDeclaringClass(), m.getName(), m.getSignature(), m.isConstructor());
        }

        @Override
        public String classLoaderNameAndId(ClassLoader loader) {
            // no need to qualify classes loaded by a bootstrap loader
            if (loader == null) {
                return "";
            }
            String name = lookupNameAndIdField(loader);
            // non-builtin loader names will look like "org.foo.bar.FooBarClassLoader @1234"
            // trim them down to something more readable
            name = stripPackage(name);
            name = name.replace(" @", "_");
            return name;
        }
    }

    private static String stripPackage(String qualifiedClassName) {
        /* Anonymous classes can contain a '/' which can lead to an invalid binary name. */
        return qualifiedClassName.substring(qualifiedClassName.lastIndexOf(".") + 1).replace("/", "");
    }

}
