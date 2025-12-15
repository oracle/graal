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
package com.oracle.svm.interpreter.metadata;

import java.lang.invoke.VarHandle;

import com.oracle.svm.core.hub.crema.CremaResolvedJavaMethod;
import com.oracle.svm.core.hub.registry.SVMSymbols;
import com.oracle.svm.core.interpreter.InterpreterSupport;
import com.oracle.svm.core.reflect.CremaConstructorAccessor;
import com.oracle.svm.core.reflect.CremaMethodAccessor;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.espresso.classfile.ExceptionHandler;
import com.oracle.svm.espresso.classfile.ParserMethod;
import com.oracle.svm.espresso.classfile.attributes.CodeAttribute;
import com.oracle.svm.espresso.classfile.descriptors.Symbol;
import com.oracle.svm.espresso.classfile.descriptors.Type;

import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaType;

public final class CremaResolvedJavaMethodImpl extends InterpreterResolvedJavaMethod implements CremaResolvedJavaMethod {
    private final ExceptionHandler[] rawExceptionHandlers;

    private CremaResolvedJavaMethodImpl(InterpreterResolvedObjectType declaringClass, ParserMethod parserMethod, int vtableIndex) {
        super(declaringClass, parserMethod, vtableIndex);
        CodeAttribute codeAttribute = (CodeAttribute) parserMethod.getAttribute(CodeAttribute.NAME);
        if (codeAttribute != null) {
            this.rawExceptionHandlers = codeAttribute.getExceptionHandlers();
        } else {
            this.rawExceptionHandlers = null;
        }
    }

    public static InterpreterResolvedJavaMethod create(InterpreterResolvedObjectType declaringClass, ParserMethod m, int vtableIndex) {
        InterpreterResolvedJavaMethod interpreterMethod = new CremaResolvedJavaMethodImpl(declaringClass, m, vtableIndex);
        interpreterMethod.setPreparedSignature(InterpreterSupport.singleton().prepareSignature(interpreterMethod));
        return interpreterMethod;
    }

    @Override
    public jdk.vm.ci.meta.ExceptionHandler[] getExceptionHandlers() {
        /*
         * GR-70247 The interpreter should primarily use classfile.ExceptionHandler. This would
         * avoid having to deal with the JavaType which is not needed during interpretation.
         */
        jdk.vm.ci.meta.ExceptionHandler[] result = exceptionHandlers;
        if (result == null) {
            boolean canCache = true;
            if (rawExceptionHandlers == null || rawExceptionHandlers.length == 0) {
                result = EMPTY_EXCEPTION_HANDLERS;
            } else {
                result = new jdk.vm.ci.meta.ExceptionHandler[rawExceptionHandlers.length];
                InterpreterConstantPool constantPool = getConstantPool();
                for (int i = 0; i < rawExceptionHandlers.length; i++) {
                    ExceptionHandler exceptionHandler = rawExceptionHandlers[i];
                    Symbol<Type> catchTypeSymbol = exceptionHandler.getCatchType();
                    int catchTypeCPI = exceptionHandler.catchTypeCPI();
                    JavaType catchType;
                    if (SVMSymbols.SVMTypes.java_lang_Throwable.equals(catchTypeSymbol)) {
                        catchTypeCPI = 0;
                        catchType = null;
                    } else if (catchTypeCPI != 0) {
                        catchType = constantPool.findClassAt(catchTypeCPI);
                        canCache = canCache && (catchType instanceof ResolvedJavaType);
                    } else {
                        assert catchTypeSymbol == null;
                        catchType = null;
                    }
                    result[i] = new jdk.vm.ci.meta.ExceptionHandler(exceptionHandler.getStartBCI(),
                                    exceptionHandler.getEndBCI(),
                                    exceptionHandler.getHandlerBCI(),
                                    catchTypeCPI,
                                    catchType);
                }
                VarHandle.fullFence();
            }
            if (canCache) {
                this.exceptionHandlers = result;
            }
        }
        return result;
    }

    @Override
    public JavaType[] getDeclaredExceptions() {
        // (GR-69097)
        throw VMError.unimplemented("getCheckedExceptions");
    }

    @Override
    public byte[] getRawAnnotations() {
        // (GR-69096)
        throw VMError.unimplemented("getRawAnnotations");
    }

    @Override
    public byte[] getRawParameterAnnotations() {
        // (GR-69096)
        throw VMError.unimplemented("getRawParameterAnnotations");
    }

    @Override
    public byte[] getRawAnnotationDefault() {
        // (GR-69096)
        throw VMError.unimplemented("getRawAnnotationDefault");
    }

    @Override
    public byte[] getRawParameters() {
        // (GR-69096)
        throw VMError.unimplemented("getRawParameters");
    }

    @Override
    public byte[] getRawTypeAnnotations() {
        // (GR-69096)
        throw VMError.unimplemented("getRawTypeAnnotations");
    }

    @Override
    public Object getAccessor(Class<?> declaringClass, Class<?>[] parameterTypes) {
        if (isConstructor()) {
            return new CremaConstructorAccessor(this, declaringClass, parameterTypes);
        } else {
            return new CremaMethodAccessor(this, declaringClass, parameterTypes);
        }
    }

    @Override
    public String getGenericSignature() {
        return getSymbolicSignature().toString();
    }
}
