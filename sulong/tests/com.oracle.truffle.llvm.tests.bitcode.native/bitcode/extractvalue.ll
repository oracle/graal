; ModuleID = 'extractvalue.bc'
target datalayout = "e-m:e-i64:64-f80:128-n8:16:32:64-S128"
target triple = "x86_64-unknown-linux-gnu"

@formatInt = private unnamed_addr constant [4 x i8] c"%d\0A\00", align 1
@formatIntPtr = global i8* getelementptr inbounds ([4 x i8], [4 x i8]* @formatInt, i64 0, i64 0), align 8

; Function Attrs: nounwind
declare i32 @printf(i8* nocapture readonly, ...)

define void @printInt(i32 %x) {
	%format = load i8*, i8** @formatIntPtr, align 8
	tail call i32 (i8*, ...) @printf(i8* %format, i32 %x)
	ret void
}

%struct.foostruct = type { [3 x i32], { i32, [2 x i32] } }
@foo = internal global %struct.foostruct { [3 x i32] [i32 1, i32 2, i32 2], { i32, [2 x i32] } { i32 3, [2 x i32] [i32 4, i32 5] } }, align 4

; Function Attrs: nounwind ssp uwtable
define i32 @main() {
  %X0 = load %struct.foostruct, %struct.foostruct* @foo

  ; extract a sub-aggregate test
  %X1 = extractvalue %struct.foostruct %X0, 0
  %X2 = extractvalue [3 x i32] %X1, 0
  call void @printInt(i32 %X2)
  
  ; multiple indices test
  %X3 = extractvalue %struct.foostruct %X0, 0, 1
  call void @printInt(i32 %X3)

  %X4 = extractvalue %struct.foostruct %X0, 1, 1, 1
  call void @printInt(i32 %X4)

  ret i32 0
}
