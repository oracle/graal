/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.graphbuilderconf;

import java.lang.reflect.*;
import java.util.*;
import java.util.stream.*;

import sun.misc.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.graphbuilderconf.InvocationPlugins.Receiver;
import com.oracle.graal.nodes.*;

/**
 * An {@link InvocationPlugin} for a method where the implementation of the method is provided by a
 * {@linkplain #getSubstitute(MetaAccessProvider) substitute} method. A substitute method must be
 * static even if the substituted method is not.
 */
public final class MethodSubstitutionPlugin implements InvocationPlugin {

    private ResolvedJavaMethod cachedSubstitute;
    private final Class<?> declaringClass;
    private final String name;
    private final Class<?>[] parameters;
    private final boolean originalIsStatic;

    /**
     * Creates a method substitution plugin.
     *
     * @param declaringClass the class in which the substitute method is declared
     * @param name the name of the substitute method
     * @param parameters the parameter types of the substitute method. If the original method is not
     *            static, then {@code parameters[0]} must be the {@link Class} value denoting
     *            {@link Receiver}
     */
    public MethodSubstitutionPlugin(Class<?> declaringClass, String name, Class<?>... parameters) {
        this.declaringClass = declaringClass;
        this.name = name;
        this.parameters = parameters;
        this.originalIsStatic = parameters.length == 0 || parameters[0] != Receiver.class;
    }

    /**
     * Gets the substitute method, resolving it first if necessary.
     */
    public ResolvedJavaMethod getSubstitute(MetaAccessProvider metaAccess) {
        if (cachedSubstitute == null) {
            cachedSubstitute = metaAccess.lookupJavaMethod(getJavaSubstitute());
        }
        return cachedSubstitute;
    }

    /**
     * Gets the reflection API version of the substitution method.
     */
    Method getJavaSubstitute() throws GraalInternalError {
        Method substituteMethod = lookupSubstitute();
        int modifiers = substituteMethod.getModifiers();
        if (Modifier.isAbstract(modifiers) || Modifier.isNative(modifiers)) {
            throw new GraalInternalError("Substitution method must not be abstract or native: " + substituteMethod);
        }
        if (!Modifier.isStatic(modifiers)) {
            throw new GraalInternalError("Substitution method must be static: " + substituteMethod);
        }
        return substituteMethod;
    }

    /**
     * Determines if a given method is the substitute method of this plugin.
     */
    private boolean isSubstitute(Method m) {
        if (Modifier.isStatic(m.getModifiers()) && m.getName().equals(name)) {
            if (parameters.length == m.getParameterCount()) {
                Class<?>[] mparams = m.getParameterTypes();
                int start = 0;
                if (!originalIsStatic) {
                    start = 1;
                    if (!mparams[0].isAssignableFrom(parameters[0])) {
                        return false;
                    }
                }
                for (int i = start; i < mparams.length; i++) {
                    if (mparams[i] != parameters[i]) {
                        return false;
                    }
                }
            }
            return true;
        }
        return false;
    }

    /**
     * Gets the substitute method of this plugin.
     */
    private Method lookupSubstitute() {
        for (Method m : declaringClass.getDeclaredMethods()) {
            if (isSubstitute(m)) {
                return m;
            }
        }
        throw new GraalInternalError("No method found in %s compatible with the signature (%s)", declaringClass.getName(), Arrays.asList(parameters).stream().map(c -> c.getSimpleName()).collect(
                        Collectors.joining(",")));
    }

    /**
     * Resolves a name to a class.
     *
     * @param className the name of the class to resolve
     * @param optional if true, resolution failure returns null
     * @return the resolved class or null if resolution fails and {@code optional} is true
     */
    public static Class<?> resolveClass(String className, boolean optional) {
        try {
            // Need to use launcher class path to handle classes
            // that are not on the boot class path
            ClassLoader cl = Launcher.getLauncher().getClassLoader();
            return Class.forName(className, false, cl);
        } catch (ClassNotFoundException e) {
            if (optional) {
                return null;
            }
            throw new GraalInternalError("Could not resolve type " + className);
        }
    }

    @Override
    public boolean execute(GraphBuilderContext b, ResolvedJavaMethod targetMethod, Receiver receiver, ValueNode[] argsIncludingReceiver) {
        ResolvedJavaMethod subst = getSubstitute(b.getMetaAccess());
        if (receiver != null) {
            receiver.get();
        }
        b.intrinsify(targetMethod, subst, argsIncludingReceiver);
        return true;
    }

    public StackTraceElement getApplySourceLocation(MetaAccessProvider metaAccess) {
        Class<?> c = getClass();
        for (Method m : c.getDeclaredMethods()) {
            if (m.getName().equals("execute")) {
                return metaAccess.lookupJavaMethod(m).asStackTraceElement(0);
            }
        }
        throw new GraalInternalError("could not find method named \"execute\" in " + c.getName());
    }
}
