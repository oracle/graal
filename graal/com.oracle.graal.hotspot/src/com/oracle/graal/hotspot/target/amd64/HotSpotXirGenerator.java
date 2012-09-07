/*
 * Copyright (c) 2011, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.target.amd64;

import static com.oracle.graal.api.code.ValueUtil.*;
import static com.oracle.graal.hotspot.target.amd64.TemplateFlag.*;
import static com.oracle.graal.hotspot.target.amd64.HotSpotAMD64Backend.*;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;

import sun.misc.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.code.Address.*;
import com.oracle.graal.api.code.Register.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.max.asm.target.amd64.*;
import com.oracle.max.cri.xir.*;
import com.oracle.max.cri.xir.XirAssembler.XirConstant;
import com.oracle.max.cri.xir.XirAssembler.XirLabel;
import com.oracle.max.cri.xir.XirAssembler.XirMark;
import com.oracle.max.cri.xir.XirAssembler.XirOperand;
import com.oracle.max.cri.xir.XirAssembler.XirParameter;

public class HotSpotXirGenerator implements XirGenerator {

    private final HotSpotVMConfig config;
    private final TargetDescription target;
    private final RegisterConfig registerConfig;
    private final HotSpotGraalRuntime compiler;


    private XirAssembler globalAsm;

    public HotSpotXirGenerator(HotSpotVMConfig config, TargetDescription target, RegisterConfig registerConfig, HotSpotGraalRuntime compiler) {
        this.config = config;
        this.target = target;
        this.registerConfig = registerConfig;
        this.compiler = compiler;
    }

    private XirConstant wordConst(XirAssembler asm, long value) {
        if (target.wordKind == Kind.Long) {
            return asm.createConstant(Constant.forLong(value));
        } else {
            assert target.wordKind == Kind.Int;
            return asm.createConstant(Constant.forInt((int) value));
        }
    }

    private XirArgument wordArg(long value) {
        if (target.wordKind == Kind.Long) {
            return XirArgument.forLong(value);
        } else {
            assert target.wordKind == Kind.Int;
            return XirArgument.forInt((int) value);
        }
    }

    private SimpleTemplates invokeInterfaceTemplates = new SimpleTemplates(NULL_CHECK) {

        @Override
        protected XirTemplate create(XirAssembler asm, long flags) {
            asm.restart();
            XirParameter receiver = asm.createInputParameter("receiver", Kind.Object);
            XirParameter addr = asm.createConstantInputParameter("addr", target.wordKind);
            XirOperand temp = asm.createRegisterTemp("temp", target.wordKind, AMD64.rax);
            XirOperand tempO = asm.createRegister("tempO", Kind.Object, AMD64.rax);

            if (is(NULL_CHECK, flags)) {
                asm.mark(MARK_IMPLICIT_NULL);
                asm.pload(target.wordKind, temp, receiver, true);
            }
            asm.mark(MARK_INVOKEINTERFACE);
            // Initialize the klassOop slot of an inline cache with null - the C++ Graal code will convert this to Universe::non_oop_word()
            asm.mov(tempO, asm.createConstant(Constant.NULL_OBJECT));

            return asm.finishTemplate(addr, "invokeinterface");
        }
    };

    private SimpleTemplates invokeVirtualTemplates = new SimpleTemplates(NULL_CHECK) {

        @Override
        protected XirTemplate create(XirAssembler asm, long flags) {
            asm.restart();
            XirParameter receiver = asm.createInputParameter("receiver", Kind.Object);
            XirParameter addr = asm.createConstantInputParameter("addr", target.wordKind);
            XirOperand temp = asm.createRegisterTemp("temp", target.wordKind, AMD64.rax);
            XirOperand tempO = asm.createRegister("tempO", Kind.Object, AMD64.rax);

            if (is(NULL_CHECK, flags)) {
                asm.mark(MARK_IMPLICIT_NULL);
                asm.pload(target.wordKind, temp, receiver, true);
            }
            asm.mark(MARK_INVOKEVIRTUAL);
            // Initialize the klassOop slot of an inline cache with null - the C++ Graal code will convert this to Universe::non_oop_word()
            asm.mov(tempO, asm.createConstant(Constant.NULL_OBJECT));

            return asm.finishTemplate(addr, "invokevirtual");
        }
    };

    private IndexTemplates inlinedInvokeVirtualTemplates = new IndexTemplates(NULL_CHECK) {

        @Override
        protected XirTemplate create(XirAssembler asm, long flags, int vtableEntryOffset) {
            asm.restart();
            XirParameter receiver = asm.createInputParameter("receiver", Kind.Object);
            XirOperand temp = asm.createRegisterTemp("temp", target.wordKind, AMD64.rax);
            XirOperand method = asm.createRegisterTemp("method", Kind.Object, AMD64.rbx);

            // load class from receiver
            if (is(NULL_CHECK, flags)) {
                asm.mark(MARK_IMPLICIT_NULL);
            }
            asm.pload(target.wordKind, temp, receiver, asm.i(config.hubOffset), true);
            // load vtable entry
            asm.pload(target.wordKind, method, temp, asm.i(vtableEntryOffset), false);
            // load entry point from methodOop
            asm.mark(MARK_IMPLICIT_NULL);
            asm.pload(target.wordKind, temp, method, asm.i(config.methodCompiledEntryOffset), true);
            asm.mark(MARK_INLINE_INVOKEVIRTUAL);

            return asm.finishTemplate(temp, "invokevirtual");
        }
    };

    private SimpleTemplates invokeSpecialTemplates = new SimpleTemplates(NULL_CHECK) {

        @Override
        protected XirTemplate create(XirAssembler asm, long flags) {
            asm.restart();
            XirParameter receiver = asm.createInputParameter("receiver", Kind.Object);
            XirParameter addr = asm.createConstantInputParameter("addr", target.wordKind);
            XirOperand temp = asm.createRegisterTemp("temp", target.wordKind, AMD64.rax);
            XirLabel stub = asm.createOutOfLineLabel("call stub");

            if (is(NULL_CHECK, flags)) {
                asm.mark(MARK_IMPLICIT_NULL);
                asm.pload(target.wordKind, temp, receiver, true);
            }
            asm.mark(MARK_INVOKESPECIAL);

            // -- out of line -------------------------------------------------------
            asm.bindOutOfLine(stub);
            XirOperand method = asm.createRegisterTemp("method", target.wordKind, AMD64.rbx);
            asm.mark(MARK_STATIC_CALL_STUB, XirMark.CALLSITE);
            asm.mov(method, wordConst(asm, 0));
            XirLabel dummy = asm.createOutOfLineLabel("dummy");
            asm.jmp(dummy);
            asm.bindOutOfLine(dummy);

            return asm.finishTemplate(addr, "invokespecial");
        }
    };

    private SimpleTemplates invokeStaticTemplates = new SimpleTemplates() {

        @Override
        protected XirTemplate create(XirAssembler asm, long flags) {
            asm.restart();
            XirParameter addr = asm.createConstantInputParameter("addr", target.wordKind);

            XirLabel stub = asm.createOutOfLineLabel("call stub");
            asm.mark(MARK_INVOKESTATIC);

            // -- out of line -------------------------------------------------------
            asm.bindOutOfLine(stub);
            XirOperand method = asm.createRegisterTemp("method", target.wordKind, AMD64.rbx);
            asm.mark(MARK_STATIC_CALL_STUB, XirMark.CALLSITE);
            asm.mov(method, wordConst(asm, 0));
            XirLabel dummy = asm.createOutOfLineLabel("dummy");
            asm.jmp(dummy);
            asm.bindOutOfLine(dummy);

            return asm.finishTemplate(addr, "invokestatic");
        }
    };

    private SimpleTemplates monitorEnterTemplates = new SimpleTemplates(NULL_CHECK) {

        @Override
        protected XirTemplate create(XirAssembler asm, long flags) {
            asm.restart(Kind.Void);
            XirParameter object = asm.createInputParameter("object", Kind.Object);
            XirParameter lock = asm.createInputParameter("lock", target.wordKind);

            if (is(NULL_CHECK, flags)) {
                asm.mark(MARK_IMPLICIT_NULL);
                asm.pload(target.wordKind, asm.createTemp("temp", target.wordKind), object, true);
            }


            // (thomaswue) It is important to use for this runtime call the debug info AFTER the monitor enter. Otherwise the monitor object
            // is not correctly garbage collected.
            final boolean useInfoAfter = true;

            if (config.useFastLocking) {
                useRegisters(asm, AMD64.rax, AMD64.rbx);
                useRegisters(asm, getGeneralParameterRegister(0));
                useRegisters(asm, getGeneralParameterRegister(1));
                asm.callRuntime(config.fastMonitorEnterStub, null, useInfoAfter, object, lock);
            } else {
                asm.reserveOutgoingStack(target.wordSize * 2);
                XirOperand rsp = asm.createRegister("rsp", target.wordKind, asRegister(AMD64.RSP));
                asm.pstore(Kind.Object, rsp, asm.i(target.wordSize), object, false);
                asm.pstore(target.wordKind, rsp, asm.i(0), lock, false);
                asm.callRuntime(config.monitorEnterStub, null, useInfoAfter);
            }

            return asm.finishTemplate("monitorEnter");
        }
    };

    private Register getGeneralParameterRegister(int index) {
        return registerConfig.getCallingConventionRegisters(CallingConvention.Type.RuntimeCall, RegisterFlag.CPU)[index];
    }

    private SimpleTemplates monitorExitTemplates = new SimpleTemplates(NULL_CHECK) {

        @Override
        protected XirTemplate create(XirAssembler asm, long flags) {
            asm.restart(Kind.Void);
            XirParameter object = asm.createInputParameter("object", Kind.Object);
            XirParameter lock = asm.createInputParameter("lock", target.wordKind);

            if (config.useFastLocking) {
                useRegisters(asm, AMD64.rax, AMD64.rbx);
                useRegisters(asm, getGeneralParameterRegister(0));
                useRegisters(asm, getGeneralParameterRegister(1));
                asm.callRuntime(config.fastMonitorExitStub, null, object, lock);
            } else {
                asm.reserveOutgoingStack(target.wordSize);
                asm.pstore(target.wordKind, asm.createRegister("rsp", target.wordKind, asRegister(AMD64.RSP)), asm.i(0), lock, false);
                asm.callRuntime(config.monitorExitStub, null);
            }

            return asm.finishTemplate("monitorExit");
        }
    };

    private final IndexTemplates newInstanceTemplates = new IndexTemplates() {

        @Override
        protected XirTemplate create(XirAssembler asm, long flags, int size) {
            XirOperand result = asm.restart(target.wordKind);
            XirOperand hub = asm.createInputParameter("hub", Kind.Object);

            XirOperand temp1 = asm.createRegisterTemp("temp1", target.wordKind, AMD64.rcx);
            XirOperand temp1o = asm.createRegister("temp1o", Kind.Object, AMD64.rcx);
            XirOperand temp2 = asm.createRegisterTemp("temp2", target.wordKind, AMD64.rbx);
            XirOperand temp2i = asm.createRegister("temp2i", Kind.Int, AMD64.rbx);
            useRegisters(asm, AMD64.rsi);
            XirLabel tlabFull = asm.createOutOfLineLabel("tlab full");
            XirLabel resume = asm.createInlineLabel("resume");

            // check if the class is already initialized
            asm.pload(Kind.Int, temp2i, hub, asm.i(config.klassStateOffset), false);
            asm.jneq(tlabFull, temp2i, asm.i(config.klassStateFullyInitialized));

            XirOperand thread = asm.createRegisterTemp("thread", target.wordKind, AMD64.r15);
            asm.pload(target.wordKind, result, thread, asm.i(config.threadTlabTopOffset), false);
            asm.add(temp1, result, wordConst(asm, size));
            asm.pload(target.wordKind, temp2, thread, asm.i(config.threadTlabEndOffset), false);

            asm.jgt(tlabFull, temp1, temp2);
            asm.pstore(target.wordKind, thread, asm.i(config.threadTlabTopOffset), temp1, false);

            asm.bindInline(resume);

            asm.pload(target.wordKind, temp1, hub, asm.i(config.instanceHeaderPrototypeOffset), false);
            asm.pstore(target.wordKind, result, asm.i(config.markOffset), temp1, false);
            asm.mov(temp1o, hub); // need a temporary register since Intel cannot store 64-bit constants to memory
            asm.pstore(Kind.Object, result, asm.i(config.hubOffset), temp1o, false);

            if (size > 2 * target.wordSize) {
                asm.mov(temp1, wordConst(asm, 0));
                for (int offset = 2 * target.wordSize; offset < size; offset += target.wordSize) {
                    asm.pstore(target.wordKind, result, asm.i(offset), temp1, false);
                }
            }

            // -- out of line -------------------------------------------------------
            asm.bindOutOfLine(tlabFull);
            XirOperand arg = asm.createRegisterTemp("runtime call argument", Kind.Object, AMD64.rdx);
            asm.mov(arg, hub);
            useRegisters(asm, AMD64.rax);
            asm.callRuntime(config.newInstanceStub, result);
            asm.jmp(resume);

            return asm.finishTemplate("new instance");
        }
    };

    private SimpleTemplates newObjectArrayTemplates = new SimpleTemplates() {

        @Override
        protected XirTemplate create(XirAssembler asm, long flags) {
            emitNewTypeArray(asm, Kind.Object, config.useFastNewObjectArray, config.newObjectArrayStub);
            return asm.finishTemplate("newObjectArray");
        }
    };

    private void emitNewTypeArray(XirAssembler asm, Kind kind, boolean useFast, long slowPathStub) {
        XirOperand result = asm.restart(target.wordKind);

        XirParameter lengthParam = asm.createInputParameter("length", Kind.Int, true);

        XirOperand length = asm.createRegisterTemp("length", Kind.Int, AMD64.rbx);
        XirOperand hub = asm.createRegisterTemp("hub", Kind.Object, AMD64.rdx);

        // Registers rsi, rcx, rdi, and rax are needed by the runtime call.
        // Hub needs to be on rdx, length on rbx.
        XirOperand temp1 = asm.createRegisterTemp("temp1", target.wordKind, AMD64.rcx);
        XirOperand temp1o = asm.createRegister("temp1o", Kind.Object, AMD64.rcx);
        XirOperand temp2 = asm.createRegisterTemp("temp2", target.wordKind, AMD64.rax);
        XirOperand temp3 = asm.createRegisterTemp("temp3", target.wordKind, AMD64.rdi);
        XirOperand size = asm.createRegisterTemp("size", Kind.Int, AMD64.rsi);

        asm.mov(hub, asm.createConstantInputParameter("hub", Kind.Object));
        asm.mov(length, lengthParam);

        if (useFast) {

            XirLabel slowPath = asm.createOutOfLineLabel("slowPath");

            XirLabel done = asm.createInlineLabel("done");

            // Check for negative array size.
            // TODO: Also check for upper bound
            asm.jlt(slowPath, length, asm.i(0));

            final int aligning = target.wordSize;
            final int arrayLengthOffset = target.wordSize * 2;
            final int arrayElementOffset = kind.getArrayBaseOffset();

            // Calculate aligned size
            asm.mov(size, length);
            int scale = CodeUtil.log2(target.sizeInBytes(kind));
            if (scale != 0) {
                asm.shl(size, size, asm.i(scale));
            }
            asm.add(size, size, asm.i(arrayElementOffset + aligning - 1));
            long mask = 0xFFFFFFFFL;
            mask <<= CodeUtil.log2(aligning);
            asm.and(size, size, asm.i((int) mask));

            // Try tlab allocation
            XirOperand thread = asm.createRegisterTemp("thread", target.wordKind, AMD64.r15);
            asm.pload(target.wordKind, result, thread, asm.i(config.threadTlabTopOffset), false);
            asm.add(temp1, result, size);
            asm.pload(target.wordKind, temp2, thread, asm.i(config.threadTlabEndOffset), false);
            asm.jgt(slowPath, temp1, temp2);
            asm.pstore(target.wordKind, thread, asm.i(config.threadTlabTopOffset), temp1, false);

            // Now the new object is in result, store mark word and klass
            asm.pload(target.wordKind, temp1, hub, asm.i(config.instanceHeaderPrototypeOffset), false);
            asm.pstore(target.wordKind, result, asm.i(config.markOffset), temp1, false);
            asm.mov(temp1o, hub); // need a temporary register since Intel cannot store 64-bit constants to memory
            asm.pstore(Kind.Object, result, asm.i(config.hubOffset), temp1o, false);

            // Store array length
            asm.pstore(Kind.Int, result, asm.i(arrayLengthOffset), length, false);

            // Initialize with 0
            XirLabel top = asm.createInlineLabel("top");
            asm.sub(size, size, asm.i(arrayElementOffset));
            asm.shr(size, size, asm.i(Scale.Times8.log2));
            asm.jeq(done, size, asm.i(0));
            asm.xor(temp3, temp3, temp3);
            asm.bindInline(top);
            asm.pstore(target.wordKind, result, size, temp3, arrayElementOffset - target.wordSize, Scale.Times8, false);
            asm.decAndJumpNotZero(top, size);

            asm.bindInline(done);

            // Slow path
            asm.bindOutOfLine(slowPath);
            asm.callRuntime(slowPathStub, result);
            asm.jmp(done);
        } else {
            asm.callRuntime(slowPathStub, result);
        }
    }

    private KindTemplates newTypeArrayTemplates = new KindTemplates() {
        @Override
        protected XirTemplate create(XirAssembler asm, long flags, Kind kind) {
            emitNewTypeArray(asm, kind, config.useFastNewTypeArray, config.newTypeArrayStub);
            return asm.finishTemplate("newTypeArray<" + kind.toString() + ">");
        }
    };

    private final IndexTemplates multiNewArrayTemplate = new IndexTemplates() {

        @Override
        protected XirTemplate create(XirAssembler asm, long flags, int dimensions) {
            XirOperand result = asm.restart(Kind.Object);

            XirOperand hub = asm.createRegisterTemp("hub", Kind.Object, AMD64.rax);
            XirOperand rank = asm.createRegisterTemp("rank", Kind.Int, AMD64.rbx);
            XirOperand sizes = asm.createRegisterTemp("sizes", Kind.Long, AMD64.rcx);
            XirOperand thread = asm.createRegisterTemp("thread", Kind.Long, AMD64.r15);
            asm.add(sizes, thread, asm.l(config.threadMultiNewArrayStorageOffset));
            for (int i = 0; i < dimensions; i++) {
                XirParameter length = asm.createInputParameter("length" + i, Kind.Int, true);
                asm.pstore(Kind.Int, sizes, asm.i(i * target.sizeInBytes(Kind.Int)), length, false);
            }

            asm.mov(hub, asm.createConstantInputParameter("hub", Kind.Object));

            asm.mov(rank, asm.i(dimensions));
            // not necessary because we already have a temp in rax:  useRegisters(asm, AMD64.rax);
            asm.callRuntime(config.newMultiArrayStub, result);
            return asm.finishTemplate("multiNewArray" + dimensions);
        }
    };

    enum CheckcastCounter {
        hintsHit("hit a hint type"),
        hintsMissed("missed the hint types"),
        exact("tested type is (statically) final"),
        noHints_class("profile information is not used (test type is a class)"),
        noHints_iface("profile information is not used (test type is an interface)"),
        noHints_unknown("test type is not a compile-time constant"),
        isNull("object tested is null"),
        exception("type test failed with a ClassCastException");

        public final String desc;

        private CheckcastCounter(String desc) {
            this.desc = desc;
        }

        static final CheckcastCounter[] VALUES = values();
    }

    private static final long[] checkcastCounters = new long[CheckcastCounter.VALUES.length];

    private IndexTemplates checkCastTemplates = new IndexTemplates(NULL_CHECK, EXACT_HINTS) {

        private void incCounter(XirAssembler asm, XirOperand counter, XirParameter counters, CheckcastCounter offset) {
            int disp = Unsafe.getUnsafe().arrayBaseOffset(long[].class);
            Scale scale = Scale.fromInt(Unsafe.getUnsafe().arrayIndexScale(long[].class));
            XirConstant index = asm.i(offset.ordinal());
            asm.pload(Kind.Long, counter, counters, index, disp, scale, false);
            asm.add(counter, counter, asm.i(1));
            asm.pstore(Kind.Long, counters, index, counter, disp, scale, false);
        }

        @Override
        protected XirTemplate create(XirAssembler asm, long flags, int hintCount) {
            asm.restart(Kind.Void);
            boolean exact = is(EXACT_HINTS, flags);
            XirParameter counters = GraalOptions.SnippetCounters ? asm.createConstantInputParameter("counters", Kind.Object) : null;
            XirParameter object = asm.createInputParameter("object", Kind.Object);
            final XirOperand hub = exact ? null : asm.createConstantInputParameter("hub", Kind.Object);

            XirOperand objHub = asm.createTemp("objHub", Kind.Object);
            XirOperand counter = counters != null ? asm.createTemp("counter", Kind.Long) : null;

            XirLabel success = asm.createInlineLabel("success");
            XirLabel slowPath = asm.createOutOfLineLabel("slow path");

            if (is(NULL_CHECK, flags)) {
                // null can be cast to anything
                if (counters != null) {
                    XirLabel isNotNull = asm.createInlineLabel("isNull");
                    asm.jneq(isNotNull, object, asm.o(null));
                    incCounter(asm, counter, counters, CheckcastCounter.isNull);
                    asm.jmp(success);
                    asm.bindInline(isNotNull);
                } else {
                    asm.jeq(success, object, asm.o(null));
                }

            }

            asm.pload(Kind.Object, objHub, object, asm.i(config.hubOffset), false);
            if (hintCount == 0) {
                assert !exact;
                if (counters != null) {
                    CheckcastCounter cc;
                    if (is(NULL_TYPE, flags)) {
                        cc = CheckcastCounter.noHints_unknown;
                    } else if (is(INTERFACE_TYPE, flags)) {
                        cc = CheckcastCounter.noHints_iface;
                    } else {
                        cc = CheckcastCounter.noHints_class;
                    }
                    incCounter(asm, counter, counters, cc);
                }

                checkSubtype(asm, objHub, objHub, hub);
                asm.jeq(slowPath, objHub, asm.o(null));
                asm.bindInline(success);

                // -- out of line -------------------------------------------------------
                asm.bindOutOfLine(slowPath);
            } else {
                XirLabel hintsSuccess = counters == null ? success : asm.createInlineLabel("hintsSuccess");
                XirOperand scratchObject = asm.createRegisterTemp("scratch", Kind.Object, AMD64.r10);
                // if we get an exact match: succeed immediately
                for (int i = 0; i < hintCount; i++) {
                    XirParameter hintHub = asm.createConstantInputParameter("hintHub" + i, Kind.Object);
                    asm.mov(scratchObject, hintHub);
                    if (i < hintCount - 1) {
                        asm.jeq(hintsSuccess, objHub, scratchObject);
                    } else {
                        asm.jneq(slowPath, objHub, scratchObject);
                    }
                }

                if (counters != null) {
                    asm.bindInline(hintsSuccess);
                    incCounter(asm, counter, counters, exact ? CheckcastCounter.exact : CheckcastCounter.hintsHit);
                }

                asm.bindInline(success);

                // -- out of line -------------------------------------------------------
                asm.bindOutOfLine(slowPath);
                if (!exact) {
                    if (counters != null) {
                        incCounter(asm, counter, counters, CheckcastCounter.hintsMissed);
                    }
                    checkSubtype(asm, objHub, objHub, hub);
                    asm.jneq(success, objHub, asm.o(null));
                }
            }

            if (counters != null) {
                incCounter(asm, counter, counters, CheckcastCounter.exception);
            }
            DeoptimizationReason deoptReason = exact ? DeoptimizationReason.OptimizedTypeCheckViolated : DeoptimizationReason.ClassCastException;
            XirOperand scratch = asm.createRegisterTemp("scratch", target.wordKind, AMD64.r10);
            asm.mov(scratch, wordConst(asm, compiler.getRuntime().encodeDeoptActionAndReason(DeoptimizationAction.InvalidateReprofile, deoptReason)));
            asm.callRuntime(RuntimeCall.Deoptimize, null);
            asm.shouldNotReachHere();

            return asm.finishTemplate("checkcast");
        }
    };

    private IndexTemplates instanceOfTemplates = new IndexTemplates(NULL_CHECK, EXACT_HINTS) {

        @Override
        protected XirTemplate create(XirAssembler asm, long flags, int hintCount) {
            asm.restart(Kind.Void);
            XirParameter object = asm.createInputParameter("object", Kind.Object);
            final XirOperand hub = is(EXACT_HINTS, flags) ? null : asm.createConstantInputParameter("hub", Kind.Object);

            XirOperand objHub = asm.createTemp("objHub", Kind.Object);

            XirLabel trueSucc = asm.createInlineLabel(XirLabel.TrueSuccessor);
            XirLabel falseSucc = asm.createInlineLabel(XirLabel.FalseSuccessor);

            if (is(NULL_CHECK, flags)) {
                // null isn't "instanceof" anything
                asm.jeq(falseSucc, object, asm.o(null));
            }

            asm.pload(Kind.Object, objHub, object, asm.i(config.hubOffset), false);
            if (hintCount == 0) {
                assert !is(EXACT_HINTS, flags);
                checkSubtype(asm, objHub, objHub, hub);
                asm.jeq(falseSucc, objHub, asm.o(null));
                asm.jmp(trueSucc);
            } else {
                XirLabel slowPath = null;
                XirOperand scratchObject = asm.createRegisterTemp("scratch", Kind.Object, AMD64.r10);

                // if we get an exact match: succeed immediately
                for (int i = 0; i < hintCount; i++) {
                    XirParameter hintHub = asm.createConstantInputParameter("hintHub" + i, Kind.Object);
                    asm.mov(scratchObject, hintHub);
                    if (i < hintCount - 1) {
                        asm.jeq(trueSucc, objHub, scratchObject);
                    } else {
                        if (is(EXACT_HINTS, flags)) {
                            asm.jneq(falseSucc, objHub, scratchObject);
                            asm.jmp(trueSucc);
                        } else {
                            slowPath = asm.createOutOfLineLabel("slow path");
                            asm.jneq(slowPath, objHub, scratchObject);
                            asm.jmp(trueSucc);
                        }
                    }
                }

                // -- out of line -------------------------------------------------------
                if (slowPath != null) {
                    asm.bindOutOfLine(slowPath);
                    checkSubtype(asm, objHub, objHub, hub);
                    asm.jeq(falseSucc, objHub, asm.o(null));
                    asm.jmp(trueSucc);
                }
            }

            return asm.finishTemplate("instanceof");
        }
    };

    private IndexTemplates materializeInstanceOfTemplates = new IndexTemplates(NULL_CHECK, EXACT_HINTS) {

        @Override
        protected XirTemplate create(XirAssembler asm, long flags, int hintCount) {
            XirOperand result = asm.restart(Kind.Int);
            XirParameter object = asm.createInputParameter("object", Kind.Object);
            final XirOperand hub = is(EXACT_HINTS, flags) ? null : asm.createConstantInputParameter("hub", Kind.Object);
            XirOperand trueValue = asm.createInputParameter("trueValue", Kind.Int);
            XirOperand falseValue = asm.createInputParameter("falseValue", Kind.Int);

            XirOperand objHub = asm.createTemp("objHub", Kind.Object);

            XirLabel end = asm.createInlineLabel("end");
            XirLabel falseSucc = asm.createInlineLabel("ko");

            if (is(NULL_CHECK, flags)) {
                // null isn't "instanceof" anything
                asm.jeq(falseSucc, object, asm.o(null));
            }

            asm.pload(Kind.Object, objHub, object, asm.i(config.hubOffset), false);
            asm.mov(result, trueValue);

            if (hintCount == 0) {
                assert !is(EXACT_HINTS, flags);
                checkSubtype(asm, objHub, objHub, hub);
                asm.jneq(end, objHub, asm.o(null));
                asm.bindInline(falseSucc);
                asm.mov(result, falseValue);
                asm.bindInline(end);
            } else {
                XirLabel slowPath = null;
                XirOperand scratchObject = asm.createRegisterTemp("scratch", Kind.Object, AMD64.r10);

                // if we get an exact match: succeed immediately
                for (int i = 0; i < hintCount; i++) {
                    XirParameter hintHub = asm.createConstantInputParameter("hintHub" + i, Kind.Object);
                    asm.mov(scratchObject, hintHub);
                    if (i < hintCount - 1) {
                        asm.jeq(end, objHub, scratchObject);
                    } else {
                        if (is(EXACT_HINTS, flags)) {
                            asm.jeq(end, objHub, scratchObject);
                        } else {
                            slowPath = asm.createOutOfLineLabel("slow path");
                            asm.jeq(end, objHub, scratchObject);
                            asm.jmp(slowPath);
                        }
                    }
                }
                asm.bindInline(falseSucc);
                asm.mov(result, falseValue);
                asm.bindInline(end);

                // -- out of line -------------------------------------------------------
                if (slowPath != null) {
                    asm.bindOutOfLine(slowPath);
                    checkSubtype(asm, objHub, objHub, hub);
                    asm.jeq(falseSucc, objHub, asm.o(null));
                    asm.jmp(end);
                }
            }

            return asm.finishTemplate("instanceof");
        }
    };

    private SimpleTemplates typeCheckTemplates = new SimpleTemplates(NULL_CHECK) {
       @Override
       protected XirTemplate create(XirAssembler asm, long flags) {
           asm.restart(Kind.Void);
           XirParameter objHub = asm.createInputParameter("objectHub", Kind.Object);
           XirOperand hub = asm.createConstantInputParameter("hub", Kind.Object);
           XirLabel falseSucc = asm.createInlineLabel(XirLabel.FalseSuccessor);

           XirOperand checkHub = asm.createTemp("checkHub", Kind.Object);

           if (is(NULL_CHECK, flags)) {
               asm.mark(MARK_IMPLICIT_NULL);
           }

           asm.mov(checkHub, hub);
           // if we get an exact match: continue.
           asm.jneq(falseSucc, objHub, checkHub);

           return asm.finishTemplate("typeCheck");
       }
    };

    @Override
    public XirSnippet genInvokeInterface(XirSite site, XirArgument receiver, JavaMethod method) {
        return new XirSnippet(invokeInterfaceTemplates.get(site), receiver, wordArg(0));
    }

    @Override
    public XirSnippet genInvokeVirtual(XirSite site, XirArgument receiver, JavaMethod method, boolean megamorph) {
        int vtableEntryOffset = 0;

        if (GraalOptions.InlineVTableStubs && (GraalOptions.AlwaysInlineVTableStubs || megamorph)) {
            HotSpotResolvedJavaMethod hsMethod = (HotSpotResolvedJavaMethod) method;
            if (!hsMethod.holder().isInterface()) {
                vtableEntryOffset = hsMethod.vtableEntryOffset();
            }
        }
        if (vtableEntryOffset > 0) {
            return new XirSnippet(inlinedInvokeVirtualTemplates.get(site, vtableEntryOffset), receiver);
        } else {
            return new XirSnippet(invokeVirtualTemplates.get(site), receiver, wordArg(0));
        }
    }

    @Override
    public XirSnippet genInvokeSpecial(XirSite site, XirArgument receiver, JavaMethod method) {
        return new XirSnippet(invokeSpecialTemplates.get(site), receiver, wordArg(0));
    }

    @Override
    public XirSnippet genInvokeStatic(XirSite site, JavaMethod method) {
        return new XirSnippet(invokeStaticTemplates.get(site), wordArg(0));
    }

    @Override
    public XirSnippet genMonitorEnter(XirSite site, XirArgument receiver, XirArgument lockAddress) {
        return new XirSnippet(monitorEnterTemplates.get(site), receiver, lockAddress);
    }

    @Override
    public XirSnippet genMonitorExit(XirSite site, XirArgument receiver, XirArgument lockAddress) {
        return new XirSnippet(monitorExitTemplates.get(site), receiver, lockAddress);
    }

    @Override
    public XirSnippet genNewInstance(XirSite site, JavaType type) {
        HotSpotResolvedJavaType resolvedType = (HotSpotResolvedJavaType) type;
        int instanceSize = resolvedType.instanceSize();
        assert instanceSize >= 0;
        return new XirSnippet(newInstanceTemplates.get(site, instanceSize), XirArgument.forObject(resolvedType.klassOop()));
    }

    @Override
    public XirSnippet genNewArray(XirSite site, XirArgument length, Kind elementKind, JavaType componentType, JavaType arrayType) {
        if (elementKind == Kind.Object) {
            assert arrayType instanceof ResolvedJavaType;
            return new XirSnippet(newObjectArrayTemplates.get(site), length, XirArgument.forObject(((HotSpotJavaType) arrayType).klassOop()));
        } else {
            assert arrayType == null;
            JavaType primitiveArrayType = compiler.getCompilerToVM().getPrimitiveArrayType(elementKind);
            return new XirSnippet(newTypeArrayTemplates.get(site, elementKind), length, XirArgument.forObject(((HotSpotJavaType) primitiveArrayType).klassOop()));
        }
    }

    @Override
    public XirSnippet genNewMultiArray(XirSite site, XirArgument[] lengths, JavaType type) {
        XirArgument[] params = Arrays.copyOf(lengths, lengths.length + 1);
        params[lengths.length] = XirArgument.forObject(((HotSpotJavaType) type).klassOop());
        return new XirSnippet(multiNewArrayTemplate.get(site, lengths.length), params);
    }

    @Override
    public XirSnippet genCheckCast(XirSite site, XirArgument receiver, XirArgument hub, ResolvedJavaType type, JavaTypeProfile profile) {
        final boolean useCounters = GraalOptions.SnippetCounters;
        TypeCheckHints hints = new TypeCheckHints(type, profile, site.assumptions(), GraalOptions.CheckcastMinHintHitProbability, GraalOptions.CheckcastMaxHints);
        int hintsLength = hints.types.length;
        if (hintsLength == 0) {
            if (useCounters) {
                if (type == null) {
                    return new XirSnippet(checkCastTemplates.get(site, 0, NULL_TYPE), XirArgument.forObject(checkcastCounters), receiver, hub);
                } else if (type.isInterface()) {
                    return new XirSnippet(checkCastTemplates.get(site, 0, INTERFACE_TYPE), XirArgument.forObject(checkcastCounters), receiver, hub);
                } else {
                    return new XirSnippet(checkCastTemplates.get(site, 0), XirArgument.forObject(checkcastCounters), receiver, hub);
                }
            } else {
                return new XirSnippet(checkCastTemplates.get(site, 0), receiver, hub);
            }
        } else {
            XirArgument[] params = new XirArgument[(useCounters ? 1 : 0) + hintsLength + (hints.exact ? 1 : 2)];
            int i = 0;
            if (useCounters) {
                params[i++] = XirArgument.forObject(checkcastCounters);
            }
            params[i++] = receiver;
            if (!hints.exact) {
                params[i++] = hub;
            }
            for (ResolvedJavaType hint : hints.types) {
                params[i++] = XirArgument.forObject(((HotSpotJavaType) hint).klassOop());
            }
            XirTemplate template = hints.exact ? checkCastTemplates.get(site, hintsLength, EXACT_HINTS) : checkCastTemplates.get(site, hintsLength);
            return new XirSnippet(template, params);
        }
    }

    @Override
    public XirSnippet genInstanceOf(XirSite site, XirArgument object, XirArgument hub, ResolvedJavaType type, JavaTypeProfile profile) {
        TypeCheckHints hints = new TypeCheckHints(type, profile, site.assumptions(), GraalOptions.InstanceOfMinHintHitProbability, GraalOptions.InstanceOfMaxHints);
        int hintsLength = hints.types.length;
        if (hintsLength == 0) {
            return new XirSnippet(instanceOfTemplates.get(site, 0), object, hub);
        } else {
            XirArgument[] params = new XirArgument[hintsLength + (hints.exact ? 1 : 2)];
            int i = 0;
            params[i++] = object;
            if (!hints.exact) {
                params[i++] = hub;
            }
            for (ResolvedJavaType hint : hints.types) {
                params[i++] = XirArgument.forObject(((HotSpotJavaType) hint).klassOop());
            }
            XirTemplate template = hints.exact ? instanceOfTemplates.get(site, hintsLength, EXACT_HINTS) : instanceOfTemplates.get(site, hintsLength);
            return new XirSnippet(template, params);
        }
    }

    @Override
    public XirSnippet genMaterializeInstanceOf(XirSite site, XirArgument object, XirArgument hub, XirArgument trueValue, XirArgument falseValue, ResolvedJavaType type, JavaTypeProfile profile) {
        TypeCheckHints hints = new TypeCheckHints(type, profile, site.assumptions(), GraalOptions.InstanceOfMinHintHitProbability, GraalOptions.InstanceOfMaxHints);
        int hintsLength = hints.types.length;
        if (hintsLength == 0) {
            return new XirSnippet(materializeInstanceOfTemplates.get(site, 0), object, hub, trueValue, falseValue);
        } else {
            XirArgument[] params = new XirArgument[hintsLength + (hints.exact ? 3 : 4)];
            int i = 0;
            params[i++] = object;
            if (!hints.exact) {
                params[i++] = hub;
            }
            params[i++] = trueValue;
            params[i++] = falseValue;
            for (ResolvedJavaType hint : hints.types) {
                params[i++] = XirArgument.forObject(((HotSpotJavaType) hint).klassOop());
            }
            XirTemplate template = hints.exact ? materializeInstanceOfTemplates.get(site, hintsLength, EXACT_HINTS) : materializeInstanceOfTemplates.get(site, hintsLength);
            return new XirSnippet(template, params);
        }
    }

    @Override
    public XirSnippet genTypeBranch(XirSite site, XirArgument thisHub, XirArgument otherHub, JavaType type) {
        assert type instanceof ResolvedJavaType;
        return new XirSnippet(typeCheckTemplates.get(site), thisHub, otherHub);
    }

    @Override
    public void initialize(XirAssembler asm) {
        this.globalAsm = asm;
    }

    private void checkSubtype(XirAssembler asm, XirOperand result, XirOperand objHub, XirOperand hub) {
        asm.push(objHub);
        asm.push(hub);
        asm.callRuntime(config.instanceofStub, null);
        asm.pop(result);
        asm.pop(result);
    }

    private static void useRegisters(XirAssembler asm, Register... registers) {
        if (registers != null) {
            for (Register register : registers) {
                asm.createRegisterTemp("reg", Kind.Illegal, register);
            }
        }
    }

    public boolean is(TemplateFlag check, long flags) {
        return (flags & check.bits()) == check.bits();
    }

    /**
     * Base class for all the ondemand template generators. It is not normally subclassed directly, but through one of
     * its subclasses (SimpleTemplates, KindTemplates, IndexTemplates).
     */
    private abstract class Templates {

        private ConcurrentHashMap<Long, XirTemplate> templates = new ConcurrentHashMap<>();
        private final long mask;

        /**
         * Each flag passed to this method will cause templates with and without it to be generated.
         */
        public Templates(TemplateFlag... flags) {
            this.mask = getBits((int) INDEX_MASK, null, flags);
        }

        protected abstract XirTemplate create(XirAssembler asm, long flags);

        protected long getBits(int index, XirSite site, TemplateFlag... flags) {
            long bits = index;
            if (site != null) {
                bits |= site.requiresNullCheck() ? NULL_CHECK.bits() : 0;
                bits |= site.requiresReadBarrier() ? READ_BARRIER.bits() : 0;
                bits |= site.requiresWriteBarrier() ? WRITE_BARRIER.bits() : 0;
                bits |= site.requiresArrayStoreCheck() ? STORE_CHECK.bits() : 0;
                bits |= site.requiresBoundsCheck() ? BOUNDS_CHECK.bits() : 0;
            }
            if (flags != null) {
                for (TemplateFlag flag : flags) {
                    bits |= flag.bits();
                }
            }
            return bits;
        }

        protected XirTemplate getInternal(long flags) {
            long maskedFlags = flags & mask;
            XirTemplate template = templates.get(maskedFlags);
            if (template == null) {
                template = create(HotSpotXirGenerator.this.globalAsm.copy(), maskedFlags);
                templates.put(maskedFlags, template);
            }
            return template;
        }
    }

    private abstract class SimpleTemplates extends Templates {

        public SimpleTemplates(TemplateFlag... flags) {
            super(flags);
        }

        public XirTemplate get(XirSite site, TemplateFlag... flags) {
            return getInternal(getBits(0, site, flags));
        }
    }

    private abstract class IndexTemplates extends Templates {

        public IndexTemplates(TemplateFlag... flags) {
            super(flags);
        }

        @Override
        protected final XirTemplate create(XirAssembler asm, long flags) {
            return create(asm, flags & FLAGS_MASK, (int) (flags & INDEX_MASK));
        }

        protected abstract XirTemplate create(XirAssembler asm, long flags, int index);

        public XirTemplate get(XirSite site, int size, TemplateFlag... flags) {
            return getInternal(getBits(size, site, flags));
        }
    }

    private abstract class KindTemplates extends Templates {

        public KindTemplates(TemplateFlag... flags) {
            super(flags);
        }

        @Override
        protected final XirTemplate create(XirAssembler asm, long flags) {
            return create(asm, flags & FLAGS_MASK, Kind.values()[(int) (flags & INDEX_MASK)]);
        }

        protected abstract XirTemplate create(XirAssembler asm, long flags, Kind kind);

        public XirTemplate get(XirSite site, Kind kind, TemplateFlag... flags) {
            return getInternal(getBits(kind.ordinal(), site, flags));
        }
    }

    private static void printCounter(PrintStream out, CheckcastCounter name, long count, long total) {
        double percent = total == 0D ? 0D : ((double) (count * 100)) / total;
        out.println(String.format("%16s: %5.2f%%%10d  // %s", name, percent, count, name.desc));
    }

    public static  void printCheckcastCounters(PrintStream out) {
        class Count implements Comparable<Count> {
            long c;
            CheckcastCounter name;
            Count(long c, CheckcastCounter name) {
                this.c = c;
                this.name = name;
            }
            public int compareTo(Count o) {
                return (int) (o.c - c);
            }
        }

        long total = 0;
        Count[] counters = new Count[checkcastCounters.length];
        for (int i = 0; i < counters.length; i++) {
            counters[i] = new Count(checkcastCounters[i], CheckcastCounter.VALUES[i]);
            total += checkcastCounters[i];
        }
        Arrays.sort(counters);

        out.println();
        out.println("** XIR checkcast counters **");
        for (Count c : counters) {
            printCounter(out, c.name, c.c, total);
        }
    }

    public static void printCounters(PrintStream out) {
        if (GraalOptions.SnippetCounters) {
            printCheckcastCounters(out);
        }
    }
}
