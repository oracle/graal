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
package com.oracle.graal.runtime;

import static com.oracle.graal.runtime.TemplateFlag.*;
import static com.sun.cri.ci.CiCallingConvention.Type.*;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;

import com.sun.c1x.target.amd64.*;
import com.sun.cri.ci.CiAddress.Scale;
import com.sun.cri.ci.CiRegister.*;
import com.sun.cri.ci.*;
import com.sun.cri.ri.*;
import com.sun.cri.ri.RiType.Representation;
import com.sun.cri.xir.*;
import com.sun.cri.xir.CiXirAssembler.XirLabel;
import com.sun.cri.xir.CiXirAssembler.XirMark;
import com.sun.cri.xir.CiXirAssembler.XirOperand;
import com.sun.cri.xir.CiXirAssembler.XirParameter;

/**
 *
 * @author Thomas Wuerthinger, Lukas Stadler
 */
public class HotSpotXirGenerator implements RiXirGenerator {

    // this needs to correspond to c1x_CodeInstaller.hpp
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

    private SimpleTemplates prologueTemplates = new SimpleTemplates(STATIC_METHOD) {

        @Override
        protected XirTemplate create(CiXirAssembler asm, long flags) {
            asm.restart(CiKind.Void);
            XirOperand framePointer = asm.createRegisterTemp("frame pointer", CiKind.Word, AMD64.rbp);
            XirOperand stackPointer = asm.createRegisterTemp("stack pointer", CiKind.Word, AMD64.rsp);
            XirLabel unverifiedStub = null;

            asm.mark(MARK_OSR_ENTRY);
            asm.mark(MARK_UNVERIFIED_ENTRY);
            if (!is(STATIC_METHOD, flags)) {
                unverifiedStub = asm.createOutOfLineLabel("unverified");

                XirOperand temp = asm.createRegisterTemp("temp (r10)", CiKind.Word, AMD64.r10);
                XirOperand cache = asm.createRegisterTemp("cache (rax)", CiKind.Word, AMD64.rax);

                CiCallingConvention conventions = registerConfig.getCallingConvention(JavaCallee, new CiKind[] {CiKind.Object}, target);
                XirOperand receiver = asm.createRegisterTemp("receiver", CiKind.Word, conventions.locations[0].asRegister());

                asm.pload(CiKind.Word, temp, receiver, asm.i(config.hubOffset), false);
                asm.jneq(unverifiedStub, cache, temp);
            }
            asm.align(config.codeEntryAlignment);
            asm.mark(MARK_VERIFIED_ENTRY);
            asm.stackOverflowCheck();
            asm.push(framePointer);
            asm.mov(framePointer, stackPointer);
            asm.pushFrame();

            // -- out of line -------------------------------------------------------
            XirOperand thread = asm.createRegisterTemp("thread", CiKind.Word, AMD64.r15);
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

            asm.nop(1);
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
            XirOperand framePointer = asm.createRegisterTemp("frame pointer", CiKind.Word, AMD64.rbp);

            asm.popFrame();
            asm.pop(framePointer);

            // TODO safepoint check

            return asm.finishTemplate("epilogue");
        }
    };

    private SimpleTemplates safepointTemplates = new SimpleTemplates() {

        @Override
        protected XirTemplate create(CiXirAssembler asm, long flags) {
            asm.restart(CiKind.Void);

            // XirOperand temp = asm.createRegister("temp", CiKind.Word, AMD64.rax);
            // asm.pload(CiKind.Word, temp, asm.w(config.safepointPollingAddress), true);

            return asm.finishTemplate("safepoint");
        }
    };

    private SimpleTemplates exceptionObjectTemplates = new SimpleTemplates() {

        @Override
        protected XirTemplate create(CiXirAssembler asm, long flags) {
            XirOperand result = asm.restart(CiKind.Object);
            XirOperand thread = asm.createRegisterTemp("thread", CiKind.Word, AMD64.r15);

            asm.pload(CiKind.Object, result, thread, asm.i(config.threadExceptionOopOffset), false);
            asm.pstore(CiKind.Object, thread, asm.i(config.threadExceptionOopOffset), asm.o(null), false);
            asm.pstore(CiKind.Long, thread, asm.i(config.threadExceptionPcOffset), asm.l(0), false);

            return asm.finishTemplate("exception object");
        }
    };

    private SimpleTemplates resolveClassTemplates = new SimpleTemplates(UNRESOLVED) {

        @Override
        protected XirTemplate create(CiXirAssembler asm, long flags) {
            XirOperand result = asm.restart(CiKind.Word);
            if (is(UNRESOLVED, flags)) {
                UnresolvedClassPatching patching = new UnresolvedClassPatching(asm, result, config);
                patching.emitInline();
                // -- out of line -------------------------------------------------------
                patching.emitOutOfLine();
            } else {
                XirOperand type = asm.createConstantInputParameter("type", CiKind.Object);
                asm.mov(result, type);
            }
            return asm.finishTemplate(is(UNRESOLVED, flags) ? "resolve class (unresolved)" : "resolve class");
        }
    };

    private SimpleTemplates invokeInterfaceTemplates = new SimpleTemplates(NULL_CHECK) {

        @Override
        protected XirTemplate create(CiXirAssembler asm, long flags) {
            asm.restart();
            XirParameter receiver = asm.createInputParameter("receiver", CiKind.Object);
            XirParameter addr = asm.createConstantInputParameter("addr", CiKind.Word);
            XirOperand temp = asm.createRegisterTemp("temp", CiKind.Word, AMD64.rax);

            if (is(NULL_CHECK, flags)) {
                asm.nop(1);
                asm.mark(MARK_IMPLICIT_NULL);
                asm.pload(CiKind.Word, temp, receiver, true);
            }
            asm.mark(MARK_INVOKEINTERFACE);
            asm.mov(temp, asm.createConstant(CiConstant.forObject(HotSpotProxy.DUMMY_CONSTANT_OBJ)));

            return asm.finishTemplate(addr, "invokeinterface");
        }
    };

    private SimpleTemplates invokeVirtualTemplates = new SimpleTemplates(NULL_CHECK) {

        @Override
        protected XirTemplate create(CiXirAssembler asm, long flags) {
            asm.restart();
            XirParameter receiver = asm.createInputParameter("receiver", CiKind.Object);
            XirParameter addr = asm.createConstantInputParameter("addr", CiKind.Word);
            XirOperand temp = asm.createRegisterTemp("temp", CiKind.Word, AMD64.rax);

            if (is(NULL_CHECK, flags)) {
                asm.nop(1);
                asm.mark(MARK_IMPLICIT_NULL);
                asm.pload(CiKind.Word, temp, receiver, true);
            }
            asm.mark(MARK_INVOKEVIRTUAL);
            asm.mov(temp, asm.createConstant(CiConstant.forObject(HotSpotProxy.DUMMY_CONSTANT_OBJ)));

            return asm.finishTemplate(addr, "invokevirtual");
        }
    };

    private SimpleTemplates invokeSpecialTemplates = new SimpleTemplates(NULL_CHECK) {

        @Override
        protected XirTemplate create(CiXirAssembler asm, long flags) {
            asm.restart();
            XirParameter receiver = asm.createInputParameter("receiver", CiKind.Object);
            XirParameter addr = asm.createConstantInputParameter("addr", CiKind.Word);
            XirOperand temp = asm.createRegisterTemp("temp", CiKind.Word, AMD64.rax);
            XirLabel stub = asm.createOutOfLineLabel("call stub");

            if (is(NULL_CHECK, flags)) {
                asm.nop(1);
                asm.mark(MARK_IMPLICIT_NULL);
                asm.pload(CiKind.Word, temp, receiver, true);
            }
            asm.mark(MARK_INVOKESPECIAL);

            // -- out of line -------------------------------------------------------
            asm.bindOutOfLine(stub);
            XirOperand method = asm.createRegisterTemp("method", CiKind.Word, AMD64.rbx);
            asm.mark(MARK_STATIC_CALL_STUB, XirMark.CALLSITE);
            asm.mov(method, asm.w(0L));
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
            XirParameter addr = asm.createConstantInputParameter("addr", CiKind.Word);

            XirLabel stub = asm.createOutOfLineLabel("call stub");
            asm.mark(MARK_INVOKESTATIC);

            // -- out of line -------------------------------------------------------
            asm.bindOutOfLine(stub);
            XirOperand method = asm.createRegisterTemp("method", CiKind.Word, AMD64.rbx);
            asm.mark(MARK_STATIC_CALL_STUB, XirMark.CALLSITE);
            asm.mov(method, asm.w(0L));
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
            XirParameter lock = asm.createInputParameter("lock", CiKind.Word);

            if (is(NULL_CHECK, flags)) {
                asm.nop(1);
                asm.mark(MARK_IMPLICIT_NULL);
                asm.pload(CiKind.Word, asm.createTemp("temp", CiKind.Word), object, true);
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
                asm.pstore(CiKind.Object, asm.createRegister("rsp", CiKind.Word, AMD64.RSP.asRegister()), asm.i(target.wordSize), object, false);
                asm.pstore(CiKind.Word, asm.createRegister("rsp", CiKind.Word, AMD64.RSP.asRegister()), asm.i(0), lock, false);
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
            XirParameter lock = asm.createInputParameter("lock", CiKind.Word);

            if (config.useFastLocking) {
                useRegisters(asm, AMD64.rax, AMD64.rbx);
                useRegisters(asm, getGeneralParameterRegister(0));
                useRegisters(asm, getGeneralParameterRegister(1));
                asm.callRuntime(config.fastMonitorExitStub, null, object, lock);
            } else {
                asm.reserveOutgoingStack(target.wordSize);
                asm.pstore(CiKind.Word, asm.createRegister("rsp", CiKind.Word, AMD64.RSP.asRegister()), asm.i(0), lock, false);
                asm.callRuntime(config.monitorExitStub, null);
            }

            return asm.finishTemplate("monitorExit");
        }
    };

    private KindTemplates getFieldTemplates = new KindTemplates(NULL_CHECK, UNRESOLVED) {

        @Override
        protected XirTemplate create(CiXirAssembler asm, long flags, CiKind kind) {
            XirOperand result = asm.restart(kind);
            XirParameter object = asm.createInputParameter("object", CiKind.Object);

            if (is(UNRESOLVED, flags)) {
                UnresolvedFieldPatching fieldPatching = new UnresolvedFieldPatching(asm, object, result, false, is(NULL_CHECK, flags), config);
                fieldPatching.emitInline();
                // -- out of line -------------------------------------------------------
                fieldPatching.emitOutOfLine();
                return asm.finishTemplate("getfield<" + kind + ">");
            }
            XirParameter fieldOffset = asm.createConstantInputParameter("fieldOffset", CiKind.Int);
            if (is(NULL_CHECK, flags)) {
                asm.nop(1);
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
            XirOperand temp = asm.createTemp("temp", CiKind.Object);
            asm.mov(temp, object);

            writeBarrier(asm, temp);
            return asm.finishTemplate("writeBarrier");
        }
    };

    private KindTemplates putFieldTemplates = new KindTemplates(WRITE_BARRIER, NULL_CHECK, UNRESOLVED) {

        @Override
        protected XirTemplate create(CiXirAssembler asm, long flags, CiKind kind) {
            asm.restart(CiKind.Void);
            XirParameter object = asm.createInputParameter("object", CiKind.Object);
            XirParameter value = asm.createInputParameter("value", kind);

            if (is(UNRESOLVED, flags)) {
                UnresolvedFieldPatching fieldPatching = new UnresolvedFieldPatching(asm, object, value, true, is(NULL_CHECK, flags), config);
                fieldPatching.emitInline();
                // -- out of line -------------------------------------------------------
                fieldPatching.emitOutOfLine();
                return asm.finishTemplate("putfield<" + kind + ">");
            }
            XirParameter fieldOffset = asm.createConstantInputParameter("fieldOffset", CiKind.Int);
            if (kind == CiKind.Object) {
                verifyPointer(asm, value);
            }
            if (is(NULL_CHECK, flags)) {
                asm.nop(1);
                asm.mark(MARK_IMPLICIT_NULL);
            }
            asm.pstore(kind, object, fieldOffset, value, is(NULL_CHECK, flags));
            if (is(WRITE_BARRIER, flags)) {
                XirOperand temp = asm.createTemp("temp", CiKind.Word);
                asm.mov(temp, object);
                writeBarrier(asm, temp);
            }
            return asm.finishTemplate("putfield<" + kind + ">");
        }
    };

    private final IndexTemplates newInstanceTemplates = new IndexTemplates() {

        @Override
        protected XirTemplate create(CiXirAssembler asm, long flags, int size) {
            XirOperand result = asm.restart(CiKind.Word);
            XirOperand type = asm.createInputParameter("type", CiKind.Object);

            XirOperand temp1 = asm.createRegisterTemp("temp1", CiKind.Word, AMD64.rcx);
            XirOperand temp2 = asm.createRegisterTemp("temp2", CiKind.Word, AMD64.rbx);
            XirOperand temp2i = asm.createRegisterTemp("temp2i", CiKind.Int, AMD64.rbx);
            useRegisters(asm, AMD64.rsi);
            XirLabel tlabFull = asm.createOutOfLineLabel("tlab full");
            XirLabel resume = asm.createInlineLabel("resume");

            // check if the class is already initialized
            asm.pload(CiKind.Int, temp2i, type, asm.i(config.klassStateOffset), false);
            asm.jneq(tlabFull, temp2i, asm.i(config.klassStateFullyInitialized));

            XirOperand thread = asm.createRegisterTemp("thread", CiKind.Word, AMD64.r15);
            asm.pload(CiKind.Word, result, thread, asm.i(config.threadTlabTopOffset), false);
            asm.add(temp1, result, asm.w(size));
            asm.pload(CiKind.Word, temp2, thread, asm.i(config.threadTlabEndOffset), false);

            asm.jgt(tlabFull, temp1, temp2);
            asm.pstore(CiKind.Word, thread, asm.i(config.threadTlabTopOffset), temp1, false);

            asm.bindInline(resume);

            asm.pload(CiKind.Word, temp1, type, asm.i(config.instanceHeaderPrototypeOffset), false);
            asm.pstore(CiKind.Word, result, temp1, false);
            asm.pstore(CiKind.Object, result, asm.i(config.hubOffset), type, false);

            if (size > 2 * target.wordSize) {
                asm.mov(temp1, asm.w(0));
                for (int offset = 2 * target.wordSize; offset < size; offset += target.wordSize) {
                    asm.pstore(CiKind.Word, result, asm.i(offset), temp1, false);
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

    private SimpleTemplates newInstanceUnresolvedTemplates = new SimpleTemplates() {

        @Override
        protected XirTemplate create(CiXirAssembler asm, long flags) {
            XirOperand result = asm.restart(CiKind.Word);
            XirOperand arg = asm.createRegisterTemp("runtime call argument", CiKind.Object, AMD64.rdx);

            UnresolvedClassPatching patching = new UnresolvedClassPatching(asm, arg, config);

            patching.emitInline();
            useRegisters(asm, AMD64.rbx, AMD64.rcx, AMD64.rsi, AMD64.rax);
            asm.callRuntime(config.unresolvedNewInstanceStub, result);

            // -- out of line -------------------------------------------------------
            patching.emitOutOfLine();

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

    private SimpleTemplates newObjectArrayTemplates = new SimpleTemplates(UNRESOLVED) {

        @Override
        protected XirTemplate create(CiXirAssembler asm, long flags) {
            emitNewTypeArray(asm, flags, CiKind.Object, config.useFastNewObjectArray, config.newObjectArrayStub);
            return asm.finishTemplate(is(UNRESOLVED, flags) ? "newObjectArray (unresolved)" : "newObjectArray");
        }
    };

    private void emitNewTypeArray(CiXirAssembler asm, long flags, CiKind kind, boolean useFast, long slowPathStub) {
        XirOperand result = asm.restart(CiKind.Word);

        XirParameter lengthParam = asm.createInputParameter("length", CiKind.Int, true);

        XirOperand length = asm.createRegisterTemp("length", CiKind.Int, AMD64.rbx);
        XirOperand hub = asm.createRegisterTemp("hub", CiKind.Object, AMD64.rdx);

        // Registers rsi, rcx, rdi, and rax are needed by the runtime call.
        // Hub needs to be on rdx, length on rbx.
        XirOperand temp1 = asm.createRegisterTemp("temp1", CiKind.Word, AMD64.rcx);
        XirOperand temp2 = asm.createRegisterTemp("temp2", CiKind.Word, AMD64.rax);
        XirOperand temp3 = asm.createRegisterTemp("temp3", CiKind.Word, AMD64.rdi);
        XirOperand size = asm.createRegisterTemp("size", CiKind.Int, AMD64.rsi);

        UnresolvedClassPatching patching = null;
        if (is(UNRESOLVED, flags)) {
            // insert the patching code for class resolving - the hub will end up in "hub"
            patching = new UnresolvedClassPatching(asm, hub, config);
            patching.emitInline();
        } else {
            asm.mov(hub, asm.createConstantInputParameter("hub", CiKind.Object));
        }

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
            int scale = CiUtil.log2(kind.sizeInBytes(target.wordSize));
            if (scale != 0) {
                asm.shl(size, size, asm.i(scale));
            }
            asm.add(size, size, asm.i(arrayElementOffset + aligning - 1));
            long mask = 0xFFFFFFFFL;
            mask <<= CiUtil.log2(aligning);
            asm.and(size, size, asm.i((int) mask));

            // Try tlab allocation
            XirOperand thread = asm.createRegisterTemp("thread", CiKind.Word, AMD64.r15);
            asm.pload(CiKind.Word, result, thread, asm.i(config.threadTlabTopOffset), false);
            asm.add(temp1, result, size);
            asm.pload(CiKind.Word, temp2, thread, asm.i(config.threadTlabEndOffset), false);
            asm.jgt(slowPath, temp1, temp2);
            asm.pstore(CiKind.Word, thread, asm.i(config.threadTlabTopOffset), temp1, false);

            // Now the new object is in result, store mark word and klass
            asm.pload(CiKind.Word, temp1, hub, asm.i(config.instanceHeaderPrototypeOffset), false);
            asm.pstore(CiKind.Word, result, temp1, false);
            asm.pstore(CiKind.Object, result, asm.i(config.hubOffset), hub, false);

            // Store array length
            asm.pstore(CiKind.Int, result, asm.i(arrayLengthOffset), length, false);

            // Initialize with 0
            XirLabel top = asm.createInlineLabel("top");
            asm.sub(size, size, asm.i(arrayElementOffset));
            asm.shr(size, size, asm.i(Scale.Times8.log2));
            asm.jeq(done, size, asm.i(0));
            asm.xor(temp3, temp3, temp3);
            asm.bindInline(top);
            asm.pstore(CiKind.Word, result, size, temp3, arrayElementOffset - target.wordSize, Scale.Times8, false);
            asm.decAndJumpNotZero(top, size);

            asm.bindInline(done);

            // Slow path
            asm.bindOutOfLine(slowPath);
            asm.callRuntime(slowPathStub, result);
            asm.jmp(done);
        } else {
            asm.callRuntime(slowPathStub, result);
        }

        if (patching != null) {
            patching.emitOutOfLine();
        }
    }

    private KindTemplates newTypeArrayTemplates = new KindTemplates() {
        @Override
        protected XirTemplate create(CiXirAssembler asm, long flags, CiKind kind) {
            emitNewTypeArray(asm, flags, kind, config.useFastNewTypeArray, config.newTypeArrayStub);
            return asm.finishTemplate("newTypeArray<" + kind.toString() + ">");
        }
    };

    private final IndexTemplates multiNewArrayTemplate = new IndexTemplates(UNRESOLVED) {

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

            UnresolvedClassPatching patching = null;
            if (is(UNRESOLVED, flags)) {
                // insert the patching code for class resolving - the hub will end up in "hub"
                patching = new UnresolvedClassPatching(asm, hub, config);
                patching.emitInline();
            } else {
                asm.mov(hub, asm.createConstantInputParameter("hub", CiKind.Object));
            }

            asm.mov(rank, asm.i(dimensions));
            useRegisters(asm, AMD64.rax);
            asm.callRuntime(config.newMultiArrayStub, result);
            if (is(UNRESOLVED, flags)) {
                patching.emitOutOfLine();
            }
            return asm.finishTemplate(is(UNRESOLVED, flags) ? "multiNewArray" + dimensions + " (unresolved)" : "multiNewArray" + dimensions);
        }
    };

    private SimpleTemplates checkCastTemplates = new SimpleTemplates(NULL_CHECK, UNRESOLVED) {

        @Override
        protected XirTemplate create(CiXirAssembler asm, long flags) {
            asm.restart();
            XirParameter object = asm.createInputParameter("object", CiKind.Object);
            final XirOperand hub;
            final UnresolvedClassPatching patching;
            if (is(UNRESOLVED, flags)) {
                hub = asm.createTemp("hub", CiKind.Object);
                // insert the patching code for class resolving - the hub will end up in "hub"
                patching = new UnresolvedClassPatching(asm, hub, config);
                patching.emitInline();
            } else {
                hub = asm.createConstantInputParameter("hub", CiKind.Object);
                patching = null;
            }

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
            XirOperand scratch = asm.createRegisterTemp("scratch", CiKind.Object, AMD64.r10);
            asm.mov(scratch, object);
            asm.callRuntime(config.throwClassCastException, null);
            asm.shouldNotReachHere();

            if (is(UNRESOLVED, flags)) {
                patching.emitOutOfLine();
            }

            return asm.finishTemplate(object, "instanceof");
        }
    };

    private SimpleTemplates instanceOfTemplates = new SimpleTemplates(NULL_CHECK, UNRESOLVED) {

        @Override
        protected XirTemplate create(CiXirAssembler asm, long flags) {
            XirOperand result = asm.restart(CiKind.Boolean);
            XirParameter object = asm.createInputParameter("object", CiKind.Object);
            final XirOperand hub;
            final UnresolvedClassPatching patching;
            if (is(UNRESOLVED, flags)) {
                hub = asm.createTemp("hub", CiKind.Object);
                // insert the patching code for class resolving - the hub will end up in "hub"
                patching = new UnresolvedClassPatching(asm, hub, config);
                patching.emitInline();
            } else {
                hub = asm.createConstantInputParameter("hub", CiKind.Object);
                patching = null;
            }

            XirOperand objHub = asm.createTemp("objHub", CiKind.Object);

            XirLabel end = asm.createInlineLabel("end");
            XirLabel slowPath = asm.createOutOfLineLabel("slow path");

            if (is(NULL_CHECK, flags)) {
                // null isn't "instanceof" anything
                asm.mov(result, asm.b(false));
                asm.jeq(end, object, asm.o(null));
            }

            asm.pload(CiKind.Object, objHub, object, asm.i(config.hubOffset), false);
            // if we get an exact match: succeed immediately
            asm.mov(result, asm.b(true));
            asm.jneq(slowPath, objHub, hub);
            asm.bindInline(end);

            // -- out of line -------------------------------------------------------
            asm.bindOutOfLine(slowPath);
            checkSubtype(asm, result, objHub, hub);
            asm.jmp(end);

            if (is(UNRESOLVED, flags)) {
                patching.emitOutOfLine();
            }

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
            asm.nop(1);
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
                asm.nop(1);
                asm.mark(MARK_IMPLICIT_NULL);
            }
            asm.pload(kind, result, array, index, config.getArrayOffset(kind), Scale.fromInt(elemSize), implicitNullException);
            if (is(BOUNDS_CHECK, flags)) {
                asm.bindOutOfLine(failBoundsCheck);
                asm.callRuntime(config.throwArrayIndexException, null);
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
           if (is(NULL_CHECK, flags)) {
               asm.nop(1);
           }
           asm.pload(CiKind.Object, result, object, asm.i(config.hubOffset), is(NULL_CHECK, flags));
           asm.pload(CiKind.Object, result, result, asm.i(config.classMirrorOffset), false);
           return asm.finishTemplate("currentThread");
       }
    };

    private SimpleTemplates currentThreadTemplates = new SimpleTemplates() {
       @Override
       protected XirTemplate create(CiXirAssembler asm, long flags) {
           XirOperand result = asm.restart(CiKind.Object);
           XirOperand thread = asm.createRegisterTemp("thread", CiKind.Word, AMD64.r15);
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

            XirOperand tempSrc = asm.createTemp("tempSrc", CiKind.Word);
            XirOperand tempDest = asm.createTemp("tempDest", CiKind.Word);
            XirOperand lengthOperand = asm.createRegisterTemp("lengthOperand", CiKind.Int, AMD64.rax);

            XirOperand compHub = null;
            XirOperand valueHub = null;
            XirOperand temp = null;
            XirLabel store = null;
            XirLabel slowStoreCheck = null;

            if (is(STORE_CHECK, flags)) {
                valueHub = asm.createRegisterTemp("valueHub", CiKind.Word, AMD64.rdi);
                compHub = asm.createRegisterTemp("compHub", CiKind.Word, AMD64.rsi);
                temp = asm.createRegisterTemp("temp", CiKind.Word, AMD64.r10);
            }

            // Calculate the factor for the repeat move instruction.
            int elementSize = kind.sizeInBytes(target.wordSize);
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
                asm.pstore(CiKind.Boolean, asm.w(config.cardtableStartAddress), tempDest, asm.b(false), false);

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
                asm.pstore(CiKind.Boolean, asm.w(config.cardtableStartAddress), tempDestEnd, asm.b(false), false);
                asm.sub(tempDestEnd, tempDestEnd, asm.i(1));
                asm.jneq(writeBarrierLoop, tempDestEnd, tempDest);
                asm.jmp(back);
            }

            if (is(STORE_CHECK, flags)) {
                assert kind == CiKind.Object;
                useRegisters(asm, AMD64.rax);
                asm.bindOutOfLine(slowStoreCheck);
                checkSubtype(asm, temp, valueHub, compHub);
                asm.jneq(store, temp, asm.w(0));
                XirOperand scratch = asm.createRegisterTemp("scratch", CiKind.Object, AMD64.r10);
                asm.mov(scratch, valueHub);
                asm.callRuntime(config.throwArrayStoreException, null);
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
            XirOperand temp = asm.createTemp("temp", CiKind.Word);
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
                        asm.nop(1);
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
            if (is(WRITE_BARRIER, flags)) {
                asm.lea(temp, array, index, disp, scale);
                asm.pstore(kind, temp, value, implicitNullException);
                writeBarrier(asm, temp);
            } else {
                asm.pstore(kind, array, index, value, disp, scale, implicitNullException);
            }

            // -- out of line -------------------------------------------------------
            if (is(BOUNDS_CHECK, flags)) {
                asm.bindOutOfLine(failBoundsCheck);
                asm.callRuntime(config.throwArrayIndexException, null);
                asm.shouldNotReachHere();
            }
            if (is(STORE_CHECK, flags) && kind == CiKind.Object) {
                useRegisters(asm, AMD64.rax);
                asm.bindOutOfLine(slowStoreCheck);
                checkSubtype(asm, temp, valueHub, compHub);
                asm.jneq(store, temp, asm.w(0));
                XirOperand scratch = asm.createRegisterTemp("scratch", CiKind.Object, AMD64.r10);
                asm.mov(scratch, valueHub);
                asm.callRuntime(config.throwArrayStoreException, null);
                asm.jmp(store);
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
                asm.nop(1);
                asm.mark(MARK_IMPLICIT_NULL);
            }
            verifyPointer(asm, object);
            asm.pload(CiKind.Int, result, object, asm.i(config.arrayLengthOffset), true);
            return asm.finishTemplate("arrayLength");
        }
    };

    @Override
    public XirSnippet genPrologue(XirSite site, RiMethod method) {
        boolean staticMethod = Modifier.isStatic(method.accessFlags());
        return new XirSnippet(staticMethod ? prologueTemplates.get(site, STATIC_METHOD) : prologueTemplates.get(site));
    }

    @Override
    public XirSnippet genEpilogue(XirSite site, RiMethod method) {
        return new XirSnippet(epilogueTemplates.get(site));
    }

    @Override
    public XirSnippet genSafepoint(XirSite site) {
        return new XirSnippet(safepointTemplates.get(site));
    }

    @Override
    public XirSnippet genExceptionObject(XirSite site) {
        return new XirSnippet(exceptionObjectTemplates.get(site));
    }

    @Override
    public XirSnippet genResolveClass(XirSite site, RiType type, Representation rep) {
        assert rep == Representation.ObjectHub || rep == Representation.StaticFields || rep == Representation.JavaClass : "unexpected representation: " + rep;
        if (type.isResolved()) {
            return new XirSnippet(resolveClassTemplates.get(site), XirArgument.forObject(type.getEncoding(rep).asObject()));
        }
        return new XirSnippet(resolveClassTemplates.get(site, UNRESOLVED));
    }

    @Override
    public XirSnippet genIntrinsic(XirSite site, XirArgument[] arguments, RiMethod method) {
        return null;
    }

    @Override
    public XirSnippet genInvokeInterface(XirSite site, XirArgument receiver, RiMethod method) {
        return new XirSnippet(invokeInterfaceTemplates.get(site), receiver, XirArgument.forWord(0));
    }

    @Override
    public XirSnippet genInvokeVirtual(XirSite site, XirArgument receiver, RiMethod method) {
        return new XirSnippet(invokeVirtualTemplates.get(site), receiver, XirArgument.forWord(0));
    }

    @Override
    public XirSnippet genInvokeSpecial(XirSite site, XirArgument receiver, RiMethod method) {
        return new XirSnippet(invokeSpecialTemplates.get(site), receiver, XirArgument.forWord(0));
    }

    @Override
    public XirSnippet genInvokeStatic(XirSite site, RiMethod method) {
        return new XirSnippet(invokeStaticTemplates.get(site), XirArgument.forWord(0));
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
        if (field.isResolved()) {
            return new XirSnippet(getFieldTemplates.get(site, field.kind()), object, XirArgument.forInt(((HotSpotField) field).offset()));
        }
        return new XirSnippet(getFieldTemplates.get(site, field.kind(), UNRESOLVED), object);
    }

    @Override
    public XirSnippet genWriteBarrier(XirArgument object) {
        return new XirSnippet(writeBarrierTemplate.get(null, CiKind.Void), object);
    }

    @Override
    public XirSnippet genPutField(XirSite site, XirArgument object, RiField field, XirArgument value) {
        if (field.isResolved()) {
            return new XirSnippet(putFieldTemplates.get(site, field.kind()), object, value, XirArgument.forInt(((HotSpotField) field).offset()));
        }
        return new XirSnippet(putFieldTemplates.get(site, field.kind(), UNRESOLVED), object, value);
    }

    @Override
    public XirSnippet genGetStatic(XirSite site, XirArgument object, RiField field) {
        if (field.isResolved()) {
            return new XirSnippet(getFieldTemplates.get(site, field.kind()), object, XirArgument.forInt(((HotSpotField) field).offset()));
        }
        return new XirSnippet(getFieldTemplates.get(site, field.kind(), UNRESOLVED), object);
    }

    @Override
    public XirSnippet genPutStatic(XirSite site, XirArgument object, RiField field, XirArgument value) {
        if (field.isResolved()) {
            return new XirSnippet(putFieldTemplates.get(site, field.kind()), object, value, XirArgument.forInt(((HotSpotField) field).offset()));
        }
        return new XirSnippet(putFieldTemplates.get(site, field.kind(), UNRESOLVED), object, value);
    }

    @Override
    public XirSnippet genNewInstance(XirSite site, RiType type) {
        if (type.isResolved()) {
            int instanceSize = ((HotSpotTypeResolved) type).instanceSize();
            return new XirSnippet(newInstanceTemplates.get(site, instanceSize), XirArgument.forObject(type));
        }
        return new XirSnippet(newInstanceUnresolvedTemplates.get(site));
    }

    @Override
    public XirSnippet genNewArray(XirSite site, XirArgument length, CiKind elementKind, RiType componentType, RiType arrayType) {
        if (elementKind == CiKind.Object) {
            if (arrayType.isResolved()) {
                return new XirSnippet(newObjectArrayTemplates.get(site), length, XirArgument.forObject(arrayType));
            }
            return new XirSnippet(newObjectArrayTemplates.get(site, UNRESOLVED), length);
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
        if (type.isResolved()) {
            XirArgument[] params = Arrays.copyOf(lengths, lengths.length + 1);
            params[lengths.length] = XirArgument.forObject(type);
            return new XirSnippet(multiNewArrayTemplate.get(site, lengths.length), params);
        }
        return new XirSnippet(multiNewArrayTemplate.get(site, lengths.length, UNRESOLVED), lengths);
    }

    @Override
    public XirSnippet genCheckCast(XirSite site, XirArgument receiver, XirArgument hub, RiType type) {
        if (type.isResolved()) {
            return new XirSnippet(checkCastTemplates.get(site), receiver, hub);
        }
        return new XirSnippet(checkCastTemplates.get(site, UNRESOLVED), receiver);
    }

    @Override
    public XirSnippet genInstanceOf(XirSite site, XirArgument object, XirArgument hub, RiType type) {
        if (type.isResolved()) {
            return new XirSnippet(instanceOfTemplates.get(site), object, hub);
        }
        return new XirSnippet(instanceOfTemplates.get(site, UNRESOLVED), object);
    }

    @Override
    public XirSnippet genArrayLoad(XirSite site, XirArgument array, XirArgument index, XirArgument length, CiKind elementKind, RiType elementType) {
        if (length == null || !site.requiresBoundsCheck()) {
            return new XirSnippet(arrayLoadTemplates.get(site, elementKind), array, index);
        }
        return new XirSnippet(arrayLoadTemplates.get(site, elementKind, GIVEN_LENGTH), array, index, length);
    }

    @Override
    public XirSnippet genArrayStore(XirSite site, XirArgument array, XirArgument index, XirArgument length, XirArgument value, CiKind elementKind, RiType elementType) {
        if (length == null || !site.requiresBoundsCheck()) {
            return new XirSnippet(arrayStoreTemplates.get(site, elementKind), array, index, value);
        }
        return new XirSnippet(arrayStoreTemplates.get(site, elementKind, GIVEN_LENGTH), array, index, value, length);
    }

    @Override
    public XirSnippet genArrayCopy(XirSite site, XirArgument src, XirArgument srcPos, XirArgument dest, XirArgument destPos, XirArgument length, RiType elementType, boolean inputsSame, boolean inputsDifferent) {
        if (elementType == null) {
            return null;
        }
        assert !inputsDifferent || !inputsSame;
        XirTemplate template = null;
        if (inputsDifferent) {
            template = arrayCopyTemplates.get(site, elementType.kind(), INPUTS_DIFFERENT);
        } else if (inputsSame) {
            template = arrayCopyTemplates.get(site, elementType.kind(), INPUTS_SAME);
        } else {
            template = arrayCopyTemplates.get(site, elementType.kind());
        }
        return new XirSnippet(template, src, srcPos, dest, destPos, length);
    }

    @Override
    public XirSnippet genArrayLength(XirSite site, XirArgument array) {
        return new XirSnippet(arrayLengthTemplates.get(site), array);
    }

    @Override
    public List<XirTemplate> buildTemplates(CiXirAssembler asm) {
        this.globalAsm = asm;
        List<XirTemplate> templates = new ArrayList<XirTemplate>();
        return templates;
    }

    private static class UnresolvedClassPatching {

        private final XirLabel patchSite;
        private final XirLabel replacement;
        private final XirLabel patchStub;
        private final CiXirAssembler asm;
        private final HotSpotVMConfig config;
        private final XirOperand arg;
        private State state;

        private enum State {
            New, Inline, Finished
        }

        public UnresolvedClassPatching(CiXirAssembler asm, XirOperand arg, HotSpotVMConfig config) {
            this.asm = asm;
            this.arg = arg;
            this.config = config;
            patchSite = asm.createInlineLabel("patch site");
            replacement = asm.createOutOfLineLabel("replacement");
            patchStub = asm.createOutOfLineLabel("patch stub");

            state = State.New;
        }

        public void emitInline() {
            assert state == State.New;

            asm.bindInline(patchSite);
            asm.mark(MARK_DUMMY_OOP_RELOCATION);

            asm.jmp(patchStub);

            // TODO: make this more generic & safe - this is needed to create space for patching
            asm.nop(5);

            state = State.Inline;
        }

        public void emitOutOfLine() {
            assert state == State.Inline;

            asm.bindOutOfLine(replacement);
            XirMark begin = asm.mark(null);
            asm.mov(arg, asm.createConstant(CiConstant.NULL_OBJECT));
            XirMark end = asm.mark(null);
            // make this piece of data look like an instruction
            asm.rawBytes(new byte[] {(byte) 0xb8, 0, 0, 0x05, 0});
            asm.mark(MARK_KLASS_PATCHING, begin, end);
            asm.bindOutOfLine(patchStub);
            asm.callRuntime(config.loadKlassStub, null);
            asm.jmp(patchSite);

            state = State.Finished;
        }
    }

    private static class UnresolvedFieldPatching {

        private final XirLabel patchSite;
        private final XirLabel replacement;
        private final XirLabel patchStub;
        private final CiXirAssembler asm;
        private final HotSpotVMConfig config;
        private State state;
        private final XirOperand receiver;
        private final XirOperand value;
        private final boolean put;
        private final boolean nullCheck;

        private enum State {
            New, Inline, Finished
        }

        public UnresolvedFieldPatching(CiXirAssembler asm, XirOperand receiver, XirOperand value, boolean put, boolean nullCheck, HotSpotVMConfig config) {
            this.asm = asm;
            this.receiver = receiver;
            this.value = value;
            this.put = put;
            this.nullCheck = nullCheck;
            this.config = config;
            patchSite = asm.createInlineLabel("patch site");
            replacement = asm.createOutOfLineLabel("replacement");
            patchStub = asm.createOutOfLineLabel("patch stub");

            state = State.New;
        }

        public void emitInline() {
            assert state == State.New;
            if (nullCheck) {
                asm.nop(1);
            }
            asm.bindInline(patchSite);
            asm.mark(MARK_DUMMY_OOP_RELOCATION);
            if (nullCheck) {
                asm.mark(MARK_IMPLICIT_NULL);
            }
            asm.safepoint();
            asm.jmp(patchStub);

            // TODO: make this more generic & safe - this is needed to create space for patching
            asm.nop(5);

            state = State.Inline;
        }

        public void emitOutOfLine() {
            assert state == State.Inline;

            asm.bindOutOfLine(replacement);
            XirMark begin = asm.mark(null);
            if (put) {
                asm.pstore(value.kind, receiver, asm.i(Integer.MAX_VALUE), value, false);
            } else {
                asm.pload(value.kind, value, receiver, asm.i(Integer.MAX_VALUE), false);
            }
            XirMark end = asm.mark(null);
            // make this piece of data look like an instruction
            asm.rawBytes(new byte[] {(byte) 0xb8, 0, 0, 0x05, 0});
            asm.mark(MARK_ACCESS_FIELD_PATCHING, begin, end);
            asm.bindOutOfLine(patchStub);
            asm.callRuntime(config.accessFieldStub, null);
            asm.jmp(patchSite);

            // Check if we need NOP instructions like in C1 to "not destroy the world".

            state = State.Finished;
        }
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
        asm.pstore(CiKind.Boolean, asm.w(config.cardtableStartAddress), base, asm.b(false), false);
    }

    public boolean is(TemplateFlag check, long flags) {
        return (flags & check.bits()) == check.bits();
    }

    /**
     * Base class for all the ondemand template generators. It is not normally subclassed directly, but through one of
     * its subclasses (SimpleTemplates, KindTemplates, IndexTemplates).
     *
     * @author Lukas Stadler
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
