# BOXP-152: GraalVM ARM32 実装ロードマップ (2026-08-11 調査結果)

## 調査結論

**LLVMバックエンド経由の ARM32 対応は技術的に可能だが、3つのアップストリームプロジェクトへの貢献が必要。**

### 発見した追加ブロッカー (grooming 時点では未確認)

| ブロッカー | 内容 | 先行事例 |
|---|---|---|
| **LLVM Statepoints 非対応** | LLVM の Statepoints (GC セーフポイント) は AArch64 と x86-64 のみ対応。ARM32 なし。 | RISC-V 対応: llvm/llvm-project PR #77337 (2023) |
| **JVMCI ARM32 不在** | GraalVM JDK (`labs-openjdk-21`) に `jdk.vm.ci.arm` (32bit) モジュールが存在しない | RISC-V 対応: `jdk.vm.ci.riscv64` 追加 |

### 修正した実装スコープ

| プロジェクト | 必要作業 | 規模 |
|---|---|---|
| `llvm/llvm-project` | ARM32 statepoint lowering 実装 | 大 (数百行, 1-3ヶ月) |
| `graalvm/labs-openjdk-21` | `jdk.vm.ci.arm` モジュール追加 | 中 (2ファイル, 2-3週間) |
| `oracle/graal` | ARM32 プラットフォーム追加 | 小 (5ファイル, 1-2週間) |

**合計推定期間: 6-12ヶ月** (RISC-V の約6ヶ月より長い。LLVM statepoint 作業が追加されるため)

---

## プロジェクト 1: llvm/llvm-project

### ARM32 Statepoint Lowering

RISC-V statepoint PR (#77337) を参考に ARM32 版を実装。

**変更対象ファイル:**
```
llvm/lib/Target/ARM/ARMISelLowering.cpp   ← STATEPOINT/PATCHPOINT 処理追加
llvm/lib/Target/ARM/ARMISelLowering.h     ← 宣言追加
llvm/test/CodeGen/ARM/statepoint-*.ll     ← テスト追加
```

**実装の概要:**
- `ARMTargetLowering::LowerSTATEPOINT()` 追加
- `ARMTargetLowering::LowerPATCHPOINT()` 追加  
- `ARMTargetLowering::LowerSTACKMAP()` 追加
- EABI (AAPCS) 呼び出し規約に沿ったレジスタ保存

---

## プロジェクト 2: graalvm/labs-openjdk-21

### jdk.vm.ci.arm モジュール追加

**新規ファイル:**
```
src/jdk.internal.vm.ci/share/classes/jdk/vm/ci/arm/ARM.java
src/jdk.internal.vm.ci/share/classes/jdk/vm/ci/arm/ARMKind.java
src/jdk.internal.vm.ci/share/classes/jdk/vm/ci/arm/package-info.java
```

コードは `impl/labs-openjdk-arm32/` ディレクトリ参照。

---

## プロジェクト 3: oracle/graal

### ARM32 プラットフォーム追加

RISC-V64 の実装 (3ファイル + LLVMTargetSpecific 追加) を参考に実装。

**変更/追加ファイル:**
```
sdk/src/org.graalvm.nativeimage/src/org/graalvm/nativeimage/Platform.java
  ← ARM32 interface と LINUX_ARM32 class 追加

substratevm/src/com.oracle.svm.core.graal.arm32/
  ├── ARM32ReservedRegisters.java    (新規)
  ├── SubstrateARM32Feature.java     (新規)
  └── SubstrateARM32RegisterConfig.java (新規)

substratevm/src/com.oracle.svm.core.graal.llvm/
  └── util/LLVMTargetSpecific.java   ← ARM32 class 追加

substratevm/mx.substratevm/suite.py   ← モジュール登録
```

コードスタブは `impl/graal-arm32/` ディレクトリ参照。

---

## ARM32 固有の技術考慮事項

### レジスタマッピング (AAPCS / EABI)

| レジスタ | 役割 | DWARF 番号 |
|---|---|---|
| r0-r3 | 引数/戻り値 | 0-3 |
| r4-r11 | 呼び出し保存 | 4-11 |
| r11 | Frame Pointer (FP) | 11 |
| r12 | Intra-Procedure-call scratch (IP) | 12 |
| r13 | Stack Pointer (SP) | 13 |
| r14 | Link Register (LR) | 14 |
| r15 | Program Counter (PC) | 15 |
| s0-s31 | VFP 単精度 | 64-95 |
| d0-d15 | VFP 倍精度 | 256-271 |

**ThreadRegister**: `r9` (Linux/EABI の典型的な Thread Pointer)
**HeapBase**: `r8` (候補)
**ScratchRegister**: `r12` (IP)

### スタックフレーム (AAPCS)

```
高アドレス
  [引数 (スタック渡し)]
  [LR (戻りアドレス)]   ← SP + frameSize
  [FP (r11)]
  [... ローカル変数 ...]
  [... スピル ... ]
低アドレス ← SP (現在)
```

`getCallFrameSeparation()`: 4 (LR のプッシュ分)
`getFramePointerOffset()`: -8 (FP が SP より 8 バイト上)
`getCallerSPOffset()`: 8L (LR + FP)

### LLVMArchName と target-triple

```
getLLVMArchName(): "arm" または "armv7"
getTargetTriple(): "armv7-unknown-linux-gnueabihf"  (hard-float)
                   "armv7-unknown-linux-gnueabi"    (soft-float)
```

### LLVM オプション

```java
getLLCAdditionalOptions():
  "--frame-pointer=all"
  "-mattr=+v7,+vfp3,+d16"
  "-target-abi=aapcs-linux"
```

### インラインアセンブリ (ARM32 Thumb2 対応)

```java
getRegisterInlineAsm(reg):  "MOV $0, " + reg
setRegisterInlineAsm(reg):  "MOV " + reg + ", $0"
getJumpInlineAsm():         "BX $0"
getLoadInlineAsm(r, off):   "LDR $0, [" + r + ", #" + off + "]"
getNopInlineAssembly():     "NOP"
getJavaFrameAnchorIPInlineAssembly(): "MOV $0, pc"  // or ADR based encoding
```

---

## 作業優先順位

### 即座に着手できる作業 (ホスト不要)

1. **llvm/llvm-project に ARM32 statepoint PR 作成** → 最長リードタイム
2. **labs-openjdk-21 に jdk.vm.ci.arm 追加** → statepoint 作業と並行可能

### llvm statepoint マージ後

3. **oracle/graal に ARM32 platform 追加** → コードスタブ準備済み

### ビルド検証

4. `mx build` で GraalVM ARM32 ビルド通過確認
5. Hello World ARM32 binary を qemu-arm で実行確認
