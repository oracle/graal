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
import static com.oracle.graal.api.meta.DeoptimizationReason.*;
import static com.oracle.graal.bytecode.Bytecodes.*;
import static com.oracle.graal.java.AbstractBytecodeParser.Options.*;

import java.util.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.api.replacements.*;
import com.oracle.graal.bytecode.*;
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.compiler.common.calc.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.graphbuilderconf.*;
import com.oracle.graal.graphbuilderconf.GraphBuilderContext.*;
import com.oracle.graal.graphbuilderconf.GraphBuilderPlugin.*;
import com.oracle.graal.java.BciBlockMapping.BciBlock;
import com.oracle.graal.java.GraphBuilderPhase.Instance.BytecodeParser;
import com.oracle.graal.nodes.*;
import com.oracle.graal.options.*;
import com.oracle.graal.phases.*;

public abstract class AbstractBytecodeParser {

    public static class Options {
        // @formatter:off
        @Option(help = "The trace level for the bytecode parser used when building a graph from bytecode", type = OptionType.Debug)
        public static final OptionValue<Integer> TraceBytecodeParserLevel = new OptionValue<>(0);

        @Option(help = "Inlines trivial methods during bytecode parsing.", type = OptionType.Expert)
        public static final StableOptionValue<Boolean> InlineDuringParsing = new StableOptionValue<>(false);

        @Option(help = "Traces inlining performed during bytecode parsing.", type = OptionType.Debug)
        public static final StableOptionValue<Boolean> TraceInlineDuringParsing = new StableOptionValue<>(false);

        @Option(help = "Traces use of plugins during bytecode parsing.", type = OptionType.Debug)
        public static final StableOptionValue<Boolean> TraceParserPlugins = new StableOptionValue<>(false);

        @Option(help = "Maximum depth when inlining during bytecode parsing.", type = OptionType.Debug)
        public static final StableOptionValue<Integer> InlineDuringParsingMaxDepth = new StableOptionValue<>(10);

        @Option(help = "Dump graphs after non-trivial changes during bytecode parsing.", type = OptionType.Debug)
        public static final StableOptionValue<Boolean> DumpDuringGraphBuilding = new StableOptionValue<>(false);

        @Option(help = "Max number of loop explosions per method.", type = OptionType.Debug)
        public static final OptionValue<Integer> MaximumLoopExplosionCount = new OptionValue<>(10000);

        @Option(help = "Do not bail out but throw an exception on failed loop explosion.", type = OptionType.Debug)
        public static final OptionValue<Boolean> FailedLoopExplosionIsFatal = new OptionValue<>(false);

        // @formatter:on
    }

    /**
     * Information about a substitute method being parsed in lieu of an original method. This can
     * happen when a call to a {@link MethodSubstitution} is encountered or the root of compilation
     * is a {@link MethodSubstitution} or a snippet.
     */
    static class ReplacementContext implements Replacement {
        /**
         * The method being replaced.
         */
        final ResolvedJavaMethod method;

        /**
         * The replacement method.
         */
        final ResolvedJavaMethod replacement;

        public ReplacementContext(ResolvedJavaMethod method, ResolvedJavaMethod substitute) {
            this.method = method;
            this.replacement = substitute;
        }

        public ResolvedJavaMethod getOriginalMethod() {
            return method;
        }

        public ResolvedJavaMethod getReplacementMethod() {
            return replacement;
        }

        public boolean isIntrinsic() {
            return false;
        }

        /**
         * Determines if a call within the compilation scope of a replacement represents a call to
         * the original method.
         */
        public boolean isCallToOriginal(ResolvedJavaMethod targetMethod) {
            return method.equals(targetMethod) || replacement.equals(targetMethod);
        }

        IntrinsicContext asIntrinsic() {
            return null;
        }
    }

    /**
     * Context for a replacement being inlined as a compiler intrinsic. Deoptimization within a
     * compiler intrinsic must replay the intrinsified call. This context object retains the
     * information required to build a frame state denoting the JVM state just before the
     * intrinsified call.
     */
    static class IntrinsicContext extends ReplacementContext {

        /**
         * The arguments to the intrinsified invocation.
         */
        private final ValueNode[] invokeArgs;

        /**
         * The BCI of the intrinsified invocation.
         */
        private final int invokeBci;

        private FrameState invokeStateBefore;
        private FrameState invokeStateDuring;

        public IntrinsicContext(ResolvedJavaMethod method, ResolvedJavaMethod substitute, ValueNode[] invokeArgs, int invokeBci) {
            super(method, substitute);
            this.invokeArgs = invokeArgs;
            this.invokeBci = invokeBci;
        }

        @Override
        public boolean isIntrinsic() {
            return true;
        }

        /**
         * Gets the frame state that will restart the interpreter just before the intrinsified
         * invocation.
         */
        public FrameState getInvokeStateBefore(BytecodeParser parent) {
            assert !parent.parsingReplacement() || parent.replacementContext instanceof IntrinsicContext;
            if (invokeStateDuring == null) {
                assert invokeStateBefore == null;
                // Find the ancestor calling the replaced method
                BytecodeParser ancestor = parent;
                while (ancestor.parsingReplacement()) {
                    ancestor = ancestor.getParent();
                }
                invokeStateDuring = ancestor.getFrameState().create(ancestor.bci(), ancestor.getParent(), true);
                invokeStateBefore = invokeStateDuring.duplicateModifiedBeforeCall(invokeBci, Kind.Void, invokeArgs);
            }
            return invokeStateBefore;
        }

        public FrameState getInvokeStateDuring() {
            assert invokeStateDuring != null : "must only be called after getInvokeStateBefore()";
            return invokeStateDuring;
        }

        @Override
        IntrinsicContext asIntrinsic() {
            return this;
        }
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

    protected HIRFrameStateBuilder frameState;
    protected BciBlock currentBlock;

    protected final BytecodeStream stream;
    protected final GraphBuilderConfiguration graphBuilderConfig;
    protected final ResolvedJavaMethod method;
    protected final ProfilingInfo profilingInfo;
    protected final OptimisticOptimizations optimisticOpts;
    protected final ConstantPool constantPool;
    protected final MetaAccessProvider metaAccess;

    protected final ReplacementContext replacementContext;

    /**
     * Meters the number of actual bytecodes parsed.
     */
    public static final DebugMetric BytecodesParsed = Debug.metric("BytecodesParsed");

    public AbstractBytecodeParser(MetaAccessProvider metaAccess, ResolvedJavaMethod method, GraphBuilderConfiguration graphBuilderConfig, OptimisticOptimizations optimisticOpts,
                    ReplacementContext replacementContext) {
        this.graphBuilderConfig = graphBuilderConfig;
        this.optimisticOpts = optimisticOpts;
        this.metaAccess = metaAccess;
        this.stream = new BytecodeStream(method.getCode());
        this.profilingInfo = (graphBuilderConfig.getUseProfiling() ? method.getProfilingInfo() : null);
        this.constantPool = method.getConstantPool();
        this.method = method;
        this.replacementContext = replacementContext;
        assert metaAccess != null;
    }

    public void setCurrentFrameState(HIRFrameStateBuilder frameState) {
        this.frameState = frameState;
    }

    protected final BytecodeStream getStream() {
        return stream;
    }

    public int bci() {
        return stream.currentBCI();
    }

    public void loadLocal(int index, Kind kind) {
        frameState.push(kind, frameState.loadLocal(index));
    }

    public void storeLocal(Kind kind, int index) {
        ValueNode value;
        if (kind == Kind.Object) {
            value = frameState.xpop();
            // astore and astore_<n> may be used to store a returnAddress (jsr)
            assert parsingReplacement() || (value.getKind() == Kind.Object || value.getKind() == Kind.Int) : value + ":" + value.getKind();
        } else {
            value = frameState.pop(kind);
        }
        frameState.storeLocal(index, value, kind);
    }

    /**
     * @param type the unresolved type of the constant
     */
    protected abstract void handleUnresolvedLoadConstant(JavaType type);

    /**
     * @param type the unresolved type of the type check
     * @param object the object value whose type is being checked against {@code type}
     */
    protected abstract void handleUnresolvedCheckCast(JavaType type, ValueNode object);

    /**
     * @param type the unresolved type of the type check
     * @param object the object value whose type is being checked against {@code type}
     */
    protected abstract void handleUnresolvedInstanceOf(JavaType type, ValueNode object);

    /**
     * @param type the type being instantiated
     */
    protected abstract void handleUnresolvedNewInstance(JavaType type);

    /**
     * @param type the type of the array being instantiated
     * @param length the length of the array
     */
    protected abstract void handleUnresolvedNewObjectArray(JavaType type, ValueNode length);

    /**
     * @param type the type being instantiated
     * @param dims the dimensions for the multi-array
     */
    protected abstract void handleUnresolvedNewMultiArray(JavaType type, List<ValueNode> dims);

    /**
     * @param field the unresolved field
     * @param receiver the object containing the field or {@code null} if {@code field} is static
     */
    protected abstract void handleUnresolvedLoadField(JavaField field, ValueNode receiver);

    /**
     * @param field the unresolved field
     * @param value the value being stored to the field
     * @param receiver the object containing the field or {@code null} if {@code field} is static
     */
    protected abstract void handleUnresolvedStoreField(JavaField field, ValueNode value, ValueNode receiver);

    /**
     * @param type
     */
    protected abstract void handleUnresolvedExceptionType(JavaType type);

    // protected abstract void handleUnresolvedInvoke(JavaMethod javaMethod, InvokeKind invokeKind);

    // protected abstract DispatchBeginNode handleException(ValueNode exceptionObject, int bci);

    private void genLoadConstant(int cpi, int opcode) {
        Object con = lookupConstant(cpi, opcode);

        if (con instanceof JavaType) {
            // this is a load of class constant which might be unresolved
            JavaType type = (JavaType) con;
            if (type instanceof ResolvedJavaType) {
                frameState.push(Kind.Object, appendConstant(((ResolvedJavaType) type).getJavaClass()));
            } else {
                handleUnresolvedLoadConstant(type);
            }
        } else if (con instanceof JavaConstant) {
            JavaConstant constant = (JavaConstant) con;
            frameState.push(constant.getKind().getStackKind(), appendConstant(constant));
        } else {
            throw new Error("lookupConstant returned an object of incorrect type");
        }
    }

    protected abstract ValueNode genLoadIndexed(ValueNode index, ValueNode array, Kind kind);

    private void genLoadIndexed(Kind kind) {

        emitExplicitExceptions(frameState.peek(1), frameState.peek(0));

        ValueNode index = frameState.ipop();
        ValueNode array = frameState.apop();
        if (!tryLoadIndexedPlugin(kind, index, array)) {
            frameState.push(kind.getStackKind(), append(genLoadIndexed(array, index, kind)));
        }
    }

    protected abstract void traceWithContext(String format, Object... args);

    protected boolean tryLoadIndexedPlugin(Kind kind, ValueNode index, ValueNode array) {
        LoadIndexedPlugin loadIndexedPlugin = graphBuilderConfig.getPlugins().getLoadIndexedPlugin();
        if (loadIndexedPlugin != null && loadIndexedPlugin.apply((GraphBuilderContext) this, array, index, kind)) {
            if (TraceParserPlugins.getValue()) {
                traceWithContext("used load indexed plugin");
            }
            return true;
        } else {
            return false;
        }
    }

    protected abstract ValueNode genStoreIndexed(ValueNode array, ValueNode index, Kind kind, ValueNode value);

    private void genStoreIndexed(Kind kind) {
        emitExplicitExceptions(frameState.peek(2), frameState.peek(1));

        ValueNode value = frameState.pop(kind.getStackKind());
        ValueNode index = frameState.ipop();
        ValueNode array = frameState.apop();
        append(genStoreIndexed(array, index, kind, value));
    }

    private void stackOp(int opcode) {
        switch (opcode) {
            case DUP_X1: {
                ValueNode w1 = frameState.xpop();
                ValueNode w2 = frameState.xpop();
                frameState.xpush(w1);
                frameState.xpush(w2);
                frameState.xpush(w1);
                break;
            }
            case DUP_X2: {
                ValueNode w1 = frameState.xpop();
                ValueNode w2 = frameState.xpop();
                ValueNode w3 = frameState.xpop();
                frameState.xpush(w1);
                frameState.xpush(w3);
                frameState.xpush(w2);
                frameState.xpush(w1);
                break;
            }
            case DUP2: {
                ValueNode w1 = frameState.xpop();
                ValueNode w2 = frameState.xpop();
                frameState.xpush(w2);
                frameState.xpush(w1);
                frameState.xpush(w2);
                frameState.xpush(w1);
                break;
            }
            case DUP2_X1: {
                ValueNode w1 = frameState.xpop();
                ValueNode w2 = frameState.xpop();
                ValueNode w3 = frameState.xpop();
                frameState.xpush(w2);
                frameState.xpush(w1);
                frameState.xpush(w3);
                frameState.xpush(w2);
                frameState.xpush(w1);
                break;
            }
            case DUP2_X2: {
                ValueNode w1 = frameState.xpop();
                ValueNode w2 = frameState.xpop();
                ValueNode w3 = frameState.xpop();
                ValueNode w4 = frameState.xpop();
                frameState.xpush(w2);
                frameState.xpush(w1);
                frameState.xpush(w4);
                frameState.xpush(w3);
                frameState.xpush(w2);
                frameState.xpush(w1);
                break;
            }
            case SWAP: {
                ValueNode w1 = frameState.xpop();
                ValueNode w2 = frameState.xpop();
                frameState.xpush(w1);
                frameState.xpush(w2);
                break;
            }
            default:
                throw GraalInternalError.shouldNotReachHere();
        }
    }

    protected abstract ValueNode genIntegerAdd(Kind kind, ValueNode x, ValueNode y);

    protected abstract ValueNode genIntegerSub(Kind kind, ValueNode x, ValueNode y);

    protected abstract ValueNode genIntegerMul(Kind kind, ValueNode x, ValueNode y);

    protected abstract ValueNode genFloatAdd(Kind kind, ValueNode x, ValueNode y, boolean isStrictFP);

    protected abstract ValueNode genFloatSub(Kind kind, ValueNode x, ValueNode y, boolean isStrictFP);

    protected abstract ValueNode genFloatMul(Kind kind, ValueNode x, ValueNode y, boolean isStrictFP);

    protected abstract ValueNode genFloatDiv(Kind kind, ValueNode x, ValueNode y, boolean isStrictFP);

    protected abstract ValueNode genFloatRem(Kind kind, ValueNode x, ValueNode y, boolean isStrictFP);

    private void genArithmeticOp(Kind result, int opcode) {
        ValueNode y = frameState.pop(result);
        ValueNode x = frameState.pop(result);
        boolean isStrictFP = method.isStrict();
        ValueNode v;
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

    protected abstract ValueNode genIntegerDiv(Kind kind, ValueNode x, ValueNode y);

    protected abstract ValueNode genIntegerRem(Kind kind, ValueNode x, ValueNode y);

    private void genIntegerDivOp(Kind result, int opcode) {
        ValueNode y = frameState.pop(result);
        ValueNode x = frameState.pop(result);
        ValueNode v;
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

    protected abstract ValueNode genNegateOp(ValueNode x);

    private void genNegateOp(Kind kind) {
        frameState.push(kind, append(genNegateOp(frameState.pop(kind))));
    }

    protected abstract ValueNode genLeftShift(Kind kind, ValueNode x, ValueNode y);

    protected abstract ValueNode genRightShift(Kind kind, ValueNode x, ValueNode y);

    protected abstract ValueNode genUnsignedRightShift(Kind kind, ValueNode x, ValueNode y);

    private void genShiftOp(Kind kind, int opcode) {
        ValueNode s = frameState.ipop();
        ValueNode x = frameState.pop(kind);
        ValueNode v;
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

    protected abstract ValueNode genAnd(Kind kind, ValueNode x, ValueNode y);

    protected abstract ValueNode genOr(Kind kind, ValueNode x, ValueNode y);

    protected abstract ValueNode genXor(Kind kind, ValueNode x, ValueNode y);

    private void genLogicOp(Kind kind, int opcode) {
        ValueNode y = frameState.pop(kind);
        ValueNode x = frameState.pop(kind);
        ValueNode v;
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

    protected abstract ValueNode genNormalizeCompare(ValueNode x, ValueNode y, boolean isUnorderedLess);

    private void genCompareOp(Kind kind, boolean isUnorderedLess) {
        ValueNode y = frameState.pop(kind);
        ValueNode x = frameState.pop(kind);
        frameState.ipush(append(genNormalizeCompare(x, y, isUnorderedLess)));
    }

    protected abstract ValueNode genFloatConvert(FloatConvert op, ValueNode input);

    private void genFloatConvert(FloatConvert op, Kind from, Kind to) {
        ValueNode input = frameState.pop(from.getStackKind());
        frameState.push(to.getStackKind(), append(genFloatConvert(op, input)));
    }

    protected abstract ValueNode genNarrow(ValueNode input, int bitCount);

    protected abstract ValueNode genSignExtend(ValueNode input, int bitCount);

    protected abstract ValueNode genZeroExtend(ValueNode input, int bitCount);

    private void genSignExtend(Kind from, Kind to) {
        ValueNode input = frameState.pop(from.getStackKind());
        if (from != from.getStackKind()) {
            input = append(genNarrow(input, from.getBitCount()));
        }
        frameState.push(to.getStackKind(), append(genSignExtend(input, to.getBitCount())));
    }

    private void genZeroExtend(Kind from, Kind to) {
        ValueNode input = frameState.pop(from.getStackKind());
        if (from != from.getStackKind()) {
            input = append(genNarrow(input, from.getBitCount()));
        }
        frameState.push(to.getStackKind(), append(genZeroExtend(input, to.getBitCount())));
    }

    private void genNarrow(Kind from, Kind to) {
        ValueNode input = frameState.pop(from.getStackKind());
        frameState.push(to.getStackKind(), append(genNarrow(input, to.getBitCount())));
    }

    private void genIncrement() {
        int index = getStream().readLocalIndex();
        int delta = getStream().readIncrement();
        ValueNode x = frameState.loadLocal(index);
        ValueNode y = appendConstant(JavaConstant.forInt(delta));
        frameState.storeLocal(index, append(genIntegerAdd(Kind.Int, x, y)));
    }

    protected abstract void genGoto();

    protected abstract ValueNode genObjectEquals(ValueNode x, ValueNode y);

    protected abstract ValueNode genIntegerEquals(ValueNode x, ValueNode y);

    protected abstract ValueNode genIntegerLessThan(ValueNode x, ValueNode y);

    protected abstract ValueNode genUnique(ValueNode x);

    protected abstract void genIf(ValueNode x, Condition cond, ValueNode y);

    private void genIfZero(Condition cond) {
        ValueNode y = appendConstant(JavaConstant.INT_0);
        ValueNode x = frameState.ipop();
        genIf(x, cond, y);
    }

    private void genIfNull(Condition cond) {
        ValueNode y = appendConstant(JavaConstant.NULL_POINTER);
        ValueNode x = frameState.apop();
        genIf(x, cond, y);
    }

    private void genIfSame(Kind kind, Condition cond) {
        ValueNode y = frameState.pop(kind);
        ValueNode x = frameState.pop(kind);
        genIf(x, cond, y);
    }

    protected abstract void genThrow();

    protected JavaType lookupType(int cpi, int bytecode) {
        maybeEagerlyResolve(cpi, bytecode);
        JavaType result = constantPool.lookupType(cpi, bytecode);
        assert !graphBuilderConfig.unresolvedIsError() || result instanceof ResolvedJavaType;
        return result;
    }

    private JavaMethod lookupMethod(int cpi, int opcode) {
        maybeEagerlyResolve(cpi, opcode);
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
        maybeEagerlyResolve(cpi, opcode);
        JavaField result = constantPool.lookupField(cpi, opcode);
        assert !graphBuilderConfig.unresolvedIsError() || (result instanceof ResolvedJavaField && ((ResolvedJavaField) result).getDeclaringClass().isInitialized()) : result;
        return result;
    }

    private Object lookupConstant(int cpi, int opcode) {
        maybeEagerlyResolve(cpi, opcode);
        Object result = constantPool.lookupConstant(cpi);
        assert !graphBuilderConfig.eagerResolving() || !(result instanceof JavaType) || (result instanceof ResolvedJavaType) : result;
        return result;
    }

    private void maybeEagerlyResolve(int cpi, int bytecode) {
        if (graphBuilderConfig.eagerResolving() || replacementContext instanceof IntrinsicContext) {
            constantPool.loadReferencedType(cpi, bytecode);
        }
    }

    private JavaTypeProfile getProfileForTypeCheck(ResolvedJavaType type) {
        if (parsingReplacement() || profilingInfo == null || !optimisticOpts.useTypeCheckHints() || !canHaveSubtype(type)) {
            return null;
        } else {
            return profilingInfo.getTypeProfile(bci());
        }
    }

    protected abstract ValueNode createCheckCast(ResolvedJavaType type, ValueNode object, JavaTypeProfile profileForTypeCheck, boolean forStoreCheck);

    private void genCheckCast() {
        int cpi = getStream().readCPI();
        JavaType type = lookupType(cpi, CHECKCAST);
        ValueNode object = frameState.apop();
        if (type instanceof ResolvedJavaType) {
            JavaTypeProfile profileForTypeCheck = getProfileForTypeCheck((ResolvedJavaType) type);
            ValueNode checkCastNode = append(createCheckCast((ResolvedJavaType) type, object, profileForTypeCheck, false));
            frameState.apush(checkCastNode);
        } else {
            handleUnresolvedCheckCast(type, object);
        }
    }

    protected abstract ValueNode createInstanceOf(ResolvedJavaType type, ValueNode object, JavaTypeProfile profileForTypeCheck);

    protected abstract ValueNode genConditional(ValueNode x);

    private void genInstanceOf() {
        int cpi = getStream().readCPI();
        JavaType type = lookupType(cpi, INSTANCEOF);
        ValueNode object = frameState.apop();
        if (type instanceof ResolvedJavaType) {
            ResolvedJavaType resolvedType = (ResolvedJavaType) type;
            ValueNode instanceOfNode = createInstanceOf((ResolvedJavaType) type, object, getProfileForTypeCheck(resolvedType));
            frameState.ipush(append(genConditional(genUnique(instanceOfNode))));
        } else {
            handleUnresolvedInstanceOf(type, object);
        }
    }

    protected abstract ValueNode createNewInstance(ResolvedJavaType type, boolean fillContents);

    void genNewInstance(int cpi) {
        JavaType type = lookupType(cpi, NEW);
        if (type instanceof ResolvedJavaType && ((ResolvedJavaType) type).isInitialized()) {
            ResolvedJavaType[] skippedExceptionTypes = this.graphBuilderConfig.getSkippedExceptionTypes();
            if (skippedExceptionTypes != null) {
                for (ResolvedJavaType exceptionType : skippedExceptionTypes) {
                    if (exceptionType.isAssignableFrom((ResolvedJavaType) type)) {
                        append(new DeoptimizeNode(DeoptimizationAction.None, TransferToInterpreter));
                        return;
                    }
                }
            }
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
        ValueNode length = frameState.ipop();
        if (type instanceof ResolvedJavaType) {
            frameState.apush(append(createNewArray((ResolvedJavaType) type, length, true)));
        } else {
            handleUnresolvedNewObjectArray(type, length);
        }

    }

    protected abstract ValueNode createNewArray(ResolvedJavaType elementType, ValueNode length, boolean fillContents);

    private void genNewMultiArray(int cpi) {
        JavaType type = lookupType(cpi, MULTIANEWARRAY);
        int rank = getStream().readUByte(bci() + 3);
        List<ValueNode> dims = new ArrayList<>(Collections.nCopies(rank, null));
        for (int i = rank - 1; i >= 0; i--) {
            dims.set(i, frameState.ipop());
        }
        if (type instanceof ResolvedJavaType) {
            frameState.apush(append(createNewMultiArray((ResolvedJavaType) type, dims)));
        } else {
            handleUnresolvedNewMultiArray(type, dims);
        }
    }

    protected abstract ValueNode createNewMultiArray(ResolvedJavaType type, List<ValueNode> dims);

    protected abstract ValueNode genLoadField(ValueNode receiver, ResolvedJavaField field);

    private void genGetField(JavaField field) {
        emitExplicitExceptions(frameState.peek(0), null);

        Kind kind = field.getKind();
        ValueNode receiver = frameState.apop();
        if ((field instanceof ResolvedJavaField) && ((ResolvedJavaField) field).getDeclaringClass().isInitialized()) {
            LoadFieldPlugin loadFieldPlugin = this.graphBuilderConfig.getPlugins().getLoadFieldPlugin();
            if (loadFieldPlugin == null || !loadFieldPlugin.apply((GraphBuilderContext) this, receiver, (ResolvedJavaField) field)) {
                appendOptimizedLoadField(kind, genLoadField(receiver, (ResolvedJavaField) field));
            }
        } else {
            handleUnresolvedLoadField(field, receiver);
        }
    }

    protected abstract void emitNullCheck(ValueNode receiver);

    protected abstract void emitBoundsCheck(ValueNode index, ValueNode length);

    private static final DebugMetric EXPLICIT_EXCEPTIONS = Debug.metric("ExplicitExceptions");

    protected abstract ValueNode genArrayLength(ValueNode x);

    protected void emitExplicitExceptions(ValueNode receiver, ValueNode outOfBoundsIndex) {
        assert receiver != null;
        if (graphBuilderConfig.omitAllExceptionEdges() || profilingInfo == null ||
                        (optimisticOpts.useExceptionProbabilityForOperations() && profilingInfo.getExceptionSeen(bci()) == TriState.FALSE && !GraalOptions.StressExplicitExceptionCode.getValue())) {
            return;
        }

        emitNullCheck(receiver);
        if (outOfBoundsIndex != null) {
            ValueNode length = append(genArrayLength(receiver));
            emitBoundsCheck(outOfBoundsIndex, length);
        }
        EXPLICIT_EXCEPTIONS.increment();
    }

    protected abstract ValueNode genStoreField(ValueNode receiver, ResolvedJavaField field, ValueNode value);

    private void genPutField(JavaField field) {
        emitExplicitExceptions(frameState.peek(1), null);

        ValueNode value = frameState.pop(field.getKind().getStackKind());
        ValueNode receiver = frameState.apop();
        if (field instanceof ResolvedJavaField && ((ResolvedJavaField) field).getDeclaringClass().isInitialized()) {
            appendOptimizedStoreField(genStoreField(receiver, (ResolvedJavaField) field, value));
        } else {
            handleUnresolvedStoreField(field, value, receiver);
        }
    }

    private void genGetStatic(JavaField field) {
        Kind kind = field.getKind();
        if (field instanceof ResolvedJavaField && ((ResolvedJavaType) field.getDeclaringClass()).isInitialized()) {
            LoadFieldPlugin loadFieldPlugin = this.graphBuilderConfig.getPlugins().getLoadFieldPlugin();
            if (loadFieldPlugin == null || !loadFieldPlugin.apply((GraphBuilderContext) this, (ResolvedJavaField) field)) {
                appendOptimizedLoadField(kind, genLoadField(null, (ResolvedJavaField) field));
            }
        } else {
            handleUnresolvedLoadField(field, null);
        }
    }

    public boolean tryLoadFieldPlugin(JavaField field, LoadFieldPlugin loadFieldPlugin) {
        return loadFieldPlugin.apply((GraphBuilderContext) this, (ResolvedJavaField) field);
    }

    private void genPutStatic(JavaField field) {
        ValueNode value = frameState.pop(field.getKind().getStackKind());
        if (field instanceof ResolvedJavaField && ((ResolvedJavaType) field.getDeclaringClass()).isInitialized()) {
            appendOptimizedStoreField(genStoreField(null, (ResolvedJavaField) field, value));
        } else {
            handleUnresolvedStoreField(field, value, null);
        }
    }

    protected void appendOptimizedStoreField(ValueNode store) {
        append(store);
    }

    protected void appendOptimizedLoadField(Kind kind, ValueNode load) {
        // append the load to the instruction
        ValueNode optimized = append(load);
        frameState.push(kind.getStackKind(), optimized);
    }

    protected abstract void genInvokeStatic(JavaMethod target);

    protected abstract void genInvokeInterface(JavaMethod target);

    protected abstract void genInvokeDynamic(JavaMethod target);

    protected abstract void genInvokeVirtual(JavaMethod target);

    protected abstract void genInvokeSpecial(JavaMethod target);

    protected abstract void genReturn(ValueNode x, Kind kind);

    protected abstract ValueNode genMonitorEnter(ValueNode x);

    protected abstract ValueNode genMonitorExit(ValueNode x, ValueNode returnValue);

    protected abstract void genJsr(int dest);

    protected abstract void genRet(int localIndex);

    private double[] switchProbability(int numberOfCases, int bci) {
        double[] prob = (profilingInfo == null ? null : profilingInfo.getSwitchProbabilities(bci));
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
        ValueNode value = frameState.ipop();

        int nofCases = bs.numberOfCases();
        double[] keyProbabilities = switchProbability(nofCases + 1, bci);

        Map<Integer, SuccessorInfo> bciToBlockSuccessorIndex = new HashMap<>();
        for (int i = 0; i < currentBlock.getSuccessorCount(); i++) {
            assert !bciToBlockSuccessorIndex.containsKey(currentBlock.getSuccessor(i).startBci);
            if (!bciToBlockSuccessorIndex.containsKey(currentBlock.getSuccessor(i).startBci)) {
                bciToBlockSuccessorIndex.put(currentBlock.getSuccessor(i).startBci, new SuccessorInfo(i));
            }
        }

        ArrayList<BciBlock> actualSuccessors = new ArrayList<>();
        int[] keys = new int[nofCases];
        int[] keySuccessors = new int[nofCases + 1];
        int deoptSuccessorIndex = -1;
        int nextSuccessorIndex = 0;
        boolean constantValue = value.isConstant();
        for (int i = 0; i < nofCases + 1; i++) {
            if (i < nofCases) {
                keys[i] = bs.keyAt(i);
            }

            if (!constantValue && isNeverExecutedCode(keyProbabilities[i])) {
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
                    actualSuccessors.add(currentBlock.getSuccessor(info.blockIndex));
                }
                keySuccessors[i] = info.actualIndex;
            }
        }

        genIntegerSwitch(value, actualSuccessors, keys, keyProbabilities, keySuccessors);

    }

    protected abstract void genIntegerSwitch(ValueNode value, ArrayList<BciBlock> actualSuccessors, int[] keys, double[] keyProbabilities, int[] keySuccessors);

    private static class SuccessorInfo {

        int blockIndex;
        int actualIndex;

        public SuccessorInfo(int blockSuccessorIndex) {
            this.blockIndex = blockSuccessorIndex;
            actualIndex = -1;
        }
    }

    protected abstract ValueNode appendConstant(JavaConstant constant);

    protected abstract ValueNode append(ValueNode v);

    protected boolean isNeverExecutedCode(double probability) {
        return probability == 0 && optimisticOpts.removeNeverExecutedCode();
    }

    protected double branchProbability() {
        if (profilingInfo == null) {
            return 0.5;
        }
        assert assertAtIfBytecode();
        double probability = profilingInfo.getBranchTakenProbability(bci());
        if (probability < 0) {
            assert probability == -1 : "invalid probability";
            Debug.log("missing probability in %s at bci %d", method, bci());
            probability = 0.5;
        }

        if (!optimisticOpts.removeNeverExecutedCode()) {
            if (probability == 0) {
                probability = 0.0000001;
            } else if (probability == 1) {
                probability = 0.999999;
            }
        }
        return probability;
    }

    private boolean assertAtIfBytecode() {
        int bytecode = stream.currentBC();
        switch (bytecode) {
            case IFEQ:
            case IFNE:
            case IFLT:
            case IFGE:
            case IFGT:
            case IFLE:
            case IF_ICMPEQ:
            case IF_ICMPNE:
            case IF_ICMPLT:
            case IF_ICMPGE:
            case IF_ICMPGT:
            case IF_ICMPLE:
            case IF_ACMPEQ:
            case IF_ACMPNE:
            case IFNULL:
            case IFNONNULL:
                return true;
        }
        assert false : String.format("%x is not an if bytecode", bytecode);
        return true;
    }

    protected abstract void iterateBytecodesForBlock(BciBlock block);

    public final void processBytecode(int bci, int opcode) {
        int cpi;

        // Checkstyle: stop
        // @formatter:off
    switch (opcode) {
        case NOP            : /* nothing to do */ break;
        case ACONST_NULL    : frameState.apush(appendConstant(JavaConstant.NULL_POINTER)); break;
        case ICONST_M1      : // fall through
        case ICONST_0       : // fall through
        case ICONST_1       : // fall through
        case ICONST_2       : // fall through
        case ICONST_3       : // fall through
        case ICONST_4       : // fall through
        case ICONST_5       : frameState.ipush(appendConstant(JavaConstant.forInt(opcode - ICONST_0))); break;
        case LCONST_0       : // fall through
        case LCONST_1       : frameState.lpush(appendConstant(JavaConstant.forLong(opcode - LCONST_0))); break;
        case FCONST_0       : // fall through
        case FCONST_1       : // fall through
        case FCONST_2       : frameState.fpush(appendConstant(JavaConstant.forFloat(opcode - FCONST_0))); break;
        case DCONST_0       : // fall through
        case DCONST_1       : frameState.dpush(appendConstant(JavaConstant.forDouble(opcode - DCONST_0))); break;
        case BIPUSH         : frameState.ipush(appendConstant(JavaConstant.forInt(stream.readByte()))); break;
        case SIPUSH         : frameState.ipush(appendConstant(JavaConstant.forInt(stream.readShort()))); break;
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
        case POP            : frameState.xpop(); break;
        case POP2           : frameState.xpop(); frameState.xpop(); break;
        case DUP            : frameState.xpush(frameState.xpeek()); break;
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
        case IRETURN        : genReturn(frameState.ipop(), Kind.Int); break;
        case LRETURN        : genReturn(frameState.lpop(), Kind.Long); break;
        case FRETURN        : genReturn(frameState.fpop(), Kind.Float); break;
        case DRETURN        : genReturn(frameState.dpop(), Kind.Double); break;
        case ARETURN        : genReturn(frameState.apop(), Kind.Object); break;
        case RETURN         : genReturn(null, Kind.Void); break;
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
            throw new BailoutException("Unsupported opcode %d (%s) [bci=%d]", opcode, nameOf(opcode), bci);
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

    public HIRFrameStateBuilder getFrameState() {
        return frameState;
    }

    protected boolean traceInstruction(int bci, int opcode, boolean blockStart) {
        if (Debug.isEnabled() && Options.TraceBytecodeParserLevel.getValue() >= TRACELEVEL_INSTRUCTIONS && Debug.isLogEnabled()) {
            traceInstructionHelper(bci, opcode, blockStart);
        }
        return true;
    }

    private void traceInstructionHelper(int bci, int opcode, boolean blockStart) {
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
        if (!currentBlock.getJsrScope().isEmpty()) {
            sb.append(' ').append(currentBlock.getJsrScope());
        }
        Debug.log("%s", sb);
    }

    public boolean parsingReplacement() {
        return replacementContext != null;
    }
}
