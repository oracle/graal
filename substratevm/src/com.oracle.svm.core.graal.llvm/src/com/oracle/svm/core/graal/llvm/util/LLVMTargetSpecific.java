/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package com.oracle.svm.core.graal.llvm.util;

import static com.oracle.svm.core.graal.llvm.util.LLVMUtils.FALSE;
import static com.oracle.svm.shared.util.VMError.shouldNotReachHere;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.FrameAccess;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.SubstrateTarget;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.shadowed.org.bytedeco.llvm.LLVM.LLVMRelocationIteratorRef;
import com.oracle.svm.shadowed.org.bytedeco.llvm.LLVM.LLVMSectionIteratorRef;
import com.oracle.svm.shadowed.org.bytedeco.llvm.LLVM.LLVMSymbolIteratorRef;
import com.oracle.svm.shadowed.org.bytedeco.llvm.global.LLVM;
import com.oracle.svm.shared.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.BuildtimeAccessOnly;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.Disallowed;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.NoLayeredCallbacks;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;

/** LLVM target-specific inline assembly snippets and information. */
public interface LLVMTargetSpecific {
    static LLVMTargetSpecific get() {
        return ImageSingletons.lookup(LLVMTargetSpecific.class);
    }

    /**
     * Snippet that gets the value of an arbitrary register.
     */
    String getRegisterInlineAsm(String register);

    /**
     * Snippet that sets the value of an arbitrary register.
     */
    String setRegisterInlineAsm(String register);

    /**
     * Snippet that jumps to a runtime-computed address.
     */
    String getJumpInlineAsm();

    /**
     * Snippet that loads a value in a register.
     */
    String getLoadInlineAsm(String inputRegister, int offset);

    /**
     * Snippet that loads a value from an address based on a reserved register into an output
     * register.
     */
    String getLoadInlineAsm(String inputRegister, int offset, int sizeInBytes);

    /**
     * Snippet that stores an input register value to an address based on a reserved register.
     */
    String getStoreInlineAsm(String outputRegister, int offset, int sizeInBytes);

    /**
     * Scratch register clobbered by fixed-register load/store snippets, or {@code null} if the
     * snippet only uses its explicit operands. Targets can use this when a memory operand cannot
     * encode the requested offset directly. The effective address must still be synthesized inside
     * the inline assembly, so LLVM never sees an ordinary pointer derived from the reserved register.
     */
    default String getFixedRegisterMemoryAccessScratchRegister(@SuppressWarnings("unused") String baseRegister, @SuppressWarnings("unused") int offset,
                    @SuppressWarnings("unused") int sizeInBytes) {
        return null;
    }

    /**
     * Snippet that adds two registers and save the result in one of them.
     */
    String getAddInlineAssembly(String outputRegisterName, String inputRegisterName);

    /**
     * Snippet representing a nop instruction.
     */
    String getNopInlineAssembly();

    /**
     * Snippet that loads the current instruction pointer into an output register.
     */
    String getJavaFrameAnchorIPInlineAssembly();

    /**
     * Name of the architecture to be passed to the LLVM compiler.
     */
    String getLLVMArchName();

    /**
     * Number of bytes separating two adjacent call frames. A call frame starts at the stack pointer
     * and its size is as given by the LLVM stack map.
     */
    int getCallFrameSeparation();

    /**
     * Offset of the frame pointer relative to the first address outside the current call frame.
     * This offset should be negative.
     */
    int getFramePointerOffset();

    /**
     * Register number of the stack pointer used by the LLVM stack maps.
     */
    int getStackPointerDwarfRegNum();

    /**
     * Register number of the frame pointer used by the LLVM stack maps.
     */
    int getFramePointerDwarfRegNum();

    /**
     * Offset between the stack pointer of the caller and the frame pointer of the callee.
     */
    long getCallerSPOffset();

    /**
     * Additional target-specific options to be passed to the LLVM compiler.
     */
    default List<String> getLLCAdditionalOptions() {
        return Collections.emptyList();
    }

    /**
     * Transformation to be applied to the name of a register given by Graal to obtain the
     * corresponding name in assembly.
     */
    default String getLLVMRegisterName(String register) {
        return register;
    }

    /**
     * A scratch register of the architecture.
     */
    String getScratchRegister();

    /**
     * Condition for adding section in sections info to avoid duplicates.
     */
    default boolean isSymbolValid(@SuppressWarnings("unused") String symbol) {
        return true;
    }

    /**
     * Extracts the instruction offset from the Stack Map section.
     */
    default int getInstructionOffset(ByteBuffer buffer, int offset, @SuppressWarnings("unused") LLVMSectionIteratorRef relocationsSectionIteratorRef,
                    @SuppressWarnings("unused") LLVMRelocationIteratorRef relocationIteratorRef) {
        return buffer.getInt(offset);
    }

    /**
     * String representing the target for compilation.
     */
    default String getTargetTriple() {
        if (Platform.includedIn(Platform.DARWIN.class)) {
            return "-unknown-darwin";
        } else if (Platform.includedIn(Platform.LINUX.class)) {
            return "-unknown-linux-gnu";
        } else {
            throw shouldNotReachHere("Unexpected target for LLVM backend: " + ImageSingletons.lookup(Platform.class).toString());
        }
    }
}

@AutomaticallyRegisteredFeature
@Platforms(Platform.AMD64.class)
@SingletonTraits(access = BuildtimeAccessOnly.class, layeredCallbacks = NoLayeredCallbacks.class, other = Disallowed.class)
class LLVMAMD64TargetSpecificFeature implements InternalFeature {
    private static final int AMD64_RSP_IDX = 7;
    private static final int AMD64_RBP_IDX = 6;

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return SubstrateOptions.useLLVMBackend();
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(LLVMTargetSpecific.class, new LLVMAMD64TargetSpecific());
    }

    @SingletonTraits(access = BuildtimeAccessOnly.class, layeredCallbacks = NoLayeredCallbacks.class, other = Disallowed.class)
    private static final class LLVMAMD64TargetSpecific implements LLVMTargetSpecific {
        @Override
        public String getRegisterInlineAsm(String register) {
            return "movq %" + register + ", $0";
        }

        @Override
        public String setRegisterInlineAsm(String register) {
            return "movq $0, %" + register;
        }

        @Override
        public String getJumpInlineAsm() {
            return "jmpq *$0";
        }

        @Override
        public String getLoadInlineAsm(String inputRegister, int offset) {
            return "movq " + offset + "(%" + inputRegister + "), $0";
        }

        @Override
        public String getLoadInlineAsm(String inputRegister, int offset, int sizeInBytes) {
            return switch (sizeInBytes) {
                case Byte.BYTES -> "movb " + offset + "(%" + inputRegister + "), $0";
                case Short.BYTES -> "movw " + offset + "(%" + inputRegister + "), $0";
                case Integer.BYTES -> "movl " + offset + "(%" + inputRegister + "), $0";
                case Long.BYTES -> "movq " + offset + "(%" + inputRegister + "), $0";
                default -> throw shouldNotReachHere("Unsupported load size: " + sizeInBytes); // ExcludeFromJacocoGeneratedReport
            };
        }

        @Override
        public String getStoreInlineAsm(String outputRegister, int offset, int sizeInBytes) {
            return switch (sizeInBytes) {
                case Byte.BYTES -> "movb $0, " + offset + "(%" + outputRegister + ")";
                case Short.BYTES -> "movw $0, " + offset + "(%" + outputRegister + ")";
                case Integer.BYTES -> "movl $0, " + offset + "(%" + outputRegister + ")";
                case Long.BYTES -> "movq $0, " + offset + "(%" + outputRegister + ")";
                default -> throw shouldNotReachHere("Unsupported store size: " + sizeInBytes); // ExcludeFromJacocoGeneratedReport
            };
        }

        @Override
        public String getAddInlineAssembly(String outputRegister, String inputRegister) {
            return "addq %" + inputRegister + ", %" + outputRegister;
        }

        @Override
        public String getNopInlineAssembly() {
            return "nop";
        }

        @Override
        public String getJavaFrameAnchorIPInlineAssembly() {
            return "leaq 0(%rip), $0";
        }

        @Override
        public String getLLVMArchName() {
            return "x86-64";
        }

        @Override
        public int getCallFrameSeparation() {
            return FrameAccess.returnAddressSize();
        }

        @Override
        public int getFramePointerOffset() {
            return -SubstrateTarget.getWordSize();
        }

        @Override
        public long getCallerSPOffset() {
            return 2L * SubstrateTarget.getWordSize();
        }

        @Override
        public int getStackPointerDwarfRegNum() {
            return AMD64_RSP_IDX;
        }

        @Override
        public int getFramePointerDwarfRegNum() {
            return AMD64_RBP_IDX;
        }

        @Override
        public List<String> getLLCAdditionalOptions() {
            List<String> list = new ArrayList<>();
            list.add("-no-x86-call-frame-opt");
            if (Platform.includedIn(Platform.IOS.class)) {
                list.add("-mtriple=x86_64-ios");
            }
            return list;
        }

        @Override
        public String getScratchRegister() {
            return "rax";
        }

        @Override
        public String getTargetTriple() {
            return "x86_64" + LLVMTargetSpecific.super.getTargetTriple();
        }
    }
}

@AutomaticallyRegisteredFeature
@Platforms(Platform.AARCH64.class)
@SingletonTraits(access = BuildtimeAccessOnly.class, layeredCallbacks = NoLayeredCallbacks.class, other = Disallowed.class)
class LLVMAArch64TargetSpecificFeature implements InternalFeature {
    private static final int AARCH64_FP_IDX = 29;
    private static final int AARCH64_SP_IDX = 31;

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return SubstrateOptions.useLLVMBackend();
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(LLVMTargetSpecific.class, new LLVMAArch64TargetSpecific());
    }

    @SingletonTraits(access = BuildtimeAccessOnly.class, layeredCallbacks = NoLayeredCallbacks.class, other = Disallowed.class)
    private static final class LLVMAArch64TargetSpecific implements LLVMTargetSpecific {
        @Override
        public String getRegisterInlineAsm(String register) {
            return "MOV $0, " + getLLVMRegisterName(register);
        }

        @Override
        public String setRegisterInlineAsm(String register) {
            return "MOV " + getLLVMRegisterName(register) + ", $0";
        }

        @Override
        public String getJumpInlineAsm() {
            return "BR $0";
        }

        @Override
        public String getLoadInlineAsm(String inputRegister, int offset) {
            return "LDR $0, [" + getLLVMRegisterName(inputRegister) + ", #" + offset + "]";
        }

        @Override
        public String getLoadInlineAsm(String inputRegister, int offset, int sizeInBytes) {
            return switch (sizeInBytes) {
                case Byte.BYTES -> getLoadStoreInlineAsm("LDRB", "${0:w}", inputRegister, offset, sizeInBytes);
                case Short.BYTES -> getLoadStoreInlineAsm("LDRH", "${0:w}", inputRegister, offset, sizeInBytes);
                case Integer.BYTES -> getLoadStoreInlineAsm("LDR", "${0:w}", inputRegister, offset, sizeInBytes);
                case Long.BYTES -> getLoadStoreInlineAsm("LDR", "$0", inputRegister, offset, sizeInBytes);
                default -> throw shouldNotReachHere("Unsupported load size: " + sizeInBytes); // ExcludeFromJacocoGeneratedReport
            };
        }

        @Override
        public String getStoreInlineAsm(String outputRegister, int offset, int sizeInBytes) {
            return switch (sizeInBytes) {
                case Byte.BYTES -> getLoadStoreInlineAsm("STRB", "${0:w}", outputRegister, offset, sizeInBytes);
                case Short.BYTES -> getLoadStoreInlineAsm("STRH", "${0:w}", outputRegister, offset, sizeInBytes);
                case Integer.BYTES -> getLoadStoreInlineAsm("STR", "${0:w}", outputRegister, offset, sizeInBytes);
                case Long.BYTES -> getLoadStoreInlineAsm("STR", "$0", outputRegister, offset, sizeInBytes);
                default -> throw shouldNotReachHere("Unsupported store size: " + sizeInBytes); // ExcludeFromJacocoGeneratedReport
            };
        }

        @Override
        public String getFixedRegisterMemoryAccessScratchRegister(String baseRegister, int offset, int sizeInBytes) {
            return isLoadStoreImmediate(offset, sizeInBytes) ? null : getScratchRegister();
        }

        private String getLoadStoreInlineAsm(String instruction, String value, String baseRegister, int offset, int sizeInBytes) {
            String base = getLLVMRegisterName(baseRegister);
            if (isLoadStoreImmediate(offset, sizeInBytes)) {
                return instruction + " " + value + ", [" + base + ", #" + offset + "]";
            }
            String scratch = getLLVMRegisterName(getScratchRegister());
            String addSub = offset < 0 ? "SUB" : "ADD";
            return loadOffsetMagnitudeInlineAsm(scratch, offset) + "; " + addSub + " " + scratch + ", " + base + ", " + scratch + "; " +
                            instruction + " " + value + ", [" + scratch + "]";
        }

        private static boolean isLoadStoreImmediate(int offset, int sizeInBytes) {
            return offset >= 0 && offset % sizeInBytes == 0 && offset / sizeInBytes <= 4095;
        }

        private static String loadOffsetMagnitudeInlineAsm(String register, int offset) {
            long magnitude = offset < 0 ? -(long) offset : offset;
            StringBuilder asm = new StringBuilder("MOVZ ").append(register).append(", #").append(magnitude & 0xffff);
            for (int shift = Short.SIZE; (magnitude >>> shift) != 0; shift += Short.SIZE) {
                asm.append("; MOVK ").append(register).append(", #").append((magnitude >>> shift) & 0xffff).append(", LSL #").append(shift);
            }
            return asm.toString();
        }

        @Override
        public String getAddInlineAssembly(String outputRegister, String inputRegister) {
            return "ADD " + getLLVMRegisterName(outputRegister) + ", " + getLLVMRegisterName(outputRegister) + ", " + getLLVMRegisterName(inputRegister);
        }

        @Override
        public String getNopInlineAssembly() {
            return "NOP";
        }

        @Override
        public String getJavaFrameAnchorIPInlineAssembly() {
            return "ADR $0, .+4";
        }

        @Override
        public String getLLVMArchName() {
            return "aarch64";
        }

        @Override
        public int getCallFrameSeparation() {
            return 0;
        }

        @Override
        public int getFramePointerOffset() {
            return -2 * SubstrateTarget.getWordSize();
        }

        @Override
        public long getCallerSPOffset() {
            return 2L * SubstrateTarget.getWordSize();
        }

        @Override
        public int getStackPointerDwarfRegNum() {
            return AARCH64_SP_IDX;
        }

        @Override
        public int getFramePointerDwarfRegNum() {
            return AARCH64_FP_IDX;
        }

        @Override
        public List<String> getLLCAdditionalOptions() {
            List<String> list = new ArrayList<>();
            list.add("--frame-pointer=all");
            list.add("--aarch64-frame-record-on-top");
            if (Platform.includedIn(Platform.IOS.class)) {
                list.add("-mtriple=arm64-ios");
            }
            return list;
        }

        @Override
        public String getLLVMRegisterName(String register) {
            return register.replace("r", "x");
        }

        @Override
        public String getScratchRegister() {
            return "x16";
        }

        @Override
        public String getTargetTriple() {
            return "aarch64" + LLVMTargetSpecific.super.getTargetTriple();
        }
    }
}

@AutomaticallyRegisteredFeature
@Platforms(Platform.RISCV64.class)
@SingletonTraits(access = BuildtimeAccessOnly.class, layeredCallbacks = NoLayeredCallbacks.class, other = Disallowed.class)
class LLVMRISCV64TargetSpecificFeature implements InternalFeature {
    private static final int RISCV64_FP_IDX = 8;
    private static final int RISCV64_SP_IDX = 2;

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return SubstrateOptions.useLLVMBackend();
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(LLVMTargetSpecific.class, new LLVMRISCV64TargetSpecific());
    }

    @SingletonTraits(access = BuildtimeAccessOnly.class, layeredCallbacks = NoLayeredCallbacks.class, other = Disallowed.class)
    private static final class LLVMRISCV64TargetSpecific implements LLVMTargetSpecific {
        @Override
        public String getRegisterInlineAsm(String register) {
            return "mv $0, " + getLLVMRegisterName(register);
        }

        @Override
        public String setRegisterInlineAsm(String register) {
            return "mv " + getLLVMRegisterName(register) + ", $0";
        }

        @Override
        public String getJumpInlineAsm() {
            return "jr $0";
        }

        @Override
        public String getLoadInlineAsm(String inputRegister, int offset) {
            return "ld $0, " + offset + "(" + getLLVMRegisterName(inputRegister) + ")";
        }

        @Override
        public String getLoadInlineAsm(String inputRegister, int offset, int sizeInBytes) {
            return switch (sizeInBytes) {
                case Byte.BYTES -> getLoadStoreInlineAsm("lb", "$0", inputRegister, offset);
                case Short.BYTES -> getLoadStoreInlineAsm("lh", "$0", inputRegister, offset);
                case Integer.BYTES -> getLoadStoreInlineAsm("lw", "$0", inputRegister, offset);
                case Long.BYTES -> getLoadStoreInlineAsm("ld", "$0", inputRegister, offset);
                default -> throw shouldNotReachHere("Unsupported load size: " + sizeInBytes); // ExcludeFromJacocoGeneratedReport
            };
        }

        @Override
        public String getStoreInlineAsm(String outputRegister, int offset, int sizeInBytes) {
            return switch (sizeInBytes) {
                case Byte.BYTES -> getLoadStoreInlineAsm("sb", "$0", outputRegister, offset);
                case Short.BYTES -> getLoadStoreInlineAsm("sh", "$0", outputRegister, offset);
                case Integer.BYTES -> getLoadStoreInlineAsm("sw", "$0", outputRegister, offset);
                case Long.BYTES -> getLoadStoreInlineAsm("sd", "$0", outputRegister, offset);
                default -> throw shouldNotReachHere("Unsupported store size: " + sizeInBytes); // ExcludeFromJacocoGeneratedReport
            };
        }

        @Override
        public String getFixedRegisterMemoryAccessScratchRegister(String baseRegister, int offset, int sizeInBytes) {
            return isLoadStoreImmediate(offset) ? null : getScratchRegister();
        }

        private String getLoadStoreInlineAsm(String instruction, String value, String baseRegister, int offset) {
            String base = getLLVMRegisterName(baseRegister);
            if (isLoadStoreImmediate(offset)) {
                return instruction + " " + value + ", " + offset + "(" + base + ")";
            }
            String scratch = getLLVMRegisterName(getScratchRegister());
            return "li " + scratch + ", " + offset + "; add " + scratch + ", " + base + ", " + scratch + "; " +
                            instruction + " " + value + ", 0(" + scratch + ")";
        }

        private static boolean isLoadStoreImmediate(int offset) {
            return offset >= -2048 && offset <= 2047;
        }

        @Override
        public String getAddInlineAssembly(String outputRegister, String inputRegister) {
            return "add " + getLLVMRegisterName(outputRegister) + ", " + getLLVMRegisterName(outputRegister) + ", " + getLLVMRegisterName(inputRegister);
        }

        @Override
        public String getNopInlineAssembly() {
            return "nop";
        }

        @Override
        public String getJavaFrameAnchorIPInlineAssembly() {
            return "auipc $0, 0\naddi $0, $0, 8";
        }

        @Override
        public String getLLVMArchName() {
            return "riscv64";
        }

        @Override
        public int getCallFrameSeparation() {
            return 0;
        }

        @Override
        public int getFramePointerOffset() {
            return 0;
        }

        @Override
        public long getCallerSPOffset() {
            return 0;
        }

        @Override
        public int getStackPointerDwarfRegNum() {
            return RISCV64_SP_IDX;
        }

        @Override
        public int getFramePointerDwarfRegNum() {
            return RISCV64_FP_IDX;
        }

        @Override
        public List<String> getLLCAdditionalOptions() {
            List<String> list = new ArrayList<>();
            list.add("--frame-pointer=all");
            list.add("-mattr=+c,+d");
            list.add("-target-abi=lp64d");
            return list;
        }

        @Override
        public String getScratchRegister() {
            return "x5";
        }

        @Override
        public boolean isSymbolValid(String section) {
            return !section.isEmpty() && !section.startsWith(".LBB") && !section.startsWith(".Lpcrel_hi");
        }

        @Override
        public int getInstructionOffset(ByteBuffer buffer, int offset, LLVMSectionIteratorRef relocationsSectionIteratorRef, LLVMRelocationIteratorRef relocationIteratorRef) {
            while (LLVM.LLVMIsRelocationIteratorAtEnd(relocationsSectionIteratorRef, relocationIteratorRef) == FALSE && offset != LLVM.LLVMGetRelocationOffset(relocationIteratorRef)) {
                LLVM.LLVMMoveToNextRelocation(relocationIteratorRef);
            }
            if (offset == LLVM.LLVMGetRelocationOffset(relocationIteratorRef)) {
                LLVMSymbolIteratorRef firstSymbol = LLVM.LLVMGetRelocationSymbol(relocationIteratorRef);
                LLVM.LLVMMoveToNextRelocation(relocationIteratorRef);
                assert offset == LLVM.LLVMGetRelocationOffset(relocationIteratorRef);
                LLVMSymbolIteratorRef secondSymbol = LLVM.LLVMGetRelocationSymbol(relocationIteratorRef);
                return (int) (LLVM.LLVMGetSymbolAddress(firstSymbol) - LLVM.LLVMGetSymbolAddress(secondSymbol));
            } else {
                throw shouldNotReachHere("Stack map has no relocation for offset " + offset);
            }
        }

        @Override
        public String getTargetTriple() {
            return "riscv64" + LLVMTargetSpecific.super.getTargetTriple();
        }
    }
}
