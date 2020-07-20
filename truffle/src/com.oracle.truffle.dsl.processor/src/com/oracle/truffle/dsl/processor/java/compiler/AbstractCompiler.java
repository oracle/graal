/*
 * Copyright (c) 2012, 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.dsl.processor.java.compiler;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.QualifiedNameable;
import javax.tools.Diagnostic;

public abstract class AbstractCompiler implements Compiler {

    protected static Object method(Object o, String methodName) throws ReflectiveOperationException {
        Method method = o.getClass().getMethod(methodName);
        method.setAccessible(true);
        return method.invoke(o);
    }

    protected static Object method(Object o, String methodName, Class<?>[] paramTypes, Object... values) throws ReflectiveOperationException {
        Method method = o.getClass().getMethod(methodName, paramTypes);
        method.setAccessible(true);
        return method.invoke(o, values);
    }

    protected static Object staticMethod(Class<?> clz, String methodName, Class<?>[] paramTypes, Object... values) throws ReflectiveOperationException {
        Method method = clz.getMethod(methodName, paramTypes);
        method.setAccessible(true);
        return method.invoke(null, values);
    }

    protected static Object field(Object o, String fieldName) throws ReflectiveOperationException {
        if (o == null) {
            return null;
        }
        Class<?> clazz = o.getClass();
        Field field = null;
        try {
            field = clazz.getField(fieldName);
        } catch (NoSuchFieldException e) {
            while (clazz != null) {
                try {
                    field = clazz.getDeclaredField(fieldName);
                    break;
                } catch (NoSuchFieldException e1) {
                    clazz = clazz.getSuperclass();
                }
            }
            if (field == null) {
                throw e;
            }
        }
        field.setAccessible(true);
        return field.get(o);
    }

    @Override
    public final void emitDeprecationWarning(ProcessingEnvironment environment, Element element) {
        if (!emitDeprecationWarningImpl(environment, element)) {
            CharSequence ownerQualifiedName = "";
            Element enclosingElement = element.getEnclosingElement();
            if (enclosingElement != null) {
                ElementKind kind = enclosingElement.getKind();
                if (kind.isClass() || kind.isInterface() || kind == ElementKind.PACKAGE) {
                    ownerQualifiedName = ((QualifiedNameable) enclosingElement).getQualifiedName();
                }
            }
            environment.getMessager().printMessage(
                            Diagnostic.Kind.WARNING,
                            String.format("%s in %s has been deprecated", element.getSimpleName(), ownerQualifiedName),
                            element);
        }
    }

    protected abstract boolean emitDeprecationWarningImpl(ProcessingEnvironment environment, Element element);
}
