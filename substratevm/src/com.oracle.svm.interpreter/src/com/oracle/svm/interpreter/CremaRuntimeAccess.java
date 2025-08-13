/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;

import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.registry.ClassRegistries;
import com.oracle.svm.core.hub.registry.SymbolsSupport;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.espresso.classfile.JavaVersion;
import com.oracle.svm.espresso.classfile.descriptors.NameSymbols;
import com.oracle.svm.espresso.classfile.descriptors.SignatureSymbols;
import com.oracle.svm.espresso.classfile.descriptors.Symbol;
import com.oracle.svm.espresso.classfile.descriptors.Type;
import com.oracle.svm.espresso.classfile.descriptors.TypeSymbols;
import com.oracle.svm.espresso.shared.meta.ErrorType;
import com.oracle.svm.espresso.shared.meta.KnownTypes;
import com.oracle.svm.espresso.shared.meta.RuntimeAccess;
import com.oracle.svm.espresso.shared.meta.SymbolPool;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedJavaField;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedJavaMethod;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedJavaType;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedObjectType;
import com.oracle.svm.interpreter.metadata.MetadataUtil;

public final class CremaRuntimeAccess implements RuntimeAccess<InterpreterResolvedJavaType, InterpreterResolvedJavaMethod, InterpreterResolvedJavaField> {

    private static final CremaRuntimeAccess INSTANCE = new CremaRuntimeAccess();

    private final SymbolPool globalSymbolPool = new SymbolPool() {
        @Override
        public NameSymbols getNames() {
            return SymbolsSupport.getNames();
        }

        @Override
        public TypeSymbols getTypes() {
            return SymbolsSupport.getTypes();
        }

        @Override
        public SignatureSymbols getSignatures() {
            return SymbolsSupport.getSignatures();
        }
    };

    private final CremaKnownTypes knownTypes = new CremaKnownTypes();

    public static CremaRuntimeAccess getInstance() {
        return INSTANCE;
    }

    @Override
    public JavaVersion getJavaVersion() {
        return JavaVersion.HOST_VERSION;
    }

    @Override
    public RuntimeException throwError(ErrorType errorType, String messageFormat, Object... args) {
        String message = MetadataUtil.fmt(messageFormat, args);
        switch (errorType) {
            case IllegalAccessError -> throw new IllegalAccessError(message);
            case NoSuchFieldError -> throw new NoSuchFieldError(message);
            case NoSuchMethodError -> throw new NoSuchMethodError(message);
            case IncompatibleClassChangeError -> throw new IncompatibleClassChangeError(message);
            case LinkageError -> throw new LinkageError(message);
        }
        throw fatal(message);
    }

    private static String toClassForName(Symbol<Type> type) {
        String typeString = type.toString();
        if (TypeSymbols.isArray(type)) {
            return typeString.replace('/', '.');
        }
        // Primitives cannot be resolved via Class.forName, but provide name for completeness.
        if (TypeSymbols.isPrimitive(type)) {
            // I -> int
            // Z -> boolean
            // ...
            return TypeSymbols.getJavaKind(type).toJavaClass().getName();
        }
        assert typeString.startsWith("L") && typeString.endsWith(";");
        return typeString.substring(1, typeString.length() - 1); // drop L and ;
    }

    @SuppressWarnings("unchecked")
    public static <T extends Throwable> RuntimeException uncheckedThrow(Throwable t) throws T {
        throw (T) t;
    }

    @Override
    public InterpreterResolvedObjectType lookupOrLoadType(Symbol<Type> type, InterpreterResolvedJavaType accessingClass) {
        String className = toClassForName(type);
        try {
            Class<?> result = ClassRegistries.forName(className, accessingClass.getJavaClass().getClassLoader());
            assert !result.isPrimitive();
            return (InterpreterResolvedObjectType) DynamicHub.fromClass(result).getInterpreterType();
        } catch (ClassNotFoundException e) {
            throw uncheckedThrow(e);
        }
    }

    @Override
    public KnownTypes<InterpreterResolvedJavaType, InterpreterResolvedJavaMethod, InterpreterResolvedJavaField> getKnownTypes() {
        return knownTypes;
    }

    @Override
    public SymbolPool getSymbolPool() {
        return globalSymbolPool;
    }

    @Override
    public RuntimeException fatal(String messageFormat, Object... args) {
        return VMError.shouldNotReachHere(MetadataUtil.fmt(messageFormat, args));
    }

    @Override
    public RuntimeException fatal(Throwable t, String messageFormat, Object... args) {
        return VMError.shouldNotReachHere(MetadataUtil.fmt(messageFormat, args), t);
    }

    @Override
    public ErrorType getErrorType(Throwable error) {
        // Unwrap exceptions thrown by interpreted code.
        Throwable throwable = error;
        if (error instanceof SemanticJavaException semanticJavaException) {
            throwable = semanticJavaException.getCause();
        }

        Class<?> exceptionClass = throwable.getClass();
        if (exceptionClass == IllegalAccessError.class) {
            return ErrorType.IllegalAccessError;
        }
        if (exceptionClass == NoSuchFieldError.class) {
            return ErrorType.NoSuchFieldError;
        }
        if (exceptionClass == NoSuchMethodError.class) {
            return ErrorType.NoSuchMethodError;
        }
        if (exceptionClass == IncompatibleClassChangeError.class) {
            return ErrorType.IncompatibleClassChangeError;
        }
        if (exceptionClass == LinkageError.class) {
            return ErrorType.LinkageError;
        }
        return null;
    }

    private static final class CremaKnownTypes implements KnownTypes<InterpreterResolvedJavaType, InterpreterResolvedJavaMethod, InterpreterResolvedJavaField> {

        static InterpreterResolvedJavaType fromJavaClass(Class<?> clazz) {
            return (InterpreterResolvedJavaType) DynamicHub.fromClass(clazz).getInterpreterType();
        }

        @Override
        public InterpreterResolvedJavaType java_lang_Object() {
            return fromJavaClass(Object.class);
        }

        @Override
        public InterpreterResolvedJavaType java_lang_Throwable() {
            return fromJavaClass(Throwable.class);
        }

        @Override
        public InterpreterResolvedJavaType java_lang_Class() {
            return fromJavaClass(Class.class);
        }

        @Override
        public InterpreterResolvedJavaType java_lang_String() {
            return fromJavaClass(String.class);
        }

        @Override
        public InterpreterResolvedJavaType java_lang_invoke_MethodType() {
            return fromJavaClass(MethodType.class);
        }

        @Override
        public InterpreterResolvedJavaType java_lang_invoke_MethodHandle() {
            return fromJavaClass(MethodHandle.class);
        }
    }
}
