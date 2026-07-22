declare i32 @llvm.bitreverse.i32(i32)

define i32 @main() {
entry:
  %reversed = call i32 @llvm.bitreverse.i32(i32 305419896)
  %correct = icmp eq i32 %reversed, 510274632
  %exit = select i1 %correct, i32 0, i32 1
  ret i32 %exit
}
