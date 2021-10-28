; ModuleID = 'reduce.bc'
target datalayout = "e-m:e-i64:64-f80:128-n8:16:32:64-S128"
target triple = "x86_64-unknown-linux-gnu"

@formatInt = private unnamed_addr constant [4 x i8] c"%d\0A\00", align 1
@formatIntPtr = global i8* getelementptr inbounds ([4 x i8], [4 x i8]* @formatInt, i64 0, i64 0), align 8
@formatDouble = private unnamed_addr constant [4 x i8] c"%f\0A\00", align 1
@formatDoublePtr = global i8* getelementptr inbounds ([4 x i8], [4 x i8]* @formatDouble, i64 0, i64 0), align 8

; Function Attrs: nounwind
declare i32 @printf(i8* nocapture readonly, ...)

define void @printInt(i32 %x) {
	%format = load i8*, i8** @formatIntPtr, align 8
	tail call i32 (i8*, ...) @printf(i8* %format, i32 %x)
	ret void
}

define void @printDouble(double %x) {
	%format = load i8*, i8** @formatDoublePtr, align 8
	tail call i32 (i8*, ...) @printf(i8* %format, double %x)
	ret void
}

declare i32 @llvm.experimental.vector.reduce.add.v4i32(<4 x i32> %a)
declare i64 @llvm.experimental.vector.reduce.add.v4i64(<4 x i64> %a)
declare i32 @llvm.experimental.vector.reduce.mul.v4i32(<4 x i32> %a)
declare i64 @llvm.experimental.vector.reduce.mul.v4i64(<4 x i64> %a)
declare i32 @llvm.experimental.vector.reduce.and.v4i32(<4 x i32> %a)
declare i64 @llvm.experimental.vector.reduce.and.v4i64(<4 x i64> %a)
declare i32 @llvm.experimental.vector.reduce.or.v4i32(<4 x i32> %a)
declare i64 @llvm.experimental.vector.reduce.or.v4i64(<4 x i64> %a)
declare i32 @llvm.experimental.vector.reduce.xor.v4i32(<4 x i32> %a)
declare i64 @llvm.experimental.vector.reduce.xor.v4i64(<4 x i64> %a)
declare i32 @llvm.experimental.vector.reduce.smax.v4i32(<4 x i32> %a)
declare i64 @llvm.experimental.vector.reduce.smax.v4i64(<4 x i64> %a)
declare i32 @llvm.experimental.vector.reduce.umax.v4i32(<4 x i32> %a)
declare i64 @llvm.experimental.vector.reduce.umax.v4i64(<4 x i64> %a)
declare i32 @llvm.experimental.vector.reduce.smin.v4i32(<4 x i32> %a)
declare i64 @llvm.experimental.vector.reduce.smin.v4i64(<4 x i64> %a)
declare i32 @llvm.experimental.vector.reduce.umin.v4i32(<4 x i32> %a)
declare i64 @llvm.experimental.vector.reduce.umin.v4i64(<4 x i64> %a)

; Function Attrs: nounwind ssp uwtable
define i32 @main() {
	%X1_1 = call i32 @llvm.experimental.vector.reduce.add.v4i32(<4 x i32> <i32 1, i32 2, i32 3, i32 4>)
	call void @printInt(i32 %X1_1)

	%X1_2 = call i32 @llvm.experimental.vector.reduce.add.v4i32(<4 x i32> <i32 2147483647, i32 2147483647, i32 2147483647, i32 2147483647>)
	call void @printInt(i32 %X1_2)

	%X2 = call i64 @llvm.experimental.vector.reduce.add.v4i64(<4 x i64> <i64 1, i64 2, i64 3, i64 4>)
	%X3 = trunc i64 %X2 to i32
	call void @printInt(i32 %X3)

	%X4 = call i32 @llvm.experimental.vector.reduce.mul.v4i32(<4 x i32> <i32 1, i32 2, i32 3, i32 4>)
	call void @printInt(i32 %X4)

	%X5 = call i64 @llvm.experimental.vector.reduce.mul.v4i64(<4 x i64> <i64 1, i64 2, i64 3, i64 4>)
	%X6 = trunc i64 %X5 to i32
	call void @printInt(i32 %X6)

	%X7 = call i32 @llvm.experimental.vector.reduce.and.v4i32(<4 x i32> <i32 1, i32 3, i32 7, i32 15>)
	call void @printInt(i32 %X7)

	%X8 = call i64 @llvm.experimental.vector.reduce.and.v4i64(<4 x i64> <i64 1, i64 3, i64 7, i64 15>)
	%X9 = trunc i64 %X8 to i32
	call void @printInt(i32 %X9)

	%X10 = call i32 @llvm.experimental.vector.reduce.or.v4i32(<4 x i32> <i32 1, i32 2, i32 4, i32 8>)
	call void @printInt(i32 %X10)

	%X11 = call i64 @llvm.experimental.vector.reduce.or.v4i64(<4 x i64> <i64 1, i64 2, i64 4, i64 8>)
	%X12 = trunc i64 %X11 to i32
	call void @printInt(i32 %X12)

	%X13 = call i32 @llvm.experimental.vector.reduce.xor.v4i32(<4 x i32> <i32 1, i32 3, i32 4, i32 12>)
	call void @printInt(i32 %X13)

	%X14 = call i64 @llvm.experimental.vector.reduce.xor.v4i64(<4 x i64> <i64 1, i64 3, i64 4, i64 12>)
	%X15 = trunc i64 %X14 to i32
	call void @printInt(i32 %X15)

	%X16_1 = call i32 @llvm.experimental.vector.reduce.smax.v4i32(<4 x i32> <i32 1, i32 2, i32 3, i32 4>)
	call void @printInt(i32 %X16_1)

	%X16_2 = call i64 @llvm.experimental.vector.reduce.smax.v4i64(<4 x i64> <i64 1, i64 2, i64 3, i64 4>)
	%X16_3 = trunc i64 %X16_2 to i32
	call void @printInt(i32 %X16_3)

	%X17_1 = call i32 @llvm.experimental.vector.reduce.umax.v4i32(<4 x i32> <i32 -1, i32 2, i32 3, i32 4>)
	call void @printInt(i32 %X17_1)

	%X17_2 = call i64 @llvm.experimental.vector.reduce.umax.v4i64(<4 x i64> <i64 -1, i64 2, i64 3, i64 4>)
	%X17_3 = trunc i64 %X17_2 to i32
	call void @printInt(i32 %X17_3)

	%X18_1 = call i32 @llvm.experimental.vector.reduce.smin.v4i32(<4 x i32> <i32 1, i32 2, i32 3, i32 4>)
	call void @printInt(i32 %X18_1)

	%X18_2 = call i64 @llvm.experimental.vector.reduce.smin.v4i64(<4 x i64> <i64 1, i64 2, i64 3, i64 4>)
	%X18_3 = trunc i64 %X18_2 to i32
	call void @printInt(i32 %X18_3)

	%X19_1 = call i32 @llvm.experimental.vector.reduce.umin.v4i32(<4 x i32> <i32 -1, i32 2, i32 3, i32 4>)
	call void @printInt(i32 %X19_1)

	%X19_2 = call i64 @llvm.experimental.vector.reduce.umin.v4i64(<4 x i64> <i64 -1, i64 2, i64 3, i64 4>)
	%X19_3 = trunc i64 %X19_2 to i32
	call void @printInt(i32 %X19_3)

	ret i32 0
}
