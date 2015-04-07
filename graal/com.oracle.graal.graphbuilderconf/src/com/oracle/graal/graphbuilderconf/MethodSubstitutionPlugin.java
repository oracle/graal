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

import sun.misc.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.api.replacements.*;
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.graphbuilderconf.InvocationPlugins.Receiver;
import com.oracle.graal.nodes.*;

/**
 * An {@link InvocationPlugin} for a method where the implementation of the method is provided by a
 * {@linkplain #getSubstitute(MetaAccessProvider) substitute} method. A substitute method must be
 * static even if the substituted method is not.
 */
public class MethodSubstitutionPlugin implements InvocationPlugin {

    private ResolvedJavaMethod cachedSubstitute;
    private final Class<?> declaringClass;
    private final String name;
    private final Class<?>[] parameters;

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
    }

    /**
     * Gets the substitute method, resolving it first if necessary.
     */
    public ResolvedJavaMethod getSubstitute(MetaAccessProvider metaAccess) {
        if (cachedSubstitute == null) {
            cachedSubstitute = metaAccess.lookupJavaMethod(getJavaSubstitute());
            assert cachedSubstitute.getAnnotation(MethodSubstitution.class) != null;
        }
        return cachedSubstitute;
    }

    /**
     * Gets the reflection API version of the substitution method.
     */
    Method getJavaSubstitute() throws GraalInternalError {
        try {
            Method substituteMethod = declaringClass.getDeclaredMethod(name, parameters);
            int modifiers = substituteMethod.getModifiers();
            if (Modifier.isAbstract(modifiers) || Modifier.isNative(modifiers)) {
                throw new GraalInternalError("Substitution method must not be abstract or native: " + substituteMethod);
            }
            if (!Modifier.isStatic(modifiers)) {
                throw new GraalInternalError("Substitution method must be static: " + substituteMethod);
            }
            return substituteMethod;
        } catch (NoSuchMethodException e) {
            throw new GraalInternalError(e);
        }
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
        b.intrinsify(targetMethod, subst, argsIncludingReceiver);
        return true;
    }
}
