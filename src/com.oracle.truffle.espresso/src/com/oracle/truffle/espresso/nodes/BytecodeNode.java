/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.nodes;

import static com.oracle.truffle.espresso.bytecode.Bytecodes.AALOAD;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.AASTORE;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ACONST_NULL;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ALOAD;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ALOAD_0;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ALOAD_1;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ALOAD_2;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ALOAD_3;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ANEWARRAY;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ARETURN;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ARRAYLENGTH;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ASTORE;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ASTORE_0;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ASTORE_1;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ASTORE_2;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ASTORE_3;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ATHROW;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.BALOAD;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.BASTORE;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.BIPUSH;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.BREAKPOINT;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.CALOAD;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.CASTORE;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.CHECKCAST;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.D2F;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.D2I;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.D2L;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.DADD;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.DALOAD;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.DASTORE;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.DCMPG;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.DCMPL;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.DCONST_0;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.DCONST_1;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.DDIV;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.DLOAD;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.DLOAD_0;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.DLOAD_1;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.DLOAD_2;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.DLOAD_3;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.DMUL;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.DNEG;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.DREM;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.DRETURN;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.DSTORE;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.DSTORE_0;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.DSTORE_1;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.DSTORE_2;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.DSTORE_3;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.DSUB;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.DUP;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.DUP2;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.DUP2_X1;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.DUP2_X2;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.DUP_X1;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.DUP_X2;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.F2D;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.F2I;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.F2L;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.FADD;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.FALOAD;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.FASTORE;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.FCMPG;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.FCMPL;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.FCONST_0;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.FCONST_1;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.FCONST_2;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.FDIV;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.FLOAD;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.FLOAD_0;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.FLOAD_1;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.FLOAD_2;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.FLOAD_3;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.FMUL;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.FNEG;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.FREM;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.FRETURN;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.FSTORE;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.FSTORE_0;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.FSTORE_1;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.FSTORE_2;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.FSTORE_3;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.FSUB;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.GETFIELD;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.GETSTATIC;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.GOTO;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.GOTO_W;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.I2B;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.I2C;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.I2D;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.I2F;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.I2L;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.I2S;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.IADD;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.IALOAD;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.IAND;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.IASTORE;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ICONST_0;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ICONST_1;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ICONST_2;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ICONST_3;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ICONST_4;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ICONST_5;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ICONST_M1;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.IDIV;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.IFEQ;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.IFGE;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.IFGT;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.IFLE;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.IFLT;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.IFNE;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.IFNONNULL;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.IFNULL;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.IF_ACMPEQ;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.IF_ACMPNE;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.IF_ICMPEQ;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.IF_ICMPGE;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.IF_ICMPGT;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.IF_ICMPLE;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.IF_ICMPLT;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.IF_ICMPNE;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.IINC;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ILOAD;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ILOAD_0;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ILOAD_1;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ILOAD_2;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ILOAD_3;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.IMUL;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.INEG;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.INSTANCEOF;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.INVOKEDYNAMIC;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.INVOKEINTERFACE;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.INVOKESPECIAL;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.INVOKESTATIC;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.INVOKEVIRTUAL;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.IOR;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.IREM;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.IRETURN;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ISHL;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ISHR;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ISTORE;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ISTORE_0;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ISTORE_1;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ISTORE_2;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ISTORE_3;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.ISUB;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.IUSHR;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.IXOR;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.JSR;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.JSR_W;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.L2D;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.L2F;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.L2I;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LADD;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LALOAD;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LAND;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LASTORE;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LCMP;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LCONST_0;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LCONST_1;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LDC;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LDC2_W;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LDC_W;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LDIV;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LLOAD;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LLOAD_0;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LLOAD_1;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LLOAD_2;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LLOAD_3;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LMUL;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LNEG;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LOOKUPSWITCH;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LOR;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LREM;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LRETURN;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LSHL;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LSHR;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LSTORE;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LSTORE_0;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LSTORE_1;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LSTORE_2;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LSTORE_3;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LSUB;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LUSHR;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.LXOR;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.MONITORENTER;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.MONITOREXIT;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.MULTIANEWARRAY;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.NEW;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.NEWARRAY;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.NOP;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.POP;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.POP2;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.PUTFIELD;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.PUTSTATIC;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.QUICK;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.RET;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.RETURN;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.SALOAD;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.SASTORE;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.SIPUSH;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.SWAP;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.TABLESWITCH;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.WIDE;

import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.locks.Lock;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotTypeException;
import com.oracle.truffle.api.frame.FrameUtil;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.api.instrumentation.StandardTags.StatementTag;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.nodes.CustomNodeCount;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.LoopNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.bytecode.BytecodeLookupSwitch;
import com.oracle.truffle.espresso.bytecode.BytecodeStream;
import com.oracle.truffle.espresso.bytecode.BytecodeTableSwitch;
import com.oracle.truffle.espresso.bytecode.Bytecodes;
import com.oracle.truffle.espresso.bytecode.MapperBCI;
import com.oracle.truffle.espresso.classfile.BootstrapMethodsAttribute;
import com.oracle.truffle.espresso.classfile.ClassConstant;
import com.oracle.truffle.espresso.classfile.ConstantPool;
import com.oracle.truffle.espresso.classfile.DoubleConstant;
import com.oracle.truffle.espresso.classfile.FloatConstant;
import com.oracle.truffle.espresso.classfile.IntegerConstant;
import com.oracle.truffle.espresso.classfile.InvokeDynamicConstant;
import com.oracle.truffle.espresso.classfile.LineNumberTable;
import com.oracle.truffle.espresso.classfile.LongConstant;
import com.oracle.truffle.espresso.classfile.MethodHandleConstant;
import com.oracle.truffle.espresso.classfile.MethodRefConstant;
import com.oracle.truffle.espresso.classfile.MethodTypeConstant;
import com.oracle.truffle.espresso.classfile.NameAndTypeConstant;
import com.oracle.truffle.espresso.classfile.PoolConstant;
import com.oracle.truffle.espresso.classfile.RuntimeConstantPool;
import com.oracle.truffle.espresso.classfile.StringConstant;
import com.oracle.truffle.espresso.descriptors.Signatures;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Signature;
import com.oracle.truffle.espresso.descriptors.Symbol.Type;
import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.ExceptionHandler;
import com.oracle.truffle.espresso.meta.JavaKind;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.EspressoException;
import com.oracle.truffle.espresso.runtime.EspressoExitException;
import com.oracle.truffle.espresso.runtime.ReturnAddress;
import com.oracle.truffle.espresso.runtime.StaticObject;
import com.oracle.truffle.espresso.substitutions.Target_java_lang_Thread;
import com.oracle.truffle.espresso.vm.InterpreterToVM;
import com.oracle.truffle.object.DebugCounter;

/**
 * Bytecode interpreter loop.
 *
 *
 * Calling convention uses strict Java primitive types although internally the VM basic types are
 * used with conversions at the boundaries.
 *
 * <h3>Operand stack</h3>
 * <p>
 * The operand stack is implemented in a PE-friendly way, with the {@code top} of the stack index
 * being a local variable. With ad-hoc implementation there's no explicit pop operation. Each
 * bytecode is first processed/executed without growing or shinking the stack and only then the
 * {@code top} of the stack index is adjusted depending on the bytecode stack offset.
 */
public final class BytecodeNode extends EspressoMethodNode implements CustomNodeCount {

    public static final boolean DEBUG_GENERAL = false;
    public static final boolean DEBUG_THROWN = false;
    public static final boolean DEBUG_CATCH = false;

    public static final DebugCounter bcCount = DebugCounter.create("Bytecodes executed");

    static final DebugCounter injectAndCallCount = DebugCounter.create("injectAndCallCount");

    public static final DebugCounter resolveFieldCount = DebugCounter.create("resolveFieldCount");
    public static final DebugCounter resolveKlassCount = DebugCounter.create("resolveKlassCount");
    public static final DebugCounter resolveMethodCount = DebugCounter.create("resolveMethodCount");

    public static final DebugCounter invokeNodes = DebugCounter.create("total invoke nodes");

    @Children private QuickNode[] nodes = QuickNode.EMPTY_ARRAY;

    @CompilationFinal(dimensions = 1) //
    private final FrameSlot[] locals;

    @CompilationFinal(dimensions = 1) //
    private final FrameSlot[] stackSlots;

    private final FrameSlot monitorSlot;
    private final FrameSlot bciSlot;

    @CompilationFinal(dimensions = 1) //
    private final int[] SOEinfo;

    @CompilationFinal(dimensions = 2) //
    private int[][] JSRbci = null;

    private final BytecodeStream bs;

    @Child private volatile InstrumentationSupport instrumentation;

    private final BranchProfile unbalancedMonitorProfile = BranchProfile.create();

    @TruffleBoundary
    public BytecodeNode(Method method, FrameDescriptor frameDescriptor, FrameSlot monitorSlot, FrameSlot bciSlot) {
        super(method);
        CompilerAsserts.neverPartOfCompilation();
        this.bs = new BytecodeStream(method.getCode());
        FrameSlot[] slots = frameDescriptor.getSlots().toArray(new FrameSlot[0]);

        this.locals = Arrays.copyOfRange(slots, 0, method.getMaxLocals());
        this.stackSlots = Arrays.copyOfRange(slots, method.getMaxLocals(), method.getMaxLocals() + method.getMaxStackSize());
        this.monitorSlot = monitorSlot;
        this.bciSlot = bciSlot;
        this.SOEinfo = getMethod().getSOEHandlerInfo();
    }

    public BytecodeNode(BytecodeNode copy) {
        this(copy.getMethod(), copy.getRootNode().getFrameDescriptor(), copy.monitorSlot, copy.bciSlot);
        System.err.println("Copying node for " + getMethod());
    }

    public final SourceSection getSourceSectionAtBCI(int bci) {
        Source s = getSource();
        if (s == null) {
            return null;
        }
        int line = getMethod().getLineNumberTable().getLineNumber(bci);
        return s.createSection(line);
    }

    @ExplodeLoop
    private void initArguments(final VirtualFrame frame) {
        boolean hasReceiver = !getMethod().isStatic();
        int argCount = Signatures.parameterCount(getMethod().getParsedSignature(), false);

        CompilerAsserts.partialEvaluationConstant(argCount);
        CompilerAsserts.partialEvaluationConstant(locals.length);

        Object[] frameArguments = frame.getArguments();
        Object[] arguments;
        if (hasReceiver) {
            arguments = copyOfRange(frameArguments, 1, argCount + 1);
        } else {
            arguments = frameArguments;
        }

        assert arguments.length == argCount;

        int n = 0;
        if (hasReceiver) {
            assert !StaticObject.isEspressoNull(frameArguments[0]) : "null receiver in init arguments !";
            setLocalObject(frame, n, (StaticObject) frameArguments[0]);
            n += JavaKind.Object.getSlotCount();
        }
        for (int i = 0; i < argCount; ++i) {
            JavaKind expectedkind = Signatures.parameterKind(getMethod().getParsedSignature(), i);
            // @formatter:off
            // Checkstyle: stop
            switch (expectedkind) {
                case Boolean : setLocalInt(frame, n, ((boolean) arguments[i]) ? 1 : 0); break;
                case Byte    : setLocalInt(frame, n, ((byte) arguments[i]));            break;
                case Short   : setLocalInt(frame, n, ((short) arguments[i]));           break;
                case Char    : setLocalInt(frame, n, ((char) arguments[i]));            break;
                case Int     : setLocalInt(frame, n, (int) arguments[i]);               break;
                case Float   : setLocalFloat(frame, n, (float) arguments[i]);           break;
                case Long    : setLocalLong(frame, n, (long) arguments[i]);             break;
                case Double  : setLocalDouble(frame, n, (double) arguments[i]);         break;
                case Object  : setLocalObject(frame, n, (StaticObject) arguments[i]);   break;
                default      : throw EspressoError.shouldNotReachHere("unexpected kind");
            }
            // @formatter:on
            // Checkstyle: resume
            n += expectedkind.getSlotCount();
        }
        setBCI(frame, 0);
        if (monitorSlot != null) {
            frame.setObject(monitorSlot, new MonitorStack());
        }
    }

    private void setBCI(VirtualFrame frame, int bci) {
        frame.setInt(bciSlot, bci);
    }

    int peekInt(VirtualFrame frame, int slot) {
        return (int) FrameUtil.getLongSafe(frame, stackSlots[slot]);
    }

    // Exposed to InstanceOfNode.
    StaticObject peekObject(VirtualFrame frame, int slot) {
        Object result = FrameUtil.getObjectSafe(frame, stackSlots[slot]);
        assert result instanceof StaticObject;
        return (StaticObject) result;
    }

    /**
     * Read and clear the operand stack slot.
     */
    StaticObject peekAndReleaseObject(VirtualFrame frame, int slot) {
        Object result = FrameUtil.getObjectSafe(frame, stackSlots[slot]);
        // nulls-out the slot, use peekObject to read only
        putObject(frame, slot, null);
        assert result instanceof StaticObject;
        return (StaticObject) result;
    }

    // Boxed value.
    Object peekValue(VirtualFrame frame, int slot) {
        return frame.getValue(stackSlots[slot]);
    }

    float peekFloat(VirtualFrame frame, int slot) {
        return Float.intBitsToFloat((int) FrameUtil.getLongSafe(frame, stackSlots[slot]));
    }

    long peekLong(VirtualFrame frame, int slot) {
        return FrameUtil.getLongSafe(frame, stackSlots[slot]);
    }

    double peekDouble(VirtualFrame frame, int slot) {
        return Double.longBitsToDouble(FrameUtil.getLongSafe(frame, stackSlots[slot]));
    }

    /**
     * Read and clear the operand stack slot.
     */
    private Object peekAndReleaseReturnAddressOrObject(VirtualFrame frame, int slot) {
        Object result = FrameUtil.getObjectSafe(frame, stackSlots[slot]);
        putObjectOrReturnAddress(frame, slot, null);
        assert result instanceof StaticObject || result instanceof ReturnAddress;
        return result;
    }

    private void putReturnAddress(VirtualFrame frame, int slot, int targetBCI) {
        frame.setObject(stackSlots[slot], ReturnAddress.create(targetBCI));
    }

    void putObject(VirtualFrame frame, int slot, StaticObject value) {
        frame.setObject(stackSlots[slot], value);
    }

    private void putObjectOrReturnAddress(VirtualFrame frame, int slot, Object value) {
        assert value instanceof StaticObject || value instanceof ReturnAddress || value == null;
        frame.setObject(stackSlots[slot], value);
    }

    void putInt(VirtualFrame frame, int slot, int value) {
        frame.setLong(stackSlots[slot], value);
    }

    void putFloat(VirtualFrame frame, int slot, float value) {
        frame.setLong(stackSlots[slot], Float.floatToRawIntBits(value));
    }

    void putLong(VirtualFrame frame, int slot, long value) {
        // frame.setObject(stackSlots[slot], StaticObject.NULL);
        frame.setLong(stackSlots[slot + 1], value);
    }

    void putDouble(VirtualFrame frame, int slot, double value) {
        // frame.setObject(stackSlots[slot], StaticObject.NULL);
        frame.setLong(stackSlots[slot + 1], Double.doubleToRawLongBits(value));
    }

    // region Local accessors

    private void setLocalObject(VirtualFrame frame, int slot, StaticObject value) {
        frame.setObject(locals[slot], value);
    }

    private void setLocalObjectOrReturnAddress(VirtualFrame frame, int slot, Object value) {
        frame.setObject(locals[slot], value);
    }

    private void setLocalInt(VirtualFrame frame, int slot, int value) {
        frame.setLong(locals[slot], value);
    }

    private void setLocalFloat(VirtualFrame frame, int slot, float value) {
        frame.setLong(locals[slot], Float.floatToRawIntBits(value));
    }

    private void setLocalLong(VirtualFrame frame, int slot, long value) {
        frame.setLong(locals[slot], value);
    }

    private void setLocalDouble(VirtualFrame frame, int slot, double value) {
        frame.setLong(locals[slot], Double.doubleToRawLongBits(value));
    }

    private int getLocalInt(VirtualFrame frame, int slot) {
        return (int) FrameUtil.getLongSafe(frame, locals[slot]);
    }

    private StaticObject getLocalObject(VirtualFrame frame, int slot) {
        Object result = FrameUtil.getObjectSafe(frame, locals[slot]);
        assert result instanceof StaticObject;
        return (StaticObject) result;
    }

    private int getLocalReturnAddress(VirtualFrame frame, int slot) {
        Object result = FrameUtil.getObjectSafe(frame, locals[slot]);
        assert result instanceof ReturnAddress;
        return ((ReturnAddress) result).getBci();
    }

    private float getLocalFloat(VirtualFrame frame, int slot) {
        return Float.intBitsToFloat((int) FrameUtil.getLongSafe(frame, locals[slot]));
    }

    private long getLocalLong(VirtualFrame frame, int slot) {
        return FrameUtil.getLongSafe(frame, locals[slot]);
    }

    private double getLocalDouble(VirtualFrame frame, int slot) {
        return Double.longBitsToDouble(FrameUtil.getLongSafe(frame, locals[slot]));
    }

    // region Local accessors

    @Override
    @SuppressWarnings("unchecked")
    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.MERGE_EXPLODE)
    public Object execute(VirtualFrame frame) {

        int curBCI = 0;
        int top = 0;
        initArguments(frame);
        InstrumentationSupport instrument = this.instrumentation;
        int statementIndex = -1;
        int nextStatementIndex = 0;

        if (instrument != null) {
            instrument.notifyEntry(frame);
        }

        loop: while (true) {
            int curOpcode;
            bcCount.inc();
            try {
                curOpcode = bs.currentBC(curBCI);
                CompilerAsserts.partialEvaluationConstant(top);
                CompilerAsserts.partialEvaluationConstant(curBCI);
                CompilerAsserts.partialEvaluationConstant(curOpcode);

                CompilerAsserts.partialEvaluationConstant(statementIndex);
                CompilerAsserts.partialEvaluationConstant(nextStatementIndex);

                if (instrument != null) {
                    instrument.notifyStatement(frame, statementIndex, nextStatementIndex);
                    statementIndex = nextStatementIndex;
                }

                if (Bytecodes.canTrap(curOpcode)) {
                    setBCI(frame, curBCI);
                }

                // @formatter:off
                // Checkstyle: stop
                try {
                    switchLabel:
                    switch (curOpcode) {
                        case NOP: break;
                        case ACONST_NULL: putObject(frame, top, StaticObject.NULL); break;

                        case ICONST_M1: // fall through
                        case ICONST_0: // fall through
                        case ICONST_1: // fall through
                        case ICONST_2: // fall through
                        case ICONST_3: // fall through
                        case ICONST_4: // fall through
                        case ICONST_5: putInt(frame, top, curOpcode - ICONST_0); break;

                        case LCONST_0: // fall through
                        case LCONST_1: putLong(frame, top, curOpcode - LCONST_0); break;

                        case FCONST_0: // fall through
                        case FCONST_1: // fall through
                        case FCONST_2: putFloat(frame, top, curOpcode - FCONST_0); break;

                        case DCONST_0: // fall through
                        case DCONST_1: putDouble(frame, top, curOpcode - DCONST_0); break;

                        case BIPUSH: putInt(frame, top, bs.readByte(curBCI)); break;
                        case SIPUSH: putInt(frame, top, bs.readShort(curBCI)); break;
                        case LDC: // fall through
                        case LDC_W: // fall through
                        case LDC2_W: putPoolConstant(frame, top, bs.readCPI(curBCI), curOpcode); break;

                        case ILOAD: putInt(frame, top, getLocalInt(frame, bs.readLocalIndex(curBCI))); break;
                        case LLOAD: putLong(frame, top, getLocalLong(frame, bs.readLocalIndex(curBCI))); break;
                        case FLOAD: putFloat(frame, top, getLocalFloat(frame, bs.readLocalIndex(curBCI))); break;
                        case DLOAD: putDouble(frame, top, getLocalDouble(frame, bs.readLocalIndex(curBCI))); break;
                        case ALOAD: putObject(frame, top, getLocalObject(frame, bs.readLocalIndex(curBCI))); break;

                        case ILOAD_0: // fall through
                        case ILOAD_1: // fall through
                        case ILOAD_2: // fall through
                        case ILOAD_3: putInt(frame, top, getLocalInt(frame, curOpcode - ILOAD_0)); break;
                        case LLOAD_0: // fall through
                        case LLOAD_1: // fall through
                        case LLOAD_2: // fall through
                        case LLOAD_3: putLong(frame, top, getLocalLong(frame, curOpcode - LLOAD_0)); break;
                        case FLOAD_0: // fall through
                        case FLOAD_1: // fall through
                        case FLOAD_2: // fall through
                        case FLOAD_3: putFloat(frame, top, getLocalFloat(frame, curOpcode - FLOAD_0)); break;
                        case DLOAD_0: // fall through
                        case DLOAD_1: // fall through
                        case DLOAD_2: // fall through
                        case DLOAD_3: putDouble(frame, top, getLocalDouble(frame, curOpcode - DLOAD_0)); break;
                        case ALOAD_0: // fall through
                        case ALOAD_1: // fall through
                        case ALOAD_2: // fall through
                        case ALOAD_3: putObject(frame, top, getLocalObject(frame, curOpcode - ALOAD_0)); break;

                        case IALOAD: putInt(frame, top - 2, getInterpreterToVM().getArrayInt(peekInt(frame, top - 1), nullCheck(peekAndReleaseObject(frame, top - 2)))); break;
                        case LALOAD: putLong(frame, top - 2, getInterpreterToVM().getArrayLong(peekInt(frame, top - 1), nullCheck(peekAndReleaseObject(frame, top - 2)))); break;
                        case FALOAD: putFloat(frame, top - 2, getInterpreterToVM().getArrayFloat(peekInt(frame, top - 1), nullCheck(peekAndReleaseObject(frame, top - 2)))); break;
                        case DALOAD: putDouble(frame, top - 2, getInterpreterToVM().getArrayDouble(peekInt(frame, top - 1), nullCheck(peekAndReleaseObject(frame, top - 2)))); break;
                        case AALOAD: putObject(frame, top - 2, getInterpreterToVM().getArrayObject(peekInt(frame, top - 1), nullCheck(peekAndReleaseObject(frame, top - 2)))); break;
                        case BALOAD: putInt(frame, top - 2, getInterpreterToVM().getArrayByte(peekInt(frame, top - 1), nullCheck(peekAndReleaseObject(frame, top - 2)))); break;
                        case CALOAD: putInt(frame, top - 2, getInterpreterToVM().getArrayChar(peekInt(frame, top - 1), nullCheck(peekAndReleaseObject(frame, top - 2)))); break;
                        case SALOAD: putInt(frame, top - 2, getInterpreterToVM().getArrayShort(peekInt(frame, top - 1), nullCheck(peekAndReleaseObject(frame, top - 2)))); break;

                        case ISTORE: setLocalInt(frame, bs.readLocalIndex(curBCI), peekInt(frame, top - 1)); break;
                        case LSTORE: setLocalLong(frame, bs.readLocalIndex(curBCI), peekLong(frame, top - 1)); break;
                        case FSTORE: setLocalFloat(frame, bs.readLocalIndex(curBCI), peekFloat(frame, top - 1)); break;
                        case DSTORE: setLocalDouble(frame, bs.readLocalIndex(curBCI), peekDouble(frame, top - 1)); break;
                        case ASTORE: setLocalObjectOrReturnAddress(frame, bs.readLocalIndex(curBCI), peekAndReleaseReturnAddressOrObject(frame, top - 1)); break;

                        case ISTORE_0: // fall through
                        case ISTORE_1: // fall through
                        case ISTORE_2: // fall through
                        case ISTORE_3: setLocalInt(frame, curOpcode - ISTORE_0, peekInt(frame, top - 1)); break;
                        case LSTORE_0: // fall through
                        case LSTORE_1: // fall through
                        case LSTORE_2: // fall through
                        case LSTORE_3: setLocalLong(frame, curOpcode - LSTORE_0, peekLong(frame, top - 1)); break;
                        case FSTORE_0: // fall through
                        case FSTORE_1: // fall through
                        case FSTORE_2: // fall through
                        case FSTORE_3: setLocalFloat(frame, curOpcode - FSTORE_0, peekFloat(frame, top - 1)); break;
                        case DSTORE_0: // fall through
                        case DSTORE_1: // fall through
                        case DSTORE_2: // fall through
                        case DSTORE_3: setLocalDouble(frame, curOpcode - DSTORE_0, peekDouble(frame, top - 1)); break;
                        case ASTORE_0: // fall through
                        case ASTORE_1: // fall through
                        case ASTORE_2: // fall through
                        case ASTORE_3: setLocalObjectOrReturnAddress(frame, curOpcode - ASTORE_0, peekAndReleaseReturnAddressOrObject(frame, top - 1)); break;

                        case IASTORE: getInterpreterToVM().setArrayInt(peekInt(frame, top - 1), peekInt(frame, top - 2), nullCheck(peekAndReleaseObject(frame, top - 3))); break;
                        case LASTORE: getInterpreterToVM().setArrayLong(peekLong(frame, top - 1), peekInt(frame, top - 3), nullCheck(peekAndReleaseObject(frame, top - 4))); break;
                        case FASTORE: getInterpreterToVM().setArrayFloat(peekFloat(frame, top - 1), peekInt(frame, top - 2), nullCheck(peekAndReleaseObject(frame, top - 3))); break;
                        case DASTORE: getInterpreterToVM().setArrayDouble(peekDouble(frame, top - 1), peekInt(frame, top - 3), nullCheck(peekAndReleaseObject(frame, top - 4))); break;
                        case AASTORE: getInterpreterToVM().setArrayObject(peekAndReleaseObject(frame, top - 1), peekInt(frame, top - 2), nullCheck(peekAndReleaseObject(frame, top - 3))); break;
                        case BASTORE: getInterpreterToVM().setArrayByte((byte) peekInt(frame, top - 1), peekInt(frame, top - 2), nullCheck(peekAndReleaseObject(frame, top - 3))); break;
                        case CASTORE: getInterpreterToVM().setArrayChar((char) peekInt(frame, top - 1), peekInt(frame, top - 2), nullCheck(peekAndReleaseObject(frame, top - 3))); break;
                        case SASTORE: getInterpreterToVM().setArrayShort((short) peekInt(frame, top - 1), peekInt(frame, top - 2), nullCheck(peekAndReleaseObject(frame, top - 3))); break;

                        case POP2:
                            putObject(frame, top - 1, null);
                            putObject(frame, top - 2, null);
                            break;
                        case POP:
                            putObject(frame, top - 1, null);
                            break;

                        // TODO(peterssen): Stack shuffling is expensive.
                        case DUP: dup1(frame, top); break;
                        case DUP_X1: dupx1(frame, top); break;
                        case DUP_X2: dupx2(frame, top); break;
                        case DUP2: dup2(frame, top); break;
                        case DUP2_X1: dup2x1(frame, top); break;
                        case DUP2_X2: dup2x2(frame, top); break;
                        case SWAP: swapSingle(frame, top); break;

                        case IADD: putInt(frame, top - 2, peekInt(frame, top - 1) + peekInt(frame, top - 2)); break;
                        case LADD: putLong(frame, top - 4, peekLong(frame, top - 1) + peekLong(frame, top - 3)); break;
                        case FADD: putFloat(frame, top - 2, peekFloat(frame, top - 1) + peekFloat(frame, top - 2)); break;
                        case DADD: putDouble(frame, top - 4, peekDouble(frame, top - 1) + peekDouble(frame, top - 3)); break;

                        case ISUB: putInt(frame, top - 2, -peekInt(frame, top - 1) + peekInt(frame, top - 2)); break;
                        case LSUB: putLong(frame, top - 4, -peekLong(frame, top - 1) + peekLong(frame, top - 3)); break;
                        case FSUB: putFloat(frame, top - 2, -peekFloat(frame, top - 1) + peekFloat(frame, top - 2)); break;
                        case DSUB: putDouble(frame, top - 4, -peekDouble(frame, top - 1) + peekDouble(frame, top - 3)); break;

                        case IMUL: putInt(frame, top - 2, peekInt(frame, top - 1) * peekInt(frame, top - 2)); break;
                        case LMUL: putLong(frame, top - 4, peekLong(frame, top - 1) * peekLong(frame, top - 3)); break;
                        case FMUL: putFloat(frame, top - 2, peekFloat(frame, top - 1) * peekFloat(frame, top - 2)); break;
                        case DMUL: putDouble(frame, top - 4, peekDouble(frame, top - 1) * peekDouble(frame, top - 3)); break;

                        case IDIV: putInt(frame, top - 2, divInt(checkNonZero(peekInt(frame, top - 1)), peekInt(frame, top - 2))); break;
                        case LDIV: putLong(frame, top - 4, divLong(checkNonZero(peekLong(frame, top - 1)), peekLong(frame, top - 3))); break;
                        case FDIV: putFloat(frame, top - 2, divFloat(peekFloat(frame, top - 1), peekFloat(frame, top - 2))); break;
                        case DDIV: putDouble(frame, top - 4, divDouble(peekDouble(frame, top - 1), peekDouble(frame, top - 3))); break;

                        case IREM: putInt(frame, top - 2, remInt(checkNonZero(peekInt(frame, top - 1)), peekInt(frame, top - 2))); break;
                        case LREM: putLong(frame, top - 4, remLong(checkNonZero(peekLong(frame, top - 1)), peekLong(frame, top - 3))); break;
                        case FREM: putFloat(frame, top - 2, remFloat(peekFloat(frame, top - 1), peekFloat(frame, top - 2))); break;
                        case DREM: putDouble(frame, top - 4, remDouble(peekDouble(frame, top - 1), peekDouble(frame, top - 3))); break;

                        case INEG: putInt(frame, top - 1, -peekInt(frame, top - 1)); break;
                        case LNEG: putLong(frame, top - 2, -peekLong(frame, top - 1)); break;
                        case FNEG: putFloat(frame, top - 1, -peekFloat(frame, top - 1)); break;
                        case DNEG: putDouble(frame, top - 2, -peekDouble(frame, top - 1)); break;

                        case ISHL: putInt(frame, top - 2, shiftLeftInt(peekInt(frame, top - 1), peekInt(frame, top - 2))); break;
                        case LSHL: putLong(frame, top - 3, shiftLeftLong(peekInt(frame, top - 1), peekLong(frame, top - 2))); break;
                        case ISHR: putInt(frame, top - 2, shiftRightSignedInt(peekInt(frame, top - 1), peekInt(frame, top - 2))); break;
                        case LSHR: putLong(frame, top - 3, shiftRightSignedLong(peekInt(frame, top - 1), peekLong(frame, top - 2))); break;
                        case IUSHR: putInt(frame, top - 2, shiftRightUnsignedInt(peekInt(frame, top - 1), peekInt(frame, top - 2))); break;
                        case LUSHR: putLong(frame, top - 3, shiftRightUnsignedLong(peekInt(frame, top - 1), peekLong(frame, top - 2))); break;

                        case IAND: putInt(frame, top - 2, peekInt(frame, top - 1) & peekInt(frame, top - 2)); break;
                        case LAND: putLong(frame, top - 4, peekLong(frame, top - 1) & peekLong(frame, top - 3)); break;

                        case IOR: putInt(frame, top - 2, peekInt(frame, top - 1) | peekInt(frame, top - 2)); break;
                        case LOR: putLong(frame, top - 4, peekLong(frame, top - 1) | peekLong(frame, top - 3)); break;

                        case IXOR: putInt(frame, top - 2, peekInt(frame, top - 1) ^ peekInt(frame, top - 2)); break;
                        case LXOR: putLong(frame, top - 4, peekLong(frame, top - 1) ^ peekLong(frame, top - 3)); break;

                        case IINC: setLocalInt(frame, bs.readLocalIndex(curBCI), getLocalInt(frame, bs.readLocalIndex(curBCI)) + bs.readIncrement(curBCI)); break;

                        case I2L: putLong(frame, top - 1, peekInt(frame, top - 1)); break;
                        case I2F: putFloat(frame, top - 1, peekInt(frame, top - 1)); break;
                        case I2D: putDouble(frame, top - 1, peekInt(frame, top - 1)); break;

                        case L2I: putInt(frame, top - 2, (int) peekLong(frame, top - 1)); break;
                        case L2F: putFloat(frame, top - 2, peekLong(frame, top - 1)); break;
                        case L2D: putDouble(frame, top - 2, peekLong(frame, top - 1)); break;

                        case F2I: putInt(frame, top - 1, (int) peekFloat(frame, top - 1)); break;
                        case F2L: putLong(frame, top - 1, (long) peekFloat(frame, top - 1)); break;
                        case F2D: putDouble(frame, top - 1, peekFloat(frame, top - 1)); break;

                        case D2I: putInt(frame, top - 2, (int) peekDouble(frame, top - 1)); break;
                        case D2L: putLong(frame, top - 2, (long) peekDouble(frame, top - 1)); break;
                        case D2F: putFloat(frame, top - 2, (float) peekDouble(frame, top - 1)); break;

                        case I2B: putInt(frame, top - 1, (byte) peekInt(frame, top - 1)); break;
                        case I2C: putInt(frame, top - 1, (char) peekInt(frame, top - 1)); break;
                        case I2S: putInt(frame, top - 1, (short) peekInt(frame, top - 1)); break;

                        case LCMP: putInt(frame, top - 4, compareLong(peekLong(frame, top - 1), peekLong(frame, top - 3))); break;
                        case FCMPL: putInt(frame, top - 2, compareFloatLess(peekFloat(frame, top - 1), peekFloat(frame, top - 2))); break;
                        case FCMPG: putInt(frame, top - 2, compareFloatGreater(peekFloat(frame, top - 1), peekFloat(frame, top - 2))); break;
                        case DCMPL: putInt(frame, top - 4, compareDoubleLess(peekDouble(frame, top - 1), peekDouble(frame, top - 3))); break;
                        case DCMPG: putInt(frame, top - 4, compareDoubleGreater(peekDouble(frame, top - 1), peekDouble(frame, top - 3))); break;

                        case IFEQ: // fall through
                        case IFNE: // fall through
                        case IFLT: // fall through
                        case IFGE: // fall through
                        case IFGT: // fall through
                        case IFLE: // fall through
                        case IF_ICMPEQ: // fall through
                        case IF_ICMPNE: // fall through
                        case IF_ICMPLT: // fall through
                        case IF_ICMPGE: // fall through
                        case IF_ICMPGT: // fall through
                        case IF_ICMPLE: // fall through
                        case IF_ACMPEQ: // fall through
                        case IF_ACMPNE: // fall through

                        // TODO(peterssen): Order shuffled.
                        case GOTO: // fall through
                        case GOTO_W: // fall through
                        case IFNULL: // fall through
                        case IFNONNULL:
                            // @formatter:on
                            // Checkstyle: resume
                            if (takeBranch(frame, top, curOpcode)) {
                                int targetBCI = bs.readBranchDest(curBCI);
                                CompilerAsserts.partialEvaluationConstant(targetBCI);
                                checkBackEdge(curBCI, targetBCI, top, curOpcode);
                                if (instrument != null) {
                                    nextStatementIndex = instrument.getStatementIndexAfterJump(statementIndex, curBCI, targetBCI);
                                }
                                top += Bytecodes.stackEffectOf(curOpcode);
                                curBCI = targetBCI;
                                continue loop;
                            }
                            break switchLabel;

                        case JSR: // fall through
                        case JSR_W: {
                            putReturnAddress(frame, top, bs.nextBCI(curBCI));
                            int targetBCI = bs.readBranchDest(curBCI);
                            CompilerAsserts.partialEvaluationConstant(targetBCI);
                            checkBackEdge(curBCI, targetBCI, top, curOpcode);
                            if (instrument != null) {
                                nextStatementIndex = instrument.getStatementIndexAfterJump(statementIndex, curBCI, targetBCI);
                            }
                            top += Bytecodes.stackEffectOf(curOpcode);
                            curBCI = targetBCI;
                            continue loop;
                        }
                        case RET: {
                            int targetBCI = getLocalReturnAddress(frame, bs.readLocalIndex(curBCI));
                            if (JSRbci == null) {
                                CompilerDirectives.transferToInterpreterAndInvalidate();
                                JSRbci = new int[bs.endBCI()][];
                            }
                            if (JSRbci[curBCI] == null) {
                                CompilerDirectives.transferToInterpreterAndInvalidate();
                                JSRbci[curBCI] = new int[]{targetBCI};
                            }
                            for (int jsr : JSRbci[curBCI]) {
                                if (jsr == targetBCI) {
                                    CompilerAsserts.partialEvaluationConstant(jsr);
                                    targetBCI = jsr;
                                    CompilerAsserts.partialEvaluationConstant(targetBCI);
                                    checkBackEdge(curBCI, targetBCI, top, curOpcode);
                                    if (instrument != null) {
                                        nextStatementIndex = instrument.getStatementIndexAfterJump(statementIndex, curBCI, targetBCI);
                                    }
                                    top += Bytecodes.stackEffectOf(curOpcode);
                                    curBCI = targetBCI;
                                    continue loop;
                                }
                            }
                            CompilerDirectives.transferToInterpreterAndInvalidate();
                            JSRbci[curBCI] = Arrays.copyOf(JSRbci[curBCI], JSRbci[curBCI].length + 1);
                            JSRbci[curBCI][JSRbci[curBCI].length - 1] = targetBCI;
                            CompilerAsserts.partialEvaluationConstant(targetBCI);
                            checkBackEdge(curBCI, targetBCI, top, curOpcode);
                            if (instrument != null) {
                                nextStatementIndex = instrument.getStatementIndexAfterJump(statementIndex, curBCI, targetBCI);
                            }
                            top += Bytecodes.stackEffectOf(curOpcode);
                            curBCI = targetBCI;
                            continue loop;
                        }

                        case TABLESWITCH: {
                            int index = peekInt(frame, top - 1);
                            BytecodeTableSwitch switchHelper = bs.getBytecodeTableSwitch();
                            int low = switchHelper.lowKey(curBCI);
                            int high = switchHelper.highKey(curBCI);
                            assert low <= high;

                            // Interpreter uses direct lookup.
                            if (CompilerDirectives.inInterpreter()) {
                                int targetBCI;
                                if (low <= index && index <= high) {
                                    targetBCI = switchHelper.targetAt(curBCI, index - low);
                                } else {
                                    targetBCI = switchHelper.defaultTarget(curBCI);
                                }
                                CompilerAsserts.partialEvaluationConstant(targetBCI);
                                checkBackEdge(curBCI, targetBCI, top, curOpcode);
                                if (instrument != null) {
                                    nextStatementIndex = instrument.getStatementIndexAfterJump(statementIndex, curBCI, targetBCI);
                                }
                                top += Bytecodes.stackEffectOf(curOpcode);
                                curBCI = targetBCI;
                                continue loop;
                            }

                            // i could overflow if high == Integer.MAX_VALUE. This loops take
                            // that
                            // into account
                            for (int i = low; i != high + 1; ++i) {
                                if (i == index) {
                                    // Key found.
                                    int targetBCI = switchHelper.targetAt(curBCI, i - low);
                                    CompilerAsserts.partialEvaluationConstant(targetBCI);
                                    checkBackEdge(curBCI, targetBCI, top, curOpcode);
                                    if (instrument != null) {
                                        nextStatementIndex = instrument.getStatementIndexAfterJump(statementIndex, curBCI, targetBCI);
                                    }
                                    top += Bytecodes.stackEffectOf(curOpcode);
                                    curBCI = targetBCI;
                                    continue loop;
                                }
                            }

                            // Key not found.
                            int targetBCI = switchHelper.defaultTarget(curBCI);
                            CompilerAsserts.partialEvaluationConstant(targetBCI);
                            checkBackEdge(curBCI, targetBCI, top, curOpcode);
                            if (instrument != null) {
                                nextStatementIndex = instrument.getStatementIndexAfterJump(statementIndex, curBCI, targetBCI);
                            }
                            top += Bytecodes.stackEffectOf(curOpcode);
                            curBCI = targetBCI;
                            continue loop;
                        }
                        case LOOKUPSWITCH: {
                            int key = peekInt(frame, top - 1);
                            BytecodeLookupSwitch switchHelper = bs.getBytecodeLookupSwitch();
                            int low = 0;
                            int high = switchHelper.numberOfCases(curBCI) - 1;
                            while (low <= high) {
                                int mid = (low + high) >>> 1;
                                int midVal = switchHelper.keyAt(curBCI, mid);
                                if (midVal < key) {
                                    low = mid + 1;
                                } else if (midVal > key) {
                                    high = mid - 1;
                                } else {
                                    // Key found.
                                    int targetBCI = curBCI + switchHelper.offsetAt(curBCI, mid);
                                    CompilerAsserts.partialEvaluationConstant(targetBCI);
                                    checkBackEdge(curBCI, targetBCI, top, curOpcode);
                                    if (instrument != null) {
                                        nextStatementIndex = instrument.getStatementIndexAfterJump(statementIndex, curBCI, targetBCI);
                                    }
                                    top += Bytecodes.stackEffectOf(curOpcode);
                                    curBCI = targetBCI;
                                    continue loop;
                                }
                            }

                            // Key not found.
                            int targetBCI = switchHelper.defaultTarget(curBCI);
                            CompilerAsserts.partialEvaluationConstant(targetBCI);
                            checkBackEdge(curBCI, targetBCI, top, curOpcode);
                            if (instrument != null) {
                                nextStatementIndex = instrument.getStatementIndexAfterJump(statementIndex, curBCI, targetBCI);
                            }
                            top += Bytecodes.stackEffectOf(curOpcode);
                            curBCI = targetBCI;
                            continue loop;
                        }
                        // @formatter:off
                        // Checkstyle: stop
                        case IRETURN: return notifyReturn(frame, statementIndex, exitMethodAndReturn(peekInt(frame, top - 1)));
                        case LRETURN: return notifyReturn(frame, statementIndex, exitMethodAndReturnObject(peekLong(frame, top - 1)));
                        case FRETURN: return notifyReturn(frame, statementIndex, exitMethodAndReturnObject(peekFloat(frame, top - 1)));
                        case DRETURN: return notifyReturn(frame, statementIndex, exitMethodAndReturnObject(peekDouble(frame, top - 1)));
                        case ARETURN: return notifyReturn(frame, statementIndex, exitMethodAndReturnObject(peekObject(frame, top - 1)));
                        case RETURN: return notifyReturn(frame, statementIndex, exitMethodAndReturn());

                        // TODO(peterssen): Order shuffled.
                        case GETSTATIC: // fall through
                        case GETFIELD: top += getField(frame, top, resolveField(curOpcode, bs.readCPI(curBCI)), curOpcode, statementIndex); break;
                        case PUTSTATIC: // fall through
                        case PUTFIELD: top += putField(frame, top, resolveField(curOpcode, bs.readCPI(curBCI)), curOpcode, statementIndex); break;

                        case INVOKEVIRTUAL: // fall through
                        case INVOKESPECIAL: // fall through
                        case INVOKESTATIC: // fall through

                        case INVOKEINTERFACE: top += quickenInvoke(frame, top, curBCI, curOpcode); break;

                        case NEW: putObject(frame, top, InterpreterToVM.newObject(resolveType(curOpcode, bs.readCPI(curBCI)))); break;
                        case NEWARRAY: putObject(frame, top - 1, InterpreterToVM.allocatePrimitiveArray(bs.readByte(curBCI), peekInt(frame, top - 1))); break;
                        case ANEWARRAY: putObject(frame, top - 1, allocateArray(resolveType(curOpcode, bs.readCPI(curBCI)), peekInt(frame, top - 1))); break;
                        case ARRAYLENGTH: putInt(frame, top - 1, InterpreterToVM.arrayLength(nullCheck(peekAndReleaseObject(frame, top - 1)))); break;

                        case ATHROW: throw new EspressoException(nullCheck(peekAndReleaseObject(frame, top - 1)));

                        case CHECKCAST: top += quickenCheckCast(frame, top, curBCI, curOpcode); break;
                        case INSTANCEOF: top += quickenInstanceOf(frame, top, curBCI, curOpcode); break;

                        case MONITORENTER: monitorEnter(frame, nullCheck(peekAndReleaseObject(frame, top - 1))); break;
                        case MONITOREXIT:  monitorExit(frame, nullCheck(peekAndReleaseObject(frame, top - 1))); break;

                        case WIDE:
                            CompilerAsserts.neverPartOfCompilation();
                            throw EspressoError.shouldNotReachHere("BytecodeStream.currentBC() should never return this bytecode.");
                        case MULTIANEWARRAY: top += allocateMultiArray(frame, top, resolveType(curOpcode, bs.readCPI(curBCI)), bs.readUByte(curBCI + 3)); break;

                        case BREAKPOINT:
                            CompilerAsserts.neverPartOfCompilation();
                            throw EspressoError.unimplemented(Bytecodes.nameOf(curOpcode) + " not supported.");

                        case INVOKEDYNAMIC: top += quickenInvokeDynamic(frame, top, curBCI, curOpcode); break;
                        case QUICK: top += nodes[bs.readCPI(curBCI)].invoke(frame); break;


                        default:
                            CompilerAsserts.neverPartOfCompilation();
                            throw EspressoError.shouldNotReachHere(Bytecodes.nameOf(curOpcode));
                    }

                    // @formatter:on
                    // Checkstyle: resume
                } catch (EspressoException | EspressoExitException e) {
                    if (instrument != null) {
                        instrument.notifyExceptionAt(frame, e, statementIndex);
                    }
                    throw e;
                } catch (OutOfMemoryError e) {
                    CompilerDirectives.transferToInterpreter();
                    // Free lots of memory (the host stack trace is ~5x bigger than the espresso
                    // one)
                    e.setStackTrace(EspressoContext.EMPTY_STACK);
                    for (int i = 0; i < getMethod().getMaxStackSize(); i++) {
                        // Free the current stack
                        putObject(frame, i, null);
                    }
                    throw getContext().getOutOfMemory();
                } catch (StackOverflowError e) {
                    // Free some memory
                    CompilerDirectives.transferToInterpreter();
                    throw getContext().getStackOverflow();
                } catch (ThreadDeath e) {
                    throw getMeta().throwEx(e.getClass());
                } catch (RuntimeException e) {
                    CompilerDirectives.transferToInterpreter();
                    throw e;
                } catch (Exception e) {
                    // TODO(garcia) Do not lose the cause.
                    // There should be no need to wrap exceptions in host language, as they
                    // should be handled one way or another by espresso itself.
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    e.printStackTrace();
                    throw EspressoError.shouldNotReachHere("Shouldn't see host exceptions here" + e);
                }
            } catch (EspressoException e) {
                // CompilerDirectives.transferToInterpreter();
                CompilerAsserts.partialEvaluationConstant(curBCI);
                if (e == getContext().getStackOverflow()) {
                    /*
                     * Stack Overflow management. All calls to stack manipulation are manually
                     * inlined to prevent another SOE.
                     *
                     * Note: no need to check for the stacktrace being null, as we reset the frames
                     * at each apparition of a host SOE.
                     */
                    if (SOEinfo != null) {
                        for (int i = 0; i < SOEinfo.length; i += 3) {
                            if (curBCI >= SOEinfo[i] && curBCI < SOEinfo[i + 1]) {
                                top = 0;
                                putObject(frame, 0, e.getExceptionObject());
                                top++;
                                curBCI = SOEinfo[i + 2];
                                continue loop;
                            }
                        }
                    }
                    throw e;
                }

                ExceptionHandler[] handlers = getMethod().getExceptionHandlers();
                ExceptionHandler handler = null;
                for (ExceptionHandler toCheck : handlers) {
                    if (curBCI >= toCheck.getStartBCI() && curBCI < toCheck.getEndBCI()) {
                        Klass catchType = null;
                        if (!toCheck.isCatchAll()) {
                            // exception handlers are similar to instanceof bytecodes, so we pass
                            // instanceof
                            catchType = resolveType(Bytecodes.INSTANCEOF, (char) toCheck.catchTypeCPI());
                        }

                        if (catchType == null || InterpreterToVM.instanceOf(e.getExceptionObject(), catchType)) {
                            // the first found exception handler is our exception handler
                            handler = toCheck;
                            break;
                        }
                    }
                }

                if (handler != null) {
                    top = 0;
                    putObject(frame, 0, e.getExceptionObject());
                    top++;
                    int targetBCI = handler.getHandlerBCI();
                    checkBackEdge(curBCI, targetBCI, top, NOP);
                    if (instrument != null) {
                        nextStatementIndex = instrument.getStatementIndexAfterJump(statementIndex, curBCI, targetBCI);
                    }
                    curBCI = targetBCI;
                    if (DEBUG_CATCH) {
                        reportCatch(e, curBCI, this);
                    }
                    continue loop; // skip bs.next()
                } else {
                    throw e;
                }
            } catch (VirtualMachineError e) {
                // TODO(peterssen): Host should not throw invalid VME (not in the boot classpath).
                Meta meta = EspressoLanguage.getCurrentContext().getMeta();
                StaticObject exceptionToHandle = meta.initEx(e.getClass());
                CompilerAsserts.partialEvaluationConstant(curBCI);
                ExceptionHandler[] handlers = getMethod().getExceptionHandlers();
                ExceptionHandler handler = null;
                for (ExceptionHandler toCheck : handlers) {
                    if (curBCI >= toCheck.getStartBCI() && curBCI < toCheck.getEndBCI()) {
                        Klass catchType = null;
                        if (!toCheck.isCatchAll()) {
                            // exception handlers are similar to instanceof bytecodes, so we pass
                            // instanceof
                            catchType = resolveType(Bytecodes.INSTANCEOF, (char) toCheck.catchTypeCPI());
                        }
                        if (catchType == null || InterpreterToVM.instanceOf(exceptionToHandle, catchType)) {
                            // the first found exception handler is our exception handler
                            handler = toCheck;
                            break;
                        }
                    }
                }
                if (handler != null) {
                    top = 0;
                    putObject(frame, 0, exceptionToHandle);
                    top++;
                    int targetBCI = handler.getHandlerBCI();
                    checkBackEdge(curBCI, targetBCI, top, NOP);
                    if (instrument != null) {
                        nextStatementIndex = instrument.getStatementIndexAfterJump(statementIndex, curBCI, targetBCI);
                    }
                    curBCI = targetBCI;
                    continue loop;
                } else {
                    throw new EspressoException(exceptionToHandle);
                }
            } catch (EspressoExitException e) {
                if (monitorSlot != null) {
                    getMonitorStack(frame).abort();
                }
                throw e;
            }
            top += Bytecodes.stackEffectOf(curOpcode);
            curBCI = bs.nextBCI(curBCI);
            if (instrument != null) {
                nextStatementIndex = instrument.getNextStatementIndex(statementIndex, curBCI);
            }
        }
    }

    int readBCI(FrameInstance frameInstance) {
        try {
            assert bciSlot != null;
            return frameInstance.getFrame(FrameInstance.FrameAccess.READ_ONLY).getInt(bciSlot);
        } catch (FrameSlotTypeException e) {
            throw EspressoError.shouldNotReachHere(e);
        }
    }

    private void monitorExit(VirtualFrame frame, StaticObject monitor) {
        unregisterMonitor(frame, monitor);
        InterpreterToVM.monitorExit(monitor);
    }

    private void unregisterMonitor(VirtualFrame frame, StaticObject monitor) {
        getMonitorStack(frame).exit(monitor, this);
    }

    private void monitorEnter(VirtualFrame frame, StaticObject monitor) {
        registerMonitor(frame, monitor);
        InterpreterToVM.monitorEnter(monitor);
    }

    private void registerMonitor(VirtualFrame frame, StaticObject monitor) {
        getMonitorStack(frame).enter(monitor);
    }

    private MonitorStack getMonitorStack(VirtualFrame frame) {
        Object frameResult = FrameUtil.getObjectSafe(frame, monitorSlot);
        assert frameResult instanceof MonitorStack;
        return (MonitorStack) frameResult;
    }

    private static class MonitorStack {
        private static int DEFAULT_CAPACITY = 4;

        private StaticObject[] monitors = new StaticObject[DEFAULT_CAPACITY];
        private int top = 0;
        private int capacity = DEFAULT_CAPACITY;

        private void enter(StaticObject monitor) {
            if (top >= capacity) {
                monitors = Arrays.copyOf(monitors, capacity <<= 1);
            }
            monitors[top++] = monitor;
        }

        private void exit(StaticObject monitor, BytecodeNode node) {
            if (top > 0 && monitor == monitors[top - 1]) {
                // Balanced locking: simply pop.
                monitors[--top] = null;
            } else {
                node.unbalancedMonitorProfile.enter();
                // Unbalanced locking: do the linear search.
                int i = top - 1;
                for (; i >= 0; i--) {
                    if (monitors[i] == monitor) {
                        System.arraycopy(monitors, i + 1, monitors, i, top - 1 - i);
                        monitors[--top] = null;
                        return;
                    }
                }
                // monitor not found. Not against the specs.
            }
        }

        private void abort() {
            for (int i = 0; i < top; i++) {
                StaticObject monitor = monitors[i];
                try {
                    InterpreterToVM.monitorExit(monitor);
                } catch (Throwable e) {
                    /* ignore */
                }
            }
        }
    }

    private Object notifyReturn(VirtualFrame frame, int statementIndex, Object toReturn) {
        if (instrumentation != null) {
            instrumentation.notifyReturn(frame, statementIndex, toReturn);
        }
        return toReturn;
    }

    public void notifyEntry(VirtualFrame frame) {
        if (instrumentation != null) {
            instrumentation.notifyEntry(frame);
        }
    }

    public InstrumentableNode materializeInstrumentableNodes(Set<Class<? extends Tag>> materializedTags) {
        InstrumentationSupport info = this.instrumentation;
        if (info == null && materializedTags.contains(StatementTag.class)) {
            Lock lock = getLock();
            lock.lock();
            try {
                info = this.instrumentation;
                // double checked locking
                if (info == null) {
                    this.instrumentation = info = insert(new InstrumentationSupport(getMethod()));
                    // the debug info contains instrumentable nodes so we need to notify for
                    // instrumentation updates.
                    notifyInserted(info);
                }
            } finally {
                lock.unlock();
            }
        }
        return this;
    }

    private boolean takeBranch(VirtualFrame frame, int top, int opCode) {
        assert Bytecodes.isBranch(opCode);
        // @formatter:off
        // Checkstyle: stop
        switch (opCode) {
            case IFEQ      : return peekInt(frame, top - 1) == 0;
            case IFNE      : return peekInt(frame, top - 1) != 0;
            case IFLT      : return peekInt(frame, top - 1)  < 0;
            case IFGE      : return peekInt(frame, top - 1) >= 0;
            case IFGT      : return peekInt(frame, top - 1)  > 0;
            case IFLE      : return peekInt(frame, top - 1) <= 0;
            case IF_ICMPEQ : return peekInt(frame, top - 1) == peekInt(frame, top - 2);
            case IF_ICMPNE : return peekInt(frame, top - 1) != peekInt(frame, top - 2);
            case IF_ICMPLT : return peekInt(frame, top - 1)  > peekInt(frame, top - 2);
            case IF_ICMPGE : return peekInt(frame, top - 1) <= peekInt(frame, top - 2);
            case IF_ICMPGT : return peekInt(frame, top - 1)  < peekInt(frame, top - 2);
            case IF_ICMPLE : return peekInt(frame, top - 1) >= peekInt(frame, top - 2);
            case IF_ACMPEQ : return peekAndReleaseObject(frame, top - 1) == peekAndReleaseObject(frame, top - 2);
            case IF_ACMPNE : return peekAndReleaseObject(frame, top - 1) != peekAndReleaseObject(frame, top - 2);
            case GOTO      : // fall though
            case GOTO_W    : return true; // unconditional
            case IFNULL    : return StaticObject.isNull(peekAndReleaseObject(frame, top - 1));
            case IFNONNULL : return StaticObject.notNull(peekAndReleaseObject(frame, top - 1));
            default        :
                throw EspressoError.shouldNotReachHere("non-branching bytecode");
        }
        // @formatter:on
        // Checkstyle: resume
    }

    private int checkBackEdge(int curBCI, int targetBCI, int top, int opCode) {
        int newTop = top + Bytecodes.stackEffectOf(opCode);
        if (targetBCI <= curBCI) {
            checkStopping(curBCI, targetBCI);
            if (CompilerDirectives.inInterpreter()) {
                LoopNode.reportLoopCount(this, 1);
            }
        }
        return newTop;
    }

    private void checkStopping(int curBCI, int targetBCI) {
        if (getContext().shouldCheckDeprecationStatus()) {
            if (targetBCI <= curBCI) {
                Target_java_lang_Thread.checkDeprecatedState(getMeta(), getContext().getCurrentThread());
            }
        }
    }

    @TruffleBoundary
    static private void reportThrow(int curBCI, Method method, StaticObject e) {
        if (method.getName().toString().contains("refill") ||
                        method.getName().toString().contains("loadClass") ||
                        method.getName().toString().contains("findClass")) {
            return;
        }
        System.err.println(e.getKlass().getType() + ": " + EspressoException.getMessage(e) + "\n\t" + method.report(curBCI));
    }

    @TruffleBoundary
    static private void reportRuntimeException(RuntimeException e, int curBCI, BytecodeNode thisNode) {
        Method m = thisNode.getMethod();
        System.err.println("Internal error (caught in invocation): " + m.report(curBCI));
        if (e.getCause() != null) {
            e.getCause().printStackTrace();
        }
        e.printStackTrace();
    }

    @TruffleBoundary
    static private void reportVMError(VirtualMachineError e, int curBCI, BytecodeNode thisNode) {
        Method m = thisNode.getMethod();
        System.err.println("Internal error (caught in invocation): " + m.report(curBCI));
        e.printStackTrace();
    }

    @TruffleBoundary
    static private void reportCatch(EspressoException e, int curBCI, BytecodeNode thisNode) {
        if (thisNode.getMethod().getName().toString().contains("refill") ||
                        thisNode.getMethod().getName().toString().contains("loadClass") ||
                        thisNode.getMethod().getName().toString().contains("findClass")) {
            return;
        }
        System.err.println("Caught: " + e.getExceptionObject().getKlass().getType() + ": " + e.getMessage() +
                        "\n\t In: " + thisNode + " at line: " + thisNode.getMethod().BCItoLineNumber(curBCI));
    }

    private JavaKind peekKind(VirtualFrame frame, int slot) {
        if (frame.isObject(stackSlots[slot])) {
            return JavaKind.Object;
        }
        if (frame.isLong(stackSlots[slot])) {
            return JavaKind.Long;
        }
        throw EspressoError.shouldNotReachHere();
    }

    // region Operand stack shuffling

    private void dup1(VirtualFrame frame, int top) {
        // value1 -> value1, value1
        JavaKind k1 = peekKind(frame, top - 1);
        Object v1 = peekValue(frame, top - 1);
        putKindUnsafe1(frame, top, v1, k1);
    }

    private void dupx1(VirtualFrame frame, int top) {
        // value2, value1 -> value1, value2, value1
        JavaKind k1 = peekKind(frame, top - 1);
        JavaKind k2 = peekKind(frame, top - 2);
        Object v1 = peekValue(frame, top - 1);
        Object v2 = peekValue(frame, top - 2);
        putKindUnsafe1(frame, top - 2, v1, k1);
        putKindUnsafe1(frame, top - 1, v2, k2);
        putKindUnsafe1(frame, top, v1, k1);
    }

    private void dupx2(VirtualFrame frame, int top) {
        // value3, value2, value1 -> value1, value3, value2, value1
        JavaKind k1 = peekKind(frame, top - 1);
        JavaKind k2 = peekKind(frame, top - 2);
        JavaKind k3 = peekKind(frame, top - 3);
        Object v1 = peekValue(frame, top - 1);
        Object v2 = peekValue(frame, top - 2);
        Object v3 = peekValue(frame, top - 3);
        putKindUnsafe1(frame, top - 3, v1, k1);
        putKindUnsafe1(frame, top - 2, v3, k3);
        putKindUnsafe1(frame, top - 1, v2, k2);
        putKindUnsafe1(frame, top, v1, k1);
    }

    private void dup2(VirtualFrame frame, int top) {
        // {value2, value1} -> {value2, value1}, {value2, value1}
        JavaKind k1 = peekKind(frame, top - 1);
        JavaKind k2 = peekKind(frame, top - 2);
        Object v1 = peekValue(frame, top - 1);
        Object v2 = peekValue(frame, top - 2);
        putKindUnsafe1(frame, top, v2, k2);
        putKindUnsafe1(frame, top + 1, v1, k1);
    }

    private void swapSingle(VirtualFrame frame, int top) {
        // value2, value1 -> value1, value2
        JavaKind k1 = peekKind(frame, top - 1);
        JavaKind k2 = peekKind(frame, top - 2);
        Object v1 = peekValue(frame, top - 1);
        Object v2 = peekValue(frame, top - 2);
        putKindUnsafe1(frame, top - 1, v2, k2);
        putKindUnsafe1(frame, top - 2, v1, k1);
    }

    private void dup2x1(VirtualFrame frame, int top) {
        // value3, {value2, value1} -> {value2, value1}, value3, {value2, value1}
        JavaKind k1 = peekKind(frame, top - 1);
        JavaKind k2 = peekKind(frame, top - 2);
        JavaKind k3 = peekKind(frame, top - 3);
        Object v1 = peekValue(frame, top - 1);
        Object v2 = peekValue(frame, top - 2);
        Object v3 = peekValue(frame, top - 3);
        putKindUnsafe1(frame, top - 3, v2, k2);
        putKindUnsafe1(frame, top - 2, v1, k1);
        putKindUnsafe1(frame, top - 1, v3, k3);
        putKindUnsafe1(frame, top - 0, v2, k2);
        putKindUnsafe1(frame, top + 1, v1, k1);
    }

    private void dup2x2(VirtualFrame frame, int top) {
        // {value4, value3}, {value2, value1} -> {value2, value1}, {value4, value3}, {value2,
        // value1}
        JavaKind k1 = peekKind(frame, top - 1);
        JavaKind k2 = peekKind(frame, top - 2);
        JavaKind k3 = peekKind(frame, top - 3);
        JavaKind k4 = peekKind(frame, top - 4);
        Object v1 = peekValue(frame, top - 1);
        Object v2 = peekValue(frame, top - 2);
        Object v3 = peekValue(frame, top - 3);
        Object v4 = peekValue(frame, top - 4);
        putKindUnsafe1(frame, top - 4, v2, k2);
        putKindUnsafe1(frame, top - 3, v1, k1);
        putKindUnsafe1(frame, top - 2, v4, k4);
        putKindUnsafe1(frame, top - 1, v3, k3);
        putKindUnsafe1(frame, top + 0, v2, k2);
        putKindUnsafe1(frame, top + 1, v1, k1);
    }

    // endregion Operand stack shuffling

    @ExplodeLoop
    @SuppressWarnings("unused")
    private ExceptionHandler resolveExceptionHandlers(int bci, StaticObject ex) {
        CompilerAsserts.partialEvaluationConstant(bci);
        ExceptionHandler[] handlers = getMethod().getExceptionHandlers();
        ExceptionHandler resolved = null;
        for (ExceptionHandler toCheck : handlers) {
            if (bci >= toCheck.getStartBCI() && bci < toCheck.getEndBCI()) {
                Klass catchType = null;
                if (!toCheck.isCatchAll()) {
                    // exception handlers are similar to instanceof bytecodes, so we pass instanceof
                    catchType = resolveType(Bytecodes.INSTANCEOF, (char) toCheck.catchTypeCPI());
                }
                if (catchType == null || InterpreterToVM.instanceOf(ex, catchType)) {
                    // the first found exception handler is our exception handler
                    resolved = toCheck;
                    break;
                }
            }
        }
        return resolved;
    }

    @ExplodeLoop
    private static Object[] copyOfRange(Object[] src, int from, int toExclusive) {
        int len = toExclusive - from;
        Object[] dst = new Object[len];
        for (int i = 0; i < len; ++i) {
            dst[i] = src[i + from];
        }
        return dst;
    }

    private void putPoolConstant(final VirtualFrame frame, int top, char cpi, int opcode) {
        assert opcode == LDC || opcode == LDC_W || opcode == LDC2_W;
        ConstantPool pool = getConstantPool();
        PoolConstant constant = pool.at(cpi);
        if (constant instanceof IntegerConstant) {
            assert opcode == LDC || opcode == LDC_W;
            putInt(frame, top, ((IntegerConstant) constant).value());
        } else if (constant instanceof LongConstant) {
            assert opcode == LDC2_W;
            putLong(frame, top, ((LongConstant) constant).value());
        } else if (constant instanceof DoubleConstant) {
            assert opcode == LDC2_W;
            putDouble(frame, top, ((DoubleConstant) constant).value());
        } else if (constant instanceof FloatConstant) {
            assert opcode == LDC || opcode == LDC_W;
            putFloat(frame, top, ((FloatConstant) constant).value());
        } else if (constant instanceof StringConstant) {
            assert opcode == LDC || opcode == LDC_W;
            StaticObject internedString = getConstantPool().resolvedStringAt(cpi);
            putObject(frame, top, internedString);
        } else if (constant instanceof ClassConstant) {
            assert opcode == LDC || opcode == LDC_W;
            Klass klass = getConstantPool().resolvedKlassAt(getMethod().getDeclaringKlass(), cpi);
            putObject(frame, top, klass.mirror());
        } else if (constant instanceof MethodHandleConstant) {
            assert opcode == LDC || opcode == LDC_W;
            StaticObject methodHandle = getConstantPool().resolvedMethodHandleAt(getMethod().getDeclaringKlass(), cpi);
            putObject(frame, top, methodHandle);
        } else if (constant instanceof MethodTypeConstant) {
            assert opcode == LDC || opcode == LDC_W;
            StaticObject methodType = getConstantPool().resolvedMethodTypeAt(getMethod().getDeclaringKlass(), cpi);
            putObject(frame, top, methodType);
        } else {
            CompilerAsserts.neverPartOfCompilation();
            throw EspressoError.unimplemented(constant.toString());
        }
    }

    protected RuntimeConstantPool getConstantPool() {
        return getMethod().getRuntimeConstantPool();
    }

    @TruffleBoundary
    private BootstrapMethodsAttribute getBootstrapMethods() {
        return (BootstrapMethodsAttribute) (getMethod().getDeclaringKlass()).getAttribute(BootstrapMethodsAttribute.NAME);
    }

    // region Bytecode quickening

    private char addQuickNode(QuickNode node) {
        CompilerAsserts.neverPartOfCompilation();
        Objects.requireNonNull(node);
        nodes = Arrays.copyOf(nodes, nodes.length + 1);
        int nodeIndex = nodes.length - 1; // latest empty slot
        nodes[nodeIndex] = insert(node);
        return (char) nodeIndex;
    }

    private void patchBci(int bci, byte opcode, char nodeIndex) {
        CompilerAsserts.neverPartOfCompilation();
        assert Bytecodes.isQuickened(opcode);
        byte[] code = getMethod().getCode();

        int oldBC = code[bci];
        assert Bytecodes.lengthOf(oldBC) >= 3 : "cannot patch slim bc";
        code[bci] = opcode;
        code[bci + 1] = (byte) ((nodeIndex >> 8) & 0xFF);
        code[bci + 2] = (byte) ((nodeIndex) & 0xFF);

        // NOP-padding.
        for (int i = 3; i < Bytecodes.lengthOf(oldBC); ++i) {
            code[bci + i] = (byte) NOP;
        }
    }

    private QuickNode injectQuick(int curBCI, QuickNode quick) {
        injectAndCallCount.inc();
        CompilerAsserts.neverPartOfCompilation();
        int nodeIndex = addQuickNode(quick);
        patchBci(curBCI, (byte) QUICK, (char) nodeIndex);
        return quick;
    }

    private int quickenCheckCast(final VirtualFrame frame, int top, int curBCI, int opCode) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        assert opCode == CHECKCAST;
        QuickNode quick;
        synchronized (this) {
            if (bs.currentBC(curBCI) == QUICK) {
                quick = nodes[bs.readCPI(curBCI)];
            } else {
                Klass typeToCheck;
                typeToCheck = resolveType(opCode, bs.readCPI(curBCI));
                quick = injectQuick(curBCI, CheckCastNodeGen.create(typeToCheck, top, curBCI));
            }
        }
        return quick.invoke(frame) - Bytecodes.stackEffectOf(opCode);
    }

    private int quickenInstanceOf(final VirtualFrame frame, int top, int curBCI, int opCode) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        assert opCode == INSTANCEOF;
        QuickNode quick;
        synchronized (this) {
            if (bs.currentBC(curBCI) == QUICK) {
                quick = nodes[bs.readCPI(curBCI)];
            } else {
                Klass typeToCheck;
                typeToCheck = resolveType(opCode, bs.readCPI(curBCI));
                quick = injectQuick(curBCI, InstanceOfNodeGen.create(typeToCheck, top, curBCI));
            }
        }
        return quick.invoke(frame) - Bytecodes.stackEffectOf(opCode);
    }

    @SuppressWarnings("unused")
    private static boolean isReturn(int opCode) {
        return opCode >= IRETURN && opCode <= RETURN;
    }

    private int quickenInvoke(final VirtualFrame frame, int top, int curBCI, int opCode) {
        invokeNodes.inc();
        CompilerDirectives.transferToInterpreterAndInvalidate();
        assert Bytecodes.isInvoke(opCode);
        QuickNode quick;
        synchronized (this) {
            if (bs.currentBC(curBCI) == QUICK) {
                quick = nodes[bs.readCPI(curBCI)];
            } else {
                // During resolution of the symbolic reference to the method, any of the exceptions
                // pertaining to method resolution (§5.4.3.3) can be thrown.
                Method resolutionSeed = resolveMethod(opCode, bs.readCPI(curBCI));
                QuickNode invoke = dispatchQuickened(top, curBCI, opCode, resolutionSeed, getContext().InlineFieldAccessors);
                quick = injectQuick(curBCI, invoke);
            }
        }
        // Perform the call outside of the lock.
        return quick.invoke(frame) - Bytecodes.stackEffectOf(opCode);
    }

    /**
     * Revert speculative quickening e.g. revert inlined fields accessors to a normal invoke.
     * INVOKEVIRTUAL -> QUICK (InlinedGetter/SetterNode) -> QUICK (InvokeVirtualNode)
     */
    int reQuickenInvoke(final VirtualFrame frame, int top, int curBCI, int opCode, Method resolutionSeed) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        assert Bytecodes.isInvoke(opCode);
        QuickNode invoke = null;
        synchronized (this) {
            assert bs.currentBC(curBCI) == QUICK;
            invoke = dispatchQuickened(top, curBCI, opCode, resolutionSeed, false);
            char cpi = bs.readCPI(curBCI);
            nodes[cpi] = nodes[cpi].replace(invoke);
        }
        // Perform the call outside of the lock.
        return invoke.invoke(frame);
    }

    private QuickNode dispatchQuickened(int top, int curBCI, int opCode, Method _resolutionSeed, boolean allowFieldAccessInlining) {
        assert !allowFieldAccessInlining || EspressoLanguage.getCurrentContext().InlineFieldAccessors;
        QuickNode invoke;
        Method resolutionSeed = _resolutionSeed;
        switch (opCode) {
            case INVOKESTATIC:
                // Otherwise, if the resolved method is an instance method, the invokestatic
                // instruction throws an IncompatibleClassChangeError.
                if (!resolutionSeed.isStatic()) {
                    CompilerDirectives.transferToInterpreter();
                    throw getMeta().throwEx(IncompatibleClassChangeError.class);
                }
                break;
            case INVOKEINTERFACE:
                // Otherwise, if the resolved method is static or private, the invokeinterface
                // instruction throws an IncompatibleClassChangeError.
                if (resolutionSeed.isStatic() || resolutionSeed.isPrivate()) {
                    CompilerDirectives.transferToInterpreter();
                    throw getMeta().throwEx(IncompatibleClassChangeError.class);
                }
                break;
            case INVOKEVIRTUAL:
                // Otherwise, if the resolved method is a class (static) method, the invokevirtual
                // instruction throws an IncompatibleClassChangeError.
                if (resolutionSeed.isStatic()) {
                    CompilerDirectives.transferToInterpreter();
                    throw getMeta().throwEx(IncompatibleClassChangeError.class);
                }
                break;
            case INVOKESPECIAL:
                // Otherwise, if the resolved method is an instance initialization method, and the
                // class in which it is declared is not the class symbolically referenced by the
                // instruction, a NoSuchMethodError is thrown.
                if (resolutionSeed.isConstructor()) {
                    if (resolutionSeed.getDeclaringKlass().getName() != getConstantPool().methodAt(bs.readCPI(curBCI)).getHolderKlassName(getConstantPool())) {
                        CompilerDirectives.transferToInterpreter();
                        throw getMeta().throwEx(NoSuchMethodError.class);
                    }
                }
                // Otherwise, if the resolved method is a class (static) method, the invokespecial
                // instruction throws an IncompatibleClassChangeError.
                if (resolutionSeed.isStatic()) {
                    CompilerDirectives.transferToInterpreter();
                    throw getMeta().throwEx(IncompatibleClassChangeError.class);
                }
                // If all of the following are true, let C be the direct superclass of the current
                // class:
                //
                // * The resolved method is not an instance initialization method (§2.9).
                //
                // * If the symbolic reference names a class (not an interface), then that class is
                // a superclass of the current class.
                //
                // * The ACC_SUPER flag is set for the class file (§4.1). In Java SE 8 and above,
                // the Java Virtual Machine considers the ACC_SUPER flag to be set in every class
                // file, regardless of the actual value of the flag in the class file and the
                // version of the class file.
                if (!resolutionSeed.isConstructor()) {
                    Klass declaringKlass = getMethod().getDeclaringKlass();
                    Klass symbolicRef = ((MethodRefConstant.Indexes) getConstantPool().methodAt(bs.readCPI(curBCI))).getResolvedHolderKlass(declaringKlass, getConstantPool());
                    if (!symbolicRef.isInterface() && symbolicRef != declaringKlass && declaringKlass.getSuperKlass() != null && symbolicRef != declaringKlass.getSuperKlass() &&
                                    symbolicRef.isAssignableFrom(declaringKlass)) {
                        resolutionSeed = declaringKlass.getSuperKlass().lookupMethod(resolutionSeed.getName(), resolutionSeed.getRawSignature(), declaringKlass);
                    }
                }
                break;
            default:
                throw EspressoError.unimplemented("Quickening for " + Bytecodes.nameOf(opCode));
        }

        if (allowFieldAccessInlining && resolutionSeed.isInlinableGetter()) {
            invoke = InlinedGetterNode.create(resolutionSeed, top, opCode, curBCI);
        } else if (allowFieldAccessInlining && resolutionSeed.isInlinableSetter()) {
            invoke = InlinedSetterNode.create(resolutionSeed, top, opCode, curBCI);
        } else if (resolutionSeed.isMethodHandleIntrinsic()) {
            invoke = new MethodHandleInvokeNode(resolutionSeed, top, curBCI);
        } else if (opCode == INVOKEINTERFACE && resolutionSeed.getITableIndex() < 0) {
            // Can happen in old classfiles that calls j.l.Object on interfaces.
            invoke = InvokeVirtualNodeGen.create(resolutionSeed, top, curBCI);
        } else if (opCode == INVOKEVIRTUAL && (resolutionSeed.isFinalFlagSet() || resolutionSeed.getDeclaringKlass().isFinalFlagSet() || resolutionSeed.isPrivate())) {
            invoke = new InvokeSpecialNode(resolutionSeed, top, curBCI);
        } else {
            // @formatter:off
            // Checkstyle: stop
            switch (opCode) {
                case INVOKESTATIC    : invoke = new InvokeStaticNode(resolutionSeed, top, curBCI);          break;
                case INVOKEINTERFACE : invoke = InvokeInterfaceNodeGen.create(resolutionSeed, top, curBCI); break;
                case INVOKEVIRTUAL   : invoke = InvokeVirtualNodeGen.create(resolutionSeed, top, curBCI);   break;
                case INVOKESPECIAL   : invoke = new InvokeSpecialNode(resolutionSeed, top, curBCI);         break;
                default              :
                    throw EspressoError.unimplemented("Quickening for " + Bytecodes.nameOf(opCode));
            }
            // @formatter:on
            // Checkstyle: resume
        }
        return invoke;
    }

    private int quickenInvokeDynamic(final VirtualFrame frame, int top, int curBCI, int opCode) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        assert (Bytecodes.INVOKEDYNAMIC == opCode);
        RuntimeConstantPool pool = null;
        InvokeDynamicConstant inDy = null;
        QuickNode quick = null;
        synchronized (this) {
            if (bs.currentBC(curBCI) == QUICK) {
                // Check if someone did the job for us. Defer the call until we are out of the lock.
                quick = nodes[bs.readCPI(curBCI)];
            } else {
                pool = getConstantPool();
                // fetch indy under lock.
                inDy = ((InvokeDynamicConstant) pool.at(bs.readCPI(curBCI)));
            }
        }
        if (quick != null) {
            // Do invocation outside of the lock.
            return quick.invoke(frame) - Bytecodes.stackEffectOf(opCode);
        }

        assert pool != null && inDy != null;

        // Do the long stuff outside the lock.
        Meta meta = getMeta();

        // Indy constant resolving.
        BootstrapMethodsAttribute bms = getBootstrapMethods();
        NameAndTypeConstant specifier = pool.nameAndTypeAt(inDy.getNameAndTypeIndex());

        assert (bms != null);
        // TODO(garcia) cache bootstrap method resolution
        // Bootstrap method resolution
        BootstrapMethodsAttribute.Entry bsEntry = bms.at(inDy.getBootstrapMethodAttrIndex());

        Klass declaringKlass = getMethod().getDeclaringKlass();
        StaticObject bootstrapmethodMethodHandle = pool.resolvedMethodHandleAt(declaringKlass, bsEntry.getBootstrapMethodRef());

        StaticObject[] args = new StaticObject[bsEntry.numBootstrapArguments()];
        // @formatter:off
        // Checkstyle: stop
        for (int i = 0; i < bsEntry.numBootstrapArguments(); i++) {
            PoolConstant pc = pool.at(bsEntry.argAt(i));
            switch (pc.tag()) {
                case METHODHANDLE : args[i] = pool.resolvedMethodHandleAt(declaringKlass, bsEntry.argAt(i));    break;
                case METHODTYPE   : args[i] = pool.resolvedMethodTypeAt(declaringKlass, bsEntry.argAt(i));      break;
                case CLASS        : args[i] = pool.resolvedKlassAt(declaringKlass, bsEntry.argAt(i)).mirror();  break;
                case STRING       : args[i] = pool.resolvedStringAt(bsEntry.argAt(i));                          break;
                case INTEGER      : args[i] = meta.boxInteger(pool.intAt(bsEntry.argAt(i)));                    break;
                case LONG         : args[i] = meta.boxLong(pool.longAt(bsEntry.argAt(i)));                      break;
                case DOUBLE       : args[i] = meta.boxDouble(pool.doubleAt(bsEntry.argAt(i)));                  break;
                case FLOAT        : args[i] = meta.boxFloat(pool.floatAt(bsEntry.argAt(i)));                    break;
                default           : throw EspressoError.shouldNotReachHere();
            }
        }
        // @formatter:on
        // Checkstyle: resume

        // Preparing Bootstrap call.
        StaticObject name = meta.toGuestString(specifier.getName(pool));
        Symbol<Signature> invokeSignature = Signatures.check(specifier.getDescriptor(pool));
        Symbol<Type>[] parsedInvokeSignature = getSignatures().parsed(invokeSignature);
        StaticObject methodType = signatureToMethodType(parsedInvokeSignature, declaringKlass, getMeta());
        StaticObject appendix = StaticObject.createArray(meta.Object_array, new StaticObject[1]);

        StaticObject memberName = (StaticObject) meta.MethodHandleNatives_linkCallSite.invokeDirect(
                        null,
                        declaringKlass.mirror(),
                        bootstrapmethodMethodHandle,
                        name, methodType,
                        StaticObject.createArray(meta.Object_array, args),
                        appendix);

        StaticObject unboxedAppendix = appendix.get(0);

        // re-lock to check if someone did the job for us, since this was a heavy operation.

        synchronized (this) {
            if (bs.currentBC(curBCI) == QUICK) {
                // someone beat us to it, just trust him.
                quick = nodes[bs.readCPI(curBCI)];
            } else {
                quick = injectQuick(curBCI, new InvokeDynamicCallSiteNode(memberName, unboxedAppendix, parsedInvokeSignature, meta, top, curBCI));
            }
        }
        return quick.invoke(frame) - Bytecodes.stackEffectOf(opCode);
    }

    public static StaticObject signatureToMethodType(Symbol<Type>[] signature, Klass declaringKlass, Meta meta) {
        Symbol<Type> rt = Signatures.returnType(signature);
        int pcount = Signatures.parameterCount(signature, false);

        StaticObject[] ptypes = new StaticObject[pcount];
        try {
            for (int i = 0; i < pcount; i++) {
                Symbol<Type> paramType = Signatures.parameterType(signature, i);
                ptypes[i] = meta.resolveSymbol(paramType, declaringKlass.getDefiningClassLoader()).mirror();
            }
        } catch (Throwable e) {
            throw meta.throwEx(NoClassDefFoundError.class);
        }
        StaticObject rtype;
        try {
            rtype = meta.resolveSymbol(rt, declaringKlass.getDefiningClassLoader()).mirror();
        } catch (Throwable e) {
            throw meta.throwEx(BootstrapMethodError.class);
        }
        return (StaticObject) meta.MethodHandleNatives_findMethodHandleType.invokeDirect(
                        null,
                        rtype, StaticObject.createArray(meta.Class_Array, ptypes));
    }
    // endregion Bytecode quickening

    // region Class/Method/Field resolution

    private Klass resolveType(@SuppressWarnings("unused") int opcode, char cpi) {
        // TODO(peterssen): Check opcode.
        return getConstantPool().resolvedKlassAt(getMethod().getDeclaringKlass(), cpi);
    }

    private Method resolveMethod(@SuppressWarnings("unused") int opcode, char cpi) {
        // TODO(peterssen): Check opcode.
        return getConstantPool().resolvedMethodAt(getMethod().getDeclaringKlass(), cpi);
    }

    private Field resolveField(@SuppressWarnings("unused") int opcode, char cpi) {
        // TODO(peterssen): Check opcode.
        return getConstantPool().resolvedFieldAt(getMethod().getDeclaringKlass(), cpi);
    }

    // endregion Class/Method/Field resolution

    // region Instance/array allocation

    @TruffleBoundary
    private static StaticObject allocateArray(Klass componentType, int length) {
        assert !componentType.isPrimitive();
        return InterpreterToVM.newArray(componentType, length);
    }

    @ExplodeLoop
    private int allocateMultiArray(final VirtualFrame frame, int top, Klass klass, int allocatedDimensions) {
        assert klass.isArray();
        CompilerAsserts.partialEvaluationConstant(allocatedDimensions);
        CompilerAsserts.partialEvaluationConstant(klass);
        int[] dimensions = new int[allocatedDimensions];
        for (int i = 0; i < allocatedDimensions; ++i) {
            dimensions[i] = peekInt(frame, top - allocatedDimensions + i);
        }
        putObject(frame, top - allocatedDimensions, getInterpreterToVM().newMultiArray(klass.getComponentType(), dimensions));
        return -allocatedDimensions; // Does not include the created (pushed) array.
    }

    // endregion Instance/array allocation

    // region Method return

    private Object exitMethodAndReturn(int result) {
        // @formatter:off
        // Checkstyle: stop
        switch (Signatures.returnKind(getMethod().getParsedSignature())) {
            case Boolean : return result != 0;
            case Byte    : return (byte) result;
            case Short   : return (short) result;
            case Char    : return (char) result;
            case Int     : return result;
            default      : throw EspressoError.shouldNotReachHere("unexpected kind");
        }
        // @formatter:on
        // Checkstyle: resume
    }

    private static Object exitMethodAndReturnObject(Object result) {
        return result;
    }

    private static Object exitMethodAndReturn() {
        return exitMethodAndReturnObject(StaticObject.VOID);
    }

    // endregion Method return

    // region Arithmetic/binary operations

    private static int divInt(int divisor, int dividend) {
        return dividend / divisor;
    }

    private static long divLong(long divisor, long dividend) {
        return dividend / divisor;
    }

    private static float divFloat(float divisor, float dividend) {
        return dividend / divisor;
    }

    private static double divDouble(double divisor, double dividend) {
        return dividend / divisor;
    }

    private static int remInt(int divisor, int dividend) {
        return dividend % divisor;
    }

    private static long remLong(long divisor, long dividend) {
        return dividend % divisor;
    }

    private static float remFloat(float divisor, float dividend) {
        return dividend % divisor;
    }

    private static double remDouble(double divisor, double dividend) {
        return dividend % divisor;
    }

    private static int shiftLeftInt(int bits, int value) {
        return value << bits;
    }

    private static long shiftLeftLong(int bits, long value) {
        return value << bits;
    }

    private static int shiftRightSignedInt(int bits, int value) {
        return value >> bits;
    }

    private static long shiftRightSignedLong(int bits, long value) {
        return value >> bits;
    }

    private static int shiftRightUnsignedInt(int bits, int value) {
        return value >>> bits;
    }

    private static long shiftRightUnsignedLong(int bits, long value) {
        return value >>> bits;
    }

    // endregion Arithmetic/binary operations

    // region Comparisons

    private static int compareLong(long y, long x) {
        return Long.compare(x, y);
    }

    private static int compareFloatGreater(float y, float x) {
        return (x < y ? -1 : ((x == y) ? 0 : 1));
    }

    private static int compareFloatLess(float y, float x) {
        return (x > y ? 1 : ((x == y) ? 0 : -1));
    }

    private static int compareDoubleGreater(double y, double x) {
        return (x < y ? -1 : ((x == y) ? 0 : 1));
    }

    private static int compareDoubleLess(double y, double x) {
        return (x > y ? 1 : ((x == y) ? 0 : -1));
    }
    // endregion Comparisons

    // region Misc. checks

    private StaticObject nullCheck(StaticObject value) {
        if (StaticObject.isNull(value)) {
            // TODO(peterssen): Profile whether null was hit or not.
            Meta meta = getMethod().getContext().getMeta();
            throw meta.throwEx(meta.NullPointerException);
        }
        return value;
    }

    private int checkNonZero(int value) {
        if (value != 0) {
            return value;
        }
        throw getMeta().throwExWithMessage(ArithmeticException.class, "/ by zero");
    }

    private long checkNonZero(long value) {
        if (value != 0L) {
            return value;
        }
        throw getMeta().throwExWithMessage(ArithmeticException.class, "/ by zero");
    }

    // endregion Misc. checks

    // region Field read/write

    /**
     * Returns the stack effect (slot delta) that cannot be inferred solely from the bytecode. e.g.
     * GETFIELD always pops the receiver, but the (read) result size (1 or 2) is unknown.
     *
     * <pre>
     *   top += putField(frame, top, resolveField(...)); break; // stack effect that depends on the field
     *   top += Bytecodes.stackEffectOf(curOpcode); // stack effect that depends solely on PUTFIELD.
     *   // at this point `top` must have the correct value.
     *   curBCI = bs.next(curBCI);
     * </pre>
     */
    private int putField(final VirtualFrame frame, int top, Field field, int opcode, int statementIndex) {
        assert opcode == PUTFIELD || opcode == PUTSTATIC;

        if (opcode == PUTFIELD) {
            // Otherwise, if the resolved field is a static field, putfield throws an
            // IncompatibleClassChangeError.
            if (field.isStatic()) {
                CompilerDirectives.transferToInterpreter();
                throw getMeta().throwEx(IncompatibleClassChangeError.class);
            }
            // Otherwise, if the field is final, it must be declared in the current class, and
            // the instruction must occur in an instance initialization method (<init>) of the
            // current class. Otherwise, an IllegalAccessError is thrown.
            if (field.isFinalFlagSet()) {
                // && getMethod().isConstructor()
                // Enforced in class files >= v53 (9).
                if (!(field.getDeclaringKlass() == getMethod().getDeclaringKlass())) {
                    CompilerDirectives.transferToInterpreter();
                    throw getMeta().throwEx(IllegalAccessError.class);
                }
            }
        } else if (opcode == PUTSTATIC) {
            // Otherwise, if the resolved field is not a static (class) field or an interface
            // field, putstatic throws an IncompatibleClassChange
            if (!field.isStatic()) {
                CompilerDirectives.transferToInterpreter();
                throw getMeta().throwEx(IncompatibleClassChangeError.class);
            }
            // Otherwise, if the field is final, it must be declared in the current class, and the
            // instruction must occur in the <clinit> method of the current class. Otherwise, an
            // IllegalAccessError is thrown.
            if (field.isFinalFlagSet()) {
                // && getMethod().isClassInitializer()
                // Enforced in class files >= v53 (9).
                if (!(field.getDeclaringKlass() == getMethod().getDeclaringKlass())) {
                    CompilerDirectives.transferToInterpreter();
                    throw getMeta().throwEx(IllegalAccessError.class);
                }
            }
        }

        assert field.isStatic() == (opcode == PUTSTATIC);

        StaticObject receiver = field.isStatic()
                        ? field.getDeclaringKlass().tryInitializeAndGetStatics()
                        : nullCheck(peekAndReleaseObject(frame, top - field.getKind().getSlotCount() - 1)); // -receiver

        // @formatter:off
        // Checkstyle: stop
        switch (field.getKind()) {
            case Boolean :
                boolean booleanValue = peekInt(frame, top - 1) == 1;
                if (instrumentation != null) {
                    instrumentation.notifyFieldModification(frame, statementIndex, field, receiver, booleanValue);
                }
                InterpreterToVM.setFieldBoolean(booleanValue, receiver, field);
                break;
            case Byte :
                byte byteValue = (byte) peekInt(frame, top - 1);
                if (instrumentation != null) {
                    instrumentation.notifyFieldModification(frame, statementIndex, field, receiver, byteValue);
                }
                InterpreterToVM.setFieldByte(byteValue, receiver, field);
                break;
            case Char :
                char charValue = (char) peekInt(frame, top - 1);
                if (instrumentation != null) {
                    instrumentation.notifyFieldModification(frame, statementIndex, field, receiver, charValue);
                }
                InterpreterToVM.setFieldChar(charValue, receiver, field);
                break;
            case Short :
                short shortValue = (short) peekInt(frame, top - 1);
                if (instrumentation != null) {
                    instrumentation.notifyFieldModification(frame, statementIndex, field, receiver, shortValue);
                }
                InterpreterToVM.setFieldShort(shortValue, receiver, field);
                break;
            case Int :
                int intValue = peekInt(frame, top - 1);
                if (instrumentation != null) {
                    instrumentation.notifyFieldModification(frame, statementIndex, field, receiver, intValue);
                }
                InterpreterToVM.setFieldInt(intValue, receiver, field);
                break;
            case Double :
                double doubleValue = peekDouble(frame, top - 1);
                if (instrumentation != null) {
                    instrumentation.notifyFieldModification(frame, statementIndex, field, receiver, doubleValue);
                }
                InterpreterToVM.setFieldDouble(doubleValue, receiver, field);
                break;
            case Float :
                float floatValue = peekFloat(frame, top - 1);
                if (instrumentation != null) {
                    instrumentation.notifyFieldModification(frame, statementIndex, field, receiver, floatValue);
                }
                InterpreterToVM.setFieldFloat(floatValue, receiver, field);
                break;
            case Long :
                long longValue = peekLong(frame, top - 1);
                if (instrumentation != null) {
                    instrumentation.notifyFieldModification(frame, statementIndex, field, receiver, longValue);
                }
                InterpreterToVM.setFieldLong(longValue, receiver, field);
                break;
            case Object :
                StaticObject value = peekAndReleaseObject(frame, top - 1);
                if (instrumentation != null) {
                    instrumentation.notifyFieldModification(frame, statementIndex, field, receiver, value);
                }
                InterpreterToVM.setFieldObject(value, receiver, field);
                break;
            default      : throw EspressoError.shouldNotReachHere("unexpected kind");
        }
        // @formatter:on
        // Checkstyle: resume
        return -field.getKind().getSlotCount();
    }

    /**
     * Returns the stack effect (slot delta) that cannot be inferred solely from the bytecode. e.g.
     * PUTFIELD always pops the receiver, but the result size (1 or 2) is unknown.
     *
     * <pre>
     *   top += getField(frame, top, resolveField(...)); break; // stack effect that depends on the field
     *   top += Bytecodes.stackEffectOf(curOpcode); // stack effect that depends solely on GETFIELD.
     *   // at this point `top` must have the correct value.
     *   curBCI = bs.next(curBCI);
     * </pre>
     */
    private int getField(final VirtualFrame frame, int top, Field field, int opcode, int statementIndex) {
        assert opcode == GETFIELD || opcode == GETSTATIC;
        CompilerAsserts.partialEvaluationConstant(field);

        if (opcode == GETFIELD) {
            // Otherwise, if the resolved field is a static field, getfield throws an
            // IncompatibleClassChangeError.
            if (field.isStatic()) {
                CompilerDirectives.transferToInterpreter();
                throw getMeta().throwEx(IncompatibleClassChangeError.class);
            }
        } else if (opcode == GETSTATIC) {
            // Otherwise, if the resolved field is not a static (class) field or an interface
            // field, getstatic throws an IncompatibleClassChangeError.
            if (!field.isStatic()) {
                CompilerDirectives.transferToInterpreter();
                throw getMeta().throwEx(IncompatibleClassChangeError.class);
            }
        }

        assert field.isStatic() == (opcode == GETSTATIC);

        StaticObject receiver = field.isStatic()
                        ? field.getDeclaringKlass().tryInitializeAndGetStatics()
                        : nullCheck(peekAndReleaseObject(frame, top - 1));

        if (instrumentation != null) {
            instrumentation.notifyFieldAccess(frame, statementIndex, field, receiver);
        }

        int resultAt = field.isStatic() ? top : (top - 1);
        // @formatter:off
        // Checkstyle: stop
        switch (field.getKind()) {
            case Boolean : putInt(frame, resultAt, InterpreterToVM.getFieldBoolean(receiver, field) ? 1 : 0); break;
            case Byte    : putInt(frame, resultAt, InterpreterToVM.getFieldByte(receiver, field));      break;
            case Char    : putInt(frame, resultAt, InterpreterToVM.getFieldChar(receiver, field));      break;
            case Short   : putInt(frame, resultAt, InterpreterToVM.getFieldShort(receiver, field));     break;
            case Int     : putInt(frame, resultAt, InterpreterToVM.getFieldInt(receiver, field));       break;
            case Double  : putDouble(frame, resultAt, InterpreterToVM.getFieldDouble(receiver, field)); break;
            case Float   : putFloat(frame, resultAt, InterpreterToVM.getFieldFloat(receiver, field));   break;
            case Long    : putLong(frame, resultAt, InterpreterToVM.getFieldLong(receiver, field));     break;
            case Object  : putObject(frame, resultAt, InterpreterToVM.getFieldObject(receiver, field)); break;
            default      : throw EspressoError.shouldNotReachHere("unexpected kind");
        }
        // @formatter:on
        // Checkstyle: resume
        return field.getKind().getSlotCount();
    }

    // endregion Field read/write

    @Override
    public String toString() {
        return getRootNode().getName();
    }

    @ExplodeLoop
    public Object[] peekAndReleaseArguments(VirtualFrame frame, int top, boolean hasReceiver, final Symbol<Type>[] signature) {
        int argCount = Signatures.parameterCount(signature, false);

        int extraParam = hasReceiver ? 1 : 0;
        final Object[] args = new Object[argCount + extraParam];

        CompilerAsserts.partialEvaluationConstant(argCount);
        CompilerAsserts.partialEvaluationConstant(signature);
        CompilerAsserts.partialEvaluationConstant(hasReceiver);

        int argAt = top - 1;
        for (int i = argCount - 1; i >= 0; --i) {
            JavaKind kind = Signatures.parameterKind(signature, i);
            // @formatter:off
            // Checkstyle: stop
            switch (kind) {
                case Boolean : args[i + extraParam] = (peekInt(frame, argAt) != 0);  break;
                case Byte    : args[i + extraParam] = (byte) peekInt(frame, argAt);  break;
                case Short   : args[i + extraParam] = (short) peekInt(frame, argAt); break;
                case Char    : args[i + extraParam] = (char) peekInt(frame, argAt);  break;
                case Int     : args[i + extraParam] = peekInt(frame, argAt);         break;
                case Float   : args[i + extraParam] = peekFloat(frame, argAt);       break;
                case Long    : args[i + extraParam] = peekLong(frame, argAt);        break;
                case Double  : args[i + extraParam] = peekDouble(frame, argAt);      break;
                case Object  : args[i + extraParam] = peekAndReleaseObject(frame, argAt); break;
                default      : throw EspressoError.shouldNotReachHere();
            }
            // @formatter:on
            // Checkstyle: resume
            argAt -= kind.getSlotCount();
        }
        if (hasReceiver) {
            args[0] = peekAndReleaseObject(frame, argAt);
        }
        return args;
    }

    // Effort to prevent double copies. Erases sub-word primitive types.
    @ExplodeLoop
    public Object[] peekAndReleaseBasicArgumentsWithArray(VirtualFrame frame, int top, final Symbol<Type>[] signature, Object[] args, final int argCount, int start) {
        // Use basic types
        CompilerAsserts.partialEvaluationConstant(argCount);
        CompilerAsserts.partialEvaluationConstant(signature);

        int argAt = top - 1;
        for (int i = argCount - 1; i >= 0; --i) {
            JavaKind kind = Signatures.parameterKind(signature, i);
            // @formatter:off
            // Checkstyle: stop
            switch (kind) {
                case Boolean : // Fall through
                case Byte    : // Fall through
                case Short   : // Fall through
                case Char    : // Fall through
                case Int     : args[i + start] = peekInt(frame, argAt);    break;
                case Float   : args[i + start] = peekFloat(frame, argAt);  break;
                case Long    : args[i + start] = peekLong(frame, argAt);   break;
                case Double  : args[i + start] = peekDouble(frame, argAt); break;
                case Object  : args[i + start] = peekAndReleaseObject(frame, argAt); break;
                default      : throw EspressoError.shouldNotReachHere();
            }
            // @formatter:on
            // Checkstyle: resume
            argAt -= kind.getSlotCount();
        }
        return args;
    }

    /**
     * Puts a value in the operand stack. This method follows the JVM spec, where sub-word types (<
     * int) are always treated as int.
     *
     * Returns the number of used slots.
     *
     * @param value value to push
     * @param kind kind to push
     */
    public int putKind(VirtualFrame frame, int top, Object value, JavaKind kind) {
        // @formatter:off
        // Checkstyle: stop
        switch (kind) {
            case Boolean : putInt(frame, top, ((boolean) value) ? 1 : 0); break;
            case Byte    : putInt(frame, top, (byte) value);              break;
            case Short   : putInt(frame, top, (short) value);             break;
            case Char    : putInt(frame, top, (char) value);              break;
            case Int     : putInt(frame, top, (int) value);               break;
            case Float   : putFloat(frame, top, (float) value);           break;
            case Long    : putLong(frame, top, (long) value);             break;
            case Double  : putDouble(frame, top, (double) value);         break;
            case Object  : putObject(frame, top, (StaticObject)value);    break;
            case Void    : /* ignore */                                   break;
            default      : throw EspressoError.shouldNotReachHere();
        }
        // @formatter:on
        // Checkstyle: resume
        return kind.getSlotCount();
    }

    // internal
    private void putKindUnsafe1(VirtualFrame frame, int slot, Object value, JavaKind kind) {
        // @formatter:off
        // Checkstyle: stop
        switch (kind) {
            case Long    : frame.setLong(stackSlots[slot], (long) value); break;
            case Object  : putObjectOrReturnAddress(frame, slot, value);  break;
            default      : throw EspressoError.shouldNotReachHere();
        }
        // @formatter:on
        // Checkstyle: resume
    }

    public StaticObject peekReceiver(final VirtualFrame frame, int top, Method m) {
        assert !m.isStatic();
        int skipSlots = Signatures.slotsForParameters(m.getParsedSignature());
        StaticObject result = peekObject(frame, top - skipSlots - 1);
        assert result != null;
        return result;
    }

    @Override
    public int customNodeCount() {
        int codeSize = getMethod().getCodeSize();
        return 2 * codeSize + 1;
    }

    static final class InstrumentationSupport extends Node {

        @Children private final EspressoInstrumentableNode[] statementNodes;
        @Child private MapperBCI hookBCIToNodeIndex;

        private final EspressoContext context;

        InstrumentationSupport(Method method) {
            this.context = method.getContext();
            LineNumberTable table = method.getLineNumberTable();

            if (table != LineNumberTable.EMPTY) {
                LineNumberTable.Entry[] entries = table.getEntries();
                this.statementNodes = new EspressoInstrumentableNode[entries.length];
                this.hookBCIToNodeIndex = new MapperBCI(table);

                for (int i = 0; i < entries.length; i++) {
                    LineNumberTable.Entry entry = entries[i];
                    statementNodes[hookBCIToNodeIndex.initIndex(i, entry.getBCI())] = new EspressoStatementNode(entry.getBCI(), entry.getLineNumber());
                }
            } else {
                this.statementNodes = null;
                this.hookBCIToNodeIndex = null;
            }
        }

        /**
         * If transitioning between two statements, exits the current one, and enter the new one.
         */
        final void notifyStatement(VirtualFrame frame, int statementIndex, int nextStatementIndex) {
            CompilerAsserts.partialEvaluationConstant(statementIndex);
            CompilerAsserts.partialEvaluationConstant(nextStatementIndex);
            if (statementIndex == nextStatementIndex) {
                return;
            }
            exitAt(frame, statementIndex);
            enterAt(frame, nextStatementIndex);
        }

        public void notifyEntry(@SuppressWarnings("unused") VirtualFrame frame) {
            // TODO(Gregersen) - implement method entry breakpoint hooks
        }

        public void notifyReturn(@SuppressWarnings("unused") VirtualFrame frame, @SuppressWarnings("unused") int statementIndex, @SuppressWarnings("unused") Object toReturn) {
            // TODO(Gregersen) - implement method return breakpoint hooks
        }

        final void notifyExceptionAt(VirtualFrame frame, Throwable t, int statementIndex) {
            EspressoInstrumentableNodeWrapper wrapperNode = getWrapperAt(statementIndex);
            if (wrapperNode == null) {
                return;
            }
            ProbeNode probeNode = wrapperNode.getProbeNode();
            probeNode.onReturnExceptionalOrUnwind(frame, t, false);
        }

        public void notifyFieldModification(VirtualFrame frame, int index, Field field, StaticObject receiver, Object value) {
            if (context.getJDWPListener().hasFieldModificationBreakpoint(field, receiver, value)) {
                enterAt(frame, index);
            }
        }

        public void notifyFieldAccess(VirtualFrame frame, int index, Field field, StaticObject receiver) {
            if (context.getJDWPListener().hasFieldAccessBreakpoint(field, receiver)) {
                enterAt(frame, index);
            }
        }

        private void enterAt(VirtualFrame frame, int index) {
            EspressoInstrumentableNodeWrapper wrapperNode = getWrapperAt(index);
            if (wrapperNode == null) {
                return;
            }
            ProbeNode probeNode = wrapperNode.getProbeNode();
            try {
                probeNode.onEnter(frame);
            } catch (Throwable t) {
                Object result = probeNode.onReturnExceptionalOrUnwind(frame, t, false);
                if (result == ProbeNode.UNWIND_ACTION_REENTER) {
                    // TODO maybe support this by returning a new bci?
                    CompilerDirectives.transferToInterpreter();
                    throw new UnsupportedOperationException();
                } else if (result != null) {
                    // ignore result values;
                    // we are instrumentation statements only.
                    return;
                }
                throw t;
            }
        }

        private void exitAt(VirtualFrame frame, int index) {
            EspressoInstrumentableNodeWrapper wrapperNode = getWrapperAt(index);
            if (wrapperNode == null) {
                return;
            }
            ProbeNode probeNode = wrapperNode.getProbeNode();
            try {
                probeNode.onReturnValue(frame, StaticObject.NULL);
            } catch (Throwable t) {
                Object result = probeNode.onReturnExceptionalOrUnwind(frame, t, true);
                if (result == ProbeNode.UNWIND_ACTION_REENTER) {
                    // TODO maybe support this by returning a new bci?
                    CompilerDirectives.transferToInterpreter();
                    throw new UnsupportedOperationException();
                } else if (result != null) {
                    // ignore result values;
                    // we are instrumentation statements only.
                    return;
                }
                throw t;
            }
        }

        final int getStatementIndexAfterJump(int statementIndex, int curBCI, int targetBCI) {
            if (hookBCIToNodeIndex == null) {
                return 0;
            }
            return hookBCIToNodeIndex.lookup(statementIndex, curBCI, targetBCI);
        }

        final int getNextStatementIndex(int statementIndex, int nextBCI) {
            if (hookBCIToNodeIndex == null) {
                return 0;
            }
            return hookBCIToNodeIndex.checkNext(statementIndex, nextBCI);
        }

        private EspressoInstrumentableNodeWrapper getWrapperAt(int index) {
            if (statementNodes == null || index < 0) {
                return null;
            }
            EspressoInstrumentableNode node = statementNodes[index];
            if (!(node instanceof EspressoInstrumentableNodeWrapper)) {
                return null;
            }
            CompilerAsserts.partialEvaluationConstant(node);
            return ((EspressoInstrumentableNodeWrapper) node);
        }

    }

}
