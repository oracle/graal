/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.dsl.processor;

import static com.oracle.truffle.dsl.processor.Utils.*;

import java.util.*;

import javax.annotation.processing.*;
import javax.lang.model.element.*;
import javax.lang.model.type.*;
import javax.lang.model.util.*;

import com.oracle.truffle.dsl.processor.ast.*;
import com.oracle.truffle.dsl.processor.ast.CodeTypeMirror.ArrayCodeTypeMirror;
import com.oracle.truffle.dsl.processor.template.*;

/**
 * THIS IS NOT PUBLIC API.
 */
public class ProcessorContext {

    private final ProcessingEnvironment environment;

    private final Map<String, Template> models = new HashMap<>();
    private final Map<String, Map<String, TypeMirror>> generatedClasses = new HashMap<>();

    private final ProcessCallback callback;
    private final Log log;
    private final TruffleTypes truffleTypes;

    public ProcessorContext(ProcessingEnvironment env, ProcessCallback callback) {
        this.environment = env;
        this.callback = callback;
        this.log = new Log(environment);
        this.truffleTypes = new TruffleTypes(this);
    }

    public TruffleTypes getTruffleTypes() {
        return truffleTypes;
    }

    public Log getLog() {
        return log;
    }

    public ProcessingEnvironment getEnvironment() {
        return environment;
    }

    public boolean containsTemplate(TypeElement element) {
        return models.containsKey(Utils.getQualifiedName(element));
    }

    public void registerTemplate(TypeElement element, Template model) {
        models.put(Utils.getQualifiedName(element), model);
    }

    public void registerType(TypeElement templateType, TypeMirror generatedTypeMirror) {
        String templateQualifiedName = getQualifiedName(templateType);
        Map<String, TypeMirror> simpleNameToType = generatedClasses.get(templateQualifiedName);
        if (simpleNameToType == null) {
            simpleNameToType = new HashMap<>();
            generatedClasses.put(templateQualifiedName, simpleNameToType);
        }
        String generatedSimpleName = getSimpleName(generatedTypeMirror);
        simpleNameToType.put(generatedSimpleName, generatedTypeMirror);
    }

    public Template getTemplate(TypeMirror templateTypeMirror, boolean invokeCallback) {
        String qualifiedName = Utils.getQualifiedName(templateTypeMirror);
        Template model = models.get(qualifiedName);
        if (model == null && invokeCallback) {
            callback.callback(Utils.fromTypeMirror(templateTypeMirror));
            model = models.get(qualifiedName);
        }
        return model;
    }

    public TypeMirror resolveNotYetCompiledType(TypeMirror mirror, Template templateHint) {
        TypeMirror resolvedType = null;
        if (mirror.getKind() == TypeKind.ARRAY) {
            TypeMirror originalComponentType = ((ArrayType) mirror).getComponentType();
            TypeMirror resolvedComponent = resolveNotYetCompiledType(originalComponentType, templateHint);
            if (resolvedComponent != originalComponentType) {
                return new ArrayCodeTypeMirror(resolvedComponent);
            }
        }

        if (mirror.getKind() == TypeKind.ERROR) {
            Element element = ((ErrorType) mirror).asElement();
            ElementKind kind = element.getKind();
            if (kind == ElementKind.CLASS || kind == ElementKind.PARAMETER || kind == ElementKind.ENUM) {
                String simpleName = element.getSimpleName().toString();
                resolvedType = findGeneratedClassBySimpleName(simpleName, templateHint);
            }
        } else {
            resolvedType = mirror;
        }

        return resolvedType;
    }

    public TypeMirror findGeneratedClassBySimpleName(String simpleName, Template templateHint) {
        if (templateHint == null) {
            // search all
            for (String qualifiedTemplateName : generatedClasses.keySet()) {
                Map<String, TypeMirror> mirrors = generatedClasses.get(qualifiedTemplateName);
                if (mirrors.get(simpleName) != null) {
                    return mirrors.get(simpleName);
                }
            }
            return null;
        } else {
            String templateQualifiedName = getQualifiedName(templateHint.getTemplateType());
            Map<String, TypeMirror> simpleNameToType = generatedClasses.get(templateQualifiedName);
            if (simpleNameToType == null) {
                return null;
            }
            return simpleNameToType.get(simpleName);
        }
    }

    public TypeMirror getType(String className) {
        TypeElement element = environment.getElementUtils().getTypeElement(className);
        if (element != null) {
            return element.asType();
        }
        return null;
    }

    public TypeMirror getType(Class<?> element) {
        TypeMirror mirror;
        if (element.isPrimitive()) {
            if (element == boolean.class) {
                mirror = environment.getTypeUtils().getPrimitiveType(TypeKind.BOOLEAN);
            } else if (element == byte.class) {
                mirror = environment.getTypeUtils().getPrimitiveType(TypeKind.BYTE);
            } else if (element == short.class) {
                mirror = environment.getTypeUtils().getPrimitiveType(TypeKind.SHORT);
            } else if (element == char.class) {
                mirror = environment.getTypeUtils().getPrimitiveType(TypeKind.CHAR);
            } else if (element == int.class) {
                mirror = environment.getTypeUtils().getPrimitiveType(TypeKind.INT);
            } else if (element == long.class) {
                mirror = environment.getTypeUtils().getPrimitiveType(TypeKind.LONG);
            } else if (element == float.class) {
                mirror = environment.getTypeUtils().getPrimitiveType(TypeKind.FLOAT);
            } else if (element == double.class) {
                mirror = environment.getTypeUtils().getPrimitiveType(TypeKind.DOUBLE);
            } else if (element == void.class) {
                mirror = environment.getTypeUtils().getNoType(TypeKind.VOID);
            } else {
                assert false;
                return null;
            }
        } else {
            TypeElement typeElement = environment.getElementUtils().getTypeElement(element.getCanonicalName());
            mirror = typeElement.asType();
        }
        return mirror;
    }

    public interface ProcessCallback {

        void callback(TypeElement template);

    }

    public TypeMirror reloadTypeElement(TypeElement type) {
        return getType(type.getQualifiedName().toString());
    }

    public TypeMirror reloadType(TypeMirror type) {
        if (type instanceof CodeTypeMirror) {
            return type;
        } else if (type.getKind().isPrimitive()) {
            return type;
        }
        Types types = getEnvironment().getTypeUtils();

        switch (type.getKind()) {
            case ARRAY:
                return types.getArrayType(reloadType(((ArrayType) type).getComponentType()));
            case WILDCARD:
                return types.getWildcardType(((WildcardType) type).getExtendsBound(), ((WildcardType) type).getSuperBound());
            case DECLARED:
                return reloadTypeElement((TypeElement) (((DeclaredType) type).asElement()));
        }
        return type;
    }
}
