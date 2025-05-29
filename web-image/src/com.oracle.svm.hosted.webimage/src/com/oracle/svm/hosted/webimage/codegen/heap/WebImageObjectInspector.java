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
package com.oracle.svm.hosted.webimage.codegen.heap;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import org.graalvm.nativeimage.c.function.CFunctionPointer;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.core.meta.MethodPointer;
import com.oracle.svm.hosted.config.DynamicHubLayout;
import com.oracle.svm.hosted.config.HybridLayout;
import com.oracle.svm.hosted.meta.HostedField;
import com.oracle.svm.hosted.meta.HostedType;
import com.oracle.svm.hosted.meta.HostedUniverse;
import com.oracle.svm.hosted.meta.PatchedWordConstant;
import com.oracle.svm.hosted.webimage.codegen.WebImageJSProviders;
import com.oracle.svm.hosted.webimage.codegen.WebImageTypeControl;
import com.oracle.svm.webimage.object.ConstantIdentityMapping;
import com.oracle.svm.webimage.object.ObjectInspector;
import com.oracle.svm.webimage.type.TypeControl;

import jdk.graal.compiler.debug.GraalError;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.PrimitiveConstant;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Recursively inspects objects and everything they point to.
 *
 * This is used to collect information about objects that were initialized at compile-time and thus
 * are lowered already initialized into the image.
 */
public class WebImageObjectInspector extends ObjectInspector {

    private final WebImageJSProviders providers;
    private final TypeControl typeControl;
    private final ConstantReflectionProvider constantReflection;

    public WebImageObjectInspector(WebImageJSProviders providers) {
        this.providers = providers;
        this.typeControl = providers.typeControl();
        this.constantReflection = providers.getConstantReflection();
    }

    /*
     * every object o being parameter to this function must have been replaced already if object
     * Replacements for original o are given
     */
    @Override
    @SuppressWarnings("unchecked")
    public ObjectDefinition inspectObject(JavaConstant c, Object reason, ConstantIdentityMapping identityMapping) {
        if (c.isNull()) {
            return NULL;
        }

        if (c instanceof PrimitiveConstant primitiveConstant) {
            return buildValueType(primitiveConstant);
        }

        if (identityMapping.hasMappingForObject(c)) {
            ObjectDefinition def = identityMapping.getDefByObject(c);
            def.addReason(reason);
            return def;
        }

        assert !isFrozen() : "Object inspector already frozen";

        HostedType type = (HostedType) providers.getMetaAccess().lookupJavaType(c);

        ObjectDefinition odef;
        if (type.isArray()) {
            odef = new ArrayType<>(c, type.getComponentType(), reason);
        } else if (type.getJavaClass() == String.class) {
            String strValue = providers.getSnippetReflection().asObject(String.class, c);
            odef = new StringType(c, strValue, reason);
        } else {
            ClassFieldList fields = buildObjectTypeFieldList(type, identityMapping);
            odef = new ObjectType(c, type, fields, reason);
        }

        /*
         * The object definition has to be put into the map before it is fully built so that
         * recursive builds for the same object don't lead to infinity recursion.
         */
        identityMapping.putObjectDef(odef);

        if (odef instanceof ArrayType<?>) {
            buildArrayType((ArrayType<ObjectDefinition>) odef, c, identityMapping);
        } else if (odef instanceof StringType stringType) {
            buildStringType(stringType, identityMapping);
        } else {
            buildObjectType((ObjectType) odef, c, identityMapping);
        }

        return odef;
    }

    protected static ValueType buildValueType(PrimitiveConstant c) {
        JavaKind kind = c.getJavaKind();
        if (kind == JavaKind.Boolean) {
            return c.asBoolean() ? TRUE : FALSE;
        }

        if (c.isDefaultForKind()) {
            return switch (kind) {
                case Byte -> ZERO_BYTE;
                case Short -> ZERO_SHORT;
                case Char -> ZERO_CHAR;
                case Int -> ZERO_INT;
                case Float -> ZERO_FLOAT;
                case Long -> ZERO_LONG;
                case Double -> ZERO_DOUBLE;
                default -> throw GraalError.shouldNotReachHere(c.toString());
            };
        }

        return ValueType.forConstant(c);
    }

    protected ClassFieldList buildObjectTypeFieldList(HostedType type, ConstantIdentityMapping identityMapping) {
        if (identityMapping.hasListForType(type)) {
            return identityMapping.getListForType(type);
        }

        ClassFieldList fields = new ClassFieldList();
        fields.type = type;
        fields.fields = new ArrayList<>();

        // Request type name because it is needed when lowering the list
        typeControl.requestTypeName(type);

        for (HostedField f : type.getInstanceFields(true)) {
            /*
             * We need to have access to typeCheckSlots as a field for instanceof checks. However,
             * we don't include hybrid fields or the hub's vtable (there is special handing for
             * vtables in TypeVtableLowered).
             */
            if (HybridLayout.isHybridField(f) || f.equals(DynamicHubLayout.singleton().vTableField)) {
                continue;
            }

            if (!f.isRead()) {
                continue;
            }

            if (isReferenceType(f)) {
                fields.fields.add(f);
            } else if (isPrimitiveType(f)) {
                /*
                 * primitives are not replaced
                 */
                assert f.isAccessed() : f;
                fields.fields.add(f);
            } else if (isWordType(f)) {
                fields.fields.add(f);
            }
        }

        identityMapping.putFieldList(fields);
        return fields;
    }

    private static boolean isWordType(HostedField f) {
        // Word types have fields with reference types, but their storage kind is primitive.
        return !f.getType().isPrimitive() && f.getType().getStorageKind().isPrimitive() && (f.isAccessed() || f.isWritten());
    }

    private static boolean isPrimitiveType(HostedField f) {
        return f.getType().isPrimitive();
    }

    private static boolean isReferenceType(HostedField f) {
        return f.getType().getStorageKind() == JavaKind.Object && (f.isAccessed() || f.isWritten());
    }

    private void buildObjectType(ObjectType out, JavaConstant c, ConstantIdentityMapping identityMapping) {
        ClassFieldList fields = out.fields;
        List<ObjectDefinition> members = new ArrayList<>();
        HostedUniverse hUniverse = ((WebImageTypeControl) typeControl).getHUniverse();
        for (HostedField f : fields.fields) {
            if (f.getJavaKind().isObject() && f.getType().getStorageKind().isObject()) {
                if (!f.isValueAvailable()) {
                    // Use NULL for computed fields such as StringInternSupport.imageInternedStrings
                    // WebImageTypeControl.postProcess will patch the right value
                    members.add(NULL);
                    continue;
                }

                JavaConstant fieldValue = constantReflection.readFieldValue(f, c);
                members.add(inspectObject(fieldValue, out, identityMapping));
            } else if (f.getType().isPrimitive() || (f.getJavaKind().isObject() && f.getType().getStorageKind().isPrimitive())) {
                JavaConstant fieldValue = constantReflection.readFieldValue(f, c);
                if (fieldValue instanceof PatchedWordConstant pwc && pwc.getWord() instanceof MethodPointer pointer) {
                    AnalysisMethod method = (AnalysisMethod) pointer.getMethod();
                    ResolvedJavaMethod hostedMethod = hUniverse.lookup(method);

                    // The target class is reached via reflection, thus needs to registered
                    // explicitly.
                    typeControl.requestTypeName(hostedMethod.getDeclaringClass());

                    int index = identityMapping.addMethodPointer(hostedMethod);
                    members.add(new MethodPointerType(pwc, hostedMethod, index, f));
                } else if (fieldValue instanceof PrimitiveConstant primitiveConstant) {
                    members.add(inspectObject(primitiveConstant, out, identityMapping));
                } else {
                    throw GraalError.shouldNotReachHere(fieldValue.toString());
                }
            }
        }

        out.members = members;

        assert members.size() == fields.fields.size() : members.size() + " != " + fields.fields.size();
    }

    private static void buildStringType(StringType out, ConstantIdentityMapping identityMapping) {
        identityMapping.putString(out);
    }

    private void buildArrayType(ArrayType<ObjectDefinition> out, JavaConstant c, ConstantIdentityMapping identityMapping) {
        assert c.isNonNull();

        ResolvedJavaType arrayType = providers.getMetaAccess().lookupJavaType(c);
        assert arrayType.isArray() : c;

        HostedType componentType = (HostedType) arrayType.getComponentType();

        typeControl.requestHubName(componentType.getArrayClass());

        int length = Objects.requireNonNull(constantReflection.readArrayLength(c));

        // Tracks whether all array elements are exactly the same
        out.isAllEqual = length != 0;
        out.elements = new ArrayList<>(length);

        Consumer<ObjectDefinition> addElement = (odef) -> {
            out.elements.add(odef);
            if (out.isAllEqual && !odef.equals(out.elements.get(0))) {
                out.isAllEqual = false;
            }
        };

        if (componentType.isPrimitive()) {
            for (int i = 0; i < length; i++) {
                JavaConstant valueConstant = constantReflection.readArrayElement(c, i);
                ObjectDefinition odef = inspectObject(valueConstant, out, identityMapping);

                addElement.accept(odef);
            }

        } else {
            for (int i = 0; i < length; i++) {
                JavaConstant valueConstant = constantReflection.readArrayElement(c, i);

                if (valueConstant.isNull()) {
                    addElement.accept(NULL);
                    continue;
                }

                boolean continueInspection = false;
                Class<?> tc = ((HostedType) providers.getMetaAccess().lookupJavaType(valueConstant)).getJavaClass();
                // bailout on function ptrs
                for (Class<?> interfaces : tc.getInterfaces()) {
                    if (interfaces.equals(CFunctionPointer.class)) {
                        continueInspection = true;
                        break;
                    }
                }
                if (continueInspection) {
                    continue;
                }
                /*
                 * replace the elements here, we can never register the dynamic type, because if it
                 * is replace it is not part of the universe
                 */
                addElement.accept(inspectObject(valueConstant, out, identityMapping));
            }

        }
    }
}
