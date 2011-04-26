/*
 * Copyright (c) 2010 Sun Microsystems, Inc.  All rights reserved.
 *
 * Sun Microsystems, Inc. has intellectual property rights relating to technology embodied in the product
 * that is described in this document. In particular, and without limitation, these intellectual property
 * rights may include one or more of the U.S. patents listed at http://www.sun.com/patents and one or
 * more additional patents or pending patent applications in the U.S. and in other countries.
 *
 * U.S. Government Rights - Commercial software. Government users are subject to the Sun
 * Microsystems, Inc. standard license agreement and applicable provisions of the FAR and its
 * supplements.
 *
 * Use is subject to license terms. Sun, Sun Microsystems, the Sun logo, Java and Solaris are trademarks or
 * registered trademarks of Sun Microsystems, Inc. in the U.S. and other countries. All SPARC trademarks
 * are used under license and are trademarks or registered trademarks of SPARC International, Inc. in the
 * U.S. and other countries.
 *
 * UNIX is a registered trademark in the U.S. and other countries, exclusively licensed through X/Open
 * Company, Ltd.
 */
package com.oracle.graal.runtime;

import java.io.*;
import java.lang.reflect.*;
import java.util.*;

import com.sun.cri.ci.*;
import com.sun.cri.ci.CiTargetMethod.Call;
import com.sun.cri.ci.CiTargetMethod.DataPatch;
import com.sun.cri.ci.CiTargetMethod.Safepoint;
import com.sun.cri.ri.*;
import com.sun.max.asm.dis.*;
import com.sun.max.lang.*;

/**
 * CRI runtime implementation for the HotSpot VM.
 *
 * @author Thomas Wuerthinger, Lukas Stadler
 */
public class HotSpotRuntime implements RiRuntime {

    final HotSpotVMConfig config;
    final HotSpotRegisterConfig regConfig;
    final HotSpotRegisterConfig globalStubRegConfig;
    private final Compiler compiler;


    public HotSpotRuntime(HotSpotVMConfig config, Compiler compiler) {
        this.config = config;
        this.compiler = compiler;
        regConfig = new HotSpotRegisterConfig(config, false);
        globalStubRegConfig = new HotSpotRegisterConfig(config, true);
    }

    @Override
    public int codeOffset() {
        return 0;
    }

    @Override
    public String disassemble(byte[] code, long address) {
        return disassemble(code, new DisassemblyPrinter(false), address);
    }

    private String disassemble(byte[] code, DisassemblyPrinter disassemblyPrinter, long address) {
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        final ISA instructionSet = ISA.AMD64;
        Disassembler.disassemble(byteArrayOutputStream, code, instructionSet, WordWidth.BITS_64, address, null, disassemblyPrinter);
        return byteArrayOutputStream.toString();
    }

    @Override
    public String disassemble(final CiTargetMethod targetMethod) {

        final DisassemblyPrinter disassemblyPrinter = new DisassemblyPrinter(false) {

            private String toString(Call call) {
                if (call.runtimeCall != null) {
                    return "{" + call.runtimeCall.name() + "}";
                } else if (call.symbol != null) {
                    return "{" + call.symbol + "}";
                } else if (call.globalStubID != null) {
                    return "{" + call.globalStubID + "}";
                } else {
                    return "{" + call.method + "}";
                }
            }

            private String siteInfo(int pcOffset) {
                for (Call call : targetMethod.directCalls) {
                    if (call.pcOffset == pcOffset) {
                        return toString(call);
                    }
                }
                for (Call call : targetMethod.indirectCalls) {
                    if (call.pcOffset == pcOffset) {
                        return toString(call);
                    }
                }
                for (Safepoint site : targetMethod.safepoints) {
                    if (site.pcOffset == pcOffset) {
                        return "{safepoint}";
                    }
                }
                for (DataPatch site : targetMethod.dataReferences) {
                    if (site.pcOffset == pcOffset) {
                        return "{" + site.constant + "}";
                    }
                }
                return null;
            }

            @Override
            protected String disassembledObjectString(Disassembler disassembler, DisassembledObject disassembledObject) {
                final String string = super.disassembledObjectString(disassembler, disassembledObject);

                String site = siteInfo(disassembledObject.startPosition());
                if (site != null) {
                    return string + " " + site;
                }
                return string;
            }
        };
        final byte[] code = Arrays.copyOf(targetMethod.targetCode(), targetMethod.targetCodeSize());
        return disassemble(code, disassemblyPrinter, 0L);
    }

    @Override
    public String disassemble(RiMethod method) {
        return "No disassembler available";
    }

    @Override
    public RiConstantPool getConstantPool(RiMethod method) {
        return ((HotSpotTypeResolved) method.holder()).constantPool();
    }

    @Override
    public RiOsrFrame getOsrFrame(RiMethod method, int bci) {
        return null;
    }

    public Class<?> getJavaClass(CiConstant c) {
        return null;
    }

    @Override
    public RiType asRiType(CiKind kind) {
        return compiler.getVMEntries().getType(kind.toJavaClass());
    }

    @Override
    public RiType getTypeOf(CiConstant constant) {
        return compiler.getVMEntries().getRiType(constant);
    }

    @Override
    public boolean isExceptionType(RiType type) {
        return type.isSubtypeOf(compiler.getVMEntries().getType(Throwable.class));
    }

    @Override
    public RiSnippets getSnippets() {
        throw new UnsupportedOperationException("getSnippets");
    }

    @Override
    public boolean mustInline(RiMethod method) {
        return false;
    }

    @Override
    public boolean mustNotCompile(RiMethod method) {
        return false;
    }

    @Override
    public boolean mustNotInline(RiMethod method) {
        return Modifier.isNative(method.accessFlags());
    }

    @Override
    public Object registerGlobalStub(CiTargetMethod targetMethod, String name) {
        return HotSpotTargetMethod.installStub(compiler, targetMethod, name);
    }

    @Override
    public int sizeOfBasicObjectLock() {
        // TODO shouldn't be hard coded
        return 2 * 8;
    }

    @Override
    public int basicObjectLockOffsetInBytes() {
        return 8;
    }

    @Override
    public RiField getRiField(Field javaField) {
        throw new UnsupportedOperationException("getRiField");
    }

    @Override
    public RiMethod getRiMethod(Method javaMethod) {
        throw new UnsupportedOperationException("getRiMethod");
    }

    @Override
    public RiMethod getRiMethod(Constructor<?> javaConstructor) {
        throw new UnsupportedOperationException("getRiMethod");
    }

    @Override
    public CiConstant invoke(RiMethod method, CiMethodInvokeArguments args) {
        return null;
    }

    @Override
    public CiConstant foldWordOperation(int opcode, CiMethodInvokeArguments args) {
        throw new UnsupportedOperationException("foldWordOperation");
    }

    @Override
    public boolean areConstantObjectsEqual(CiConstant x, CiConstant y) {
        return compiler.getVMEntries().compareConstantObjects(x, y);
    }

    @Override
    public RiRegisterConfig getRegisterConfig(RiMethod method) {
        return regConfig;
    }

    /**
     * HotSpots needs an area suitable for storing a program counter for temporary use during the deoptimization process.
     */
    @Override
    public int getCustomStackAreaSize() {
        return 8;
    }

    @Override
    public boolean supportsArrayIntrinsics() {
        return true;
    }

    @Override
    public int getArrayLength(CiConstant array) {
        return compiler.getVMEntries().getArrayLength(array);
    }

    @Override
    public Class<?> asJavaClass(CiConstant c) {
        return null;
    }

    @Override
    public Object asJavaObject(CiConstant c) {
        return null;
    }
}
