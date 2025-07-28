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
package com.oracle.svm.hosted.webimage.codegen;

import static com.oracle.svm.webimage.hightiercodegen.Emitter.of;

import java.lang.reflect.Array;
import java.math.BigInteger;
import java.util.HashSet;
import java.util.Set;

import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.hosted.meta.HostedType;
import com.oracle.svm.webimage.hightiercodegen.Emitter;
import com.oracle.svm.webimage.type.TypeControl;

import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;

/**
 * Lowers tweaks and modifications to the normal runtime.
 */
public class RuntimeModificationLowerer {

    /**
     * Classes for which the hub needs to be accessible at runtime by name.
     *
     * @see #assignToRuntimeHubs
     */
    private static final Set<Class<?>> COMPULSORY_RUNTIME_HUBS = new HashSet<>();

    static {
        for (JavaKind kind : JavaKind.values()) {
            if (kind.isPrimitive() && kind != JavaKind.Void) {
                COMPULSORY_RUNTIME_HUBS.add(kind.toBoxedJavaClass());
            }
        }
        COMPULSORY_RUNTIME_HUBS.add(Class.class);
        COMPULSORY_RUNTIME_HUBS.add(String.class);
        COMPULSORY_RUNTIME_HUBS.add(BigInteger.class);
    }

    public void lower(JSCodeGenTool tool) {
        tool.getCodeBuffer().emitNewLine();
        indexHubs(tool);
        storeBoxedHubs(tool);
        tool.getCodeBuffer().emitNewLine();
    }

    private static void storeBoxedHubs(JSCodeGenTool tool) {
        tool.genComment("Store the corresponding boxed type hub into each primitive type hub.");
        for (JavaKind kind : JavaKind.values()) {
            if (kind.isPrimitive() && kind != JavaKind.Void) {
                insertPrimitiveHubProperties(tool, kind);
            }
        }
    }

    private static void insertPrimitiveHubProperties(JSCodeGenTool tool, JavaKind kind) {
        try {
            Class<?> primitiveClass = kind.toJavaClass();
            Class<?> boxedClass = kind.toBoxedJavaClass();
            MetaAccessProvider meta = tool.getProviders().getMetaAccess();
            TypeControl typeControl = tool.getJSProviders().typeControl();
            HostedType primitiveType = (HostedType) meta.lookupJavaType(primitiveClass);
            HostedType boxedType = (HostedType) meta.lookupJavaType(boxedClass);
            assignHubProperty(tool, typeControl, primitiveType, of(RuntimeConstants.RUNTIME_SYMBOL + ".boxedHub"), typeControl.requestHubName(boxedType));
            // Note: The valueOf and <type>Value methods are always added to the universe in the
            // WebImageGenerator.
            String boxedName = typeControl.requestTypeName(boxedType);
            String valueOfName = typeControl.requestMethodName(meta.lookupJavaMethod(boxedClass.getMethod("valueOf", primitiveClass)));
            assignHubProperty(tool, typeControl, primitiveType, of(RuntimeConstants.RUNTIME_SYMBOL + ".box"), boxedName + "." + valueOfName);
            String toPrimitiveName = typeControl.requestMethodName(meta.lookupJavaMethod(boxedClass.getMethod(kind.getJavaName() + "Value")));
            assignHubProperty(tool, typeControl, primitiveType, of(RuntimeConstants.RUNTIME_SYMBOL + ".unbox"), boxedName + ".prototype." + toPrimitiveName);
        } catch (NoSuchMethodException e) {
            throw JVMCIError.shouldNotReachHere(e);
        }
    }

    private static void assignHubProperty(JSCodeGenTool tool, TypeControl typeControl, HostedType primitiveType, Emitter propertyExpression, String value) {
        tool.genPropertyBracketAccessWithExpression(of(typeControl.requestHubName(primitiveType)), propertyExpression);
        tool.genAssignment();
        tool.getCodeBuffer().emitText(value);
        tool.genResolvedVarDeclPostfix(null);
    }

    private static void indexHubs(JSCodeGenTool tool) {
        WebImageTypeControl typeControl = tool.getJSProviders().typeControl();
        tool.genComment("Create an index between class names and hubs in the image.");
        MetaAccessProvider meta = tool.getProviders().getMetaAccess();
        for (HostedType type : typeControl.emittedTypes()) {
            assignJsClassToHub(tool, typeControl, type);
            if (COMPULSORY_RUNTIME_HUBS.contains(type.getJavaClass())) {
                // Only classes necessary for coercion are emitted.
                assignToRuntimeHubs(tool, typeControl, type, type.getJavaClass().getName());
            }
        }
        for (JavaKind kind : JavaKind.values()) {
            if (kind.isPrimitive() && kind != JavaKind.Void) {
                Class<?> arrayClass = Array.newInstance(kind.toJavaClass(), 0).getClass();
                assignToRuntimeHubs(tool, typeControl, (HostedType) meta.lookupJavaType(arrayClass), kind.getJavaName() + "[]");
            }
        }
    }

    /**
     * Generates code to put a reference to the generated JavaScript class for the given type into
     * the type's {@link DynamicHub} class through the {@link RuntimeConstants#JS_CLASS_SYMBOL}
     * symbol.
     *
     * <pre>{@code
     * <hub constant>[JC] = _String;
     * }</pre>
     */
    private static void assignJsClassToHub(JSCodeGenTool tool, TypeControl typeControl, HostedType type) {
        tool.genPropertyBracketAccessWithExpression(of(typeControl.requestHubName(type)), of(RuntimeConstants.JS_CLASS_SYMBOL));
        tool.genAssignment();
        tool.getCodeBuffer().emitText(typeControl.requestTypeName(type));
        tool.genResolvedVarDeclPostfix(null);
    }

    /**
     * Generates code to assign the hub instance into the {@link RuntimeConstants#RUNTIME_HUBS} map
     * with the given name as the key.
     *
     * <pre>{@code
     * RH['java.lang.Double'] = <hub constant>;
     * }</pre>
     *
     * @param type Type whose hub is assigned
     * @param name Name under which corresponding hub can be looked up. Either fully qualified class
     *            name or {@code <primitive>[]} for arrays.
     */
    private static void assignToRuntimeHubs(JSCodeGenTool tool, TypeControl typeControl, HostedType type, String name) {
        tool.genPropertyBracketAccess(of(RuntimeConstants.RUNTIME_HUBS), name);
        tool.genAssignment();
        tool.getCodeBuffer().emitText(typeControl.requestHubName(type));
        tool.genResolvedVarDeclPostfix(null);
    }

}
