; ModuleID = 'fpcasts.bc'
target datalayout = "e-m:e-i64:64-f80:128-n8:16:32:64-S128"
target triple = "x86_64-pc-linux-gnu"

@formatDouble = private unnamed_addr constant [4 x i8] c"%f\0A\00", align 1
@formatDoublePtr = global i8* getelementptr inbounds ([4 x i8], [4 x i8]* @formatDouble, i64 0, i64 0), align 8

; Function Attrs: nounwind
declare i32 @printf(i8* nocapture readonly, ...)

define void @printDouble(double %x) {
	%format = load i8*, i8** @formatDoublePtr, align 8
	tail call i32 (i8*, ...) @printf(i8* %format, double %x)

	ret void
}

declare double @llvm.experimental.constrained.fpext.f32(float, metadata)
                                    
; Function Attrs: nounwind ssp uwtable
define i32 @main() {
	%X = fpext float 1.25 to double
	call void (double) @printDouble(double %X)
	
	%Y = call double @llvm.experimental.constrained.fpext.f32(float 1.25, metadata !"fpexcept.strict")
	call void (double) @printDouble(double %Y)
	
	ret i32 0
}
