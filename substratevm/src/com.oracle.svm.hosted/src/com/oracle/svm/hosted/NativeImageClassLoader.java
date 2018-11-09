/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted;

import static jdk.internal.org.objectweb.asm.Opcodes.ACC_ABSTRACT;
import static jdk.internal.org.objectweb.asm.Opcodes.ACC_INTERFACE;
import static jdk.internal.org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static jdk.internal.org.objectweb.asm.Opcodes.V1_8;

import java.net.URL;
import java.net.URLClassLoader;

import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.internal.org.objectweb.asm.Type;

public class NativeImageClassLoader extends URLClassLoader {

    /**
     * Marker interface for ghost classes. A ghost class is a synthetic replacement interface that
     * we generate for classes that cannot be found. Thus the NativeImageClassLoader.findClass()
     * will never throw a ClassNotFoundException.
     */
    public interface Ghost {
    }

    /**
     * When a type is missing from the classpath the {@link NativeImageClassLoader} intercepts it
     * and replaces it with a mock-up interface type. We call the replacement type a ghost type: it
     * has the same name as the original type but none of its structure. Using the ghost types
     * avoids running into LinkageErrors early in the parsing process and reporting them only when
     * the faulty code is actually reached.
     */
    public static boolean classIsMissing(Class<?> clazz) {
        return clazz != Ghost.class && Ghost.class.isAssignableFrom(clazz);
    }

    NativeImageClassLoader(URL[] urls, ClassLoader parent) {
        super(urls, parent);
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        Class<?> clazz;
        try {
            clazz = super.loadClass(name, resolve);
        } catch (ClassNotFoundException | NoClassDefFoundError e) {
            if (mustNotReturnGhost(true)) {
                throw e;
            }
            /* The class is missing. Generate a ghost interface and return it. */
            return defineClass(name);
        } catch (IncompatibleClassChangeError e) {
            /*
             * The IncompatibleClassChangeError can be thrown when a known subtype of a missing type
             * is loaded after the missing type has been previously loaded and a Ghost interface has
             * been created. The Ghost interface is seen as the super class of the class being
             * loaded and instead of throwing a ClassNotFoundException due to a missing class in the
             * hierarchy this leads to a "java.lang.IncompatibleClassChangeError: KnownClass has
             * interface MissingSuperClass in the image builder".
             */

            if (mustNotReturnGhost(true)) {
                /*
                 * Instead of throwing the IncompatibleClassChangeError we throw a
                 * ClassNotFoundException.
                 */
                throw new ClassNotFoundException(name);
            }
            /* The class is missing. Generate a ghost interface and return it. */
            return defineClass(name);
        }

        if (classIsMissing(clazz) && mustNotReturnGhost(false)) {
            throw new ClassNotFoundException(name);
        }
        return clazz;
    }

    /**
     * In special situations we don't want to generate/return a ghost class. For example when the
     * loading request comes from a call to Class.forName() or a recursive call to
     * NativeImageClassLoader.loadClass(). We only return ghost interfaces when the class loading
     * request is triggered by bytecode parsing.
     */
    private static boolean mustNotReturnGhost(boolean checkRecursiveLoaderCall) {
        StackTraceElement[] trace = Thread.currentThread().getStackTrace();
        int imageLoaderLoadClassCalls = 0;
        for (StackTraceElement element : trace) {
            /* Class.forName() should not trigger ghost class creation. */
            if (isCallTo(element, Class.class, "forName")) {
                return true;
            }

            /*
             * Recursive calls to NativeImageClassLoader.loadClass() should not trigger ghost class
             * creation.
             */
            if (checkRecursiveLoaderCall && isCallTo(element, NativeImageClassLoader.class, "loadClass")) {
                imageLoaderLoadClassCalls++;
                if (imageLoaderLoadClassCalls > 1) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isCallTo(StackTraceElement element, Class<?> clazz, String methodName) {
        return element.getClassName().equals(clazz.getName()) && element.getMethodName().equals(methodName);
    }

    private Class<?> defineClass(String name) {
        byte[] b = generateClass(name.replace('.', '/'));
        return defineClass(name, b, 0, b.length);
    }

    /** Generate an interface with the given name that extends Ghost. */
    private static byte[] generateClass(String internalName) {
        /*
         * According to the JVM class format spec (
         * https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-4.html):
         * 
         * "If the ACC_INTERFACE flag is set, the ACC_ABSTRACT flag must also be set [...]"
         */
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        cw.visit(V1_8, // Java 1.8
                        ACC_PUBLIC + ACC_INTERFACE + ACC_ABSTRACT, // public interface
                        internalName, // package and name
                        null, // signature (null means not generic)
                        "java/lang/Object", // superclass
                        new String[]{Type.getInternalName(Ghost.class)}); // interfaces
        return cw.toByteArray();
    }
}
