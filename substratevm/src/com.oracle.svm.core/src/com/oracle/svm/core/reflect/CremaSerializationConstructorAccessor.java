/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.reflect;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;

import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.crema.CremaSupport;

import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Constructor accessor for deserializing runtime-loaded Crema classes when the first
 * non-serializable superclass has a constructor that Crema can execute.
 * <p>
 * The normal serialization constructor accessor table is built before runtime-loaded classes exist,
 * so it cannot contain entries for these Crema classes. This accessor allocates the deserialization
 * target class directly, then invokes the selected superclass constructor through Crema. The
 * {@link Object#Object()} root case has no instance state to initialize, but it is still invoked to
 * match the JDK serialization constructor path.
 */
public final class CremaSerializationConstructorAccessor extends AbstractCremaConstructorAccessor {
    private final Class<?> serializationTargetClass;

    private static boolean isAbstract(Class<?> serializationTargetClass) {
        return Modifier.isAbstract(DynamicHub.fromClass(serializationTargetClass).getInterpreterType().getModifiers());
    }

    public CremaSerializationConstructorAccessor(Class<?> serializationTargetClass, Constructor<?> constructorToCall) {
        /*
         * Resolve the constructor once so each deserialization does not repeat the
         * reflection-to-JVMCI lookup.
         */
        this(serializationTargetClass, constructorToCall.getDeclaringClass(), constructorToCall.getParameterTypes(), CremaSupport.singleton().toJVMCI(constructorToCall));
    }

    private CremaSerializationConstructorAccessor(Class<?> serializationTargetClass, Class<?> constructorDeclaringClass, Class<?>[] constructorParameterTypes, ResolvedJavaMethod constructorToCall) {
        super(constructorToCall, constructorDeclaringClass, constructorParameterTypes, isAbstract(serializationTargetClass));
        this.serializationTargetClass = serializationTargetClass;
    }

    @Override
    protected Class<?> getInstantiatedClass() {
        return serializationTargetClass;
    }
}
