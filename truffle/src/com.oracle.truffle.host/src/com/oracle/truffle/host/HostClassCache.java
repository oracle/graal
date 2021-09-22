/*
 * Copyright (c) 2016, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.host;

import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.APIAccess;
import org.graalvm.polyglot.impl.AbstractPolyglotImpl.AbstractHostAccess;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleOptions;

final class HostClassCache {

    static final HostTargetMapping[] EMPTY_MAPPINGS = new HostTargetMapping[0];

    private final APIAccess apiAccess;
    final HostAccess hostAccess;
    final AbstractHostAccess polyglotHostAccess;
    private final boolean arrayAccess;
    private final boolean listAccess;
    private final boolean bufferAccess;
    private final boolean iterableAccess;
    private final boolean iteratorAccess;
    private final boolean mapAccess;
    private final Map<Class<?>, Object> targetMappings;
    private final Object unnamedModule;
    private final WeakReference<HostClassCache> weakHostClassRef = new WeakReference<>(this);

    private final ClassValue<HostClassDesc> descs = new ClassValue<HostClassDesc>() {
        @Override
        protected HostClassDesc computeValue(Class<?> type) {
            /*
             * The weak reference is a workaround for JDK-8169425. Cyclic references are not
             * supported for values in ClassValue. In practice the passed in weak reference should
             * never become null during a usage of HostClassDesc.
             */
            return new HostClassDesc(weakHostClassRef, type);
        }
    };

    private HostClassCache(AbstractHostAccess polyglotAccess, APIAccess apiAccess, HostAccess conf, ClassLoader classLoader) {
        this.polyglotHostAccess = polyglotAccess;
        this.hostAccess = conf;
        this.apiAccess = apiAccess;
        this.arrayAccess = apiAccess.isArrayAccessible(hostAccess);
        this.listAccess = apiAccess.isListAccessible(hostAccess);
        this.bufferAccess = apiAccess.isBufferAccessible(hostAccess);
        this.iterableAccess = apiAccess.isIterableAccessible(hostAccess);
        this.iteratorAccess = apiAccess.isIteratorAccessible(hostAccess);
        this.mapAccess = apiAccess.isMapAccessible(hostAccess);
        this.targetMappings = groupMappings(apiAccess, conf);
        this.unnamedModule = HostAccessor.JDKSERVICES.getUnnamedModule(classLoader);
    }

    Object getUnnamedModule() {
        return unnamedModule;
    }

    boolean hasTargetMappings() {
        return targetMappings != null;
    }

    @TruffleBoundary
    HostTargetMapping[] getMappings(Class<?> targetType) {
        if (targetMappings != null) {
            Class<?> lookupType;
            if (targetType.isPrimitive()) {
                if (targetType == byte.class) {
                    lookupType = Byte.class;
                } else if (targetType == short.class) {
                    lookupType = Short.class;
                } else if (targetType == int.class) {
                    lookupType = Integer.class;
                } else if (targetType == long.class) {
                    lookupType = Long.class;
                } else if (targetType == float.class) {
                    lookupType = Float.class;
                } else if (targetType == double.class) {
                    lookupType = Double.class;
                } else if (targetType == boolean.class) {
                    lookupType = Boolean.class;
                } else if (targetType == char.class) {
                    lookupType = Character.class;
                } else if (targetType == void.class) {
                    lookupType = Void.class;
                } else {
                    lookupType = null;
                }
            } else {
                lookupType = targetType;
            }
            HostTargetMapping[] mappings = (HostTargetMapping[]) targetMappings.get(lookupType);
            if (mappings == null) {
                return EMPTY_MAPPINGS;
            } else {
                return mappings;
            }
        }
        return EMPTY_MAPPINGS;
    }

    @SuppressWarnings("unchecked")
    private static Map<Class<?>, Object> groupMappings(AbstractPolyglotImpl.APIAccess apiAccess, HostAccess conf) {
        List<Object> mappings = apiAccess.getTargetMappings(conf);
        if (mappings == null) {
            return null;
        }
        Map<Class<?>, Object> localMappings = new HashMap<>();
        for (Object mapping : mappings) {
            HostTargetMapping map = (HostTargetMapping) mapping;
            List<HostTargetMapping> list = (List<HostTargetMapping>) localMappings.get(map.targetType);
            if (list == null) {
                list = new ArrayList<>();
                localMappings.put(map.targetType, list);
            }
            list.add(map);
        }

        for (Entry<Class<?>, Object> object : localMappings.entrySet()) {
            List<HostTargetMapping> classMappings = ((List<HostTargetMapping>) object.getValue());
            Collections.sort(classMappings);
            object.setValue(classMappings.toArray(EMPTY_MAPPINGS));
        }
        return localMappings;
    }

    public static HostClassCache findOrInitialize(AbstractHostAccess hostLanguage, APIAccess apiAccess, HostAccess conf, ClassLoader classLoader) {
        HostClassCache cache = (HostClassCache) apiAccess.getHostAccessImpl(conf);
        if (cache == null) {
            cache = initializeHostCache(hostLanguage, apiAccess, conf, classLoader);
        }
        return cache;
    }

    private static HostClassCache initializeHostCache(AbstractHostAccess polyglotAccess, APIAccess apiAccess, HostAccess conf, ClassLoader classLoader) {
        HostClassCache cache;
        synchronized (conf) {
            cache = (HostClassCache) apiAccess.getHostAccessImpl(conf);
            if (cache == null) {
                cache = new HostClassCache(polyglotAccess, apiAccess, conf, classLoader);
                apiAccess.setHostAccessImpl(conf, cache);
            }
        }
        return cache;
    }

    @TruffleBoundary
    public static HostClassCache forInstance(HostObject receiver) {
        return receiver.context.getHostClassCache();
    }

    @TruffleBoundary
    HostClassDesc forClass(Class<?> clazz) {
        return descs.get(clazz);
    }

    @TruffleBoundary
    boolean allowsAccess(Method m) {
        return apiAccess.allowsAccess(hostAccess, m) || isGeneratedClassMember(m);
    }

    @TruffleBoundary
    boolean allowsAccess(Constructor<?> m) {
        return apiAccess.allowsAccess(hostAccess, m) || isGeneratedClassMember(m);
    }

    @TruffleBoundary
    boolean allowsAccess(Field f) {
        return apiAccess.allowsAccess(hostAccess, f) || isGeneratedClassMember(f);
    }

    /***
     * Generated class members are always accessible, i.e., members of implementable interfaces and
     * classes are implicitly exported through their implementations.
     */
    private static boolean isGeneratedClassMember(Member member) {
        if (TruffleOptions.AOT) {
            return false;
        }
        if (HostAdapterClassLoader.isGeneratedClass(member.getDeclaringClass())) {
            return true;
        }
        return false;
    }

    boolean isArrayAccess() {
        return arrayAccess;
    }

    boolean isListAccess() {
        return listAccess;
    }

    boolean isBufferAccess() {
        return bufferAccess;
    }

    boolean isIterableAccess() {
        return iterableAccess;
    }

    boolean isIteratorAccess() {
        return iteratorAccess;
    }

    boolean isMapAccess() {
        return mapAccess;
    }

    boolean allowsImplementation(Class<?> type) {
        return apiAccess.allowsImplementation(hostAccess, type);
    }

    boolean methodScoped(Executable e) {
        return apiAccess.isMethodScoped(hostAccess, e);
    }
}
