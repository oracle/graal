/*
 * Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.espresso.EspressoOptions.SpecComplianceMode.STRICT;
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
import static com.oracle.truffle.espresso.nodes.EspressoFrame.clear;
import static com.oracle.truffle.espresso.nodes.EspressoFrame.createFrameDescriptor;
import static com.oracle.truffle.espresso.nodes.EspressoFrame.dup1;
import static com.oracle.truffle.espresso.nodes.EspressoFrame.dup2;
import static com.oracle.truffle.espresso.nodes.EspressoFrame.dup2x1;
import static com.oracle.truffle.espresso.nodes.EspressoFrame.dup2x2;
import static com.oracle.truffle.espresso.nodes.EspressoFrame.dupx1;
import static com.oracle.truffle.espresso.nodes.EspressoFrame.dupx2;
import static com.oracle.truffle.espresso.nodes.EspressoFrame.getBCI;
import static com.oracle.truffle.espresso.nodes.EspressoFrame.getLocalDouble;
import static com.oracle.truffle.espresso.nodes.EspressoFrame.getLocalFloat;
import static com.oracle.truffle.espresso.nodes.EspressoFrame.getLocalInt;
import static com.oracle.truffle.espresso.nodes.EspressoFrame.getLocalLong;
import static com.oracle.truffle.espresso.nodes.EspressoFrame.getLocalObject;
import static com.oracle.truffle.espresso.nodes.EspressoFrame.getLocalReturnAddress;
import static com.oracle.truffle.espresso.nodes.EspressoFrame.peekObject;
import static com.oracle.truffle.espresso.nodes.EspressoFrame.popDouble;
import static com.oracle.truffle.espresso.nodes.EspressoFrame.popFloat;
import static com.oracle.truffle.espresso.nodes.EspressoFrame.popInt;
import static com.oracle.truffle.espresso.nodes.EspressoFrame.popLong;
import static com.oracle.truffle.espresso.nodes.EspressoFrame.popObject;
import static com.oracle.truffle.espresso.nodes.EspressoFrame.popReturnAddressOrObject;
import static com.oracle.truffle.espresso.nodes.EspressoFrame.putDouble;
import static com.oracle.truffle.espresso.nodes.EspressoFrame.putFloat;
import static com.oracle.truffle.espresso.nodes.EspressoFrame.putInt;
import static com.oracle.truffle.espresso.nodes.EspressoFrame.putLong;
import static com.oracle.truffle.espresso.nodes.EspressoFrame.putObject;
import static com.oracle.truffle.espresso.nodes.EspressoFrame.putReturnAddress;
import static com.oracle.truffle.espresso.nodes.EspressoFrame.setBCI;
import static com.oracle.truffle.espresso.nodes.EspressoFrame.setLocalDouble;
import static com.oracle.truffle.espresso.nodes.EspressoFrame.setLocalFloat;
import static com.oracle.truffle.espresso.nodes.EspressoFrame.setLocalInt;
import static com.oracle.truffle.espresso.nodes.EspressoFrame.setLocalLong;
import static com.oracle.truffle.espresso.nodes.EspressoFrame.setLocalObject;
import static com.oracle.truffle.espresso.nodes.EspressoFrame.setLocalObjectOrReturnAddress;
import static com.oracle.truffle.espresso.nodes.EspressoFrame.startingStackOffset;
import static com.oracle.truffle.espresso.nodes.EspressoFrame.swapSingle;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.HostCompilerDirectives.BytecodeInterpreterSwitch;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleSafepoint;
import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.InstrumentableNode;
import com.oracle.truffle.api.instrumentation.ProbeNode;
import com.oracle.truffle.api.instrumentation.StandardTags.StatementTag;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.nodes.BytecodeOSRNode;
import com.oracle.truffle.api.nodes.ControlFlowException;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.LoopNode;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.analysis.liveness.LivenessAnalysis;
import com.oracle.truffle.espresso.bytecode.BytecodeLookupSwitch;
import com.oracle.truffle.espresso.bytecode.BytecodeStream;
import com.oracle.truffle.espresso.bytecode.BytecodeTableSwitch;
import com.oracle.truffle.espresso.bytecode.Bytecodes;
import com.oracle.truffle.espresso.bytecode.MapperBCI;
import com.oracle.truffle.espresso.classfile.ClassfileParser;
import com.oracle.truffle.espresso.classfile.RuntimeConstantPool;
import com.oracle.truffle.espresso.classfile.attributes.BootstrapMethodsAttribute;
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
import com.oracle.truffle.espresso.impl.ObjectKlass;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.ExceptionHandler;
import com.oracle.truffle.espresso.meta.JavaKind;
import com.oracle.truffle.espresso.nodes.helper.EspressoReferenceArrayStoreNode;
import com.oracle.truffle.espresso.nodes.quick.BaseQuickNode;
import com.oracle.truffle.espresso.nodes.quick.CheckCastQuickNode;
import com.oracle.truffle.espresso.nodes.quick.InstanceOfQuickNode;
import com.oracle.truffle.espresso.nodes.quick.QuickNode;
import com.oracle.truffle.espresso.nodes.quick.VolatileArrayAccess;
import com.oracle.truffle.espresso.nodes.quick.interop.ArrayLengthQuickNode;
import com.oracle.truffle.espresso.nodes.quick.interop.ByteArrayLoadQuickNode;
import com.oracle.truffle.espresso.nodes.quick.interop.ByteArrayStoreQuickNode;
import com.oracle.truffle.espresso.nodes.quick.interop.CharArrayLoadQuickNode;
import com.oracle.truffle.espresso.nodes.quick.interop.CharArrayStoreQuickNode;
import com.oracle.truffle.espresso.nodes.quick.interop.DoubleArrayLoadQuickNode;
import com.oracle.truffle.espresso.nodes.quick.interop.DoubleArrayStoreQuickNode;
import com.oracle.truffle.espresso.nodes.quick.interop.FloatArrayLoadQuickNode;
import com.oracle.truffle.espresso.nodes.quick.interop.FloatArrayStoreQuickNode;
import com.oracle.truffle.espresso.nodes.quick.interop.IntArrayLoadQuickNode;
import com.oracle.truffle.espresso.nodes.quick.interop.IntArrayStoreQuickNode;
import com.oracle.truffle.espresso.nodes.quick.interop.LongArrayLoadQuickNode;
import com.oracle.truffle.espresso.nodes.quick.interop.LongArrayStoreQuickNode;
import com.oracle.truffle.espresso.nodes.quick.interop.QuickenedGetFieldNode;
import com.oracle.truffle.espresso.nodes.quick.interop.QuickenedPutFieldNode;
import com.oracle.truffle.espresso.nodes.quick.interop.ReferenceArrayLoadQuickNode;
import com.oracle.truffle.espresso.nodes.quick.interop.ReferenceArrayStoreQuickNode;
import com.oracle.truffle.espresso.nodes.quick.interop.ShortArrayLoadQuickNode;
import com.oracle.truffle.espresso.nodes.quick.interop.ShortArrayStoreQuickNode;
import com.oracle.truffle.espresso.nodes.quick.invoke.InvokeDynamicCallSiteNode;
import com.oracle.truffle.espresso.nodes.quick.invoke.InvokeHandleNode;
import com.oracle.truffle.espresso.nodes.quick.invoke.InvokeInterfaceQuickNode;
import com.oracle.truffle.espresso.nodes.quick.invoke.InvokeQuickNode;
import com.oracle.truffle.espresso.nodes.quick.invoke.InvokeSpecialQuickNode;
import com.oracle.truffle.espresso.nodes.quick.invoke.InvokeStaticQuickNode;
import com.oracle.truffle.espresso.nodes.quick.invoke.InvokeVirtualQuickNode;
import com.oracle.truffle.espresso.nodes.quick.invoke.inline.InlinedMethodNode;
import com.oracle.truffle.espresso.perf.DebugCounter;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.EspressoException;
import com.oracle.truffle.espresso.runtime.EspressoExitException;
import com.oracle.truffle.espresso.runtime.GuestAllocator;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.vm.InterpreterToVM;

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
public final class BytecodeNode extends AbstractInstrumentableBytecodeNode implements BytecodeOSRNode, GuestAllocator.AllocationProfiler {

    private static final DebugCounter EXECUTED_BYTECODES_COUNT = DebugCounter.create("Executed bytecodes");
    private static final DebugCounter QUICKENED_BYTECODES = DebugCounter.create("Quickened bytecodes");
    private static final DebugCounter QUICKENED_INVOKES = DebugCounter.create("Quickened invokes (excluding INDY)");

    private static final byte TRIVIAL_UNINITIALIZED = -1;
    private static final byte TRIVIAL_NO = 0;
    private static final byte TRIVIAL_YES = 1;

    private static final int REPORT_LOOP_STRIDE = 1 << 8;

    static {
        assert Integer.bitCount(REPORT_LOOP_STRIDE) == 1 : "must be a power of 2";
    }

    // must not be of type QuickNode as it might be wrapped by instrumentation
    @Children private BaseQuickNode[] nodes = QuickNode.EMPTY_ARRAY;
    @Children private BaseQuickNode[] sparseNodes = QuickNode.EMPTY_ARRAY;
    /**
     * Ideally, we would want one such node per AASTORE bytecode. Unfortunately, the AASTORE
     * bytecode is a single byte long, so we cannot quicken it, and it is far too common to pay for
     * spawning the sparse nodes array.
     */
    @Child private volatile EspressoReferenceArrayStoreNode refArrayStoreNode;

    @CompilationFinal(dimensions = 1) //
    private final int[] stackOverflowErrorInfo;

    /**
     * Outer array should be seen and used as a {@code @CompilationFinal volatile} array, while
     * inner arrays can be seen as {@code final} arrays.
     */
    @CompilationFinal(dimensions = 2) //
    private volatile int[][] jsrBci = null;

    private final BytecodeStream bs;

    @CompilationFinal private EspressoRootNode rootNode;

    @Child private volatile InstrumentationSupport instrumentation;

    private final Assumption noForeignObjects;

    // Cheap profile for implicit exceptions e.g. null checks, division by 0, index out of bounds.
    // All implicit exception paths in the method will be compiled if at least one implicit
    // exception is thrown.
    @CompilationFinal private boolean implicitExceptionProfile;

    @CompilationFinal private boolean linkageExceptionProfile;

    private final LivenessAnalysis livenessAnalysis;

    private byte trivialBytecodesCache = -1;

    @CompilationFinal private Object osrMetadata;

    private final FrameDescriptor frameDescriptor;

    private final MethodVersion methodVersion;

    public BytecodeNode(MethodVersion methodVersion) {
        CompilerAsserts.neverPartOfCompilation();
        Method method = methodVersion.getMethod();
        assert method.hasBytecodes();
        this.methodVersion = methodVersion;
        this.bs = new BytecodeStream(methodVersion.getCode());
        this.stackOverflowErrorInfo = method.getSOEHandlerInfo();
        this.frameDescriptor = createFrameDescriptor(methodVersion.getMaxLocals(), methodVersion.getMaxStackSize());
        this.noForeignObjects = Truffle.getRuntime().createAssumption("noForeignObjects");
        this.implicitExceptionProfile = false;
        this.livenessAnalysis = LivenessAnalysis.analyze(methodVersion);
        /*
         * The "triviality" is partially computed here since isTrivial is called from a compiler
         * thread where the context is not accessible.
         */
        this.trivialBytecodesCache = method.getOriginalCode().length <= method.getContext().getEspressoEnv().TrivialMethodSize
                        ? TRIVIAL_UNINITIALIZED
                        : TRIVIAL_NO;
    }

    public FrameDescriptor getFrameDescriptor() {
        return frameDescriptor;
    }

    Source getSource() {
        return getMethodVersion().getSource();
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
    private void initArguments(VirtualFrame frame) {
        Object[] arguments = frame.getArguments();

        boolean hasReceiver = !getMethod().isStatic();
        int receiverSlot = hasReceiver ? 1 : 0;
        int curSlot = 0;
        if (hasReceiver) {
            assert StaticObject.notNull((StaticObject) arguments[0]) : "null receiver in init arguments !";
            StaticObject receiver = (StaticObject) arguments[0];
            setLocalObject(frame, curSlot, receiver);
            checkNoForeignObjectAssumption(receiver);
            curSlot += JavaKind.Object.getSlotCount();
        }

        Symbol<Type>[] methodSignature = getMethod().getParsedSignature();
        int argCount = Signatures.parameterCount(methodSignature);
        CompilerAsserts.partialEvaluationConstant(argCount);
        for (int i = 0; i < argCount; ++i) {
            Symbol<Type> argType = Signatures.parameterType(methodSignature, i);
            // @formatter:off
            switch (argType.byteAt(0)) {
                case 'Z' : setLocalInt(frame, curSlot, ((boolean) arguments[i + receiverSlot]) ? 1 : 0); break;
                case 'B' : setLocalInt(frame, curSlot, ((byte) arguments[i + receiverSlot]));            break;
                case 'S' : setLocalInt(frame, curSlot, ((short) arguments[i + receiverSlot]));           break;
                case 'C' : setLocalInt(frame, curSlot, ((char) arguments[i + receiverSlot]));            break;
                case 'I' : setLocalInt(frame, curSlot, (int) arguments[i + receiverSlot]);               break;
                case 'F' : setLocalFloat(frame, curSlot, (float) arguments[i + receiverSlot]);           break;
                case 'J' : setLocalLong(frame, curSlot, (long) arguments[i + receiverSlot]);     ++curSlot; break;
                case 'D' : setLocalDouble(frame, curSlot, (double) arguments[i + receiverSlot]); ++curSlot; break;
                case '[' : // fall through
                case 'L' : {
                    // Reference type.
                    StaticObject argument = (StaticObject) arguments[i + receiverSlot];
                    setLocalObject(frame, curSlot, argument);
                    checkNoForeignObjectAssumption(argument);
                    break;
                }
                default :
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw EspressoError.shouldNotReachHere();
            }
            // @formatter:on
            ++curSlot;
        }
    }

    public void checkNoForeignObjectAssumption(StaticObject object) {
        if (noForeignObjects.isValid() && object.isForeignObject()) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            noForeignObjects.invalidate();
        }
    }

    @Override
    void initializeFrame(VirtualFrame frame) {
        initArguments(frame);
    }

    // region OSR support

    private static final class EspressoOSRInterpreterState {
        // The index of the top of the stack. At a back-edge, it is typically 0, but the JVM spec
        // does not guarantee this.
        final int top;
        // The statement index of the next instruction (if instrumentation is enabled).
        final int nextStatementIndex;

        EspressoOSRInterpreterState(int top, int nextStatementIndex) {
            this.top = top;
            this.nextStatementIndex = nextStatementIndex;
        }
    }

    @SuppressWarnings("serial")
    private static final class EspressoOSRReturnException extends ControlFlowException {
        private final Object result;
        private final Throwable throwable;

        EspressoOSRReturnException(Object result) {
            this.result = result;
            this.throwable = null;
        }

        EspressoOSRReturnException(Throwable throwable) {
            this.result = null;
            this.throwable = throwable;
        }

        Object getResultOrRethrow() {
            if (throwable != null) {
                throw sneakyThrow(throwable);
            }
            return result;
        }

        @SuppressWarnings("unchecked")
        private static <T extends Throwable> RuntimeException sneakyThrow(Throwable ex) throws T {
            throw (T) ex;
        }
    }

    @Override
    public Object executeOSR(VirtualFrame osrFrame, int target, Object interpreterState) {
        EspressoOSRInterpreterState state = (EspressoOSRInterpreterState) interpreterState;
        return executeBodyFromBCI(osrFrame, target, state.top, state.nextStatementIndex, true);
    }

    @Override
    public Object getOSRMetadata() {
        return osrMetadata;
    }

    @Override
    public void setOSRMetadata(Object osrMetadata) {
        this.osrMetadata = osrMetadata;
    }

    @Override
    public void prepareOSR(int target) {
        getRoot(); // force initialization of root node since we need it in OSR
    }

    @Override
    public void copyIntoOSRFrame(VirtualFrame frame, VirtualFrame parentFrame, int target, Object entryMetadata) {
        BytecodeOSRNode.super.copyIntoOSRFrame(frame, parentFrame, target, entryMetadata);
        setBCI(frame, target);
    }

    @Override
    public void restoreParentFrame(VirtualFrame osrFrame, VirtualFrame parentFrame) {
        BytecodeOSRNode.super.restoreParentFrame(osrFrame, parentFrame);
        setBCI(parentFrame, getBci(osrFrame));
    }

    // endregion OSR support

    /**
     * Smaller than int[1], does not kill int[] on write and doesn't need bounds checks.
     */
    private static final class Counter {
        int value;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        int startTop = startingStackOffset(getMethodVersion().getMaxLocals());
        return executeBodyFromBCI(frame, 0, startTop, 0, false);
    }

    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.MERGE_EXPLODE)
    @BytecodeInterpreterSwitch
    Object executeBodyFromBCI(VirtualFrame frame, int startBCI, int startTop, int startStatementIndex, boolean isOSR) {
        CompilerAsserts.partialEvaluationConstant(startBCI);
        int curBCI = startBCI;
        int top = startTop;
        final InstrumentationSupport instrument = this.instrumentation;
        int statementIndex = InstrumentationSupport.NO_STATEMENT;
        int nextStatementIndex = startStatementIndex;
        boolean skipEntryInstrumentation = isOSR;
        boolean skipLivenessActions = false;

        final Counter loopCount = new Counter();

        setBCI(frame, curBCI);

        if (instrument != null && !skipEntryInstrumentation) {
            instrument.notifyEntry(frame, this);
        }
        // During OSR, the method is not executed from the beginning hence onStart is not applicable
        if (!isOSR) {
            livenessAnalysis.onStart(frame, skipLivenessActions);
        }

        loop: while (true) {
            final int curOpcode = bs.opcode(curBCI);
            EXECUTED_BYTECODES_COUNT.inc();
            try {
                CompilerAsserts.partialEvaluationConstant(top);
                CompilerAsserts.partialEvaluationConstant(curBCI);
                CompilerAsserts.partialEvaluationConstant(skipEntryInstrumentation);
                CompilerAsserts.partialEvaluationConstant(curOpcode);

                CompilerAsserts.partialEvaluationConstant(statementIndex);
                CompilerAsserts.partialEvaluationConstant(nextStatementIndex);

                if (instrument != null || Bytecodes.canTrap(curOpcode)) {
                    /*
                     * curOpcode can be == WIDE, but none of the WIDE-prefixed bytecodes throw
                     * exceptions.
                     */
                    setBCI(frame, curBCI);
                }
                if (instrument != null) {
                    if (!skipEntryInstrumentation) {
                        instrument.notifyStatement(frame, statementIndex, nextStatementIndex);
                    }
                    skipEntryInstrumentation = false;
                    statementIndex = nextStatementIndex;
                }

                // @formatter:off
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

                    case LDC   : putPoolConstant(frame, top, bs.readCPI1(curBCI), curOpcode); break;
                    case LDC_W : // fall through
                    case LDC2_W: putPoolConstant(frame, top, bs.readCPI2(curBCI), curOpcode); break;

                    case ILOAD:
                        putInt(frame, top, getLocalInt(frame, bs.readLocalIndex1(curBCI)));
                        livenessAnalysis.performPostBCI(frame, curBCI, skipLivenessActions);
                        break;
                    case LLOAD:
                        putLong(frame, top, getLocalLong(frame, bs.readLocalIndex1(curBCI)));
                        livenessAnalysis.performPostBCI(frame, curBCI, skipLivenessActions);
                        break;
                    case FLOAD:
                        putFloat(frame, top, getLocalFloat(frame, bs.readLocalIndex1(curBCI)));
                        livenessAnalysis.performPostBCI(frame, curBCI, skipLivenessActions);
                        break;
                    case DLOAD:
                        putDouble(frame, top, getLocalDouble(frame, bs.readLocalIndex1(curBCI)));
                        livenessAnalysis.performPostBCI(frame, curBCI, skipLivenessActions);
                        break;
                    case ALOAD:
                        putObject(frame, top, getLocalObject(frame, bs.readLocalIndex1(curBCI)));
                        livenessAnalysis.performPostBCI(frame, curBCI, skipLivenessActions);
                        break;

                    case ILOAD_0: // fall through
                    case ILOAD_1: // fall through
                    case ILOAD_2: // fall through
                    case ILOAD_3:
                        putInt(frame, top, getLocalInt(frame, curOpcode - ILOAD_0));
                        livenessAnalysis.performPostBCI(frame, curBCI, skipLivenessActions);
                        break;
                    case LLOAD_0: // fall through
                    case LLOAD_1: // fall through
                    case LLOAD_2: // fall through
                    case LLOAD_3:
                        putLong(frame, top, getLocalLong(frame, curOpcode - LLOAD_0));
                        livenessAnalysis.performPostBCI(frame, curBCI, skipLivenessActions);
                        break;
                    case FLOAD_0: // fall through
                    case FLOAD_1: // fall through
                    case FLOAD_2: // fall through
                    case FLOAD_3:
                        putFloat(frame, top, getLocalFloat(frame, curOpcode - FLOAD_0));
                        livenessAnalysis.performPostBCI(frame, curBCI, skipLivenessActions);
                        break;
                    case DLOAD_0: // fall through
                    case DLOAD_1: // fall through
                    case DLOAD_2: // fall through
                    case DLOAD_3:
                        putDouble(frame, top, getLocalDouble(frame, curOpcode - DLOAD_0));
                        livenessAnalysis.performPostBCI(frame, curBCI, skipLivenessActions);
                        break;
                    case ALOAD_0:
                        putObject(frame, top, getLocalObject(frame, 0));
                        livenessAnalysis.performPostBCI(frame, curBCI, skipLivenessActions);
                        break;
                    case ALOAD_1: // fall through
                    case ALOAD_2: // fall through
                    case ALOAD_3:
                        putObject(frame, top, getLocalObject(frame, curOpcode - ALOAD_0));
                        livenessAnalysis.performPostBCI(frame, curBCI, skipLivenessActions);
                        break;

                    case IALOAD: // fall through
                    case LALOAD: // fall through
                    case FALOAD: // fall through
                    case DALOAD: // fall through
                    case BALOAD: // fall through
                    case CALOAD: // fall through
                    case SALOAD: arrayLoad(frame, top, curBCI, curOpcode); break;
                    case AALOAD:
                        arrayLoad(frame, top, curBCI, AALOAD);
                        checkNoForeignObjectAssumption(peekObject(frame, top - 2));
                        break;

                    case ISTORE:
                        setLocalInt(frame, bs.readLocalIndex1(curBCI), popInt(frame, top - 1));
                        livenessAnalysis.performPostBCI(frame, curBCI, skipLivenessActions);
                        break;
                    case LSTORE:
                        setLocalLong(frame, bs.readLocalIndex1(curBCI), popLong(frame, top - 1));
                        livenessAnalysis.performPostBCI(frame, curBCI, skipLivenessActions);
                        break;
                    case FSTORE:
                        setLocalFloat(frame, bs.readLocalIndex1(curBCI), popFloat(frame, top - 1));
                        livenessAnalysis.performPostBCI(frame, curBCI, skipLivenessActions);
                        break;
                    case DSTORE:
                        setLocalDouble(frame, bs.readLocalIndex1(curBCI), popDouble(frame, top - 1));
                        livenessAnalysis.performPostBCI(frame, curBCI, skipLivenessActions);
                        break;
                    case ASTORE:
                        setLocalObjectOrReturnAddress(frame, bs.readLocalIndex1(curBCI), popReturnAddressOrObject(frame, top - 1));
                        livenessAnalysis.performPostBCI(frame, curBCI, skipLivenessActions);
                        break;

                    case ISTORE_0: // fall through
                    case ISTORE_1: // fall through
                    case ISTORE_2: // fall through
                    case ISTORE_3:
                        setLocalInt(frame, curOpcode - ISTORE_0, popInt(frame, top - 1));
                        livenessAnalysis.performPostBCI(frame, curBCI, skipLivenessActions);
                        break;
                    case LSTORE_0: // fall through
                    case LSTORE_1: // fall through
                    case LSTORE_2: // fall through
                    case LSTORE_3:
                        setLocalLong(frame, curOpcode - LSTORE_0, popLong(frame, top - 1));
                        livenessAnalysis.performPostBCI(frame, curBCI, skipLivenessActions);
                        break;
                    case FSTORE_0: // fall through
                    case FSTORE_1: // fall through
                    case FSTORE_2: // fall through
                    case FSTORE_3:
                        setLocalFloat(frame, curOpcode - FSTORE_0, popFloat(frame, top - 1));
                        livenessAnalysis.performPostBCI(frame, curBCI, skipLivenessActions);
                        break;
                    case DSTORE_0: // fall through
                    case DSTORE_1: // fall through
                    case DSTORE_2: // fall through
                    case DSTORE_3:
                        setLocalDouble(frame, curOpcode - DSTORE_0, popDouble(frame, top - 1));
                        livenessAnalysis.performPostBCI(frame, curBCI, skipLivenessActions);
                        break;
                    case ASTORE_0: // fall through
                    case ASTORE_1: // fall through
                    case ASTORE_2: // fall through
                    case ASTORE_3:
                        setLocalObjectOrReturnAddress(frame, curOpcode - ASTORE_0, popReturnAddressOrObject(frame, top - 1));
                        livenessAnalysis.performPostBCI(frame, curBCI, skipLivenessActions);
                        break;

                    case IASTORE: // fall through
                    case LASTORE: // fall through
                    case FASTORE: // fall through
                    case DASTORE: // fall through
                    case AASTORE: // fall through
                    case BASTORE: // fall through
                    case CASTORE: // fall through
                    case SASTORE: arrayStore(frame, top, curBCI, curOpcode); break;

                    case POP2:
                        clear(frame, top - 1);
                        clear(frame, top - 2);
                        break;
                    case POP:
                        clear(frame, top - 1);
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

                    case ISUB: putInt(frame, top - 2, popInt(frame, top - 2) - popInt(frame, top - 1)); break;
                    case LSUB: putLong(frame, top - 4, popLong(frame, top - 3) - popLong(frame, top - 1)); break;
                    case FSUB: putFloat(frame, top - 2, popFloat(frame, top - 2) - popFloat(frame, top - 1)); break;
                    case DSUB: putDouble(frame, top - 4, popDouble(frame, top - 3) - popDouble(frame, top - 1)); break;

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

                    case IINC:
                        setLocalInt(frame, bs.readLocalIndex1(curBCI), getLocalInt(frame, bs.readLocalIndex1(curBCI)) + bs.readIncrement1(curBCI));
                        livenessAnalysis.performPostBCI(frame, curBCI, skipLivenessActions);
                        break;

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

                    // @formatter:on
                    case IFEQ: // fall through
                    case IFNE: // fall through
                    case IFLT: // fall through
                    case IFGE: // fall through
                    case IFGT: // fall through
                    case IFLE: // fall through
                        if (takeBranchPrimitive1(popInt(frame, top - 1), curOpcode)) {
                            int targetBCI = bs.readBranchDest2(curBCI);
                            top += Bytecodes.stackEffectOf(IFLE);
                            nextStatementIndex = beforeJumpChecks(frame, curBCI, targetBCI, top, statementIndex, instrument, loopCount, skipLivenessActions);
                            curBCI = targetBCI;
                            continue loop;
                        }
                        break;

                    case IF_ICMPEQ: // fall through
                    case IF_ICMPNE: // fall through
                    case IF_ICMPLT: // fall through
                    case IF_ICMPGE: // fall through
                    case IF_ICMPGT: // fall through
                    case IF_ICMPLE:
                        if (takeBranchPrimitive2(popInt(frame, top - 1), popInt(frame, top - 2), curOpcode)) {
                            top += Bytecodes.stackEffectOf(IF_ICMPLE);
                            nextStatementIndex = beforeJumpChecks(frame, curBCI, bs.readBranchDest2(curBCI), top, statementIndex, instrument, loopCount, skipLivenessActions);
                            curBCI = bs.readBranchDest2(curBCI);
                            continue loop;
                        }
                        break;

                    case IF_ACMPEQ: // fall through
                    case IF_ACMPNE:
                        if (takeBranchRef2(popObject(frame, top - 1), popObject(frame, top - 2), curOpcode)) {
                            int targetBCI = bs.readBranchDest2(curBCI);
                            top += Bytecodes.stackEffectOf(IF_ACMPNE);
                            nextStatementIndex = beforeJumpChecks(frame, curBCI, targetBCI, top, statementIndex, instrument, loopCount, skipLivenessActions);
                            curBCI = targetBCI;
                            continue loop;
                        }
                        break;

                    case IFNULL: // fall through
                    case IFNONNULL:
                        if (takeBranchRef1(popObject(frame, top - 1), curOpcode)) {
                            int targetBCI = bs.readBranchDest2(curBCI);
                            top += Bytecodes.stackEffectOf(IFNONNULL);
                            nextStatementIndex = beforeJumpChecks(frame, curBCI, targetBCI, top, statementIndex, instrument, loopCount, skipLivenessActions);
                            curBCI = targetBCI;
                            continue loop;
                        }
                        break;

                    case GOTO: {
                        int targetBCI = bs.readBranchDest2(curBCI);
                        nextStatementIndex = beforeJumpChecks(frame, curBCI, targetBCI, top, statementIndex, instrument, loopCount, skipLivenessActions);
                        curBCI = targetBCI;
                        continue loop;
                    }
                    case GOTO_W: {
                        int targetBCI = bs.readBranchDest4(curBCI);
                        nextStatementIndex = beforeJumpChecks(frame, curBCI, targetBCI, top, statementIndex, instrument, loopCount, skipLivenessActions);
                        curBCI = targetBCI;
                        continue loop;
                    }
                    case JSR: {
                        putReturnAddress(frame, top, bs.nextBCI(curBCI));
                        int targetBCI = bs.readBranchDest2(curBCI);
                        top += Bytecodes.stackEffectOf(JSR);
                        nextStatementIndex = beforeJumpChecks(frame, curBCI, targetBCI, top, statementIndex, instrument, loopCount, skipLivenessActions);
                        curBCI = targetBCI;
                        continue loop;
                    }
                    case JSR_W: {
                        putReturnAddress(frame, top, bs.nextBCI(curBCI));
                        int targetBCI = bs.readBranchDest4(curBCI);
                        top += Bytecodes.stackEffectOf(JSR_W);
                        nextStatementIndex = beforeJumpChecks(frame, curBCI, targetBCI, top, statementIndex, instrument, loopCount, skipLivenessActions);
                        curBCI = targetBCI;
                        continue loop;
                    }
                    case RET: {
                        // Use final local variables to pass in lambdas.
                        final int retOpBci = curBCI;
                        final int targetBCI = getLocalReturnAddress(frame, bs.readLocalIndex1(curBCI));
                        livenessAnalysis.performPostBCI(frame, curBCI, skipLivenessActions);

                        // Safely obtain the known targets mappings.
                        int[][] knownTargets = jsrBci;
                        if (knownTargets == null) {
                            CompilerDirectives.transferToInterpreterAndInvalidate();
                            atomic(() -> {
                                // Double-checked locking.
                                if (jsrBci == null) {
                                    jsrBci = new int[bs.endBCI()][];
                                }
                            });
                            knownTargets = jsrBci;
                        }

                        // Safely obtain the known targets for the current ret operation.
                        int[] knownRets = VolatileArrayAccess.volatileRead(knownTargets, retOpBci);
                        if (knownRets == null) {
                            CompilerDirectives.transferToInterpreterAndInvalidate();
                            atomic(() -> {
                                if (VolatileArrayAccess.volatileRead(jsrBci, retOpBci) != null) {
                                    return;
                                }
                                /*
                                 * Be very careful on updating the known target bcis, as if another
                                 * thread reads the not fully initialized array, it may consider 0
                                 * to be a valid RET target, completely breaking PE.
                                 */
                                int[] targets = new int[]{targetBCI};
                                // Also serves as a "final publication" barrier for the assignment
                                // above.
                                VolatileArrayAccess.volatileWrite(jsrBci, retOpBci, targets);
                            });
                            knownRets = VolatileArrayAccess.volatileRead(knownTargets, retOpBci);
                        }
                        assert knownRets != null;

                        // Lookup in the known targets to transform the return address to a
                        // constant.
                        for (int jsr : knownRets) {
                            if (jsr == targetBCI) {
                                CompilerAsserts.partialEvaluationConstant(jsr);
                                top += Bytecodes.stackEffectOf(RET);
                                nextStatementIndex = beforeJumpChecks(frame, curBCI, jsr, top, statementIndex, instrument, loopCount, skipLivenessActions);
                                curBCI = jsr;
                                continue loop;
                            }
                        }

                        // Lookup failed: Add the current target to the known targets.
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        atomic(() -> {
                            int[] currentRets = VolatileArrayAccess.volatileRead(jsrBci, retOpBci);
                            for (int jsr : currentRets) {
                                if (jsr == targetBCI) {
                                    // target has been added by another thread.
                                    return;
                                }
                            }
                            int[] updatedTargets = Arrays.copyOf(currentRets, currentRets.length + 1);
                            /*
                             * Be very careful on updating the known target bcis, as if another
                             * thread reads the not fully initialized array, it may consider 0 to be
                             * a valid RET target, completely breaking PE.
                             */
                            updatedTargets[updatedTargets.length - 1] = targetBCI;
                            // Also serves as a "final publication" barrier for the assignment
                            // above.
                            VolatileArrayAccess.volatileWrite(jsrBci, retOpBci, updatedTargets);
                        });
                        top += Bytecodes.stackEffectOf(RET);
                        nextStatementIndex = beforeJumpChecks(frame, retOpBci, targetBCI, top, statementIndex, instrument, loopCount, skipLivenessActions);
                        curBCI = targetBCI;
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
                            top += Bytecodes.stackEffectOf(TABLESWITCH);
                            nextStatementIndex = beforeJumpChecks(frame, curBCI, targetBCI, top, statementIndex, instrument, loopCount, skipLivenessActions);
                            curBCI = targetBCI;
                            continue loop;
                        }

                        // i could overflow if high == Integer.MAX_VALUE.
                        // This loops take that into account.
                        for (int i = low; i != high + 1; ++i) {
                            if (i == index) {
                                // Key found.
                                int targetBCI = switchHelper.targetAt(bs, curBCI, i - low);
                                top += Bytecodes.stackEffectOf(TABLESWITCH);
                                nextStatementIndex = beforeJumpChecks(frame, curBCI, targetBCI, top, statementIndex, instrument, loopCount, skipLivenessActions);
                                curBCI = targetBCI;
                                continue loop;
                            }
                        }

                        // Key not found.
                        int targetBCI = switchHelper.defaultTarget(bs, curBCI);
                        top += Bytecodes.stackEffectOf(TABLESWITCH);
                        nextStatementIndex = beforeJumpChecks(frame, curBCI, targetBCI, top, statementIndex, instrument, loopCount, skipLivenessActions);
                        curBCI = targetBCI;
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
                                top += Bytecodes.stackEffectOf(LOOKUPSWITCH);
                                nextStatementIndex = beforeJumpChecks(frame, curBCI, targetBCI, top, statementIndex, instrument, loopCount, skipLivenessActions);
                                curBCI = targetBCI;
                                continue loop;
                            }
                        }

                        // Key not found.
                        int targetBCI = switchHelper.defaultTarget(bs, curBCI);
                        top += Bytecodes.stackEffectOf(LOOKUPSWITCH);
                        nextStatementIndex = beforeJumpChecks(frame, curBCI, targetBCI, top, statementIndex, instrument, loopCount, skipLivenessActions);
                        curBCI = targetBCI;
                        continue loop;
                    }

                    case IRETURN: // fall through
                    case LRETURN: // fall through
                    case FRETURN: // fall through
                    case DRETURN: // fall through
                    case ARETURN: // fall through
                    case RETURN: {
                        if (CompilerDirectives.hasNextTier() && loopCount.value > 0) {
                            LoopNode.reportLoopCount(this, loopCount.value);
                        }
                        Object returnValue = getReturnValueAsObject(frame, top);
                        if (instrument != null) {
                            instrument.notifyReturn(frame, statementIndex, returnValue);
                        }
                        return returnValue;
                    }
                    // @formatter:off
                    // TODO(peterssen): Order shuffled.
                    case GETSTATIC : // fall through
                    case GETFIELD  : top += getField(frame, top,
                                     resolveField(curOpcode, /* Quickenable -> read from original code for thread safety */ readOriginalCPI(curBCI)),
                                     curBCI, curOpcode, statementIndex); break;
                    case PUTSTATIC : // fall through
                    case PUTFIELD  : top += putField(frame, top,
                                     resolveField(curOpcode, /* Quickenable -> read from original code for thread safety */ readOriginalCPI(curBCI)),
                                     curBCI, curOpcode, statementIndex); break;

                    case INVOKEVIRTUAL: // fall through
                    case INVOKESPECIAL: // fall through
                    case INVOKESTATIC:  // fall through
                    case INVOKEINTERFACE:
                        top += quickenInvoke(frame, top, curBCI, curOpcode, statementIndex); break;

                    case NEW         :
                        Klass klass = resolveType(NEW, bs.readCPI2(curBCI));
                        putObject(frame, top, newReferenceObject(klass)); break;
                    case NEWARRAY    :
                        byte jvmPrimitiveType = bs.readByte(curBCI);
                        int length = popInt(frame, top - 1);
                        putObject(frame, top - 1, newPrimitiveArray(jvmPrimitiveType, length)); break;
                    case ANEWARRAY   : putObject(frame, top - 1, newReferenceArray(resolveType(ANEWARRAY, bs.readCPI2(curBCI)), popInt(frame, top - 1))); break;

                    case ARRAYLENGTH : arrayLength(frame, top, curBCI); break;

                    case ATHROW      :
                        throw getMeta().throwException(nullCheck(popObject(frame, top - 1)));

                    case CHECKCAST   : {
                        StaticObject receiver = peekObject(frame, top - 1);
                        if (StaticObject.isNull(receiver) || receiver.getKlass() == resolveType(CHECKCAST, readOriginalCPI(curBCI))) {
                            // Most common case, avoid spawning a node.
                        } else {
                            CompilerDirectives.transferToInterpreterAndInvalidate();
                            quickenCheckCast(frame, top, curBCI, CHECKCAST);
                        }
                        break;
                    }
                    case INSTANCEOF  : {
                        StaticObject receiver = popObject(frame, top - 1);
                        if (StaticObject.isNull(receiver)) {
                            // Skip resolution.
                            putInt(frame, top - 1, /* false */ 0);
                        } else if (receiver.getKlass() == resolveType(INSTANCEOF, readOriginalCPI(curBCI))) {
                            // Quick-check, avoid spawning a node.
                            putInt(frame, top - 1, /* true */ 1);
                        } else {
                            CompilerDirectives.transferToInterpreterAndInvalidate();
                            putObject(frame, top - 1, receiver);
                            quickenInstanceOf(frame, top, curBCI, INSTANCEOF);
                        }
                        break;
                    }
                    case MONITORENTER: getRoot().monitorEnter(frame, nullCheck(popObject(frame, top - 1))); break;
                    case MONITOREXIT : getRoot().monitorExit(frame, nullCheck(popObject(frame, top - 1))); break;

                    case WIDE: {
                        int wideOpcode = bs.opcode(curBCI + 1);
                        switch (wideOpcode) {
                            case ILOAD: putInt(frame, top, getLocalInt(frame, bs.readLocalIndex2(curBCI))); break;
                            case LLOAD: putLong(frame, top, getLocalLong(frame, bs.readLocalIndex2(curBCI))); break;
                            case FLOAD: putFloat(frame, top, getLocalFloat(frame, bs.readLocalIndex2(curBCI))); break;
                            case DLOAD: putDouble(frame, top, getLocalDouble(frame, bs.readLocalIndex2(curBCI))); break;
                            case ALOAD: putObject(frame, top, getLocalObject(frame, bs.readLocalIndex2(curBCI))); break;

                            case ISTORE: setLocalInt(frame, bs.readLocalIndex2(curBCI), popInt(frame, top - 1)); break;
                            case LSTORE: setLocalLong(frame, bs.readLocalIndex2(curBCI), popLong(frame, top - 1)); break;
                            case FSTORE: setLocalFloat(frame, bs.readLocalIndex2(curBCI), popFloat(frame, top - 1)); break;
                            case DSTORE: setLocalDouble(frame, bs.readLocalIndex2(curBCI), popDouble(frame, top - 1)); break;
                            case ASTORE: setLocalObjectOrReturnAddress(frame, bs.readLocalIndex2(curBCI), popReturnAddressOrObject(frame, top - 1)); break;
                            case IINC: setLocalInt(frame, bs.readLocalIndex2(curBCI), getLocalInt(frame, bs.readLocalIndex2(curBCI)) + bs.readIncrement2(curBCI)); break;
                            // @formatter:on
                            case RET: {
                                // Use final local variables to pass in lambdas.
                                final int retOpBci = curBCI;
                                final int targetBCI = getLocalReturnAddress(frame, bs.readLocalIndex2(curBCI));
                                livenessAnalysis.performPostBCI(frame, curBCI, skipLivenessActions);

                                // Safely obtain the known targets mappings.
                                int[][] knownTargets = jsrBci;
                                if (knownTargets == null) {
                                    CompilerDirectives.transferToInterpreterAndInvalidate();
                                    atomic(() -> {
                                        // Double-checked locking.
                                        if (jsrBci == null) {
                                            jsrBci = new int[bs.endBCI()][];
                                        }
                                    });
                                    knownTargets = jsrBci;
                                }

                                // Safely obtain the known targets for the current ret operation.
                                int[] knownRets = VolatileArrayAccess.volatileRead(knownTargets, retOpBci);
                                if (knownRets == null) {
                                    CompilerDirectives.transferToInterpreterAndInvalidate();
                                    atomic(() -> {
                                        if (VolatileArrayAccess.volatileRead(jsrBci, retOpBci) != null) {
                                            return;
                                        }
                                        /*
                                         * Be very careful on updating the known target bcis, as if
                                         * another thread reads the not fully initialized array, it
                                         * may consider 0 to be a valid RET target, completely
                                         * breaking PE.
                                         */
                                        int[] targets = new int[]{targetBCI};
                                        // Also serves as a "final publication" barrier for the
                                        // assignment above.
                                        VolatileArrayAccess.volatileWrite(jsrBci, retOpBci, targets);
                                    });
                                    knownRets = VolatileArrayAccess.volatileRead(knownTargets, retOpBci);
                                }
                                assert knownRets != null;

                                // Lookup in the known targets to transform the return address to a
                                // constant.
                                for (int jsr : knownRets) {
                                    if (jsr == targetBCI) {
                                        CompilerAsserts.partialEvaluationConstant(jsr);
                                        top += Bytecodes.stackEffectOf(RET);
                                        nextStatementIndex = beforeJumpChecks(frame, curBCI, jsr, top, statementIndex, instrument, loopCount, skipLivenessActions);
                                        curBCI = jsr;
                                        continue loop;
                                    }
                                }

                                // Lookup failed: Add the current target to the known targets.
                                CompilerDirectives.transferToInterpreterAndInvalidate();
                                atomic(() -> {
                                    int[] currentRets = VolatileArrayAccess.volatileRead(jsrBci, retOpBci);
                                    for (int jsr : currentRets) {
                                        if (jsr == targetBCI) {
                                            // target has been added by another thread.
                                            return;
                                        }
                                    }
                                    int[] updatedTargets = Arrays.copyOf(currentRets, currentRets.length + 1);
                                    /*
                                     * Be very careful on updating the known target bcis, as if
                                     * another thread reads the not fully initialized array, it may
                                     * consider 0 to be a valid RET target, completely breaking PE.
                                     */
                                    updatedTargets[updatedTargets.length - 1] = targetBCI;
                                    // Also serves as a "final publication" barrier for the
                                    // assignment above.
                                    VolatileArrayAccess.volatileWrite(jsrBci, retOpBci, updatedTargets);
                                });
                                top += Bytecodes.stackEffectOf(RET);
                                nextStatementIndex = beforeJumpChecks(frame, retOpBci, targetBCI, top, statementIndex, instrument, loopCount, skipLivenessActions);
                                curBCI = targetBCI;
                                continue loop;
                            }
                            default:
                                CompilerDirectives.transferToInterpreterAndInvalidate();
                                throw EspressoError.shouldNotReachHere(Bytecodes.nameOf(curOpcode));
                        }
                        livenessAnalysis.performPostBCI(frame, curBCI, skipLivenessActions);
                        int targetBCI = bs.nextBCI(curBCI);
                        livenessAnalysis.performOnEdge(frame, curBCI, targetBCI, skipLivenessActions);
                        top += Bytecodes.stackEffectOf(wideOpcode);
                        curBCI = targetBCI;
                        continue loop;
                    }

                    case MULTIANEWARRAY:
                        top += allocateMultiArray(frame, top, resolveType(MULTIANEWARRAY, bs.readCPI2(curBCI)), bs.readUByte(curBCI + 3));
                        break;

                    case BREAKPOINT:
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        throw EspressoError.unimplemented(Bytecodes.nameOf(curOpcode) + " not supported.");

                    case INVOKEDYNAMIC:
                        top += quickenInvokeDynamic(frame, top, curBCI, INVOKEDYNAMIC);
                        break;

                    case QUICK: {
                        // Force a volatile read of the opcode.
                        if (bs.currentVolatileBC(curBCI) != QUICK) {
                            // Possible case of read reordering. Retry handling the bytecode to make
                            // sure we get a correct CPI.
                            CompilerDirectives.transferToInterpreterAndInvalidate();
                            continue loop;
                        }
                        BaseQuickNode quickNode = nodes[bs.readCPI2(curBCI)];
                        if (quickNode.removedByRedefinition()) {
                            CompilerDirectives.transferToInterpreterAndInvalidate();
                            quickNode = getBaseQuickNode(curBCI, top, statementIndex, quickNode);
                        }
                        top += quickNode.execute(frame);
                        break;
                    }
                    case SLIM_QUICK:
                        top += sparseNodes[curBCI].execute(frame);
                        break;

                    default:
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        throw EspressoError.shouldNotReachHere(Bytecodes.nameOf(curOpcode));
                }
            } catch (AbstractTruffleException | StackOverflowError | OutOfMemoryError e) {
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
                                clearOperandStack(frame, top);
                                top = startingStackOffset(getMethodVersion().getMaxLocals());
                                putObject(frame, top, wrappedStackOverflowError.getGuestException());
                                top++;
                                int targetBCI = stackOverflowErrorInfo[i + 2];
                                nextStatementIndex = beforeJumpChecks(frame, curBCI, targetBCI, top, statementIndex, instrument, loopCount, skipLivenessActions);
                                curBCI = targetBCI;
                                continue loop; // skip bs.next()
                            }
                        }
                    }
                    if (instrument != null) {
                        instrument.notifyExceptionAt(frame, wrappedStackOverflowError, statementIndex);
                    }
                    if (CompilerDirectives.hasNextTier() && loopCount.value > 0) {
                        LoopNode.reportLoopCount(this, loopCount.value);
                    }
                    throw wrappedStackOverflowError;

                } else /* EspressoException or AbstractTruffleException or OutOfMemoryError */ {
                    EspressoException wrappedException;
                    if (e instanceof EspressoException) {
                        wrappedException = (EspressoException) e;
                    } else if (e instanceof AbstractTruffleException) {
                        if (e instanceof EspressoExitException) {
                            CompilerDirectives.transferToInterpreter();
                            getRoot().abortMonitor(frame);
                            // Tearing down the VM, no need to report loop count.
                            throw e;
                        }
                        assert getContext().getEspressoEnv().Polyglot;
                        getMeta().polyglot.ForeignException.safeInitialize(); // should fold
                        wrappedException = EspressoException.wrap(
                                        getAllocator().createForeignException(getContext(), e, InteropLibrary.getUncached(e)), getMeta());
                    } else {
                        assert e instanceof OutOfMemoryError;
                        CompilerDirectives.transferToInterpreter();
                        wrappedException = getContext().getOutOfMemory();
                    }

                    ExceptionHandler[] handlers = getMethodVersion().getExceptionHandlers();
                    ExceptionHandler handler = null;
                    for (ExceptionHandler toCheck : handlers) {
                        CompilerAsserts.partialEvaluationConstant(toCheck);
                        if (curBCI >= toCheck.getStartBCI() && curBCI < toCheck.getEndBCI()) {
                            Klass catchType = null;
                            if (!toCheck.isCatchAll()) {
                                // exception handlers are similar to instanceof bytecodes, so we
                                // pass instanceof
                                catchType = resolveType(Bytecodes.INSTANCEOF, (char) toCheck.catchTypeCPI());
                            }
                            CompilerAsserts.partialEvaluationConstant(catchType);
                            if (catchType == null || InterpreterToVM.instanceOf(wrappedException.getGuestException(), catchType)) {
                                // the first found exception handler is our exception handler
                                handler = toCheck;
                                break;
                            }
                        }
                    }
                    if (handler != null) {
                        clearOperandStack(frame, top);
                        top = startingStackOffset(getMethodVersion().getMaxLocals());
                        checkNoForeignObjectAssumption(wrappedException.getGuestException());
                        putObject(frame, top, wrappedException.getGuestException());
                        top++;
                        int targetBCI = handler.getHandlerBCI();
                        nextStatementIndex = beforeJumpChecks(frame, curBCI, targetBCI, top, statementIndex, instrument, loopCount, skipLivenessActions);
                        curBCI = targetBCI;
                        continue loop; // skip bs.next()
                    } else {
                        if (instrument != null) {
                            instrument.notifyExceptionAt(frame, wrappedException, statementIndex);
                        }
                        if (CompilerDirectives.hasNextTier() && loopCount.value > 0) {
                            LoopNode.reportLoopCount(this, loopCount.value);
                        }
                        throw e;
                    }
                }
            } catch (EspressoOSRReturnException e) {
                if (CompilerDirectives.hasNextTier() && loopCount.value > 0) {
                    LoopNode.reportLoopCount(this, loopCount.value);
                }
                return e.getResultOrRethrow();
            }
            assert curOpcode != WIDE && curOpcode != LOOKUPSWITCH && curOpcode != TABLESWITCH;

            int targetBCI = curBCI + Bytecodes.lengthOf(curOpcode);
            livenessAnalysis.performOnEdge(frame, curBCI, targetBCI, skipLivenessActions);
            if (instrument != null) {
                nextStatementIndex = instrument.getNextStatementIndex(statementIndex, targetBCI);
            }
            top += Bytecodes.stackEffectOf(curOpcode);
            curBCI = targetBCI;
        }
    }

    @Override
    public void enterNewReference() {
        enterImplicitExceptionProfile();
    }

    @Override
    public void enterNewArray() {
        enterImplicitExceptionProfile();
    }

    @Override
    public void enterNewMultiArray() {
        enterImplicitExceptionProfile();
    }

    private StaticObject newReferenceObject(Klass klass) {
        assert !klass.isPrimitive() : "Verifier guarantee";
        GuestAllocator.AllocationChecks.checkCanAllocateNewReference(getMeta(), klass, true, this);
        return getAllocator().createNew((ObjectKlass) klass);
    }

    private StaticObject newPrimitiveArray(byte jvmPrimitiveType, int length) {
        GuestAllocator.AllocationChecks.checkCanAllocateArray(getMeta(), length, this);
        return getAllocator().createNewPrimitiveArray(getMeta(), jvmPrimitiveType, length);
    }

    private StaticObject newReferenceArray(Klass componentType, int length) {
        GuestAllocator.AllocationChecks.checkCanAllocateArray(getMeta(), length, this);
        return getAllocator().createNewReferenceArray(componentType, length);
    }

    private BaseQuickNode getBaseQuickNode(int curBCI, int top, int statementIndex, BaseQuickNode quickNode) {
        // block while class redefinition is ongoing
        quickNode.getContext().getClassRedefinition().check();
        BaseQuickNode result = quickNode;
        result = atomic(() -> {
            // re-check if node was already replaced by another thread
            if (quickNode != nodes[readCPI(curBCI)]) {
                // another thread beat us
                return nodes[readCPI(curBCI)];
            } else {
                // other threads might still have beat us but if
                // so, the resolution failed and so will we below
                BytecodeStream original = new BytecodeStream(getMethodVersion().getCodeAttribute().getOriginalCode());
                char cpi = original.readCPI(curBCI);
                int nodeOpcode = original.currentBC(curBCI);
                Method resolutionSeed = resolveMethodNoCache(nodeOpcode, cpi);
                BaseQuickNode toInsert = insert(dispatchQuickened(top, curBCI, cpi, nodeOpcode, statementIndex, resolutionSeed, getContext().getEspressoEnv().bytecodeLevelInlining));
                nodes[readCPI(curBCI)] = toInsert;
                return toInsert;
            }
        });
        return result;
    }

    private Object getReturnValueAsObject(VirtualFrame frame, int top) {
        Symbol<Type> returnType = Signatures.returnType(getMethod().getParsedSignature());
        // @formatter:off
        switch (returnType.byteAt(0)) {
            case 'Z' : return stackIntToBoolean(popInt(frame, top - 1));
            case 'B' : return (byte) popInt(frame, top - 1);
            case 'S' : return (short) popInt(frame, top - 1);
            case 'C' : return (char) popInt(frame, top - 1);
            case 'I' : return popInt(frame, top - 1);
            case 'J' : return popLong(frame, top - 1);
            case 'F' : return popFloat(frame, top - 1);
            case 'D' : return popDouble(frame, top - 1);
            case 'V' : return StaticObject.NULL; // void
            case '[' : // fall through
            case 'L' : return popObject(frame, top - 1);
            default:
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw EspressoError.shouldNotReachHere();
        }
        // @formatter:on
    }

    @ExplodeLoop
    private void clearOperandStack(VirtualFrame frame, int top) {
        int stackStart = startingStackOffset(getMethodVersion().getMaxLocals());
        for (int slot = top - 1; slot >= stackStart; --slot) {
            clear(frame, slot);
        }
    }

    @Override
    MethodVersion getMethodVersion() {
        return methodVersion;
    }

    private ObjectKlass getDeclaringKlass() {
        return methodVersion.getDeclaringKlass();
    }

    private EspressoRootNode getRoot() {
        if (rootNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            rootNode = (EspressoRootNode) getRootNode();
        }
        return rootNode;
    }

    @Override
    public int getBci(Frame frame) {
        return getBCI(frame);
    }

    @Override
    public InstrumentableNode materializeInstrumentableNodes(Set<Class<? extends Tag>> materializedTags) {
        InstrumentationSupport info = this.instrumentation;
        if (info == null && materializedTags.contains(StatementTag.class)) {
            Lock lock = getLock();
            lock.lock();
            try {
                info = this.instrumentation;
                // double checked locking
                if (info == null) {
                    generifyBytecodeLevelInlining();
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

    private static boolean takeBranchRef1(StaticObject operand, int opcode) {
        assert IFNULL <= opcode && opcode <= IFNONNULL;
        // @formatter:off
        switch (opcode) {
            case IFNULL    : return StaticObject.isNull(operand);
            case IFNONNULL : return StaticObject.notNull(operand);
            default        :
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw EspressoError.shouldNotReachHere("expected IFNULL or IFNONNULL bytecode");
        }
        // @formatter:on
    }

    private static boolean takeBranchPrimitive1(int operand, int opcode) {
        assert IFEQ <= opcode && opcode <= IFLE;
        // @formatter:off
        switch (opcode) {
            case IFEQ      : return operand == 0;
            case IFNE      : return operand != 0;
            case IFLT      : return operand  < 0;
            case IFGE      : return operand >= 0;
            case IFGT      : return operand  > 0;
            case IFLE      : return operand <= 0;
            default        :
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw EspressoError.shouldNotReachHere("expecting IFEQ,IFNE,IFLT,IFGE,IFGT,IFLE");
        }
        // @formatter:on
    }

    private static boolean takeBranchPrimitive2(int operand1, int operand2, int opcode) {
        assert IF_ICMPEQ <= opcode && opcode <= IF_ICMPLE;
        // @formatter:off
        switch (opcode) {
            case IF_ICMPEQ : return operand1 == operand2;
            case IF_ICMPNE : return operand1 != operand2;
            case IF_ICMPLT : return operand1  > operand2;
            case IF_ICMPGE : return operand1 <= operand2;
            case IF_ICMPGT : return operand1  < operand2;
            case IF_ICMPLE : return operand1 >= operand2;
            default        :
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw EspressoError.shouldNotReachHere("expecting IF_ICMPEQ,IF_ICMPNE,IF_ICMPLT,IF_ICMPGE,IF_ICMPGT,IF_ICMPLE");
        }
        // @formatter:on
    }

    private boolean takeBranchRef2(StaticObject operand1, StaticObject operand2, int opcode) {
        assert IF_ACMPEQ <= opcode && opcode <= IF_ACMPNE;
        // @formatter:off
        if (noForeignObjects.isValid()) {
            switch (opcode) {
                case IF_ACMPEQ:
                    return operand1 == operand2;
                case IF_ACMPNE:
                    return operand1 != operand2;
                default:
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw EspressoError.shouldNotReachHere("expecting IF_ACMPEQ,IF_ACMPNE");
            }
        } else {
            boolean equal = InterpreterToVM.referenceIdentityEqual(operand1, operand2, getLanguage());
            switch (opcode) {
                case IF_ACMPEQ: {
                    return equal;
                }
                case IF_ACMPNE: {
                    return !equal;
                }
                default:
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw EspressoError.shouldNotReachHere("expecting IF_ACMPEQ,IF_ACMPNE");
            }
        }
        // @formatter:on
    }

    private void arrayLength(VirtualFrame frame, int top, int curBCI) {
        StaticObject array = nullCheck(popObject(frame, top - 1));
        if (noForeignObjects.isValid() || array.isEspressoObject()) {
            putInt(frame, top - 1, InterpreterToVM.arrayLength(array, getLanguage()));
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
        CompilerAsserts.partialEvaluationConstant(loadOpcode);
        int index = popInt(frame, top - 1);
        StaticObject array = nullCheck(popObject(frame, top - 2));
        if (noForeignObjects.isValid() || array.isEspressoObject()) {
            EspressoLanguage language = getLanguage();
            // @formatter:off
            switch (loadOpcode) {
                case BALOAD: putInt(frame, top - 2, getInterpreterToVM().getArrayByte(language, index, array, this));      break;
                case SALOAD: putInt(frame, top - 2, getInterpreterToVM().getArrayShort(language, index, array, this));     break;
                case CALOAD: putInt(frame, top - 2, getInterpreterToVM().getArrayChar(language, index, array, this));      break;
                case IALOAD: putInt(frame, top - 2, getInterpreterToVM().getArrayInt(language, index, array, this));       break;
                case FALOAD: putFloat(frame, top - 2, getInterpreterToVM().getArrayFloat(language, index, array, this));   break;
                case LALOAD: putLong(frame, top - 2, getInterpreterToVM().getArrayLong(language, index, array, this));     break;
                case DALOAD: putDouble(frame, top - 2, getInterpreterToVM().getArrayDouble(language, index, array, this)); break;
                case AALOAD: putObject(frame, top - 2, getInterpreterToVM().getArrayObject(language, index, array, this));       break;
                default:
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw EspressoError.shouldNotReachHere();
            }
            // @formatter:on
        } else {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            // The array was released, it must be restored for the quickening.
            putInt(frame, top - 1, index);
            putObject(frame, top - 2, array);
            // The stack effect difference vs. original bytecode is always 0.
            quickenArrayLoad(frame, top, curBCI, loadOpcode);
        }
    }

    private void arrayStore(VirtualFrame frame, int top, int curBCI, int storeOpcode) {
        assert IASTORE <= storeOpcode && storeOpcode <= SASTORE;
        CompilerAsserts.partialEvaluationConstant(storeOpcode);
        int offset = (storeOpcode == LASTORE || storeOpcode == DASTORE) ? 2 : 1;
        int index = popInt(frame, top - 1 - offset);
        StaticObject array = nullCheck(popObject(frame, top - 2 - offset));
        if (noForeignObjects.isValid() || array.isEspressoObject()) {
            EspressoLanguage language = getLanguage();
            // @formatter:off
            switch (storeOpcode) {
                case BASTORE: getInterpreterToVM().setArrayByte(language, (byte) popInt(frame, top - 1), index, array, this);   break;
                case SASTORE: getInterpreterToVM().setArrayShort(language, (short) popInt(frame, top - 1), index, array, this); break;
                case CASTORE: getInterpreterToVM().setArrayChar(language, (char) popInt(frame, top - 1), index, array, this);   break;
                case IASTORE: getInterpreterToVM().setArrayInt(language, popInt(frame, top - 1), index, array, this);           break;
                case FASTORE: getInterpreterToVM().setArrayFloat(language, popFloat(frame, top - 1), index, array, this);       break;
                case LASTORE: getInterpreterToVM().setArrayLong(language, popLong(frame, top - 1), index, array, this);         break;
                case DASTORE: getInterpreterToVM().setArrayDouble(language, popDouble(frame, top - 1), index, array, this);     break;
                case AASTORE: referenceArrayStore(frame, top, index, array);     break;
                default:
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw EspressoError.shouldNotReachHere();
            }
            // @formatter:on
        } else {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            // The array was released, it must be restored for the quickening.
            putInt(frame, top - 1 - offset, index);
            putObject(frame, top - 2 - offset, array);
            // The stack effect difference vs. original bytecode is always 0.
            quickenArrayStore(frame, top, curBCI, storeOpcode);
        }
    }

    private void referenceArrayStore(VirtualFrame frame, int top, int index, StaticObject array) {
        if (refArrayStoreNode == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            atomic(() -> {
                if (refArrayStoreNode == null) {
                    refArrayStoreNode = insert(new EspressoReferenceArrayStoreNode());
                }
            });
        }
        refArrayStoreNode.arrayStore(getLanguage(), getContext().getMeta(), popObject(frame, top - 1), index, array);
    }

    private int beforeJumpChecks(VirtualFrame frame, int curBCI, int targetBCI, int top, int statementIndex, InstrumentationSupport instrument, Counter loopCount, boolean skipLivenessActions) {
        CompilerAsserts.partialEvaluationConstant(targetBCI);
        int nextStatementIndex = (instrument == null) ? 0 : instrument.getStatementIndexAfterJump(statementIndex, curBCI, targetBCI);
        if (targetBCI <= curBCI) {
            TruffleSafepoint.poll(this);
            if (CompilerDirectives.hasNextTier() && ++loopCount.value >= REPORT_LOOP_STRIDE) {
                LoopNode.reportLoopCount(this, REPORT_LOOP_STRIDE);
                loopCount.value = 0;
            }
            if (CompilerDirectives.inInterpreter() && BytecodeOSRNode.pollOSRBackEdge(this)) {
                Runnable beforeTransfer;
                if (instrument != null && statementIndex != nextStatementIndex) {
                    // Since multiple code locations can jump back to the same bci, we can't
                    // compile a "statement exit" event inside the OSR code (we don't know
                    // which statement we came from). Instead, we notify statement exit and entry
                    // before calling OSR code, and skip the first notify event inside OSR code.
                    beforeTransfer = () -> {
                        instrument.notifyStatement(frame, statementIndex, nextStatementIndex);
                    };
                } else {
                    // If there's no instrumentation, or we're jumping within the same statement,
                    // don't send an instrumentation event.
                    beforeTransfer = null;
                }
                livenessAnalysis.catchUpOSR(frame, targetBCI, skipLivenessActions);
                Object osrResult;
                try {
                    osrResult = BytecodeOSRNode.tryOSR(this, targetBCI, new EspressoOSRInterpreterState(top, nextStatementIndex), beforeTransfer, frame);
                } catch (Throwable any) {
                    // Has already been guest-handled in OSR. Shortcut out of the method.
                    throw new EspressoOSRReturnException(any);
                }
                if (osrResult != null) {
                    throw new EspressoOSRReturnException(osrResult);
                }
            }
        }
        livenessAnalysis.performOnEdge(frame, curBCI, targetBCI, skipLivenessActions);
        return nextStatementIndex;
    }

    @ExplodeLoop
    @SuppressWarnings("unused")
    private ExceptionHandler resolveExceptionHandlers(int bci, StaticObject ex) {
        CompilerAsserts.partialEvaluationConstant(bci);
        ExceptionHandler[] handlers = getMethodVersion().getExceptionHandlers();
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

    private void putPoolConstant(VirtualFrame frame, int top, char cpi, int opcode) {
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
            Klass klass = pool.resolvedKlassAt(getDeclaringKlass(), cpi);
            putObject(frame, top, klass.mirror());
        } else if (constant instanceof MethodHandleConstant) {
            assert opcode == LDC || opcode == LDC_W;
            StaticObject methodHandle = pool.resolvedMethodHandleAt(getDeclaringKlass(), cpi);
            putObject(frame, top, methodHandle);
        } else if (constant instanceof MethodTypeConstant) {
            assert opcode == LDC || opcode == LDC_W;
            StaticObject methodType = pool.resolvedMethodTypeAt(getDeclaringKlass(), cpi);
            putObject(frame, top, methodType);
        } else if (constant instanceof DynamicConstant) {
            DynamicConstant.Resolved dynamicConstant = pool.resolvedDynamicConstantAt(getDeclaringKlass(), cpi);
            dynamicConstant.putResolved(frame, top, this);
        } else {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw EspressoError.unimplemented(constant.toString());
        }
    }

    protected RuntimeConstantPool getConstantPool() {
        return getMethodVersion().getPool();
    }

    @TruffleBoundary
    private BootstrapMethodsAttribute getBootstrapMethods() {
        return (BootstrapMethodsAttribute) (getDeclaringKlass()).getAttribute(BootstrapMethodsAttribute.NAME);
    }

    // region Bytecode quickening

    private char readCPI(int curBCI) {
        assert (!Bytecodes.isQuickenable(bs.currentBC(curBCI)) ||
                        lockIsHeld()) : "Reading the CPI for a quickenable bytecode must be done under the BytecodeNode lock. Please obtain the lock, or use readOriginalCPI.";
        return bs.readCPI(curBCI);
    }

    private char readOriginalCPI(int curBCI) {
        return BytecodeStream.readCPI(getMethodVersion().getOriginalCode(), curBCI);
    }

    private char addQuickNode(BaseQuickNode node) {
        CompilerAsserts.neverPartOfCompilation();
        Objects.requireNonNull(node);
        nodes = Arrays.copyOf(nodes, nodes.length + 1);
        int nodeIndex = nodes.length - 1; // latest empty slot
        nodes[nodeIndex] = insert(node);
        return (char) nodeIndex;
    }

    private void addSlimQuickNode(BaseQuickNode node, int curBCI) {
        CompilerAsserts.neverPartOfCompilation();
        Objects.requireNonNull(node);
        if (sparseNodes == QuickNode.EMPTY_ARRAY) {
            sparseNodes = new QuickNode[getMethodVersion().getCode().length];
        }
        sparseNodes[curBCI] = insert(node);
    }

    private void patchBci(int bci, byte opcode, char nodeIndex) {
        CompilerAsserts.neverPartOfCompilation();
        assert Bytecodes.isQuickened(opcode);
        byte[] code = getMethodVersion().getCode();

        int oldBC = code[bci];
        if (opcode == (byte) QUICK) {
            code[bci + 1] = (byte) ((nodeIndex >> 8) & 0xFF);
            code[bci + 2] = (byte) ((nodeIndex) & 0xFF);
        }
        // NOP-padding.
        for (int i = Bytecodes.lengthOf(opcode); i < Bytecodes.lengthOf(oldBC); ++i) {
            code[bci + i] = (byte) NOP;
        }
        // Make sure the Quickened bytecode is written after the rest, as it is used for
        // synchronization.
        VolatileArrayAccess.volatileWrite(code, bci, opcode);
    }

    private BaseQuickNode injectQuick(int curBCI, BaseQuickNode quick, int opcode) {
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

    private BaseQuickNode tryPatchQuick(int curBCI, Supplier<BaseQuickNode> newQuickNode) {
        return atomic(() -> {
            if (bs.currentVolatileBC(curBCI) == QUICK) {
                return nodes[readCPI(curBCI)];
            } else {
                return injectQuick(curBCI, newQuickNode.get(), QUICK);
            }
        });
    }

    private int quickenCheckCast(VirtualFrame frame, int top, int curBCI, int opcode) {
        CompilerAsserts.neverPartOfCompilation();
        assert opcode == CHECKCAST;
        BaseQuickNode quick = tryPatchQuick(curBCI, () -> new CheckCastQuickNode(resolveType(CHECKCAST, readCPI(curBCI)), top, curBCI));
        quick.execute(frame);
        assert Bytecodes.stackEffectOf(opcode) == 0;
        return 0; // Bytecodes.stackEffectOf(opcode);
    }

    private int quickenInstanceOf(VirtualFrame frame, int top, int curBCI, int opcode) {
        CompilerAsserts.neverPartOfCompilation();
        assert opcode == INSTANCEOF;
        BaseQuickNode quick = tryPatchQuick(curBCI, () -> new InstanceOfQuickNode(resolveType(INSTANCEOF, readCPI(curBCI)), top, curBCI));
        quick.execute(frame);
        assert Bytecodes.stackEffectOf(opcode) == 0;
        return 0; // Bytecodes.stackEffectOf(opcode);
    }

    @SuppressWarnings("try")
    private int quickenInvoke(VirtualFrame frame, int top, int curBCI, int opcode, int statementIndex) {
        QUICKENED_INVOKES.inc();
        CompilerDirectives.transferToInterpreterAndInvalidate();
        assert Bytecodes.isInvoke(opcode);
        BaseQuickNode quick = tryPatchQuick(curBCI, () -> {
            // During resolution of the symbolic reference to the method, any of the exceptions
            // pertaining to method resolution (&sect;5.4.3.3) can be thrown.
            char cpi = readCPI(curBCI);
            Method resolutionSeed = resolveMethod(opcode, cpi);
            return dispatchQuickened(top, curBCI, cpi, opcode, statementIndex, resolutionSeed, getContext().getEspressoEnv().bytecodeLevelInlining);
        });
        if (opcode == INVOKESTATIC && quick instanceof InvokeStaticQuickNode invokeStaticQuickNode) {
            try (EspressoLanguage.DisableSingleStepping ignored = getLanguage().disableStepping()) {
                invokeStaticQuickNode.initializeResolvedKlass();
            }
        }
        // Perform the call outside of the lock.
        return quick.execute(frame) - Bytecodes.stackEffectOf(opcode);
    }

    /**
     * Revert speculative quickening e.g. revert inlined fields accessors to a normal invoke.
     * INVOKEVIRTUAL -> QUICK (InlinedGetter/SetterNode) -> QUICK (InvokeVirtualNode)
     */
    public int reQuickenInvoke(VirtualFrame frame, int top, int opcode, int curBCI, int statementIndex, Method resolutionSeed) {
        CompilerAsserts.neverPartOfCompilation();
        assert Bytecodes.isInvoke(opcode);
        BaseQuickNode invoke = generifyInlinedMethodNode(top, opcode, curBCI, statementIndex, resolutionSeed);
        // Perform the call outside of the lock.
        return invoke.execute(frame);
    }

    /**
     * Atomically replaces a quick node with another one.
     */
    public int replaceQuickAt(VirtualFrame frame, int opcode, int curBCI, BaseQuickNode old, BaseQuickNode replacement) {
        CompilerAsserts.neverPartOfCompilation();
        assert Bytecodes.isInvoke(opcode);
        BaseQuickNode invoke = atomic(() -> {
            assert bs.currentBC(curBCI) == QUICK;
            char nodeIndex = readCPI(curBCI);
            BaseQuickNode currentQuick = nodes[nodeIndex];
            if (currentQuick != old) {
                // Another thread might have already replaced our node at this point.
                return currentQuick;
            }
            nodes[nodeIndex] = currentQuick.replace(replacement);
            return replacement;
        });
        // Perform the call outside of the lock.
        return invoke.execute(frame);
    }

    /**
     * Reverts Bytecode-level method inlining at the current bci, in case instrumentation starts
     * happening on this node.
     */
    public BaseQuickNode generifyInlinedMethodNode(int top, int opcode, int curBCI, int statementIndex, Method resolutionSeed) {
        CompilerAsserts.neverPartOfCompilation();
        return atomic(() -> {
            assert bs.currentBC(curBCI) == QUICK;
            char nodeIndex = readCPI(curBCI);
            BaseQuickNode currentQuick = nodes[nodeIndex];
            if (!(currentQuick instanceof InlinedMethodNode)) {
                // Another thread might have already generify-ed our node at this point.
                // Might be racy, as read is not volatile, but redoing the work should be OK.
                return currentQuick;
            }
            BaseQuickNode invoke = dispatchQuickened(top, curBCI, readOriginalCPI(curBCI), opcode, statementIndex, resolutionSeed, false);
            nodes[nodeIndex] = currentQuick.replace(invoke);
            return invoke;
        });
    }

    /**
     * Reverts all bytecode-level inlining to a generic invoke quick node.
     */
    private void generifyBytecodeLevelInlining() {
        atomic(() -> {
            for (BaseQuickNode quick : nodes) {
                if (quick instanceof InlinedMethodNode) {
                    notifyInserted(((InlinedMethodNode) quick).revertToGeneric(this));
                }
            }
        });
    }

    // region quickenForeign
    public int quickenGetField(final VirtualFrame frame, int top, int curBCI, int opcode, int statementIndex, Field field) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        assert opcode == GETFIELD;
        BaseQuickNode getField = tryPatchQuick(curBCI, () -> new QuickenedGetFieldNode(top, curBCI, statementIndex, field));
        return getField.execute(frame) - Bytecodes.stackEffectOf(opcode);
    }

    public int quickenPutField(VirtualFrame frame, int top, int curBCI, int opcode, int statementIndex, Field field) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        assert opcode == PUTFIELD;
        BaseQuickNode putField = tryPatchQuick(curBCI, () -> new QuickenedPutFieldNode(top, curBCI, field, statementIndex));
        return putField.execute(frame) - Bytecodes.stackEffectOf(opcode);
    }

    private int quickenArrayLength(VirtualFrame frame, int top, int curBCI) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        BaseQuickNode arrayLengthNode = atomic(() -> {
            if (bs.currentVolatileBC(curBCI) == SLIM_QUICK) {
                return sparseNodes[curBCI];
            } else {
                return injectQuick(curBCI, new ArrayLengthQuickNode(top, curBCI), SLIM_QUICK);
            }
        });
        return arrayLengthNode.execute(frame) - Bytecodes.stackEffectOf(ARRAYLENGTH);
    }

    private int quickenArrayLoad(VirtualFrame frame, int top, int curBCI, int loadOpcode) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        assert IALOAD <= loadOpcode && loadOpcode <= SALOAD;
        BaseQuickNode arrayLoadNode = atomic(() -> {
            if (bs.currentVolatileBC(curBCI) == SLIM_QUICK) {
                return sparseNodes[curBCI];
            } else {
                // @formatter:off
                BaseQuickNode quickNode;
                switch (loadOpcode)  {
                    case BALOAD: quickNode = new ByteArrayLoadQuickNode(top, curBCI);   break;
                    case SALOAD: quickNode = new ShortArrayLoadQuickNode(top, curBCI);  break;
                    case CALOAD: quickNode = new CharArrayLoadQuickNode(top, curBCI);   break;
                    case IALOAD: quickNode = new IntArrayLoadQuickNode(top, curBCI);    break;
                    case FALOAD: quickNode = new FloatArrayLoadQuickNode(top, curBCI);  break;
                    case LALOAD: quickNode = new LongArrayLoadQuickNode(top, curBCI);   break;
                    case DALOAD: quickNode = new DoubleArrayLoadQuickNode(top, curBCI); break;
                    case AALOAD: quickNode = new ReferenceArrayLoadQuickNode(top, curBCI); break;
                    default:
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        throw EspressoError.shouldNotReachHere("unexpected kind");
                }
                // @formatter:on
                return injectQuick(curBCI, quickNode, SLIM_QUICK);
            }
        });
        return arrayLoadNode.execute(frame) - Bytecodes.stackEffectOf(loadOpcode);
    }

    private int quickenArrayStore(final VirtualFrame frame, int top, int curBCI, int storeOpcode) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        assert IASTORE <= storeOpcode && storeOpcode <= SASTORE;
        BaseQuickNode arrayStoreNode = atomic(() -> {
            if (bs.currentVolatileBC(curBCI) == SLIM_QUICK) {
                return sparseNodes[curBCI];
            } else {
                BaseQuickNode quickNode;
                // @formatter:off
                switch (storeOpcode)  {
                    case BASTORE: quickNode = new ByteArrayStoreQuickNode(top, curBCI);   break;
                    case SASTORE: quickNode = new ShortArrayStoreQuickNode(top, curBCI);  break;
                    case CASTORE: quickNode = new CharArrayStoreQuickNode(top, curBCI);   break;
                    case IASTORE: quickNode = new IntArrayStoreQuickNode(top, curBCI);    break;
                    case FASTORE: quickNode = new FloatArrayStoreQuickNode(top, curBCI);  break;
                    case LASTORE: quickNode = new LongArrayStoreQuickNode(top, curBCI);   break;
                    case DASTORE: quickNode = new DoubleArrayStoreQuickNode(top, curBCI); break;
                    case AASTORE: quickNode = new ReferenceArrayStoreQuickNode(top, curBCI); break;
                    default:
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        throw EspressoError.shouldNotReachHere("unexpected kind");
                }
                // @formatter:on
                return injectQuick(curBCI, quickNode, SLIM_QUICK);
            }
        });
        return arrayStoreNode.execute(frame) - Bytecodes.stackEffectOf(storeOpcode);
    }

    // endregion quickenForeign

    private BaseQuickNode dispatchQuickened(int top, int curBCI, char cpi, int opcode, int statementIndex, Method resolutionSeed, boolean allowBytecodeInlining) {
        InvokeQuickNode invoke;
        Method resolved = resolutionSeed;
        int resolvedOpCode = opcode;
        switch (opcode) {
            case INVOKESTATIC:
                // Otherwise, if the resolved method is an instance method, the invokestatic
                // instruction throws an IncompatibleClassChangeError.
                if (!resolved.isStatic()) {
                    enterLinkageExceptionProfile();
                    throw throwBoundary(getMeta().java_lang_IncompatibleClassChangeError);
                }
                break;
            case INVOKEINTERFACE:
                // Otherwise, if the resolved method is static or (jdk8 or earlier) private, the
                // invokeinterface instruction throws an IncompatibleClassChangeError.
                if (resolved.isStatic() ||
                                (getContext().getJavaVersion().java8OrEarlier() && resolved.isPrivate())) {
                    enterLinkageExceptionProfile();
                    throw throwBoundary(getMeta().java_lang_IncompatibleClassChangeError);
                }
                if (resolved.getITableIndex() < 0) {
                    if (resolved.isPrivate()) {
                        assert getJavaVersion().java9OrLater();
                        // Interface private methods do not appear in itables.
                        resolvedOpCode = INVOKESPECIAL;
                    } else {
                        // Can happen in old classfiles that calls j.l.Object on interfaces.
                        resolvedOpCode = INVOKEVIRTUAL;
                    }
                }
                break;
            case INVOKEVIRTUAL:
                // Otherwise, if the resolved method is a class (static) method, the invokevirtual
                // instruction throws an IncompatibleClassChangeError.
                if (resolved.isStatic()) {
                    enterLinkageExceptionProfile();
                    throw throwBoundary(getMeta().java_lang_IncompatibleClassChangeError);
                }
                if (resolved.isFinalFlagSet() || resolved.getDeclaringKlass().isFinalFlagSet() || resolved.isPrivate()) {
                    resolvedOpCode = INVOKESPECIAL;
                }
                break;
            case INVOKESPECIAL:
                // Otherwise, if the resolved method is an instance initialization method, and the
                // class in which it is declared is not the class symbolically referenced by the
                // instruction, a NoSuchMethodError is thrown.
                if (resolved.isConstructor()) {
                    if (resolved.getDeclaringKlass().getName() != getConstantPool().methodAt(cpi).getHolderKlassName(getConstantPool())) {
                        enterLinkageExceptionProfile();
                        throw throwBoundary(getMeta().java_lang_NoSuchMethodError,
                                        "%s.%s%s",
                                        resolved.getDeclaringKlass().getNameAsString(),
                                        resolved.getNameAsString(),
                                        resolved.getSignatureAsString());
                    }
                }
                // Otherwise, if the resolved method is a class (static) method, the invokespecial
                // instruction throws an IncompatibleClassChangeError.
                if (resolved.isStatic()) {
                    enterLinkageExceptionProfile();
                    throw throwBoundary(getMeta().java_lang_IncompatibleClassChangeError);
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
                    if (!symbolicRef.isInterface() &&
                                    symbolicRef != declaringKlass &&
                                    declaringKlass.getSuperKlass() != null &&
                                    symbolicRef != declaringKlass.getSuperKlass() &&
                                    symbolicRef.isAssignableFrom(declaringKlass)) {
                        resolved = declaringKlass.getSuperKlass().lookupMethod(resolved.getName(), resolved.getRawSignature(), Klass.LookupMode.INSTANCE_ONLY);
                    }
                }
                break;
            default:
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw EspressoError.unimplemented("Quickening for " + Bytecodes.nameOf(opcode));
        }
        // Skip inlined nodes if instrumentation is live.
        // Lock must be owned for correctness.
        assert lockIsHeld();
        boolean tryBytecodeLevelInlining = this.instrumentation == null && allowBytecodeInlining;
        if (tryBytecodeLevelInlining) {
            invoke = InlinedMethodNode.createFor(resolved, top, resolvedOpCode, curBCI, statementIndex);
            if (invoke != null) {
                return invoke;
            }
        }

        if (resolved.isPolySignatureIntrinsic()) {
            invoke = new InvokeHandleNode(resolved, getDeclaringKlass(), top, curBCI);
        } else {
            // @formatter:off
            switch (resolvedOpCode) {
                case INVOKESTATIC    : invoke = new InvokeStaticQuickNode(resolved, top, curBCI);         break;
                case INVOKEINTERFACE : invoke = new InvokeInterfaceQuickNode(resolved, top, curBCI); break;
                case INVOKEVIRTUAL   : invoke = new InvokeVirtualQuickNode(resolved, top, curBCI);   break;
                case INVOKESPECIAL   : invoke = new InvokeSpecialQuickNode(resolved, top, curBCI);        break;
                default              :
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    throw EspressoError.unimplemented("Quickening for " + Bytecodes.nameOf(resolvedOpCode));
            }
            // @formatter:on
        }
        return invoke;
    }

    @TruffleBoundary
    private RuntimeException throwBoundary(ObjectKlass exceptionKlass) {
        throw getMeta().throwException(exceptionKlass);
    }

    @TruffleBoundary
    private RuntimeException throwBoundary(ObjectKlass exceptionKlass, String message) {
        throw getMeta().throwExceptionWithMessage(exceptionKlass, message);
    }

    @TruffleBoundary
    private RuntimeException throwBoundary(ObjectKlass exceptionKlass, String messageFormat, String... args) {
        throw getMeta().throwExceptionWithMessage(exceptionKlass, String.format(Locale.ENGLISH, messageFormat, (Object[]) args));
    }

    private int quickenInvokeDynamic(final VirtualFrame frame, int top, int curBCI, int opcode) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        assert (Bytecodes.INVOKEDYNAMIC == opcode);
        RuntimeConstantPool pool = getConstantPool();
        BaseQuickNode quick = null;
        int indyIndex = -1;
        Lock lock = getLock();
        try {
            lock.lock();
            if (bs.currentVolatileBC(curBCI) == QUICK) {
                // Check if someone did the job for us. Defer the call until we are out of the lock.
                quick = nodes[readCPI(curBCI)];
            } else {
                // fetch indy under lock.
                indyIndex = readCPI(curBCI);
            }
        } finally {
            lock.unlock();
        }
        if (quick != null) {
            // Do invocation outside of the lock.
            return quick.execute(frame) - Bytecodes.stackEffectOf(opcode);
        }
        // Resolution should happen outside of the bytecode patching lock.
        InvokeDynamicConstant.CallSiteLink link = pool.linkInvokeDynamic(getMethod().getDeclaringKlass(), indyIndex);

        // re-lock to check if someone did the job for us, since this was a heavy operation.
        quick = atomic(() -> {
            if (bs.currentVolatileBC(curBCI) == QUICK) {
                // someone beat us to it, just trust him.
                return nodes[readCPI(curBCI)];
            } else {
                return injectQuick(curBCI, new InvokeDynamicCallSiteNode(link.getMemberName(), link.getUnboxedAppendix(), link.getParsedSignature(), getMeta(), top, curBCI), QUICK);
            }
        });
        return quick.execute(frame) - Bytecodes.stackEffectOf(opcode);
    }

    // endregion Bytecode quickening

    // region Class/Method/Field resolution

    // Exposed to CheckCastNode and InstanceOfNode
    public Klass resolveType(int opcode, char cpi) {
        assert opcode == INSTANCEOF || opcode == CHECKCAST || opcode == NEW || opcode == ANEWARRAY || opcode == MULTIANEWARRAY;
        return getConstantPool().resolvedKlassAt(getDeclaringKlass(), cpi);
    }

    public Method resolveMethod(int opcode, char cpi) {
        assert Bytecodes.isInvoke(opcode);
        return getConstantPool().resolvedMethodAt(getDeclaringKlass(), cpi);
    }

    private Method resolveMethodNoCache(int opcode, char cpi) {
        CompilerAsserts.neverPartOfCompilation();
        assert Bytecodes.isInvoke(opcode);
        return getConstantPool().resolvedMethodAtNoCache(getDeclaringKlass(), cpi);
    }

    private Field resolveField(int opcode, char cpi) {
        assert opcode == GETFIELD || opcode == GETSTATIC || opcode == PUTFIELD || opcode == PUTSTATIC;
        Field field = getConstantPool().resolvedFieldAt(getMethod().getDeclaringKlass(), cpi);
        if (field.needsReResolution()) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            getMethod().getContext().getClassRedefinition().check();
            field = getConstantPool().resolveFieldAndUpdate(getMethod().getDeclaringKlass(), cpi, field);
        }
        return field;
    }

    // endregion Class/Method/Field resolution

    // region Instance/array allocation

    @ExplodeLoop
    private int allocateMultiArray(VirtualFrame frame, int top, Klass klass, int allocatedDimensions) {
        assert klass.isArray();
        CompilerAsserts.partialEvaluationConstant(allocatedDimensions);
        CompilerAsserts.partialEvaluationConstant(klass);
        int[] dimensions = new int[allocatedDimensions];
        for (int i = 0; i < allocatedDimensions; ++i) {
            dimensions[i] = popInt(frame, top - allocatedDimensions + i);
        }
        Klass component = ((ArrayKlass) klass).getComponentType();
        GuestAllocator.AllocationChecks.checkCanAllocateMultiArray(getMeta(), component, dimensions, this);
        StaticObject value = getAllocator().createNewMultiArray(component, dimensions);
        putObject(frame, top - allocatedDimensions, value);
        return -allocatedDimensions; // Does not include the created (pushed) array.
    }

    // endregion Instance/array allocation

    // region Method return

    private boolean stackIntToBoolean(int result) {
        return getJavaVersion().java9OrLater() ? (result & 1) != 0 : result != 0;
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

    public void enterImplicitExceptionProfile() {
        if (!implicitExceptionProfile) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            implicitExceptionProfile = true;
        }
    }

    public void enterLinkageExceptionProfile() {
        if (!linkageExceptionProfile) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            linkageExceptionProfile = true;
        }
    }

    private StaticObject nullCheck(StaticObject value) {
        if (!StaticObject.isNull(value)) {
            return value;
        }
        enterImplicitExceptionProfile();
        throw getMeta().throwNullPointerException();
    }

    private int checkNonZero(int value) {
        if (value != 0) {
            return value;
        }
        enterImplicitExceptionProfile();
        throw throwBoundary(getMeta().java_lang_ArithmeticException, "/ by zero");
    }

    private long checkNonZero(long value) {
        if (value != 0L) {
            return value;
        }
        enterImplicitExceptionProfile();
        throw throwBoundary(getMeta().java_lang_ArithmeticException, "/ by zero");
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
    private int putField(VirtualFrame frame, int top, Field field, int curBCI, int opcode, int statementIndex) {
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
            enterLinkageExceptionProfile();
            throw throwBoundary(getMeta().java_lang_IncompatibleClassChangeError,
                            "Expected %s field %s.%s",
                            (opcode == PUTSTATIC) ? "static" : "non-static",
                            field.getDeclaringKlass().getNameAsString(),
                            field.getNameAsString());
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
            if (field.getDeclaringKlass() != getDeclaringKlass()) {
                enterLinkageExceptionProfile();
                throw throwBoundary(getMeta().java_lang_IllegalAccessError,
                                "Update to %s final field %s.%s attempted from a different class (%s) than the field's declaring class",
                                (opcode == PUTSTATIC) ? "static" : "non-static",
                                field.getDeclaringKlass().getNameAsString(),
                                field.getNameAsString(),
                                getDeclaringKlass().getNameAsString());
            }

            boolean enforceInitializerCheck = (getLanguage().getSpecComplianceMode() == STRICT) ||
                            // HotSpot enforces this only for >= Java 9 (v53) .class files.
                            field.getDeclaringKlass().getMajorVersion() >= ClassfileParser.JAVA_9_VERSION;

            if (enforceInitializerCheck &&
                            ((opcode == PUTFIELD && !getMethod().isConstructor()) ||
                                            (opcode == PUTSTATIC && !getMethod().isClassInitializer()))) {
                enterLinkageExceptionProfile();
                throw throwBoundary(getMeta().java_lang_IllegalAccessError,
                                "Update to %s final field %s.%s attempted from a different method (%s) than the initializer method %s ",
                                (opcode == PUTSTATIC) ? "static" : "non-static",
                                field.getDeclaringKlass().getNameAsString(),
                                field.getNameAsString(),
                                getMethod().getNameAsString(),
                                (opcode == PUTSTATIC) ? "<clinit>" : "<init>");
            }
        }

        assert field.isStatic() == (opcode == PUTSTATIC);

        byte typeHeader = field.getType().byteAt(0);
        int slotCount = (typeHeader == 'J' || typeHeader == 'D') ? 2 : 1;
        assert slotCount == field.getKind().getSlotCount();
        int slot = top - slotCount - 1; // -receiver
        StaticObject receiver;
        if (opcode == PUTSTATIC) {
            receiver = initializeAndGetStatics(field);
        } else {
            // Do not release the object, it might be read again in PutFieldNode
            receiver = nullCheck(popObject(frame, slot));
        }
        if (!noForeignObjects.isValid() && opcode == PUTFIELD) {
            if (receiver.isForeignObject()) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                // Restore the receiver for quickening.
                putObject(frame, slot, receiver);
                return quickenPutField(frame, top, curBCI, opcode, statementIndex, field);
            }
        }

        switch (typeHeader) {
            case 'Z':
                boolean booleanValue = stackIntToBoolean(popInt(frame, top - 1));
                if (instrumentation != null) {
                    instrumentation.notifyFieldModification(frame, statementIndex, field, receiver, booleanValue);
                }
                InterpreterToVM.setFieldBoolean(booleanValue, receiver, field);
                break;
            case 'B':
                byte byteValue = (byte) popInt(frame, top - 1);
                if (instrumentation != null) {
                    instrumentation.notifyFieldModification(frame, statementIndex, field, receiver, byteValue);
                }
                InterpreterToVM.setFieldByte(byteValue, receiver, field);
                break;
            case 'C':
                char charValue = (char) popInt(frame, top - 1);
                if (instrumentation != null) {
                    instrumentation.notifyFieldModification(frame, statementIndex, field, receiver, charValue);
                }
                InterpreterToVM.setFieldChar(charValue, receiver, field);
                break;
            case 'S':
                short shortValue = (short) popInt(frame, top - 1);
                if (instrumentation != null) {
                    instrumentation.notifyFieldModification(frame, statementIndex, field, receiver, shortValue);
                }
                InterpreterToVM.setFieldShort(shortValue, receiver, field);
                break;
            case 'I':
                int intValue = popInt(frame, top - 1);
                if (instrumentation != null) {
                    instrumentation.notifyFieldModification(frame, statementIndex, field, receiver, intValue);
                }
                InterpreterToVM.setFieldInt(intValue, receiver, field);
                break;
            case 'D':
                double doubleValue = popDouble(frame, top - 1);
                if (instrumentation != null) {
                    instrumentation.notifyFieldModification(frame, statementIndex, field, receiver, doubleValue);
                }
                InterpreterToVM.setFieldDouble(doubleValue, receiver, field);
                break;
            case 'F':
                float floatValue = popFloat(frame, top - 1);
                if (instrumentation != null) {
                    instrumentation.notifyFieldModification(frame, statementIndex, field, receiver, floatValue);
                }
                InterpreterToVM.setFieldFloat(floatValue, receiver, field);
                break;
            case 'J':
                long longValue = popLong(frame, top - 1);
                if (instrumentation != null) {
                    instrumentation.notifyFieldModification(frame, statementIndex, field, receiver, longValue);
                }
                InterpreterToVM.setFieldLong(longValue, receiver, field);
                break;
            case '[': // fall through
            case 'L':
                StaticObject value = popObject(frame, top - 1);
                if (instrumentation != null) {
                    instrumentation.notifyFieldModification(frame, statementIndex, field, receiver, value);
                }
                InterpreterToVM.setFieldObject(value, receiver, field);
                break;
            default:
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw EspressoError.shouldNotReachHere("unexpected kind");
        }
        return -slotCount;
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
    private int getField(VirtualFrame frame, int top, Field field, int curBCI, int opcode, int statementIndex) {
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
            enterLinkageExceptionProfile();
            throw throwBoundary(getMeta().java_lang_IncompatibleClassChangeError,
                            "Expected %s field %s.%s",
                            (opcode == GETSTATIC) ? "static" : "non-static",
                            field.getDeclaringKlass().getNameAsString(),
                            field.getNameAsString());
        }

        assert field.isStatic() == (opcode == GETSTATIC);

        int slot = top - 1;
        StaticObject receiver;
        if (opcode == GETSTATIC) {
            receiver = initializeAndGetStatics(field);
        } else {
            // Do not release the object, it might be read again in GetFieldNode
            receiver = nullCheck(peekObject(frame, slot));
        }
        if (!noForeignObjects.isValid() && opcode == GETFIELD) {
            if (receiver.isForeignObject()) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
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
        byte typeHeader = field.getType().byteAt(0);
        switch (typeHeader) {
            case 'Z' : putInt(frame, resultAt, InterpreterToVM.getFieldBoolean(receiver, field) ? 1 : 0); break;
            case 'B' : putInt(frame, resultAt, InterpreterToVM.getFieldByte(receiver, field));      break;
            case 'C' : putInt(frame, resultAt, InterpreterToVM.getFieldChar(receiver, field));      break;
            case 'S' : putInt(frame, resultAt, InterpreterToVM.getFieldShort(receiver, field));     break;
            case 'I' : putInt(frame, resultAt, InterpreterToVM.getFieldInt(receiver, field));       break;
            case 'D' : putDouble(frame, resultAt, InterpreterToVM.getFieldDouble(receiver, field)); break;
            case 'F' : putFloat(frame, resultAt, InterpreterToVM.getFieldFloat(receiver, field));   break;
            case 'J' : putLong(frame, resultAt, InterpreterToVM.getFieldLong(receiver, field));     break;
            case '[' : // fall through
            case 'L' : {
                StaticObject value = InterpreterToVM.getFieldObject(receiver, field);
                putObject(frame, resultAt, value);
                checkNoForeignObjectAssumption(value);
                break;
            }
            default:
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw EspressoError.shouldNotReachHere("unexpected kind");
        }
        // @formatter:on
        int slotCount = (typeHeader == 'J' || typeHeader == 'D') ? 2 : 1;
        assert slotCount == field.getKind().getSlotCount();
        return slotCount;
    }

    private StaticObject initializeAndGetStatics(Field field) {
        ObjectKlass declaringKlass = field.getDeclaringKlass();
        if (!declaringKlass.isInitialized()) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            try (EspressoLanguage.DisableSingleStepping ignored = getLanguage().disableStepping()) {
                declaringKlass.safeInitialize();
            }
        }
        return declaringKlass.getStatics();
    }

    // endregion Field read/write

    @Override
    public String toString() {
        return getRootNode().getQualifiedName();
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

    private boolean lockIsHeld() {
        return ((ReentrantLock) getLock()).isHeldByCurrentThread();
    }

    static final class InstrumentationSupport extends EspressoNode {
        static final int NO_STATEMENT = -1;

        @Children private final EspressoBaseStatementNode[] statementNodes;
        @Child private MapperBCI hookBCIToNodeIndex;

        private final EspressoContext context;
        private final MethodVersion method;

        InstrumentationSupport(MethodVersion method) {
            this.method = method;
            this.context = method.getMethod().getContext();

            LineNumberTableAttribute table = method.getLineNumberTableAttribute();

            if (table != LineNumberTableAttribute.EMPTY) {
                List<LineNumberTableAttribute.Entry> entries = table.getEntries();
                // don't allow multiple entries with same line, keep only the first one
                // reduce the checks needed heavily by keeping track of max seen line number
                int[] seenLines = new int[entries.size()];
                Arrays.fill(seenLines, -1);
                int maxSeenLine = -1;

                this.statementNodes = new EspressoBaseStatementNode[entries.size()];
                this.hookBCIToNodeIndex = new MapperBCI(table);

                for (int i = 0; i < entries.size(); i++) {
                    LineNumberTableAttribute.Entry entry = entries.get(i);
                    int lineNumber = entry.getLineNumber();
                    boolean seen = false;
                    boolean checkSeen = !(maxSeenLine < lineNumber);
                    if (checkSeen) {
                        for (int seenLine : seenLines) {
                            if (seenLine == lineNumber) {
                                seen = true;
                                break;
                            }
                        }
                    }
                    if (!seen) {
                        statementNodes[hookBCIToNodeIndex.initIndex(i, entry.getBCI())] = new EspressoStatementNode(entry.getBCI(), lineNumber);
                        seenLines[i] = lineNumber;
                        maxSeenLine = Math.max(maxSeenLine, lineNumber);
                    }
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

        public void notifyEntry(@SuppressWarnings("unused") VirtualFrame frame, AbstractInstrumentableBytecodeNode instrumentableNode) {
            if (context.shouldReportVMEvents() && method.hasActiveHook()) {
                if (context.reportOnMethodEntry(method, instrumentableNode.getScope(frame, true))) {
                    enterAt(frame, 0);
                }
            }
        }

        public void notifyReturn(VirtualFrame frame, int statementIndex, Object returnValue) {
            if (context.shouldReportVMEvents() && method.hasActiveHook()) {
                if (context.reportOnMethodReturn(method, returnValue)) {
                    enterAt(frame, statementIndex);
                }
            }
        }

        void notifyExceptionAt(VirtualFrame frame, Throwable t, int statementIndex) {
            WrapperNode wrapperNode = getWrapperAt(statementIndex);
            if (wrapperNode == null) {
                return;
            }
            ProbeNode probeNode = wrapperNode.getProbeNode();
            probeNode.onReturnExceptionalOrUnwind(frame, t, false);
        }

        public void notifyFieldModification(VirtualFrame frame, int index, Field field, StaticObject receiver, Object value) {
            if (context.shouldReportVMEvents() && field.hasActiveBreakpoint()) {
                if (context.reportOnFieldModification(field, receiver, value)) {
                    enterAt(frame, index);
                }
            }
        }

        public void notifyFieldAccess(VirtualFrame frame, int index, Field field, StaticObject receiver) {
            if (context.shouldReportVMEvents() && field.hasActiveBreakpoint()) {
                if (context.reportOnFieldAccess(field, receiver)) {
                    enterAt(frame, index);
                }
            }
        }

        private void enterAt(VirtualFrame frame, int index) {
            WrapperNode wrapperNode = getWrapperAt(index);
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
            WrapperNode wrapperNode = getWrapperAt(index);
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

        private WrapperNode getWrapperAt(int index) {
            if (statementNodes == null || index < 0) {
                return null;
            }
            EspressoBaseStatementNode node = statementNodes[index];
            if (!(node instanceof WrapperNode)) {
                return null;
            }
            CompilerAsserts.partialEvaluationConstant(node);
            return ((WrapperNode) node);
        }
    }

    private boolean trivialBytecodes() {
        if (getMethod().isSynchronized()) {
            return false;
        }
        byte[] originalCode = getMethodVersion().getOriginalCode();
        /*
         * originalCode.length < TrivialMethodSize is checked in the constructor because this method
         * is called from a compiler thread where the context is not accessible.
         */
        BytecodeStream stream = new BytecodeStream(originalCode);
        for (int bci = 0; bci < stream.endBCI(); bci = stream.nextBCI(bci)) {
            int bc = stream.currentBC(bci);
            // Trivial methods should be leaves.
            if (Bytecodes.isInvoke(bc)) {
                return false;
            }
            if (Bytecodes.LOOKUPSWITCH == bc || Bytecodes.TABLESWITCH == bc) {
                return false;
            }
            if (Bytecodes.MONITORENTER == bc || Bytecodes.MONITOREXIT == bc) {
                return false;
            }
            if (Bytecodes.ANEWARRAY == bc || MULTIANEWARRAY == bc) {
                // The allocated array is Arrays.fill-ed with StaticObject.NULL but loops are not
                // allowed in trivial methods.
                return false;
            }
            if (Bytecodes.isBranch(bc)) {
                int dest = stream.readBranchDest(bci);
                if (dest <= bci) {
                    // Back-edge (probably a loop) but loops are not allowed in trivial methods.
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    protected boolean isTrivial() {
        CompilerAsserts.neverPartOfCompilation();
        /**
         * These two checks are dynamic and must be performed on every compilation. In the worst
         * case (a race):
         *
         * - A trivial "block" (interop operation or implicit exception creation and throw) is
         * introduced => the method will be inlined, which may or may not blow-up compilation. The
         * compiler checks that trivial methods have <= 500 Graal nodes, which reduces the chances
         * of choking the compiler with huge graphs.
         *
         * - A non-trivial "block" (interop operation or implicit exception creation and throw) is
         * introduced => the compiler "triviality" checks fail, the call is not inlined and a
         * warning is printed.
         *
         * The compiler checks that trivial methods have no guest calls, no loops and a have <= 500
         * Graal nodes.
         */
        if (!noForeignObjects.isValid() || implicitExceptionProfile) {
            return false;
        }
        if (trivialBytecodesCache == TRIVIAL_UNINITIALIZED) {
            // Cache "triviality" of original bytecodes.
            trivialBytecodesCache = trivialBytecodes() ? TRIVIAL_YES : TRIVIAL_NO;
        }
        return trivialBytecodesCache == TRIVIAL_YES;
    }
}
