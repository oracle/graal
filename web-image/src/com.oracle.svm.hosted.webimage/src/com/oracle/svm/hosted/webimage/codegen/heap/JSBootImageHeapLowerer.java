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

import static com.oracle.svm.webimage.object.ObjectInspector.StringType;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import com.oracle.svm.core.option.HostedOptionValues;
import com.oracle.svm.hosted.meta.HostedArrayClass;
import com.oracle.svm.hosted.meta.HostedField;
import com.oracle.svm.hosted.meta.HostedInstanceClass;
import com.oracle.svm.hosted.meta.HostedType;
import com.oracle.svm.hosted.webimage.JSCodeBuffer;
import com.oracle.svm.hosted.webimage.codegen.Array;
import com.oracle.svm.hosted.webimage.codegen.JSCodeGenTool;
import com.oracle.svm.hosted.webimage.codegen.Runtime;
import com.oracle.svm.hosted.webimage.codegen.WebImageJSProviders;
import com.oracle.svm.hosted.webimage.codegen.wrappers.JSEmitter;
import com.oracle.svm.hosted.webimage.logging.LoggerContext;
import com.oracle.svm.hosted.webimage.options.WebImageOptions;
import com.oracle.svm.hosted.webimage.util.metrics.BootHeapMetricsCollector;
import com.oracle.svm.hosted.webimage.util.metrics.CodeSizeCollector;
import com.oracle.svm.webimage.hightiercodegen.CodeGenTool;
import com.oracle.svm.webimage.hightiercodegen.Emitter;
import com.oracle.svm.webimage.hightiercodegen.IEmitter;
import com.oracle.svm.webimage.object.ConstantIdentityMapping;
import com.oracle.svm.webimage.object.ConstantIdentityMapping.IdentityNode;
import com.oracle.svm.webimage.object.ObjectInspector.ArrayType;
import com.oracle.svm.webimage.object.ObjectInspector.ClassFieldList;
import com.oracle.svm.webimage.object.ObjectInspector.MethodPointerType;
import com.oracle.svm.webimage.object.ObjectInspector.ObjectDefinition;
import com.oracle.svm.webimage.object.ObjectInspector.ObjectType;
import com.oracle.svm.webimage.object.ObjectInspector.ValueType;

import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class JSBootImageHeapLowerer {

    public static final String OBJECT_INIT_FUN = "object_init";
    public static final String OBJECT_HASH_INIT_FUN = "object_init_hash";

    /**
     * Name of the list storing the object field lists.
     */
    public static final String FIELD_LIST = "fieldLists";

    protected final ConstantIdentityMapping identityMapping;
    protected final JSCodeGenTool jsLTools;
    protected final JSCodeBuffer masm;
    protected final WebImageJSProviders providers;

    public JSBootImageHeapLowerer(WebImageJSProviders providers, JSCodeGenTool jsLTools, ConstantIdentityMapping identityMapping) {
        this.identityMapping = identityMapping;
        this.jsLTools = jsLTools;
        this.providers = providers;

        masm = (JSCodeBuffer) jsLTools.getCodeBuffer();
    }

    /**
     * Generates setup code needed for initialization of the static objects.
     */
    public void emitSetup() {
        collectMetrics();
        // Lower field lists for all types for object initializations
        lowerFieldLists();

        /*
         * Lower method pointers to runtime.funtab (defined in runtime.js).
         *
         * Binary serialization of objects contain indices to the table.
         *
         * Assumption: runtime.funtab does not contain any other entries before execution of the
         * following code.
         */
        masm.emitNewLine();
        masm.emitNewLine();
        for (ResolvedJavaMethod method : identityMapping.getMethodPointers()) {
            Runtime.ADD_TO_FUNTAB.emitCall(jsLTools, JSEmitter.ofMethodReference(method));
            masm.emitText(";");
            masm.emitNewLine();
        }
        masm.emitNewLine();
        masm.emitNewLine();
    }

    protected void collectMetrics() {
        BootHeapMetricsCollector metricsCollector = new BootHeapMetricsCollector(LoggerContext.currentContext().currentScope(), jsLTools.getJSProviders().typeControl());

        for (IdentityNode node : identityMapping.identityNodes()) {
            ObjectDefinition def = node.getDefinition();

            if (def instanceof ObjectType) {
                HostedInstanceClass resolvedType = (HostedInstanceClass) providers.getMetaAccess().lookupJavaType(def.getConstant());
                metricsCollector.constantObject(resolvedType);
            } else if (def instanceof ArrayType<?>) {
                metricsCollector.constantArray((ArrayType<?>) def);
            } else if (def instanceof ValueType valueType) {
                // nothing to do, are not declared
                metricsCollector.constantPrimitive(valueType);
            } else if (def instanceof StringType stringType) {
                metricsCollector.constantString(stringType);
            } else if (def instanceof MethodPointerType methodPointerType) {
                metricsCollector.constantMethodPointer(methodPointerType);
            } else {
                JVMCIError.shouldNotReachHere(def.toString());
            }
        }
    }

    /**
     * Generates the all the object declarations for static objects.
     */
    public void lowerDeclarations() {
        for (IdentityNode node : identityMapping.identityNodes()) {
            /*
             * Objects that are not referenced by name don't have to be emitted.
             */
            if (!node.hasName()) {
                continue;
            }

            String name = node.getName();

            ObjectDefinition def = node.getDefinition();
            jsLTools.genResolvedVarDeclPrefix(name);

            if (def instanceof ObjectType) {
                emitObjectDeclaration((ObjectType) def);
                jsLTools.genResolvedVarDeclPostfix(null);
            } else if (def instanceof ArrayType<?>) {
                emitArrayDeclaration((ArrayType<?>) def);
                jsLTools.genResolvedVarDeclPostfix(null);
            } else if (def instanceof ValueType) {
                // nothing to do, are not declared
            } else if (def instanceof StringType) {
                emitStringDeclaration((StringType) def);
                jsLTools.genResolvedVarDeclPostfix(null);
            } else {
                JVMCIError.shouldNotReachHere();
            }
        }
    }

    protected void emitObjectDeclaration(ObjectType otype) {
        JVMCIError.guarantee(otype.getConstant().isNonNull(), "Object must not be null %s", otype);
        jsLTools.genObjectCreate(Emitter.of(otype.type));
    }

    protected void emitArrayDeclaration(ArrayType<?> arrayType) {
        /*
         * all arrays are initially lowered with the ctor call, and during property assignment phase
         * they get assigned their elements
         */
        HostedType t = arrayType.componentType;

        Array.lowerArrayConstructor(t.getJavaKind(), arrayType.length(), jsLTools);
    }

    protected void emitStringDeclaration(StringType strType) {
        Runtime.TO_JAVA_STRING_LAZY.emitCall(jsLTools, Emitter.stringLiteral(strType.stringVal));
    }

    /**
     * Creates a field list for every inspected type.
     * <p>
     * The field list is later used in {@link #emitObjectInitializer} to provide field names to the
     * object_init function.
     * <p>
     * This way all the field names have to be written only once in the field list.
     */
    protected void lowerFieldLists() {
        Supplier<Stream<ClassFieldList>> stream = () -> StreamSupport.stream(Spliterators.spliteratorUnknownSize(identityMapping.classFieldListIterator(), Spliterator.ORDERED), false).sorted(
                        Comparator.comparingInt(ClassFieldList::getId));
        Iterator<ClassFieldList> it = stream.get().iterator();

        assert masm.getScopeIndent() == 0 : masm.getScopeIndent();

        masm.emitNewLine();
        masm.emitText("const " + FIELD_LIST + " = [");

        int lastFieldListId = 0;
        /*
         * Emit field lists in order of field list ids.
         */
        while (it.hasNext()) {
            ClassFieldList classFieldList = it.next();

            masm.setScopeIndent(1);

            int fieldListId = classFieldList.getId();

            // Emit commas after the previous element as well as for all gaps in the list
            for (; lastFieldListId < fieldListId; lastFieldListId++) {
                masm.emitText(",");
            }

            masm.emitNewLine();
            jsLTools.genComment(fieldListId + ": " + classFieldList.type.toString());

            masm.emitText("[");
            masm.setScopeIndent(2);
            masm.emitNewLine();
            jsLTools.genTypeName(classFieldList.type);
            masm.emitText(",");

            for (HostedField f : classFieldList.fields) {
                masm.emitNewLine();
                masm.emitText("[\"");
                jsLTools.genFieldName(f);
                masm.emitText("\",");
                masm.emitIntLiteral(getKindNum(f));
                masm.emitText("]");
                masm.emitText(",");
            }

            masm.setScopeIndent(1);
            masm.emitNewLine();
            masm.emitText("]");
        }

        masm.setScopeIndent(0);
        masm.emitNewLine();
        masm.emitText("];");
    }

    @SuppressWarnings("try")
    public void lowerInitialization() {
        for (IdentityNode node : identityMapping.identityNodes()) {
            ObjectDefinition def = node.getDefinition();

            if (def instanceof StringType) {
                // String types are already initialized when their variables are declared.
                continue;
            }

            if (def instanceof MethodPointerType) {
                // Method pointers do not need to be initialized
                continue;
            }

            if (def instanceof ValueType) {
                JVMCIError.shouldNotReachHere("All value types should have already been resolved");
            }

            int hashCode = providers.getIdentityHashCodeProvider().identityHashCode(node.getDefinition().getConstant());

            if (def instanceof ObjectType) {
                try (CodeSizeCollector collector = CodeSizeCollector.trackObjectSize(jsLTools::getCodeSize)) {
                    emitObjectInitializer(node, (ObjectType) def, hashCode);
                }
            } else if (def instanceof ArrayType<?>) {
                @SuppressWarnings("unchecked")
                ArrayType<ObjectDefinition> atype = (ArrayType<ObjectDefinition>) def;
                try (CodeSizeCollector collector = CodeSizeCollector.trackObjectSize(jsLTools::getCodeSize)) {
                    emitArrayInitializer(node, atype, hashCode);
                }
            } else {
                JVMCIError.shouldNotReachHere("Invalid object definition: " + def.getClass().getName());
            }
        }
    }

    protected void emitHashInitializer(IdentityNode node, int hashCode) {
        if (hashCode != 0) {
            masm.emitNewLine();
            masm.emitText(OBJECT_HASH_INIT_FUN + "(" + node.requestName() + ", ");
            masm.emitIntLiteral(hashCode);
            masm.emitText(");");
        }
    }

    protected void emitObjectInitializer(IdentityNode node, ObjectType odef, int hashCode) {
        if (odef.members.isEmpty()) {
            // We may still need to initialize the hash code, even if there are no other fields.
            emitHashInitializer(node, hashCode);
            return;
        }

        masm.emitNewLine();

        List<IEmitter> args = new ArrayList<>(odef.members.size() + 3);
        args.add(JSEmitter.of(node));
        args.add(Emitter.of(odef.fields.getId()));
        args.add(Emitter.of(hashCode));

        odef.members.forEach(oo -> args.add(valueEmitter(oo)));
        jsLTools.genFunctionCall(null, Emitter.of(OBJECT_INIT_FUN), args.toArray(new IEmitter[0]));
        masm.emitInsEnd();
    }

    /**
     * Packs together all values in the array into an integer array.
     * <p>
     * For values that take fewer bits, multiple can be packed into a single integer.
     *
     * @param bits The number of bits used by array elements.
     * @param getter Function to extract a value from an array element {@link ObjectDefinition}. The
     *            the first 'bits' bits will be used.
     */
    protected static int[] packIntegerValues(ArrayType<ObjectDefinition> atype, int bits, ToIntFunction<ValueType> getter) {
        int len = atype.length();
        assert (32 % bits == 0) : bits;

        // How many elements fit into an int
        int perInt = 32 / bits;

        // For 32 bits, we need a special case, because the general case overflows.
        int mask = bits == 32 ? 0xffffffff : (1 << bits) - 1;

        int[] ints = new int[(len + (perInt - 1)) / perInt];

        for (int i = 0; i < ints.length; i++) {
            int compound = 0;
            for (int j = 0; j < perInt; j++) {
                int idx = i * perInt + j;

                if (idx >= atype.length()) {
                    // The last few elements in the array cannot completely fill up an integer.
                    break;
                }

                ValueType val = (ValueType) atype.elements.get(idx);

                assert val.kind == atype.componentType.getJavaKind() : val.kind + " != " + atype.componentType.getJavaKind();

                int component = getter.applyAsInt(val) & mask;

                compound |= component << (j * bits);
            }

            ints[i] = compound;
        }

        return ints;
    }

    protected void emitArrayInitializer(IdentityNode node, ArrayType<ObjectDefinition> atype, int hashCode) {
        String arrayName = node.requestName();

        HostedType componentType = atype.componentType;

        HostedArrayClass ac = componentType.getArrayClass();
        assert ac != null;
        assert ac.isArray() : ac;
        String hubVarName = jsLTools.getJSProviders().typeControl().requestHubName(ac);

        JavaKind kind = componentType.getJavaKind();

        // If all elements are equal, we can simplify the initialization
        if (atype.isAllEqual) {
            masm.emitNewLine();
            ObjectDefinition firstEl = atype.elements.get(0);
            if (kind == JavaKind.Long) {
                Runtime.ArrayInitFunWithHubBigInt64.emitCall(jsLTools, JSEmitter.of(node), valueEmitter(firstEl), Emitter.of(hubVarName), Emitter.of(hashCode));
            } else {
                Runtime.ArrayInitFunWithHub.emitCall(jsLTools, JSEmitter.of(node), valueEmitter(firstEl), Emitter.of(hubVarName), Emitter.of(hashCode));
            }

            jsLTools.genResolvedVarDeclPostfix(null);
        } else if (kind.isNumericInteger() && kind != JavaKind.Long) {
            /*
             * We pack together non-long integer primitives (e.g. we can pass 32 booleans as a
             * single 32-bit integer) to reduce the amount of code generated for the values.
             */
            int len = atype.length();
            int bits;
            ToIntFunction<ValueType> getter;

            switch (kind) {
                case Boolean:
                    bits = 1;
                    getter = val -> val.asBoolean() ? 1 : 0;
                    break;
                case Byte:
                    bits = 8;
                    getter = ValueType::asByte;
                    break;
                case Short:
                    bits = 16;
                    getter = ValueType::asShort;
                    break;
                case Char:
                    bits = 16;
                    getter = ValueType::asChar;
                    break;
                case Int:
                    bits = 32;
                    getter = ValueType::asInt;
                    break;
                default:
                    throw JVMCIError.shouldNotReachHere("Unhandled integer component type: " + kind);
            }

            int[] ints = packIntegerValues(atype, bits, getter);

            boolean emitBase64ByteArray = false;

            // Determine whether we should use a base64 representation for this array
            if (WebImageOptions.EncodeImageHeapArraysBase64.getValue(HostedOptionValues.singleton())) {
                int numBytes = len * bits / 8;
                // Number of bytes this array uses in base64
                float numBytesB64 = numBytes * 4.0f / 3;

                // Number of bytes this array uses when emitted using integer literals.
                int numBytesLit = 0;

                for (int anInt : ints) {
                    numBytesLit = CodeGenTool.getEfficientIntLiteral(anInt).length() + 1;
                }

                // It only makes sense to emit a base64 string if it takes less space
                emitBase64ByteArray = numBytesB64 < numBytesLit;
            }

            if (emitBase64ByteArray) {
                emitBase64ByteArrayInitializer(node, hubVarName, len, bits, ints, hashCode);
            } else {

                masm.emitNewLine();
                Runtime.PackedArrayInitFunWitHubAndValue.emitReference(jsLTools);
                masm.emitText("(" + arrayName + ", ");
                masm.emitIntLiteral(len);
                masm.emitText(", ");
                masm.emitIntLiteral(bits);
                masm.emitText(", ");

                masm.emitText("[");
                for (int val : ints) {
                    masm.emitIntLiteral(val);
                    masm.emitText(",");
                }
                masm.emitText("], " + hubVarName + ", ");
                masm.emitIntLiteral(hashCode);
                masm.emitText(")");
                jsLTools.genResolvedVarDeclPostfix(null);
            }

        } else {
            masm.emitNewLine();
            if (kind == JavaKind.Long) {
                Runtime.ArrayInitFunWithHubAndValueBigInt64.emitReference(jsLTools);
            } else {
                Runtime.ArrayInitFunWithHubAndValue.emitReference(jsLTools);
            }
            masm.emitText("(" + arrayName + ", ");

            masm.emitText("[");
            List<IEmitter> elements = new ArrayList<>(atype.length());
            atype.elements.forEach(oo -> elements.add(valueEmitter(oo)));
            jsLTools.genCommaList(elements);
            masm.emitText("], " + hubVarName + ", ");
            masm.emitIntLiteral(hashCode);
            masm.emitText(")");
            jsLTools.genResolvedVarDeclPostfix(null);
        }
    }

    /**
     * Lowers an array of packed integer constants to a base64 representation.
     * <p>
     * Decoding is done at startup. Can reduce size of array initializers by 3x
     *
     * @param len Number of 'bits' sized values that are packed in the ints array
     */
    protected void emitBase64ByteArrayInitializer(IdentityNode node, String hubVarName, int len, int bits, int[] ints, int hashCode) {
        int numBytes = (len * bits + 7) / 8;
        byte[] bytes = new byte[numBytes];

        // We need to repack the integer array into a byte array
        for (int i = 0; i < ints.length; i++) {
            int compound = ints[i];
            for (int j = 0; j < 4; j++) {
                int idx = i * 4 + j;

                if (idx >= numBytes) {
                    break;
                }

                bytes[idx] = (byte) ((compound >> (j * 8)) & 0xff);
            }
        }
        String b64 = Base64.getEncoder().encodeToString(bytes);

        masm.emitNewLine();
        Runtime.PackedArrayInitFunWitHubAndBase64Value.emitCall(jsLTools, JSEmitter.of(node), Emitter.of(len), Emitter.of(bits), Emitter.stringLiteral(b64), Emitter.of(hubVarName),
                        Emitter.of(hashCode));
        jsLTools.genResolvedVarDeclPostfix(null);
    }

    protected IEmitter valueEmitter(ObjectDefinition odef) {
        if (odef instanceof ValueType valueType) {
            return JSEmitter.of(valueType);
        } else if (odef instanceof MethodPointerType methodPointerType) {
            return JSEmitter.of(methodPointerType);
        } else {
            JavaConstant constant = odef.getConstant();

            if (constant.isNull()) {
                return Emitter.ofNull();
            }

            return JSEmitter.of(identityMapping.getIdentityNode(constant));
        }
    }

    /**
     * Converts the given {@link JavaKind} to an integer that can be lowered into JS.
     */
    public static int getKindNum(JavaKind kind) {
        return kind.ordinal();
    }

    public static int getKindNum(HostedField f) {
        return getKindNum(f.getStorageKind());
    }
}
