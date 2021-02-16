; ModuleID = 'anon-struct.bc'
target datalayout = "e-m:e-i64:64-f80:128-n8:16:32:64-S128"
target triple = "x86_64-unknown-linux-gnu"

%"<anon>" = type { i32, %"<anon>"* }

@my_cell = common global %"<anon>" zeroinitializer, align 8

define i32 @main() {
  %1 = getelementptr inbounds %"<anon>", %"<anon>"* @my_cell, i64 0, i32 0
  store i32 42, i32* %1, align 8
  %2 = load i32, i32* %1, align 8
  ret i32 %2
}
