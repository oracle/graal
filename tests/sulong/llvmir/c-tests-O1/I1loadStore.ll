; ModuleID = '/home/manuel/phd/dev/graal-llvm/llvm/com.oracle.truffle.llvm.test/tests/c/truffle-c/pointerTest/pointerTest12.c'
target datalayout = "e-p:64:64:64-i1:8:8-i8:8:8-i16:16:16-i32:32:32-i64:64:64-f32:32:32-f64:64:64-v64:64:64-v128:128:128-a0:0:64-s0:64:64-f80:128:128-n8:16:32:64-S128"
target triple = "x86_64-unknown-linux-gnu"

@func.initialized.b = internal unnamed_addr global i1 false
@a = common global i32* null, align 8

define i32* @func() nounwind uwtable {
  %.b = load i1* @func.initialized.b, align 1
  br i1 %.b, label %4, label %1

; <label>:1                                       ; preds = %0
  %2 = tail call noalias i8* @malloc(i64 4) nounwind
  %3 = bitcast i8* %2 to i32*
  store i32* %3, i32** @a, align 8, !tbaa !0
  store i1 true, i1* @func.initialized.b, align 1
  store i32 4, i32* %3, align 4, !tbaa !3
  br label %4

; <label>:4                                       ; preds = %1, %0
  %5 = load i32** @a, align 8, !tbaa !0
  ret i32* %5
}

declare noalias i8* @malloc(i64) nounwind

define i32 @main() nounwind uwtable {
  %1 = tail call i32* @func()
  %2 = load i32* %1, align 4, !tbaa !3
  %3 = add nsw i32 %2, 1
  store i32 %3, i32* %1, align 4, !tbaa !3
  %4 = load i32** @a, align 8, !tbaa !0
  %5 = load i32* %4, align 4, !tbaa !3
  %6 = add nsw i32 %5, 1
  store i32 %6, i32* %4, align 4, !tbaa !3
  %7 = tail call i32* @func()
  %8 = load i32* %7, align 4, !tbaa !3
  ret i32 %8
}

!0 = metadata !{metadata !"any pointer", metadata !1}
!1 = metadata !{metadata !"omnipotent char", metadata !2}
!2 = metadata !{metadata !"Simple C/C++ TBAA"}
!3 = metadata !{metadata !"int", metadata !1}
