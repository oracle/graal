/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.hotspot.management.libgraal;

import static org.graalvm.compiler.hotspot.management.libgraal.annotation.JMXToLibGraal.Id.FinishRegistration;
import static org.graalvm.compiler.hotspot.management.libgraal.annotation.JMXToLibGraal.Id.GetAttributes;
import static org.graalvm.compiler.hotspot.management.libgraal.annotation.JMXToLibGraal.Id.GetMBeanInfo;
import static org.graalvm.compiler.hotspot.management.libgraal.annotation.JMXToLibGraal.Id.GetObjectName;
import static org.graalvm.compiler.hotspot.management.libgraal.annotation.JMXToLibGraal.Id.Invoke;
import static org.graalvm.compiler.hotspot.management.libgraal.annotation.JMXToLibGraal.Id.PollRegistrations;
import static org.graalvm.compiler.hotspot.management.libgraal.annotation.JMXToLibGraal.Id.SetAttributes;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.DynamicMBean;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanException;
import javax.management.MBeanInfo;
import javax.management.MBeanOperationInfo;
import javax.management.MBeanParameterInfo;
import javax.management.ReflectionException;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeType;
import org.graalvm.compiler.hotspot.management.JMXToLibGraalCalls;
import org.graalvm.compiler.hotspot.management.libgraal.annotation.JMXToLibGraal;
import org.graalvm.compiler.hotspot.management.libgraal.annotation.JMXToLibGraal.Id;
import org.graalvm.libgraal.jni.JNILibGraalScope;
import org.graalvm.libgraal.jni.JNI;
import org.graalvm.libgraal.jni.JNIUtil;
import org.graalvm.nativeimage.ObjectHandles;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CLongPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.util.OptionsEncoder;
import org.graalvm.word.WordFactory;

/**
 * Entry points in libgraal for calls from HotSpot.
 */
final class JMXToLibGraalEntryPoints {

    private JMXToLibGraalEntryPoints() {
    }

    /**
     * Returns the pending {@link DynamicMBean} registrations.
     */
    @JMXToLibGraal(PollRegistrations)
    @CEntryPoint(name = "Java_org_graalvm_compiler_hotspot_management_JMXToLibGraalCalls_pollRegistrations")
    @SuppressWarnings({"try", "unused"})
    static JNI.JLongArray pollRegistrations(JNI.JNIEnv env, JNI.JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateThreadId) {
        JNILibGraalScope<Id> scope = new JNILibGraalScope<>(PollRegistrations, env);
        try (JNILibGraalScope<Id> s = scope) {
            List<MBeanProxy<?>> registrations = MBeanProxy.drainRegistrations();
            JNI.JLongArray res = JNIUtil.NewLongArray(env, registrations.size());
            CLongPointer elems = JNIUtil.GetLongArrayElements(env, res, WordFactory.nullPointer());
            try {
                ObjectHandles globalHandles = ObjectHandles.getGlobal();
                for (int i = 0; i < registrations.size(); i++) {
                    long handle = globalHandles.create(registrations.get(i)).rawValue();
                    elems.write(i, handle);
                }
            } finally {
                JNIUtil.ReleaseLongArrayElements(env, res, elems, JNI.JArray.MODE_WRITE_RELEASE);
            }
            scope.setObjectResult(res);
        }
        return scope.getObjectResult();
    }

    /**
     * Notifies the {@link MBeanProxy} about finished registration.
     */
    @JMXToLibGraal(FinishRegistration)
    @CEntryPoint(name = "Java_org_graalvm_compiler_hotspot_management_JMXToLibGraalCalls_finishRegistration")
    @SuppressWarnings({"try", "unused"})
    static void finishRegistration(JNI.JNIEnv env, JNI.JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateThreadId, JNI.JLongArray handles) {
        try (JNILibGraalScope<Id> s = new JNILibGraalScope<>(FinishRegistration, env)) {
            long len = JNIUtil.GetArrayLength(env, handles);
            CLongPointer elems = JNIUtil.GetLongArrayElements(env, handles, WordFactory.nullPointer());
            try {
                ObjectHandles globalHandles = ObjectHandles.getGlobal();
                for (int i = 0; i < len; i++) {
                    MBeanProxy<?> registration = globalHandles.get(WordFactory.pointer(elems.read(i)));
                    registration.finishRegistration();
                }
            } finally {
                JNIUtil.ReleaseLongArrayElements(env, handles, elems, JNI.JArray.MODE_RELEASE);
            }
        }
    }

    /**
     * Returns the name to use to register the MBean.
     */
    @JMXToLibGraal(GetObjectName)
    @CEntryPoint(name = "Java_org_graalvm_compiler_hotspot_management_JMXToLibGraalCalls_getObjectName")
    @SuppressWarnings({"try", "unused"})
    static JNI.JString getObjectName(JNI.JNIEnv env, JNI.JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateThreadId, long handle) {
        JNILibGraalScope<Id> scope = new JNILibGraalScope<>(GetObjectName, env);
        try (JNILibGraalScope<Id> s = scope) {
            ObjectHandles globalHandles = ObjectHandles.getGlobal();
            MBeanProxy<?> registration = globalHandles.get(WordFactory.pointer(handle));
            String name = registration.getName();
            scope.setObjectResult(JNIUtil.createHSString(env, name));
        }
        return scope.getObjectResult();
    }

    /**
     * Returns the {@link MBeanInfo} encoded as a byte array using {@link OptionsEncoder}.
     */
    @JMXToLibGraal(GetMBeanInfo)
    @CEntryPoint(name = "Java_org_graalvm_compiler_hotspot_management_JMXToLibGraalCalls_getMBeanInfo")
    @SuppressWarnings({"try", "unused"})
    static JNI.JByteArray getMBeanInfo(JNI.JNIEnv env, JNI.JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateThreadId, long handle) {
        JNILibGraalScope<Id> scope = new JNILibGraalScope<>(GetMBeanInfo, env);
        try (JNILibGraalScope<Id> s = scope) {
            ObjectHandles globalHandles = ObjectHandles.getGlobal();
            MBeanProxy<?> registration = globalHandles.get(WordFactory.pointer(handle));
            MBeanInfo info = registration.getBean().getMBeanInfo();
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("bean.class", info.getClassName());
            map.put("bean.description", info.getDescription());
            for (MBeanAttributeInfo attr : info.getAttributes()) {
                putAttributeInfo(map, attr);
            }
            int opCounter = 0;
            for (MBeanOperationInfo op : info.getOperations()) {
                putOperationInfo(map, op, ++opCounter);
            }
            scope.setObjectResult(mapToRaw(env, map));
        }
        return scope.getObjectResult();
    }

    /**
     * Serialization of a {@link MBeanAttributeInfo} into map.
     */
    private static void putAttributeInfo(Map<String, Object> into, MBeanAttributeInfo attrInfo) {
        String name = attrInfo.getName();
        into.put("attr." + name + ".name", name);
        into.put("attr." + name + ".type", attrInfo.getType());
        into.put("attr." + name + ".description", attrInfo.getDescription());
        into.put("attr." + name + ".r", attrInfo.isReadable());
        into.put("attr." + name + ".w", attrInfo.isWritable());
        into.put("attr." + name + ".i", attrInfo.isIs());
    }

    /**
     * Serialization of a {@link MBeanOperationInfo} into map.
     */
    private static void putOperationInfo(Map<String, Object> into, MBeanOperationInfo opInfo, int opCounter) {
        String name = opInfo.getName();
        into.put("op." + opCounter + ".id", opCounter);
        into.put("op." + opCounter + ".name", name);
        into.put("op." + opCounter + ".type", opInfo.getReturnType());
        into.put("op." + opCounter + ".description", opInfo.getDescription());
        into.put("op." + opCounter + ".i", opInfo.getImpact());
        for (MBeanParameterInfo param : opInfo.getSignature()) {
            String paramName = param.getName();
            into.put("op." + opCounter + ".arg." + paramName + ".name", paramName);
            into.put("op." + opCounter + ".arg." + paramName + ".description", param.getDescription());
            into.put("op." + opCounter + ".arg." + paramName + ".type", param.getType());
        }
    }

    /**
     * Returns the required {@link DynamicMBean}'s attribute values encoded as a byte array using
     * {@link OptionsEncoder}.
     */
    @JMXToLibGraal(GetAttributes)
    @CEntryPoint(name = "Java_org_graalvm_compiler_hotspot_management_JMXToLibGraalCalls_getAttributes")
    @SuppressWarnings({"try", "unused"})
    static JNI.JByteArray getAttributes(JNI.JNIEnv env, JNI.JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateThreadId, long handle, JNI.JObjectArray requiredAttributes) {
        JNILibGraalScope<Id> scope = new JNILibGraalScope<>(GetAttributes, env);
        try (JNILibGraalScope<Id> s = scope) {
            int len = JNIUtil.GetArrayLength(env, requiredAttributes);
            String[] attrNames = new String[len];
            for (int i = 0; i < len; i++) {
                JNI.JString el = (JNI.JString) JNIUtil.GetObjectArrayElement(env, requiredAttributes, i);
                attrNames[i] = JNIUtil.createString(env, el);
            }
            MBeanProxy<?> registration = ObjectHandles.getGlobal().get(WordFactory.pointer(handle));
            AttributeList attributesList = registration.getBean().getAttributes(attrNames);
            scope.setObjectResult(attributeListToRaw(env, attributesList));
        }
        return scope.getObjectResult();
    }

    /**
     * Sets the given {@link DynamicMBean}'s attribute values.
     */
    @JMXToLibGraal(SetAttributes)
    @CEntryPoint(name = "Java_org_graalvm_compiler_hotspot_management_JMXToLibGraalCalls_setAttributes")
    @SuppressWarnings({"try", "unused"})
    static JNI.JByteArray setAttributes(JNI.JNIEnv env, JNI.JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateThreadId, long handle, JNI.JByteArray attributes) {
        JNILibGraalScope<Id> scope = new JNILibGraalScope<>(SetAttributes, env);
        try (JNILibGraalScope<Id> s = scope) {
            Map<String, Object> map = rawToMap(env, attributes);
            AttributeList attributesList = new AttributeList();
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                attributesList.add(new Attribute(entry.getKey(), entry.getValue()));
            }
            MBeanProxy<?> registration = ObjectHandles.getGlobal().get(WordFactory.pointer(handle));
            attributesList = registration.getBean().setAttributes(attributesList);
            scope.setObjectResult(attributeListToRaw(env, attributesList));
        }
        return scope.getObjectResult();
    }

    /**
     * Invokes an action on {@link DynamicMBean}.
     */
    @JMXToLibGraal(Invoke)
    @CEntryPoint(name = "Java_org_graalvm_compiler_hotspot_management_JMXToLibGraalCalls_invoke")
    @SuppressWarnings({"try", "unused"})
    static JNI.JByteArray invoke(JNI.JNIEnv env, JNI.JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateThreadId, long handle, JNI.JString hsActionName,
                    JNI.JByteArray hsParams, JNI.JObjectArray hsSignature) {
        JNILibGraalScope<Id> scope = new JNILibGraalScope<>(Invoke, env);
        try (JNILibGraalScope<Id> s = scope) {
            String actionName = JNIUtil.createString(env, hsActionName);
            int len = hsSignature.isNull() ? 0 : JNIUtil.GetArrayLength(env, hsSignature);
            String[] signature = new String[len];
            for (int i = 0; i < len; i++) {
                signature[i] = JNIUtil.createString(env, (JNI.JString) JNIUtil.GetObjectArrayElement(env, hsSignature, i));
            }
            Map<String, Object> map = rawToMap(env, hsParams);
            Object[] params = map.values().toArray(new Object[map.size()]);
            MBeanProxy<?> registration = ObjectHandles.getGlobal().get(WordFactory.pointer(handle));
            try {
                Object result = registration.getBean().invoke(actionName, params, signature);
                AttributeList attributesList = new AttributeList();
                if (result != null) {
                    attributesList.add(new Attribute("result", result));
                }
                scope.setObjectResult(attributeListToRaw(env, attributesList));
            } catch (MBeanException | ReflectionException e) {
                scope.setObjectResult(WordFactory.nullPointer());
            }
        }
        return scope.getObjectResult();
    }

    /**
     * Converts properties encoded as JNI byte array into {@link Map} using {@link OptionsEncoder}.
     */
    private static Map<String, Object> rawToMap(JNI.JNIEnv env, JNI.JByteArray raw) {
        int len = JNIUtil.GetArrayLength(env, raw);
        byte[] serialized = new byte[len];
        CCharPointer elems = JNIUtil.GetByteArrayElements(env, raw, WordFactory.nullPointer());
        try {
            CTypeConversion.asByteBuffer(elems, len).get(serialized);
        } finally {
            JNIUtil.ReleaseByteArrayElements(env, raw, elems, JNI.JArray.MODE_WRITE_RELEASE);
        }
        return OptionsEncoder.decode(serialized);
    }

    /**
     * Encodes a {@link Map} of properties into JNI byte array using {@link OptionsEncoder}.
     */
    private static JNI.JByteArray mapToRaw(JNI.JNIEnv env, Map<String, Object> map) {
        byte[] serialized = OptionsEncoder.encode(map);
        JNI.JByteArray res = JNIUtil.NewByteArray(env, serialized.length);
        CCharPointer elems = JNIUtil.GetByteArrayElements(env, res, WordFactory.nullPointer());
        try {
            CTypeConversion.asByteBuffer(elems, serialized.length).put(serialized);
        } finally {
            JNIUtil.ReleaseByteArrayElements(env, res, elems, JNI.JArray.MODE_WRITE_RELEASE);
        }
        return res;
    }

    /**
     * Encodes an {@link AttributeList} into JNI byte array using {@link OptionsEncoder}.
     */
    private static JNI.JByteArray attributeListToRaw(JNI.JNIEnv env, AttributeList attributesList) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (Object item : attributesList) {
            Attribute attr = (Attribute) item;
            putAttribute(values, attr.getName(), attr.getValue());
        }
        return mapToRaw(env, values);
    }

    /**
     * Serialization of a single attribute into a map.
     */
    private static void putAttribute(Map<String, Object> into, String name, Object value) {
        if (value == null) {
            return;
        } else if (value instanceof CompositeData) {
            putCompositeData(into, name, (CompositeData) value);
        } else {
            into.put(name, value);
        }
    }

    /**
     * Serialization of {@link CompositeData} into a map.
     */
    private static void putCompositeData(Map<String, Object> into, String scope, CompositeData data) {
        String prefix = scope + ".composite";
        CompositeType type = data.getCompositeType();
        into.put(prefix, type.getTypeName());
        for (String key : type.keySet()) {
            Object value = data.get(key);
            String name = prefix + '.' + key;
            putAttribute(into, name, value);
        }
    }

    static {
        JNIUtil.checkToLibGraalCalls(JMXToLibGraalEntryPoints.class, JMXToLibGraalCalls.class, JMXToLibGraal.class);
    }
}
