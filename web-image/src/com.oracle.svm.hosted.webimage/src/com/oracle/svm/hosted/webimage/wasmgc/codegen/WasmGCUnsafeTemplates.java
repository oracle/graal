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

import java.util.Arrays;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import com.oracle.svm.core.graal.meta.KnownOffsets;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.snippets.SnippetRuntime;
import com.oracle.svm.hosted.meta.HostedField;
import com.oracle.svm.hosted.webimage.name.WebImageNamingConvention;
import com.oracle.svm.hosted.webimage.wasm.ast.Function;
import com.oracle.svm.hosted.webimage.wasm.ast.FunctionTypeDescriptor;
import com.oracle.svm.hosted.webimage.wasm.ast.Instruction;
import com.oracle.svm.hosted.webimage.wasm.ast.Instruction.Binary;
import com.oracle.svm.hosted.webimage.wasm.ast.Instruction.Const;
import com.oracle.svm.hosted.webimage.wasm.ast.Instruction.Unary;
import com.oracle.svm.hosted.webimage.wasm.ast.Instructions;
import com.oracle.svm.hosted.webimage.wasm.ast.TypeUse;
import com.oracle.svm.hosted.webimage.wasm.ast.id.WasmId;
import com.oracle.svm.hosted.webimage.wasm.ast.id.WasmIdFactory;
import com.oracle.svm.hosted.webimage.wasm.codegen.WasmCodeGenTool;
import com.oracle.svm.hosted.webimage.wasm.codegen.WasmFunctionTemplate;
import com.oracle.svm.hosted.webimage.wasmgc.WasmGCUnalignedUnsafeSupport;
import com.oracle.svm.hosted.webimage.wasmgc.WasmGCUnsafeSupport;
import com.oracle.svm.hosted.webimage.wasmgc.ast.id.GCKnownIds;
import com.oracle.svm.hosted.webimage.wasmgc.ast.id.WebImageWasmGCIds;
import com.oracle.svm.hosted.webimage.wasmgc.types.WasmGCUtil;
import com.oracle.svm.hosted.webimage.wasmgc.types.WasmRefType;
import com.oracle.svm.webimage.wasm.types.WasmPrimitiveType;
import com.oracle.svm.webimage.wasm.types.WasmUtil;
import com.oracle.svm.webimage.wasm.types.WasmValType;

import jdk.vm.ci.code.CodeUtil;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Template functions to support {@code sun.misc.Unsafe} accesses (and any other raw memory accesses
 * inside objects) in WasmGC.
 * <p>
 * Only very specific {@code Unsafe} accesses are legal, all others are considered undefined
 * behavior and trigger undefined behavior. In WasmGC this undefined behavior usually manifests
 * itself by trapping.
 * <p>
 * For non-array objects, an access is only legal if the offset corresponds to the start of a Java
 * field in that object. For arrays, the offset must be of the form {@code B+N*S} where {@code N} is
 * an in-bounds array index, and {@code B} and {@code S} are the array base offset (see
 * {@code sun.misc.Unsafe#arrayBaseOffset}) and the array index scale (see
 * {@code sun.misc.Unsafe#arrayIndexScale}) for that array type respectively. In addition, the
 * access type must match the field or array component type
 * <p>
 * To support field accesses, each {@link DynamicHub} has a custom field containing a sparse array
 * containing global function table indices for accessor functions for all the object's fields, the
 * dispatch array. The read accessor for a field with offset {@code X} is at index {@code 2*X} and
 * the write accessor at {@code 2*X+1}. Given an object and an offset, the corresponding accessor
 * can be looked up from that array. Any invalid offset will ultimately cause a trap (either because
 * the object doesn't have any accessible fields, the index is out of bounds, there is no accessor
 * at that index, or the accessor doesn't match the access type).
 * <p>
 * Consider {@link java.lang.String}, where Native Image maps fields at the following offsets:
 * <ul>
 * <li>12: {@code String.hash}</li>
 * <li>16: {@code String.value}</li>
 * <li>24: {@code String.coder}</li>
 * <li>25: {@code String.hashIsZero}</li>
 * </ul>
 *
 * The dispatch array in the {@link DynamicHub} instance for {@link String} will have 52 entries (2
 * for each possible offset, even the ones that are not mapped to a field). The array contains
 * indices into the global function tables corresponding to the read/write accessor functions for
 * that offset. For example, for the {@code value} field at offset 16, index 32 in the dispatch
 * array will contain the function index for the read accessor and index 33 the index for the write
 * accessor. Any indices not mapped to some field will contain 0.
 *
 * <pre>
 *     Dispatch
 *      Array                Global Function Table
 *  0 +-------+        0 +---------------------------+
 *    |       |          |                           |
 *    :  ...  :          :            ...            :
 * 24 +-------+     1138 +---------------------------+
 *    |  1138 |          | Read: String.hash         |
 * 25 +-------+     1139 +---------------------------+
 *    |  1142 |          | Read: String.value        |
 *    +-------+     1140 +---------------------------+
 *    :  ...  :          | Read: String.coder        |
 * 32 +-------+     1141 +---------------------------+
 *    | 1139  |          | Read: String.hashIsZero   |
 * 33 +-------+     1142 +---------------------------+
 *    | 1143  |          | Write: String.hash        |
 *    +-------+     1143 +---------------------------+
 *    :  ...  :          | Write: String.value       |
 * 48 +-------+     1144 +---------------------------+
 *    | 1140  |          | Write: String.coder       |
 * 49 +-------+     1145 +---------------------------+
 *    | 1144  |          | Write: String.hashIsZero  |
 * 50 +-------+          +---------------------------+
 *    | 1141  |          |                           |
 * 51 +-------+          :            ...            :
 *    | 1145  |
 * 52 +-------+
 * </pre>
 * <p>
 * For array accesses, the original array index can be computed from the offset and the access type:
 * {@code (offset / B) >>> log2(S)}, where {@code B} and {@code S} are again the array base offset
 * and index scale for the access type as above. This is implemented in {@link ArrayAccess}.
 * <p>
 * See {@link DispatchAccess} for the main template implementing this logic.
 */
public class WasmGCUnsafeTemplates {
    /**
     * Field accessor function. Each function reads/writes a specific Java field.
     * <p>
     * Takes a generic {@link Object} as receiver instead of the specific type declaring the field.
     * This done so that all accessor functions accessing a field of the same {@link JavaKind} have
     * the same type signature for easy dispatch (see {@link DispatchAccess}).
     * <p>
     * For the same reason, writes to {@link Object} fields will also take {@link Object} as the
     * value argument and inserts an explicit downcast.
     * <p>
     * For reads, this generates:
     *
     * <pre>{@code
     * (func $func.unsafe.read.field.<class>.<field> (param $receiver (ref null $_Object)) (result <field type>)
     *   (struct.get $<struct name> $<field name>
     *     (ref.cast (ref null $<struct name>)
     *       (local.get $receiver)
     *     )
     *   )
     * )
     * }</pre>
     *
     * Will look mostly the same for writes, except the function takes and additional value
     * argument, doesn't return anything, and uses {@code struct.set} instead of {@code struct.get}.
     * <p>
     * For static fields, this will generate a global get or set, ignoring the receiver object (but
     * still including it as a parameter so that function signatures match).
     */
    public static class FieldAccess extends WasmFunctionTemplate<FieldAccess.Param> {
        protected record Param(HostedField field, boolean isRead) {
            boolean isValid() {
                return field.hasLocation();
            }
        }

        public FieldAccess(WasmIdFactory idFactory) {
            super(idFactory, true);
        }

        public WasmId.Func requestReadFunctionId(HostedField field) {
            return requestFunctionId(new Param(field, true));
        }

        public WasmId.Func requestWriteFunctionId(HostedField field) {
            return requestFunctionId(new Param(field, false));
        }

        @Override
        protected boolean isValidParameter(Param param) {
            return param.isValid();
        }

        @Override
        protected String getFunctionName(Param param) {
            return "unsafe." + getReadWriteString(param.isRead()) + ".field." + WebImageNamingConvention.getInstance().identForType(param.field().getDeclaringClass()) + "." + param.field().getName();
        }

        @Override
        protected Function createFunction(Context ctxt) {
            Param param = ctxt.getParameter();
            WebImageWasmGCProviders providers = (WebImageWasmGCProviders) ctxt.getProviders();
            WasmGCUtil util = providers.util();

            boolean isRead = param.isRead();
            HostedField field = param.field();
            ResolvedJavaType fieldType = util.canonicalizeJavaType(field.getType());
            JavaKind accessKind = util.memoryKind(fieldType.getJavaKind());
            WasmValType accessType = util.mapType(accessKind);

            TypeUse typeUse = isRead ? TypeUse.withResult(accessType, util.getJavaLangObjectType()) : TypeUse.withoutResult(util.getJavaLangObjectType(), accessType);

            Function f = ctxt.createFunction(typeUse, "Unsafe field " + getReadWriteString(isRead) + " for field " + field.format("%H.%n of type %T"));
            WasmId.Local valueParam = isRead ? null : f.getParam(1);
            Instructions instructions = f.getInstructions();

            if (field.isStatic()) {
                /*
                 * For static fields, the first parameter is ignored, it will be a marker object
                 * that "holds" the static field values, but the actual field is stored in a global
                 * variable.
                 */
                WasmValType fieldWasmType = util.typeForJavaType(fieldType);
                WasmId.Global staticField = idFactory.forStaticField(fieldWasmType, field);

                if (isRead) {
                    instructions.add(staticField.getter());
                } else {
                    instructions.add(staticField.setter(downcastFieldValue(util, fieldType, valueParam.getter())));
                }
            } else {
                ResolvedJavaType receiverType = util.canonicalizeJavaType(field.getDeclaringClass());
                WebImageWasmGCIds.JavaStruct receiverStruct = idFactory.newJavaStruct(receiverType);
                WasmId.Field javaField = idFactory.newJavaField(field);

                WasmId.Local objectParam = f.getParam(0);
                Instruction downCastReceiver = new Instruction.RefCast(objectParam.getter(), (WasmRefType) util.typeForJavaType(receiverType));

                if (isRead) {
                    instructions.add(new Instruction.StructGet(receiverStruct, javaField, WasmUtil.Extension.forKind(fieldType.getJavaKind()), downCastReceiver));
                } else {
                    Instruction value = downcastFieldValue(util, fieldType, valueParam.getter());
                    instructions.add(new Instruction.StructSet(receiverStruct, javaField, downCastReceiver, value));
                }
            }

            return f;
        }

        private static Instruction downcastFieldValue(WasmUtil util, ResolvedJavaType fieldType, Instruction originalValue) {
            /*
             * Object field stores require an explicit downcast since the value parameter is of type
             * java.lang.Object.
             */
            if (!fieldType.isPrimitive() && !fieldType.isJavaLangObject()) {
                return new Instruction.RefCast(originalValue, (WasmRefType) util.typeForJavaType(fieldType));
            }

            return originalValue;
        }
    }

    protected record DispatchAccessParam(JavaKind accessKind, boolean isRead) {
        boolean isValid() {
            return accessKind.getSlotCount() > 0;
        }
    }

    /**
     * Main entry point for arbitrary unsafe accesses, parameterized on whether it is a read or
     * write and the {@link JavaKind} of the access.
     * <p>
     * Given a receiver object and an offset, it will try to access the right array element or
     * object field. For arrays, see
     * {@link #getArrayDispatch(WebImageWasmGCProviders, boolean, WasmId.Local, WasmId.Local, WasmId.Local, JavaKind)}.
     * For fields, uses {@link GetDispatchIndex} to get a function table index, then looks up and
     * calls the function reference stored in the table.
     * <p>
     * For reads, this generates approximately:
     *
     * <pre>{@code
     * (func $func.unsafe.dispatch.read.<access kind> (param $receiver (ref null $_Object)) (param $offset i64) (result i32)
     *   (local $tableIndex i32)
     *   (local $accessor funcref)
     *
     *   (; Optional null check ;)
     *
     *   (if
     *     (ref.test (ref $struct.baseArray)
     *       (local.get $receiver)
     *     )
     *     (then
     *       (; Check for all possible array types and dispatch to the matching one ;)
     *       (; see {@link #getArrayDispatch(WebImageWasmGCProviders, boolean, WasmId.Local, WasmId.Local, WasmId.Local, JavaKind)};)
     *
     *       (; For primitive accesses: Dispatch to {@link WasmGCUnalignedUnsafeSupport} ;)
     *       (; For object accesses: Optional error message about array type mismatch ;)
     *       (unreachable)
     *     )
     *   )
     *
     *   (; Optional check for hub type, see {@link #getHubVtableDispatch(WebImageWasmGCProviders, WasmId.Local, WasmId.Local)} ;)
     *
     *   (local.set $tableIndex
     *     (call $func.unsafe.dispatch.read.getindex
     *       (local.get $receiver)
     *       (local.get $offset)
     *     ) (; see {@link GetDispatchIndex} ;)
     *   )
     *
     *   (; Optional check that tableIndex is not 0 ;)
     *
     *   (local.set $accessor
     *     (table.get $table0
     *       (local.get $tableIndex)
     *     )
     *   )
     *
     *   (; Optional check that the accessor function has the right type ;)
     *
     *   (call_ref $<accessor function type>
     *     (local.get $receiver)
     *     (ref.cast (ref $<accessor function type>)
     *       (local.get $accessor)
     *     )
     *   )
     * )
     * }</pre>
     *
     * Will look mostly the same for writes, except the function takes and addition value argument,
     * doesn't return anything, and calls the write variant of the various other templates it uses.
     */
    public static class DispatchAccess extends WasmFunctionTemplate<DispatchAccessParam> {
        public DispatchAccess(WasmIdFactory idFactory) {
            super(idFactory);
        }

        @Override
        protected boolean isValidParameter(DispatchAccessParam param) {
            return param.isValid();
        }

        public WasmId.Func requestReadFunctionId(JavaKind accessKind) {
            return requestFunctionId(new DispatchAccessParam(accessKind, true));
        }

        public WasmId.Func requestWriteFunctionId(JavaKind accessKind) {
            return requestFunctionId(new DispatchAccessParam(accessKind, false));
        }

        @Override
        protected String getFunctionName(DispatchAccessParam param) {
            return "unsafe.dispatch." + getReadWriteString(param.isRead()) + "." + param.accessKind;
        }

        @Override
        protected Function createFunction(Context ctxt) {
            DispatchAccessParam param = ctxt.getParameter();
            WebImageWasmGCProviders providers = (WebImageWasmGCProviders) ctxt.getProviders();
            WasmGCUtil util = providers.util();
            WasmGCCodeGenTool codeGenTool = (WasmGCCodeGenTool) ctxt.getCodeGenTool();

            boolean isRead = param.isRead();
            JavaKind accessKind = param.accessKind;
            WasmValType accessType = util.mapType(accessKind);

            TypeUse typeUse = isRead ? TypeUse.withResult(accessType, util.getJavaLangObjectType(), WasmPrimitiveType.i64)
                            : TypeUse.withoutResult(util.getJavaLangObjectType(), WasmPrimitiveType.i64, accessType);

            Function f = ctxt.createFunction(typeUse, "Unsafe " + getReadWriteString(isRead) + " for type " + accessKind);

            WasmId.Local objectParam = f.getParam(0);
            WasmId.Local offsetParam = f.getParam(1);
            // Only available for write dispatches
            WasmId.Local valueParam = isRead ? null : f.getParam(2);

            Instructions instructions = f.getInstructions();

            instructions.add(checkForFatalError(codeGenTool, isRead, Unary.Op.RefIsNull.create(objectParam.getter()), objectParam.getter(), "Access on null object", offsetParam.getter()));

            Instruction.If arrayCheck = new Instruction.If(null, providers.builder.isArrayStruct(objectParam.getter(), null));
            instructions.add(arrayCheck);

            /*
             * Some access kinds may have multiple underlying array kinds they could be accessing
             * (e.g. in the IR both a char and short access are represented as just a 16 bit access,
             * which we map to a short access).
             */
            JavaKind[] componentKindsToCheck = switch (accessKind) {
                case Boolean, Byte -> new JavaKind[]{JavaKind.Boolean, JavaKind.Byte};
                case Short, Char -> new JavaKind[]{JavaKind.Short, JavaKind.Char};
                default -> new JavaKind[]{accessKind};
            };

            // For each possible access kind check if the array type matches and dispatch
            for (JavaKind componentKind : componentKindsToCheck) {
                arrayCheck.thenInstructions.add(getArrayDispatch(providers, isRead, objectParam, offsetParam, valueParam, componentKind));
            }

            if (accessKind.isPrimitive()) {
                // For primitive accesses we fall back to the unaligned access code
                generateUnalignedArrayAccess(codeGenTool, isRead, accessKind, objectParam.getter(), offsetParam.getter(), isRead ? null : valueParam.getter(), arrayCheck.thenInstructions::add);
            } else {
                /*
                 * If none match, the effective array type is incompatible with the access kind of
                 * this function.
                 */
                arrayCheck.thenInstructions.add(fatalError(codeGenTool, isRead, objectParam.getter(),
                                "Array type mismatch. Attempt to access array element of type " +
                                                Arrays.stream(componentKindsToCheck).map(JavaKind::toString).collect(Collectors.joining(" or ")),
                                offsetParam.getter()));
            }

            // Word reads may also target a hub's vtable
            if (isRead && providers.getWordTypes().getWordKind() == accessKind) {
                instructions.add(getHubVtableDispatch(providers, objectParam, offsetParam));
            }

            WasmId.Local functionIdx = idFactory.newTemporaryVariable(WasmPrimitiveType.i32);
            instructions.add(functionIdx.setter(new Instruction.Call(providers.knownIds().getDispatchIndexTemplate.requestFunctionId(isRead), objectParam.getter(), offsetParam.getter())));

            instructions.add(checkForFatalError(codeGenTool,
                            isRead,
                            Unary.Op.I32Eqz.create(functionIdx.getter()),
                            objectParam.getter(),
                            "No accessor for field type " + accessKind + " exists",
                            offsetParam.getter()));

            TypeUse accessorTypeUse = isRead ? TypeUse.withResult(accessType, util.getJavaLangObjectType()) : TypeUse.withoutResult(util.getJavaLangObjectType(), accessType);
            WasmId.FuncType accessorFuncType = idFactory.newFuncType(FunctionTypeDescriptor.createSimple(accessorTypeUse));

            Instructions args = new Instructions();
            args.add(objectParam.getter());

            if (!isRead) {
                args.add(valueParam.getter());
            }

            WasmId.Local funcref = idFactory.newTemporaryVariable(WasmRefType.FUNCREF);
            instructions.add(funcref.setter(new Instruction.TableGet(providers.knownIds().functionTable, functionIdx.getter())));

            instructions.add(checkForFatalError(codeGenTool, isRead, Unary.Op.I32Eqz.create(new Instruction.RefTest(funcref.getter(), accessorFuncType.asNonNull())), objectParam.getter(),
                            "Field type mismatch, expected " + accessKind, offsetParam.getter()));

            instructions.add(new Instruction.CallRef(accessorFuncType, new Instruction.RefCast(funcref.getter(), accessorFuncType.asNonNull()), args));

            return f;
        }

        /**
         * Generates an array type check and dispatches to the corresponding array accessor function
         * (see {@link ArrayAccess}) if the object is an array of the given type:
         *
         * <pre>{@code
         * (if
         *   (ref.test (ref $struct.array.<component kind>)
         *     (local.get $receiver)
         *   )
         *   (then
         *     (return
         *       (call $func.unsafe.read.array.<component kind>
         *         (ref.cast (ref $struct.array.<component kind>)
         *           (local.get $receiver)
         *         )
         *         (local.get $offset)
         *       ) (; Dispatch to matching array accessor, see {@link ArrayAccess} ;)
         *     )
         *   )
         * )
         * }</pre>
         *
         * Will look mostly the same for writes, except that it calls the write variant of the array
         * accessor template.
         */
        private static Instruction getArrayDispatch(WebImageWasmGCProviders providers, boolean isRead, WasmId.Local objectParam, WasmId.Local offsetParam, WasmId.Local valueParam,
                        JavaKind componentKind) {
            Instruction.If arrayCheck = new Instruction.If(null, providers.builder.isArrayStruct(objectParam.getter(), componentKind));
            Instructions instructions = arrayCheck.thenInstructions;

            Instruction castArray = new Instruction.RefCast(objectParam.getter(), providers.knownIds().getArrayStructType(componentKind).asNonNull());

            if (isRead) {
                instructions.add(new Instruction.Return(new Instruction.Call(providers.knownIds().arrayAccessTemplate.requestReadFunctionId(componentKind), castArray, offsetParam.getter())));
            } else {
                instructions.add(new Instruction.Call(providers.knownIds().arrayAccessTemplate.requestWriteFunctionId(componentKind), castArray, offsetParam.getter(), valueParam.getter()));
                instructions.add(new Instruction.Return());
            }

            return arrayCheck;
        }

        /**
         * Dispatches to {@link WasmGCUnalignedUnsafeSupport} to perform an unaligned array access.
         * <p>
         * This can be used for arbitrary primitive accesses, both unaligned and/or with the array
         * element kind not matching the access kind.
         */
        private static void generateUnalignedArrayAccess(WasmGCCodeGenTool codeGenTool, boolean isRead, JavaKind accessKind, Instruction object, Instruction offset, Instruction value,
                        Consumer<Instruction> gen) {
            assert accessKind.isPrimitive() : "Only primitive arrays can have unaligned accesses, got " + accessKind;

            WebImageWasmGCProviders providers = codeGenTool.getWasmProviders();
            SnippetRuntime.SubstrateForeignCallDescriptor descriptor = (isRead ? WasmGCUnalignedUnsafeSupport.READ_ARRAY_FOREIGN_CALLS : WasmGCUnalignedUnsafeSupport.WRITE_ARRAY_FOREIGN_CALLS)
                            .get(accessKind);
            ResolvedJavaMethod method = descriptor.findMethod(providers.getMetaAccess());
            WasmId.Func methodId = providers.idFactory().forMethod(method);

            if (isRead) {
                gen.accept(new Instruction.Return(codeGenTool.getCall(method, true, new Instruction.Call(methodId, object, offset))));
            } else {
                gen.accept(codeGenTool.getCall(method, true, new Instruction.Call(methodId, object, offset, value)));
                gen.accept(new Instruction.Return());
            }

        }

        /**
         * Produces instructions to potentially read an entry from the vtable if the receiver object
         * is a {@link DynamicHub}.
         * <p>
         * Unsafe accesses to hubs at offsets at or above {@link KnownOffsets#getVTableBaseOffset()}
         * are not Java field reads, but reads of vtable entries (which in Native Image are stored
         * after the hub fields). In that case, the instructions perform a vtable read and return
         * the correct vtable entry.
         * <p>
         * Can only be used for Word reads.
         */
        private static Instruction getHubVtableDispatch(WebImageWasmGCProviders providers, WasmId.Local objectParam, WasmId.Local offsetParam) {
            GCKnownIds knownIds = providers.knownIds();
            WasmGCUtil util = providers.util();
            KnownOffsets knownOffsets = KnownOffsets.singleton();
            WasmRefType hubObjectType = util.getHubObjectType();

            /*
             * object instanceof DynamicHub && offset >= vtableBaseOffset
             *
             * If the check succeeds, the offset is supposed to point to a vtable entry of the hub
             * object.
             */
            Instruction condition = Binary.Op.I32And.create(new Instruction.RefTest(objectParam.getter(), hubObjectType),
                            Binary.Op.I64GeS.create(offsetParam.getter(), Const.forLong(knownOffsets.getVTableBaseOffset())));
            Instruction.If hubCheck = new Instruction.If(null, condition);

            Instruction vtable = new Instruction.StructGet(util.getHubObjectId(), knownIds.vtableField, WasmUtil.Extension.None, new Instruction.RefCast(objectParam.getter(), hubObjectType));
            // Reconstruct vtable index by computing (offset - vtableBaseOffset) / vtableEntrySize
            Instruction vtableIndex = Binary.Op.I32DivS.create(
                            Binary.Op.I32Sub.create(
                                            Unary.Op.I32Wrap64.create(offsetParam.getter()),
                                            Const.forInt(knownOffsets.getVTableBaseOffset())),
                            Const.forInt(knownOffsets.getVTableEntrySize()));
            hubCheck.thenInstructions.add(new Instruction.Return(new Instruction.ArrayGet(knownIds.vtableFieldType, WasmUtil.Extension.None, vtable, vtableIndex)));

            return hubCheck;
        }

    }

    /**
     * Function that looks up the function table index for the accessor at the given offset.
     * <p>
     * Each object's {@link DynamicHub} has a sparse dispatch array that contains indices in the
     * global function table for the read and write accessor functions for all fields in the object.
     * This function retrieves that function table index based on the offset and whether this is a
     * read or write.
     * <p>
     * Reads live at index {@code 2*offset} and writes at {@code 2*offset + 1} so that they're both
     * in the same array.
     *
     * <pre>{@code
     * (func $func.unsafe.dispatch.read.getindex (param $receiver (ref null $_Object)) (param $offset i64) (result i32)
     *   (local $index i32)
     *   (local $dispatchArray (ref null $array.accessDispatchTable))
     *   (local.set $index
     *     (i32.mul
     *       (i32.wrap_i64
     *         (local.get $offset)
     *       )
     *       (i32.const 0x2)
     *     )
     *   ) (; For reads, the accessor function is at index 2*offset ;)
     *   (local.set $dispatchArray
     *     (struct.get $_Class $field.accessDispatch
     *       (struct.get $_Object $field.dynamicHub
     *         (local.get $receiver)
     *       )
     *     )
     *   )
     *
     *   (; Optional check that the array is non-null ;)
     *   (; Optional check that the index is within the array boundaries ;)
     *
     *   (array.get $array.accessDispatchTable
     *     (local.get $dispatchArray)
     *     (local.get $index)
     *   )
     * )
     * }</pre>
     *
     * Will look mostly the same for writes, except that the index into the array is calculated as
     * {@code 2*offset + 1}.
     */
    public static class GetDispatchIndex extends WasmFunctionTemplate<Boolean> {

        public GetDispatchIndex(WasmIdFactory idFactory) {
            super(idFactory);
        }

        @Override
        protected boolean isValidParameter(Boolean isRead) {
            return isRead != null;
        }

        @Override
        protected String getFunctionName(Boolean isRead) {
            return "unsafe.dispatch." + getReadWriteString(isRead) + ".getindex";
        }

        @Override
        protected Function createFunction(Context ctxt) {
            WebImageWasmGCProviders providers = (WebImageWasmGCProviders) ctxt.getProviders();
            WasmGCUtil util = providers.util();
            GCKnownIds knownIds = providers.knownIds();

            WasmId.StructType baseObjectId = util.getJavaLangObjectId();
            WasmId.StructType hubObjectId = util.getHubObjectId();

            boolean isRead = ctxt.getParameter();

            Function f = ctxt.createFunction(TypeUse.withResult(WasmPrimitiveType.i32, baseObjectId.asNullable(), WasmPrimitiveType.i64),
                            "Get function table index for the " + getReadWriteString(isRead) + " function of the given object at the given offset");

            WasmId.Local objectParam = f.getParam(0);
            WasmId.Local offsetParam = f.getParam(1);

            Instructions instructions = f.getInstructions();

            Instruction arrayIndexInstr = Binary.Op.I32Mul.create(Unary.Op.I32Wrap64.create(offsetParam.getter()), Const.forInt(2));

            if (!isRead) {
                arrayIndexInstr = Binary.Op.I32Add.create(arrayIndexInstr, Const.forInt(1));
            }

            WasmId.Local arrayIndex = idFactory.newTemporaryVariable(WasmPrimitiveType.i32);
            instructions.add(arrayIndex.setter(arrayIndexInstr));

            WasmId.Local array = idFactory.newTemporaryVariable(knownIds.accessDispatchFieldType.asNullable());
            instructions.add(array.setter(new Instruction.StructGet(hubObjectId, knownIds.accessDispatchField, WasmUtil.Extension.None,
                            providers.builder().getHub(objectParam.getter()))));

            // If the dispatch array is null, this object has no accessors
            instructions.add(checkForFatalError(ctxt.getCodeGenTool(), isRead,
                            Unary.Op.RefIsNull.create(array.getter()),
                            objectParam.getter(), "No unsafe accessible fields available", offsetParam.getter()));

            instructions.add(checkForFatalError(ctxt.getCodeGenTool(), isRead,
                            // Array bounds check (arrayIndex < 0 || arrayIndex >= array.length)
                            Binary.Op.I32Or.create(Binary.Op.I32LtS.create(arrayIndex.getter(), Const.forInt(0)),
                                            Binary.Op.I32GeS.create(arrayIndex.getter(), new Instruction.ArrayLen(array.getter()))),
                            objectParam.getter(), "Outside of object boundary", offsetParam.getter()));

            instructions.add(new Instruction.ArrayGet(knownIds.accessDispatchFieldType, WasmUtil.Extension.None, array.getter(), arrayIndex.getter()));

            return f;
        }
    }

    /**
     * Function for unsafe array accesses parameterized on the specific component kind and whether
     * this is a read of write.
     * <p>
     * Translates the offset to an array index:
     * {@code (offset - arrayBaseOffset) / arrayIndexScale}.
     * <p>
     * For reads, this generates approximately:
     *
     * <pre>{@code
     * (func $func.unsafe.read.array.<type> (param $receiver (ref null $<array struct type>)) (param $offset i64) (result <component type>)
     *   (local $scaledIndex i64)
     *   (local.set $scaledIndex
     *     (i64.sub
     *       (local.get $offset)
     *       (i64.const 0x10) (; arrayBaseOffset ;)
     *     )
     *   )
     *
     *   (; Optional check that scaledIndex points to the start of an array element ;)
     *
     *   (array.get $<inner array type>
     *     (struct.get $<array struct type> $field.inner
     *       (local.get $receiver)
     *     )
     *     (i32.wrap_i64
     *       (i64.shr_u
     *         (local.get $scaledIndex)
     *         (i64.const 0x2) (; log2(arrayIndexScale) ;)
     *       )
     *     )
     *   )
     * )
     * }</pre>
     *
     * Will look mostly the same for writes, except the function takes an additional value argument,
     * doesn't return anything, and uses {@code array.set} instead of {@code array.get}.
     *
     * @see DispatchAccess
     */
    public static class ArrayAccess extends WasmFunctionTemplate<DispatchAccessParam> {
        public ArrayAccess(WasmIdFactory idFactory) {
            super(idFactory);
        }

        @Override
        protected boolean isValidParameter(DispatchAccessParam param) {
            return param.isValid();
        }

        public WasmId.Func requestReadFunctionId(JavaKind accessKind) {
            return requestFunctionId(new DispatchAccessParam(accessKind, true));
        }

        public WasmId.Func requestWriteFunctionId(JavaKind accessKind) {
            return requestFunctionId(new DispatchAccessParam(accessKind, false));
        }

        @Override
        protected String getFunctionName(DispatchAccessParam param) {
            return "unsafe." + getReadWriteString(param.isRead()) + ".array." + param.accessKind();
        }

        @Override
        protected Function createFunction(Context ctxt) {
            DispatchAccessParam param = ctxt.getParameter();
            WebImageWasmGCProviders providers = (WebImageWasmGCProviders) ctxt.getProviders();
            WasmGCUtil util = providers.util();
            MetaAccessProvider metaAccess = providers.getMetaAccess();
            WasmGCCodeGenTool codeGenTool = (WasmGCCodeGenTool) ctxt.getCodeGenTool();

            boolean isRead = param.isRead();
            JavaKind componentKind = param.accessKind();

            ResolvedJavaType receiverType = metaAccess.lookupJavaType((componentKind == JavaKind.Object ? Object.class : componentKind.toJavaClass()).arrayType());
            int baseOffset = metaAccess.getArrayBaseOffset(componentKind);
            int indexScale = metaAccess.getArrayIndexScale(componentKind);
            assert CodeUtil.isPowerOf2(indexScale);
            int indexScaleBits = CodeUtil.log2(indexScale);
            long indexScaleMask = CodeUtil.mask(indexScaleBits);

            TypeUse typeUse = isRead ? TypeUse.withResult(util.mapType(componentKind), util.typeForJavaType(receiverType), WasmPrimitiveType.i64)
                            : TypeUse.withoutResult(util.typeForJavaType(receiverType), WasmPrimitiveType.i64, util.mapType(componentKind));

            Function f = ctxt.createFunction(typeUse, "Unsafe array " + getReadWriteString(isRead) + " of type " + componentKind);

            WasmId.Local objectParam = f.getParam(0);
            WasmId.Local offsetParam = f.getParam(1);
            // Only available for write dispatches
            WasmId.Local valueParam = isRead ? null : f.getParam(2);

            // offset - baseOffset
            WasmId.Local scaledIndex = idFactory.newTemporaryVariable(WasmPrimitiveType.i64);

            Instructions instructions = f.getInstructions();
            instructions.add(scaledIndex.setter(Binary.Op.I64Sub.create(offsetParam.getter(), Const.forLong(baseOffset))));

            /*
             * Is 1, if the offset is not aligned to the start of some array element (could be out
             * of bounds though).
             *
             * scaledIndex & indexScaleMax != 0
             */
            Instruction offsetAlignmentCheck = Binary.Op.I64Ne.create(Binary.Op.I64And.create(scaledIndex.getter(), Const.forLong(indexScaleMask)), Const.forLong(0));

            if (componentKind.isPrimitive()) {
                /*
                 * For primitive arrays, fall back to unaligned access code in case the offset is
                 * not element aligned.
                 */
                Instruction.If offsetCheckIf = new Instruction.If(null, offsetAlignmentCheck);
                instructions.add(offsetCheckIf);
                DispatchAccess.generateUnalignedArrayAccess(codeGenTool, isRead, componentKind, objectParam.getter(), offsetParam.getter(), isRead ? null : valueParam.getter(),
                                offsetCheckIf.thenInstructions::add);
            } else {
                /*
                 * This will produce an optional error message only for unaligned object array
                 * accesses. Out of bounds accesses will just trap later when the actual access is
                 * performed.
                 */
                instructions.add(checkForFatalError(codeGenTool, isRead, offsetAlignmentCheck, objectParam.getter(), "Between array elements",
                                offsetParam.getter()));
            }

            // scaledIndex >>> indexScaleBits
            Instruction index = Unary.Op.I32Wrap64.create(Binary.Op.I64ShrU.create(scaledIndex.getter(), Const.forLong(indexScaleBits)));

            if (isRead) {
                instructions.add(providers.builder.getArrayElement(objectParam.getter(), index, componentKind));
            } else {
                instructions.add(providers.builder.setArrayElement(objectParam.getter(), index, valueParam.getter(), componentKind));
            }

            return f;

        }
    }

    /**
     * Produces a human-readable error message in case the given condition is met at runtime.
     * <p>
     * Used to produce better error messages for illegal unsafe accesses (e.g. the given offset does
     * not point to a field, or it's outside the object's boundary).
     * <p>
     * The error is only produced if error messages are turned on. Otherwise, nothing happens. This
     * is fine, if the condition is met, the access triggers undefined behavior, the error message
     * just makes it easier to debug.
     *
     * @param condition Instruction, which, if true, will cause a fatal error.
     * @return An if instruction block that performs the check and traps if the condition is true.
     *         Or a nop if error messages are turned off.
     * @see #fatalError(WasmCodeGenTool, boolean, Instruction, String, Instruction)
     */
    private static Instruction checkForFatalError(WasmCodeGenTool codeGenTool, boolean isRead, Instruction condition, Instruction receiver, String errorMessage, Instruction offset) {
        if (WasmGCUnsafeSupport.includeErrorMessage()) {
            Instruction.If check = new Instruction.If(null, condition);
            check.thenInstructions.add(fatalError(codeGenTool, isRead, receiver, errorMessage, offset));
            return check;
        } else {
            return new Instruction.Nop();
        }
    }

    /**
     * Unconditionally traps with the given error message.
     * <p>
     * Can be turned off using the {@code UnsafeErrorMessages} option. In which case this is a
     * simple trap.
     *
     * @param errorMessage Human-readable error message presented to the user. Will be included in
     *            the image heap.
     * @param offset Requested offset for which the error occurred
     * @return An instruction, that ultimately traps. If errors are turned on, will dispatch to
     *         {@link WasmGCUnsafeSupport#fatalAccessError(Object, String, long, boolean)}
     */
    private static Instruction fatalError(WasmCodeGenTool codeGenTool, boolean isRead, Instruction receiver, String errorMessage, Instruction offset) {
        if (WasmGCUnsafeSupport.includeErrorMessage()) {
            ResolvedJavaMethod method = WasmGCUnsafeSupport.FATAL_ACCESS_ERROR.findMethod(codeGenTool.getProviders().getMetaAccess());
            /*
             * Create image heap constant for error message. Interned, so it's not duplicated in the
             * image heap.
             */
            Instruction string = codeGenTool.getConstantRelocation(codeGenTool.getProviders().getConstantReflection().forString(errorMessage.intern()));
            return new Instruction.Call(codeGenTool.idFactory.forMethod(method), receiver, string, offset, Const.forBoolean(isRead));
        } else {
            return new Instruction.Unreachable();
        }
    }

    private static String getReadWriteString(boolean isRead) {
        return isRead ? "read" : "write";
    }
}
