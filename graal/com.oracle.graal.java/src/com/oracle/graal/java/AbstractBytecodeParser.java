/*
 * Copyright (c) 2014, 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

package com.oracle.graal.java;

import static com.oracle.graal.api.code.TypeCheckHints.*;
import static com.oracle.graal.bytecode.Bytecodes.*;
import java.util.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.api.meta.ProfilingInfo.TriState;
import com.oracle.graal.api.meta.ResolvedJavaType.Representation;
import com.oracle.graal.bytecode.*;
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.compiler.common.calc.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.java.BciBlockMapping.BciBlock;
import com.oracle.graal.nodes.*;
import com.oracle.graal.options.*;
import com.oracle.graal.phases.*;

public abstract class AbstractBytecodeParser<T extends KindProvider, F extends AbstractFrameStateBuilder<T, F>> {

    static class Options {
        // @formatter:off
        @Option(help = "The trace level for the bytecode parser used when building a graph from bytecode")
        public static final OptionValue<Integer> TraceBytecodeParserLevel = new OptionValue<>(0);
        // @formatter:on
    }

    /**
     * The minimum value to which {@link Options#TraceBytecodeParserLevel} must be set to trace the
     * bytecode instructions as they are parsed.
     */
    public static final int TRACELEVEL_INSTRUCTIONS = 1;

    /**
     * The minimum value to which {@link Options#TraceBytecodeParserLevel} must be set to trace the
     * frame state before each bytecode instruction as it is parsed.
     */
    public static final int TRACELEVEL_STATE = 2;

    protected F frameState;
    protected BytecodeStream stream;
    private GraphBuilderConfiguration graphBuilderConfig;
    protected ResolvedJavaMethod method;
    protected BciBlock currentBlock;
    protected ProfilingInfo profilingInfo;
    protected OptimisticOptimizations optimisticOpts;
    protected ConstantPool constantPool;
    private final MetaAccessProvider metaAccess;
    protected int entryBCI;

    /**
     * Meters the number of actual bytecodes parsed.
     */
    public static final DebugMetric BytecodesParsed = Debug.metric("BytecodesParsed");

    public AbstractBytecodeParser(MetaAccessProvider metaAccess, ResolvedJavaMethod method, GraphBuilderConfiguration graphBuilderConfig, OptimisticOptimizations optimisticOpts, F frameState,
                    BytecodeStream stream, ProfilingInfo profilingInfo, ConstantPool constantPool, int entryBCI) {
        this.frameState = frameState;
        this.graphBuilderConfig = graphBuilderConfig;
        this.optimisticOpts = optimisticOpts;
        this.metaAccess = metaAccess;
        this.stream = stream;
        this.profilingInfo = profilingInfo;
        this.constantPool = constantPool;
        this.entryBCI = entryBCI;
        this.method = method;
        assert metaAccess != null;
    }

    /**
     * Start the bytecode parser.
     */
    protected abstract void build();

    public void setCurrentFrameState(F frameState) {
        this.frameState = frameState;
    }

    public final void setStream(BytecodeStream stream) {
        this.stream = stream;
    }

    protected final BytecodeStream getStream() {
        return stream;
    }

    protected int bci() {
        return stream.currentBCI();
    }

    public void loadLocal(int index, Kind kind) {
        frameState.push(kind, frameState.loadLocal(index));
    }

    public void storeLocal(Kind kind, int index) {
        T value;
        if (kind == Kind.Object) {
            value = frameState.xpop();
            // astore and astore_<n> may be used to store a returnAddress (jsr)
            assert value.getKind() == Kind.Object || value.getKind() == Kind.Int;
        } else {
            value = frameState.pop(kind);
        }
        frameState.storeLocal(index, value);
    }

    /**
     * @param type the unresolved type of the constant
     */
    protected abstract void handleUnresolvedLoadConstant(JavaType type);

    /**
     * @param type the unresolved type of the type check
     * @param object the object value whose type is being checked against {@code type}
     */
    protected abstract void handleUnresolvedCheckCast(JavaType type, T object);

    /**
     * @param type the unresolved type of the type check
     * @param object the object value whose type is being checked against {@code type}
     */
    protected abstract void handleUnresolvedInstanceOf(JavaType type, T object);

    /**
     * @param type the type being instantiated
     */
    protected abstract void handleUnresolvedNewInstance(JavaType type);

    /**
     * @param type the type of the array being instantiated
     * @param length the length of the array
     */
    protected abstract void handleUnresolvedNewObjectArray(JavaType type, T length);

    /**
     * @param type the type being instantiated
     * @param dims the dimensions for the multi-array
     */
    protected abstract void handleUnresolvedNewMultiArray(JavaType type, List<T> dims);

    /**
     * @param field the unresolved field
     * @param receiver the object containing the field or {@code null} if {@code field} is static
     */
    protected abstract void handleUnresolvedLoadField(JavaField field, T receiver);

    /**
     * @param field the unresolved field
     * @param value the value being stored to the field
     * @param receiver the object containing the field or {@code null} if {@code field} is static
     */
    protected abstract void handleUnresolvedStoreField(JavaField field, T value, T receiver);

    /**
     * @param representation
     * @param type
     */
    protected abstract void handleUnresolvedExceptionType(Representation representation, JavaType type);

    // protected abstract void handleUnresolvedInvoke(JavaMethod javaMethod, InvokeKind invokeKind);

    // protected abstract DispatchBeginNode handleException(T exceptionObject, int bci);

    private void genLoadConstant(int cpi, int opcode) {
        Object con = lookupConstant(cpi, opcode);

        if (con instanceof JavaType) {
            // this is a load of class constant which might be unresolved
            JavaType type = (JavaType) con;
            if (type instanceof ResolvedJavaType) {
                frameState.push(Kind.Object, appendConstant(((ResolvedJavaType) type).getEncoding(Representation.JavaClass)));
            } else {
                handleUnresolvedLoadConstant(type);
            }
        } else if (con instanceof Constant) {
            Constant constant = (Constant) con;
            frameState.push(constant.getKind().getStackKind(), appendConstant(constant));
        } else {
            throw new Error("lookupConstant returned an object of incorrect type");
        }
    }

    protected abstract T genLoadIndexed(T index, T array, Kind kind);

    private void genLoadIndexed(Kind kind) {
        emitExplicitExceptions(frameState.peek(1), frameState.peek(0));

        T index = frameState.ipop();
        T array = frameState.apop();
        frameState.push(kind.getStackKind(), append(genLoadIndexed(array, index, kind)));
    }

    protected abstract T genStoreIndexed(T array, T index, Kind kind, T value);

    private void genStoreIndexed(Kind kind) {
        emitExplicitExceptions(frameState.peek(2), frameState.peek(1));

        T value = frameState.pop(kind.getStackKind());
        T index = frameState.ipop();
        T array = frameState.apop();
        append(genStoreIndexed(array, index, kind, value));
    }

    private void stackOp(int opcode) {
        switch (opcode) {
            case POP: {
                frameState.xpop();
                break;
            }
            case POP2: {
                frameState.xpop();
                frameState.xpop();
                break;
            }
            case DUP: {
                T w = frameState.xpop();
                frameState.xpush(w);
                frameState.xpush(w);
                break;
            }
            case DUP_X1: {
                T w1 = frameState.xpop();
                T w2 = frameState.xpop();
                frameState.xpush(w1);
                frameState.xpush(w2);
                frameState.xpush(w1);
                break;
            }
            case DUP_X2: {
                T w1 = frameState.xpop();
                T w2 = frameState.xpop();
                T w3 = frameState.xpop();
                frameState.xpush(w1);
                frameState.xpush(w3);
                frameState.xpush(w2);
                frameState.xpush(w1);
                break;
            }
            case DUP2: {
                T w1 = frameState.xpop();
                T w2 = frameState.xpop();
                frameState.xpush(w2);
                frameState.xpush(w1);
                frameState.xpush(w2);
                frameState.xpush(w1);
                break;
            }
            case DUP2_X1: {
                T w1 = frameState.xpop();
                T w2 = frameState.xpop();
                T w3 = frameState.xpop();
                frameState.xpush(w2);
                frameState.xpush(w1);
                frameState.xpush(w3);
                frameState.xpush(w2);
                frameState.xpush(w1);
                break;
            }
            case DUP2_X2: {
                T w1 = frameState.xpop();
                T w2 = frameState.xpop();
                T w3 = frameState.xpop();
                T w4 = frameState.xpop();
                frameState.xpush(w2);
                frameState.xpush(w1);
                frameState.xpush(w4);
                frameState.xpush(w3);
                frameState.xpush(w2);
                frameState.xpush(w1);
                break;
            }
            case SWAP: {
                T w1 = frameState.xpop();
                T w2 = frameState.xpop();
                frameState.xpush(w1);
                frameState.xpush(w2);
                break;
            }
            default:
                throw GraalInternalError.shouldNotReachHere();
        }
    }

    protected abstract T genIntegerAdd(Kind kind, T x, T y);

    protected abstract T genIntegerSub(Kind kind, T x, T y);

    protected abstract T genIntegerMul(Kind kind, T x, T y);

    protected abstract T genFloatAdd(Kind kind, T x, T y, boolean isStrictFP);

    protected abstract T genFloatSub(Kind kind, T x, T y, boolean isStrictFP);

    protected abstract T genFloatMul(Kind kind, T x, T y, boolean isStrictFP);

    protected abstract T genFloatDiv(Kind kind, T x, T y, boolean isStrictFP);

    protected abstract T genFloatRem(Kind kind, T x, T y, boolean isStrictFP);

    private void genArithmeticOp(Kind result, int opcode) {
        T y = frameState.pop(result);
        T x = frameState.pop(result);
        boolean isStrictFP = method.isStrict();
        T v;
        switch (opcode) {
            case IADD:
            case LADD:
                v = genIntegerAdd(result, x, y);
                break;
            case FADD:
            case DADD:
                v = genFloatAdd(result, x, y, isStrictFP);
                break;
            case ISUB:
            case LSUB:
                v = genIntegerSub(result, x, y);
                break;
            case FSUB:
            case DSUB:
                v = genFloatSub(result, x, y, isStrictFP);
                break;
            case IMUL:
            case LMUL:
                v = genIntegerMul(result, x, y);
                break;
            case FMUL:
            case DMUL:
                v = genFloatMul(result, x, y, isStrictFP);
                break;
            case FDIV:
            case DDIV:
                v = genFloatDiv(result, x, y, isStrictFP);
                break;
            case FREM:
            case DREM:
                v = genFloatRem(result, x, y, isStrictFP);
                break;
            default:
                throw new GraalInternalError("should not reach");
        }
        frameState.push(result, append(v));
    }

    protected abstract T genIntegerDiv(Kind kind, T x, T y);

    protected abstract T genIntegerRem(Kind kind, T x, T y);

    private void genIntegerDivOp(Kind result, int opcode) {
        T y = frameState.pop(result);
        T x = frameState.pop(result);
        T v;
        switch (opcode) {
            case IDIV:
            case LDIV:
                v = genIntegerDiv(result, x, y);
                break;
            case IREM:
            case LREM:
                v = genIntegerRem(result, x, y);
                break;
            default:
                throw new GraalInternalError("should not reach");
        }
        frameState.push(result, append(v));
    }

    protected abstract T genNegateOp(T x);

    private void genNegateOp(Kind kind) {
        frameState.push(kind, append(genNegateOp(frameState.pop(kind))));
    }

    protected abstract T genLeftShift(Kind kind, T x, T y);

    protected abstract T genRightShift(Kind kind, T x, T y);

    protected abstract T genUnsignedRightShift(Kind kind, T x, T y);

    private void genShiftOp(Kind kind, int opcode) {
        T s = frameState.ipop();
        T x = frameState.pop(kind);
        T v;
        switch (opcode) {
            case ISHL:
            case LSHL:
                v = genLeftShift(kind, x, s);
                break;
            case ISHR:
            case LSHR:
                v = genRightShift(kind, x, s);
                break;
            case IUSHR:
            case LUSHR:
                v = genUnsignedRightShift(kind, x, s);
                break;
            default:
                throw new GraalInternalError("should not reach");
        }
        frameState.push(kind, append(v));
    }

    protected abstract T genAnd(Kind kind, T x, T y);

    protected abstract T genOr(Kind kind, T x, T y);

    protected abstract T genXor(Kind kind, T x, T y);

    private void genLogicOp(Kind kind, int opcode) {
        T y = frameState.pop(kind);
        T x = frameState.pop(kind);
        T v;
        switch (opcode) {
            case IAND:
            case LAND:
                v = genAnd(kind, x, y);
                break;
            case IOR:
            case LOR:
                v = genOr(kind, x, y);
                break;
            case IXOR:
            case LXOR:
                v = genXor(kind, x, y);
                break;
            default:
                throw new GraalInternalError("should not reach");
        }
        frameState.push(kind, append(v));
    }

    protected abstract T genNormalizeCompare(T x, T y, boolean isUnorderedLess);

    private void genCompareOp(Kind kind, boolean isUnorderedLess) {
        T y = frameState.pop(kind);
        T x = frameState.pop(kind);
        frameState.ipush(append(genNormalizeCompare(x, y, isUnorderedLess)));
    }

    protected abstract T genFloatConvert(FloatConvert op, T input);

    private void genFloatConvert(FloatConvert op, Kind from, Kind to) {
        T input = frameState.pop(from.getStackKind());
        frameState.push(to.getStackKind(), append(genFloatConvert(op, input)));
    }

    protected abstract T genNarrow(T input, int bitCount);

    protected abstract T genSignExtend(T input, int bitCount);

    protected abstract T genZeroExtend(T input, int bitCount);

    private void genSignExtend(Kind from, Kind to) {
        T input = frameState.pop(from.getStackKind());
        if (from != from.getStackKind()) {
            input = append(genNarrow(input, from.getBitCount()));
        }
        frameState.push(to.getStackKind(), append(genSignExtend(input, to.getBitCount())));
    }

    private void genZeroExtend(Kind from, Kind to) {
        T input = frameState.pop(from.getStackKind());
        if (from != from.getStackKind()) {
            input = append(genNarrow(input, from.getBitCount()));
        }
        frameState.push(to.getStackKind(), append(genZeroExtend(input, to.getBitCount())));
    }

    private void genNarrow(Kind from, Kind to) {
        T input = frameState.pop(from.getStackKind());
        frameState.push(to.getStackKind(), append(genNarrow(input, to.getBitCount())));
    }

    private void genIncrement() {
        int index = getStream().readLocalIndex();
        int delta = getStream().readIncrement();
        T x = frameState.loadLocal(index);
        T y = appendConstant(Constant.forInt(delta));
        frameState.storeLocal(index, append(genIntegerAdd(Kind.Int, x, y)));
    }

    protected abstract void genGoto();

    protected abstract T genObjectEquals(T x, T y);

    protected abstract T genIntegerEquals(T x, T y);

    protected abstract T genIntegerLessThan(T x, T y);

    protected abstract T genUnique(T x);

    protected abstract void genIf(T x, Condition cond, T y);

    private void genIfZero(Condition cond) {
        T y = appendConstant(Constant.INT_0);
        T x = frameState.ipop();
        genIf(x, cond, y);
    }

    private void genIfNull(Condition cond) {
        T y = appendConstant(Constant.NULL_OBJECT);
        T x = frameState.apop();
        genIf(x, cond, y);
    }

    private void genIfSame(Kind kind, Condition cond) {
        T y = frameState.pop(kind);
        T x = frameState.pop(kind);
        // assert !x.isDeleted() && !y.isDeleted();
        genIf(x, cond, y);
    }

    protected abstract void genThrow();

    protected JavaType lookupType(int cpi, int bytecode) {
        eagerResolvingForSnippets(cpi, bytecode);
        JavaType result = constantPool.lookupType(cpi, bytecode);
        assert !graphBuilderConfig.unresolvedIsError() || result instanceof ResolvedJavaType;
        return result;
    }

    private JavaMethod lookupMethod(int cpi, int opcode) {
        eagerResolvingForSnippets(cpi, opcode);
        JavaMethod result = constantPool.lookupMethod(cpi, opcode);
        /*
         * In general, one cannot assume that the declaring class being initialized is useful, since
         * the actual concrete receiver may be a different class (except for static calls). Also,
         * interfaces are initialized only under special circumstances, so that this assertion would
         * often fail for interface calls.
         */
        assert !graphBuilderConfig.unresolvedIsError() || (result instanceof ResolvedJavaMethod && (opcode != INVOKESTATIC || ((ResolvedJavaMethod) result).getDeclaringClass().isInitialized())) : result;
        return result;
    }

    private JavaField lookupField(int cpi, int opcode) {
        eagerResolvingForSnippets(cpi, opcode);
        JavaField result = constantPool.lookupField(cpi, opcode);
        assert !graphBuilderConfig.unresolvedIsError() || (result instanceof ResolvedJavaField && ((ResolvedJavaField) result).getDeclaringClass().isInitialized()) : result;
        return result;
    }

    private Object lookupConstant(int cpi, int opcode) {
        eagerResolvingForSnippets(cpi, opcode);
        Object result = constantPool.lookupConstant(cpi);
        assert !graphBuilderConfig.eagerResolving() || !(result instanceof JavaType) || (result instanceof ResolvedJavaType) : result;
        return result;
    }

    private void eagerResolvingForSnippets(int cpi, int bytecode) {
        if (graphBuilderConfig.eagerResolving()) {
            constantPool.loadReferencedType(cpi, bytecode);
        }
    }

    private JavaTypeProfile getProfileForTypeCheck(ResolvedJavaType type) {
        if (!optimisticOpts.useTypeCheckHints() || !canHaveSubtype(type)) {
            return null;
        } else {
            return profilingInfo.getTypeProfile(bci());
        }
    }

    protected abstract T createCheckCast(ResolvedJavaType type, T object, JavaTypeProfile profileForTypeCheck, boolean forStoreCheck);

    private void genCheckCast() {
        int cpi = getStream().readCPI();
        JavaType type = lookupType(cpi, CHECKCAST);
        T object = frameState.apop();
        if (type instanceof ResolvedJavaType) {
            JavaTypeProfile profileForTypeCheck = getProfileForTypeCheck((ResolvedJavaType) type);
            T checkCastNode = append(createCheckCast((ResolvedJavaType) type, object, profileForTypeCheck, false));
            frameState.apush(checkCastNode);
        } else {
            handleUnresolvedCheckCast(type, object);
        }
    }

    protected abstract T createInstanceOf(ResolvedJavaType type, T object, JavaTypeProfile profileForTypeCheck);

    protected abstract T genConditional(T x);

    private void genInstanceOf() {
        int cpi = getStream().readCPI();
        JavaType type = lookupType(cpi, INSTANCEOF);
        T object = frameState.apop();
        if (type instanceof ResolvedJavaType) {
            ResolvedJavaType resolvedType = (ResolvedJavaType) type;
            T instanceOfNode = createInstanceOf((ResolvedJavaType) type, object, getProfileForTypeCheck(resolvedType));
            frameState.ipush(append(genConditional(genUnique(instanceOfNode))));
        } else {
            handleUnresolvedInstanceOf(type, object);
        }
    }

    protected abstract T createNewInstance(ResolvedJavaType type, boolean fillContents);

    void genNewInstance(int cpi) {
        JavaType type = lookupType(cpi, NEW);
        if (type instanceof ResolvedJavaType && ((ResolvedJavaType) type).isInitialized()) {
            frameState.apush(append(createNewInstance((ResolvedJavaType) type, true)));
        } else {
            handleUnresolvedNewInstance(type);
        }
    }

    /**
     * Gets the kind of array elements for the array type code that appears in a
     * {@link Bytecodes#NEWARRAY} bytecode.
     *
     * @param code the array type code
     * @return the kind from the array type code
     */
    public static Class<?> arrayTypeCodeToClass(int code) {
        // Checkstyle: stop
        switch (code) {
            case 4:
                return boolean.class;
            case 5:
                return char.class;
            case 6:
                return float.class;
            case 7:
                return double.class;
            case 8:
                return byte.class;
            case 9:
                return short.class;
            case 10:
                return int.class;
            case 11:
                return long.class;
            default:
                throw new IllegalArgumentException("unknown array type code: " + code);
        }
        // Checkstyle: resume
    }

    private void genNewPrimitiveArray(int typeCode) {
        Class<?> clazz = arrayTypeCodeToClass(typeCode);
        ResolvedJavaType elementType = metaAccess.lookupJavaType(clazz);
        frameState.apush(append(createNewArray(elementType, frameState.ipop(), true)));
    }

    private void genNewObjectArray(int cpi) {
        JavaType type = lookupType(cpi, ANEWARRAY);
        T length = frameState.ipop();
        if (type instanceof ResolvedJavaType) {
            frameState.apush(append(createNewArray((ResolvedJavaType) type, length, true)));
        } else {
            handleUnresolvedNewObjectArray(type, length);
        }

    }

    protected abstract T createNewArray(ResolvedJavaType elementType, T length, boolean fillContents);

    private void genNewMultiArray(int cpi) {
        JavaType type = lookupType(cpi, MULTIANEWARRAY);
        int rank = getStream().readUByte(bci() + 3);
        List<T> dims = new ArrayList<>(Collections.nCopies(rank, null));
        for (int i = rank - 1; i >= 0; i--) {
            dims.set(i, frameState.ipop());
        }
        if (type instanceof ResolvedJavaType) {
            frameState.apush(append(createNewMultiArray((ResolvedJavaType) type, dims)));
        } else {
            handleUnresolvedNewMultiArray(type, dims);
        }
    }

    protected abstract T createNewMultiArray(ResolvedJavaType type, List<T> dims);

    protected abstract T genLoadField(T receiver, ResolvedJavaField field);

    private void genGetField(JavaField field) {
        emitExplicitExceptions(frameState.peek(0), null);

        Kind kind = field.getKind();
        T receiver = frameState.apop();
        if ((field instanceof ResolvedJavaField) && ((ResolvedJavaField) field).getDeclaringClass().isInitialized()) {
            appendOptimizedLoadField(kind, genLoadField(receiver, (ResolvedJavaField) field));
        } else {
            handleUnresolvedLoadField(field, receiver);
        }
    }

    protected abstract void emitNullCheck(T receiver);

    protected abstract void emitBoundsCheck(T index, T length);

    private static final DebugMetric EXPLICIT_EXCEPTIONS = Debug.metric("ExplicitExceptions");

    protected abstract T genArrayLength(T x);

    protected void emitExplicitExceptions(T receiver, T outOfBoundsIndex) {
        assert receiver != null;
        if (graphBuilderConfig.omitAllExceptionEdges() || (optimisticOpts.useExceptionProbabilityForOperations() && profilingInfo.getExceptionSeen(bci()) == TriState.FALSE)) {
            return;
        }

        emitNullCheck(receiver);
        if (outOfBoundsIndex != null) {
            T length = append(genArrayLength(receiver));
            emitBoundsCheck(outOfBoundsIndex, length);
        }
        EXPLICIT_EXCEPTIONS.increment();
    }

    protected abstract T genStoreField(T receiver, ResolvedJavaField field, T value);

    private void genPutField(JavaField field) {
        emitExplicitExceptions(frameState.peek(1), null);

        T value = frameState.pop(field.getKind().getStackKind());
        T receiver = frameState.apop();
        if (field instanceof ResolvedJavaField && ((ResolvedJavaField) field).getDeclaringClass().isInitialized()) {
            appendOptimizedStoreField(genStoreField(receiver, (ResolvedJavaField) field, value));
        } else {
            handleUnresolvedStoreField(field, value, receiver);
        }
    }

    private void genGetStatic(JavaField field) {
        Kind kind = field.getKind();
        if (field instanceof ResolvedJavaField && ((ResolvedJavaType) field.getDeclaringClass()).isInitialized()) {
            appendOptimizedLoadField(kind, genLoadField(null, (ResolvedJavaField) field));
        } else {
            handleUnresolvedLoadField(field, null);
        }
    }

    private void genPutStatic(JavaField field) {
        T value = frameState.pop(field.getKind().getStackKind());
        if (field instanceof ResolvedJavaField && ((ResolvedJavaType) field.getDeclaringClass()).isInitialized()) {
            appendOptimizedStoreField(genStoreField(null, (ResolvedJavaField) field, value));
        } else {
            handleUnresolvedStoreField(field, value, null);
        }
    }

    protected void appendOptimizedStoreField(T store) {
        append(store);
    }

    protected void appendOptimizedLoadField(Kind kind, T load) {
        // append the load to the instruction
        T optimized = append(load);
        frameState.push(kind.getStackKind(), optimized);
    }

    protected abstract void genInvokeStatic(JavaMethod target);

    protected abstract void genInvokeInterface(JavaMethod target);

    protected abstract void genInvokeDynamic(JavaMethod target);

    protected abstract void genInvokeVirtual(JavaMethod target);

    protected abstract void genInvokeSpecial(JavaMethod target);

    protected abstract void genReturn(T x);

    protected abstract T genMonitorEnter(T x);

    protected abstract T genMonitorExit(T x, T returnValue);

    protected abstract void genJsr(int dest);

    protected abstract void genRet(int localIndex);

    private double[] switchProbability(int numberOfCases, int bci) {
        double[] prob = profilingInfo.getSwitchProbabilities(bci);
        if (prob != null) {
            assert prob.length == numberOfCases;
        } else {
            Debug.log("Missing probability (switch) in %s at bci %d", method, bci);
            prob = new double[numberOfCases];
            for (int i = 0; i < numberOfCases; i++) {
                prob[i] = 1.0d / numberOfCases;
            }
        }
        assert allPositive(prob);
        return prob;
    }

    private static boolean allPositive(double[] a) {
        for (double d : a) {
            if (d < 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * Helper function that sums up the probabilities of all keys that lead to a specific successor.
     *
     * @return an array of size successorCount with the accumulated probability for each successor.
     */
    public static double[] successorProbabilites(int successorCount, int[] keySuccessors, double[] keyProbabilities) {
        double[] probability = new double[successorCount];
        for (int i = 0; i < keySuccessors.length; i++) {
            probability[keySuccessors[i]] += keyProbabilities[i];
        }
        return probability;
    }

    private void genSwitch(BytecodeSwitch bs) {
        int bci = bci();
        T value = frameState.ipop();

        int nofCases = bs.numberOfCases();
        double[] keyProbabilities = switchProbability(nofCases + 1, bci);

        Map<Integer, SuccessorInfo> bciToBlockSuccessorIndex = new HashMap<>();
        for (int i = 0; i < currentBlock.getSuccessorCount(); i++) {
            assert !bciToBlockSuccessorIndex.containsKey(currentBlock.getSuccessors().get(i).startBci);
            if (!bciToBlockSuccessorIndex.containsKey(currentBlock.getSuccessors().get(i).startBci)) {
                bciToBlockSuccessorIndex.put(currentBlock.getSuccessors().get(i).startBci, new SuccessorInfo(i));
            }
        }

        ArrayList<BciBlock> actualSuccessors = new ArrayList<>();
        int[] keys = new int[nofCases];
        int[] keySuccessors = new int[nofCases + 1];
        int deoptSuccessorIndex = -1;
        int nextSuccessorIndex = 0;
        for (int i = 0; i < nofCases + 1; i++) {
            if (i < nofCases) {
                keys[i] = bs.keyAt(i);
            }

            if (isNeverExecutedCode(keyProbabilities[i])) {
                if (deoptSuccessorIndex < 0) {
                    deoptSuccessorIndex = nextSuccessorIndex++;
                    actualSuccessors.add(null);
                }
                keySuccessors[i] = deoptSuccessorIndex;
            } else {
                int targetBci = i >= nofCases ? bs.defaultTarget() : bs.targetAt(i);
                SuccessorInfo info = bciToBlockSuccessorIndex.get(targetBci);
                if (info.actualIndex < 0) {
                    info.actualIndex = nextSuccessorIndex++;
                    actualSuccessors.add(currentBlock.getSuccessors().get(info.blockIndex));
                }
                keySuccessors[i] = info.actualIndex;
            }
        }

        genIntegerSwitch(value, actualSuccessors, keys, keyProbabilities, keySuccessors);

    }

    protected abstract void genIntegerSwitch(T value, ArrayList<BciBlock> actualSuccessors, int[] keys, double[] keyProbabilities, int[] keySuccessors);

    private static class SuccessorInfo {

        int blockIndex;
        int actualIndex;

        public SuccessorInfo(int blockSuccessorIndex) {
            this.blockIndex = blockSuccessorIndex;
            actualIndex = -1;
        }
    }

    protected abstract T appendConstant(Constant constant);

    protected abstract T append(T v);

    protected boolean isNeverExecutedCode(double probability) {
        return probability == 0 && optimisticOpts.removeNeverExecutedCode() && entryBCI == StructuredGraph.INVOCATION_ENTRY_BCI;
    }

    protected abstract void processBlock(BciBlock block);

    protected abstract void iterateBytecodesForBlock(BciBlock block);

    public void processBytecode(int bci, int opcode) {
        int cpi;

        // Checkstyle: stop
        // @formatter:off
    switch (opcode) {
        case NOP            : /* nothing to do */ break;
        case ACONST_NULL    : frameState.apush(appendConstant(Constant.NULL_OBJECT)); break;
        case ICONST_M1      : // fall through
        case ICONST_0       : // fall through
        case ICONST_1       : // fall through
        case ICONST_2       : // fall through
        case ICONST_3       : // fall through
        case ICONST_4       : // fall through
        case ICONST_5       : frameState.ipush(appendConstant(Constant.forInt(opcode - ICONST_0))); break;
        case LCONST_0       : // fall through
        case LCONST_1       : frameState.lpush(appendConstant(Constant.forLong(opcode - LCONST_0))); break;
        case FCONST_0       : // fall through
        case FCONST_1       : // fall through
        case FCONST_2       : frameState.fpush(appendConstant(Constant.forFloat(opcode - FCONST_0))); break;
        case DCONST_0       : // fall through
        case DCONST_1       : frameState.dpush(appendConstant(Constant.forDouble(opcode - DCONST_0))); break;
        case BIPUSH         : frameState.ipush(appendConstant(Constant.forInt(stream.readByte()))); break;
        case SIPUSH         : frameState.ipush(appendConstant(Constant.forInt(stream.readShort()))); break;
        case LDC            : // fall through
        case LDC_W          : // fall through
        case LDC2_W         : genLoadConstant(stream.readCPI(), opcode); break;
        case ILOAD          : loadLocal(stream.readLocalIndex(), Kind.Int); break;
        case LLOAD          : loadLocal(stream.readLocalIndex(), Kind.Long); break;
        case FLOAD          : loadLocal(stream.readLocalIndex(), Kind.Float); break;
        case DLOAD          : loadLocal(stream.readLocalIndex(), Kind.Double); break;
        case ALOAD          : loadLocal(stream.readLocalIndex(), Kind.Object); break;
        case ILOAD_0        : // fall through
        case ILOAD_1        : // fall through
        case ILOAD_2        : // fall through
        case ILOAD_3        : loadLocal(opcode - ILOAD_0, Kind.Int); break;
        case LLOAD_0        : // fall through
        case LLOAD_1        : // fall through
        case LLOAD_2        : // fall through
        case LLOAD_3        : loadLocal(opcode - LLOAD_0, Kind.Long); break;
        case FLOAD_0        : // fall through
        case FLOAD_1        : // fall through
        case FLOAD_2        : // fall through
        case FLOAD_3        : loadLocal(opcode - FLOAD_0, Kind.Float); break;
        case DLOAD_0        : // fall through
        case DLOAD_1        : // fall through
        case DLOAD_2        : // fall through
        case DLOAD_3        : loadLocal(opcode - DLOAD_0, Kind.Double); break;
        case ALOAD_0        : // fall through
        case ALOAD_1        : // fall through
        case ALOAD_2        : // fall through
        case ALOAD_3        : loadLocal(opcode - ALOAD_0, Kind.Object); break;
        case IALOAD         : genLoadIndexed(Kind.Int   ); break;
        case LALOAD         : genLoadIndexed(Kind.Long  ); break;
        case FALOAD         : genLoadIndexed(Kind.Float ); break;
        case DALOAD         : genLoadIndexed(Kind.Double); break;
        case AALOAD         : genLoadIndexed(Kind.Object); break;
        case BALOAD         : genLoadIndexed(Kind.Byte  ); break;
        case CALOAD         : genLoadIndexed(Kind.Char  ); break;
        case SALOAD         : genLoadIndexed(Kind.Short ); break;
        case ISTORE         : storeLocal(Kind.Int, stream.readLocalIndex()); break;
        case LSTORE         : storeLocal(Kind.Long, stream.readLocalIndex()); break;
        case FSTORE         : storeLocal(Kind.Float, stream.readLocalIndex()); break;
        case DSTORE         : storeLocal(Kind.Double, stream.readLocalIndex()); break;
        case ASTORE         : storeLocal(Kind.Object, stream.readLocalIndex()); break;
        case ISTORE_0       : // fall through
        case ISTORE_1       : // fall through
        case ISTORE_2       : // fall through
        case ISTORE_3       : storeLocal(Kind.Int, opcode - ISTORE_0); break;
        case LSTORE_0       : // fall through
        case LSTORE_1       : // fall through
        case LSTORE_2       : // fall through
        case LSTORE_3       : storeLocal(Kind.Long, opcode - LSTORE_0); break;
        case FSTORE_0       : // fall through
        case FSTORE_1       : // fall through
        case FSTORE_2       : // fall through
        case FSTORE_3       : storeLocal(Kind.Float, opcode - FSTORE_0); break;
        case DSTORE_0       : // fall through
        case DSTORE_1       : // fall through
        case DSTORE_2       : // fall through
        case DSTORE_3       : storeLocal(Kind.Double, opcode - DSTORE_0); break;
        case ASTORE_0       : // fall through
        case ASTORE_1       : // fall through
        case ASTORE_2       : // fall through
        case ASTORE_3       : storeLocal(Kind.Object, opcode - ASTORE_0); break;
        case IASTORE        : genStoreIndexed(Kind.Int   ); break;
        case LASTORE        : genStoreIndexed(Kind.Long  ); break;
        case FASTORE        : genStoreIndexed(Kind.Float ); break;
        case DASTORE        : genStoreIndexed(Kind.Double); break;
        case AASTORE        : genStoreIndexed(Kind.Object); break;
        case BASTORE        : genStoreIndexed(Kind.Byte  ); break;
        case CASTORE        : genStoreIndexed(Kind.Char  ); break;
        case SASTORE        : genStoreIndexed(Kind.Short ); break;
        case POP            : // fall through
        case POP2           : // fall through
        case DUP            : // fall through
        case DUP_X1         : // fall through
        case DUP_X2         : // fall through
        case DUP2           : // fall through
        case DUP2_X1        : // fall through
        case DUP2_X2        : // fall through
        case SWAP           : stackOp(opcode); break;
        case IADD           : // fall through
        case ISUB           : // fall through
        case IMUL           : genArithmeticOp(Kind.Int, opcode); break;
        case IDIV           : // fall through
        case IREM           : genIntegerDivOp(Kind.Int, opcode); break;
        case LADD           : // fall through
        case LSUB           : // fall through
        case LMUL           : genArithmeticOp(Kind.Long, opcode); break;
        case LDIV           : // fall through
        case LREM           : genIntegerDivOp(Kind.Long, opcode); break;
        case FADD           : // fall through
        case FSUB           : // fall through
        case FMUL           : // fall through
        case FDIV           : // fall through
        case FREM           : genArithmeticOp(Kind.Float, opcode); break;
        case DADD           : // fall through
        case DSUB           : // fall through
        case DMUL           : // fall through
        case DDIV           : // fall through
        case DREM           : genArithmeticOp(Kind.Double, opcode); break;
        case INEG           : genNegateOp(Kind.Int); break;
        case LNEG           : genNegateOp(Kind.Long); break;
        case FNEG           : genNegateOp(Kind.Float); break;
        case DNEG           : genNegateOp(Kind.Double); break;
        case ISHL           : // fall through
        case ISHR           : // fall through
        case IUSHR          : genShiftOp(Kind.Int, opcode); break;
        case IAND           : // fall through
        case IOR            : // fall through
        case IXOR           : genLogicOp(Kind.Int, opcode); break;
        case LSHL           : // fall through
        case LSHR           : // fall through
        case LUSHR          : genShiftOp(Kind.Long, opcode); break;
        case LAND           : // fall through
        case LOR            : // fall through
        case LXOR           : genLogicOp(Kind.Long, opcode); break;
        case IINC           : genIncrement(); break;
        case I2F            : genFloatConvert(FloatConvert.I2F, Kind.Int, Kind.Float); break;
        case I2D            : genFloatConvert(FloatConvert.I2D, Kind.Int, Kind.Double); break;
        case L2F            : genFloatConvert(FloatConvert.L2F, Kind.Long, Kind.Float); break;
        case L2D            : genFloatConvert(FloatConvert.L2D, Kind.Long, Kind.Double); break;
        case F2I            : genFloatConvert(FloatConvert.F2I, Kind.Float, Kind.Int); break;
        case F2L            : genFloatConvert(FloatConvert.F2L, Kind.Float, Kind.Long); break;
        case F2D            : genFloatConvert(FloatConvert.F2D, Kind.Float, Kind.Double); break;
        case D2I            : genFloatConvert(FloatConvert.D2I, Kind.Double, Kind.Int); break;
        case D2L            : genFloatConvert(FloatConvert.D2L, Kind.Double, Kind.Long); break;
        case D2F            : genFloatConvert(FloatConvert.D2F, Kind.Double, Kind.Float); break;
        case L2I            : genNarrow(Kind.Long, Kind.Int); break;
        case I2L            : genSignExtend(Kind.Int, Kind.Long); break;
        case I2B            : genSignExtend(Kind.Byte, Kind.Int); break;
        case I2S            : genSignExtend(Kind.Short, Kind.Int); break;
        case I2C            : genZeroExtend(Kind.Char, Kind.Int); break;
        case LCMP           : genCompareOp(Kind.Long, false); break;
        case FCMPL          : genCompareOp(Kind.Float, true); break;
        case FCMPG          : genCompareOp(Kind.Float, false); break;
        case DCMPL          : genCompareOp(Kind.Double, true); break;
        case DCMPG          : genCompareOp(Kind.Double, false); break;
        case IFEQ           : genIfZero(Condition.EQ); break;
        case IFNE           : genIfZero(Condition.NE); break;
        case IFLT           : genIfZero(Condition.LT); break;
        case IFGE           : genIfZero(Condition.GE); break;
        case IFGT           : genIfZero(Condition.GT); break;
        case IFLE           : genIfZero(Condition.LE); break;
        case IF_ICMPEQ      : genIfSame(Kind.Int, Condition.EQ); break;
        case IF_ICMPNE      : genIfSame(Kind.Int, Condition.NE); break;
        case IF_ICMPLT      : genIfSame(Kind.Int, Condition.LT); break;
        case IF_ICMPGE      : genIfSame(Kind.Int, Condition.GE); break;
        case IF_ICMPGT      : genIfSame(Kind.Int, Condition.GT); break;
        case IF_ICMPLE      : genIfSame(Kind.Int, Condition.LE); break;
        case IF_ACMPEQ      : genIfSame(Kind.Object, Condition.EQ); break;
        case IF_ACMPNE      : genIfSame(Kind.Object, Condition.NE); break;
        case GOTO           : genGoto(); break;
        case JSR            : genJsr(stream.readBranchDest()); break;
        case RET            : genRet(stream.readLocalIndex()); break;
        case TABLESWITCH    : genSwitch(new BytecodeTableSwitch(getStream(), bci())); break;
        case LOOKUPSWITCH   : genSwitch(new BytecodeLookupSwitch(getStream(), bci())); break;
        case IRETURN        : genReturn(frameState.ipop()); break;
        case LRETURN        : genReturn(frameState.lpop()); break;
        case FRETURN        : genReturn(frameState.fpop()); break;
        case DRETURN        : genReturn(frameState.dpop()); break;
        case ARETURN        : genReturn(frameState.apop()); break;
        case RETURN         : genReturn(null); break;
        case GETSTATIC      : cpi = stream.readCPI(); genGetStatic(lookupField(cpi, opcode)); break;
        case PUTSTATIC      : cpi = stream.readCPI(); genPutStatic(lookupField(cpi, opcode)); break;
        case GETFIELD       : cpi = stream.readCPI(); genGetField(lookupField(cpi, opcode)); break;
        case PUTFIELD       : cpi = stream.readCPI(); genPutField(lookupField(cpi, opcode)); break;
        case INVOKEVIRTUAL  : cpi = stream.readCPI(); genInvokeVirtual(lookupMethod(cpi, opcode)); break;
        case INVOKESPECIAL  : cpi = stream.readCPI(); genInvokeSpecial(lookupMethod(cpi, opcode)); break;
        case INVOKESTATIC   : cpi = stream.readCPI(); genInvokeStatic(lookupMethod(cpi, opcode)); break;
        case INVOKEINTERFACE: cpi = stream.readCPI(); genInvokeInterface(lookupMethod(cpi, opcode)); break;
        case INVOKEDYNAMIC  : cpi = stream.readCPI4(); genInvokeDynamic(lookupMethod(cpi, opcode)); break;
        case NEW            : genNewInstance(stream.readCPI()); break;
        case NEWARRAY       : genNewPrimitiveArray(stream.readLocalIndex()); break;
        case ANEWARRAY      : genNewObjectArray(stream.readCPI()); break;
        case ARRAYLENGTH    : genArrayLength(); break;
        case ATHROW         : genThrow(); break;
        case CHECKCAST      : genCheckCast(); break;
        case INSTANCEOF     : genInstanceOf(); break;
        case MONITORENTER   : genMonitorEnter(frameState.apop()); break;
        case MONITOREXIT    : genMonitorExit(frameState.apop(), null); break;
        case MULTIANEWARRAY : genNewMultiArray(stream.readCPI()); break;
        case IFNULL         : genIfNull(Condition.EQ); break;
        case IFNONNULL      : genIfNull(Condition.NE); break;
        case GOTO_W         : genGoto(); break;
        case JSR_W          : genJsr(stream.readBranchDest()); break;
        case BREAKPOINT:
            throw new BailoutException("concurrent setting of breakpoint");
        default:
            throw new BailoutException("Unsupported opcode " + opcode + " (" + nameOf(opcode) + ") [bci=" + bci + "]");
    }
    // @formatter:on
        // Checkstyle: resume
    }

    private void genArrayLength() {
        frameState.ipush(append(genArrayLength(frameState.apop())));
    }

    public ResolvedJavaMethod getMethod() {
        return method;
    }

    public F getFrameState() {
        return frameState;
    }

    protected final int traceLevel = Options.TraceBytecodeParserLevel.getValue();

    protected void traceInstruction(int bci, int opcode, boolean blockStart) {
        if (traceLevel >= TRACELEVEL_INSTRUCTIONS && Debug.isLogEnabled()) {
            StringBuilder sb = new StringBuilder(40);
            sb.append(blockStart ? '+' : '|');
            if (bci < 10) {
                sb.append("  ");
            } else if (bci < 100) {
                sb.append(' ');
            }
            sb.append(bci).append(": ").append(Bytecodes.nameOf(opcode));
            for (int i = bci + 1; i < stream.nextBCI(); ++i) {
                sb.append(' ').append(stream.readUByte(i));
            }
            if (!currentBlock.jsrScope.isEmpty()) {
                sb.append(' ').append(currentBlock.jsrScope);
            }
            Debug.log("%s", sb);
        }
    }

}
