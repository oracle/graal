; ModuleID = 'mxbuild/linux-amd64/SULONG_EMBEDDED_TEST_SUITES/other/unsupported_inline_asm.c.dir/bitcode-O0-MEM2REG.bc'
source_filename = "llvm-link"
target datalayout = "e-m:e-p270:32:32-p271:32:32-p272:64:64-i64:64-f80:128-n8:16:32:64-S128"
target triple = "x86_64-unknown-linux-gnu"

define dso_local i32 @run(i32) {
  %2 = icmp sgt i32 %0, 1
  br i1 %2, label %3, label %4
3:
  call void asm sideeffect "unsupported_asm", "~{dirflag},~{fpsr},~{flags}"()
  br label %5
4:
  br label %5
5:
  %.0 = phi i32 [ 1, %3 ], [ 2, %4 ]
  ret i32 %.0
}
