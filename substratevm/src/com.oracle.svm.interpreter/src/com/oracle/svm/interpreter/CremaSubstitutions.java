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
package com.oracle.svm.interpreter;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.MethodRefHolder;
import com.oracle.svm.core.hub.registry.SymbolsSupport;
import com.oracle.svm.espresso.classfile.descriptors.Name;
import com.oracle.svm.espresso.classfile.descriptors.NameSymbols;
import com.oracle.svm.espresso.classfile.descriptors.Signature;
import com.oracle.svm.espresso.classfile.descriptors.SignatureSymbols;
import com.oracle.svm.espresso.classfile.descriptors.Symbol;
import com.oracle.svm.espresso.classfile.descriptors.Type;
import com.oracle.svm.espresso.classfile.descriptors.TypeSymbols;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedJavaMethod;
import com.oracle.svm.shared.util.ReflectionUtil;

/**
 * Temporary class for registering Crema interpreter-only substitution.
 * <p>
 * Currently only used to provide an implementation for un-preservable methods, such as:
 * <ul>
 * <li>{@link ImageSingletons#contains(Class)}
 * <li>{@link ImageInfo}{@code #inImage*()}
 * </ul>
 * These are only substituted if their declared class is included, otherwise, the actual bytecode
 * is executed normally.
 * <p>
 * This should become obsolete once support for partial types land (GR-71616). With that, crema
 * will be able to simply execute the original bytecode of the methods, which is the wanted behavior.
 */
@Platforms(Platform.HOSTED_ONLY.class)
public class CremaSubstitutions {
    private final Map<MethodKey, Supplier<MethodRefHolder>> substitutions = new HashMap<>();

    public CremaSubstitutions() {
        Method contains = ReflectionUtil.lookupMethod(ImageSingletons.class, "contains", Class.class);
        substitutions.put(of(contains), InterpreterKnownCompiledEntryPoints::getReturnsFalseStub);
        Method inImageRuntimeCode = ReflectionUtil.lookupMethod(ImageInfo.class, "inImageRuntimeCode");
        substitutions.put(of(inImageRuntimeCode), InterpreterKnownCompiledEntryPoints::getReturnsFalseStub);
        Method inImageCode = ReflectionUtil.lookupMethod(ImageInfo.class, "inImageCode");
        substitutions.put(of(inImageCode), InterpreterKnownCompiledEntryPoints::getReturnsFalseStub);
        Method inImageBuildtimeCode = ReflectionUtil.lookupMethod(ImageInfo.class, "inImageBuildtimeCode");
        substitutions.put(of(inImageBuildtimeCode), InterpreterKnownCompiledEntryPoints::getReturnsFalseStub);
    }

    public MethodRefHolder get(InterpreterResolvedJavaMethod m) {
        MethodKey key = new MethodKey(m.getDeclaringClass().getSymbolicType(), m.getSymbolicName(), m.getSymbolicSignature());
        Supplier<MethodRefHolder> supplier = substitutions.get(key);
        if (supplier == null) {
            return null;
        }
        return supplier.get();
    }

    private record MethodKey(
                    Symbol<Type> clazz,
                    Symbol<Name> methodName,
                    Symbol<Signature> signature) {
    }

    private static MethodKey of(Method m) {
        NameSymbols names = SymbolsSupport.getNames();
        TypeSymbols types = SymbolsSupport.getTypes();
        SignatureSymbols signatures = SymbolsSupport.getSignatures();

        Symbol<Type> t = types.fromClassGetName(m.getDeclaringClass().getName());
        Symbol<Name> n = names.getOrCreate(m.getName());

        String desc = "(" + Arrays.stream(m.getParameterTypes())
                        .map(Class::descriptorString)
                        .reduce(String::concat).orElse("") + ")" + m.getReturnType().descriptorString();
        Symbol<Signature> s = signatures.getOrCreateValidSignature(desc);
        return new MethodKey(t, n, s);
    }
}
