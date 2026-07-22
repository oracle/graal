target triple = "x86_64-unknown-linux-gnu"

define internal i32 @answer() {
  ret i32 42
}

define i32 @main() {
  %pointer = ptrtoint ptr @answer to i64
  %alignment.bit = and i64 %pointer, 1
  %aligned = icmp eq i64 %alignment.bit, 0
  %answer = call i32 @answer()
  %answer.ok = icmp eq i32 %answer, 42
  %ok = and i1 %aligned, %answer.ok
  %result = select i1 %ok, i32 0, i32 1
  ret i32 %result
}
