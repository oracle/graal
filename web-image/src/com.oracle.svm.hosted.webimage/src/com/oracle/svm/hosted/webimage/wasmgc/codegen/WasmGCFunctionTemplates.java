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

package com.oracle.svm.hosted.webimage.wasmgc.codegen;

import java.util.ArrayList;
import java.util.List;

import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.Hybrid;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.config.DynamicHubLayout;
import com.oracle.svm.hosted.config.HybridLayout;
import com.oracle.svm.hosted.meta.HostedClass;
import com.oracle.svm.hosted.meta.HostedField;
import com.oracle.svm.hosted.meta.HostedInstanceClass;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.meta.HostedType;
import com.oracle.svm.hosted.webimage.name.WebImageNamingConvention;
import com.oracle.svm.hosted.webimage.wasm.ast.Function;
import com.oracle.svm.hosted.webimage.wasm.ast.FunctionTypeDescriptor;
import com.oracle.svm.hosted.webimage.wasm.ast.Instruction;
import com.oracle.svm.hosted.webimage.wasm.ast.Instruction.Binary;
import com.oracle.svm.hosted.webimage.wasm.ast.Instruction.Unary;
import com.oracle.svm.hosted.webimage.wasm.ast.Instructions;
import com.oracle.svm.hosted.webimage.wasm.ast.TypeUse;
import com.oracle.svm.hosted.webimage.wasm.ast.id.WasmId;
import com.oracle.svm.hosted.webimage.wasm.ast.id.WasmIdFactory;
import com.oracle.svm.hosted.webimage.wasm.ast.id.WebImageWasmIds;
import com.oracle.svm.hosted.webimage.wasm.codegen.WasmFunctionTemplate;
import com.oracle.svm.hosted.webimage.wasm.codegen.WebImageWasmBackend;
import com.oracle.svm.hosted.webimage.wasmgc.ast.id.GCKnownIds;
import com.oracle.svm.hosted.webimage.wasmgc.ast.id.WebImageWasmGCIds;
import com.oracle.svm.hosted.webimage.wasmgc.types.WasmGCUtil;
import com.oracle.svm.hosted.webimage.wasmgc.types.WasmRefType;
import com.oracle.svm.webimage.wasm.types.WasmPrimitiveType;
import com.oracle.svm.webimage.wasm.types.WasmUtil;
import com.oracle.svm.webimage.wasm.types.WasmValType;
import com.oracle.svm.webimage.wasmgc.WasmExtern;

import jdk.graal.compiler.nodes.extended.AbstractBoxingNode;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Function templates for the WasmGC backend.
 *
 * @see WasmFunctionTemplate
 */
public class WasmGCFunctionTemplates {

    private static boolean isValidArrayComponentKind(JavaKind kind) {
        return switch (kind) {
            case Boolean, Byte, Short, Char, Int, Float, Long, Double, Object -> true;
            default -> false;
        };
    }

    /**
     * Function that converts a Java object to an {@code externref}.
     * <p>
     * If the object is a {@link WasmExtern}, it will unwrap the inner {@code externref}.
     * <p>
     * The object is converted using {@code extern.convert_any}.
     * <p>
     * Produces approximately the following pseudo code:
     *
     * <pre>{@code
     * if (o instanceof WasmExtern extern) {
     *     return extern.embedderField;
     * } else {
     *     return extern.convert_any(o);
     * }
     * }</pre>
     *
     * @see WrapExtern
     */
    public static class ToExtern extends WasmFunctionTemplate.Singleton {
        public ToExtern(WasmIdFactory idFactory) {
            super(idFactory, true);
        }

        @Override
        protected String getFunctionName() {
            return "extern.unwrap";
        }

        @Override
        protected Function createFunction(Context ctxt) {
            WebImageWasmGCProviders providers = (WebImageWasmGCProviders) ctxt.getProviders();
            ResolvedJavaType wasmExternType = providers.getMetaAccess().lookupJavaType(WasmExtern.class);
            WasmId.StructType wasmExternId = idFactory.newJavaStruct(wasmExternType);
            WasmRefType wasmExternRef = wasmExternId.asNonNull();

            Function f = ctxt.createFunction(TypeUse.forUnary(WasmRefType.EXTERNREF, providers.util().getJavaLangObjectType()),
                            "Converts a Java object to an externref. WasmExtern instances are simply unwrapped.");

            WasmId.Local objectParam = f.getParam(0);

            var ifInstr = new Instruction.If(null, new Instruction.RefTest(objectParam.getter(), wasmExternRef));

            Instructions thenBranch = ifInstr.thenInstructions;
            Instructions elseBranch = ifInstr.elseInstructions;

            f.getInstructions().add(ifInstr);
            thenBranch.add(new Instruction.Return(
                            new Instruction.StructGet(wasmExternId, providers.knownIds().embedderField, WasmUtil.Extension.None, new Instruction.RefCast(objectParam.getter(), wasmExternRef))));
            elseBranch.add(new Instruction.Return(Instruction.AnyExternConversion.toExtern(objectParam.getter())));

            return f;
        }
    }

    /**
     * Function that takes an {@code externref} and produces a corresponding Java object.
     * <p>
     * If the externref is {@code null}, will return {@code null}. If the externref actually refers
     * to a Java object, the {@code externref} is cast to the Java object
     * ({@code any.convert_extern} followed by a downcast). Otherwise, the {@code externref} truly
     * is a host object and it's wrapped in an {@link WasmExtern} instance.
     *
     * @see ToExtern
     */
    public static class WrapExtern extends WasmFunctionTemplate.Singleton {

        public WrapExtern(WasmIdFactory idFactory) {
            super(idFactory);
        }

        @Override
        protected String getFunctionName() {
            return "extern.wrap";
        }

        @Override
        protected Function createFunction(Context ctxt) {
            WebImageWasmGCProviders providers = (WebImageWasmGCProviders) ctxt.getProviders();
            WasmGCUtil util = providers.util();
            ResolvedJavaType wasmExternType = providers.getMetaAccess().lookupJavaType(WasmExtern.class);
            WasmId.StructType wasmExternId = idFactory.newJavaStruct(wasmExternType);
            WasmRefType javaLangObjectType = util.getJavaLangObjectType();

            JavaConstant hubConstant = providers.getConstantReflection().asJavaClass(providers.getMetaAccess().lookupJavaType(WasmExtern.class));

            Function f = ctxt.createFunction(TypeUse.forUnary(javaLangObjectType, WasmRefType.EXTERNREF), "Wrap externref in Java object");

            WasmId.Local externRefParam = f.getParam(0);

            // Return null if the externref is null
            Instruction.If nullCheck = new Instruction.If(null, Unary.Op.RefIsNull.create(externRefParam.getter()));
            nullCheck.thenInstructions.add(new Instruction.Return(new Instruction.RefNull(javaLangObjectType)));
            f.getInstructions().add(nullCheck);

            // Return the Java object if the externref is actually an externalized Java object
            Instruction.If javaObjectTest = new Instruction.If(null, new Instruction.RefTest(Instruction.AnyExternConversion.toAny(externRefParam.getter()), javaLangObjectType));
            javaObjectTest.thenInstructions.add(new Instruction.Return(new Instruction.RefCast(Instruction.AnyExternConversion.toAny(externRefParam.getter()), javaLangObjectType)));
            f.getInstructions().add(javaObjectTest);

            // Finally, if this is truly a non-Java object, wrap it in an WasmExtern
            Instruction identityHashCode = Instruction.Const.forInt(0);
            f.getInstructions().add(new Instruction.StructNew(wasmExternId, ctxt.getCodeGenTool().getConstantRelocation(hubConstant), identityHashCode, externRefParam.getter()));

            return f;
        }
    }

    /**
     * Function that reads the length of a Java array.
     *
     * @see WasmGCBuilder#getArrayLength(Instruction)
     */
    public static class ArrayLength extends WasmFunctionTemplate.Singleton {

        public ArrayLength(WasmIdFactory idFactory) {
            super(idFactory, true);
        }

        @Override
        protected String getFunctionName() {
            return "array.length";
        }

        @Override
        protected Function createFunction(Context ctxt) {
            WebImageWasmGCProviders providers = (WebImageWasmGCProviders) ctxt.getProviders();

            WasmValType arrayStructType = providers.knownIds().baseArrayType.asNullable();

            Function f = ctxt.createFunction(TypeUse.forUnary(WasmPrimitiveType.i32, arrayStructType), "Get array length of an array struct");

            WasmId.Local arrayParam = f.getParam(0);

            f.getInstructions().add(providers.builder().getArrayLength(arrayParam.getter()));

            return f;
        }
    }

    /**
     * Function that reads an element of a Java array.
     * <p>
     * The template is parameterized on the array's component kind.
     *
     * @see WasmGCBuilder#getArrayElement(Instruction, Instruction, JavaKind)
     */
    public static class ArrayElementLoad extends WasmFunctionTemplate<JavaKind> {

        public ArrayElementLoad(WasmIdFactory idFactory) {
            super(idFactory, true);
        }

        @Override
        protected boolean isValidParameter(JavaKind parameter) {
            return isValidArrayComponentKind(parameter);
        }

        @Override
        protected String getFunctionName(JavaKind kind) {
            return "array." + kind.name() + ".read";
        }

        @Override
        protected Function createFunction(Context ctxt) {
            JavaKind componentKind = ctxt.getParameter();
            WebImageWasmGCProviders providers = (WebImageWasmGCProviders) ctxt.getProviders();

            WasmValType arrayStructType = idFactory.newJavaArrayStruct(componentKind).asNullable();
            WasmValType elementType = providers.util().mapType(componentKind);

            Function f = ctxt.createFunction(TypeUse.forBinary(elementType, arrayStructType, WasmPrimitiveType.i32),
                            "Loads an array element from an array struct with component kind " + componentKind);

            WasmId.Local arrayParam = f.getParam(0);
            WasmId.Local indexParam = f.getParam(1);

            f.getInstructions().add(providers.builder().getArrayElement(arrayParam.getter(), indexParam.getter(), componentKind));

            return f;
        }
    }

    /**
     * Function that sets an element of a Java array.
     * <p>
     * The template is parameterized on the array's component kind.
     *
     * @apiNote Be careful when using these functions, they don't perform store type checks. It's
     *          easy to store incompatible types into arrays this way.
     */
    public static class ArrayElementStore extends WasmFunctionTemplate<JavaKind> {

        public ArrayElementStore(WasmIdFactory idFactory) {
            super(idFactory, true);
        }

        @Override
        protected boolean isValidParameter(JavaKind parameter) {
            return isValidArrayComponentKind(parameter);
        }

        @Override
        protected String getFunctionName(JavaKind kind) {
            return "array." + kind.name() + ".write";
        }

        @Override
        protected Function createFunction(Context ctxt) {
            JavaKind componentKind = ctxt.getParameter();
            WebImageWasmGCProviders providers = (WebImageWasmGCProviders) ctxt.getProviders();

            WasmValType arrayStructType = idFactory.newJavaArrayStruct(componentKind).asNullable();
            WasmValType elementType = providers.util().mapType(componentKind);

            Function f = ctxt.createFunction(TypeUse.withoutResult(arrayStructType, WasmPrimitiveType.i32, elementType),
                            "Stores an array element to an array struct with component kind " + componentKind);

            WasmId.Local arrayParam = f.getParam(0);
            WasmId.Local indexParam = f.getParam(1);
            WasmId.Local valueParam = f.getParam(2);

            f.getInstructions().add(providers.builder().setArrayElement(arrayParam.getter(), indexParam.getter(), valueParam.getter(), componentKind));

            return f;
        }
    }

    /**
     * Function that allocates a Java array instance with a given length.
     * <p>
     * The template is parameterized on the array's component type.
     */
    public static class ArrayCreate extends WasmFunctionTemplate<Class<?>> {

        public ArrayCreate(WasmIdFactory idFactory) {
            super(idFactory);
        }

        @Override
        protected String getFunctionName(Class<?> componentType) {
            return "array." + componentType.getCanonicalName() + ".create";
        }

        @Override
        protected Function createFunction(Context ctxt) {
            Class<?> componentType = ctxt.getParameter();
            WebImageWasmGCProviders providers = (WebImageWasmGCProviders) ctxt.getProviders();

            ResolvedJavaType resolvedComponentType = providers.getMetaAccess().lookupJavaType(componentType);
            JavaKind componentKind = resolvedComponentType.getJavaKind();

            WasmValType arrayStructType = idFactory.newJavaArrayStruct(componentKind).asNullable();

            JavaConstant hubConstant = providers.getConstantReflection().asJavaClass(resolvedComponentType.getArrayClass());
            assert !hubConstant.isNull() : hubConstant;

            Function f = ctxt.createFunction(TypeUse.forUnary(arrayStructType, WasmPrimitiveType.i32),
                            "Creates a Java array of type " + componentType + "[]");

            WasmId.Local lengthParam = f.getParam(0);

            f.getInstructions().add(providers.builder().createNewArray(componentKind, ctxt.getCodeGenTool().getConstantRelocation(hubConstant), lengthParam.getter()));
            return f;
        }
    }

    /**
     * Creates an uninitialized Java (non-array) object instance and sets the dynamic hub field.
     *
     * Generates approximately:
     *
     * <pre>{@code
     * (local.tee $l (struct.new_default $structType))
     * (struct.set $structType $hubField (nop) (relocation $CONST_HUB))
     * (return local.get $l)
     * }</pre>
     *
     * The {@code relocation} is later replaced with load of the {@link DynamicHub} instance.
     */
    public static class InstanceCreate extends WasmFunctionTemplate<Class<?>> {

        public InstanceCreate(WasmIdFactory idFactory) {
            super(idFactory);
        }

        @Override
        protected boolean isValidParameter(Class<?> parameter) {
            // Only non-array types are valid
            return !parameter.isArray();
        }

        @Override
        protected String getFunctionName(Class<?> type) {
            // TODO GR-41720 Ensure this is a valid Wasm name
            return "struct." + type.getName() + ".create";
        }

        @Override
        protected Function createFunction(Context ctxt) {
            Class<?> key = ctxt.getParameter();
            WebImageWasmGCProviders providers = (WebImageWasmGCProviders) ctxt.getProviders();
            MetaAccessProvider metaAccess = providers.getMetaAccess();

            ResolvedJavaType type = metaAccess.lookupJavaType(key);
            WasmId.StructType structType = idFactory.newJavaStruct(type);
            WasmValType structValType = structType.asNonNull();

            /*
             * The type of this function has to be a subtype of the base type represented by
             * newInstanceFieldType because we want to store references to these functions in a
             * field of the base type
             */
            WebImageWasmIds.DescriptorFuncType funcType = idFactory.newFuncType(new FunctionTypeDescriptor(providers.knownIds().newInstanceFieldType, true, TypeUse.withResult(structValType)));
            Function f = ctxt.createFunction(funcType, "Creates an instance of type " + key);

            WasmId.Local structVar = idFactory.newTemporaryVariable(structValType);

            JavaConstant hubConstant = providers.getConstantReflection().asJavaClass(type);

            Instructions instructions = f.getInstructions();

            // Allocate struct and push value to stack
            instructions.add(structVar.tee(new Instruction.StructNew(structType)));

            // Fill hub field with hub constant. The struct reference is loaded from the stack.
            instructions.add(new Instruction.StructSet(structType, providers.knownIds().hubField, new Instruction.Nop(), ctxt.getCodeGenTool().getConstantRelocation(hubConstant)));

            // Return struct in variable
            instructions.add(new Instruction.Return(structVar.getter()));

            return f;
        }
    }

    /**
     * Allocates a boxed primitive.
     * <p>
     * Generates approximately:
     *
     * <pre>{@code
     * (func $func.box.<kind> (param $primitiveValue <valtype>) (result (ref $<boxed>))
     *   (local $boxed (ref $<boxed>))
     *   (local.set $boxed
     *     (; Allocates the struct. See {@link InstanceCreate} ;)
     *     (call $func.struct.<boxed>.create)
     *   )
     *   (struct.set $<boxed> $value
     *     (local.get $boxed)
     *     (local.get $primitiveValue)
     *   )
     *   (return
     *     (local.get $boxed)
     *   )
     * )
     * }</pre>
     */
    public static class AllocatingBox extends WasmFunctionTemplate<JavaKind> {
        public AllocatingBox(WasmIdFactory idFactory) {
            super(idFactory);
        }

        @Override
        protected boolean isValidParameter(JavaKind javaKind) {
            return javaKind.isPrimitive();
        }

        @Override
        protected String getFunctionName(JavaKind javaKind) {
            return "box." + javaKind.name();
        }

        @Override
        protected Function createFunction(Context ctxt) {
            JavaKind boxedKind = ctxt.getParameter();
            WebImageWasmGCProviders providers = (WebImageWasmGCProviders) ctxt.getProviders();
            MetaAccessProvider metaAccess = providers.getMetaAccess();
            Class<?> boxedJavaClass = boxedKind.toBoxedJavaClass();
            ResolvedJavaType boxing = metaAccess.lookupJavaType(boxedJavaClass);
            WebImageWasmGCIds.JavaStruct boxedStruct = idFactory.newJavaStruct(boxing);
            WasmRefType returnValue = boxedStruct.asNonNull();

            ResolvedJavaField valueField = AbstractBoxingNode.getValueField(boxing);

            Function f = ctxt.createFunction(TypeUse.forUnary(returnValue, providers.util().mapType(boxedKind)), "Creates a boxed " + boxedKind.name());
            Instructions instructions = f.getInstructions();

            WasmId.Local valueParam = f.getParam(0);
            WasmId.Local boxed = idFactory.newTemporaryVariable(returnValue);

            instructions.add(boxed.setter(new Instruction.Call(providers.knownIds().instanceCreateTemplate.requestFunctionId(boxedJavaClass))));
            instructions.add(new Instruction.StructSet(boxedStruct, idFactory.newJavaField(valueField), boxed.getter(), valueParam.getter()));
            instructions.add(new Instruction.Return(boxed.getter()));

            return f;
        }
    }

    /**
     * Template to look up the function table index for some vtable index.
     * <p>
     * Generates:
     *
     * <pre>{@code
     * (func $func.get_funtable_idx (param $receiver (ref null $_Object)) (param $vtableIndex i32) (result i32)
     *   (i32.wrap_i64 (array.get $vtable
     *     (struct.get $hub $vtable
     *       (struct.get $Object $hub (local.get $vtableIndex))
     *     )
     *     (local.get $receiver)
     *   ))
     * )
     * }</pre>
     */
    public static class GetFunctionIndex extends WasmFunctionTemplate.Singleton {

        public GetFunctionIndex(WasmIdFactory idFactory) {
            super(idFactory);
        }

        @Override
        protected String getFunctionName() {
            return "get_funtable_idx";
        }

        @Override
        protected Function createFunction(Context ctxt) {
            WebImageWasmGCProviders providers = (WebImageWasmGCProviders) ctxt.getProviders();
            GCKnownIds knownIds = providers.knownIds();

            WasmValType objectValType = providers.util().getJavaLangObjectType();

            WasmId.StructType hubStructType = providers.util().getHubObjectId();

            Function f = ctxt.createFunction(TypeUse.withResult(WasmPrimitiveType.i32, objectValType, WasmPrimitiveType.i32),
                            "Loads the global function table index from the Object's vtable");

            WasmId.Local objectParam = f.getParam(0);
            WasmId.Local vtableIndexParam = f.getParam(1);

            Instruction hub = providers.builder.getHub(objectParam.getter());
            Instruction.StructGet vtable = new Instruction.StructGet(hubStructType, knownIds.vtableField, WasmUtil.Extension.None, hub);

            f.getInstructions().add(Unary.Op.I32Wrap64.create(new Instruction.ArrayGet(knownIds.vtableFieldType, WasmUtil.Extension.None, vtable, vtableIndexParam.getter())));

            return f;
        }
    }

    /**
     * Bridge method outlining an indirect call to some base method's implementation.
     * <p>
     * The template is parameterized by the base method. Virtual and interface calls can be
     * expressed as a call to this template.
     * <p>
     * Generates:
     *
     * <pre>{@code
     * (func $func.bridge.<method> (param $receiver (ref null $_Object)) (...<other params>) (result i32)
     *   (call_indirect $<function table> (type $<function type>)
     *     (local.get $receiver)
     *     (call $func.get_funtable_idx
     *       (local.get $receiver)
     *       (i32.const <method vtable index>)
     *     ) (; See {@link GetFunctionIndex} ;)
     *   )
     * )
     * }</pre>
     */
    public static class IndirectCallBridge extends WasmFunctionTemplate<HostedMethod> {

        public IndirectCallBridge(WasmIdFactory idFactory) {
            super(idFactory);
        }

        @Override
        protected boolean isValidParameter(HostedMethod hostedMethod) {
            return hostedMethod.getImplementations().length > 1;
        }

        @Override
        protected String getFunctionName(HostedMethod hostedMethod) {
            // TODO GR-41720 Ensure this is a valid Wasm name
            return "bridge." + WebImageNamingConvention.getInstance().identForType(hostedMethod.getDeclaringClass()) + "." + WebImageNamingConvention.getInstance().identForMethod(hostedMethod);
        }

        @Override
        protected Function createFunction(Context ctxt) {
            HostedMethod hostedMethod = ctxt.getParameter();
            WebImageWasmGCProviders providers = (WebImageWasmGCProviders) ctxt.getProviders();
            GCKnownIds knownIds = providers.knownIds();

            ResolvedJavaType[] paramTypes = WebImageWasmBackend.constructParamTypes(providers, hostedMethod);
            ResolvedJavaType returnType = WebImageWasmBackend.constructReturnType(hostedMethod);

            TypeUse typeUse = WebImageWasmBackend.signatureToTypeUse(providers, paramTypes, returnType);

            WasmId.FuncType funcType = providers.util().functionIdForMethod(typeUse);

            Function f = ctxt.createFunction(typeUse, "Bridge for indirect calls to " + hostedMethod.format("%H.%n(%P)"));

            WasmId.Local receiverParam = f.getParam(0);
            Instructions params = new Instructions();

            for (WasmId.Local param : f.getParams()) {
                params.add(param.getter());
            }

            Instruction.Call functionIdx = new Instruction.Call(knownIds.getFunctionIndexTemplate.requestFunctionId(), receiverParam.getter(), Instruction.Const.forInt(hostedMethod.getVTableIndex()));

            f.getInstructions().add(new Instruction.CallIndirect(knownIds.functionTable, functionIdx, funcType, typeUse, params));

            return f;
        }
    }

    /**
     * Fills fields in an image heap object.
     * <p>
     * The function sets all fields declared in the parameterized class and calls this template for
     * the next superclass with its own fields (see
     * {@link WasmGCHeapWriter#getFirstClassWithFields(HostedClass)}).
     * <p>
     * The generated functions take the following arguments:
     * <ul>
     * <li>{@code i32}: Index of this heap object</li>
     * <li>{@code i32}: Index of the dynamic hub</li>
     * <li>{@code i32}: Identity hash code</li>
     * <li>{@code array(i32)} (DynamicHub only): access dispatch array</li>
     * <li>{@code array(i64)} (DynamicHub only): vtable</li>
     * <li>{@code array(i16)} (DynamicHub only): closed world type check slots</li>
     * <li>{@code func (result (ref $_Object))} (DynamicHub only): function pointer for the
     * {@link GCKnownIds#newInstanceField} field</li>
     * <li>{@code func (param (ref null $_Object)) (result (ref $_Object))} (DynamicHub only):
     * function pointer for the {@link GCKnownIds#cloneField} field</li>
     * <li>...: One argument per field, first all fields declared in this class, followed by all
     * fields declared in superclasses. References are represented as i32 indices into the object
     * table</li>
     * </ul>
     *
     * <pre>{@code
     * (func $func.heap.init.object.<type> (param $objectIndex i32) (param $hubIndex i32) (param $hashCode i32) (param $field1 i32) (param $field2 i32))
     *   (local $instance (ref $<type>))
     *   (; Load object from table ;)
     *   (local.set $instance
     *     (ref.cast (ref $<type>)
     *       (table.get $table0
     *         (local.get $objectIndex)
     *       )
     *     )
     *   )
     *
     *   (; Call function for supertype ;)
     *   (call $func.heap.init.object.<supertype>
     *     (local.get $objectIndex)
     *     (local.get $hubIndex)
     *     (local.get $hashCode)
     *     (local.get $field2)
     *   )
     *
     *   (; Set fields declared in this class ;)
     *   (struct.set $<type> $<field1>
     *     (local.get $instance)
     *     (local.get $field1)
     *   )
     * )
     * }</pre>
     *
     * For {@link DynamicHub}, the function has additional arguments, which it uses to set its
     * custom fields: access dispatch array (see {@link WasmGCUnsafeTemplates}), vtable fields, type
     * check slots, new instance function pointer, and clone function pointer.
     * <p>
     * For {@link java.lang.Object}, no super call is inserted. Instead, the hub and identity hash
     * code field are set from the arguments.
     */
    public static class FillHeapObject extends WasmFunctionTemplate<HostedInstanceClass> {

        public FillHeapObject(WasmIdFactory idFactory) {
            super(idFactory, true);
        }

        @Override
        protected boolean isValidParameter(HostedInstanceClass instanceClass) {
            return instanceClass.isJavaLangObject() || !WasmGCHeapWriter.getOwnInstanceFields(instanceClass).isEmpty();
        }

        @Override
        protected String getFunctionName(HostedInstanceClass hostedType) {
            return "heap.init.object." + WebImageNamingConvention.getInstance().identForType(hostedType);
        }

        @Override
        protected Function createFunction(Context ctxt) {
            HostedInstanceClass clazz = ctxt.getParameter();
            WebImageWasmGCProviders providers = (WebImageWasmGCProviders) ctxt.getProviders();
            WasmGCUtil util = providers.util();

            WasmId.StructType typeId = providers.idFactory().newJavaStruct(clazz);
            DynamicHubLayout dynamicHubLayout = DynamicHubLayout.singleton();

            List<HostedField> ownFields = WasmGCHeapWriter.getOwnInstanceFields(clazz);
            List<HostedField> notOwnFields = WasmGCHeapWriter.getInstanceFields(clazz.getSuperclass());
            List<HostedField> instanceFields = new ArrayList<>(ownFields.size() + notOwnFields.size());
            instanceFields.addAll(ownFields);
            instanceFields.addAll(notOwnFields);

            List<WasmValType> paramTypes = new ArrayList<>();
            paramTypes.add(WasmPrimitiveType.i32);
            paramTypes.add(WasmPrimitiveType.i32);
            paramTypes.add(WasmPrimitiveType.i32);

            if (dynamicHubLayout.isDynamicHub(clazz)) {
                // Access dispatch array
                paramTypes.add(providers.knownIds().accessDispatchFieldType.asNullable());
                // vtable
                paramTypes.add(providers.knownIds().vtableFieldType.asNullable());
                // Type check slots
                paramTypes.add(providers.knownIds().typeCheckSlotsFieldType.asNullable());
                paramTypes.add(providers.knownIds().newInstanceFieldType.asNullable());
                paramTypes.add(providers.knownIds().cloneFieldType.asNullable());
            } else if (HybridLayout.isHybrid(clazz)) {
                throw VMError.shouldNotReachHere("Found unsupported @" + Hybrid.class.getSimpleName() + " image heap object of type: " + clazz);
            }

            // The value for the first Java field is passed with the parameter at this index.
            int numNonFieldParams = paramTypes.size();

            for (HostedField field : instanceFields) {
                HostedType fieldType = (HostedType) util.canonicalizeJavaType(field.getType());
                if (fieldType.isPrimitive()) {
                    paramTypes.add(util.typeForJavaType(fieldType));
                } else {
                    paramTypes.add(WasmPrimitiveType.i32);
                }
            }

            Function f = ctxt.createFunction(TypeUse.withoutResult(paramTypes.toArray(new WasmValType[0])), "Fill image heap object of type " + clazz.toJavaName());
            Instructions instructions = f.getInstructions();

            WasmId.Local objectIndexParam = f.getParam(0);
            WasmId.Local hubIndexParam = f.getParam(1);
            WasmId.Local hashCodeParam = f.getParam(2);

            WasmId.Local objectRef = idFactory.newTemporaryVariable(typeId.asNonNull());

            instructions.add(objectRef.setter(getObject(providers.knownIds(), objectIndexParam.getter(), typeId.asNonNull())));

            if (dynamicHubLayout.isDynamicHub(clazz)) {
                WasmId.Local accessDispatchParam = f.getParam(3);
                WasmId.Local vtableParam = f.getParam(4);
                WasmId.Local typeCheckSlotsParam = f.getParam(5);
                WasmId.Local newInstanceFunctionParam = f.getParam(6);
                WasmId.Local cloneFunctionParam = f.getParam(7);

                instructions.add(new Instruction.StructSet(typeId, providers.knownIds().accessDispatchField, objectRef.getter(), accessDispatchParam.getter()));
                instructions.add(new Instruction.StructSet(typeId, providers.knownIds().vtableField, objectRef.getter(), vtableParam.getter()));
                instructions.add(new Instruction.StructSet(typeId, providers.knownIds().typeCheckSlotsField, objectRef.getter(), typeCheckSlotsParam.getter()));
                instructions.add(new Instruction.StructSet(typeId, providers.knownIds().newInstanceField, objectRef.getter(), newInstanceFunctionParam.getter()));
                instructions.add(new Instruction.StructSet(typeId, providers.knownIds().cloneField, objectRef.getter(), cloneFunctionParam.getter()));
            }

            if (clazz.isJavaLangObject()) {
                instructions.add(new Instruction.StructSet(typeId, providers.knownIds().hubField, objectRef.getter(), getHub(providers, hubIndexParam.getter())));
                instructions.add(new Instruction.StructSet(typeId, providers.knownIds().identityHashCodeField, objectRef.getter(), hashCodeParam.getter()));
            } else {
                Instructions superCallArgs = new Instructions();
                superCallArgs.add(objectIndexParam.getter());
                superCallArgs.add(hubIndexParam.getter());
                superCallArgs.add(hashCodeParam.getter());

                for (int i = ownFields.size(); i < instanceFields.size(); i++) {
                    WasmId.Local argParam = f.getParam(numNonFieldParams + i);
                    superCallArgs.add(argParam.getter().setComment(instanceFields.get(i).format("%T %h.%n")));
                }

                HostedInstanceClass nextSuperClass = WasmGCHeapWriter.getFirstClassWithFields(clazz.getSuperclass());
                // "super" call to the template for the superclass
                instructions.add(new Instruction.Call(providers.knownIds().fillHeapObjectTemplate.requestFunctionId(nextSuperClass), superCallArgs));
            }

            int i = 0;
            for (HostedField field : ownFields) {
                WasmId.Local argParam = f.getParam(numNonFieldParams + i);
                HostedType fieldType = (HostedType) util.canonicalizeJavaType(field.getType());

                Instruction value;
                if (fieldType.isPrimitive()) {
                    value = argParam.getter();
                } else {
                    value = getObject(providers.knownIds(), argParam.getter(), (WasmRefType) util.typeForJavaType(fieldType));
                }

                instructions.add(new Instruction.StructSet(typeId, idFactory.newJavaField(field), objectRef.getter(), value));

                i++;
            }

            return f;
        }

        /**
         * Creates an instruction to load a {@link DynamicHub} from the image heap table.
         */
        public static Instruction getHub(WebImageWasmGCProviders providers, Instruction index) {
            return getObject(providers.knownIds(), index, providers.idFactory().newJavaStruct(providers.getMetaAccess().lookupJavaType(Class.class)).asNonNull());
        }

        public static Instruction getObject(GCKnownIds knownIds, Instruction index) {
            return getObject(knownIds, index, null);
        }

        /**
         * Creates an instruction to load an object from the image heap table at the given index.
         *
         * @param targetType If non-null, the loaded object is cast to this type.
         */
        public static Instruction getObject(GCKnownIds knownIds, Instruction index, WasmRefType targetType) {
            Instruction result = new Instruction.TableGet(knownIds.imageHeapObjectTable, index);
            if (targetType != null) {
                result = new Instruction.RefCast(result, targetType);
            }
            return result;
        }
    }

    /**
     * Fills an image heap array with data.
     * <p>
     * The hash code is already set during instantiation, this function only sets the dynamic hub
     * field and the array contents.
     * <p>
     * The generated functions take the following arguments:
     *
     * <ul>
     * <li>{@code i32}: Index of this heap object</li>
     * <li>{@code i32}: Index of the dynamic hub</li>
     * <li>{@code i32} (primitive arrays only): Offset in memory. Used for array.init_data</li>
     * <li>{@code array(i32)} (object arrays only): Array of indices for all objects in this
     * array.</li>
     * </ul>
     *
     * Indices are into the image heap object table that holds all image heap object instances
     * during initialization. Primitive arrays are initialized with data from a data segment while
     * for object arrays the template accepts an array of indices into the image heap table.
     * <p>
     * For primitive arrays generates:
     *
     * <pre>{@code
     * (func $func.heap.init.array.<component kind> (param $objectIndex i32) (param $hubIndex i32) (param $offset i32)
     *   (local $arrayRef (ref $struct.array.<component kind>))
     *   (local $innerArray (ref $array.<component kind>))
     *   (local $arrayLength i32)
     *   (; Load object from table ;)
     *   (local.set $arrayRef
     *     (ref.cast (ref $struct.array.<component kind>)
     *       (table.get $table0 (local.get $objectIndex))
     *     )
     *   )
     *   (local.set $innerArray
     *     (struct.get $struct.array.<component kind> $field.inner (local.get $arrayRef))
     *   )
     *   (local.set $arrayLength
     *     (array.len (local.get $innerArray))
     *   )
     *
     *   (; Load and set dynamic hub field ;)
     *   (struct.set $struct.array.<component kind> $field.dynamicHub
     *     (local.get $arrayRef)
     *     (ref.cast (ref $_Class)
     *       (table.get $table0 (local.get $hubIndex))
     *     )
     *   )
     *
     *   (; Fill array data from data segment ;)
     *   (array.init_data $array.<component kind> $data.imageHeap
     *     (local.get $innerArray)
     *     (i32.const 0x0)
     *     (local.get $offset)
     *     (local.get $arrayLength)
     *   )
     * )
     * }</pre>
     *
     * For the object array, the generated function only differs in the last part, filling the array
     * elements. Here, it has to loop through the object index array and for each index load the
     * object from the table and store it in the image heap array:
     *
     * <pre>{@code
     * (func $func.heap.init.array.Object (param $objectIndex i32) (param $hubIndex i32) (param $objectIndices (ref $array.int))
     *   (local $arrayRef (ref $struct.array.Object))
     *   (local $innerArray (ref $array.Object))
     *   (local $arrayLength i32)
     *   (; Loop index ;)
     *   (local $idx i32)
     *
     *   (; Loading the array from the table, loading the array length, and
     *      setting the hub field are the same as for primitive arrays. ;)
     *
     *   (loop $label.loop
     *     (if
     *       (i32.lt_s (local.get $idx) (local.get $arrayLength))
     *       (then
     *         (; Load object from table and store it at index $idx in target array ;)
     *         (array.set $array.Object
     *           (local.get $innerArray)
     *           (local.get $idx)
     *           (table.get $table0
     *             (array.get $array.int
     *               (local.get $objectIndices)
     *               (local.get $idx)
     *             )
     *           )
     *         )
     *         (local.set $idx
     *           (i32.add (local.get $idx) (i32.const 0x1))
     *         )
     *         (br $label.loop)
     *       )
     *     )
     *   )
     * )
     * }</pre>
     */
    public static class FillHeapArray extends WasmFunctionTemplate<JavaKind> {

        public FillHeapArray(WasmIdFactory idFactory) {
            super(idFactory, true);
        }

        @Override
        protected boolean isValidParameter(JavaKind javaKind) {
            return javaKind.getSlotCount() > 0;
        }

        @Override
        protected String getFunctionName(JavaKind javaKind) {
            return "heap.init.array." + javaKind;
        }

        @Override
        protected Function createFunction(Context ctxt) {
            JavaKind componentKind = ctxt.getParameter();
            WebImageWasmGCProviders providers = (WebImageWasmGCProviders) ctxt.getProviders();
            GCKnownIds knownIds = providers.knownIds();

            WasmId.StructType arrayStructType = knownIds.arrayStructTypes.get(componentKind);
            WasmId.ArrayType innerArrayType = knownIds.innerArrayTypes.get(componentKind);
            WasmId.ArrayType objectIndicesArrayType = knownIds.innerArrayTypes.get(JavaKind.Int);

            boolean isObject = componentKind.isObject();

            List<WasmValType> argTypes = new ArrayList<>();
            argTypes.add(WasmPrimitiveType.i32);
            argTypes.add(WasmPrimitiveType.i32);
            if (isObject) {
                argTypes.add(objectIndicesArrayType.asNonNull());
            } else {
                argTypes.add(WasmPrimitiveType.i32);
            }

            Function f = ctxt.createFunction(TypeUse.withoutResult(argTypes.toArray(new WasmValType[0])), "Fill image heap " + componentKind + " array");
            Instructions instructions = f.getInstructions();

            WasmId.Local objectIndexParam = f.getParam(0);
            WasmId.Local hubIndexParam = f.getParam(1);
            // Holds the array struct
            WasmId.Local arrayRef = idFactory.newTemporaryVariable(arrayStructType.asNonNull());
            WasmId.Local innerArray = idFactory.newTemporaryVariable(innerArrayType.asNonNull());
            WasmId.Local arrayLength = idFactory.newTemporaryVariable(WasmPrimitiveType.i32);

            instructions.add(arrayRef.setter(FillHeapObject.getObject(knownIds, objectIndexParam.getter(), arrayStructType.asNonNull())));
            instructions.add(innerArray.setter(providers.builder().getInnerArray(arrayRef.getter(), componentKind)));
            instructions.add(arrayLength.setter(new Instruction.ArrayLen(innerArray.getter())));

            // Set hub field
            instructions.add(new Instruction.StructSet(arrayStructType, knownIds.hubField, arrayRef.getter(), FillHeapObject.getHub(providers, hubIndexParam.getter())));

            if (isObject) {
                WasmId.Local indicesArrayParam = f.getParam(2);
                WasmId.Local loopIndex = idFactory.newTemporaryVariable(WasmPrimitiveType.i32);
                WasmId.Label loopLabel = idFactory.newInternalLabel("loop");
                Instruction.Loop loop = new Instruction.Loop(loopLabel);

                instructions.add(loop);

                // idx < length
                Instruction.If ifInstr = new Instruction.If(null, Binary.Op.I32LtS.create(loopIndex.getter(), arrayLength.getter()));
                loop.instructions.add(ifInstr);

                Instructions thenInstructions = ifInstr.thenInstructions;
                Instruction objectRef = FillHeapObject.getObject(knownIds, new Instruction.ArrayGet(objectIndicesArrayType,
                                WasmUtil.Extension.None,
                                indicesArrayParam.getter(),
                                loopIndex.getter()));
                // Sets innerArray[idx] with object loaded from object table
                thenInstructions.add(new Instruction.ArraySet(innerArrayType,
                                innerArray.getter(),
                                loopIndex.getter(),
                                objectRef));
                // idx++
                thenInstructions.add(loopIndex.setter(Binary.Op.I32Add.create(loopIndex.getter(), Instruction.Const.forInt(1))));
                // continue
                thenInstructions.add(new Instruction.Break(loopLabel));

            } else {
                WasmId.Local offsetParam = f.getParam(2);
                instructions.add(new Instruction.ArrayInitData(innerArrayType, knownIds.dataSegmentId, innerArray.getter(), Instruction.Const.forInt(0),
                                offsetParam.getter(),
                                arrayLength.getter()));
            }

            return f;
        }
    }

    /**
     * Implements an array copy operation between arrays with the parameterized component kind.
     * <p>
     * Has the same semantics as {@link System#arraycopy(Object, int, Object, int, int)} except that
     * it does not throw any exceptions. This template has to be called after all error conditions
     * have been checked.
     * <p>
     * The generated functions take the same arguments as
     * {@link System#arraycopy(Object, int, Object, int, int)}. The {@code Object} arguments must be
     * arrays with the component kind the template is parameterized with, it is later downcast to
     * that.
     * <p>
     * Generates:
     *
     * <pre>{@code
     * (func $func.arraycopy.<component kind>
     *   (param $fromArray (ref null $_Object))
     *   (param $fromIndex i32)
     *   (param $toArray (ref null $_Object))
     *   (param $toIndex i32)
     *   (param $length i32)
     *   (array.copy $array.<component kind> $array.<component kind>
     *     (struct.get $struct.array.<component kind> $field.inner
     *       (ref.cast (ref null $struct.array.<component kind>)
     *         (local.get $toArray)
     *       )
     *     )
     *     (local.get $toIndex)
     *     (struct.get $struct.array.<component kind> $field.inner
     *       (ref.cast (ref null $struct.array.<component kind>)
     *         (local.get $fromArray)
     *       )
     *     )
     *     (local.get $fromIndex)
     *     (local.get $length)
     *   )
     * )
     * }</pre>
     */
    public static class ArrayCopy extends WasmFunctionTemplate<JavaKind> {

        public ArrayCopy(WasmIdFactory idFactory) {
            super(idFactory);
        }

        @Override
        protected boolean isValidParameter(JavaKind javaKind) {
            return javaKind.getSlotCount() > 0;
        }

        @Override
        protected String getFunctionName(JavaKind javaKind) {
            return "arraycopy." + javaKind;
        }

        @Override
        protected Function createFunction(Context ctxt) {
            JavaKind componentKind = ctxt.getParameter();
            WebImageWasmGCProviders providers = (WebImageWasmGCProviders) ctxt.getProviders();

            WasmValType object = providers.util().getJavaLangObjectType();
            WasmId.ArrayType innerArrayType = providers.knownIds().innerArrayTypes.get(componentKind);
            WasmRefType arrayStruct = providers.knownIds().arrayStructTypes.get(componentKind).asNullable();

            Function f = ctxt.createFunction(TypeUse.withoutResult(object, WasmPrimitiveType.i32, object, WasmPrimitiveType.i32, WasmPrimitiveType.i32), "Array copy for " + componentKind);

            WasmId.Local fromArrayParam = f.getParam(0);
            WasmId.Local fromIndexParam = f.getParam(1);
            WasmId.Local toArrayParam = f.getParam(2);
            WasmId.Local toIndexParam = f.getParam(3);
            WasmId.Local lengthParam = f.getParam(4);

            f.getInstructions().add(new Instruction.ArrayCopy(innerArrayType,
                            innerArrayType,
                            providers.builder.getInnerArray(new Instruction.RefCast(toArrayParam.getter(), arrayStruct), componentKind),
                            toIndexParam.getter(),
                            providers.builder.getInnerArray(new Instruction.RefCast(fromArrayParam.getter(), arrayStruct), componentKind),
                            fromIndexParam.getter(),
                            lengthParam.getter()));

            return f;
        }
    }

    /**
     * Function that throws the Java exception passed as an argument.
     * <p>
     * The Wasm tags used for exceptions don't use subtyping (the exception handling spec does not
     * know about subtyping). For example, a method may throw a {@link IllegalArgumentException},
     * which is not a valid type for the exception tag, it would require an upcast to
     * {@link Throwable}. Instead, any code that throws can call this method without a cast because
     * subtyping works for call parameters.
     */
    public static class Throw extends WasmFunctionTemplate.Singleton {

        public Throw(WasmIdFactory idFactory) {
            super(idFactory);
        }

        @Override
        protected String getFunctionName() {
            return "throw";
        }

        @Override
        protected Function createFunction(Context context) {
            WebImageWasmGCProviders providers = (WebImageWasmGCProviders) context.getProviders();
            Function f = context.createFunction(TypeUse.withoutResult(providers.util().getThrowableType()), "Throws a Java exception");

            WasmId.Local exceptionParam = f.getParam(0);

            f.getInstructions().add(new Instruction.Throw(providers.knownIds().getJavaThrowableTag(), exceptionParam.getter()));

            return f;
        }
    }

    /**
     * Given a Java class, instantiates an uninitialized instance of that type.
     *
     * @see WasmGCBuilder#createUninitialized(Instruction)
     */
    public static class UnsafeCreate extends WasmFunctionTemplate.Singleton {
        public UnsafeCreate(WasmIdFactory idFactory) {
            super(idFactory);
        }

        @Override
        protected String getFunctionName() {
            return "unsafe.create";
        }

        @Override
        protected Function createFunction(Context context) {
            WebImageWasmGCProviders providers = (WebImageWasmGCProviders) context.getProviders();
            WasmGCUtil util = providers.util();
            Function f = context.createFunction(TypeUse.withResult(util.getJavaLangObjectType(), util.getHubObjectType()),
                            "Creates an uninitialized instance of the given class");

            WasmId.Local clazzParam = f.getParam(0);

            f.getInstructions().add(providers.builder().createUninitialized(clazzParam.getter()));
            return f;
        }
    }
}
