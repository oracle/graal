; ModuleID = 'binary.bc'
target datalayout = "e-m:e-i64:64-f80:128-n8:16:32:64-S128"
target triple = "x86_64-pc-linux-gnu"

@format = private unnamed_addr constant [5 x i8] c"%lf\0A\00", align 1
@formatPtr = global i8* getelementptr inbounds ([5 x i8], [5 x i8]* @format, i64 0, i64 0), align 8

; Function Attrs: nounwind
declare i32 @printf(i8* nocapture readonly, ...)

declare float @llvm.experimental.constrained.fmul.f32(float, float, metadata, metadata)
declare double @llvm.experimental.constrained.fmul.f64(double, double, metadata, metadata)

declare float @llvm.experimental.constrained.fdiv.f32(float, float, metadata, metadata)
declare double @llvm.experimental.constrained.fdiv.f64(double, double, metadata, metadata)

declare float @llvm.experimental.constrained.frem.f32(float, float, metadata, metadata)
declare double @llvm.experimental.constrained.frem.f64(double, double, metadata, metadata)

declare float @llvm.experimental.constrained.fadd.f32(float, float, metadata, metadata)
declare double @llvm.experimental.constrained.fadd.f64(double, double, metadata, metadata)

declare float @llvm.experimental.constrained.fsub.f32(float, float, metadata, metadata)
declare double @llvm.experimental.constrained.fsub.f64(double, double, metadata, metadata)

; Function Attrs: nounwind ssp uwtable
define i32 @main() {
	%fmt = load i8*, i8** @formatPtr, align 8

	; fmul
	%res1 = call float @llvm.experimental.constrained.fmul.f32(float 1.25, float 1.5, metadata !"round.dynamic", metadata !"fpexcept.strict")
	%res1Dbl = fpext float %res1 to double
	tail call i32 (i8*, ...) @printf(i8* %fmt, double %res1Dbl)

	%res2 = call double @llvm.experimental.constrained.fmul.f64(double 1.25, double 1.5, metadata !"round.dynamic", metadata !"fpexcept.strict")
	tail call i32 (i8*, ...) @printf(i8* %fmt, double %res2)

	; fdiv
	%res3 = call float @llvm.experimental.constrained.fdiv.f32(float 1.25, float 1.5, metadata !"round.dynamic", metadata !"fpexcept.strict")
	%res3Dbl = fpext float %res3 to double
	tail call i32 (i8*, ...) @printf(i8* %fmt, double %res3Dbl)

	%res4 = call double @llvm.experimental.constrained.fdiv.f64(double 1.25, double 1.5, metadata !"round.dynamic", metadata !"fpexcept.strict")
	tail call i32 (i8*, ...) @printf(i8* %fmt, double %res4)

	; frem
	%res5 = call float @llvm.experimental.constrained.frem.f32(float 10.25, float 5.5, metadata !"round.dynamic", metadata !"fpexcept.strict")
	%res5Dbl = fpext float %res5 to double
	tail call i32 (i8*, ...) @printf(i8* %fmt, double %res5Dbl)

	%res6 = call double @llvm.experimental.constrained.frem.f64(double 10.25, double 5.5, metadata !"round.dynamic", metadata !"fpexcept.strict")
	tail call i32 (i8*, ...) @printf(i8* %fmt, double %res6)

	; fadd
	%res7 = call float @llvm.experimental.constrained.fadd.f32(float 1.25, float 1.5, metadata !"round.dynamic", metadata !"fpexcept.strict")
	%res7Dbl = fpext float %res7 to double
	tail call i32 (i8*, ...) @printf(i8* %fmt, double %res7Dbl)

	%res8 = call double @llvm.experimental.constrained.fadd.f64(double 1.25, double 1.5, metadata !"round.dynamic", metadata !"fpexcept.strict")
	tail call i32 (i8*, ...) @printf(i8* %fmt, double %res8)
	
	; fadd
	%res9 = call float @llvm.experimental.constrained.fsub.f32(float 1.25, float 1.5, metadata !"round.dynamic", metadata !"fpexcept.strict")
	%res9Dbl = fpext float %res9 to double
	tail call i32 (i8*, ...) @printf(i8* %fmt, double %res9Dbl)

	%res10 = call double @llvm.experimental.constrained.fsub.f64(double 1.25, double 1.5, metadata !"round.dynamic", metadata !"fpexcept.strict")
	tail call i32 (i8*, ...) @printf(i8* %fmt, double %res10)

	ret i32 0
}
