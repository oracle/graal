; ModuleID = 'test.ll'
target datalayout = "e-p:64:64:64-i1:8:8-i8:8:8-i16:16:16-i16:32:32-i64:64:64-f32:32:32-f64:64:64-v64:64:64-v128:128:128-a0:0:64-s0:64:64-f80:128:128-n8:16:32:64-S128"
target triple = "x86_64-unknown-linux-gnu"

@test = global i16 1, align 2

define i16 @main() nounwind uwtable {
  %1 = load volatile i16* @test, align 4
  switch i16 %1, label %5 [
    i16 0, label %2
    i16 1, label %3
    i16 2, label %4
  ]

; <label>:2                                       ; preds = %0
  br label %6

; <label>:3                                       ; preds = %0
  br label %6

; <label>:4                                       ; preds = %0
  br label %6

; <label>:5                                       ; preds = %0
  br label %6

; <label>:6                                       ; preds = %5, %4, %3, %2
  %.0 = phi i16 [ -1, %5 ], [ 5, %4 ], [ 3, %3 ], [ 1, %2 ]
  ret i16 %.0
}
