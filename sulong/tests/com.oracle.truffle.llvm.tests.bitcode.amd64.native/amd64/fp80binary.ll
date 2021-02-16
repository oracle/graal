; ModuleID = 'fp80binary.bc'
target datalayout = "e-m:e-i64:64-f80:128-n8:16:32:64-S128"
target triple = "x86_64-pc-linux-gnu"

@formatBytes = private unnamed_addr constant [31 x i8] c"%x %x %x %x %x %x %x %x %x %x\0A\00", align 1
@formatBytesPtr = global i8* getelementptr inbounds ([31 x i8], [31 x i8]* @formatBytes, i64 0, i64 0), align 8

; Function Attrs: nounwind
declare i32 @printf(i8* nocapture readonly, ...)

define void @printFP80(x86_fp80 %x) {
	%S = bitcast x86_fp80 %x to <10 x i8>
	%R0 = extractelement <10 x i8> %S, i32 0
	%S0 = zext i8 %R0 to i32
	%R1 = extractelement <10 x i8> %S, i32 1
	%S1 = zext i8 %R1 to i32
	%R2 = extractelement <10 x i8> %S, i32 2
	%S2 = zext i8 %R2 to i32
	%R3 = extractelement <10 x i8> %S, i32 3
	%S3 = zext i8 %R3 to i32
	%R4 = extractelement <10 x i8> %S, i32 4
	%S4 = zext i8 %R4 to i32
	%R5 = extractelement <10 x i8> %S, i32 5
	%S5 = zext i8 %R5 to i32
	%R6 = extractelement <10 x i8> %S, i32 6
	%S6 = zext i8 %R6 to i32
	%R7 = extractelement <10 x i8> %S, i32 7
	%S7 = zext i8 %R7 to i32
	%R8 = extractelement <10 x i8> %S, i32 8
	%S8 = zext i8 %R8 to i32
	%R9 = extractelement <10 x i8> %S, i32 9
	%S9 = zext i8 %R9 to i32

	%format = load i8*, i8** @formatBytesPtr, align 8
	tail call i32 (i8*, ...) @printf(i8* %format, i32 %S0, i32 %S1, i32 %S2, i32 %S3, i32 %S4, i32 %S5, i32 %S6, i32 %S7, i32 %S8, i32 %S9)

	ret void
}

declare x86_fp80 @llvm.experimental.constrained.fmul.f80(x86_fp80, x86_fp80, metadata, metadata)
declare x86_fp80 @llvm.experimental.constrained.fdiv.f80(x86_fp80, x86_fp80, metadata, metadata)
declare x86_fp80 @llvm.experimental.constrained.frem.f80(x86_fp80, x86_fp80, metadata, metadata)
declare x86_fp80 @llvm.experimental.constrained.fadd.f80(x86_fp80, x86_fp80, metadata, metadata)
declare x86_fp80 @llvm.experimental.constrained.fsub.f80(x86_fp80, x86_fp80, metadata, metadata)

; Function Attrs: nounwind ssp uwtable
define i32 @main() {
	%x = fpext double 10.25 to x86_fp80
	%y = fpext double 5.25 to x86_fp80

	%res1 = call x86_fp80 @llvm.experimental.constrained.fmul.f80(x86_fp80 %x, x86_fp80 %y, metadata !"round.dynamic", metadata !"fpexcept.strict")
	call void (x86_fp80) @printFP80(x86_fp80 %res1)

	%res2 = call x86_fp80 @llvm.experimental.constrained.fdiv.f80(x86_fp80 %x, x86_fp80 %y, metadata !"round.dynamic", metadata !"fpexcept.strict")
	call void (x86_fp80) @printFP80(x86_fp80 %res2)
	
	%res3 = call x86_fp80 @llvm.experimental.constrained.frem.f80(x86_fp80 %x, x86_fp80 %y, metadata !"round.dynamic", metadata !"fpexcept.strict")
	call void (x86_fp80) @printFP80(x86_fp80 %res3)

	%res4 = call x86_fp80 @llvm.experimental.constrained.fadd.f80(x86_fp80 %x, x86_fp80 %y, metadata !"round.dynamic", metadata !"fpexcept.strict")
	call void (x86_fp80) @printFP80(x86_fp80 %res4)

	%res5 = call x86_fp80 @llvm.experimental.constrained.fsub.f80(x86_fp80 %x, x86_fp80 %y, metadata !"round.dynamic", metadata !"fpexcept.strict")
	call void (x86_fp80) @printFP80(x86_fp80 %res5)

	ret i32 0
}
