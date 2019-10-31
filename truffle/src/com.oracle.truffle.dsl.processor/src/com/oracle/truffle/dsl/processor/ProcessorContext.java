/*
 * Copyright (c) 2012, 2019, Oracle and/or its affiliates. All rights reserved.
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
    private TruffleTypes types;

    public ProcessorContext(ProcessingEnvironment env, ProcessCallback callback) {
        this.environment = env;
        this.callback = callback;
        boolean emitWarnings = !Boolean.parseBoolean(System.getProperty("truffle.dsl.ignoreCompilerWarnings", "false"));
        this.log = new Log(environment, emitWarnings);
    }

    public TruffleTypes getTypes() {
        return types;
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

    public DeclaredType getDeclaredTypeOptional(String element) {
        TypeElement type = ElementUtils.getTypeElement(environment, element);
        if (type == null) {
            return null;
        }
        return (DeclaredType) type.asType();
    }

    public DeclaredType getDeclaredType(String element) {
        TypeElement type = ElementUtils.getTypeElement(environment, element);
        if (type == null) {
            throw new IllegalArgumentException("Processor requested type " + element + " but was not on the classpath.");
        }
        return (DeclaredType) type.asType();
    }

    public boolean isType(TypeMirror type, Class<?> clazz) {
        return ElementUtils.typeEquals(type, getType(clazz));
    }

    public TypeMirror getType(Class<?> element) {
        return ElementUtils.getType(environment, element);
    }

    public TypeElement getTypeElement(Class<?> element) {
        DeclaredType type = getDeclaredType(element);
        return (TypeElement) type.asElement();
    }

    public TypeElement getTypeElement(DeclaredType element) {
        return (TypeElement) element.asElement();
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
        Types typesUtils = getEnvironment().getTypeUtils();

        switch (type.getKind()) {
            case ARRAY:
                return typesUtils.getArrayType(reloadType(((ArrayType) type).getComponentType()));
            case WILDCARD:
                return typesUtils.getWildcardType(((WildcardType) type).getExtendsBound(), ((WildcardType) type).getSuperBound());
            case DECLARED:
                return reloadTypeElement((TypeElement) (((DeclaredType) type).asElement()));
        }
        return type;
    }

    private static final ThreadLocal<ProcessorContext> instance = new ThreadLocal<>();

    public static ProcessorContext enter(ProcessingEnvironment environment, ProcessCallback callback) {
        ProcessorContext context = new ProcessorContext(environment, callback);
        setThreadLocalInstance(context);
        return context;
    }

    public static ProcessorContext enter(ProcessingEnvironment environment) {
        return enter(environment, null);
    }

    public static void leave() {
        instance.set(null);
    }

    private static void setThreadLocalInstance(ProcessorContext context) {
        instance.set(context);
        if (context != null && context.types == null) {
            try {
                context.types = new TruffleTypes();
            } catch (IllegalArgumentException e) {
                TruffleProcessor.handleThrowable(null, e, null);
                throw e;
            }
        }
    }

    public static ProcessorContext getInstance() {
        return instance.get();
    }

    public List<TypeMirror> getFrameTypes() {
        return Arrays.asList(types.VirtualFrame, types.MaterializedFrame, types.Frame);
    }

    private final Map<Class<?>, Map<?, ?>> caches = new HashMap<>();

    @SuppressWarnings("unchecked")
    public <K, V> Map<K, V> getCacheMap(Class<?> key) {
        Map<?, ?> cacheMap = caches.get(key);
        if (cacheMap == null) {
            cacheMap = new HashMap<>();
            caches.put(key, cacheMap);
        }
        return (Map<K, V>) cacheMap;
    }
}
