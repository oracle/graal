/*
 * Copyright (c) 2015, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.guest.staging;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.shared.singletons.traits.BuiltinTraits.AllAccess;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.NoLayeredCallbacks;
import com.oracle.svm.shared.singletons.traits.SingletonLayeredInstallationKind.ApplicationLayerOnly;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;
import com.oracle.svm.shared.util.ClassUtil;
import com.oracle.svm.shared.util.ModuleSupport;
import com.oracle.svm.shared.util.ModuleSupport.Access;
import com.oracle.svm.shared.util.ReflectionUtil;

/**
 * Main-method state installed by the image builder for Java main images.
 * <p>
 * This class contains the method handles, declaring class name, and argument state needed to invoke
 * the application Java main method. Launcher control flow, C argument handling, thread setup,
 * shutdown, and other runtime behavior are owned by {@code com.oracle.svm.core.JavaMainWrapper}.
 */
@SingletonTraits(access = AllAccess.class, layeredCallbacks = NoLayeredCallbacks.class, layeredInstallationKind = ApplicationLayerOnly.class)
public class JavaMainSupport {
    /**
     * Method handle used by the launcher to invoke the application Java main method.
     */
    public final MethodHandle javaMainHandle;

    /**
     * Constructor handle used when the application declares an instance main method, or {@code null}
     * for static main methods.
     */
    public final MethodHandle javaMainClassCtorHandle;

    /**
     * Fully qualified name of the class declaring the application Java main method.
     */
    public final String javaMainClassName;

    /**
     * Parsed arguments passed to the application Java main method after runtime option consumption.
     */
    public String[] mainArgs;

    /**
     * Whether the application Java main method has no argument parameter.
     */
    public final boolean mainWithoutArgs;

    /**
     * Whether the application Java main method must be invoked on a newly constructed receiver.
     */
    public final boolean mainNonstatic;

    /**
     * Creates support state for invoking {@code javaMainMethod} at image runtime.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    public JavaMainSupport(Method javaMainMethod) throws IllegalAccessException {
        int mods = javaMainMethod.getModifiers();
        this.mainNonstatic = !Modifier.isStatic(mods);
        this.mainWithoutArgs = javaMainMethod.getParameterCount() == 0;

        makeUnreflectable(javaMainMethod);

        MethodHandle mainHandle = MethodHandles.lookup().unreflect(javaMainMethod);
        MethodHandle ctorHandle = null;
        Class<?> javaMainClass = javaMainMethod.getDeclaringClass();
        if (mainNonstatic) {
            /*
             * Instance main.
             */
            try {
                Constructor<?> ctor = ReflectionUtil.lookupConstructor(javaMainClass);
                ctorHandle = MethodHandles.lookup().unreflectConstructor(ctor);
            } catch (ReflectionUtil.ReflectionUtilError ex) {
                throw new IllegalArgumentException("No non-private zero argument constructor found in class " + ClassUtil.getUnqualifiedName(javaMainClass), ex);
            }
        }
        this.javaMainHandle = mainHandle;
        this.javaMainClassCtorHandle = ctorHandle;
        this.javaMainClassName = javaMainClass.getName();
    }

    /**
     * Ensures {@code method} can be converted via {@link Lookup#unreflect} to a
     * {@link MethodHandle}.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    @SuppressWarnings("deprecation")
    private static void makeUnreflectable(Method method) {
        if (!method.isAccessible()) {
            Class<?> declaringClass = method.getDeclaringClass();
            Module module = declaringClass.getModule();
            if (module.isNamed()) {
                Module myModule = JavaMainSupport.class.getModule();
                String declaringPackage = declaringClass.getPackageName();
                if (!module.isExported(declaringPackage, myModule)) {
                    /*
                     * Package containing main method must be exported for Method.setAccessible to
                     * succeed.
                     */
                    ModuleSupport.accessModule(Access.EXPORT, myModule, module, declaringPackage);
                }
            }
            method.setAccessible(true);
        }
    }

    /**
     * Returns the Java command shown by management APIs, consisting of the main class name followed by
     * the application main arguments, or {@code null} before main arguments are available.
     */
    public String getJavaCommand() {
        if (mainArgs != null) {
            StringBuilder commandLine = new StringBuilder(javaMainClassName);

            for (String arg : mainArgs) {
                commandLine.append(' ');
                commandLine.append(arg);
            }
            return commandLine.toString();
        }
        return null;
    }

    /**
     * Returns the VM input arguments by subtracting the parsed application main arguments from the
     * original Java arguments captured when the VM was created.
     */
    public List<String> getInputArguments() {
        String[] initialArgs = ArgsSupport.singleton().getInitialArgs();
        if (initialArgs != null) {
            List<String> inputArgs = new ArrayList<>(Arrays.asList(initialArgs));

            if (mainArgs != null) {
                inputArgs.removeAll(Arrays.asList(mainArgs));
            }
            return Collections.unmodifiableList(inputArgs);
        }
        return Collections.emptyList();
    }
}
