/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.max.graal.hotspot;

import static com.oracle.max.graal.hotspot.TemplateFlag.*;
import static com.sun.cri.ci.CiCallingConvention.Type.*;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;

import com.oracle.max.asm.target.amd64.*;
import com.oracle.max.graal.compiler.*;
import com.sun.cri.ci.CiAddress.Scale;
import com.sun.cri.ci.*;
import com.sun.cri.ci.CiRegister.RegisterFlag;
import com.sun.cri.ri.*;
import com.sun.cri.ri.RiType.Representation;
import com.sun.cri.xir.*;
import com.sun.cri.xir.CiXirAssembler.XirConstant;
import com.sun.cri.xir.CiXirAssembler.XirLabel;
import com.sun.cri.xir.CiXirAssembler.XirMark;
import com.sun.cri.xir.CiXirAssembler.XirOperand;
import com.sun.cri.xir.CiXirAssembler.XirParameter;

public class HotSpotXirGenerator implements RiXirGenerator {

    // this needs to correspond to graal_CodeInstaller.hpp
    // @formatter:off
    private static final Integer MARK_VERIFIED_ENTRY            = 0x0001;
    private static final Integer MARK_UNVERIFIED_ENTRY          = 0x0002;
    private static final Integer MARK_OSR_ENTRY                 = 0x0003;
    private static final Integer MARK_UNWIND_ENTRY              = 0x0004;
    private static final Integer MARK_EXCEPTION_HANDLER_ENTRY   = 0x0005;
    private static final Integer MARK_DEOPT_HANDLER_ENTRY       = 0x0006;

    private static final Integer MARK_STATIC_CALL_STUB          = 0x1000;

    private static final Integer MARK_INVOKE_INVALID            = 0x2000;
    private static final Integer MARK_INVOKEINTERFACE           = 0x2001;
    private static final Integer MARK_INVOKESTATIC              = 0x2002;
    private static final Integer MARK_INVOKESPECIAL             = 0x2003;
    private static final Integer MARK_INVOKEVIRTUAL             = 0x2004;

    private static final Integer MARK_IMPLICIT_NULL             = 0x3000;
    private static final Integer MARK_POLL_NEAR                 = 0x3001;
    private static final Integer MARK_POLL_RETURN_NEAR          = 0x3002;
    private static final Integer MARK_POLL_FAR                  = 0x3003;
    private static final Integer MARK_POLL_RETURN_FAR           = 0x3004;

    private static final Integer MARK_KLASS_PATCHING            = 0x4000;
    private static final Integer MARK_DUMMY_OOP_RELOCATION      = 0x4001;
    private static final Integer MARK_ACCESS_FIELD_PATCHING     = 0x4002;
    // @formatter:on

    private final HotSpotVMConfig config;
    private final CiTarget target;
    private final RiRegisterConfig registerConfig;
    private final Compiler compiler;

    private CiXirAssembler globalAsm;

    public HotSpotXirGenerator(HotSpotVMConfig config, CiTarget target, RiRegisterConfig registerConfig, Compiler compiler) {
        this.config = config;
        this.target = target;
        this.registerConfig = registerConfig;
        this.compiler = compiler;
    }

    private XirConstant wordConst(CiXirAssembler asm, long value) {
        if (target.wordKind == CiKind.Long) {
            return asm.createConstant(CiConstant.forLong(value));
        } else {
            assert target.wordKind == CiKind.Int;
            return asm.createConstant(CiConstant.forInt((int) value));
        }
    }

    private XirArgument wordArg(long value) {
        if (target.wordKind == CiKind.Long) {
            return XirArgument.forLong(value);
        } else {
            assert target.wordKind == CiKind.Int;
            return XirArgument.forInt((int) value);
        }
    }

    private SimpleTemplates prologueTemplates = new SimpleTemplates(STATIC_METHOD) {

        @Override
        protected XirTemplate create(CiXirAssembler asm, long flags) {
            asm.restart(CiKind.Void);
            XirOperand framePointer = asm.createRegisterTemp("frame pointer", target.wordKind, AMD64.rbp);
            XirOperand stackPointer = asm.createRegisterTemp("stack pointer", target.wordKind, AMD64.rsp);
            XirLabel unverifiedStub = null;

            asm.mark(MARK_OSR_ENTRY);
            asm.mark(MARK_UNVERIFIED_ENTRY);
            if (!is(STATIC_METHOD, flags)) {
                unverifiedStub = asm.createOutOfLineLabel("unverified");

                XirOperand temp = asm.createRegisterTemp("temp (r10)", target.wordKind, AMD64.r10);
                XirOperand cache = asm.createRegisterTemp("cache (rax)", target.wordKind, AMD64.rax);

                CiCallingConvention conventions = registerConfig.getCallingConvention(JavaCallee, new CiKind[] {CiKind.Object}, target, false);
                XirOperand receiver = asm.createRegisterTemp("receiver", target.wordKind, conventions.locations[0].asRegister());

                asm.pload(target.wordKind, temp, receiver, asm.i(config.hubOffset), false);
                asm.jneq(unverifiedStub, cache, temp);
            }
            asm.align(config.codeEntryAlignment);
            asm.mark(MARK_VERIFIED_ENTRY);
            asm.stackOverflowCheck();
            asm.push(framePointer);
            asm.mov(framePointer, stackPointer);
            asm.pushFrame();

            // -- out of line -------------------------------------------------------
            XirOperand thread = asm.createRegisterTemp("thread", target.wordKind, AMD64.r15);
            XirOperand exceptionOop = asm.createTemp("exception oop", CiKind.Object);
            XirLabel unwind = asm.createOutOfLineLabel("unwind");
            asm.bindOutOfLine(unwind);

            asm.mark(MARK_UNWIND_ENTRY);

            asm.pload(CiKind.Object, exceptionOop, thread, asm.i(config.threadExceptionOopOffset), false);
            asm.pstore(CiKind.Object, thread, asm.i(config.threadExceptionOopOffset), asm.createConstant(CiConstant.NULL_OBJECT), false);
            asm.pstore(CiKind.Long, thread, asm.i(config.threadExceptionPcOffset), asm.l(0), false);

            asm.callRuntime(config.unwindExceptionStub, null, exceptionOop);
            asm.shouldNotReachHere();

            asm.mark(MARK_EXCEPTION_HANDLER_ENTRY);
            asm.callRuntime(config.handleExceptionStub, null);
            asm.shouldNotReachHere();

            asm.mark(MARK_DEOPT_HANDLER_ENTRY);
            asm.callRuntime(config.handleDeoptStub, null);
            asm.shouldNotReachHere();

            if (!is(STATIC_METHOD, flags)) {
                asm.bindOutOfLine(unverifiedStub);
                asm.jmpRuntime(config.inlineCacheMissStub);
            }

            return asm.finishTemplate(is(STATIC_METHOD, flags) ? "static prologue" : "prologue");
        }
    };

    private SimpleTemplates epilogueTemplates = new SimpleTemplates(STATIC_METHOD, SYNCHRONIZED) {

        @Override
        protected XirTemplate create(CiXirAssembler asm, long flags) {
            asm.restart(CiKind.Void);
            XirOperand framePointer = asm.createRegisterTemp("frame pointer", target.wordKind, AMD64.rbp);

            asm.popFrame();
            asm.pop(framePointer);

            if (GraalOptions.GenSafepoints) {
                XirOperand temp = asm.createRegisterTemp("temp", target.wordKind, AMD64.r10);
                if (config.isPollingPageFar) {
                    asm.mov(temp, wordConst(asm, config.safepointPollingAddress));
                    asm.mark(MARK_POLL_RETURN_FAR);
                    asm.pload(target.wordKind, temp, temp, true);
                } else {
                    XirOperand rip = asm.createRegister("rip", target.wordKind, AMD64.rip);
                    asm.mark(MARK_POLL_RETURN_NEAR);
                    asm.pload(target.wordKind, temp, rip, asm.i(0xEFBEADDE), true);
                }
            }

            return asm.finishTemplate("epilogue");
        }
    };

    private SimpleTemplates safepointTemplates = new SimpleTemplates() {

        @Override
        protected XirTemplate create(CiXirAssembler asm, long flags) {
            asm.restart(CiKind.Void);

            XirOperand temp = asm.createRegisterTemp("temp", target.wordKind, AMD64.r10);
            if (config.isPollingPageFar) {
                asm.mov(temp, wordConst(asm, config.safepointPollingAddress));
                asm.mark(MARK_POLL_FAR);
                asm.pload(target.wordKind, temp, temp, true);
            } else {
                XirOperand rip = asm.createRegister("rip", target.wordKind, AMD64.rip);
                asm.mark(MARK_POLL_NEAR);
                asm.pload(target.wordKind, temp, rip, asm.i(0xEFBEADDE), true);
            }

            return asm.finishTemplate("safepoint");
        }
    };

    private SimpleTemplates exceptionObjectTemplates = new SimpleTemplates() {

        @Override
        protected XirTemplate create(CiXirAssembler asm, long flags) {
            XirOperand result = asm.restart(CiKind.Object);
            XirOperand thread = asm.createRegisterTemp("thread", target.wordKind, AMD64.r15);

            asm.pload(CiKind.Object, result, thread, asm.i(config.threadExceptionOopOffset), false);
            asm.pstore(CiKind.Object, thread, asm.i(config.threadExceptionOopOffset), asm.o(null), false);
            asm.pstore(CiKind.Long, thread, asm.i(config.threadExceptionPcOffset), asm.l(0), false);

            return asm.finishTemplate("exception object");
        }
    };

    private SimpleTemplates invokeInterfaceTemplates = new SimpleTemplates(NULL_CHECK) {

        @Override
        protected XirTemplate create(CiXirAssembler asm, long flags) {
            asm.restart();
            XirParameter receiver = asm.createInputParameter("receiver", CiKind.Object);
            XirParameter addr = asm.createConstantInputParameter("addr", target.wordKind);
            XirOperand temp = asm.createRegisterTemp("temp", target.wordKind, AMD64.rax);
            XirOperand tempO = asm.createRegisterTemp("tempO", CiKind.Object, AMD64.rax);

            if (is(NULL_CHECK, flags)) {
                asm.mark(MARK_IMPLICIT_NULL);
                asm.pload(target.wordKind, temp, receiver, true);
            }
            asm.mark(MARK_INVOKEINTERFACE);
            asm.mov(tempO, asm.createConstant(CiConstant.forObject(HotSpotProxy.DUMMY_CONSTANT_OBJ)));

            return asm.finishTemplate(addr, "invokeinterface");
        }
    };

    private SimpleTemplates invokeVirtualTemplates = new SimpleTemplates(NULL_CHECK) {

        @Override
        protected XirTemplate create(CiXirAssembler asm, long flags) {
            asm.restart();
            XirParameter receiver = asm.createInputParameter("receiver", CiKind.Object);
            XirParameter addr = asm.createConstantInputParameter("addr", target.wordKind);
            XirOperand temp = asm.createRegisterTemp("temp", target.wordKind, AMD64.rax);
            XirOperand tempO = asm.createRegisterTemp("tempO", CiKind.Object, AMD64.rax);

            if (is(NULL_CHECK, flags)) {
                asm.mark(MARK_IMPLICIT_NULL);
                asm.pload(target.wordKind, temp, receiver, true);
            }
            asm.mark(MARK_INVOKEVIRTUAL);
            asm.mov(tempO, asm.createConstant(CiConstant.forObject(HotSpotProxy.DUMMY_CONSTANT_OBJ)));

            return asm.finishTemplate(addr, "invokevirtual");
        }
    };

    private SimpleTemplates invokeSpecialTemplates = new SimpleTemplates(NULL_CHECK) {

        @Override
        protected XirTemplate create(CiXirAssembler asm, long flags) {
            asm.restart();
            XirParameter receiver = asm.createInputParameter("receiver", CiKind.Object);
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
        protected XirTemplate create(CiXirAssembler asm, long flags) {
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
        protected XirTemplate create(CiXirAssembler asm, long flags) {
            asm.restart(CiKind.Void);
            XirParameter object = asm.createInputParameter("object", CiKind.Object);
            XirParameter lock = asm.createInputParameter("lock", target.wordKind);

            if (is(NULL_CHECK, flags)) {
                asm.mark(MARK_IMPLICIT_NULL);
                asm.pload(target.wordKind, asm.createTemp("temp", target.wordKind), object, true);
            }


            // (tw) It is important to use for this runtime call the debug info AFTER the monitor enter. Otherwise the monitor object
            // is not correctly garbage collected.
            final boolean useInfoAfter = true;

            if (config.useFastLocking) {
                useRegisters(asm, AMD64.rax, AMD64.rbx);
                useRegisters(asm, getGeneralParameterRegister(0));
                useRegisters(asm, getGeneralParameterRegister(1));
                asm.callRuntime(config.fastMonitorEnterStub, null, useInfoAfter, object, lock);
            } else {
                asm.reserveOutgoingStack(target.wordSize * 2);
                XirOperand rsp = asm.createRegister("rsp", target.wordKind, AMD64.RSP.asRegister());
                asm.pstore(CiKind.Object, rsp, asm.i(target.wordSize), object, false);
                asm.pstore(target.wordKind, rsp, asm.i(0), lock, false);
                asm.callRuntime(config.monitorEnterStub, null, useInfoAfter);
            }

            return asm.finishTemplate("monitorEnter");
        }
    };

    private CiRegister getGeneralParameterRegister(int index) {
        return registerConfig.getCallingConventionRegisters(CiCallingConvention.Type.RuntimeCall, RegisterFlag.CPU)[index];
    }

    private SimpleTemplates monitorExitTemplates = new SimpleTemplates(NULL_CHECK) {

        @Override
        protected XirTemplate create(CiXirAssembler asm, long flags) {
            asm.restart(CiKind.Void);
            XirParameter object = asm.createInputParameter("object", CiKind.Object);
            XirParameter lock = asm.createInputParameter("lock", target.wordKind);

            if (config.useFastLocking) {
                useRegisters(asm, AMD64.rax, AMD64.rbx);
                useRegisters(asm, getGeneralParameterRegister(0));
                useRegisters(asm, getGeneralParameterRegister(1));
                asm.callRuntime(config.fastMonitorExitStub, null, object, lock);
            } else {
                asm.reserveOutgoingStack(target.wordSize);
                asm.pstore(target.wordKind, asm.createRegister("rsp", target.wordKind, AMD64.RSP.asRegister()), asm.i(0), lock, false);
                asm.callRuntime(config.monitorExitStub, null);
            }

            return asm.finishTemplate("monitorExit");
        }
    };

    private KindTemplates getFieldTemplates = new KindTemplates(NULL_CHECK) {

        @Override
        protected XirTemplate create(CiXirAssembler asm, long flags, CiKind kind) {
            XirOperand result = asm.restart(kind);
            XirParameter object = asm.createInputParameter("object", CiKind.Object);

            XirParameter fieldOffset = asm.createConstantInputParameter("fieldOffset", CiKind.Int);
            if (is(NULL_CHECK, flags)) {
                asm.mark(MARK_IMPLICIT_NULL);
            }
            asm.pload(kind, result, object, fieldOffset, is(NULL_CHECK, flags));
            return asm.finishTemplate("getfield<" + kind + ">");
        }
    };

    private KindTemplates writeBarrierTemplate = new KindTemplates() {

        @Override
        protected XirTemplate create(CiXirAssembler asm, long flags, CiKind kind) {
            asm.restart(CiKind.Void);
            XirParameter object = asm.createInputParameter("object", CiKind.Object);

            // Need temp operand, because the write barrier destroys the object pointer.
            XirOperand temp = asm.createTemp("temp", target.wordKind);
            asm.mov(temp, object);

            writeBarrier(asm, temp);
            return asm.finishTemplate("writeBarrier");
        }
    };

    private KindTemplates putFieldTemplates = new KindTemplates(WRITE_BARRIER, NULL_CHECK) {

        @Override
        protected XirTemplate create(CiXirAssembler asm, long flags, CiKind kind) {
            asm.restart(CiKind.Void);
            XirParameter object = asm.createInputParameter("object", CiKind.Object);
            XirParameter value = asm.createInputParameter("value", kind);
            XirParameter fieldOffset = asm.createConstantInputParameter("fieldOffset", CiKind.Int);
            if (kind == CiKind.Object) {
                verifyPointer(asm, value);
            }
            if (is(NULL_CHECK, flags)) {
                asm.mark(MARK_IMPLICIT_NULL);
            }
            asm.pstore(kind, object, fieldOffset, value, is(NULL_CHECK, flags));
            if (is(WRITE_BARRIER, flags) && kind == CiKind.Object) {
                XirOperand temp = asm.createTemp("temp", target.wordKind);
                asm.mov(temp, object);
                writeBarrier(asm, temp);
            }
            return asm.finishTemplate("putfield<" + kind + ">");
        }
    };

    private final IndexTemplates newInstanceTemplates = new IndexTemplates() {

        @Override
        protected XirTemplate create(CiXirAssembler asm, long flags, int size) {
            XirOperand result = asm.restart(target.wordKind);
            XirOperand type = asm.createInputParameter("type", CiKind.Object);

            XirOperand temp1 = asm.createRegisterTemp("temp1", target.wordKind, AMD64.rcx);
            XirOperand temp1o = asm.createRegisterTemp("temp1o", CiKind.Object, AMD64.rcx);
            XirOperand temp2 = asm.createRegisterTemp("temp2", target.wordKind, AMD64.rbx);
            XirOperand temp2i = asm.createRegisterTemp("temp2i", CiKind.Int, AMD64.rbx);
            useRegisters(asm, AMD64.rsi);
            XirLabel tlabFull = asm.createOutOfLineLabel("tlab full");
            XirLabel resume = asm.createInlineLabel("resume");

            // check if the class is already initialized
            asm.pload(CiKind.Int, temp2i, type, asm.i(config.klassStateOffset), false);
            asm.jneq(tlabFull, temp2i, asm.i(config.klassStateFullyInitialized));

            XirOperand thread = asm.createRegisterTemp("thread", target.wordKind, AMD64.r15);
            asm.pload(target.wordKind, result, thread, asm.i(config.threadTlabTopOffset), false);
            asm.add(temp1, result, wordConst(asm, size));
            asm.pload(target.wordKind, temp2, thread, asm.i(config.threadTlabEndOffset), false);

            asm.jgt(tlabFull, temp1, temp2);
            asm.pstore(target.wordKind, thread, asm.i(config.threadTlabTopOffset), temp1, false);

            asm.bindInline(resume);

            asm.pload(target.wordKind, temp1, type, asm.i(config.instanceHeaderPrototypeOffset), false);
            asm.pstore(target.wordKind, result, temp1, false);
            asm.mov(temp1o, type); // need a temporary register since Intel cannot store 64-bit constants to memory
            asm.pstore(CiKind.Object, result, asm.i(config.hubOffset), temp1o, false);

            if (size > 2 * target.wordSize) {
                asm.mov(temp1, wordConst(asm, 0));
                for (int offset = 2 * target.wordSize; offset < size; offset += target.wordSize) {
                    asm.pstore(target.wordKind, result, asm.i(offset), temp1, false);
                }
            }

            // -- out of line -------------------------------------------------------
            asm.bindOutOfLine(tlabFull);
            XirOperand arg = asm.createRegisterTemp("runtime call argument", CiKind.Object, AMD64.rdx);
            asm.mov(arg, type);
            useRegisters(asm, AMD64.rax);
            asm.callRuntime(config.newInstanceStub, result);
            asm.jmp(resume);

            return asm.finishTemplate("new instance");
        }
    };

    private SimpleTemplates newObjectArrayCloneTemplates = new SimpleTemplates() {

        @Override
        protected XirTemplate create(CiXirAssembler asm, long flags) {
            XirOperand result = asm.restart(CiKind.Object);
            XirParameter lengthParam = asm.createInputParameter("length", CiKind.Int, true);
            XirParameter src = asm.createInputParameter("src", CiKind.Object);

            // Set up length and hub.
            XirOperand length = asm.createRegisterTemp("length", CiKind.Int, AMD64.rbx);
            XirOperand hub = asm.createRegisterTemp("hub", CiKind.Object, AMD64.rdx);
            asm.pload(CiKind.Object, hub, src, asm.i(config.hubOffset), false);
            asm.mov(length, lengthParam);

            useRegisters(asm, AMD64.rsi, AMD64.rcx, AMD64.rdi, AMD64.rax);
            asm.callRuntime(config.newObjectArrayStub, result);
            return asm.finishTemplate("objectArrayClone");
        }
    };

    private SimpleTemplates newObjectArrayTemplates = new SimpleTemplates() {

        @Override
        protected XirTemplate create(CiXirAssembler asm, long flags) {
            emitNewTypeArray(asm, flags, CiKind.Object, config.useFastNewObjectArray, config.newObjectArrayStub);
            return asm.finishTemplate("newObjectArray");
        }
    };

    private void emitNewTypeArray(CiXirAssembler asm, long flags, CiKind kind, boolean useFast, long slowPathStub) {
        XirOperand result = asm.restart(target.wordKind);

        XirParameter lengthParam = asm.createInputParameter("length", CiKind.Int, true);

        XirOperand length = asm.createRegisterTemp("length", CiKind.Int, AMD64.rbx);
        XirOperand hub = asm.createRegisterTemp("hub", CiKind.Object, AMD64.rdx);

        // Registers rsi, rcx, rdi, and rax are needed by the runtime call.
        // Hub needs to be on rdx, length on rbx.
        XirOperand temp1 = asm.createRegisterTemp("temp1", target.wordKind, AMD64.rcx);
        XirOperand temp1o = asm.createRegisterTemp("temp1o", CiKind.Object, AMD64.rcx);
        XirOperand temp2 = asm.createRegisterTemp("temp2", target.wordKind, AMD64.rax);
        XirOperand temp3 = asm.createRegisterTemp("temp3", target.wordKind, AMD64.rdi);
        XirOperand size = asm.createRegisterTemp("size", CiKind.Int, AMD64.rsi);

        asm.mov(hub, asm.createConstantInputParameter("hub", CiKind.Object));
        asm.mov(length, lengthParam);

        if (useFast) {

            XirLabel slowPath = asm.createOutOfLineLabel("slowPath");

            XirLabel done = asm.createInlineLabel("done");

            // Check for negative array size.
            // TODO: Also check for upper bound
            asm.jlt(slowPath, length, asm.i(0));

            final int aligning = target.wordSize;
            final int arrayLengthOffset = target.wordSize * 2;
            final int arrayElementOffset = config.getArrayOffset(kind);

            // Calculate aligned size
            asm.mov(size, length);
            int scale = CiUtil.log2(target.sizeInBytes(kind));
            if (scale != 0) {
                asm.shl(size, size, asm.i(scale));
            }
            asm.add(size, size, asm.i(arrayElementOffset + aligning - 1));
            long mask = 0xFFFFFFFFL;
            mask <<= CiUtil.log2(aligning);
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
            asm.pstore(target.wordKind, result, temp1, false);
            asm.mov(temp1o, hub); // need a temporary register since Intel cannot store 64-bit constants to memory
            asm.pstore(CiKind.Object, result, asm.i(config.hubOffset), temp1o, false);

            // Store array length
            asm.pstore(CiKind.Int, result, asm.i(arrayLengthOffset), length, false);

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
        protected XirTemplate create(CiXirAssembler asm, long flags, CiKind kind) {
            emitNewTypeArray(asm, flags, kind, config.useFastNewTypeArray, config.newTypeArrayStub);
            return asm.finishTemplate("newTypeArray<" + kind.toString() + ">");
        }
    };

    private final IndexTemplates multiNewArrayTemplate = new IndexTemplates() {

        @Override
        protected XirTemplate create(CiXirAssembler asm, long flags, int dimensions) {
            XirOperand result = asm.restart(CiKind.Object);

            XirOperand hub = asm.createRegisterTemp("hub", CiKind.Object, AMD64.rax);
            XirOperand rank = asm.createRegisterTemp("rank", CiKind.Int, AMD64.rbx);
            XirOperand sizes = asm.createRegisterTemp("sizes", CiKind.Long, AMD64.rcx);
            XirOperand thread = asm.createRegisterTemp("thread", CiKind.Long, AMD64.r15);
            asm.add(sizes, thread, asm.l(config.threadMultiNewArrayStorage));
            for (int i = 0; i < dimensions; i++) {
                XirParameter length = asm.createInputParameter("length" + i, CiKind.Int, true);
                asm.pstore(CiKind.Int, sizes, asm.i(i * target.sizeInBytes(CiKind.Int)), length, false);
            }

            asm.mov(hub, asm.createConstantInputParameter("hub", CiKind.Object));

            asm.mov(rank, asm.i(dimensions));
            useRegisters(asm, AMD64.rax);
            asm.callRuntime(config.newMultiArrayStub, result);
            return asm.finishTemplate("multiNewArray" + dimensions);
        }
    };

    private SimpleTemplates checkCastTemplates = new SimpleTemplates(NULL_CHECK) {

        @Override
        protected XirTemplate create(CiXirAssembler asm, long flags) {
            asm.restart();
            XirParameter object = asm.createInputParameter("object", CiKind.Object);
            final XirOperand hub;
            hub = asm.createConstantInputParameter("hub", CiKind.Object);

            XirOperand objHub = asm.createTemp("objHub", CiKind.Object);

            XirLabel end = asm.createInlineLabel("end");
            XirLabel slowPath = asm.createOutOfLineLabel("slow path");

            if (is(NULL_CHECK, flags)) {
                // null can be cast to anything
                asm.jeq(end, object, asm.o(null));
            }

            asm.pload(CiKind.Object, objHub, object, asm.i(config.hubOffset), false);
            // if we get an exact match: succeed immediately
            asm.jneq(slowPath, objHub, hub);
            asm.bindInline(end);

            // -- out of line -------------------------------------------------------
            asm.bindOutOfLine(slowPath);
            checkSubtype(asm, objHub, objHub, hub);
            asm.jneq(end, objHub, asm.o(null));
            XirOperand scratch = asm.createRegisterTemp("scratch", target.wordKind, AMD64.r10);
            asm.mov(scratch, wordConst(asm, 2));

            asm.callRuntime(CiRuntimeCall.Deoptimize, null);
            asm.shouldNotReachHere();

            return asm.finishTemplate(object, "checkcast");
        }
    };

    private SimpleTemplates instanceOfTemplates = new SimpleTemplates(NULL_CHECK) {

        @Override
        protected XirTemplate create(CiXirAssembler asm, long flags) {
            asm.restart(CiKind.Void);
            XirParameter object = asm.createInputParameter("object", CiKind.Object);
            final XirOperand hub;
            hub = asm.createConstantInputParameter("hub", CiKind.Object);

            XirOperand objHub = asm.createTemp("objHub", CiKind.Object);

            XirLabel slowPath = asm.createOutOfLineLabel("slow path");
            XirLabel trueSucc = asm.createInlineLabel(XirLabel.TrueSuccessor);
            XirLabel falseSucc = asm.createInlineLabel(XirLabel.FalseSuccessor);

            if (is(NULL_CHECK, flags)) {
                // null isn't "instanceof" anything
                asm.jeq(falseSucc, object, asm.o(null));
            }

            asm.pload(CiKind.Object, objHub, object, asm.i(config.hubOffset), false);
            // if we get an exact match: succeed immediately
            asm.jeq(trueSucc, objHub, hub);
            asm.jmp(slowPath);

            // -- out of line -------------------------------------------------------
            asm.bindOutOfLine(slowPath);
            checkSubtype(asm, objHub, objHub, hub);
            asm.jeq(falseSucc, objHub, asm.o(null));
            asm.jmp(trueSucc);

            return asm.finishTemplate("instanceof");
        }
    };

    private SimpleTemplates materializeInstanceOfTemplates = new SimpleTemplates(NULL_CHECK) {

        @Override
        protected XirTemplate create(CiXirAssembler asm, long flags) {
            XirOperand result = asm.restart(CiKind.Int);
            XirParameter object = asm.createInputParameter("object", CiKind.Object);
            final XirOperand hub;
            hub = asm.createConstantInputParameter("hub", CiKind.Object);
            XirOperand trueValue = asm.createConstantInputParameter("trueValue", CiKind.Int);
            XirOperand falseValue = asm.createConstantInputParameter("falseValue", CiKind.Int);

            XirOperand objHub = asm.createTemp("objHub", CiKind.Object);

            XirLabel slowPath = asm.createOutOfLineLabel("slow path");
            XirLabel trueSucc = asm.createInlineLabel("ok");
            XirLabel falseSucc = asm.createInlineLabel("ko");
            XirLabel end = asm.createInlineLabel("end");

            if (is(NULL_CHECK, flags)) {
                // null isn't "instanceof" anything
                asm.jeq(falseSucc, object, asm.o(null));
            }

            asm.pload(CiKind.Object, objHub, object, asm.i(config.hubOffset), false);
            // if we get an exact match: succeed immediately
            asm.jeq(trueSucc, objHub, hub);
            asm.jmp(slowPath);

            asm.bindInline(trueSucc);
            asm.mov(result, trueValue);
            asm.jmp(end);
            asm.bindInline(falseSucc);
            asm.mov(result, falseValue);
            asm.bindInline(end);

            // -- out of line -------------------------------------------------------
            asm.bindOutOfLine(slowPath);
            checkSubtype(asm, objHub, objHub, hub);
            asm.jeq(falseSucc, objHub, asm.o(null));
            asm.jmp(trueSucc);

            return asm.finishTemplate("instanceof");
        }
    };

    private XirOperand genArrayLength(CiXirAssembler asm, XirOperand array, boolean implicitNullException) {
        XirOperand length = asm.createTemp("length", CiKind.Int);
        genArrayLength(asm, length, array, implicitNullException);
        return length;
    }

    private void genArrayLength(CiXirAssembler asm, XirOperand length, XirOperand array, boolean implicitNullException) {
        if (implicitNullException) {
            asm.mark(MARK_IMPLICIT_NULL);
        }
        asm.pload(CiKind.Int, length, array, asm.i(config.arrayLengthOffset), implicitNullException);
    }

    private KindTemplates arrayLoadTemplates = new KindTemplates(NULL_CHECK, READ_BARRIER, BOUNDS_CHECK, GIVEN_LENGTH) {

        @Override
        protected XirTemplate create(CiXirAssembler asm, long flags, CiKind kind) {
            XirOperand result = asm.restart(kind);
            XirParameter array = asm.createInputParameter("array", CiKind.Object);
            XirParameter index = asm.createInputParameter("index", CiKind.Int, true);
            XirLabel failBoundsCheck = null;
            // if the length is known the array cannot be null
            boolean implicitNullException = is(NULL_CHECK, flags);

            if (is(BOUNDS_CHECK, flags)) {
                // load the array length and check the index
                failBoundsCheck = asm.createOutOfLineLabel("failBoundsCheck");
                XirOperand length;
                if (is(GIVEN_LENGTH, flags)) {
                    length = asm.createInputParameter("length", CiKind.Int, true);
                } else {
                    length = genArrayLength(asm, array, implicitNullException);
                }
                asm.jugteq(failBoundsCheck, index, length);
                implicitNullException = false;
            }
            int elemSize = target.sizeInBytes(kind);
            if (implicitNullException) {
                asm.mark(MARK_IMPLICIT_NULL);
            }
            asm.pload(kind, result, array, index, config.getArrayOffset(kind), Scale.fromInt(elemSize), implicitNullException);
            if (is(BOUNDS_CHECK, flags)) {
                asm.bindOutOfLine(failBoundsCheck);
                XirOperand scratch = asm.createRegisterTemp("scratch", target.wordKind, AMD64.r10);
                asm.mov(scratch, wordConst(asm, 0));
                asm.callRuntime(CiRuntimeCall.Deoptimize, null);
                asm.shouldNotReachHere();
            }
            return asm.finishTemplate("arrayload<" + kind + ">");
        }
    };

    private SimpleTemplates getClassTemplates = new SimpleTemplates() {
       @Override
       protected XirTemplate create(CiXirAssembler asm, long flags) {
           XirOperand result = asm.restart(CiKind.Object);
           XirOperand object = asm.createInputParameter("object", CiKind.Object);
           asm.pload(CiKind.Object, result, object, asm.i(config.hubOffset), is(NULL_CHECK, flags));
           asm.pload(CiKind.Object, result, result, asm.i(config.classMirrorOffset), false);
           return asm.finishTemplate("getClass");
       }
    };

    private SimpleTemplates currentThreadTemplates = new SimpleTemplates() {
       @Override
       protected XirTemplate create(CiXirAssembler asm, long flags) {
           XirOperand result = asm.restart(CiKind.Object);
           XirOperand thread = asm.createRegisterTemp("thread", target.wordKind, AMD64.r15);
           asm.pload(CiKind.Object, result, thread, asm.i(config.threadObjectOffset), false);
           return asm.finishTemplate("currentThread");
       }
    };

    @Override
    public XirSnippet genCurrentThread(XirSite site) {
        return new XirSnippet(currentThreadTemplates.get(site));
    }

    @Override
    public XirSnippet genGetClass(XirSite site, XirArgument object) {
        return new XirSnippet(getClassTemplates.get(site), object);
    }

    private KindTemplates arrayCopyTemplates = new KindTemplates() {

        @Override
        protected XirTemplate create(CiXirAssembler asm, long flags, CiKind kind) {
            asm.restart(CiKind.Void);
            XirParameter src = asm.createInputParameter("src", CiKind.Object);
            XirParameter srcPos = asm.createInputParameter("srcPos", CiKind.Int, true);
            XirParameter dest = asm.createInputParameter("dest", CiKind.Object);
            XirParameter destPos = asm.createInputParameter("destPos", CiKind.Int, true);
            XirParameter length = asm.createInputParameter("length", CiKind.Int, true);

            XirOperand tempSrc = asm.createTemp("tempSrc", target.wordKind);
            XirOperand tempDest = asm.createTemp("tempDest", target.wordKind);
            XirOperand lengthOperand = asm.createRegisterTemp("lengthOperand", CiKind.Int, AMD64.rax);

            XirOperand compHub = null;
            XirOperand valueHub = null;
            XirOperand temp = null;
            XirLabel store = null;
            XirLabel slowStoreCheck = null;

            if (is(STORE_CHECK, flags) && kind == CiKind.Object) {
                valueHub = asm.createRegisterTemp("valueHub", target.wordKind, AMD64.rdi);
                compHub = asm.createRegisterTemp("compHub", target.wordKind, AMD64.rsi);
                temp = asm.createRegisterTemp("temp", target.wordKind, AMD64.r10);
            }

            // Calculate the factor for the repeat move instruction.
            int elementSize = target.sizeInBytes(kind);
            int factor;
            boolean wordSize;
            if (elementSize >= target.wordSize) {
                assert elementSize % target.wordSize == 0;
                wordSize = true;
                factor = elementSize / target.wordSize;
            } else {
                factor = elementSize;
                wordSize = false;
            }

            // Adjust the length if the factor is not 1.
            if (factor != 1) {
                asm.shl(lengthOperand, length, asm.i(CiUtil.log2(factor)));
            } else {
                asm.mov(lengthOperand, length);
            }

            // Set the start and the end pointer.
            asm.lea(tempSrc, src, srcPos, config.getArrayOffset(kind), Scale.fromInt(elementSize));
            asm.lea(tempDest, dest, destPos, config.getArrayOffset(kind), Scale.fromInt(elementSize));

            XirLabel reverse = null;
            XirLabel normal = null;

            if (is(STORE_CHECK, flags)) {
                reverse = asm.createInlineLabel("reverse");
                asm.jneq(reverse, src, dest);
            }

            if (!is(STORE_CHECK, flags) && !is(INPUTS_DIFFERENT, flags) && !is(INPUTS_SAME, flags)) {
                normal = asm.createInlineLabel("normal");
                asm.jneq(normal, src, dest);
            }

            if (!is(INPUTS_DIFFERENT, flags)) {
                if (reverse == null) {
                    reverse = asm.createInlineLabel("reverse");
                }
                asm.jlt(reverse, srcPos, destPos);
            }

            if (!is(STORE_CHECK, flags) && !is(INPUTS_DIFFERENT, flags) && !is(INPUTS_SAME, flags)) {
                asm.bindInline(normal);
            }

            // Everything set up => repeat mov.
            if (wordSize) {
                asm.repmov(tempSrc, tempDest, lengthOperand);
            } else {
                asm.repmovb(tempSrc, tempDest, lengthOperand);
            }

            if (!is(INPUTS_DIFFERENT, flags) || is(STORE_CHECK, flags)) {

                XirLabel end = asm.createInlineLabel("end");
                asm.jmp(end);

                // Implement reverse copy, because srcPos < destPos and src == dest.
                asm.bindInline(reverse);

                if (is(STORE_CHECK, flags)) {
                    asm.pload(CiKind.Object, compHub, dest, asm.i(config.hubOffset), false);
                    asm.pload(CiKind.Object, compHub, compHub, asm.i(config.arrayClassElementOffset), false);
                }

                CiKind copyKind = wordSize ? CiKind.Object : CiKind.Byte;
                XirOperand tempValue = asm.createTemp("tempValue", copyKind);
                XirLabel start = asm.createInlineLabel("start");
                asm.bindInline(start);
                asm.sub(lengthOperand, lengthOperand, asm.i(1));
                asm.jlt(end, lengthOperand, asm.i(0));

                Scale scale = wordSize ? Scale.fromInt(target.wordSize) : Scale.Times1;
                asm.pload(copyKind, tempValue, tempSrc, lengthOperand, 0, scale, false);

                if (is(STORE_CHECK, flags)) {
                    slowStoreCheck = asm.createOutOfLineLabel("slowStoreCheck");
                    store = asm.createInlineLabel("store");
                    asm.jeq(store, tempValue, asm.o(null)); // first check if value is null
                    asm.pload(CiKind.Object, valueHub, tempValue, asm.i(config.hubOffset), false);
                    asm.jneq(slowStoreCheck, compHub, valueHub); // then check component hub matches value hub
                    asm.bindInline(store);
                }

                asm.pstore(copyKind, tempDest, lengthOperand, tempValue, 0, scale, false);

                asm.jmp(start);
                asm.bindInline(end);
            }

            if (kind == CiKind.Object) {
                // Do write barriers
                asm.lea(tempDest, dest, destPos, config.getArrayOffset(kind), Scale.fromInt(elementSize));
                asm.shr(tempDest, tempDest, asm.i(config.cardtableShift));
                asm.pstore(CiKind.Boolean, wordConst(asm, config.cardtableStartAddress), tempDest, asm.b(false), false);

                XirOperand tempDestEnd = tempSrc; // Reuse src temp
                asm.lea(tempDestEnd, dest, destPos, config.getArrayOffset(kind), Scale.fromInt(elementSize));
                asm.add(tempDestEnd, tempDestEnd, length);
                asm.shr(tempDestEnd, tempDestEnd, asm.i(config.cardtableShift));

                // Jump to out-of-line write barrier loop if the array is big.
                XirLabel writeBarrierLoop = asm.createOutOfLineLabel("writeBarrierLoop");
                asm.jneq(writeBarrierLoop, tempDest, tempSrc);
                XirLabel back = asm.createInlineLabel("back");
                asm.bindInline(back);

                asm.bindOutOfLine(writeBarrierLoop);
                asm.pstore(CiKind.Boolean, wordConst(asm, config.cardtableStartAddress), tempDestEnd, asm.b(false), false);
                asm.sub(tempDestEnd, tempDestEnd, asm.i(1));
                asm.jneq(writeBarrierLoop, tempDestEnd, tempDest);
                asm.jmp(back);
            }

            if (is(STORE_CHECK, flags)) {
                assert kind == CiKind.Object;
                useRegisters(asm, AMD64.rax);
                asm.bindOutOfLine(slowStoreCheck);
                checkSubtype(asm, temp, valueHub, compHub);
                asm.jneq(store, temp, wordConst(asm, 0));
                XirOperand scratch = asm.createRegisterTemp("scratch", target.wordKind, AMD64.r10);
                asm.mov(scratch, wordConst(asm, 0));
                asm.callRuntime(CiRuntimeCall.Deoptimize, null);
                asm.jmp(store);
            }

            return asm.finishTemplate("arraycopy<" + kind + ">");
        }
    };

    private KindTemplates arrayStoreTemplates = new KindTemplates(NULL_CHECK, WRITE_BARRIER, BOUNDS_CHECK, STORE_CHECK, GIVEN_LENGTH) {

        @Override
        protected XirTemplate create(CiXirAssembler asm, long flags, CiKind kind) {
            asm.restart(CiKind.Void);
            XirParameter array = asm.createInputParameter("array", CiKind.Object);
            XirParameter index = asm.createInputParameter("index", CiKind.Int, true);
            XirParameter value = asm.createInputParameter("value", kind, kind != CiKind.Object);
            XirOperand temp = asm.createTemp("temp", target.wordKind);
            XirOperand valueHub = null;
            XirOperand compHub = null;
            XirLabel store = asm.createInlineLabel("store");
            XirLabel failBoundsCheck = null;
            XirLabel slowStoreCheck = null;
            // if the length is known the array cannot be null
            boolean implicitNullException = is(NULL_CHECK, flags);

            if (is(BOUNDS_CHECK, flags)) {
                // load the array length and check the index
                failBoundsCheck = asm.createOutOfLineLabel("failBoundsCheck");
                XirOperand length;
                if (is(GIVEN_LENGTH, flags)) {
                    length = asm.createInputParameter("length", CiKind.Int);
                } else {
                    length = asm.createTemp("length", CiKind.Int);
                    if (implicitNullException) {
                        asm.mark(MARK_IMPLICIT_NULL);
                    }
                    asm.pload(CiKind.Int, length, array, asm.i(config.arrayLengthOffset), implicitNullException);
                    implicitNullException = false;
                }
                asm.jugteq(failBoundsCheck, index, length);

            }
            if (is(STORE_CHECK, flags) && kind == CiKind.Object) {
                slowStoreCheck = asm.createOutOfLineLabel("slowStoreCheck");
                asm.jeq(store, value, asm.o(null)); // first check if value is null
                valueHub = asm.createTemp("valueHub", CiKind.Object);
                compHub = asm.createTemp("compHub", CiKind.Object);
                if (implicitNullException) {
                    asm.mark(MARK_IMPLICIT_NULL);
                }
                asm.pload(CiKind.Object, compHub, array, asm.i(config.hubOffset), implicitNullException);
                asm.pload(CiKind.Object, compHub, compHub, asm.i(config.arrayClassElementOffset), false);
                asm.pload(CiKind.Object, valueHub, value, asm.i(config.hubOffset), false);
                asm.jneq(slowStoreCheck, compHub, valueHub); // then check component hub matches value hub

                implicitNullException = false;
            }
            asm.bindInline(store);
            int elemSize = target.sizeInBytes(kind);

            if (implicitNullException) {
                asm.mark(MARK_IMPLICIT_NULL);
            }
            int disp = config.getArrayOffset(kind);
            Scale scale = Scale.fromInt(elemSize);
            if (kind == CiKind.Object) {
                verifyPointer(asm, value);
            }
            if (is(WRITE_BARRIER, flags) && kind == CiKind.Object) {
                asm.lea(temp, array, index, disp, scale);
                asm.pstore(kind, temp, value, implicitNullException);
                writeBarrier(asm, temp);
            } else {
                asm.pstore(kind, array, index, value, disp, scale, implicitNullException);
            }

            // -- out of line -------------------------------------------------------
            if (is(BOUNDS_CHECK, flags)) {
                asm.bindOutOfLine(failBoundsCheck);
                XirOperand scratch = asm.createRegisterTemp("scratch", target.wordKind, AMD64.r10);
                asm.mov(scratch, wordConst(asm, 0));
                asm.callRuntime(CiRuntimeCall.Deoptimize, null);
                asm.shouldNotReachHere();
            }
            if (is(STORE_CHECK, flags) && kind == CiKind.Object) {
                useRegisters(asm, AMD64.rax);
                asm.bindOutOfLine(slowStoreCheck);
                checkSubtype(asm, temp, valueHub, compHub);
                asm.jneq(store, temp, wordConst(asm, 0));
                XirOperand scratch = asm.createRegisterTemp("scratch", target.wordKind, AMD64.r10);
                asm.mov(scratch, wordConst(asm, 0));
                asm.callRuntime(CiRuntimeCall.Deoptimize, null);
                asm.shouldNotReachHere();
            }
            return asm.finishTemplate("arraystore<" + kind + ">");
        }
    };

    private SimpleTemplates arrayLengthTemplates = new SimpleTemplates(NULL_CHECK) {

        @Override
        protected XirTemplate create(CiXirAssembler asm, long flags) {
            XirOperand result = asm.restart(CiKind.Int);
            XirParameter object = asm.createInputParameter("object", CiKind.Object);
            if (is(NULL_CHECK, flags)) {
                asm.mark(MARK_IMPLICIT_NULL);
            }
            verifyPointer(asm, object);
            asm.pload(CiKind.Int, result, object, asm.i(config.arrayLengthOffset), true);
            return asm.finishTemplate("arrayLength");
        }
    };

    private SimpleTemplates typeCheckTemplates = new SimpleTemplates(NULL_CHECK) {
       @Override
       protected XirTemplate create(CiXirAssembler asm, long flags) {
           asm.restart();
           XirParameter object = asm.createInputParameter("object", CiKind.Object);
           XirOperand hub = asm.createConstantInputParameter("hub", CiKind.Object);

           XirOperand objHub = asm.createTemp("objHub", CiKind.Object);
           XirOperand checkHub = asm.createTemp("checkHub", CiKind.Object);

           XirLabel slowPath = asm.createOutOfLineLabel("deopt");

           if (is(NULL_CHECK, flags)) {
               asm.mark(MARK_IMPLICIT_NULL);
           }

           asm.pload(CiKind.Object, objHub, object, asm.i(config.hubOffset), false);
           asm.mov(checkHub, hub);
           // if we get an exact match: continue
           asm.jneq(slowPath, objHub, checkHub);

           // -- out of line -------------------------------------------------------
           asm.bindOutOfLine(slowPath);
           XirOperand scratch = asm.createRegisterTemp("scratch", target.wordKind, AMD64.r10);
           asm.mov(scratch, wordConst(asm, 2));

           asm.callRuntime(CiRuntimeCall.Deoptimize, null);
           asm.shouldNotReachHere();

           return asm.finishTemplate(object, "typeCheck");
       }
    };

    @Override
    public XirSnippet genPrologue(XirSite site, RiResolvedMethod method) {
        boolean staticMethod = Modifier.isStatic(method.accessFlags());
        return new XirSnippet(staticMethod ? prologueTemplates.get(site, STATIC_METHOD) : prologueTemplates.get(site));
    }

    @Override
    public XirSnippet genEpilogue(XirSite site, RiResolvedMethod method) {
        return new XirSnippet(epilogueTemplates.get(site));
    }

    @Override
    public XirSnippet genSafepointPoll(XirSite site) {
        return new XirSnippet(safepointTemplates.get(site));
    }

    @Override
    public XirSnippet genExceptionObject(XirSite site) {
        return new XirSnippet(exceptionObjectTemplates.get(site));
    }

    @Override
    public XirSnippet genResolveClass(XirSite site, RiType type, Representation rep) {
        throw new CiBailout("Xir ResolveClass not available");
    }

    @Override
    public XirSnippet genIntrinsic(XirSite site, XirArgument[] arguments, RiMethod method) {
        return null;
    }

    @Override
    public XirSnippet genInvokeInterface(XirSite site, XirArgument receiver, RiMethod method) {
        return new XirSnippet(invokeInterfaceTemplates.get(site), receiver, wordArg(0));
    }

    @Override
    public XirSnippet genInvokeVirtual(XirSite site, XirArgument receiver, RiMethod method) {
        return new XirSnippet(invokeVirtualTemplates.get(site), receiver, wordArg(0));
    }

    @Override
    public XirSnippet genInvokeSpecial(XirSite site, XirArgument receiver, RiMethod method) {
        return new XirSnippet(invokeSpecialTemplates.get(site), receiver, wordArg(0));
    }

    @Override
    public XirSnippet genInvokeStatic(XirSite site, RiMethod method) {
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
    public XirSnippet genGetField(XirSite site, XirArgument object, RiField field) {
        return new XirSnippet(getFieldTemplates.get(site, field.kind(false)), object, XirArgument.forInt(((HotSpotField) field).offset()));
    }

    @Override
    public XirSnippet genWriteBarrier(XirArgument object) {
        return new XirSnippet(writeBarrierTemplate.get(null, CiKind.Void), object);
    }

    @Override
    public XirSnippet genPutField(XirSite site, XirArgument object, RiField field, XirArgument value) {
        return new XirSnippet(putFieldTemplates.get(site, field.kind(false)), object, value, XirArgument.forInt(((HotSpotField) field).offset()));
    }

    @Override
    public XirSnippet genGetStatic(XirSite site, XirArgument object, RiField field) {
        return new XirSnippet(getFieldTemplates.get(site, field.kind(false)), object, XirArgument.forInt(((HotSpotField) field).offset()));
    }

    @Override
    public XirSnippet genPutStatic(XirSite site, XirArgument object, RiField field, XirArgument value) {
        return new XirSnippet(putFieldTemplates.get(site, field.kind(false)), object, value, XirArgument.forInt(((HotSpotField) field).offset()));
    }

    @Override
    public XirSnippet genNewInstance(XirSite site, RiType type) {
        int instanceSize = ((HotSpotTypeResolved) type).instanceSize();
        return new XirSnippet(newInstanceTemplates.get(site, instanceSize), XirArgument.forObject(type));
    }

    @Override
    public XirSnippet genNewArray(XirSite site, XirArgument length, CiKind elementKind, RiType componentType, RiType arrayType) {
        if (elementKind == CiKind.Object) {
            assert arrayType instanceof RiResolvedType;
            return new XirSnippet(newObjectArrayTemplates.get(site), length, XirArgument.forObject(arrayType));
        }
        assert arrayType == null;
        arrayType = compiler.getVMEntries().getPrimitiveArrayType(elementKind);
        return new XirSnippet(newTypeArrayTemplates.get(site, elementKind), length, XirArgument.forObject(arrayType));
    }

    @Override
    public XirSnippet genNewObjectArrayClone(XirSite site, XirArgument newLength, XirArgument referenceArray) {
        return new XirSnippet(newObjectArrayCloneTemplates.get(site), newLength, referenceArray);
    }

    @Override
    public XirSnippet genNewMultiArray(XirSite site, XirArgument[] lengths, RiType type) {
        XirArgument[] params = Arrays.copyOf(lengths, lengths.length + 1);
        params[lengths.length] = XirArgument.forObject(type);
        return new XirSnippet(multiNewArrayTemplate.get(site, lengths.length), params);
    }

    @Override
    public XirSnippet genCheckCast(XirSite site, XirArgument receiver, XirArgument hub, RiType type) {
        return new XirSnippet(checkCastTemplates.get(site), receiver, hub);
    }

    @Override
    public XirSnippet genInstanceOf(XirSite site, XirArgument object, XirArgument hub, RiType type) {
        return new XirSnippet(instanceOfTemplates.get(site), object, hub);
    }

    @Override
    public XirSnippet genMaterializeInstanceOf(XirSite site, XirArgument object, XirArgument hub, XirArgument trueValue, XirArgument falseValue, RiType type) {
        return new XirSnippet(materializeInstanceOfTemplates.get(site), object, hub, trueValue, falseValue);
    }

    @Override
    public XirSnippet genArrayLoad(XirSite site, XirArgument array, XirArgument index, CiKind elementKind, RiType elementType) {
        return new XirSnippet(arrayLoadTemplates.get(site, elementKind), array, index);
    }

    @Override
    public XirSnippet genArrayStore(XirSite site, XirArgument array, XirArgument index, XirArgument value, CiKind elementKind, RiType elementType) {
        return new XirSnippet(arrayStoreTemplates.get(site, elementKind), array, index, value);
    }

    @Override
    public XirSnippet genArrayCopy(XirSite site, XirArgument src, XirArgument srcPos, XirArgument dest, XirArgument destPos, XirArgument length, RiType elementType, boolean inputsSame, boolean inputsDifferent) {
        if (elementType == null) {
            return null;
        }
        assert !inputsDifferent || !inputsSame;
        XirTemplate template = null;
        if (inputsDifferent) {
            template = arrayCopyTemplates.get(site, elementType.kind(true), INPUTS_DIFFERENT);
        } else if (inputsSame) {
            template = arrayCopyTemplates.get(site, elementType.kind(true), INPUTS_SAME);
        } else {
            template = arrayCopyTemplates.get(site, elementType.kind(true));
        }
        return new XirSnippet(template, src, srcPos, dest, destPos, length);
    }

    @Override
    public XirSnippet genArrayLength(XirSite site, XirArgument array) {
        return new XirSnippet(arrayLengthTemplates.get(site), array);
    }

    @Override
    public XirSnippet genTypeCheck(XirSite site, XirArgument object, XirArgument hub, RiType type) {
        assert type instanceof RiResolvedType;
        return new XirSnippet(typeCheckTemplates.get(site), object, hub);
    }

    @Override
    public List<XirTemplate> makeTemplates(CiXirAssembler asm) {
        this.globalAsm = asm;
        List<XirTemplate> templates = new ArrayList<XirTemplate>();
        return templates;
    }

    private void verifyPointer(CiXirAssembler asm, XirOperand pointer) {
        if (config.verifyPointers) {
            // The verify pointer stub wants the argument in a fixed register.
            XirOperand fixed = asm.createRegisterTemp("fixed", CiKind.Object, AMD64.r13);
            asm.push(fixed);
            asm.mov(fixed, pointer);
            asm.callRuntime(config.verifyPointerStub, null);
            asm.pop(fixed);
        }
    }

    private void checkSubtype(CiXirAssembler asm, XirOperand result, XirOperand objHub, XirOperand hub) {
        asm.push(objHub);
        asm.push(hub);
        asm.callRuntime(config.instanceofStub, null);
        asm.pop(result);
        asm.pop(result);
    }

    private void useRegisters(CiXirAssembler asm, CiRegister... registers) {
        if (registers != null) {
            for (CiRegister register : registers) {
                asm.createRegisterTemp("reg", CiKind.Illegal, register);
            }
        }
    }

    private void writeBarrier(CiXirAssembler asm, XirOperand base) {
        asm.shr(base, base, asm.i(config.cardtableShift));
        asm.pstore(CiKind.Boolean, wordConst(asm, config.cardtableStartAddress), base, asm.b(false), false);
    }

    public boolean is(TemplateFlag check, long flags) {
        return (flags & check.bits()) == check.bits();
    }

    /**
     * Base class for all the ondemand template generators. It is not normally subclassed directly, but through one of
     * its subclasses (SimpleTemplates, KindTemplates, IndexTemplates).
     */
    private abstract class Templates {

        private ConcurrentHashMap<Long, XirTemplate> templates = new ConcurrentHashMap<Long, XirTemplate>();
        private final long mask;

        /**
         * Each flag passed to this method will cause templates with and without it to be generated.
         */
        public Templates(TemplateFlag... flags) {
            this.mask = getBits((int) INDEX_MASK, null, flags);
        }

        protected abstract XirTemplate create(CiXirAssembler asm, long flags);

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
            flags = flags & mask;
            XirTemplate template = templates.get(flags);
            if (template == null) {
                template = create(HotSpotXirGenerator.this.globalAsm.copy(), flags);
                templates.put(flags, template);
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
        protected final XirTemplate create(CiXirAssembler asm, long flags) {
            return create(asm, flags & FLAGS_MASK, (int) (flags & INDEX_MASK));
        }

        protected abstract XirTemplate create(CiXirAssembler asm, long flags, int index);

        public XirTemplate get(XirSite site, int size, TemplateFlag... flags) {
            return getInternal(getBits(size, site, flags));
        }
    }

    private abstract class KindTemplates extends Templates {

        public KindTemplates(TemplateFlag... flags) {
            super(flags);
        }

        @Override
        protected final XirTemplate create(CiXirAssembler asm, long flags) {
            return create(asm, flags & FLAGS_MASK, CiKind.VALUES[(int) (flags & INDEX_MASK)]);
        }

        protected abstract XirTemplate create(CiXirAssembler asm, long flags, CiKind kind);

        public XirTemplate get(XirSite site, CiKind kind, TemplateFlag... flags) {
            return getInternal(getBits(kind.ordinal(), site, flags));
        }
    }
}
