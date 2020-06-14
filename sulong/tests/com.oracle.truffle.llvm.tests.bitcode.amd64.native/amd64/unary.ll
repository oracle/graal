; ModuleID = 'unary.bc'
target datalayout = "e-m:e-i64:64-f80:128-n8:16:32:64-S128"
target triple = "x86_64-pc-linux-gnu"

@format = private unnamed_addr constant [4 x i8] c"%f\0A\00", align 1
@formatPtr = global i8* getelementptr inbounds ([4 x i8], [4 x i8]* @format, i64 0, i64 0), align 8

; Function Attrs: nounwind
declare i32 @printf(i8* nocapture readonly, ...)

define void @printFP80(x86_fp80 %x) {
	%x2 = fptrunc x86_fp80 %x to double

	%fmt = load i8*, i8** @formatPtr, align 8
	tail call i32 (i8*, ...) @printf(i8* %fmt, double %x2)

	ret void
}

define void @test(x86_fp80 %x) {
	%negx = fneg x86_fp80 %x

	%fmt = load i8*, i8** @formatPtr, align 8
	call void (x86_fp80) @printFP80(x86_fp80 %negx)
	
	ret void
}

; Function Attrs: nounwind ssp uwtable
define i32 @main() {
	%x = fpext float 1.25 to x86_fp80
	call void (x86_fp80) @test(x86_fp80 %x)
	ret i32 0
}
