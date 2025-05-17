/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.substitutions.continuations;

import java.lang.invoke.CallSite;
import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.substitutions.EspressoSubstitutions;
import com.oracle.truffle.espresso.substitutions.JavaType;
import com.oracle.truffle.espresso.substitutions.Substitution;
import com.oracle.truffle.espresso.substitutions.SubstitutionNode;

/**
 * This class exists for serializable continuations. It forcibly overrides the lambda generation
 * mode to request that serializability is always included. This is insufficient to enable
 * serialization with ObjectOutputStream because the standard Java protocol requires code generation
 * by javac, but it <i>is</i> sufficient to make lambdas transparently serializable when using a
 * custom serializer.
 */
@EspressoSubstitutions
public final class Target_java_lang_invoke_LambdaMetafactory {
    @Substitution
    abstract static class Metafactory extends SubstitutionNode {
        abstract @JavaType(CallSite.class) StaticObject execute(
                        @JavaType(MethodHandles.Lookup.class) StaticObject caller,
                        @JavaType(String.class) StaticObject interfaceMethodName,
                        @JavaType(MethodType.class) StaticObject factoryType,
                        @JavaType(MethodType.class) StaticObject interfaceMethodType,
                        @JavaType(MethodHandle.class) StaticObject implementation,
                        @JavaType(MethodType.class) StaticObject dynamicMethodType);

        @Specialization
        @JavaType(CallSite.class)
        StaticObject doCached(
                        @JavaType(MethodHandles.Lookup.class) StaticObject caller,
                        @JavaType(String.class) StaticObject interfaceMethodName,
                        @JavaType(MethodType.class) StaticObject factoryType,
                        @JavaType(MethodType.class) StaticObject interfaceMethodType,
                        @JavaType(MethodHandle.class) StaticObject implementation,
                        @JavaType(MethodType.class) StaticObject dynamicMethodType,
                        @Bind("getMeta()") Meta meta,
                        @Cached("create(meta.java_lang_invoke_LambdaMetafactory_altMetafactory.getCallTargetNoSubstitution())") DirectCallNode altMetafactory,
                        @Cached("create(meta.java_lang_invoke_LambdaMetafactory_metafactory.getCallTargetNoSubstitution())") DirectCallNode original,
                        @Bind("getLanguage()") EspressoLanguage lang) {
            if (lang.isContinuumEnabled()) {
                // altMetafactory has a curious calling convention, apparently designed for
                // extensibility.
                StaticObject extraArgsRef = lang.getAllocator().createNewReferenceArray(meta.java_lang_Object, 4);
                StaticObject[] extraArgs = extraArgsRef.unwrap(lang);
                extraArgs[0] = interfaceMethodType;
                extraArgs[1] = implementation;
                extraArgs[2] = dynamicMethodType;
                extraArgs[3] = (StaticObject) meta.java_lang_Integer_valueOf.getCallTarget().call(LambdaMetafactory.FLAG_SERIALIZABLE);
                return (StaticObject) altMetafactory.call(caller, interfaceMethodName, factoryType, extraArgsRef);
            } else {
                return (StaticObject) original.call(caller, interfaceMethodName, factoryType, interfaceMethodType, implementation, dynamicMethodType);
            }
        }
    }

    @Substitution
    abstract static class AltMetafactory extends SubstitutionNode {
        abstract @JavaType(CallSite.class) StaticObject execute(
                        @JavaType(MethodHandles.Lookup.class) StaticObject caller,
                        @JavaType(String.class) StaticObject interfaceMethodName,
                        @JavaType(MethodType.class) StaticObject factoryType,
                        @JavaType(Object[].class) StaticObject args);

        @Specialization
        @JavaType(CallSite.class)
        StaticObject doCached(
                        @JavaType(MethodHandles.Lookup.class) StaticObject caller,
                        @JavaType(String.class) StaticObject interfaceMethodName,
                        @JavaType(MethodType.class) StaticObject factoryType,
                        @JavaType(Object[].class) StaticObject args,
                        @Bind("getMeta()") Meta meta,
                        @Cached("create(meta.java_lang_invoke_LambdaMetafactory_altMetafactory.getCallTargetNoSubstitution())") DirectCallNode original,
                        @Bind("getContext()") EspressoContext context) {
            if (context.getLanguage().isContinuumEnabled()) {
                StaticObject[] extraArgs = args.unwrap(context.getLanguage());
                extraArgs[3] = meta.boxInteger(meta.unboxInteger(extraArgs[3]) | LambdaMetafactory.FLAG_SERIALIZABLE);
                return (StaticObject) original.call(caller, interfaceMethodName, factoryType, StaticObject.wrap(extraArgs, meta));
            } else {
                return (StaticObject) original.call(caller, interfaceMethodName, factoryType, args);
            }
        }
    }
}
