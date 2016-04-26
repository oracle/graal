/*
Copyright (c) 2013, Intel Corporation

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

    * Redistributions of source code must retain the above copyright notice,
      this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above copyright notice,
      this list of conditions and the following disclaimer in the documentation
      and/or other materials provided with the distribution.
    * Neither the name of Intel Corporation nor the names of its contributors
      may be used to endorse or promote products derived from this software
      without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package com.intel.llvm.ireditor.types;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.xtext.nodemodel.util.NodeModelUtils;
import org.eclipse.xtext.util.OnChangeEvictingCache;

import com.google.inject.Provider;
import com.intel.llvm.ireditor.constants.ConstantResolver;
import com.intel.llvm.ireditor.lLVM_IR.AddressSpace;
import com.intel.llvm.ireditor.lLVM_IR.Alias;
import com.intel.llvm.ireditor.lLVM_IR.ArrayConstant;
import com.intel.llvm.ireditor.lLVM_IR.ArrayType;
import com.intel.llvm.ireditor.lLVM_IR.BinaryInstruction;
import com.intel.llvm.ireditor.lLVM_IR.BitwiseBinaryInstruction;
import com.intel.llvm.ireditor.lLVM_IR.Constant;
import com.intel.llvm.ireditor.lLVM_IR.ConstantExpression;
import com.intel.llvm.ireditor.lLVM_IR.ConstantExpression_binary;
import com.intel.llvm.ireditor.lLVM_IR.ConstantExpression_compare;
import com.intel.llvm.ireditor.lLVM_IR.ConstantExpression_convert;
import com.intel.llvm.ireditor.lLVM_IR.ConstantExpression_getelementptr;
import com.intel.llvm.ireditor.lLVM_IR.ConstantExpression_select;
import com.intel.llvm.ireditor.lLVM_IR.ConversionInstruction;
import com.intel.llvm.ireditor.lLVM_IR.FloatingType;
import com.intel.llvm.ireditor.lLVM_IR.FunctionHeader;
import com.intel.llvm.ireditor.lLVM_IR.GlobalValueRef;
import com.intel.llvm.ireditor.lLVM_IR.GlobalVariable;
import com.intel.llvm.ireditor.lLVM_IR.Instruction_alloca;
import com.intel.llvm.ireditor.lLVM_IR.Instruction_atomicrmw;
import com.intel.llvm.ireditor.lLVM_IR.Instruction_call_nonVoid;
import com.intel.llvm.ireditor.lLVM_IR.Instruction_cmpxchg;
import com.intel.llvm.ireditor.lLVM_IR.Instruction_extractelement;
import com.intel.llvm.ireditor.lLVM_IR.Instruction_extractvalue;
import com.intel.llvm.ireditor.lLVM_IR.Instruction_fcmp;
import com.intel.llvm.ireditor.lLVM_IR.Instruction_getelementptr;
import com.intel.llvm.ireditor.lLVM_IR.Instruction_icmp;
import com.intel.llvm.ireditor.lLVM_IR.Instruction_insertelement;
import com.intel.llvm.ireditor.lLVM_IR.Instruction_insertvalue;
import com.intel.llvm.ireditor.lLVM_IR.Instruction_invoke_nonVoid;
import com.intel.llvm.ireditor.lLVM_IR.Instruction_landingpad;
import com.intel.llvm.ireditor.lLVM_IR.Instruction_load;
import com.intel.llvm.ireditor.lLVM_IR.Instruction_phi;
import com.intel.llvm.ireditor.lLVM_IR.Instruction_select;
import com.intel.llvm.ireditor.lLVM_IR.Instruction_shufflevector;
import com.intel.llvm.ireditor.lLVM_IR.Instruction_va_arg;
import com.intel.llvm.ireditor.lLVM_IR.IntType;
import com.intel.llvm.ireditor.lLVM_IR.LocalValueRef;
import com.intel.llvm.ireditor.lLVM_IR.MetadataNode;
import com.intel.llvm.ireditor.lLVM_IR.MetadataRef;
import com.intel.llvm.ireditor.lLVM_IR.MetadataString;
import com.intel.llvm.ireditor.lLVM_IR.MetadataType;
import com.intel.llvm.ireditor.lLVM_IR.NamedMiddleInstruction;
import com.intel.llvm.ireditor.lLVM_IR.NamedTerminatorInstruction;
import com.intel.llvm.ireditor.lLVM_IR.NonLeftRecursiveNonVoidType;
import com.intel.llvm.ireditor.lLVM_IR.NonLeftRecursiveType;
import com.intel.llvm.ireditor.lLVM_IR.OpaqueType;
import com.intel.llvm.ireditor.lLVM_IR.Parameter;
import com.intel.llvm.ireditor.lLVM_IR.ParameterType;
import com.intel.llvm.ireditor.lLVM_IR.SimpleConstant;
import com.intel.llvm.ireditor.lLVM_IR.Star;
import com.intel.llvm.ireditor.lLVM_IR.StartingInstruction;
import com.intel.llvm.ireditor.lLVM_IR.StructType;
import com.intel.llvm.ireditor.lLVM_IR.StructureConstant;
import com.intel.llvm.ireditor.lLVM_IR.Type;
import com.intel.llvm.ireditor.lLVM_IR.TypeDef;
import com.intel.llvm.ireditor.lLVM_IR.TypeSuffix;
import com.intel.llvm.ireditor.lLVM_IR.TypedConstant;
import com.intel.llvm.ireditor.lLVM_IR.TypedValue;
import com.intel.llvm.ireditor.lLVM_IR.Undef;
import com.intel.llvm.ireditor.lLVM_IR.VectorConstant;
import com.intel.llvm.ireditor.lLVM_IR.VectorType;
import com.intel.llvm.ireditor.lLVM_IR.VoidType;
import com.intel.llvm.ireditor.lLVM_IR.X86mmxType;
import com.intel.llvm.ireditor.lLVM_IR.ZeroInitializer;
import com.intel.llvm.ireditor.lLVM_IR.util.LLVM_IRSwitch;

/**
 * Converts an EObject to a String representing its type.
 */
public class TypeResolver extends LLVM_IRSwitch<ResolvedType> {
    private final ConstantResolver constResolver = new ConstantResolver();
    private final OnChangeEvictingCache resolvedNamedTypesCache = new OnChangeEvictingCache();

    public static final Map<String, ResolvedType> SIMPLE_TYPES = new HashMap<>();
    public static final ResolvedUnknownType TYPE_UNKNOWN = new ResolvedUnknownType();
    public static final ResolvedVarargType TYPE_VARARG = new ResolvedVarargType();
    public static final ResolvedAnyType TYPE_ANY = new ResolvedAnyType();
    public static final ResolvedType TYPE_ANY_POINTER = new ResolvedPointerType(TYPE_ANY, BigInteger.valueOf(-1));
    public static final ResolvedAnyArrayType TYPE_CSTRING = new ResolvedAnyArrayType(new ResolvedIntegerType(8));
    public static final ResolvedAnyFloatingType TYPE_FLOATING = new ResolvedAnyFloatingType();
    public static final ResolvedIntegerType TYPE_BOOLEAN = new ResolvedIntegerType(1);
    public static final ResolvedMetadataType TYPE_METADATA = new ResolvedMetadataType();
    public static final ResolvedOpaqueType TYPE_OPAQUE = new ResolvedOpaqueType();
    public static final ResolvedAnyIntegerType TYPE_ANY_INTEGER = new ResolvedAnyIntegerType();
    public static final ResolvedAnyVectorType TYPE_ANY_VECTOR = new ResolvedAnyVectorType(TYPE_ANY);
    public static final ResolvedAnyFunctionType TYPE_ANY_FUNCTION = new ResolvedAnyFunctionType();
    public static final ResolvedPointerType TYPE_ANY_FUNCTION_POINTER = new ResolvedPointerType(TYPE_ANY_FUNCTION, BigInteger.ZERO);
    public static final ResolvedIntegerType TYPE_I32 = new ResolvedIntegerType(32);
    public static final ResolvedType TYPE_ANY_ARRAY = new ResolvedAnyArrayType(TYPE_ANY);
    public static final ResolvedType TYPE_ANY_STRUCT = new ResolvedAnyStructType();
    public static final ResolvedAnyVectorType TYPE_INTEGER_VECTOR = new ResolvedAnyVectorType(TYPE_ANY_INTEGER);
    public static final ResolvedAnyVectorType TYPE_POINTER_VECTOR = new ResolvedAnyVectorType(TYPE_ANY_POINTER);
    public static final ResolvedAnyVectorType TYPE_FLOATING_VECTOR = new ResolvedAnyVectorType(TYPE_FLOATING);
    public static final ResolvedAnyVectorType TYPE_BOOLEAN_VECTOR = new ResolvedAnyVectorType(TYPE_BOOLEAN);
    public static final ResolvedVoidType TYPE_VOID = new ResolvedVoidType();

    private static final BigInteger MAX_INTEGER_TYPE_SIZE = BigInteger.valueOf((2 << 23) - 1);
    private static final Object RESOLVED_NAMED_TYPES_CACHE_KEY = new Object();

    static {
        SIMPLE_TYPES.put("void", new ResolvedVoidType());
        SIMPLE_TYPES.put("half", new ResolvedFloatingType("half", 16));
        SIMPLE_TYPES.put("float", new ResolvedFloatingType("float", 32));
        SIMPLE_TYPES.put("double", new ResolvedFloatingType("double", 64));
        SIMPLE_TYPES.put("fp128", new ResolvedFloatingType("fp128", 128));
        SIMPLE_TYPES.put("x86_fp80", new ResolvedFloatingType("x86_fp80", 80));
        SIMPLE_TYPES.put("ppc_fp128", new ResolvedFloatingType("ppc_fp128", 128));
    }

    public ResolvedType resolve(EObject object) {
        if (object == null) {
            return TYPE_UNKNOWN;
        }
        ResolvedType result = doSwitch(object);
        return result == null ? TYPE_UNKNOWN : result;
    }

    @Override
    public ResolvedType defaultCase(EObject object) {
        return TYPE_UNKNOWN;
    }

    @Override
    public ResolvedType caseType(Type object) {
        ResolvedType result = resolve(object.getBaseType());
        result = buildPointersTo(result, object.getStars());
        for (TypeSuffix suffix : object.getSuffixes()) {
            result = buildTypeFromSuffix(result, suffix);
        }
        return result;
    }

    @Override
    public ResolvedType caseInstruction_call_nonVoid(Instruction_call_nonVoid object) {
        return resolveCall(object.getType());
    }

    @Override
    public ResolvedType caseInstruction_invoke_nonVoid(Instruction_invoke_nonVoid object) {
        return resolveCall(object.getType());
    }

    private ResolvedType resolveCall(Type type) {
        ResolvedType t = resolve(type);
        if (t.isPointer() && t.getContainedType(0).isFunction()) {
            return t.getContainedType(0).asFunction().getReturnType();
        }
        return t;
    }

    @Override
    public ResolvedType caseNonLeftRecursiveType(NonLeftRecursiveType object) {
        return resolveNonLeftRecursiveType(object.getType(), object.getTypedef());
    }

    @Override
    public ResolvedType caseNonLeftRecursiveNonVoidType(NonLeftRecursiveNonVoidType object) {
        return resolveNonLeftRecursiveType(object.getType(), object.getTypedef());
    }

    private ResolvedType resolveNonLeftRecursiveType(EObject type, TypeDef typeDef) {
        return resolve(type != null ? type : typeDef);
    }

    @Override
    public ResolvedType caseParameterType(ParameterType object) {
        return resolve(object.getType());
    }

    @Override
    public ResolvedType caseIntType(IntType object) {
        BigInteger bits = atoi(textOf(object).substring(1));
        if (bits.compareTo(MAX_INTEGER_TYPE_SIZE) >= 0) {
            return TYPE_UNKNOWN;
        }
        return new ResolvedIntegerType(bits.intValue());
    }

    @Override
    public ResolvedFloatingType caseFloatingType(FloatingType object) {
        return (ResolvedFloatingType) getSimpleType(textOf(object));
    }

    @Override
    public ResolvedFloatingType caseX86mmxType(X86mmxType object) {
        return (ResolvedFloatingType) getSimpleType(textOf(object));
    }

    @Override
    public ResolvedVoidType caseVoidType(VoidType object) {
        return (ResolvedVoidType) getSimpleType(textOf(object));
    }

    @Override
    public ResolvedMetadataType caseMetadataType(MetadataType object) {
        return TYPE_METADATA;
    }

    @Override
    public ResolvedType caseMetadataNode(MetadataNode object) {
        return TYPE_METADATA;
    }

    @Override
    public ResolvedType caseMetadataString(MetadataString object) {
        return TYPE_METADATA;
    }

    @Override
    public ResolvedType caseMetadataRef(MetadataRef object) {
        return TYPE_METADATA;
    }

    @Override
    public ResolvedOpaqueType caseOpaqueType(OpaqueType object) {
        return TYPE_OPAQUE;
    }

    @Override
    public ResolvedVectorType caseVectorType(VectorType object) {
        return new ResolvedVectorType(atoi(object.getSize()).intValue(), resolve(object.getElemType()));
    }

    @Override
    public ResolvedArrayType caseArrayType(ArrayType object) {
        return new ResolvedArrayType(atoi(object.getSize()).intValue(), resolve(object.getElemType()));
    }

    @Override
    public ResolvedStructType caseStructType(StructType object) {
        EList<Type> types = object.getTypes();
        List<ResolvedType> resolvedTypes = new ArrayList<>(types.size());
        for (Type t : types) {
            resolvedTypes.add(resolve(t));
        }
        return new ResolvedStructType(resolvedTypes, object.getPacked() != null, false);
    }

    @Override
    public ResolvedNamedType caseTypeDef(TypeDef object) {
        String name = object.getName();

        Map<String, ResolvedNamedType> resolvedNamedTypes = resolvedNamedTypesCache.get(RESOLVED_NAMED_TYPES_CACHE_KEY, object.eResource(), new Provider<Map<String, ResolvedNamedType>>() {
            @Override
            public Map<String, ResolvedNamedType> get() {
                return new HashMap<>();
            }
        });

        // To prevent infinite recursion on recursive types:
        ResolvedNamedType type = resolvedNamedTypes.get(name);
        if (type != null) {
            return type;
        }

        // Create a named type, resolve the actual type, and then set the named type to refer to it
        ResolvedNamedType result = new ResolvedNamedType(name);
        resolvedNamedTypes.put(name, result);
        result.setReferredType(resolve(object.getType()));

        return result;
    }

    @Override
    public ResolvedType caseTypedValue(TypedValue object) {
        return resolve(object.getType());
    }

    @Override
    public ResolvedType caseLocalValueRef(LocalValueRef object) {
        return resolve(object.getRef());
    }

    @Override
    public ResolvedType caseGlobalValueRef(GlobalValueRef object) {
        if (object.getConstant() != null) {
            return resolve(object.getConstant());
        }
        if (object.getMetadata() != null) {
            return resolve(object.getMetadata());
        }
        return null;
    }

    @Override
    public ResolvedType caseParameter(Parameter object) {
        return resolve(object.getType().getType());
    }

    @Override
    public ResolvedType caseStartingInstruction(StartingInstruction object) {
        return resolve(object.getInstruction());
    }

    @Override
    public ResolvedType caseNamedMiddleInstruction(NamedMiddleInstruction object) {
        return resolve(object.getInstruction());
    }

    @Override
    public ResolvedType caseNamedTerminatorInstruction(NamedTerminatorInstruction object) {
        return resolve(object.getInstruction());
    }

    @Override
    public ResolvedType caseAlias(Alias object) {
        return resolve(object.getType());
    }

    @Override
    public ResolvedPointerType caseFunctionHeader(FunctionHeader object) {
        ResolvedType rettype = resolve(object.getRettype());
        List<ResolvedType> paramTypes = new LinkedList<>();
        for (Parameter p : object.getParameters().getParameters()) {
            paramTypes.add(resolve(p.getType()));
        }
        if (object.getParameters().getVararg() != null) {
            paramTypes.add(TYPE_VARARG);
        }
        return new ResolvedPointerType(new ResolvedFunctionType(rettype, paramTypes), BigInteger.ZERO);
    }

    @Override
    public ResolvedType caseTypedConstant(TypedConstant object) {
        return resolve(object.getType());
    }

    @Override
    public ResolvedType caseSimpleConstant(SimpleConstant object) {
        String content = textOf(object);
        if (content.startsWith("c\"")) {
            return TYPE_CSTRING;
        } else if (content.matches("-?\\d+\\.\\d+(e[+-]?\\d+)?") || content.matches("0x[klmhKLMH]?[0-9a-fA-F]+")) {
            return TYPE_FLOATING;
        } else if (content.matches("-?\\d+")) {
            return TYPE_ANY_INTEGER;
        } else if (content.equals("true") || content.equals("false")) {
            return TYPE_BOOLEAN;
        } else if (content.equals("null")) {
            return TYPE_ANY_POINTER;
        }
        return null;
    }

    @Override
    public ResolvedType caseZeroInitializer(ZeroInitializer object) {
        return TYPE_ANY;
    }

    @Override
    public ResolvedType caseUndef(Undef object) {
        return TYPE_ANY;
    }

    @Override
    public ResolvedVectorType caseVectorConstant(VectorConstant object) {
        EList<TypedConstant> values = object.getList().getTypedConstants();
        return new ResolvedVectorType(BigInteger.valueOf(values.size()).intValue(), resolve(values.get(0).getType()));
    }

    @Override
    public ResolvedArrayType caseArrayConstant(ArrayConstant object) {
        EList<TypedConstant> values = object.getList().getTypedConstants();
        return new ResolvedArrayType(BigInteger.valueOf(values.size()).intValue(), resolve(values.get(0).getType()));
    }

    @Override
    public ResolvedStructType caseStructureConstant(StructureConstant object) {
        EList<TypedConstant> values = object.getList().getTypedConstants();
        List<ResolvedType> resolvedTypes = new ArrayList<>(values.size());
        for (TypedConstant tc : values) {
            resolvedTypes.add(resolve(tc.getType()));
        }
        return new ResolvedStructType(resolvedTypes, object.getPacked() != null, true);
    }

    @Override
    public ResolvedType caseBinaryInstruction(BinaryInstruction object) {
        return resolve(object.getType());
    }

    @Override
    public ResolvedType caseBitwiseBinaryInstruction(BitwiseBinaryInstruction object) {
        return resolve(object.getType());
    }

    @Override
    public ResolvedType caseConversionInstruction(ConversionInstruction object) {
        return resolve(object.getTargetType());
    }

    @Override
    public ResolvedPointerType caseInstruction_alloca(Instruction_alloca object) {
        return new ResolvedPointerType(resolve(object.getType()), BigInteger.ZERO);
    }

    @Override
    public ResolvedType caseInstruction_atomicrmw(Instruction_atomicrmw object) {
        return resolve(object.getArgument().getType());
    }

    @Override
    public ResolvedType caseInstruction_phi(Instruction_phi object) {
        return resolve(object.getType());
    }

    @Override
    public ResolvedType caseInstruction_select(Instruction_select object) {
        ResolvedType conditionType = resolve(object.getCondition());
        if (conditionType.isVector()) {
            return new ResolvedVectorType(conditionType.asVector().getSize(), resolve(object.getValue1()).getContainedType(0));
        }
        return resolve(object.getValue1().getType());
    }

    @Override
    public ResolvedType caseInstruction_load(Instruction_load object) {
        return resolve(object.getPointer().getType()).getContainedType(0);
    }

    @Override
    public ResolvedType caseInstruction_extractelement(Instruction_extractelement object) {
        return resolve(object.getVector().getType()).getContainedType(0);
    }

    @Override
    public ResolvedVectorType caseInstruction_insertelement(Instruction_insertelement object) {
        return resolve(object.getVector().getType()).asVector();
    }

    @Override
    public ResolvedType caseInstruction_shufflevector(Instruction_shufflevector object) {
        ResolvedVectorType mask = resolve(object.getMask().getType()).asVector();
        ResolvedType element = resolve(object.getVector1().getType()).getContainedType(0);
        if (element == null) {
            return null;
        }
        return new ResolvedVectorType(mask.getSize(), element);
    }

    @Override
    public ResolvedType caseInstruction_getelementptr(Instruction_getelementptr object) {
        return resolveGep(object.getBase().getType(), object.getIndices());
    }

    @Override
    public ResolvedType caseInstruction_extractvalue(Instruction_extractvalue object) {
        ResolvedType result = resolve(object.getAggregate().getType());

        for (Constant index : object.getIndices()) {
            Integer indexValue = constResolver.getInteger(index);
            if (indexValue == null) {
                // We could not resolve the index constant, so we cannot tell what the type is.
                return TYPE_ANY;
            }
            result = result.getContainedType(indexValue);
            if (result == null) {
                return null;
            }
        }

        return result;
    }

    @Override
    public ResolvedType caseInstruction_insertvalue(Instruction_insertvalue object) {
        return resolve(object.getAggregate().getType());
    }

    @Override
    public ResolvedType caseInstruction_cmpxchg(Instruction_cmpxchg object) {
        return resolve(object.getComparedWith().getType());
    }

    @Override
    public ResolvedType caseInstruction_icmp(Instruction_icmp object) {
        ResolvedType type = resolve(object.getType());
        if (type.isVector()) {
            return new ResolvedVectorType(type.asVector().getSize(), TYPE_BOOLEAN);
        }
        return TYPE_BOOLEAN;
    }

    @Override
    public ResolvedType caseInstruction_fcmp(Instruction_fcmp object) {
        ResolvedType type = resolve(object.getType());
        if (type.isVector()) {
            return new ResolvedVectorType(type.asVector().getSize(), TYPE_BOOLEAN);
        }
        return TYPE_BOOLEAN;
    }

    @Override
    public ResolvedType caseInstruction_va_arg(Instruction_va_arg object) {
        return resolve(object.getType());
    }

    @Override
    public ResolvedType caseInstruction_landingpad(Instruction_landingpad object) {
        return resolve(object.getResultType());
    }

    @Override
    public ResolvedPointerType caseGlobalVariable(GlobalVariable object) {
        return new ResolvedPointerType(resolve(object.getType()), atoi(object.getAddrspace().getValue()));
    }

    @Override
    public ResolvedType caseConstantExpression_binary(ConstantExpression_binary object) {
        return resolve(object.getOp1().getType());
    }

    @Override
    public ResolvedType caseConstantExpression_compare(ConstantExpression_compare object) {
        ResolvedType type = resolve(object.getOp1().getType());
        if (type.isVector()) {
            return new ResolvedVectorType(type.asVector().getSize(), TYPE_BOOLEAN);
        }
        return TYPE_BOOLEAN;
    }

    @Override
    public ResolvedType caseConstantExpression_convert(ConstantExpression_convert object) {
        return resolve(object.getTargetType());
    }

    @Override
    public ResolvedType caseConstantExpression_select(ConstantExpression_select object) {
        ResolvedType conditionType = resolve(object.getCondition());
        if (conditionType.isVector()) {
            return new ResolvedVectorType(conditionType.asVector().getSize(), resolve(object.getOp1()).getContainedType(0));
        }
        return resolve(object.getOp1().getType());
    }

    @Override
    public ResolvedType caseConstantExpression_getelementptr(ConstantExpression_getelementptr object) {
        return resolveGep(object.getConstantType(), object.getIndices());
    }

    @Override
    public ResolvedType caseConstantExpression(ConstantExpression object) {
        // TODO remove this once all constant expression types are handled
        return TYPE_ANY;
    }

    @Override
    public ResolvedType caseConstant(Constant object) {
        return resolve(object.getRef());
    }

    private ResolvedType resolveGep(Type baseType, EList<? extends EObject> indices) {
        ResolvedType result = resolve(baseType);
        if (result.isVector()) {
            return result;
        }

        if (!result.isPointer()) {
            // That's not legal
            return TYPE_UNKNOWN;
        }

        BigInteger addrSpace = result.asPointer().getAddrSpace();

        for (EObject index : indices) {
            Integer indexValue = 0;
            if (result.isStruct()) {
                indexValue = constResolver.getInteger(index);
                if (indexValue == null) {
                    // We could not resolve the index constant, so we cannot tell what the type is.
                    return TYPE_ANY;
                }
            }
            result = result.getContainedType(indexValue);
            if (result == null) {
                return null;
            }
        }

        return new ResolvedPointerType(result, addrSpace);
    }

    private static String textOf(EObject object) {
        return NodeModelUtils.getTokenText(NodeModelUtils.getNode(object));
    }

    private static BigInteger atoi(String s) {
        try {
            return new BigInteger(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private ResolvedType buildTypeFromSuffix(ResolvedType rettype, TypeSuffix suffix) {
        List<ResolvedType> paramTypes = new LinkedList<>();
        for (ParameterType t : suffix.getContainedTypes()) {
            paramTypes.add(resolve(t));
        }
        if (suffix.getVararg() != null) {
            paramTypes.add(TYPE_VARARG);
        }
        ResolvedType result = new ResolvedFunctionType(rettype, paramTypes);
        return buildPointersTo(result, suffix.getStars());
    }

    private static ResolvedType buildPointersTo(ResolvedType base, Iterable<Star> stars) {
        ResolvedType result = base;
        for (Star star : stars) {
            AddressSpace addrSpace = star.getAddressSpace();
            BigInteger addrSpaceValue = addrSpace == null ? BigInteger.ZERO : atoi(addrSpace.getValue());
            result = new ResolvedPointerType(result, addrSpaceValue);
        }
        return result;
    }

    private static ResolvedType getSimpleType(String text) {
        return SIMPLE_TYPES.get(text);
    }

}
