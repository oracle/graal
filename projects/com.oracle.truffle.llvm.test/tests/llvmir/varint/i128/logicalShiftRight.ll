target datalayout = "e-p:64:64:64-i1:8:8-i8:8:8-i16:16:16-i32:32:32-i64:64:64-f32:32:32-f64:64:64-v64:64:64-v128:128:128-a0:0:64-s0:64:64-f80:128:128-n8:16:32:64-S128"
target triple = "x86_64-unknown-linux-gnu"

define i1 @main() nounwind uwtable {
  %1 = lshr i128 1234567890123456789012345678901234567, 64
  %2 = icmp eq i128 %1, 66926059427634869
  ret i1 %2
}
