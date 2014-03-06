/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.baseline;

import static com.oracle.graal.api.code.TypeCheckHints.*;
import static com.oracle.graal.api.meta.DeoptimizationAction.*;
import static com.oracle.graal.api.meta.DeoptimizationReason.*;
import static com.oracle.graal.bytecode.Bytecodes.*;
import static com.oracle.graal.phases.GraalOptions.*;
import static java.lang.reflect.Modifier.*;

import java.lang.reflect.*;
import java.util.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.api.meta.ProfilingInfo.TriState;
import com.oracle.graal.api.meta.ResolvedJavaType.Representation;
import com.oracle.graal.bytecode.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.java.*;
import com.oracle.graal.java.BciBlockMapping.Block;
import com.oracle.graal.java.BciBlockMapping.ExceptionDispatchBlock;
import com.oracle.graal.java.GraphBuilderPhase.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.nodes.calc.FloatConvertNode.FloatConvert;
import com.oracle.graal.nodes.extended.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.java.MethodCallTargetNode.InvokeKind;
import com.oracle.graal.nodes.type.*;
import com.oracle.graal.nodes.util.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.tiers.*;

/**
 * The {@code GraphBuilder} class parses the bytecode of a method and builds the IR graph.
 */
@SuppressWarnings("all")
public class BaselineCompiler {

    public BaselineCompiler(GraphBuilderConfiguration graphBuilderConfig, MetaAccessProvider metaAccess) {
        this.graphBuilderConfig = graphBuilderConfig;
        this.metaAccess = metaAccess;
    }

    private final MetaAccessProvider metaAccess;
    private ConstantPool constantPool;
    private ResolvedJavaMethod method;
    private int entryBCI;
    private ProfilingInfo profilingInfo;
    private BytecodeStream stream;           // the bytecode stream

    private Block currentBlock;

    private ValueNode methodSynchronizedObject;
    private ExceptionDispatchBlock unwindBlock;

    private final GraphBuilderConfiguration graphBuilderConfig;
    private Block[] loopHeaders;

    /**
     * Meters the number of actual bytecodes parsed.
     */
    public static final DebugMetric BytecodesParsed = Debug.metric("BytecodesParsed");

    protected ResolvedJavaMethod getMethod() {
        return method;
    }

    public LIR generate(ResolvedJavaMethod method, int entryBCI) {
        this.method = method;
        this.entryBCI = entryBCI;
        profilingInfo = method.getProfilingInfo();
        assert method.getCode() != null : "method must contain bytecodes: " + method;
        this.stream = new BytecodeStream(method.getCode());
        this.constantPool = method.getConstantPool();
        unwindBlock = null;
        methodSynchronizedObject = null;
        TTY.Filter filter = new TTY.Filter(PrintFilter.getValue(), method);
        try {
            build();
        } finally {
            filter.remove();
        }
        return null;
    }

    protected void build() {
        if (PrintProfilingInformation.getValue()) {
            TTY.println("Profiling info for " + MetaUtil.format("%H.%n(%p)", method));
            TTY.println(MetaUtil.indent(MetaUtil.profileToString(profilingInfo, method, CodeUtil.NEW_LINE), "  "));
        }

        Indent indent = Debug.logAndIndent("build graph for %s", method);

        // compute the block map, setup exception handlers and get the entrypoint(s)
        BciBlockMapping blockMap = BciBlockMapping.create(method);
        loopHeaders = blockMap.loopHeaders;

        if (isSynchronized(method.getModifiers())) {
            throw GraalInternalError.unimplemented("Handle synchronized methods");
        }

        // TODO: clear non live locals

        currentBlock = blockMap.startBlock;
        if (blockMap.startBlock.isLoopHeader) {
            throw GraalInternalError.unimplemented("Handle start block as loop header");
        }

        for (Block block : blockMap.blocks) {
            processBlock(block);
        }

        indent.outdent();
    }

    public BytecodeStream stream() {
        return stream;
    }

    public int bci() {
        return stream.currentBCI();
    }

    private void loadLocal(int index, Kind kind) {
        throw GraalInternalError.unimplemented();
    }

    private void storeLocal(Kind kind, int index) {
        throw GraalInternalError.unimplemented();
    }

    /**
     * @param type the unresolved type of the constant
     */
    protected void handleUnresolvedLoadConstant(JavaType type) {
        throw GraalInternalError.unimplemented();
    }

    /**
     * @param type the unresolved type of the type check
     * @param object the object value whose type is being checked against {@code type}
     */
    protected void handleUnresolvedCheckCast(JavaType type, ValueNode object) {
        throw GraalInternalError.unimplemented();
    }

    /**
     * @param type the unresolved type of the type check
     * @param object the object value whose type is being checked against {@code type}
     */
    protected void handleUnresolvedInstanceOf(JavaType type, ValueNode object) {
        throw GraalInternalError.unimplemented();
    }

    /**
     * @param type the type being instantiated
     */
    protected void handleUnresolvedNewInstance(JavaType type) {
        throw GraalInternalError.unimplemented();
    }

    /**
     * @param type the type of the array being instantiated
     * @param length the length of the array
     */
    protected void handleUnresolvedNewObjectArray(JavaType type, ValueNode length) {
        throw GraalInternalError.unimplemented();
    }

    /**
     * @param type the type being instantiated
     * @param dims the dimensions for the multi-array
     */
    protected void handleUnresolvedNewMultiArray(JavaType type, ValueNode[] dims) {
        throw GraalInternalError.unimplemented();
    }

    /**
     * @param field the unresolved field
     * @param receiver the object containing the field or {@code null} if {@code field} is static
     */
    protected void handleUnresolvedLoadField(JavaField field, ValueNode receiver) {
        throw GraalInternalError.unimplemented();
    }

    /**
     * @param field the unresolved field
     * @param value the value being stored to the field
     * @param receiver the object containing the field or {@code null} if {@code field} is static
     */
    protected void handleUnresolvedStoreField(JavaField field, ValueNode value, ValueNode receiver) {
        throw GraalInternalError.unimplemented();
    }

    /**
     * @param representation
     * @param type
     */
    protected void handleUnresolvedExceptionType(Representation representation, JavaType type) {
        throw GraalInternalError.unimplemented();
    }

    protected void handleUnresolvedInvoke(JavaMethod javaMethod, InvokeKind invokeKind) {
        throw GraalInternalError.unimplemented();
    }

    private DispatchBeginNode handleException(ValueNode exceptionObject, int bci) {
        throw GraalInternalError.unimplemented();
    }

    private void genLoadConstant(int cpi, int opcode) {
        throw GraalInternalError.unimplemented();
    }

    private void genLoadIndexed(Kind kind) {
        throw GraalInternalError.unimplemented();
    }

    private void genStoreIndexed(Kind kind) {
        throw GraalInternalError.unimplemented();
    }

    private void stackOp(int opcode) {
        throw GraalInternalError.unimplemented();
    }

    private void genArithmeticOp(Kind result, int opcode) {
        throw GraalInternalError.unimplemented();
    }

    private void genIntegerDivOp(Kind result, int opcode) {
        throw GraalInternalError.unimplemented();
    }

    private void genNegateOp(Kind kind) {
        throw GraalInternalError.unimplemented();
    }

    private void genShiftOp(Kind kind, int opcode) {
        throw GraalInternalError.unimplemented();
    }

    private void genLogicOp(Kind kind, int opcode) {
        throw GraalInternalError.unimplemented();
    }

    private void genCompareOp(Kind kind, boolean isUnorderedLess) {
        throw GraalInternalError.unimplemented();
    }

    private void genFloatConvert(FloatConvert op, Kind from, Kind to) {
        throw GraalInternalError.unimplemented();
    }

    private void genSignExtend(Kind from, Kind to) {
        throw GraalInternalError.unimplemented();
    }

    private void genZeroExtend(Kind from, Kind to) {
        throw GraalInternalError.unimplemented();
    }

    private void genNarrow(Kind from, Kind to) {
        throw GraalInternalError.unimplemented();
    }

    private void genIncrement() {
        throw GraalInternalError.unimplemented();
    }

    private void genGoto() {
        throw GraalInternalError.unimplemented();
    }

    private void genIfZero(Condition cond) {
        throw GraalInternalError.unimplemented();
    }

    private void genIfNull(Condition cond) {
        throw GraalInternalError.unimplemented();
    }

    private void genIfSame(Kind kind, Condition cond) {
        throw GraalInternalError.unimplemented();
    }

    private void genThrow() {
        throw GraalInternalError.unimplemented();
    }

    private JavaType lookupType(int cpi, int bytecode) {
        eagerResolvingForSnippets(cpi, bytecode);
        JavaType result = constantPool.lookupType(cpi, bytecode);
        assert !graphBuilderConfig.unresolvedIsError() || result instanceof ResolvedJavaType;
        return result;
    }

    private JavaMethod lookupMethod(int cpi, int opcode) {
        eagerResolvingForSnippets(cpi, opcode);
        JavaMethod result = constantPool.lookupMethod(cpi, opcode);
        /*
         * assert !graphBuilderConfig.unresolvedIsError() || ((result instanceof ResolvedJavaMethod)
         * && ((ResolvedJavaMethod) result).getDeclaringClass().isInitialized()) : result;
         */
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
        assert !graphBuilderConfig.eagerResolving() || !(result instanceof JavaType) || (result instanceof ResolvedJavaType);
        return result;
    }

    private void eagerResolvingForSnippets(int cpi, int bytecode) {
        if (graphBuilderConfig.eagerResolving()) {
            constantPool.loadReferencedType(cpi, bytecode);
        }
    }

    private JavaTypeProfile getProfileForTypeCheck(ResolvedJavaType type) {
        if (!canHaveSubtype(type)) {
            return null;
        } else {
            return profilingInfo.getTypeProfile(bci());
        }
    }

    private void genCheckCast() {
        throw GraalInternalError.unimplemented();
    }

    private void genInstanceOf() {
        throw GraalInternalError.unimplemented();
    }

    void genNewInstance(int cpi) {
        throw GraalInternalError.unimplemented();
    }

    protected NewInstanceNode createNewInstance(ResolvedJavaType type, boolean fillContents) {
        return new NewInstanceNode(type, fillContents);
    }

    private void genNewPrimitiveArray(int typeCode) {
        throw GraalInternalError.unimplemented();
    }

    private void genNewObjectArray(int cpi) {
        throw GraalInternalError.unimplemented();
    }

    private void genNewMultiArray(int cpi) {
        throw GraalInternalError.unimplemented();
    }

    private void genGetField(JavaField field) {
        throw GraalInternalError.unimplemented();
    }

    private void genPutField(JavaField field) {
        throw GraalInternalError.unimplemented();
    }

    private void genGetStatic(JavaField field) {
        throw GraalInternalError.unimplemented();
    }

    private void genPutStatic(JavaField field) {
        throw GraalInternalError.unimplemented();
    }

    private void genInvokeStatic(JavaMethod target) {
        throw GraalInternalError.unimplemented();
    }

    private void genInvokeInterface(JavaMethod target) {
        throw GraalInternalError.unimplemented();
    }

    private void genInvokeDynamic(JavaMethod target) {
        throw GraalInternalError.unimplemented();
    }

    private void genInvokeVirtual(JavaMethod target) {
        throw GraalInternalError.unimplemented();
    }

    private void genInvokeSpecial(JavaMethod target) {
        throw GraalInternalError.unimplemented();
    }

    private void genJsr(int dest) {
        throw GraalInternalError.unimplemented();
    }

    private void genRet(int localIndex) {
        throw GraalInternalError.unimplemented();
    }

    private void genSwitch(BytecodeSwitch bs) {
        throw GraalInternalError.unimplemented();
    }

    private void processBlock(Block block) {
        Indent indent = Debug.logAndIndent("Parsing block %s  firstInstruction: %s  loopHeader: %b", block, block.firstInstruction, block.isLoopHeader);
        currentBlock = block;
        iterateBytecodesForBlock(block);
        indent.outdent();
    }

    private void createExceptionDispatch(ExceptionDispatchBlock block) {
        throw GraalInternalError.unimplemented();
    }

    private void iterateBytecodesForBlock(Block block) {

        int endBCI = stream.endBCI();

        stream.setBCI(block.startBci);
        int bci = block.startBci;
        BytecodesParsed.add(block.endBci - bci);

        while (bci < endBCI) {

            // read the opcode
            int opcode = stream.currentBC();
            traceInstruction(bci, opcode, bci == block.startBci);
            if (bci == entryBCI) {
                throw GraalInternalError.unimplemented();
            }
            processBytecode(bci, opcode);

            stream.next();
            bci = stream.currentBCI();
        }
    }

    private void processBytecode(int bci, int opcode) {
        int cpi;

        // Checkstyle: stop
        // @formatter:off
        switch (opcode) {
            case NOP            : /* nothing to do */ break;
//            case ACONST_NULL    : frameState.apush(appendConstant(Constant.NULL_OBJECT)); break;
//            case ICONST_M1      : frameState.ipush(appendConstant(Constant.INT_MINUS_1)); break;
//            case ICONST_0       : frameState.ipush(appendConstant(Constant.INT_0)); break;
//            case ICONST_1       : frameState.ipush(appendConstant(Constant.INT_1)); break;
//            case ICONST_2       : frameState.ipush(appendConstant(Constant.INT_2)); break;
//            case ICONST_3       : frameState.ipush(appendConstant(Constant.INT_3)); break;
//            case ICONST_4       : frameState.ipush(appendConstant(Constant.INT_4)); break;
//            case ICONST_5       : frameState.ipush(appendConstant(Constant.INT_5)); break;
//            case LCONST_0       : frameState.lpush(appendConstant(Constant.LONG_0)); break;
//            case LCONST_1       : frameState.lpush(appendConstant(Constant.LONG_1)); break;
//            case FCONST_0       : frameState.fpush(appendConstant(Constant.FLOAT_0)); break;
//            case FCONST_1       : frameState.fpush(appendConstant(Constant.FLOAT_1)); break;
//            case FCONST_2       : frameState.fpush(appendConstant(Constant.FLOAT_2)); break;
//            case DCONST_0       : frameState.dpush(appendConstant(Constant.DOUBLE_0)); break;
//            case DCONST_1       : frameState.dpush(appendConstant(Constant.DOUBLE_1)); break;
//            case BIPUSH         : frameState.ipush(appendConstant(Constant.forInt(stream.readByte()))); break;
//            case SIPUSH         : frameState.ipush(appendConstant(Constant.forInt(stream.readShort()))); break;
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
            case TABLESWITCH    : genSwitch(new BytecodeTableSwitch(stream(), bci())); break;
            case LOOKUPSWITCH   : genSwitch(new BytecodeLookupSwitch(stream(), bci())); break;
//            case IRETURN        : genReturn(frameState.ipop()); break;
//            case LRETURN        : genReturn(frameState.lpop()); break;
//            case FRETURN        : genReturn(frameState.fpop()); break;
//            case DRETURN        : genReturn(frameState.dpop()); break;
//            case ARETURN        : genReturn(frameState.apop()); break;
//            case RETURN         : genReturn(null); break;
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
//            case MONITORENTER   : genMonitorEnter(frameState.apop()); break;
//            case MONITOREXIT    : genMonitorExit(frameState.apop(), null); break;
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

    private void traceInstruction(int bci, int opcode, boolean blockStart) {
        if (Debug.isLogEnabled()) {
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
            Debug.log(sb.toString());
        }
    }

    private void genArrayLength() {
        throw GraalInternalError.unimplemented();
    }
}
