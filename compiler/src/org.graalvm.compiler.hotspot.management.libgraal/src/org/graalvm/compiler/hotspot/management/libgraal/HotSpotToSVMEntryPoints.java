/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
import org.graalvm.libgraal.jni.HotSpotToSVMScope;
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
 * Entry points in SVM for calls from HotSpot.
 */
final class HotSpotToSVMEntryPoints {

    private HotSpotToSVMEntryPoints() {
    }

    @CEntryPoint(name = "Java_org_graalvm_compiler_hotspot_management_HotSpotToSVMCalls_attachThread", builtin = CEntryPoint.Builtin.ATTACH_THREAD)
    static native long attachThread(JNI.JNIEnv env, JNI.JClass hsClazz, @CEntryPoint.IsolateContext long isolateId);

    @CEntryPoint(name = "Java_org_graalvm_compiler_hotspot_management_HotSpotToSVMCalls_detachThread", builtin = CEntryPoint.Builtin.DETACH_THREAD)
    static native void detachThread(JNI.JNIEnv env, JNI.JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateThreadId);

    /**
     * Returns the pending {@link DynamicMBean} registrations.
     */
    @CEntryPoint(name = "Java_org_graalvm_compiler_hotspot_management_HotSpotToSVMCalls_pollRegistrations")
    @SuppressWarnings({"try", "unused"})
    static JNI.JLongArray pollRegistrations(JNI.JNIEnv env, JNI.JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateThreadId) {
        HotSpotToSVMScope<Id> scope = new HotSpotToSVMScope<>(Id.PollRegistrations, env);
        try (HotSpotToSVMScope<Id> s = scope) {
            List<MBeanProxy<?>> registrations = MBeanProxy.drain();
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
    @CEntryPoint(name = "Java_org_graalvm_compiler_hotspot_management_HotSpotToSVMCalls_finishRegistration")
    @SuppressWarnings({"try", "unused"})
    static void finishRegistration(JNI.JNIEnv env, JNI.JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateThreadId, JNI.JLongArray svmRegistrations) {
        try (HotSpotToSVMScope<Id> s = new HotSpotToSVMScope<>(Id.FinishRegistration, env)) {
            long len = JNIUtil.GetArrayLength(env, svmRegistrations);
            CLongPointer elems = JNIUtil.GetLongArrayElements(env, svmRegistrations, WordFactory.nullPointer());
            try {
                ObjectHandles globalHandles = ObjectHandles.getGlobal();
                for (int i = 0; i < len; i++) {
                    MBeanProxy<?> registration = globalHandles.get(WordFactory.pointer(elems.read(i)));
                    registration.finishRegistration();
                }
            } finally {
                JNIUtil.ReleaseLongArrayElements(env, svmRegistrations, elems, JNI.JArray.MODE_RELEASE);
            }
        }
    }

    /**
     * Returns the name to use to register the MBean.
     */
    @CEntryPoint(name = "Java_org_graalvm_compiler_hotspot_management_HotSpotToSVMCalls_getObjectName")
    @SuppressWarnings({"try", "unused"})
    static JNI.JString getObjectName(JNI.JNIEnv env, JNI.JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateThreadId, long svmRegistration) {
        HotSpotToSVMScope<Id> scope = new HotSpotToSVMScope<>(Id.GetObjectName, env);
        try (HotSpotToSVMScope<Id> s = scope) {
            ObjectHandles globalHandles = ObjectHandles.getGlobal();
            MBeanProxy<?> registration = globalHandles.get(WordFactory.pointer(svmRegistration));
            String name = registration.getName();
            scope.setObjectResult(JNIUtil.createHSString(env, name));
        }
        return scope.getObjectResult();
    }

    /**
     * Returns the {@link MBeanInfo} encoded as a byte array using {@link OptionsEncoder}.
     */
    @CEntryPoint(name = "Java_org_graalvm_compiler_hotspot_management_HotSpotToSVMCalls_getMBeanInfo")
    @SuppressWarnings({"try", "unused"})
    static JNI.JByteArray getMBeanInfo(JNI.JNIEnv env, JNI.JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateThreadId, long svmRegistration) {
        HotSpotToSVMScope<Id> scope = new HotSpotToSVMScope<>(Id.GetMBeanInfo, env);
        try (HotSpotToSVMScope<Id> s = scope) {
            ObjectHandles globalHandles = ObjectHandles.getGlobal();
            MBeanProxy<?> registration = globalHandles.get(WordFactory.pointer(svmRegistration));
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
    @CEntryPoint(name = "Java_org_graalvm_compiler_hotspot_management_HotSpotToSVMCalls_getAttributes")
    @SuppressWarnings({"try", "unused"})
    static JNI.JByteArray getAttributes(JNI.JNIEnv env, JNI.JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateThreadId, long svmRegistration, JNI.JObjectArray requiredAttributes) {
        HotSpotToSVMScope<Id> scope = new HotSpotToSVMScope<>(Id.GetAttributes, env);
        try (HotSpotToSVMScope<Id> s = scope) {
            int len = JNIUtil.GetArrayLength(env, requiredAttributes);
            String[] attrNames = new String[len];
            for (int i = 0; i < len; i++) {
                JNI.JString el = (JNI.JString) JNIUtil.GetObjectArrayElement(env, requiredAttributes, i);
                attrNames[i] = JNIUtil.createString(env, el);
            }
            MBeanProxy<?> registration = ObjectHandles.getGlobal().get(WordFactory.pointer(svmRegistration));
            AttributeList attributesList = registration.getBean().getAttributes(attrNames);
            scope.setObjectResult(attributeListToRaw(env, attributesList));
        }
        return scope.getObjectResult();
    }

    /**
     * Sets the given {@link DynamicMBean}'s attribute values.
     */
    @CEntryPoint(name = "Java_org_graalvm_compiler_hotspot_management_HotSpotToSVMCalls_setAttributes")
    @SuppressWarnings({"try", "unused"})
    static JNI.JByteArray setAttributes(JNI.JNIEnv env, JNI.JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateThreadId, long svmRegistration, JNI.JByteArray attributes) {
        HotSpotToSVMScope<Id> scope = new HotSpotToSVMScope<>(Id.SetAttributes, env);
        try (HotSpotToSVMScope<Id> s = scope) {
            Map<String, Object> map = rawToMap(env, attributes);
            AttributeList attributesList = new AttributeList();
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                attributesList.add(new Attribute(entry.getKey(), entry.getValue()));
            }
            MBeanProxy<?> registration = ObjectHandles.getGlobal().get(WordFactory.pointer(svmRegistration));
            attributesList = registration.getBean().setAttributes(attributesList);
            scope.setObjectResult(attributeListToRaw(env, attributesList));
        }
        return scope.getObjectResult();
    }

    /**
     * Invokes an action on {@link DynamicMBean}.
     */
    @CEntryPoint(name = "Java_org_graalvm_compiler_hotspot_management_HotSpotToSVMCalls_invoke")
    @SuppressWarnings({"try", "unused"})
    static JNI.JByteArray invoke(JNI.JNIEnv env, JNI.JClass hsClazz, @CEntryPoint.IsolateThreadContext long isolateThreadId, long svmRegistration, JNI.JString hsActionName,
                    JNI.JByteArray hsParams, JNI.JObjectArray hsSignature) {
        HotSpotToSVMScope<Id> scope = new HotSpotToSVMScope<>(Id.Invoke, env);
        try (HotSpotToSVMScope<Id> s = scope) {
            String actionName = JNIUtil.createString(env, hsActionName);
            int len = hsSignature.isNull() ? 0 : JNIUtil.GetArrayLength(env, hsSignature);
            String[] signature = new String[len];
            for (int i = 0; i < len; i++) {
                signature[i] = JNIUtil.createString(env, (JNI.JString) JNIUtil.GetObjectArrayElement(env, hsSignature, i));
            }
            Map<String, Object> map = rawToMap(env, hsParams);
            Object[] params = map.values().toArray(new Object[map.size()]);
            MBeanProxy<?> registration = ObjectHandles.getGlobal().get(WordFactory.pointer(svmRegistration));
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
}

/**
 * An identifier for a call from HotSpot to SVM.
 */
enum Id {
    DefineClasses,
    FinishRegistration,
    GetAttributes,
    GetFactory,
    GetMBeanInfo,
    GetObjectName,
    Invoke,
    NewMBean,
    PollRegistrations,
    RegisterNatives,
    SetAttributes
}
