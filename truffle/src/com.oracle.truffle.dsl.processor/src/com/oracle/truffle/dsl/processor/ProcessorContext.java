/*
 * Copyright (c) 2012, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.util.function.Function;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.Types;

import com.oracle.truffle.dsl.processor.java.ElementUtils;
import com.oracle.truffle.dsl.processor.java.model.CodeTypeMirror;

/**
 * THIS IS NOT PUBLIC API.
 */
public class ProcessorContext implements AutoCloseable {

    private final ProcessingEnvironment environment;
    private final Log log;
    private TruffleTypes types;
    private final Map<String, TypeElement> typeLookupCache = new HashMap<>();
    private final Map<Class<?>, Map<String, Object>> modelCache = new HashMap<>();
    private final Map<String, ExecutableElement> inlineSignatureCache = new HashMap<>();

    private Timer currentTimer;

    private final boolean timingsEnabled;

    public ProcessorContext(ProcessingEnvironment env) {
        this.environment = env;
        boolean emitWarnings = !Boolean.parseBoolean(System.getProperty("truffle.dsl.ignoreCompilerWarnings", "false")) && !TruffleProcessorOptions.suppressAllWarnings(env);

        String[] suppressWarnings = null;
        if (emitWarnings) {
            suppressWarnings = TruffleProcessorOptions.suppressDSLWarnings(env);
            if (suppressWarnings != null) {
                for (String warning : suppressWarnings) {
                    if (!TruffleSuppressedWarnings.ALL_KEYS.contains(warning)) {
                        throw new IllegalArgumentException(String.format("Invalid truffle.dsl.SuppressWarnings key '%s' specified.", warning));
                    }
                }
            }
        }

        this.log = new Log(environment, emitWarnings, suppressWarnings);
        this.timingsEnabled = TruffleProcessorOptions.printTimings(env);
    }

    Timer getCurrentTimer() {
        return currentTimer;
    }

    void setCurrentTimer(Timer currentTimer) {
        this.currentTimer = currentTimer;
    }

    public boolean timingsEnabled() {
        return timingsEnabled;
    }

    public static TruffleTypes types() {
        return getInstance().getTypes();
    }

    public TruffleTypes getTypes() {
        return types;
    }

    public Log getLog() {
        return log;
    }

    public Map<String, ExecutableElement> getInlineSignatureCache() {
        return inlineSignatureCache;
    }

    public ProcessingEnvironment getEnvironment() {
        return environment;
    }

    public DeclaredType getDeclaredType(Class<?> element) {
        return (DeclaredType) getType(element);
    }

    public DeclaredType getDeclaredTypeOptional(String element) {
        TypeElement type = getTypeElement(element);
        if (type == null) {
            return null;
        }
        return (DeclaredType) type.asType();
    }

    public TypeElement getTypeElement(final CharSequence typeName) {
        final String typeNameString = typeName.toString();
        TypeElement type = typeLookupCache.get(typeNameString);
        if (type == null) {
            type = environment.getElementUtils().getTypeElement(typeName);
            if (type != null) {
                typeLookupCache.put(typeNameString, type);
            }
        }
        return type;
    }

    public TypeMirror getType(Class<?> element) {
        if (element.isArray()) {
            return environment.getTypeUtils().getArrayType(getType(element.getComponentType()));
        }
        if (element.isPrimitive()) {
            if (element == void.class) {
                return environment.getTypeUtils().getNoType(TypeKind.VOID);
            }
            TypeKind typeKind;
            if (element == boolean.class) {
                typeKind = TypeKind.BOOLEAN;
            } else if (element == byte.class) {
                typeKind = TypeKind.BYTE;
            } else if (element == short.class) {
                typeKind = TypeKind.SHORT;
            } else if (element == char.class) {
                typeKind = TypeKind.CHAR;
            } else if (element == int.class) {
                typeKind = TypeKind.INT;
            } else if (element == long.class) {
                typeKind = TypeKind.LONG;
            } else if (element == float.class) {
                typeKind = TypeKind.FLOAT;
            } else if (element == double.class) {
                typeKind = TypeKind.DOUBLE;
            } else {
                assert false;
                return null;
            }
            return environment.getTypeUtils().getPrimitiveType(typeKind);
        } else {
            TypeElement typeElement = getTypeElement(element.getCanonicalName());
            if (typeElement == null) {
                return null;
            }
            return environment.getTypeUtils().erasure(typeElement.asType());
        }
    }

    public DeclaredType getDeclaredType(String element) {
        TypeElement type = getTypeElement(element);
        if (type == null) {
            throw new IllegalArgumentException("Processor requested type " + element + " but was not on the classpath.");
        }
        return (DeclaredType) type.asType();
    }

    public boolean isType(TypeMirror type, Class<?> clazz) {
        return ElementUtils.typeEquals(type, getType(clazz));
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
        return type.asType();
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

    public static ProcessorContext enter(ProcessingEnvironment environment) {
        ProcessorContext context = new ProcessorContext(environment);
        if (instance.get() != null) {
            throw new IllegalStateException("context already entered");
        }
        instance.set(context);
        try {
            if (context != null && context.types == null) {
                try {
                    context.types = new TruffleTypes();
                } catch (IllegalArgumentException e) {
                    TruffleProcessor.handleThrowable(null, e, null);
                    throw e;
                }
            }
        } catch (Throwable t) {
            // make sure we do not set the instance if type init fails
            // otherwise the next enter will fail
            instance.set(null);
            throw t;
        }
        return context;
    }

    @Override
    public void close() {
        ProcessorContext context = instance.get();
        if (context != this) {
            throw new IllegalStateException("context cannot be left if not entered");
        }
        context.notifyLeave();
        instance.set(null);
    }

    private void notifyLeave() {
        if (currentTimer != null && timingsEnabled()) {
            currentTimer.printSummary(System.out, "  ");
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

    @SuppressWarnings("unchecked")
    public <T> T parseIfAbsent(TypeElement element, Class<?> cacheKey,
                    Function<TypeElement, T> parser) {
        Map<String, Object> cache = modelCache.computeIfAbsent(cacheKey, (e) -> new HashMap<>());
        String typeId = ElementUtils.getUniqueIdentifier(element.asType());
        T result;
        if (cache.containsKey(typeId)) {
            result = (T) cache.get(typeId);
        } else {
            result = parser.apply(element);
            cache.put(typeId, result);
        }
        return result;
    }

}
