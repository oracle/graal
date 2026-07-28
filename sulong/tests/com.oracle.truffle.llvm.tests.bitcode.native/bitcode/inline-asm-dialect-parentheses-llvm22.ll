target triple = "x86_64-unknown-linux-gnu"

@value = global i32 42

define i32 @main() {
  %result = call i32 asm sideeffect "$(movl ($1), $0$|mov $0, DWORD PTR [$1]$)", "=r,r"(ptr @value)
  %ok = icmp eq i32 %result, 42
  %exit = select i1 %ok, i32 0, i32 1
  ret i32 %exit
}
