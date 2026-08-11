// LLVMTargetSpecific.java への追加パッチ
// ファイルパス: substratevm/src/com.oracle.svm.core.graal.llvm/src/com/oracle/svm/core/graal/llvm/util/LLVMTargetSpecific.java
//
// 既存の LLVMRISCV64TargetSpecificFeature の後に追加する。

@AutomaticallyRegisteredFeature
@Platforms(Platform.ARM32.class)
class LLVMARM32TargetSpecificFeature implements InternalFeature {
    // ARM32 EABI DWARF register numbers:
    //   r0-r15: 0-15
    //   SP (r13): 13
    //   FP (r11): 11
    private static final int ARM32_FP_IDX = 11;
    private static final int ARM32_SP_IDX = 13;

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return SubstrateOptions.useLLVMBackend();
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(LLVMTargetSpecific.class, new LLVMARM32TargetSpecific());
    }

    @SingletonTraits(access = BuildtimeAccessOnly.class, layeredCallbacks = NoLayeredCallbacks.class, other = DisallowLayered.class)
    private static final class LLVMARM32TargetSpecific implements LLVMTargetSpecific {

        @Override
        public String getRegisterInlineAsm(String register) {
            // ARM32: MOV dst, src
            return "MOV $0, " + register;
        }

        @Override
        public String setRegisterInlineAsm(String register) {
            return "MOV " + register + ", $0";
        }

        @Override
        public String getJumpInlineAsm() {
            // ARM32: BX for branch-and-exchange (handles ARM/Thumb interworking)
            return "BX $0";
        }

        @Override
        public String getLoadInlineAsm(String inputRegister, int offset) {
            // LDR for 32-bit word load
            if (isLoadStoreImmediate(offset, Integer.BYTES)) {
                return "LDR $0, [" + inputRegister + ", #" + offset + "]";
            }
            String scratch = getScratchRegister();
            return "LDR " + scratch + ", =" + offset + "; ADD " + scratch + ", " + inputRegister + ", " + scratch + "; LDR $0, [" + scratch + "]";
        }

        @Override
        public String getLoadInlineAsm(String inputRegister, int offset, int sizeInBytes) {
            return switch (sizeInBytes) {
                case Byte.BYTES   -> getLoadStoreInlineAsm("LDRB", "$0", inputRegister, offset, sizeInBytes);
                case Short.BYTES  -> getLoadStoreInlineAsm("LDRH", "$0", inputRegister, offset, sizeInBytes);
                case Integer.BYTES -> getLoadStoreInlineAsm("LDR",  "$0", inputRegister, offset, sizeInBytes);
                default -> throw shouldNotReachHere("Unsupported load size: " + sizeInBytes);
            };
        }

        @Override
        public String getStoreInlineAsm(String outputRegister, int offset, int sizeInBytes) {
            return switch (sizeInBytes) {
                case Byte.BYTES   -> getLoadStoreInlineAsm("STRB", "$0", outputRegister, offset, sizeInBytes);
                case Short.BYTES  -> getLoadStoreInlineAsm("STRH", "$0", outputRegister, offset, sizeInBytes);
                case Integer.BYTES -> getLoadStoreInlineAsm("STR",  "$0", outputRegister, offset, sizeInBytes);
                default -> throw shouldNotReachHere("Unsupported store size: " + sizeInBytes);
            };
        }

        private String getLoadStoreInlineAsm(String instruction, String value, String baseRegister, int offset, int sizeInBytes) {
            if (isLoadStoreImmediate(offset, sizeInBytes)) {
                return instruction + " " + value + ", [" + baseRegister + ", #" + offset + "]";
            }
            String scratch = getScratchRegister();
            // Load offset magnitude then add to base
            return "LDR " + scratch + ", =" + offset + "; ADD " + scratch + ", " + baseRegister + ", " + scratch + "; " +
                   instruction + " " + value + ", [" + scratch + "]";
        }

        private static boolean isLoadStoreImmediate(int offset, int sizeInBytes) {
            // ARM32 LDR/STR: unsigned 12-bit offset (0-4095) aligned to access size
            return offset >= 0 && offset % sizeInBytes == 0 && offset / sizeInBytes <= 4095;
        }

        @Override
        public String getFixedRegisterMemoryAccessScratchRegister(String baseRegister, int offset, int sizeInBytes) {
            return isLoadStoreImmediate(offset, sizeInBytes) ? null : getScratchRegister();
        }

        @Override
        public String getAddInlineAssembly(String outputRegister, String inputRegister) {
            return "ADD " + outputRegister + ", " + outputRegister + ", " + inputRegister;
        }

        @Override
        public String getNopInlineAssembly() {
            return "NOP";
        }

        @Override
        public String getJavaFrameAnchorIPInlineAssembly() {
            // On ARM32, PC reads as current_instruction + 8 (ARM state) or +4 (Thumb state)
            // ADR calculates PC-relative address: target = PC + offset
            // "ADR $0, . + 8" captures current instruction address in ARM state
            return "ADR $0, .+8";
        }

        @Override
        public String getLLVMArchName() {
            return "arm";
        }

        @Override
        public int getCallFrameSeparation() {
            // ARM32: LR is pushed on the stack by PUSH {lr} in prologue
            // The call frame starts at the point before the PUSH, so separation = 4 (LR size)
            return FrameAccess.returnAddressSize();
        }

        @Override
        public int getFramePointerOffset() {
            // ARM32 AAPCS: prologue does PUSH {r11, lr}; MOV r11, sp
            // FP (r11) points to the pushed FP/LR pair
            // Frame pointer is at SP - 8 (relative to top of frame)
            return -2 * SubstrateTarget.getWordSize();
        }

        @Override
        public long getCallerSPOffset() {
            // Caller SP = callee FP + 8 (FP + LR saved)
            return 2L * SubstrateTarget.getWordSize();
        }

        @Override
        public int getStackPointerDwarfRegNum() {
            return ARM32_SP_IDX;  // r13
        }

        @Override
        public int getFramePointerDwarfRegNum() {
            return ARM32_FP_IDX;  // r11
        }

        @Override
        public List<String> getLLCAdditionalOptions() {
            List<String> list = new ArrayList<>();
            list.add("--frame-pointer=all");
            list.add("-mattr=+v7,+vfp3,+d16");
            list.add("-target-abi=aapcs-linux");
            return list;
        }

        @Override
        public String getScratchRegister() {
            // r12 (IP) is the ARM intra-procedure-call scratch register
            return "r12";
        }

        @Override
        public String getTargetTriple() {
            // Hard-float ABI for IS01 (EABIHF)
            return "armv7" + LLVMTargetSpecific.super.getTargetTriple() + "eabihf";
            // For soft-float: "armv7" + super.getTargetTriple() + "eabi"
        }
    }
}
