/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.replacements;

import static com.oracle.graal.api.meta.MetaUtil.*;
import static com.oracle.graal.replacements.ReplacementsInstaller.*;
import static java.lang.Thread.*;
import static java.lang.reflect.Modifier.*;

import java.lang.reflect.*;
import java.util.*;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.api.replacements.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.phases.*;

/**
 * Resolves field and method {@linkplain Alias aliases} to the aliased fields and methods.
 */
public class AliasResolutionPhase extends Phase {

    private final MetaAccessProvider runtime;

    public AliasResolutionPhase(MetaAccessProvider runtime) {
        this.runtime = runtime;
    }

    @Override
    protected void run(StructuredGraph graph) {
        for (LoadFieldNode loadField : graph.getNodes(LoadFieldNode.class)) {
            ResolvedJavaField field = loadField.field();
            Field aliasedField = getAliasedField(field);
            if (aliasedField != null) {
                LoadFieldNode replacement = graph.add(new LoadFieldNode(loadField.object(), runtime.lookupJavaField(aliasedField)));
                graph.replaceFixedWithFixed(loadField, replacement);
            }
        }
        for (StoreFieldNode storeField : graph.getNodes().filter(StoreFieldNode.class)) {
            ResolvedJavaField field = storeField.field();
            Field aliasedField = getAliasedField(field);
            if (aliasedField != null) {
                StoreFieldNode replacement = graph.add(new StoreFieldNode(storeField.object(), runtime.lookupJavaField(aliasedField), storeField.value()));
                graph.replaceFixedWithFixed(storeField, replacement);
            }
        }

        for (Invoke invoke : graph.getInvokes()) {
            if (invoke.callTarget() instanceof MethodCallTargetNode) {
                MethodCallTargetNode methodCallTarget = invoke.methodCallTarget();
                ResolvedJavaMethod method = methodCallTarget.targetMethod();
                Method aliasedMethod = getAliasedMethod(method);
                if (aliasedMethod != null) {
                    methodCallTarget.setTargetMethod(runtime.lookupJavaMethod(aliasedMethod));
                }
            }
        }
    }

    private static Field getAliasedField(ResolvedJavaField field) {
        Alias alias = field.getAnnotation(Alias.class);
        if (alias == null) {
            return null;
        }
        Class holder = declaringClass(alias, field);
        if (holder == null) {
            assert alias.optional();
            return null;
        }

        String name = alias.name();
        if (name.isEmpty()) {
            name = field.getName();
        }

        Class type;
        if (alias.descriptor().isEmpty()) {
            type = getMirrorOrFail((ResolvedJavaType) field.getType(), currentThread().getContextClassLoader());
        } else {
            type = resolveType(alias.descriptor(), false);
        }

        for (Field f : holder.getDeclaredFields()) {
            if (f.getName().equals(name) && f.getType().equals(type) && isStatic(f.getModifiers()) == isStatic(field.getModifiers())) {
                return f;
            }
        }
        if (alias.optional()) {
            return null;
        }
        throw new GraalInternalError("Could not resolve field alias %s", format("%T %H.%n", field));
    }

    private Method getAliasedMethod(ResolvedJavaMethod method) {
        Alias alias = method.getAnnotation(Alias.class);
        if (alias == null) {
            return null;
        }
        Class holder = declaringClass(alias, method);
        if (holder == null) {
            assert alias.optional();
            return null;
        }

        String name = alias.name();
        if (name.isEmpty()) {
            name = method.getName();
        }

        Class[] parameters;
        if (alias.descriptor().isEmpty()) {
            parameters = NodeIntrinsificationPhase.signatureToTypes(method.getSignature(), null);
        } else {
            Signature signature = runtime.parseMethodDescriptor(alias.descriptor());
            parameters = NodeIntrinsificationPhase.signatureToTypes(signature, null);
        }

        for (Method m : holder.getDeclaredMethods()) {
            if (m.getName().equals(name) && Arrays.equals(m.getParameterTypes(), parameters) && isStatic(m.getModifiers()) == isStatic(method.getModifiers())) {
                return m;
            }
        }
        if (alias.optional()) {
            return null;
        }
        throw new GraalInternalError("Could not resolve method alias %s", format("%R %H.%n(%P)", method));
    }

    private static Class getInnerClass(Class outerClass, String innerClassSimpleName) {
        for (Class innerClass : outerClass.getDeclaredClasses()) {
            if (innerClass.getSimpleName().equals(innerClassSimpleName)) {
                return innerClass;
            }
        }
        return null;
    }

    private static Class declaringClass(Alias alias, Object member) {
        Class holder;
        if (alias.declaringClass() == Alias.class) {
            assert !alias.declaringClassName().isEmpty() : "declaring class missing for alias " + member;
            holder = resolveType(alias.declaringClassName(), alias.optional());
        } else {
            assert alias.declaringClassName().isEmpty() : "declaring class specified more than once for alias " + member;
            holder = alias.declaringClass();
        }
        if (holder != null && !alias.innerClass().isEmpty()) {
            holder = getInnerClass(holder, alias.innerClass());
        }
        return holder;
    }
}
