// Platform.java への追加パッチ
// ファイルパス: sdk/src/org.graalvm.nativeimage/src/org/graalvm/nativeimage/Platform.java
//
// AARCH64 interface の後、RISCV64 の前（またはRISCV64の後）に追加する。

    /**
     * Supported architecture: ARM 32-bit (ARMv7 / EABI / EABIHF).
     *
     * @since 26.0
     */
    interface ARM32 extends Platform, InternalPlatform.NATIVE_ONLY {

        /**
         * Returns string representing ARM32 architecture.
         *
         * @since 26.0
         */
        default String getArchitecture() {
            return "arm32";
        }
    }

// ---

// LINUX_ARM32 と LINUX_ARMHF を leaf platform として追加する。
// 既存の LINUX_RISCV64 の後に追加:

    /**
     * Supported platform: Linux on ARMv7 with soft-float ABI (EABI).
     *
     * @since 26.0
     */
    final class LINUX_ARM32 implements LINUX, ARM32 {
        /**
         * Instantiates a marker instance of this platform.
         *
         * @since 26.0
         */
        @Platforms(Platform.HOSTED_ONLY.class)
        public LINUX_ARM32() {
        }
    }

    /**
     * Supported platform: Linux on ARMv7 with hard-float ABI (EABIHF).
     * This is the preferred target for IS01 and most modern ARMv7 Linux systems.
     *
     * @since 26.0
     */
    final class LINUX_ARM32HF implements LINUX, ARM32 {
        /**
         * Instantiates a marker instance of this platform.
         *
         * @since 26.0
         */
        @Platforms(Platform.HOSTED_ONLY.class)
        public LINUX_ARM32HF() {
        }
    }
