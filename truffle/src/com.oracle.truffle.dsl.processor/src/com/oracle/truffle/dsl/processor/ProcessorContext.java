/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.dsl.processor;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.Types;

import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.dsl.processor.java.ElementUtils;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeMirror;
import com.oracle.truffle.dsl.processor.model.Template;

/**
 * THIS IS NOT PUBLIC API.
 */
public class ProcessorContext {

    private final ProcessingEnvironment environment;

    private final Map<String, Template> models = new HashMap<>();

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
        return models.containsKey(ElementUtils.getQualifiedName(element));
    }

    public void registerTemplate(TypeElement element, Template model) {
        models.put(ElementUtils.getQualifiedName(element), model);
    }

    public Template getTemplate(TypeMirror templateTypeMirror, boolean invokeCallback) {
        String qualifiedName = ElementUtils.getQualifiedName(templateTypeMirror);
        Template model = models.get(qualifiedName);
        if (model == null && invokeCallback) {
            callback.callback(ElementUtils.fromTypeMirror(templateTypeMirror));
            model = models.get(qualifiedName);
        }
        return model;
    }

    public DeclaredType getDeclaredType(Class<?> element) {
        return (DeclaredType) ElementUtils.getType(environment, element);
    }

    public boolean isType(TypeMirror type, Class<?> clazz) {
        return ElementUtils.typeEquals(type, getType(clazz));
    }

    public TypeMirror getType(Class<?> element) {
        return ElementUtils.getType(environment, element);
    }

    public interface ProcessCallback {

        void callback(TypeElement template);

    }

    public TypeMirror reloadTypeElement(TypeElement type) {
        return getType(type.getQualifiedName().toString());
    }

    private TypeMirror getType(String className) {
        TypeElement element = ElementUtils.getTypeElement(environment, className);
        if (element != null) {
            return element.asType();
        }
        return null;
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

    private static final ThreadLocal<ProcessorContext> instance = new ThreadLocal<>();

    public static void setThreadLocalInstance(ProcessorContext context) {
        instance.set(context);
    }

    public static ProcessorContext getInstance() {
        return instance.get();
    }

    public List<TypeMirror> getFrameTypes() {
        return Arrays.asList(getType(VirtualFrame.class), getType(MaterializedFrame.class), getType(Frame.class));
    }
}
