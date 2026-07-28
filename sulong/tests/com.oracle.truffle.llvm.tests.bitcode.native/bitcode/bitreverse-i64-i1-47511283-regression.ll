declare i1 @llvm.bitreverse.i1(i1)
declare i64 @llvm.bitreverse.i64(i64)

define i32 @main() {
entry:
  %reversed-i1-false = call i1 @llvm.bitreverse.i1(i1 false)
  %reversed-i1-true = call i1 @llvm.bitreverse.i1(i1 true)
  %reversed-i64 = call i64 @llvm.bitreverse.i64(i64 81985529216486895)
  %i1-false-correct = icmp eq i1 %reversed-i1-false, false
  %i1-true-correct = icmp eq i1 %reversed-i1-true, true
  %i64-correct = icmp eq i64 %reversed-i64, -597899502893742976
  %i1-correct = and i1 %i1-false-correct, %i1-true-correct
  %correct = and i1 %i1-correct, %i64-correct
  %exit = select i1 %correct, i32 0, i32 1
  ret i32 %exit
}
