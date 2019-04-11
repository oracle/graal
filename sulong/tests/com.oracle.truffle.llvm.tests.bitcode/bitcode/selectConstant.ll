; ModuleID = 'selectConstant.bc'
target datalayout = "e-m:e-i64:64-f80:128-n8:16:32:64-S128"
target triple = "x86_64-unknown-linux-gnu"

@g_A = global { i32, i32 } zeroinitializer
@g_B = global { i32, i32 } zeroinitializer

; Function Attrs: nounwind uwtable
define i32 @main() personality i8* null {
    %res = or i32 0, select (i1 icmp eq (i32* getelementptr ({ i32, i32 }, { i32, i32 }* @g_A, i32 0, i32 1), i32* getelementptr ({ i32, i32 }, { i32, i32 }* @g_B, i32 0, i32 0)), i32 1, i32 0)
    ret i32 %res
}
