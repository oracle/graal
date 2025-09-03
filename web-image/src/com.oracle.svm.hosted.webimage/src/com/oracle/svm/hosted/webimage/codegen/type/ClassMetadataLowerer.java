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
package com.oracle.svm.hosted.webimage.codegen.type;

import static com.oracle.svm.hosted.webimage.codegen.RuntimeConstants.RUNTIME_SYMBOL;
import static com.oracle.svm.hosted.webimage.codegen.RuntimeConstants.UNDEFINED;
import static com.oracle.svm.webimage.hightiercodegen.Emitter.of;
import static com.oracle.svm.webimage.hightiercodegen.Emitter.ofArray;
import static com.oracle.svm.webimage.hightiercodegen.Emitter.ofObject;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.graalvm.webimage.api.JSObject;

import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.config.ObjectLayout;
import com.oracle.svm.core.meta.SharedType;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.graal.meta.SubstrateField;
import com.oracle.svm.hosted.meta.HostedField;
import com.oracle.svm.hosted.meta.HostedInstanceClass;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.meta.HostedType;
import com.oracle.svm.hosted.webimage.JSCodeBuffer;
import com.oracle.svm.hosted.webimage.codegen.JSCodeGenTool;
import com.oracle.svm.hosted.webimage.codegen.RuntimeConstants;
import com.oracle.svm.hosted.webimage.js.JSKeyword;
import com.oracle.svm.hosted.webimage.util.ReflectUtil;
import com.oracle.svm.webimage.api.Nothing;
import com.oracle.svm.webimage.hightiercodegen.Emitter;
import com.oracle.svm.webimage.hightiercodegen.IEmitter;
import com.oracle.svm.webimage.type.TypeControl;

import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.meta.Signature;

/**
 * Responsible for lowering offset field table and access function for each type.
 */
public class ClassMetadataLowerer {

    /**
     * The name of the field table property of a type (written to the object type itself (the
     * function)).
     *
     * The table contains lambdas used as field accessors.
     */
    public static final String FIELD_TABLE_NAME = "ft";

    /**
     * Every instance type that needs a hash code field gets assigned one with this specific name.
     */
    public static final String INSTANCE_TYPE_HASHCODE_FIELD_NAME = "__hc";

    /**
     * Name of the internal JavaScript class that stores extra runtime metadata for each class.
     */
    public static final String CLASS_METADATA_CLASS = "ClassMetadata";

    /**
     * Name of a constructor method.
     */
    public static final String CTOR_NAME = "<init>";

    private static boolean typeNeedsHashCodeField(HostedType t) {
        return t instanceof HostedInstanceClass;
    }

    private static boolean shouldNotGenerate(HostedType t) {
        HostedField[] fields = t.getInstanceFields(true);
        return fields.length == 0 && !typeNeedsHashCodeField(t);
    }

    /**
     * @return The offset of the field or -1 if there is no offset
     */
    public static int getFieldOffset(HostedField f) {
        if (!f.hasLocation()) {
            return -1;
        }

        SharedType metaType = f.getDeclaringClass().getHub().getMetaType();

        // if this type has a meta type, that has the same field attached on a different
        // location we must use the different location (-> e.g. for truffle nodes)
        if (metaType != null) {
            for (ResolvedJavaField metafield : metaType.getInstanceFields(true)) {
                SubstrateField substrateMetafield = (SubstrateField) metafield;

                if (substrateMetafield.getName().equals(f.getName())) {
                    return substrateMetafield.getLocation();
                }
            }
        }

        return f.getLocation();
    }

    /**
     * Creates a mapping from offset to field name for the given type.
     */
    private static Map<Integer, String> generateFieldMap(HostedType t, JSCodeGenTool tools) {
        /*
         * we do not want to recursively dispatch the field offset lookup, so we generate all fields
         * here
         */
        if (shouldNotGenerate(t)) {
            return null;
        }

        // Maps field offsets to field names
        HashMap<Integer, String> fieldMap = new HashMap<>();

        for (HostedField f : t.getInstanceFields(true)) {
            if (JSObject.class.isAssignableFrom(t.getJavaClass())) {
                continue;
            }

            int offset = getFieldOffset(f);

            if (offset < 0) {
                continue;
            }

            String fieldName = tools.getJSProviders().typeControl().requestFieldName(f);
            fieldMap.put(offset, fieldName);
        }

        ObjectLayout ol = ConfigurationValues.getObjectLayout();
        VMError.guarantee(ol.isIdentityHashFieldInObjectHeader());
        fieldMap.put(ol.getObjectHeaderIdentityHashOffset(), INSTANCE_TYPE_HASHCODE_FIELD_NAME);

        return fieldMap;
    }

    /**
     * We lower the metadata for all instance classes as a {@code ClassMetadata} object stored
     * behind the {@code runtime.symbol.classMeta} field.
     *
     * The field-lookup table is an object that contains get or set accessors, depending on whether
     * the second argument is undefined:
     *
     * <pre>
     * {
     *     "offset1": (self, v) => v !== undefined ? self.fieldname1 = v : self.fieldname1,
     *     "offset2": (self, v) => v !== undefined ? self.fieldname2 = v : self.fieldname2,
     *     ...
     * }
     * </pre>
     *
     * The functionalInterface field is set to either undefined if the class is not a functional
     * interface, or to a function that invokes the single abstract method of the functional
     * interface:
     *
     * <pre>
     *     (self, ...args) => singleAbstractMethod.apply(self, args)
     * </pre>
     */
    public static void lowerClassMetadata(HostedType t, JSCodeGenTool tools, Map<HostedMethod, StructuredGraph> methodGraphs) {
        JSCodeBuffer buffer = (JSCodeBuffer) tools.getCodeBuffer();
        buffer.emitScopeBegin();
        buffer.emitLetDeclPrefix("_");
        String className = tools.getJSProviders().typeControl().requestTypeName(t);
        buffer.emitText(RuntimeConstants.GET_CLASS_PROTOTYPE + "(" + className + ")");
        buffer.emitResolvedBuiltInVarDeclPostfix(null);
        buffer.emitText("lazy");
        buffer.emitKeyword(JSKeyword.LPAR);
        tools.genCommaList(of(t), of(RuntimeConstants.CLASS_META_SYMBOL));
        buffer.emitText(", () => ");
        buffer.emitNew();
        buffer.emitText(CLASS_METADATA_CLASS);
        buffer.emitKeyword(JSKeyword.LPAR);
        tools.genCommaList(
                        createAccessorMap(t, tools),
                        createSingleAbstractMethod(t, tools, className),
                        createMethodSignatures(t, tools, methodGraphs, className));
        buffer.emitKeyword(JSKeyword.RPAR);
        buffer.emitKeyword(JSKeyword.RPAR);
        buffer.emitInsEnd();
        buffer.emitScopeEnd();
        buffer.emitNewLine();
    }

    private static Emitter createAccessorMap(HostedType t, JSCodeGenTool tools) {
        Map<Integer, String> fieldMap = generateFieldMap(t, tools);

        if (fieldMap == null) {
            return of("{}");
        }

        Map<Object, IEmitter> functionMap = fieldMap.entrySet().stream().map(e -> {
            String fieldName = e.getValue();
            String function = "(s, v) => v !== " + UNDEFINED + " ? s." + fieldName + " = v : s." + fieldName;
            return new AbstractMap.SimpleEntry<>(Integer.toString(e.getKey()), of(function));
        }).collect(Collectors.toMap(AbstractMap.SimpleEntry::getKey, AbstractMap.SimpleEntry::getValue));
        return ofObject(functionMap);
    }

    private static Emitter createSingleAbstractMethod(HostedType t, JSCodeGenTool tools, String className) {
        Optional<HostedMethod> sam = ReflectUtil.singleAbstractMethodForClass(tools.getProviders().getMetaAccess(), t);
        if (sam.isPresent()) {
            return methodMetadataEmitter(tools, className, sam.get());
        }
        return of(UNDEFINED);
    }

    private static Emitter createMethodSignatures(HostedType t, JSCodeGenTool tools, Map<HostedMethod, StructuredGraph> methodGraphs, String className) {
        if (!isInstantiatedOrSubclassInstantiated(t)) {
            // The analysis decided that this type is not instantiated in the code, and is not in
            // the image heap.
            // No need to emit its method metadata.
            return of(UNDEFINED);
        }
        HashMap<String, ArrayList<Emitter>> methodGroups = new HashMap<>();
        for (HostedMethod method : t.getAllDeclaredMethods()) {
            if (!shouldEmitMetadata(method)) {
                // Java Proxies only expose public instance methods.
                continue;
            }
            if (methodGraphs.get(method) == null) {
                // Some methods (that survive analysis) are not generated.
                continue;
            }
            ArrayList<Emitter> emitters = methodGroups.get(method.getName());
            if (emitters == null) {
                emitters = new ArrayList<>();
                methodGroups.put(method.getName(), emitters);
            }
            Emitter methodMetadata = methodMetadataEmitter(tools, className, method);
            emitters.add(methodMetadata);
        }
        HashMap<Object, IEmitter> methods = new HashMap<>();
        for (Map.Entry<String, ArrayList<Emitter>> entry : methodGroups.entrySet()) {
            ArrayList<Emitter> emitters = entry.getValue();
            String key = entry.getKey();
            // Constructor metadata is stored under a special symbolic key.
            methods.put(key.equals(CTOR_NAME) ? of(RUNTIME_SYMBOL + ".ctor") : key, ofArray(emitters.toArray(new Emitter[emitters.size()])));
        }
        return ofObject(methods);
    }

    public static boolean isInstantiatedOrSubclassInstantiated(HostedType t) {
        return t.getWrapped().isAnySubtypeInstantiated();
    }

    public static boolean shouldEmitMetadata(HostedMethod method) {
        // We expect people to declare package-private exported JavaScript classes, so we are
        // lenient to constructor definitions (which may be implicit).
        return method.isPublic() || (method.isConstructor() && method.isPackagePrivate());
    }

    private static Emitter methodMetadataEmitter(JSCodeGenTool tools, String className, HostedMethod method) {
        TypeControl typeControl = tools.getJSProviders().typeControl();
        String generatedName = typeControl.requestMethodName(method);
        Signature signature = method.getSignature();
        StringBuilder argHubs = new StringBuilder();
        for (int i = 0; i < signature.getParameterCount(false); i++) {
            argHubs.append(", ");
            argHubs.append(hubNameFor(tools, (HostedType) signature.getParameterType(i, null)));
        }
        String returnHub = hubNameFor(tools, (HostedType) signature.getReturnType(null));
        final Emitter methodMetadata;
        if (method.isStatic()) {
            methodMetadata = of("smmeta(" + className + "." + generatedName + ", " + returnHub + argHubs + ")");
        } else {
            methodMetadata = of("mmeta(_." + generatedName + ", " + returnHub + argHubs + ")");
        }
        return methodMetadata;
    }

    private static String hubNameFor(JSCodeGenTool tools, HostedType type) {
        TypeControl typeControl = tools.getJSProviders().typeControl();
        if (type.getWrapped().isReachable()) {
            // If the type is reachable, then the class will have its initialization info, and the
            // hub will be available.
            // This seems to currently be an invariant in the Native Image codebase.
            return typeControl.requestHubName(type);
        } else {
            // If the type is not reachable, then there should be no instances of that type at
            // runtime. For any runtime object, the type-check for this type would fail.
            // We conveniently use null to denote that the typecheck in JavaScript should fail.
            ResolvedJavaType nothingHub = tools.getProviders().getMetaAccess().lookupJavaType(Nothing.class);
            return typeControl.requestHubName(nothingHub);
        }
    }

}
