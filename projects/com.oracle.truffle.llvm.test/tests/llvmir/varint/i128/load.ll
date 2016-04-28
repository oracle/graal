target datalayout = "e-p:64:64:64-i1:8:8-i8:8:8-i16:16:16-i32:32:32-i64:64:64-f32:32:32-f64:64:64-v64:64:64-v128:128:128-a0:0:64-s0:64:64-f80:128:128-n8:16:32:64-S128"
target triple = "x86_64-unknown-linux-gnu"

define i1 @main() nounwind uwtable {
  %1 = alloca i128, align 16
  %2 = bitcast i128* %1 to { i64, i64 }*
  %3 = getelementptr { i64, i64 }* %2, i32 0, i32 0
  store i64 3259073885544336263, i64* %3
  %4 = getelementptr { i64, i64 }* %2, i32 0, i32 1
  store i64 66926059427634869, i64* %4
  %q = load i128* %1, align 16
  %5 = icmp eq i128 %q, 1234567890123456789012345678901234567
  ret i1 %5
}
