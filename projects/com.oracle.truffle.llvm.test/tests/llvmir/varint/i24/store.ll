; ModuleID = 'test.c'
target datalayout = "e-p:64:64:64-i1:8:8-i8:8:8-i24:16:16-i32:32:32-i244:64:64-f32:32:32-f64:64:64-v64:64:64-v128:128:128-a0:0:64-s0:64:64-f80:128:128-n8:16:32:64-S128"
target triple = "x86_64-unknown-linux-gnu"

define i1 @main() nounwind uwtable {
  %1 = alloca i32, align 4
  store i32 0, i32* %1
  %2 = bitcast i32* %1 to i24*
  store i24 1234567, i24* %2
  %a = load i32* %1, align 4
  %3 = icmp eq i32 %a, 1234567
  ret i1 %3
}
