/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.espresso.EspressoOptions.SpecCompliancyMode.STRICT;
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
import static com.oracle.truffle.espresso.bytecode.Bytecodes.SLIM_QUICK;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.SWAP;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.TABLESWITCH;
import static com.oracle.truffle.espresso.bytecode.Bytecodes.WIDE;

import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.logging.Level;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotTypeException;
import com.oracle.truffle.api.frame.FrameUtil;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.StandardTags.StatementTag;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.LoopNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.espresso.analysis.liveness.LivenessAnalysis;
import com.oracle.truffle.espresso.bytecode.BytecodeLookupSwitch;
import com.oracle.truffle.espresso.bytecode.BytecodeStream;
import com.oracle.truffle.espresso.bytecode.BytecodeTableSwitch;
import com.oracle.truffle.espresso.bytecode.Bytecodes;
import com.oracle.truffle.espresso.bytecode.MapperBCI;
import com.oracle.truffle.espresso.classfile.ClassfileParser;
import com.oracle.truffle.espresso.classfile.RuntimeConstantPool;
import com.oracle.truffle.espresso.classfile.attributes.BootstrapMethodsAttribute;
import com.oracle.truffle.espresso.classfile.attributes.CodeAttribute;
import com.oracle.truffle.espresso.classfile.attributes.LineNumberTableAttribute;
import com.oracle.truffle.espresso.classfile.constantpool.ClassConstant;
import com.oracle.truffle.espresso.classfile.constantpool.DoubleConstant;
import com.oracle.truffle.espresso.classfile.constantpool.DynamicConstant;
import com.oracle.truffle.espresso.classfile.constantpool.FloatConstant;
import com.oracle.truffle.espresso.classfile.constantpool.IntegerConstant;
import com.oracle.truffle.espresso.classfile.constantpool.InvokeDynamicConstant;
import com.oracle.truffle.espresso.classfile.constantpool.LongConstant;
import com.oracle.truffle.espresso.classfile.constantpool.MethodHandleConstant;
import com.oracle.truffle.espresso.classfile.constantpool.MethodRefConstant;
import com.oracle.truffle.espresso.classfile.constantpool.MethodTypeConstant;
import com.oracle.truffle.espresso.classfile.constantpool.PoolConstant;
import com.oracle.truffle.espresso.classfile.constantpool.StringConstant;
import com.oracle.truffle.espresso.descriptors.Signatures;
import com.oracle.truffle.espresso.descriptors.Symbol;
import com.oracle.truffle.espresso.descriptors.Symbol.Type;
import com.oracle.truffle.espresso.impl.ArrayKlass;
import com.oracle.truffle.espresso.impl.Field;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.impl.Method;
import com.oracle.truffle.espresso.impl.Method.MethodVersion;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.ExceptionHandler;
import com.oracle.truffle.espresso.meta.JavaKind;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.nodes.helper.EspressoReferenceArrayStoreNode;
import com.oracle.truffle.espresso.nodes.quick.CheckCastNode;
import com.oracle.truffle.espresso.nodes.quick.InstanceOfNode;
import com.oracle.truffle.espresso.nodes.quick.QuickNode;
import com.oracle.truffle.espresso.nodes.quick.interop.ArrayLengthNodeGen;
import com.oracle.truffle.espresso.nodes.quick.interop.ByteArrayLoadNodeGen;
import com.oracle.truffle.espresso.nodes.quick.interop.ByteArrayStoreNodeGen;
import com.oracle.truffle.espresso.nodes.quick.interop.CharArrayLoadNodeGen;
import com.oracle.truffle.espresso.nodes.quick.interop.CharArrayStoreNodeGen;
import com.oracle.truffle.espresso.nodes.quick.interop.DoubleArrayLoadNodeGen;
import com.oracle.truffle.espresso.nodes.quick.interop.DoubleArrayStoreNodeGen;
import com.oracle.truffle.espresso.nodes.quick.interop.FloatArrayLoadNodeGen;
import com.oracle.truffle.espresso.nodes.quick.interop.FloatArrayStoreNodeGen;
import com.oracle.truffle.espresso.nodes.quick.interop.IntArrayLoadNodeGen;
import com.oracle.truffle.espresso.nodes.quick.interop.IntArrayStoreNodeGen;
import com.oracle.truffle.espresso.nodes.quick.interop.LongArrayLoadNodeGen;
import com.oracle.truffle.espresso.nodes.quick.interop.LongArrayStoreNodeGen;
import com.oracle.truffle.espresso.nodes.quick.interop.QuickenedGetFieldNode;
import com.oracle.truffle.espresso.nodes.quick.interop.QuickenedPutFieldNode;
import com.oracle.truffle.espresso.nodes.quick.interop.ReferenceArrayLoadNodeGen;
import com.oracle.truffle.espresso.nodes.quick.interop.ReferenceArrayStoreNodeGen;
import com.oracle.truffle.espresso.nodes.quick.interop.ShortArrayLoadNodeGen;
import com.oracle.truffle.espresso.nodes.quick.interop.ShortArrayStoreNodeGen;
import com.oracle.truffle.espresso.nodes.quick.invoke.InlinedGetterNode;
import com.oracle.truffle.espresso.nodes.quick.invoke.InlinedSetterNode;
import com.oracle.truffle.espresso.nodes.quick.invoke.InvokeDynamicCallSiteNode;
import com.oracle.truffle.espresso.nodes.quick.invoke.InvokeHandleNode;
import com.oracle.truffle.espresso.nodes.quick.invoke.InvokeInterfaceNodeGen;
import com.oracle.truffle.espresso.nodes.quick.invoke.InvokeSpecialNode;
import com.oracle.truffle.espresso.nodes.quick.invoke.InvokeStaticNode;
import com.oracle.truffle.espresso.nodes.quick.invoke.InvokeVirtualNodeGen;
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
 * bytecode is first processed/executed without growing or shrinking the stack and only then the
 * {@code top} of the stack index is adjusted depending on the bytecode stack offset.
 */
public final class BytecodeNode extends EspressoMethodNode {

    private static final DebugCounter EXECUTED_BYTECODES_COUNT = DebugCounter.create("Executed bytecodes");
    private static final DebugCounter QUICKENED_BYTECODES = DebugCounter.create("Quickened bytecodes");
    private static final DebugCounter QUICKENED_INVOKES = DebugCounter.create("Quickened invokes (excluding INDY)");

    @Children private QuickNode[] nodes = QuickNode.EMPTY_ARRAY;
    @Children private QuickNode[] sparseNodes = QuickNode.EMPTY_ARRAY;
    /**
     * Ideally, we would want one such node per AASTORE bytecode. Unfortunately, the AASTORE
     * bytecode is a single byte long, so we cannot quicken it, and it is far too common to pay for
     * spawning the sparse nodes array.
     */
    @Child private volatile EspressoReferenceArrayStoreNode refArrayStoreNode;

    @CompilationFinal(dimensions = 1) //
    private final FrameSlot[] locals;

    @CompilationFinal(dimensions = 1) //
    private final FrameSlot[] stackSlots;

    private final FrameSlot bciSlot;

    @CompilationFinal(dimensions = 1) //
    private final int[] stackOverflowErrorInfo;

    @CompilationFinal(dimensions = 2) //
    private int[][] jsrBci = null;

    private final BytecodeStream bs;

    private EspressoRootNode rootNode;

    @Child private volatile InstrumentationSupport instrumentation;

    private final Assumption noForeignObjects;

    // Cheap profile for implicit exceptions e.g. null checks, division by 0, index out of bounds.
    // All implicit exception paths in the method will be compiled if at least one implicit
    // exception is thrown.
    @CompilationFinal private boolean implicitExceptionProfile;

    private final LivenessAnalysis livenessAnalysis;

    @TruffleBoundary
    public BytecodeNode(MethodVersion method, FrameDescriptor frameDescriptor, FrameSlot bciSlot) {
        super(method);
        CompilerAsserts.neverPartOfCompilation();
        CodeAttribute codeAttribute = method.getCodeAttribute();
        this.bs = new BytecodeStream(codeAttribute.getCode());
        FrameSlot[] slots = frameDescriptor.getSlots().toArray(new FrameSlot[0]);

        this.locals = Arrays.copyOfRange(slots, 0, codeAttribute.getMaxLocals());
        this.stackSlots = Arrays.copyOfRange(slots, codeAttribute.getMaxLocals(), codeAttribute.getMaxLocals() + codeAttribute.getMaxStack());
        this.bciSlot = bciSlot;
        this.stackOverflowErrorInfo = getMethod().getSOEHandlerInfo();
        // TODO(peterssen): Allocate new assumption iff there's a bytecode that can produce foreign
        // objects.
        this.noForeignObjects = Truffle.getRuntime().createAssumption("noForeignObjects");
        this.implicitExceptionProfile = false;
        this.livenessAnalysis = LivenessAnalysis.analyze(method.getMethod());
    }

    public BytecodeNode(BytecodeNode copy) {
        this(copy.getMethodVersion(), copy.getRootNode().getFrameDescriptor(), copy.bciSlot);
        getContext().getLogger().log(Level.FINE, "Copying node for {}", getMethod());
    }

    public SourceSection getSourceSectionAtBCI(int bci) {
        Source s = getSource();
        if (s == null) {
            return null;
        }

        LineNumberTableAttribute table = getMethodVersion().getLineNumberTableAttribute();

        if (table == LineNumberTableAttribute.EMPTY) {
            return null;
        }
        int line = table.getLineNumber(bci);
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
            assert StaticObject.notNull((StaticObject) frameArguments[0]) : "null receiver in init arguments !";
            StaticObject receiver = (StaticObject) frameArguments[0];
            setLocalObject(frame, n, receiver);
            if (noForeignObjects.isValid() && receiver.isForeignObject()) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                noForeignObjects.invalidate();
            }
            n += JavaKind.Object.getSlotCount();
        }
        for (int i = 0; i < argCount; ++i) {
            JavaKind expectedkind = Signatures.parameterKind(getMethod().getParsedSignature(), i);
            // @formatter:off
            switch (expectedkind) {
                case Boolean : setLocalInt(frame, n, ((boolean) arguments[i]) ? 1 : 0); break;
                case Byte    : setLocalInt(frame, n, ((byte) arguments[i]));            break;
                case Short   : setLocalInt(frame, n, ((short) arguments[i]));           break;
                case Char    : setLocalInt(frame, n, ((char) arguments[i]));            break;
                case Int     : setLocalInt(frame, n, (int) arguments[i]);               break;
                case Float   : setLocalFloat(frame, n, (float) arguments[i]);           break;
                case Long    : setLocalLong(frame, n, (long) arguments[i]);             break;
                case Double  : setLocalDouble(frame, n, (double) arguments[i]);         break;
                case Object  :
                    setLocalObject(frame, n, (StaticObject) arguments[i]);
                    if (noForeignObjects.isValid() && ((StaticObject) arguments[i]).isForeignObject()) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        noForeignObjects.invalidate();
                    }
                    break;
                default      :
                    CompilerDirectives.transferToInterpreter();
                    throw EspressoError.shouldNotReachHere("unexpected kind");
            }
            // @formatter:on
            n += expectedkind.getSlotCount();
        }
        setBCI(frame, 0);
    }

    private void setBCI(VirtualFrame frame, int bci) {
        frame.setInt(bciSlot, bci);
    }

    private int peekInt(VirtualFrame frame, int slot) {
        return (int) FrameUtil.getLongSafe(frame, stackSlots[slot]);
    }

    public int popInt(VirtualFrame frame, int slot) {
        int result = peekInt(frame, slot);
        if (CompilerDirectives.inCompiledCode()) {
            putObject(frame, slot, null);
        }
        return result;
    }

    // Exposed to CheckCastNode.
    // Exposed to InstanceOfNode and quick nodes, which can produce foreign objects.
    public StaticObject peekObject(VirtualFrame frame, int slot) {
        Object result = FrameUtil.getObjectSafe(frame, stackSlots[slot]);
        assert result instanceof StaticObject;
        return (StaticObject) result;
    }

    private Object peekReturnAddressOrObject(VirtualFrame frame, int slot) {
        Object result = FrameUtil.getObjectSafe(frame, stackSlots[slot]);
        assert result instanceof StaticObject || result instanceof ReturnAddress;
        return result;
    }

    /**
     * Reads and clear the operand stack slot.
     */
    public StaticObject popObject(VirtualFrame frame, int slot) {
        Object result = FrameUtil.getObjectSafe(frame, stackSlots[slot]);
        // nulls-out the slot, use peekObject to read only
        putObject(frame, slot, null);
        assert result instanceof StaticObject;
        return (StaticObject) result;
    }

    // Boxed value.
    private Object peekValue(VirtualFrame frame, int slot) {
        return frame.getValue(stackSlots[slot]);
    }

    private float peekFloat(VirtualFrame frame, int slot) {
        return Float.intBitsToFloat((int) FrameUtil.getLongSafe(frame, stackSlots[slot]));
    }

    public float popFloat(VirtualFrame frame, int slot) {
        float result = peekFloat(frame, slot);
        if (CompilerDirectives.inCompiledCode()) {
            putObject(frame, slot, null);
        }
        return result;
    }

    private long peekLong(VirtualFrame frame, int slot) {
        return FrameUtil.getLongSafe(frame, stackSlots[slot]);
    }

    public long popLong(VirtualFrame frame, int slot) {
        long result = peekLong(frame, slot);
        if (CompilerDirectives.inCompiledCode()) {
            putObject(frame, slot - 1, null);
            putObject(frame, slot, null);
        }
        return result;
    }

    private double peekDouble(VirtualFrame frame, int slot) {
        return Double.longBitsToDouble(FrameUtil.getLongSafe(frame, stackSlots[slot]));
    }

    public double popDouble(VirtualFrame frame, int slot) {
        double result = peekDouble(frame, slot);
        if (CompilerDirectives.inCompiledCode()) {
            putObject(frame, slot - 1, null);
            putObject(frame, slot, null);
        }
        return result;
    }

    /**
     * Read and clear the operand stack slot.
     */
    private Object popReturnAddressOrObject(VirtualFrame frame, int slot) {
        Object result = FrameUtil.getObjectSafe(frame, stackSlots[slot]);
        putObjectOrReturnAddress(frame, slot, null);
        assert result instanceof StaticObject || result instanceof ReturnAddress;
        return result;
    }

    private void putReturnAddress(VirtualFrame frame, int slot, int targetBCI) {
        frame.setObject(stackSlots[slot], ReturnAddress.create(targetBCI));
    }

    public void putObject(VirtualFrame frame, int slot, StaticObject value) {
        frame.setObject(stackSlots[slot], value);
    }

    private void putObjectOrReturnAddress(VirtualFrame frame, int slot, Object value) {
        assert value instanceof StaticObject || value instanceof ReturnAddress || value == null;
        frame.setObject(stackSlots[slot], value);
    }

    public void putInt(VirtualFrame frame, int slot, int value) {
        frame.setLong(stackSlots[slot], value);
    }

    public void putFloat(VirtualFrame frame, int slot, float value) {
        frame.setLong(stackSlots[slot], Float.floatToRawIntBits(value));
    }

    public void putLong(VirtualFrame frame, int slot, long value) {
        frame.setLong(stackSlots[slot + 1], value);
    }

    public void putDouble(VirtualFrame frame, int slot, double value) {
        frame.setLong(stackSlots[slot + 1], Double.doubleToRawLongBits(value));
    }

    // region Local accessors

    public void freeLocal(VirtualFrame frame, int slot) {
        // TODO(garcia): use frame.clear() once available
        // frame.clear(locals[slot]);
        if (frame.isObject(locals[slot])) {
            frame.setObject(locals[slot], null); // null out object array
        } else {
            frame.setLong(locals[slot], 0L); // null out primitive array
        }
    }

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

    // endregion Local accessors

    @Override
    void initializeBody(VirtualFrame frame) {
        initArguments(frame);
    }

    @Override
    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.MERGE_EXPLODE)
    Object executeBody(VirtualFrame frame) {
        int curBCI = 0;
        int top = 0;
        InstrumentationSupport instrument = this.instrumentation;
        int statementIndex = -1;
        int nextStatementIndex = 0;

        if (instrument != null) {
            instrument.notifyEntry(frame);
        }

        preBCI(frame, curBCI);

        loop: while (true) {
            int curOpcode;
            EXECUTED_BYTECODES_COUNT.inc();
            try {
                curOpcode = bs.currentBC(curBCI);
                CompilerAsserts.partialEvaluationConstant(top);
                CompilerAsserts.partialEvaluationConstant(curBCI);
                CompilerAsserts.partialEvaluationConstant(curOpcode);

                CompilerAsserts.partialEvaluationConstant(statementIndex);
                CompilerAsserts.partialEvaluationConstant(nextStatementIndex);

                if (Bytecodes.canTrap(curOpcode) || instrument != null) {
                    setBCI(frame, curBCI);
                }

                if (instrument != null) {
                    instrument.notifyStatement(frame, statementIndex, nextStatementIndex);
                    statementIndex = nextStatementIndex;

                    // check for early return
                    Object earlyReturnValue = getContext().getJDWPListener().getEarlyReturnValue();
                    if (earlyReturnValue != null) {
                        return notifyReturn(frame, statementIndex, exitMethodEarlyAndReturn(earlyReturnValue));
                    }
                }

                // @formatter:off
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

                    case ILOAD: putInt(frame, top, getLocalInt(frame, bs.readLocalIndex(curBCI)));
                        postLocalAccess(frame, curBCI); break;
                    case LLOAD: putLong(frame, top, getLocalLong(frame, bs.readLocalIndex(curBCI)));
                        postLocalAccess(frame, curBCI); break;
                    case FLOAD: putFloat(frame, top, getLocalFloat(frame, bs.readLocalIndex(curBCI)));
                        postLocalAccess(frame, curBCI); break;
                    case DLOAD: putDouble(frame, top, getLocalDouble(frame, bs.readLocalIndex(curBCI)));
                        postLocalAccess(frame, curBCI); break;
                    case ALOAD: putObject(frame, top, getLocalObject(frame, bs.readLocalIndex(curBCI)));
                        postLocalAccess(frame, curBCI); break;

                    case ILOAD_0: // fall through
                    case ILOAD_1: // fall through
                    case ILOAD_2: // fall through
                    case ILOAD_3: putInt(frame, top, getLocalInt(frame, curOpcode - ILOAD_0));
                        postLocalAccess(frame, curBCI); break;
                    case LLOAD_0: // fall through
                    case LLOAD_1: // fall through
                    case LLOAD_2: // fall through
                    case LLOAD_3: putLong(frame, top, getLocalLong(frame, curOpcode - LLOAD_0));
                        postLocalAccess(frame, curBCI); break;
                    case FLOAD_0: // fall through
                    case FLOAD_1: // fall through
                    case FLOAD_2: // fall through
                    case FLOAD_3: putFloat(frame, top, getLocalFloat(frame, curOpcode - FLOAD_0));
                        postLocalAccess(frame, curBCI); break;
                    case DLOAD_0: // fall through
                    case DLOAD_1: // fall through
                    case DLOAD_2: // fall through
                    case DLOAD_3: putDouble(frame, top, getLocalDouble(frame, curOpcode - DLOAD_0));
                        postLocalAccess(frame, curBCI); break;
                    case ALOAD_0: // fall through
                    case ALOAD_1: // fall through
                    case ALOAD_2: // fall through
                    case ALOAD_3: putObject(frame, top, getLocalObject(frame, curOpcode - ALOAD_0));
                        postLocalAccess(frame, curBCI); break;

                    case IALOAD: // fall through
                    case LALOAD: // fall through
                    case FALOAD: // fall through
                    case DALOAD: // fall through
                    case BALOAD: // fall through
                    case CALOAD: // fall through
                    case SALOAD: arrayLoad(frame, top, curBCI, curOpcode); break;
                    case AALOAD:
                        arrayLoad(frame, top, curBCI, curOpcode);
                        if (noForeignObjects.isValid() && peekObject(frame, top - 2).isForeignObject()) {
                            CompilerDirectives.transferToInterpreterAndInvalidate();
                            noForeignObjects.invalidate();
                        }
                        break;

                    case ISTORE: setLocalInt(frame, bs.readLocalIndex(curBCI), popInt(frame, top - 1));
                        postLocalAccess(frame, curBCI); break;
                    case LSTORE: setLocalLong(frame, bs.readLocalIndex(curBCI), popLong(frame, top - 1));
                        postLocalAccess(frame, curBCI); break;
                    case FSTORE: setLocalFloat(frame, bs.readLocalIndex(curBCI), popFloat(frame, top - 1));
                        postLocalAccess(frame, curBCI); break;
                    case DSTORE: setLocalDouble(frame, bs.readLocalIndex(curBCI), popDouble(frame, top - 1));
                        postLocalAccess(frame, curBCI); break;
                    case ASTORE: setLocalObjectOrReturnAddress(frame, bs.readLocalIndex(curBCI), popReturnAddressOrObject(frame, top - 1));
                        postLocalAccess(frame, curBCI); break;

                    case ISTORE_0: // fall through
                    case ISTORE_1: // fall through
                    case ISTORE_2: // fall through
                    case ISTORE_3: setLocalInt(frame, curOpcode - ISTORE_0, popInt(frame, top - 1));
                        postLocalAccess(frame, curBCI); break;
                    case LSTORE_0: // fall through
                    case LSTORE_1: // fall through
                    case LSTORE_2: // fall through
                    case LSTORE_3: setLocalLong(frame, curOpcode - LSTORE_0, popLong(frame, top - 1));
                        postLocalAccess(frame, curBCI); break;
                    case FSTORE_0: // fall through
                    case FSTORE_1: // fall through
                    case FSTORE_2: // fall through
                    case FSTORE_3: setLocalFloat(frame, curOpcode - FSTORE_0, popFloat(frame, top - 1));
                        postLocalAccess(frame, curBCI); break;
                    case DSTORE_0: // fall through
                    case DSTORE_1: // fall through
                    case DSTORE_2: // fall through
                    case DSTORE_3: setLocalDouble(frame, curOpcode - DSTORE_0, popDouble(frame, top - 1));
                        postLocalAccess(frame, curBCI); break;
                    case ASTORE_0: // fall through
                    case ASTORE_1: // fall through
                    case ASTORE_2: // fall through
                    case ASTORE_3: setLocalObjectOrReturnAddress(frame, curOpcode - ASTORE_0, popReturnAddressOrObject(frame, top - 1));
                        postLocalAccess(frame, curBCI); break;

                    case IASTORE: // fall through
                    case LASTORE: // fall through
                    case FASTORE: // fall through
                    case DASTORE: // fall through
                    case AASTORE: // fall through
                    case BASTORE: // fall through
                    case CASTORE: // fall through
                    case SASTORE: arrayStore(frame, top, curBCI, curOpcode); break;

                    case POP2:
                        putObject(frame, top - 1, null);
                        putObject(frame, top - 2, null);
                        break;
                    case POP:
                        putObject(frame, top - 1, null);
                        break;

                    // TODO(peterssen): Stack shuffling is expensive.
                    case DUP     : dup1(frame, top);       break;
                    case DUP_X1  : dupx1(frame, top);      break;
                    case DUP_X2  : dupx2(frame, top);      break;
                    case DUP2    : dup2(frame, top);       break;
                    case DUP2_X1 : dup2x1(frame, top);     break;
                    case DUP2_X2 : dup2x2(frame, top);     break;
                    case SWAP    : swapSingle(frame, top); break;

                    case IADD: putInt(frame, top - 2, popInt(frame, top - 1) + popInt(frame, top - 2)); break;
                    case LADD: putLong(frame, top - 4, popLong(frame, top - 1) + popLong(frame, top - 3)); break;
                    case FADD: putFloat(frame, top - 2, popFloat(frame, top - 1) + popFloat(frame, top - 2)); break;
                    case DADD: putDouble(frame, top - 4, popDouble(frame, top - 1) + popDouble(frame, top - 3)); break;

                    case ISUB: putInt(frame, top - 2, -popInt(frame, top - 1) + popInt(frame, top - 2)); break;
                    case LSUB: putLong(frame, top - 4, -popLong(frame, top - 1) + popLong(frame, top - 3)); break;
                    case FSUB: putFloat(frame, top - 2, -popFloat(frame, top - 1) + popFloat(frame, top - 2)); break;
                    case DSUB: putDouble(frame, top - 4, -popDouble(frame, top - 1) + popDouble(frame, top - 3)); break;

                    case IMUL: putInt(frame, top - 2, popInt(frame, top - 1) * popInt(frame, top - 2)); break;
                    case LMUL: putLong(frame, top - 4, popLong(frame, top - 1) * popLong(frame, top - 3)); break;
                    case FMUL: putFloat(frame, top - 2, popFloat(frame, top - 1) * popFloat(frame, top - 2)); break;
                    case DMUL: putDouble(frame, top - 4, popDouble(frame, top - 1) * popDouble(frame, top - 3)); break;

                    case IDIV: putInt(frame, top - 2, divInt(checkNonZero(popInt(frame, top - 1)), popInt(frame, top - 2))); break;
                    case LDIV: putLong(frame, top - 4, divLong(checkNonZero(popLong(frame, top - 1)), popLong(frame, top - 3))); break;
                    case FDIV: putFloat(frame, top - 2, divFloat(popFloat(frame, top - 1), popFloat(frame, top - 2))); break;
                    case DDIV: putDouble(frame, top - 4, divDouble(popDouble(frame, top - 1), popDouble(frame, top - 3))); break;

                    case IREM: putInt(frame, top - 2, remInt(checkNonZero(popInt(frame, top - 1)), popInt(frame, top - 2))); break;
                    case LREM: putLong(frame, top - 4, remLong(checkNonZero(popLong(frame, top - 1)), popLong(frame, top - 3))); break;
                    case FREM: putFloat(frame, top - 2, remFloat(popFloat(frame, top - 1), popFloat(frame, top - 2))); break;
                    case DREM: putDouble(frame, top - 4, remDouble(popDouble(frame, top - 1), popDouble(frame, top - 3))); break;

                    case INEG: putInt(frame, top - 1, -popInt(frame, top - 1)); break;
                    case LNEG: putLong(frame, top - 2, -popLong(frame, top - 1)); break;
                    case FNEG: putFloat(frame, top - 1, -popFloat(frame, top - 1)); break;
                    case DNEG: putDouble(frame, top - 2, -popDouble(frame, top - 1)); break;

                    case ISHL: putInt(frame, top - 2, shiftLeftInt(popInt(frame, top - 1), popInt(frame, top - 2))); break;
                    case LSHL: putLong(frame, top - 3, shiftLeftLong(popInt(frame, top - 1), popLong(frame, top - 2))); break;
                    case ISHR: putInt(frame, top - 2, shiftRightSignedInt(popInt(frame, top - 1), popInt(frame, top - 2))); break;
                    case LSHR: putLong(frame, top - 3, shiftRightSignedLong(popInt(frame, top - 1), popLong(frame, top - 2))); break;
                    case IUSHR: putInt(frame, top - 2, shiftRightUnsignedInt(popInt(frame, top - 1), popInt(frame, top - 2))); break;
                    case LUSHR: putLong(frame, top - 3, shiftRightUnsignedLong(popInt(frame, top - 1), popLong(frame, top - 2))); break;

                    case IAND: putInt(frame, top - 2, popInt(frame, top - 1) & popInt(frame, top - 2)); break;
                    case LAND: putLong(frame, top - 4, popLong(frame, top - 1) & popLong(frame, top - 3)); break;

                    case IOR: putInt(frame, top - 2, popInt(frame, top - 1) | popInt(frame, top - 2)); break;
                    case LOR: putLong(frame, top - 4, popLong(frame, top - 1) | popLong(frame, top - 3)); break;

                    case IXOR: putInt(frame, top - 2, popInt(frame, top - 1) ^ popInt(frame, top - 2)); break;
                    case LXOR: putLong(frame, top - 4, popLong(frame, top - 1) ^ popLong(frame, top - 3)); break;

                    case IINC: setLocalInt(frame, bs.readLocalIndex(curBCI), getLocalInt(frame, bs.readLocalIndex(curBCI)) + bs.readIncrement(curBCI));
                        postLocalAccess(frame, curBCI); break;

                    case I2L: putLong(frame, top - 1, popInt(frame, top - 1)); break;
                    case I2F: putFloat(frame, top - 1, popInt(frame, top - 1)); break;
                    case I2D: putDouble(frame, top - 1, popInt(frame, top - 1)); break;

                    case L2I: putInt(frame, top - 2, (int) popLong(frame, top - 1)); break;
                    case L2F: putFloat(frame, top - 2, popLong(frame, top - 1)); break;
                    case L2D: putDouble(frame, top - 2, popLong(frame, top - 1)); break;

                    case F2I: putInt(frame, top - 1, (int) popFloat(frame, top - 1)); break;
                    case F2L: putLong(frame, top - 1, (long) popFloat(frame, top - 1)); break;
                    case F2D: putDouble(frame, top - 1, popFloat(frame, top - 1)); break;

                    case D2I: putInt(frame, top - 2, (int) popDouble(frame, top - 1)); break;
                    case D2L: putLong(frame, top - 2, (long) popDouble(frame, top - 1)); break;
                    case D2F: putFloat(frame, top - 2, (float) popDouble(frame, top - 1)); break;

                    case I2B: putInt(frame, top - 1, (byte) popInt(frame, top - 1)); break;
                    case I2C: putInt(frame, top - 1, (char) popInt(frame, top - 1)); break;
                    case I2S: putInt(frame, top - 1, (short) popInt(frame, top - 1)); break;

                    case LCMP : putInt(frame, top - 4, compareLong(popLong(frame, top - 1), popLong(frame, top - 3))); break;
                    case FCMPL: putInt(frame, top - 2, compareFloatLess(popFloat(frame, top - 1), popFloat(frame, top - 2))); break;
                    case FCMPG: putInt(frame, top - 2, compareFloatGreater(popFloat(frame, top - 1), popFloat(frame, top - 2))); break;
                    case DCMPL: putInt(frame, top - 4, compareDoubleLess(popDouble(frame, top - 1), popDouble(frame, top - 3))); break;
                    case DCMPG: putInt(frame, top - 4, compareDoubleGreater(popDouble(frame, top - 1), popDouble(frame, top - 3))); break;

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

                    // @formatter:on
                    case IFNONNULL:

                        if (takeBranch(frame, top, curOpcode)) {
                            int targetBCI = bs.readBranchDest(curBCI);
                            CompilerAsserts.partialEvaluationConstant(targetBCI);
                            checkBackEdge(curBCI, targetBCI, top, curOpcode);
                            if (instrument != null) {
                                nextStatementIndex = instrument.getStatementIndexAfterJump(statementIndex, curBCI, targetBCI);
                            }
                            top += Bytecodes.stackEffectOf(curOpcode);
                            curBCI = targetBCI;
                            preBCI(frame, curBCI);
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
                        preBCI(frame, curBCI);
                        continue loop;
                    }
                    case RET: {
                        int targetBCI = getLocalReturnAddress(frame, bs.readLocalIndex(curBCI));
                        postLocalAccess(frame, curBCI);
                        if (jsrBci == null) {
                            CompilerDirectives.transferToInterpreterAndInvalidate();
                            jsrBci = new int[bs.endBCI()][];
                        }
                        if (jsrBci[curBCI] == null) {
                            CompilerDirectives.transferToInterpreterAndInvalidate();
                            jsrBci[curBCI] = new int[]{targetBCI};
                        }
                        for (int jsr : jsrBci[curBCI]) {
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
                                preBCI(frame, curBCI);
                                continue loop;
                            }
                        }
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        jsrBci[curBCI] = Arrays.copyOf(jsrBci[curBCI], jsrBci[curBCI].length + 1);
                        jsrBci[curBCI][jsrBci[curBCI].length - 1] = targetBCI;
                        CompilerAsserts.partialEvaluationConstant(targetBCI);
                        checkBackEdge(curBCI, targetBCI, top, curOpcode);
                        if (instrument != null) {
                            nextStatementIndex = instrument.getStatementIndexAfterJump(statementIndex, curBCI, targetBCI);
                        }
                        top += Bytecodes.stackEffectOf(curOpcode);
                        curBCI = targetBCI;
                        preBCI(frame, curBCI);
                        continue loop;
                    }

                    case TABLESWITCH: {
                        int index = popInt(frame, top - 1);
                        BytecodeTableSwitch switchHelper = BytecodeTableSwitch.INSTANCE;
                        int low = switchHelper.lowKey(bs, curBCI);
                        int high = switchHelper.highKey(bs, curBCI);
                        assert low <= high;

                        // Interpreter uses direct lookup.
                        if (CompilerDirectives.inInterpreter()) {
                            int targetBCI;
                            if (low <= index && index <= high) {
                                targetBCI = switchHelper.targetAt(bs, curBCI, index - low);
                            } else {
                                targetBCI = switchHelper.defaultTarget(bs, curBCI);
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

                        // i could overflow if high == Integer.MAX_VALUE.
                        // This loops take that into account.
                        for (int i = low; i != high + 1; ++i) {
                            if (i == index) {
                                // Key found.
                                int targetBCI = switchHelper.targetAt(bs, curBCI, i - low);
                                CompilerAsserts.partialEvaluationConstant(targetBCI);
                                checkBackEdge(curBCI, targetBCI, top, curOpcode);
                                if (instrument != null) {
                                    nextStatementIndex = instrument.getStatementIndexAfterJump(statementIndex, curBCI, targetBCI);
                                }
                                top += Bytecodes.stackEffectOf(curOpcode);
                                curBCI = targetBCI;
                                preBCI(frame, curBCI);
                                continue loop;
                            }
                        }

                        // Key not found.
                        int targetBCI = switchHelper.defaultTarget(bs, curBCI);
                        CompilerAsserts.partialEvaluationConstant(targetBCI);
                        checkBackEdge(curBCI, targetBCI, top, curOpcode);
                        if (instrument != null) {
                            nextStatementIndex = instrument.getStatementIndexAfterJump(statementIndex, curBCI, targetBCI);
                        }
                        top += Bytecodes.stackEffectOf(curOpcode);
                        curBCI = targetBCI;
                        preBCI(frame, curBCI);
                        continue loop;
                    }
                    case LOOKUPSWITCH: {
                        int key = popInt(frame, top - 1);
                        BytecodeLookupSwitch switchHelper = BytecodeLookupSwitch.INSTANCE;
                        int low = 0;
                        int high = switchHelper.numberOfCases(bs, curBCI) - 1;
                        while (low <= high) {
                            int mid = (low + high) >>> 1;
                            int midVal = switchHelper.keyAt(bs, curBCI, mid);
                            if (midVal < key) {
                                low = mid + 1;
                            } else if (midVal > key) {
                                high = mid - 1;
                            } else {
                                // Key found.
                                int targetBCI = curBCI + switchHelper.offsetAt(bs, curBCI, mid);
                                CompilerAsserts.partialEvaluationConstant(targetBCI);
                                checkBackEdge(curBCI, targetBCI, top, curOpcode);
                                if (instrument != null) {
                                    nextStatementIndex = instrument.getStatementIndexAfterJump(statementIndex, curBCI, targetBCI);
                                }
                                top += Bytecodes.stackEffectOf(curOpcode);
                                curBCI = targetBCI;
                                preBCI(frame, curBCI);
                                continue loop;
                            }
                        }

                        // Key not found.
                        int targetBCI = switchHelper.defaultTarget(bs, curBCI);
                        CompilerAsserts.partialEvaluationConstant(targetBCI);
                        checkBackEdge(curBCI, targetBCI, top, curOpcode);
                        if (instrument != null) {
                            nextStatementIndex = instrument.getStatementIndexAfterJump(statementIndex, curBCI, targetBCI);
                        }
                        top += Bytecodes.stackEffectOf(curOpcode);
                        curBCI = targetBCI;
                        preBCI(frame, curBCI);
                        continue loop;
                    }
                    // @formatter:off
                    case IRETURN: return notifyReturn(frame, statementIndex, exitMethodAndReturn(popInt(frame, top - 1)));
                    case LRETURN: return notifyReturn(frame, statementIndex, exitMethodAndReturnObject(popLong(frame, top - 1)));
                    case FRETURN: return notifyReturn(frame, statementIndex, exitMethodAndReturnObject(popFloat(frame, top - 1)));
                    case DRETURN: return notifyReturn(frame, statementIndex, exitMethodAndReturnObject(popDouble(frame, top - 1)));
                    case ARETURN: return notifyReturn(frame, statementIndex, exitMethodAndReturnObject(popObject(frame, top - 1)));
                    case RETURN : return notifyReturn(frame, statementIndex, exitMethodAndReturn());

                    // TODO(peterssen): Order shuffled.
                    case GETSTATIC : // fall through
                    case GETFIELD  : top += getField(frame, top, resolveField(curOpcode, bs.readCPI(curBCI)), curBCI, curOpcode, statementIndex); break;
                    case PUTSTATIC : // fall through
                    case PUTFIELD  : top += putField(frame, top, resolveField(curOpcode, bs.readCPI(curBCI)), curBCI, curOpcode, statementIndex); break;

                    case INVOKEVIRTUAL: // fall through
                    case INVOKESPECIAL: // fall through
                    case INVOKESTATIC: // fall through

                    case INVOKEINTERFACE: top += quickenInvoke(frame, top, curBCI, curOpcode, statementIndex); break;

                    case NEW         : putObject(frame, top, InterpreterToVM.newObject(resolveType(curOpcode, bs.readCPI(curBCI)), true)); break;
                    case NEWARRAY    : putObject(frame, top - 1, InterpreterToVM.allocatePrimitiveArray(bs.readByte(curBCI), popInt(frame, top - 1), getMeta())); break;
                    case ANEWARRAY   : putObject(frame, top - 1, allocateArray(resolveType(curOpcode, bs.readCPI(curBCI)), popInt(frame, top - 1))); break;
                    case ARRAYLENGTH : arrayLength(frame, top, curBCI); break;
                    case ATHROW      : throw Meta.throwException(nullCheck(popObject(frame, top - 1)));

                    case CHECKCAST   : top += quickenCheckCast(frame, top, curBCI, curOpcode); break;
                    case INSTANCEOF  : top += quickenInstanceOf(frame, top, curBCI, curOpcode); break;

                    case MONITORENTER: getRoot().monitorEnter(frame, nullCheck(popObject(frame, top - 1))); break;
                    case MONITOREXIT : getRoot().monitorExit(frame, nullCheck(popObject(frame, top - 1))); break;

                    case WIDE:
                        CompilerDirectives.transferToInterpreter();
                        throw EspressoError.shouldNotReachHere("BytecodeStream.currentBC() should never return this bytecode.");

                    case MULTIANEWARRAY: top += allocateMultiArray(frame, top, resolveType(curOpcode, bs.readCPI(curBCI)), bs.readUByte(curBCI + 3)); break;

                    case BREAKPOINT:
                        CompilerDirectives.transferToInterpreter();
                        throw EspressoError.unimplemented(Bytecodes.nameOf(curOpcode) + " not supported.");

                    case INVOKEDYNAMIC: top += quickenInvokeDynamic(frame, top, curBCI, curOpcode); break;
                    case QUICK: {
                        QuickNode quickNode = nodes[bs.readCPI(curBCI)];
                        if (quickNode.removedByRedefintion()) {
                            CompilerDirectives.transferToInterpreterAndInvalidate();
                            synchronized (this) {
                                // re-check if node was already replaced by another thread
                                if (quickNode != nodes[bs.readCPI(curBCI)]) {
                                    // another thread beat us
                                    quickNode = nodes[bs.readCPI(curBCI)];
                                } else {
                                    // other threads might still have beat us but if
                                    // so, the resolution failed and so will we below
                                    BytecodeStream original = new BytecodeStream(getMethodVersion().getCodeAttribute().getOriginalCode());
                                    char cpi = original.readCPI(curBCI);
                                    int nodeOpcode = original.currentBC(curBCI);
                                    Method resolutionSeed = resolveMethodNoCache(nodeOpcode, cpi);
                                    quickNode = insert(dispatchQuickened(top, curBCI, cpi, nodeOpcode, statementIndex, resolutionSeed, getContext().InlineFieldAccessors));
                                    nodes[bs.readCPI(curBCI)] = quickNode;
                                }
                            }
                            top += quickNode.execute(frame);
                        } else {
                            top += quickNode.execute(frame);
                        }
                        break;
                    }
                    case SLIM_QUICK: top += sparseNodes[curBCI].execute(frame); break;

                    default:
                        CompilerDirectives.transferToInterpreter();
                        throw EspressoError.shouldNotReachHere(Bytecodes.nameOf(curOpcode));
                }
                // @formatter:on
            } catch (EspressoException | StackOverflowError | OutOfMemoryError e) {
                if (instrument != null && e instanceof EspressoException) {
                    instrument.notifyExceptionAt(frame, e, statementIndex);
                }
                CompilerAsserts.partialEvaluationConstant(curBCI);
                // Handle both guest and host StackOverflowError.
                if (e == getContext().getStackOverflow() || e instanceof StackOverflowError) {
                    // Always deopt on SOE.
                    CompilerDirectives.transferToInterpreter();
                    EspressoException wrappedStackOverflowError = null;
                    if (e == getContext().getStackOverflow()) {
                        wrappedStackOverflowError = (EspressoException) e;
                    } else {
                        wrappedStackOverflowError = getContext().getStackOverflow();
                    }
                    /*
                     * Stack Overflow management. All calls to stack manipulation are manually
                     * inlined to prevent another SOE.
                     *
                     * Note: no need to check for the stacktrace being null, as we reset the frames
                     * at each apparition of a host SOE.
                     */
                    if (stackOverflowErrorInfo != null) {
                        for (int i = 0; i < stackOverflowErrorInfo.length; i += 3) {
                            if (curBCI >= stackOverflowErrorInfo[i] && curBCI < stackOverflowErrorInfo[i + 1]) {
                                top = 0;
                                putObject(frame, 0, wrappedStackOverflowError.getExceptionObject());
                                top++;
                                int targetBCI = stackOverflowErrorInfo[i + 2];
                                checkBackEdge(curBCI, targetBCI, top, NOP);
                                if (instrument != null) {
                                    nextStatementIndex = instrument.getStatementIndexAfterJump(statementIndex, curBCI, targetBCI);
                                }
                                curBCI = targetBCI;
                                preBCI(frame, curBCI);
                                continue loop; // skip bs.next()
                            }
                        }
                    }
                    if (instrument != null) {
                        instrument.notifyExceptionAt(frame, wrappedStackOverflowError, statementIndex);
                    }
                    throw wrappedStackOverflowError;

                } else /* EspressoException or OutOfMemoryError */ {

                    EspressoException wrappedException;
                    if (e instanceof EspressoException) {
                        wrappedException = (EspressoException) e;
                    } else {
                        assert e instanceof OutOfMemoryError;
                        CompilerDirectives.transferToInterpreter();
                        wrappedException = getContext().getOutOfMemory();
                    }

                    ExceptionHandler[] handlers = getMethodVersion().getExceptionHandlers();
                    ExceptionHandler handler = null;
                    for (ExceptionHandler toCheck : handlers) {
                        if (curBCI >= toCheck.getStartBCI() && curBCI < toCheck.getEndBCI()) {
                            Klass catchType = null;
                            if (!toCheck.isCatchAll()) {
                                // exception handlers are similar to instanceof bytecodes, so we
                                // pass instanceof
                                catchType = resolveType(Bytecodes.INSTANCEOF, (char) toCheck.catchTypeCPI());
                            }
                            if (catchType == null || InterpreterToVM.instanceOf(wrappedException.getExceptionObject(), catchType)) {
                                // the first found exception handler is our exception handler
                                handler = toCheck;
                                break;
                            }
                        }
                    }
                    if (handler != null) {
                        top = 0;
                        putObject(frame, 0, wrappedException.getExceptionObject());
                        top++;
                        int targetBCI = handler.getHandlerBCI();
                        checkBackEdge(curBCI, targetBCI, top, NOP);
                        if (instrument != null) {
                            nextStatementIndex = instrument.getStatementIndexAfterJump(statementIndex, curBCI, targetBCI);
                        }
                        curBCI = targetBCI;
                        preBCI(frame, curBCI);
                        continue loop; // skip bs.next()
                    } else {
                        if (instrument != null) {
                            instrument.notifyExceptionAt(frame, wrappedException, statementIndex);
                        }
                        throw wrappedException;
                    }
                }
            } catch (EspressoExitException e) {
                getRoot().abortMonitor(frame);
                throw e;
            }
            // This check includes newly rewritten QUICK nodes, not just curOpcode == quick
            if (noForeignObjects.isValid() && (bs.currentBC(curBCI) == QUICK || bs.currentBC(curBCI) == SLIM_QUICK)) {
                QuickNode quickNode;
                if (bs.currentBC(curBCI) == QUICK) {
                    quickNode = nodes[bs.readCPI(curBCI)];
                } else {
                    assert bs.currentBC(curBCI) == SLIM_QUICK;
                    quickNode = sparseNodes[curBCI];
                }
                if (quickNode.producedForeignObject(frame)) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    noForeignObjects.invalidate();
                }
            }
            top += Bytecodes.stackEffectOf(curOpcode);
            int targetBCI = bs.nextBCI(curBCI);
            if (instrument != null) {
                nextStatementIndex = instrument.getNextStatementIndex(statementIndex, targetBCI);
            }
            curBCI = targetBCI;
            preBCI(frame, curBCI);
        }
    }

    private void preBCI(VirtualFrame frame, int curBCI) {
        livenessAnalysis.performPreBCI(frame, curBCI, this);
    }

    private void postLocalAccess(VirtualFrame frame, int curBCI) {
        livenessAnalysis.performPostBCI(frame, curBCI, this);
    }

    private EspressoRootNode getRoot() {
        if (rootNode == null) {
            rootNode = (EspressoRootNode) getRootNode();
        }
        return rootNode;
    }

    public int readBCI(Frame frame) {
        try {
            assert bciSlot != null;
            return frame.getInt(bciSlot);
        } catch (FrameSlotTypeException e) {
            CompilerDirectives.transferToInterpreter();
            throw EspressoError.shouldNotReachHere(e);
        }
    }

    private Object notifyReturn(VirtualFrame frame, int statementIndex, Object toReturn) {
        if (instrumentation != null) {
            instrumentation.notifyReturn(frame, statementIndex, toReturn);
        }
        return toReturn;
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
                    this.instrumentation = info = insert(new InstrumentationSupport(getMethodVersion()));
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

    private boolean takeBranch(VirtualFrame frame, int top, int opcode) {
        assert Bytecodes.isBranch(opcode);
        // @formatter:off
        switch (opcode) {
            case IFEQ      : return popInt(frame, top - 1) == 0;
            case IFNE      : return popInt(frame, top - 1) != 0;
            case IFLT      : return popInt(frame, top - 1)  < 0;
            case IFGE      : return popInt(frame, top - 1) >= 0;
            case IFGT      : return popInt(frame, top - 1)  > 0;
            case IFLE      : return popInt(frame, top - 1) <= 0;
            case IF_ICMPEQ : return popInt(frame, top - 1) == popInt(frame, top - 2);
            case IF_ICMPNE : return popInt(frame, top - 1) != popInt(frame, top - 2);
            case IF_ICMPLT : return popInt(frame, top - 1)  > popInt(frame, top - 2);
            case IF_ICMPGE : return popInt(frame, top - 1) <= popInt(frame, top - 2);
            case IF_ICMPGT : return popInt(frame, top - 1)  < popInt(frame, top - 2);
            case IF_ICMPLE : return popInt(frame, top - 1) >= popInt(frame, top - 2);
            case IF_ACMPEQ : return popObject(frame, top - 1) == popObject(frame, top - 2);
            case IF_ACMPNE : return popObject(frame, top - 1) != popObject(frame, top - 2);
            case GOTO      : // fall though
            case GOTO_W    : return true; // unconditional
            case IFNULL    : return StaticObject.isNull(popObject(frame, top - 1));
            case IFNONNULL : return StaticObject.notNull(popObject(frame, top - 1));
            default        :
                CompilerDirectives.transferToInterpreter();
                throw EspressoError.shouldNotReachHere("non-branching bytecode");
        }
        // @formatter:on
    }

    private static JavaKind arrayAccessKind(int opcode) {
        assert (IALOAD <= opcode && opcode <= SALOAD) || (IASTORE <= opcode && opcode <= SASTORE);
        switch (opcode) {
            case IALOAD: // fall through
            case IASTORE:
                return JavaKind.Int;
            case LALOAD: // fall through
            case LASTORE:
                return JavaKind.Long;
            case FALOAD: // fall through
            case FASTORE:
                return JavaKind.Float;
            case DALOAD: // fall through
            case DASTORE:
                return JavaKind.Double;
            case AALOAD: // fall through
            case AASTORE:
                return JavaKind.Object;
            case BALOAD: // fall through
            case BASTORE:
                return JavaKind.Byte; // or Boolean
            case CALOAD: // fall through
            case CASTORE:
                return JavaKind.Char;
            case SALOAD: // fall through
            case SASTORE:
                return JavaKind.Short;
            default:
                CompilerDirectives.transferToInterpreter();
                throw EspressoError.shouldNotReachHere();
        }
    }

    private void arrayLength(VirtualFrame frame, int top, int curBCI) {
        StaticObject array = nullCheck(popObject(frame, top - 1));
        if (noForeignObjects.isValid() || array.isEspressoObject()) {
            putInt(frame, top - 1, InterpreterToVM.arrayLength(array));
        } else {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            // The array was released, it must be restored for the quickening.
            putObject(frame, top - 1, array);
            // The stack effect difference vs. original bytecode is always 0.
            quickenArrayLength(frame, top, curBCI);
        }
    }

    private void arrayLoad(VirtualFrame frame, int top, int curBCI, int loadOpcode) {
        assert IALOAD <= loadOpcode && loadOpcode <= SALOAD;
        JavaKind kind = arrayAccessKind(loadOpcode);
        CompilerAsserts.partialEvaluationConstant(kind);
        int index = popInt(frame, top - 1);
        StaticObject array = nullCheck(popObject(frame, top - 2));
        if (noForeignObjects.isValid() || array.isEspressoObject()) {
            // @formatter:off
            switch (kind) {
                case Byte:    putInt(frame, top - 2, getInterpreterToVM().getArrayByte(index, array, this));      break;
                case Short:   putInt(frame, top - 2, getInterpreterToVM().getArrayShort(index, array, this));     break;
                case Char:    putInt(frame, top - 2, getInterpreterToVM().getArrayChar(index, array, this));      break;
                case Int:     putInt(frame, top - 2, getInterpreterToVM().getArrayInt(index, array, this));       break;
                case Float:   putFloat(frame, top - 2, getInterpreterToVM().getArrayFloat(index, array, this));   break;
                case Long:    putLong(frame, top - 2, getInterpreterToVM().getArrayLong(index, array, this));     break;
                case Double:  putDouble(frame, top - 2, getInterpreterToVM().getArrayDouble(index, array, this)); break;
                case Object:  putObject(frame, top - 2, getInterpreterToVM().getArrayObject(index, array, this)); break;
                default:
                    CompilerDirectives.transferToInterpreter();
                    throw EspressoError.shouldNotReachHere();
            }
            // @formatter:on
        } else {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            // The array was released, it must be restored for the quickening.
            putInt(frame, top - 1, index);
            putObject(frame, top - 2, array);
            // The stack effect difference vs. original bytecode is always 0.
            quickenArrayLoad(frame, top, curBCI, loadOpcode, kind);
        }
    }

    private void arrayStore(VirtualFrame frame, int top, int curBCI, int storeOpcode) {
        assert IASTORE <= storeOpcode && storeOpcode <= SASTORE;
        JavaKind kind = arrayAccessKind(storeOpcode);
        CompilerAsserts.partialEvaluationConstant(kind);
        int offset = kind.needsTwoSlots() ? 2 : 1;
        int index = popInt(frame, top - 1 - offset);
        StaticObject array = nullCheck(popObject(frame, top - 2 - offset));
        if (noForeignObjects.isValid() || array.isEspressoObject()) {
            // @formatter:off
            switch (kind) {
                case Byte:    getInterpreterToVM().setArrayByte((byte) popInt(frame, top - 1), index, array, this);   break;
                case Short:   getInterpreterToVM().setArrayShort((short) popInt(frame, top - 1), index, array, this); break;
                case Char:    getInterpreterToVM().setArrayChar((char) popInt(frame, top - 1), index, array, this);   break;
                case Int:     getInterpreterToVM().setArrayInt(popInt(frame, top - 1), index, array, this);           break;
                case Float:   getInterpreterToVM().setArrayFloat(popFloat(frame, top - 1), index, array, this);       break;
                case Long:    getInterpreterToVM().setArrayLong(popLong(frame, top - 1), index, array, this);         break;
                case Double:  getInterpreterToVM().setArrayDouble(popDouble(frame, top - 1), index, array, this);     break;
                case Object:  referenceArrayStore(frame, top, index, array);     break;
                default:
                    CompilerDirectives.transferToInterpreter();
                    throw EspressoError.shouldNotReachHere();
            }
            // @formatter:on
        } else {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            // The array was released, it must be restored for the quickening.
            putInt(frame, top - 1 - offset, index);
            putObject(frame, top - 2 - offset, array);
            // The stack effect difference vs. original bytecode is always 0.
            quickenArrayStore(frame, top, curBCI, storeOpcode, kind);
        }
    }

    private void referenceArrayStore(VirtualFrame frame, int top, int index, StaticObject array) {
        if (refArrayStoreNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            synchronized (this) {
                if (refArrayStoreNode == null) {
                    refArrayStoreNode = insert(new EspressoReferenceArrayStoreNode(getContext()));
                }
            }
        }
        refArrayStoreNode.arrayStore(popObject(frame, top - 1), index, array);
    }

    private int checkBackEdge(int curBCI, int targetBCI, int top, int opcode) {
        int newTop = top + Bytecodes.stackEffectOf(opcode);
        if (targetBCI <= curBCI) {
            checkStopping(curBCI, targetBCI);
            LoopNode.reportLoopCount(this, 1);
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

    private JavaKind peekKind(VirtualFrame frame, int slot) {
        if (frame.isObject(stackSlots[slot])) {
            return JavaKind.Object;
        }
        if (frame.isLong(stackSlots[slot])) {
            return JavaKind.Long;
        }
        CompilerDirectives.transferToInterpreter();
        throw EspressoError.shouldNotReachHere();
    }

    // region Operand stack shuffling

    private void dup1(VirtualFrame frame, int top) {
        // value1 -> value1, value1
        if (frame.isObject(stackSlots[top - 1])) {
            putObjectOrReturnAddress(frame, top, peekReturnAddressOrObject(frame, top - 1));
        } else {
            putInt(frame, top, peekInt(frame, top - 1));
        }
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
        RuntimeConstantPool pool = getConstantPool();
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
            StaticObject internedString = pool.resolvedStringAt(cpi);
            putObject(frame, top, internedString);
        } else if (constant instanceof ClassConstant) {
            assert opcode == LDC || opcode == LDC_W;
            Klass klass = pool.resolvedKlassAt(getMethod().getDeclaringKlass(), cpi);
            putObject(frame, top, klass.mirror());
        } else if (constant instanceof MethodHandleConstant) {
            assert opcode == LDC || opcode == LDC_W;
            StaticObject methodHandle = pool.resolvedMethodHandleAt(getMethod().getDeclaringKlass(), cpi);
            putObject(frame, top, methodHandle);
        } else if (constant instanceof MethodTypeConstant) {
            assert opcode == LDC || opcode == LDC_W;
            StaticObject methodType = pool.resolvedMethodTypeAt(getMethod().getDeclaringKlass(), cpi);
            putObject(frame, top, methodType);
        } else if (constant instanceof DynamicConstant) {
            DynamicConstant.Resolved dynamicConstant = pool.resolvedDynamicConstantAt(getMethod().getDeclaringKlass(), cpi);
            dynamicConstant.putResolved(frame, top, this);

        } else {
            CompilerDirectives.transferToInterpreter();
            throw EspressoError.unimplemented(constant.toString());
        }
    }

    protected RuntimeConstantPool getConstantPool() {
        return getMethodVersion().getPool();
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

    private void addSlimQuickNode(QuickNode node, int curBCI) {
        CompilerAsserts.neverPartOfCompilation();
        Objects.requireNonNull(node);
        if (sparseNodes == QuickNode.EMPTY_ARRAY) {
            sparseNodes = new QuickNode[getMethodVersion().getCodeAttribute().getCode().length];
        }
        sparseNodes[curBCI] = insert(node);
    }

    private void patchBci(int bci, byte opcode, char nodeIndex) {
        CompilerAsserts.neverPartOfCompilation();
        assert Bytecodes.isQuickened(opcode);
        byte[] code = getMethodVersion().getCodeAttribute().getCode();

        int oldBC = code[bci];
        code[bci] = opcode;
        if (opcode == (byte) QUICK) {
            code[bci + 1] = (byte) ((nodeIndex >> 8) & 0xFF);
            code[bci + 2] = (byte) ((nodeIndex) & 0xFF);
        }
        // NOP-padding.
        for (int i = Bytecodes.lengthOf(opcode); i < Bytecodes.lengthOf(oldBC); ++i) {
            code[bci + i] = (byte) NOP;
        }
    }

    private QuickNode injectQuick(int curBCI, QuickNode quick, int opcode) {
        QUICKENED_BYTECODES.inc();
        CompilerAsserts.neverPartOfCompilation();
        if (opcode == SLIM_QUICK) {
            addSlimQuickNode(quick, curBCI);
            patchBci(curBCI, (byte) SLIM_QUICK, (char) 0);
        } else {
            char nodeIndex = addQuickNode(quick);
            patchBci(curBCI, (byte) QUICK, nodeIndex);
        }
        return quick;
    }

    private int quickenCheckCast(final VirtualFrame frame, int top, int curBCI, int opcode) {
        if (StaticObject.isNull(peekObject(frame, top - 1))) {
            // Skip resolution.
            return -Bytecodes.stackEffectOf(opcode);
        }
        CompilerDirectives.transferToInterpreterAndInvalidate();
        assert opcode == CHECKCAST;
        QuickNode quick;
        synchronized (this) {
            if (bs.currentBC(curBCI) == QUICK) {
                quick = nodes[bs.readCPI(curBCI)];
            } else {
                Klass typeToCheck = resolveType(CHECKCAST, bs.readCPI(curBCI));
                quick = injectQuick(curBCI, new CheckCastNode(typeToCheck, top, curBCI), QUICK);
            }
        }
        return quick.execute(frame) - Bytecodes.stackEffectOf(opcode);
    }

    private int quickenInstanceOf(final VirtualFrame frame, int top, int curBCI, int opcode) {
        if (StaticObject.isNull(peekObject(frame, top - 1))) {
            // Skip resolution.
            putInt(frame, top - 1, 0);
            return -Bytecodes.stackEffectOf(opcode);
        }
        CompilerDirectives.transferToInterpreterAndInvalidate();
        assert opcode == INSTANCEOF;
        QuickNode quick;
        synchronized (this) {
            if (bs.currentBC(curBCI) == QUICK) {
                quick = nodes[bs.readCPI(curBCI)];
            } else {
                Klass typeToCheck = resolveType(opcode, bs.readCPI(curBCI));
                quick = injectQuick(curBCI, new InstanceOfNode(typeToCheck, top, curBCI), QUICK);
            }
        }
        return quick.execute(frame) - Bytecodes.stackEffectOf(opcode);
    }

    private int quickenInvoke(final VirtualFrame frame, int top, int curBCI, int opcode, int statementIndex) {
        QUICKENED_INVOKES.inc();
        CompilerDirectives.transferToInterpreterAndInvalidate();
        assert Bytecodes.isInvoke(opcode);
        QuickNode quick;
        synchronized (this) {
            if (bs.currentBC(curBCI) == QUICK) {
                quick = nodes[bs.readCPI(curBCI)];
            } else {
                // During resolution of the symbolic reference to the method, any of the exceptions
                // pertaining to method resolution (&sect;5.4.3.3) can be thrown.
                char cpi = bs.readCPI(curBCI);
                Method resolutionSeed = resolveMethod(opcode, cpi);
                QuickNode invoke = dispatchQuickened(top, curBCI, cpi, opcode, statementIndex, resolutionSeed, getContext().InlineFieldAccessors);
                quick = injectQuick(curBCI, invoke, QUICK);
            }
        }
        // Perform the call outside of the lock.
        return quick.execute(frame) - Bytecodes.stackEffectOf(opcode);
    }

    /**
     * Revert speculative quickening e.g. revert inlined fields accessors to a normal invoke.
     * INVOKEVIRTUAL -> QUICK (InlinedGetter/SetterNode) -> QUICK (InvokeVirtualNode)
     */
    public int reQuickenInvoke(final VirtualFrame frame, int top, int curBCI, int opcode, int statementIndex, Method resolutionSeed) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        assert Bytecodes.isInvoke(opcode);
        QuickNode invoke = null;
        synchronized (this) {
            assert bs.currentBC(curBCI) == QUICK;
            char cpi = bs.readCPI(curBCI);
            invoke = dispatchQuickened(top, curBCI, cpi, opcode, statementIndex, resolutionSeed, false);
            nodes[cpi] = nodes[cpi].replace(invoke);
        }
        // Perform the call outside of the lock.
        return invoke.execute(frame);
    }

    // region quickenForeign

    public int quickenGetField(final VirtualFrame frame, int top, int curBCI, int opcode, int statementIndex, Field field) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        assert opcode == GETFIELD;
        QuickNode getField;
        synchronized (this) {
            if (bs.currentBC(curBCI) == QUICK) {
                getField = nodes[bs.readCPI(curBCI)];
            } else {
                getField = injectQuick(curBCI, new QuickenedGetFieldNode(top, curBCI, statementIndex, field), QUICK);
            }
        }
        return getField.execute(frame) - Bytecodes.stackEffectOf(opcode);
    }

    public int quickenPutField(final VirtualFrame frame, int top, int curBCI, int opcode, int statementIndex, Field field) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        assert opcode == PUTFIELD;
        QuickNode putField;
        synchronized (this) {
            if (bs.currentBC(curBCI) == QUICK) {
                putField = nodes[bs.readCPI(curBCI)];
            } else {
                putField = injectQuick(curBCI, new QuickenedPutFieldNode(top, curBCI, field, statementIndex), QUICK);
            }
        }
        return putField.execute(frame) - Bytecodes.stackEffectOf(opcode);
    }

    private int quickenArrayLength(final VirtualFrame frame, int top, int curBCI) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        QuickNode arrayLengthNode;
        synchronized (this) {
            if (bs.currentBC(curBCI) == SLIM_QUICK) {
                arrayLengthNode = sparseNodes[curBCI];
            } else {
                arrayLengthNode = injectQuick(curBCI, ArrayLengthNodeGen.create(top, curBCI), SLIM_QUICK);
            }
        }
        return arrayLengthNode.execute(frame) - Bytecodes.stackEffectOf(ARRAYLENGTH);
    }

    private int quickenArrayLoad(final VirtualFrame frame, int top, int curBCI, int loadOpcode, JavaKind componentKind) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        QuickNode arrayLoadNode;
        synchronized (this) {
            if (bs.currentBC(curBCI) == SLIM_QUICK) {
                arrayLoadNode = sparseNodes[curBCI];
            } else {
                // @formatter:off
                switch (componentKind)  {
                    case Boolean: // fall-through
                    case Byte:
                        arrayLoadNode = ByteArrayLoadNodeGen.create(top, curBCI);
                        break;
                    case Short  : arrayLoadNode = ShortArrayLoadNodeGen.create(top, curBCI);  break;
                    case Char   : arrayLoadNode = CharArrayLoadNodeGen.create(top, curBCI);   break;
                    case Int    : arrayLoadNode = IntArrayLoadNodeGen.create(top, curBCI);    break;
                    case Float  : arrayLoadNode = FloatArrayLoadNodeGen.create(top, curBCI);  break;
                    case Long   : arrayLoadNode = LongArrayLoadNodeGen.create(top, curBCI);   break;
                    case Double : arrayLoadNode = DoubleArrayLoadNodeGen.create(top, curBCI); break;
                    case Object : arrayLoadNode = ReferenceArrayLoadNodeGen.create(top, curBCI); break;
                    default:
                        CompilerDirectives.transferToInterpreter();
                        throw EspressoError.shouldNotReachHere("unexpected kind");
                }
                // @formatter:on
                arrayLoadNode = injectQuick(curBCI, arrayLoadNode, SLIM_QUICK);
            }
        }
        return arrayLoadNode.execute(frame) - Bytecodes.stackEffectOf(loadOpcode);
    }

    private int quickenArrayStore(final VirtualFrame frame, int top, int curBCI, int storeOpcode, JavaKind componentKind) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        QuickNode arrayStoreNode;
        synchronized (this) {
            if (bs.currentBC(curBCI) == SLIM_QUICK) {
                arrayStoreNode = sparseNodes[curBCI];
            } else {
                // @formatter:off
                switch (componentKind)  {
                    case Boolean: // fall-through
                    case Byte:
                        arrayStoreNode = ByteArrayStoreNodeGen.create(top, curBCI);
                        break;
                    case Short  : arrayStoreNode = ShortArrayStoreNodeGen.create(top, curBCI);  break;
                    case Char   : arrayStoreNode = CharArrayStoreNodeGen.create(top, curBCI);   break;
                    case Int    : arrayStoreNode = IntArrayStoreNodeGen.create(top, curBCI);    break;
                    case Float  : arrayStoreNode = FloatArrayStoreNodeGen.create(top, curBCI);  break;
                    case Long   : arrayStoreNode = LongArrayStoreNodeGen.create(top, curBCI);   break;
                    case Double : arrayStoreNode = DoubleArrayStoreNodeGen.create(top, curBCI); break;
                    case Object : arrayStoreNode = ReferenceArrayStoreNodeGen.create(top, curBCI); break;
                    default:
                        CompilerDirectives.transferToInterpreter();
                        throw EspressoError.shouldNotReachHere("unexpected kind");
                }
                // @formatter:on
                arrayStoreNode = injectQuick(curBCI, arrayStoreNode, SLIM_QUICK);
            }
        }
        return arrayStoreNode.execute(frame) - Bytecodes.stackEffectOf(storeOpcode);
    }

    // endregion quickenForeign

    private QuickNode dispatchQuickened(int top, int curBCI, char cpi, int opcode, int statementIndex, Method resolutionSeed, boolean allowFieldAccessInlining) {
        assert !allowFieldAccessInlining || getContext().InlineFieldAccessors;
        QuickNode invoke;
        Method resolved = resolutionSeed;
        switch (opcode) {
            case INVOKESTATIC:
                // Otherwise, if the resolved method is an instance method, the invokestatic
                // instruction throws an IncompatibleClassChangeError.
                if (!resolved.isStatic()) {
                    CompilerDirectives.transferToInterpreter();
                    throw Meta.throwException(getMeta().java_lang_IncompatibleClassChangeError);
                }
                break;
            case INVOKEINTERFACE:
                // Otherwise, if the resolved method is static or (jdk8 or earlier) private, the
                // invokeinterface instruction throws an IncompatibleClassChangeError.
                if (resolved.isStatic() ||
                                (getContext().getJavaVersion().java8OrEarlier() && resolved.isPrivate())) {
                    CompilerDirectives.transferToInterpreter();
                    throw Meta.throwException(getMeta().java_lang_IncompatibleClassChangeError);
                }
                break;
            case INVOKEVIRTUAL:
                // Otherwise, if the resolved method is a class (static) method, the invokevirtual
                // instruction throws an IncompatibleClassChangeError.
                if (resolved.isStatic()) {
                    CompilerDirectives.transferToInterpreter();
                    throw Meta.throwException(getMeta().java_lang_IncompatibleClassChangeError);
                }
                break;
            case INVOKESPECIAL:
                // Otherwise, if the resolved method is an instance initialization method, and the
                // class in which it is declared is not the class symbolically referenced by the
                // instruction, a NoSuchMethodError is thrown.
                if (resolved.isConstructor()) {
                    if (resolved.getDeclaringKlass().getName() != getConstantPool().methodAt(cpi).getHolderKlassName(getConstantPool())) {
                        CompilerDirectives.transferToInterpreter();
                        throw Meta.throwException(getMeta().java_lang_NoSuchMethodError);
                    }
                }
                // Otherwise, if the resolved method is a class (static) method, the invokespecial
                // instruction throws an IncompatibleClassChangeError.
                if (resolved.isStatic()) {
                    CompilerDirectives.transferToInterpreter();
                    throw Meta.throwException(getMeta().java_lang_IncompatibleClassChangeError);
                }
                // If all of the following are true, let C be the direct superclass of the current
                // class:
                //
                // * The resolved method is not an instance initialization method (&sect;2.9).
                //
                // * If the symbolic reference names a class (not an interface), then that class is
                // a superclass of the current class.
                //
                // * The ACC_SUPER flag is set for the class file (&sect;4.1). In Java SE 8 and
                // above, the Java Virtual Machine considers the ACC_SUPER flag to be set in every
                // class file, regardless of the actual value of the flag in the class file and the
                // version of the class file.
                if (!resolved.isConstructor()) {
                    Klass declaringKlass = getMethod().getDeclaringKlass();
                    Klass symbolicRef = ((MethodRefConstant.Indexes) getConstantPool().methodAt(cpi)).getResolvedHolderKlass(declaringKlass, getConstantPool());
                    if (!symbolicRef.isInterface() && symbolicRef != declaringKlass && declaringKlass.getSuperKlass() != null && symbolicRef != declaringKlass.getSuperKlass() &&
                                    symbolicRef.isAssignableFrom(declaringKlass)) {
                        resolved = declaringKlass.getSuperKlass().lookupMethod(resolved.getName(), resolved.getRawSignature(), declaringKlass);
                    }
                }
                break;
            default:
                CompilerDirectives.transferToInterpreter();
                throw EspressoError.unimplemented("Quickening for " + Bytecodes.nameOf(opcode));
        }

        if (allowFieldAccessInlining && resolved.isInlinableGetter()) {
            invoke = InlinedGetterNode.create(resolved, top, opcode, curBCI, statementIndex);
        } else if (allowFieldAccessInlining && resolved.isInlinableSetter()) {
            invoke = InlinedSetterNode.create(resolved, top, opcode, curBCI, statementIndex);
        } else if (resolved.isPolySignatureIntrinsic()) {
            invoke = new InvokeHandleNode(resolved, getMethod().getDeclaringKlass(), top, curBCI);
        } else if (opcode == INVOKEINTERFACE && resolved.getITableIndex() < 0) {
            if (resolved.isPrivate()) {
                assert getJavaVersion().java9OrLater();
                // Interface private methods do not appear in itables.
                invoke = new InvokeSpecialNode(resolved, top, curBCI);
            } else {
                // Can happen in old classfiles that calls j.l.Object on interfaces.
                invoke = InvokeVirtualNodeGen.create(resolved, top, curBCI);
            }
        } else if (opcode == INVOKEVIRTUAL && (resolved.isFinalFlagSet() || resolved.getDeclaringKlass().isFinalFlagSet() || resolved.isPrivate())) {
            invoke = new InvokeSpecialNode(resolved, top, curBCI);
        } else {
            // @formatter:off
            switch (opcode) {
                case INVOKESTATIC    : invoke = new InvokeStaticNode(resolved, top, curBCI);          break;
                case INVOKEINTERFACE : invoke = InvokeInterfaceNodeGen.create(resolved, top, curBCI); break;
                case INVOKEVIRTUAL   : invoke = InvokeVirtualNodeGen.create(resolved, top, curBCI);   break;
                case INVOKESPECIAL   : invoke = new InvokeSpecialNode(resolved, top, curBCI);         break;
                default              :
                    CompilerDirectives.transferToInterpreter();
                    throw EspressoError.unimplemented("Quickening for " + Bytecodes.nameOf(opcode));
            }
            // @formatter:on
        }
        return invoke;
    }

    private int quickenInvokeDynamic(final VirtualFrame frame, int top, int curBCI, int opcode) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        assert (Bytecodes.INVOKEDYNAMIC == opcode);
        RuntimeConstantPool pool = getConstantPool();
        QuickNode quick = null;
        int indyIndex = -1;
        synchronized (this) {
            if (bs.currentBC(curBCI) == QUICK) {
                // Check if someone did the job for us. Defer the call until we are out of the lock.
                quick = nodes[bs.readCPI(curBCI)];
            } else {
                // fetch indy under lock.
                indyIndex = bs.readCPI(curBCI);
            }
        }
        if (quick != null) {
            // Do invocation outside of the lock.
            return quick.execute(frame) - Bytecodes.stackEffectOf(opcode);
        }

        // Resolution should happen outside of the bytecode patching lock.
        InvokeDynamicConstant.Resolved inDy = pool.resolvedInvokeDynamicAt(getMethod().getDeclaringKlass(), indyIndex);

        // re-lock to check if someone did the job for us, since this was a heavy operation.
        synchronized (this) {
            if (bs.currentBC(curBCI) == QUICK) {
                // someone beat us to it, just trust him.
                quick = nodes[bs.readCPI(curBCI)];
            } else {
                quick = injectQuick(curBCI, new InvokeDynamicCallSiteNode(inDy.getMemberName(), inDy.getUnboxedAppendix(), inDy.getParsedSignature(), getMeta(), top, curBCI), QUICK);
            }
        }
        return quick.execute(frame) - Bytecodes.stackEffectOf(opcode);
    }

    // endregion Bytecode quickening

    // region Class/Method/Field resolution

    // Exposed to CheckCastNode and InstanceOfNode
    public Klass resolveType(int opcode, char cpi) {
        assert opcode == INSTANCEOF || opcode == CHECKCAST || opcode == NEW || opcode == ANEWARRAY || opcode == MULTIANEWARRAY;
        return getConstantPool().resolvedKlassAt(getMethod().getDeclaringKlass(), cpi);
    }

    public Method resolveMethod(int opcode, char cpi) {
        assert Bytecodes.isInvoke(opcode);
        return getConstantPool().resolvedMethodAt(getMethod().getDeclaringKlass(), cpi);
    }

    private Method resolveMethodNoCache(int opcode, char cpi) {
        CompilerAsserts.neverPartOfCompilation();
        assert Bytecodes.isInvoke(opcode);
        return getConstantPool().resolvedMethodAtNoCache(getMethod().getDeclaringKlass(), cpi);
    }

    private Field resolveField(int opcode, char cpi) {
        assert opcode == GETFIELD || opcode == GETSTATIC || opcode == PUTFIELD || opcode == PUTSTATIC;
        return getConstantPool().resolvedFieldAt(getMethod().getDeclaringKlass(), cpi);
    }

    // endregion Class/Method/Field resolution

    // region Instance/array allocation

    private static StaticObject allocateArray(Klass componentType, int length) {
        assert !componentType.isPrimitive();
        return InterpreterToVM.newReferenceArray(componentType, length);
    }

    @ExplodeLoop
    private int allocateMultiArray(final VirtualFrame frame, int top, Klass klass, int allocatedDimensions) {
        assert klass.isArray();
        CompilerAsserts.partialEvaluationConstant(allocatedDimensions);
        CompilerAsserts.partialEvaluationConstant(klass);
        int[] dimensions = new int[allocatedDimensions];
        for (int i = 0; i < allocatedDimensions; ++i) {
            dimensions[i] = popInt(frame, top - allocatedDimensions + i);
        }
        putObject(frame, top - allocatedDimensions, getInterpreterToVM().newMultiArray(((ArrayKlass) klass).getComponentType(), dimensions));
        return -allocatedDimensions; // Does not include the created (pushed) array.
    }

    // endregion Instance/array allocation

    // region Method return

    private Object exitMethodAndReturn(int result) {
        // @formatter:off
        switch (Signatures.returnKind(getMethod().getParsedSignature())) {
            case Boolean :
                // TODO: Use a node ?
                return stackIntToBoolean(result);
            case Byte    : return (byte) result;
            case Short   : return (short) result;
            case Char    : return (char) result;
            case Int     : return result;
            default      :
                CompilerDirectives.transferToInterpreter();
                throw EspressoError.shouldNotReachHere("unexpected kind");
        }
        // @formatter:on
    }

    private boolean stackIntToBoolean(int result) {
        return getJavaVersion().java9OrLater() ? (result & 1) != 0 : result != 0;
    }

    private static Object exitMethodAndReturnObject(Object result) {
        return result;
    }

    private static Object exitMethodAndReturn() {
        return exitMethodAndReturnObject(StaticObject.NULL);
    }

    private Object exitMethodEarlyAndReturn(Object result) {
        if (Signatures.returnKind(getMethod().getParsedSignature()) == JavaKind.Void) {
            return exitMethodAndReturn();
        } else {
            return result;
        }
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
            enterImplicitExceptionProfile();
            throw getMeta().throwNullPointerException();
        }
        return value;
    }

    public void enterImplicitExceptionProfile() {
        if (!implicitExceptionProfile) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            implicitExceptionProfile = true;
        }
    }

    private int checkNonZero(int value) {
        if (value != 0) {
            return value;
        }
        enterImplicitExceptionProfile();
        throw Meta.throwExceptionWithMessage(getMeta().java_lang_ArithmeticException, "/ by zero");
    }

    private long checkNonZero(long value) {
        if (value != 0L) {
            return value;
        }
        enterImplicitExceptionProfile();
        throw Meta.throwExceptionWithMessage(getMeta().java_lang_ArithmeticException, "/ by zero");
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
    private int putField(final VirtualFrame frame, int top, Field field, int curBCI, int opcode, int statementIndex) {
        assert opcode == PUTFIELD || opcode == PUTSTATIC;
        CompilerAsserts.partialEvaluationConstant(field);

        /*
         * PUTFIELD: Otherwise, if the resolved field is a static field, putfield throws an
         * IncompatibleClassChangeError.
         *
         * PUTSTATIC: Otherwise, if the resolved field is not a static (class) field or an interface
         * field, putstatic throws an IncompatibleClassChangeError.
         */
        if (field.isStatic() != (opcode == PUTSTATIC)) {
            CompilerDirectives.transferToInterpreter();
            throw Meta.throwExceptionWithMessage(getMeta().java_lang_IncompatibleClassChangeError,
                            String.format("Expected %s field %s.%s",
                                            (opcode == PUTSTATIC) ? "static" : "non-static",
                                            field.getDeclaringKlass().getNameAsString(),
                                            field.getNameAsString()));
        }

        /*
         * PUTFIELD: Otherwise, if the field is final, it must be declared in the current class, and
         * the instruction must occur in an instance initialization method (<init>) of the current
         * class. Otherwise, an IllegalAccessError is thrown.
         * 
         * PUTSTATIC: Otherwise, if the field is final, it must be declared in the current class,
         * and the instruction must occur in the <clinit> method of the current class. Otherwise, an
         * IllegalAccessError is thrown.
         */
        if (field.isFinalFlagSet()) {
            if (field.getDeclaringKlass() != getMethod().getDeclaringKlass()) {
                CompilerDirectives.transferToInterpreter();
                throw Meta.throwExceptionWithMessage(getMeta().java_lang_IllegalAccessError,
                                String.format("Update to %s final field %s.%s attempted from a different class (%s) than the field's declaring class",
                                                (opcode == PUTSTATIC) ? "static" : "non-static",
                                                field.getDeclaringKlass().getNameAsString(),
                                                field.getNameAsString(),
                                                getMethod().getDeclaringKlass().getNameAsString()));
            }

            boolean enforceInitializerCheck = (getContext().SpecCompliancyMode == STRICT) ||
                            // HotSpot enforces this only for >= Java 9 (v53) .class files.
                            field.getDeclaringKlass().getMajorVersion() >= ClassfileParser.JAVA_9_VERSION;

            if (enforceInitializerCheck &&
                            ((opcode == PUTFIELD && !getMethod().isConstructor()) ||
                                            (opcode == PUTSTATIC && !getMethod().isClassInitializer()))) {
                CompilerDirectives.transferToInterpreter();
                throw Meta.throwExceptionWithMessage(getMeta().java_lang_IllegalAccessError,
                                String.format("Update to %s final field %s.%s attempted from a different method (%s) than the initializer method %s ",
                                                (opcode == PUTSTATIC) ? "static" : "non-static",
                                                field.getDeclaringKlass().getNameAsString(),
                                                field.getNameAsString(),
                                                getMethod().getNameAsString(),
                                                (opcode == PUTSTATIC) ? "<clinit>" : "<init>"));
            }
        }

        assert field.isStatic() == (opcode == PUTSTATIC);

        int slot = top - field.getKind().getSlotCount() - 1; // -receiver
        StaticObject receiver = field.isStatic()
                        ? field.getDeclaringKlass().tryInitializeAndGetStatics()
                        // Do not release the object, it might be read again in PutFieldNode
                        : nullCheck(popObject(frame, slot));

        if (!noForeignObjects.isValid() && opcode == PUTFIELD) {
            if (receiver.isForeignObject()) {
                // Restore the receiver for quickening.
                putObject(frame, slot, receiver);
                return quickenPutField(frame, top, curBCI, opcode, statementIndex, field);
            }
        }

        switch (field.getKind()) {
            case Boolean:
                boolean booleanValue = stackIntToBoolean(popInt(frame, top - 1));
                if (instrumentation != null) {
                    instrumentation.notifyFieldModification(frame, statementIndex, field, receiver, booleanValue);
                }
                InterpreterToVM.setFieldBoolean(booleanValue, receiver, field);
                break;
            case Byte:
                byte byteValue = (byte) popInt(frame, top - 1);
                if (instrumentation != null) {
                    instrumentation.notifyFieldModification(frame, statementIndex, field, receiver, byteValue);
                }
                InterpreterToVM.setFieldByte(byteValue, receiver, field);
                break;
            case Char:
                char charValue = (char) popInt(frame, top - 1);
                if (instrumentation != null) {
                    instrumentation.notifyFieldModification(frame, statementIndex, field, receiver, charValue);
                }
                InterpreterToVM.setFieldChar(charValue, receiver, field);
                break;
            case Short:
                short shortValue = (short) popInt(frame, top - 1);
                if (instrumentation != null) {
                    instrumentation.notifyFieldModification(frame, statementIndex, field, receiver, shortValue);
                }
                InterpreterToVM.setFieldShort(shortValue, receiver, field);
                break;
            case Int:
                int intValue = popInt(frame, top - 1);
                if (instrumentation != null) {
                    instrumentation.notifyFieldModification(frame, statementIndex, field, receiver, intValue);
                }
                InterpreterToVM.setFieldInt(intValue, receiver, field);
                break;
            case Double:
                double doubleValue = popDouble(frame, top - 1);
                if (instrumentation != null) {
                    instrumentation.notifyFieldModification(frame, statementIndex, field, receiver, doubleValue);
                }
                InterpreterToVM.setFieldDouble(doubleValue, receiver, field);
                break;
            case Float:
                float floatValue = popFloat(frame, top - 1);
                if (instrumentation != null) {
                    instrumentation.notifyFieldModification(frame, statementIndex, field, receiver, floatValue);
                }
                InterpreterToVM.setFieldFloat(floatValue, receiver, field);
                break;
            case Long:
                long longValue = popLong(frame, top - 1);
                if (instrumentation != null) {
                    instrumentation.notifyFieldModification(frame, statementIndex, field, receiver, longValue);
                }
                InterpreterToVM.setFieldLong(longValue, receiver, field);
                break;
            case Object:
                StaticObject value = popObject(frame, top - 1);
                if (instrumentation != null) {
                    instrumentation.notifyFieldModification(frame, statementIndex, field, receiver, value);
                }
                InterpreterToVM.setFieldObject(value, receiver, field);
                break;
            default:
                CompilerDirectives.transferToInterpreter();
                throw EspressoError.shouldNotReachHere("unexpected kind");
        }
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
    private int getField(final VirtualFrame frame, int top, Field field, int curBCI, int opcode, int statementIndex) {
        assert opcode == GETFIELD || opcode == GETSTATIC;
        CompilerAsserts.partialEvaluationConstant(field);

        /*
         * GETFIELD: Otherwise, if the resolved field is a static field, getfield throws an
         * IncompatibleClassChangeError.
         * 
         * GETSTATIC: Otherwise, if the resolved field is not a static (class) field or an interface
         * field, getstatic throws an IncompatibleClassChangeError.
         */
        if (field.isStatic() != (opcode == GETSTATIC)) {
            CompilerDirectives.transferToInterpreter();
            throw Meta.throwExceptionWithMessage(getMeta().java_lang_IncompatibleClassChangeError,
                            String.format("Expected %s field %s.%s",
                                            (opcode == GETSTATIC) ? "static" : "non-static",
                                            field.getDeclaringKlass().getNameAsString(),
                                            field.getNameAsString()));
        }

        assert field.isStatic() == (opcode == GETSTATIC);

        int slot = top - 1;
        StaticObject receiver = field.isStatic()
                        ? field.getDeclaringKlass().tryInitializeAndGetStatics()
                        // Do not release the object, it might be read again in GetFieldNode
                        : nullCheck(peekObject(frame, slot));

        if (!noForeignObjects.isValid() && opcode == GETFIELD) {
            if (receiver.isForeignObject()) {
                // Restore the receiver for quickening.
                putObject(frame, slot, receiver);
                return quickenGetField(frame, top, curBCI, opcode, statementIndex, field);
            }
        }

        if (instrumentation != null) {
            instrumentation.notifyFieldAccess(frame, statementIndex, field, receiver);
        }

        int resultAt = field.isStatic() ? top : (top - 1);
        // @formatter:off
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
            default      :
                CompilerDirectives.transferToInterpreter();
                throw EspressoError.shouldNotReachHere("unexpected kind");
        }
        // @formatter:on
        if (noForeignObjects.isValid() && field.getKind().isObject() && peekObject(frame, resultAt).isForeignObject()) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            noForeignObjects.invalidate();
        }
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
            switch (kind) {
                case Boolean : args[i + extraParam] = (popInt(frame, argAt) != 0);  break;
                case Byte    : args[i + extraParam] = (byte) popInt(frame, argAt);  break;
                case Short   : args[i + extraParam] = (short) popInt(frame, argAt); break;
                case Char    : args[i + extraParam] = (char) popInt(frame, argAt);  break;
                case Int     : args[i + extraParam] = popInt(frame, argAt);         break;
                case Float   : args[i + extraParam] = popFloat(frame, argAt);       break;
                case Long    : args[i + extraParam] = popLong(frame, argAt);        break;
                case Double  : args[i + extraParam] = popDouble(frame, argAt);      break;
                case Object  : args[i + extraParam] = popObject(frame, argAt); break;
                default      :
                    CompilerDirectives.transferToInterpreter();
                    throw EspressoError.shouldNotReachHere();
            }
            // @formatter:on
            argAt -= kind.getSlotCount();
        }
        if (hasReceiver) {
            args[0] = popObject(frame, argAt);
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
            switch (kind) {
                case Boolean : // Fall through
                case Byte    : // Fall through
                case Short   : // Fall through
                case Char    : // Fall through
                case Int     : args[i + start] = popInt(frame, argAt);    break;
                case Float   : args[i + start] = popFloat(frame, argAt);  break;
                case Long    : args[i + start] = popLong(frame, argAt);   break;
                case Double  : args[i + start] = popDouble(frame, argAt); break;
                case Object  : args[i + start] = popObject(frame, argAt); break;
                default      :
                    CompilerDirectives.transferToInterpreter();
                    throw EspressoError.shouldNotReachHere();
            }
            // @formatter:on
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
        switch (kind) {
            case Boolean : putInt(frame, top, ((boolean) value) ? 1 : 0); break;
            case Byte    : putInt(frame, top, (byte) value);              break;
            case Short   : putInt(frame, top, (short) value);             break;
            case Char    : putInt(frame, top, (char) value);              break;
            case Int     : putInt(frame, top, (int) value);               break;
            case Float   : putFloat(frame, top, (float) value);           break;
            case Long    : putLong(frame, top, (long) value);             break;
            case Double  : putDouble(frame, top, (double) value);         break;
            case Object  : putObject(frame, top, (StaticObject) value);   break;
            case Void    : /* ignore */                                   break;
            default      :
                CompilerDirectives.transferToInterpreter();
                throw EspressoError.shouldNotReachHere();
        }
        // @formatter:on
        return kind.getSlotCount();
    }

    // internal
    private void putKindUnsafe1(VirtualFrame frame, int slot, Object value, JavaKind kind) {
        // @formatter:off
        switch (kind) {
            case Long    : frame.setLong(stackSlots[slot], (long) value); break;
            case Object  : putObjectOrReturnAddress(frame, slot, value);  break;
            default      :
                CompilerDirectives.transferToInterpreter();
                throw EspressoError.shouldNotReachHere();
        }
        // @formatter:on
    }

    public StaticObject peekReceiver(final VirtualFrame frame, int top, Method m) {
        assert !m.isStatic();
        int skipSlots = Signatures.slotsForParameters(m.getParsedSignature());
        StaticObject result = peekObject(frame, top - skipSlots - 1);
        assert result != null;
        return result;
    }

    @Override
    public boolean hasTag(Class<? extends Tag> tag) {
        return tag == StandardTags.RootBodyTag.class || tag == StandardTags.RootTag.class;
    }

    public void notifyFieldModification(VirtualFrame frame, int index, Field field, StaticObject receiver, Object value) {
        // Notifications are only for Espresso objects
        if (instrumentation != null && (noForeignObjects.isValid() || receiver.isEspressoObject())) {
            instrumentation.notifyFieldModification(frame, index, field, receiver, value);
        }
    }

    public void notifyFieldAccess(VirtualFrame frame, int index, Field field, StaticObject receiver) {
        // Notifications are only for Espresso objects
        if (instrumentation != null && (noForeignObjects.isValid() || receiver.isEspressoObject())) {
            instrumentation.notifyFieldAccess(frame, index, field, receiver);
        }
    }

    static final class InstrumentationSupport extends Node {
        @Children private final EspressoInstrumentableNode[] statementNodes;
        @Child private MapperBCI hookBCIToNodeIndex;

        private final EspressoContext context;
        private final MethodVersion method;

        InstrumentationSupport(MethodVersion method) {
            this.context = method.getMethod().getContext();
            this.method = method;

            LineNumberTableAttribute table = method.getLineNumberTableAttribute();

            if (table != LineNumberTableAttribute.EMPTY) {
                LineNumberTableAttribute.Entry[] entries = table.getEntries();
                this.statementNodes = new EspressoInstrumentableNode[entries.length];
                this.hookBCIToNodeIndex = new MapperBCI(table);

                for (int i = 0; i < entries.length; i++) {
                    LineNumberTableAttribute.Entry entry = entries[i];
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
        void notifyStatement(VirtualFrame frame, int statementIndex, int nextStatementIndex) {
            CompilerAsserts.partialEvaluationConstant(statementIndex);
            CompilerAsserts.partialEvaluationConstant(nextStatementIndex);
            if (statementIndex == nextStatementIndex) {
                return;
            }
            exitAt(frame, statementIndex);
            enterAt(frame, nextStatementIndex);
        }

        public void notifyEntry(@SuppressWarnings("unused") VirtualFrame frame) {
            // TODO(Gregersen) - method entry breakpoints are currently implemented by submitting
            // first line breakpoints within each method. This works insofar the method has a valid
            // line table. For classes compiled without debug information we could use this hook
            // instead.
        }

        public void notifyReturn(VirtualFrame frame, int statementIndex, Object returnValue) {
            if (context.getJDWPListener().hasMethodBreakpoint(method, returnValue)) {
                enterAt(frame, statementIndex);
            }
        }

        void notifyExceptionAt(VirtualFrame frame, Throwable t, int statementIndex) {
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

        int getStatementIndexAfterJump(int statementIndex, int curBCI, int targetBCI) {
            if (hookBCIToNodeIndex == null) {
                return 0;
            }
            return hookBCIToNodeIndex.lookup(statementIndex, curBCI, targetBCI);
        }

        int getNextStatementIndex(int statementIndex, int nextBCI) {
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
