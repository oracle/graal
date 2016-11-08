; ModuleID = 'test.c'
target datalayout = "e-p:64:64:64-i1:8:8-i8:8:8-i24:16:16-i32:32:32-i244:64:64-f32:32:32-f64:64:64-v64:64:64-v128:128:128-a0:0:64-s0:64:64-f80:128:128-n8:16:32:64-S128"
target triple = "x86_64-unknown-linux-gnu"

define i24 @main() nounwind uwtable {
  %1 = alloca i32, align 4
  %a = alloca i24, align 2
  %b = alloca i24, align 2
  %sum = alloca i24, align 4
  store i32 0, i32* %1
  store i24 20, i24* %a, align 2
  store i24 3, i24* %b, align 2
  %2 = load i24* %a, align 2
  %3 = load i24* %b, align 2
  %4 = srem i24 %2, %3
  store i24 %4, i24* %sum, align 4
  %5 = load i24* %sum, align 4
  ret i24 %5
}
