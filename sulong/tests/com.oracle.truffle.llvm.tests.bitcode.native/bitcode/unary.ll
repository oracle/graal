; ModuleID = 'unary.bc'
target datalayout = "e-m:e-i64:64-f80:128-n8:16:32:64-S128"
target triple = "x86_64-pc-linux-gnu"

@format = private unnamed_addr constant [7 x i8] c"%f %f\0A\00", align 1
@formatPtr = global i8* getelementptr inbounds ([7 x i8], [7 x i8]* @format, i64 0, i64 0), align 8

; Function Attrs: nounwind
declare i32 @printf(i8* nocapture readonly, ...)

define void @test(float %x, double %y) {
	%negx = fneg float %x
	%negy = fneg double %y 

	%negx2 = fpext float %negx to double
	%fmt = load i8*, i8** @formatPtr, align 8
	tail call i32 (i8*, ...) @printf(i8* %fmt, double %negx2, double %negy)
	
	ret void
}

; Function Attrs: nounwind ssp uwtable
define i32 @main() {
	call void (float, double) @test(float 1.25, double 1.25)
	ret i32 0
}
