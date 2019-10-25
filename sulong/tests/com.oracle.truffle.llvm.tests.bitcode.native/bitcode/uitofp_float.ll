; ModuleID = 'uitofp_float_mod.bc'
target datalayout = "e-m:e-i64:64-f80:128-n8:16:32:64-S128"
target triple = "x86_64-unknown-linux-gnu"

@.str = private unnamed_addr constant [11 x i8] c"%30u : %f\0A\00", align 1
@.str.1 = private unnamed_addr constant [12 x i8] c"%30lu : %f\0A\00", align 1
@str = private unnamed_addr constant [13 x i8] c"testI8Scalar\00", align 1
@str.10 = private unnamed_addr constant [14 x i8] c"testI16Scalar\00", align 1
@str.11 = private unnamed_addr constant [14 x i8] c"testI32Scalar\00", align 1
@str.12 = private unnamed_addr constant [14 x i8] c"testI64Scalar\00", align 1
@str.13 = private unnamed_addr constant [13 x i8] c"testI8Vector\00", align 1
@str.14 = private unnamed_addr constant [14 x i8] c"testI16Vector\00", align 1
@str.15 = private unnamed_addr constant [14 x i8] c"testI32Vector\00", align 1
@str.16 = private unnamed_addr constant [14 x i8] c"testI64Vector\00", align 1

; Function Attrs: nounwind uwtable
define internal void @testI8Scalar(i8 zeroext) #0 {
  %2 = uitofp i8 %0 to float
  %3 = zext i8 %0 to i32
  %4 = fpext float %2 to double
  %5 = tail call i32 (i8*, ...) @printf(i8* getelementptr inbounds ([11 x i8], [11 x i8]* @.str, i64 0, i64 0), i32 %3, double %4)
  ret void
}

; Function Attrs: nounwind readnone speculatable
declare void @llvm.dbg.value(metadata, metadata, metadata) #1

; Function Attrs: nounwind
declare i32 @printf(i8* nocapture readonly, ...) #2

; Function Attrs: nounwind uwtable
define internal void @testI16Scalar(i16 zeroext) #0 {
  %2 = uitofp i16 %0 to float
  %3 = zext i16 %0 to i32
  %4 = fpext float %2 to double
  %5 = tail call i32 (i8*, ...) @printf(i8* getelementptr inbounds ([11 x i8], [11 x i8]* @.str, i64 0, i64 0), i32 %3, double %4)
  ret void
}

; Function Attrs: nounwind uwtable
define internal void @testI32Scalar(i32) #0 {
  %2 = uitofp i32 %0 to float
  %3 = fpext float %2 to double
  %4 = tail call i32 (i8*, ...) @printf(i8* getelementptr inbounds ([11 x i8], [11 x i8]* @.str, i64 0, i64 0), i32 %0, double %3)
  ret void
}

; Function Attrs: nounwind uwtable
define internal void @testI64Scalar(i64) #0 {
  %2 = uitofp i64 %0 to float
  %3 = fpext float %2 to double
  %4 = tail call i32 (i8*, ...) @printf(i8* getelementptr inbounds ([12 x i8], [12 x i8]* @.str.1, i64 0, i64 0), i64 %0, double %3)
  ret void
}

; Function Attrs: nounwind uwtable
define internal void @testI8Vector(i8 zeroext) #0 {
  %tmp = insertelement <2 x i8> undef, i8 %0, i32 0
  %uivec = insertelement <2 x i8> %tmp, i8 %0, i32 1
  %fpvec = uitofp <2 x i8> %uivec to <2 x float>
  %fp0 = extractelement <2 x float> %fpvec, i32 0
  %fp1 = extractelement <2 x float> %fpvec, i32 1
  %dp0 = fpext float %fp0 to double
  %dp1 = fpext float %fp1 to double
  %arg32 = zext i8 %0 to i32
  tail call i32 (i8*, ...) @printf(i8* getelementptr inbounds ([11 x i8], [11 x i8]* @.str, i64 0, i64 0), i32 %arg32, double %dp0)
  tail call i32 (i8*, ...) @printf(i8* getelementptr inbounds ([11 x i8], [11 x i8]* @.str, i64 0, i64 0), i32 %arg32, double %dp1)
  ret void
}

; Function Attrs: nounwind uwtable
define internal void @testI16Vector(i16 zeroext) #0 {
  %tmp = insertelement <2 x i16> undef, i16 %0, i32 0
  %uivec = insertelement <2 x i16> %tmp, i16 %0, i32 1
  %fpvec = uitofp <2 x i16> %uivec to <2 x float>
  %fp0 = extractelement <2 x float> %fpvec, i32 0
  %fp1 = extractelement <2 x float> %fpvec, i32 1
  %dp0 = fpext float %fp0 to double
  %dp1 = fpext float %fp1 to double
  %arg32 = zext i16 %0 to i32
  tail call i32 (i8*, ...) @printf(i8* getelementptr inbounds ([11 x i8], [11 x i8]* @.str, i64 0, i64 0), i32 %arg32, double %dp0)
  tail call i32 (i8*, ...) @printf(i8* getelementptr inbounds ([11 x i8], [11 x i8]* @.str, i64 0, i64 0), i32 %arg32, double %dp1)
  ret void
}

; Function Attrs: nounwind uwtable
define internal void @testI32Vector(i32) #0 {
  %tmp = insertelement <2 x i32> undef, i32 %0, i32 0
  %uivec = insertelement <2 x i32> %tmp, i32 %0, i32 1
  %fpvec = uitofp <2 x i32> %uivec to <2 x float>
  %fp0 = extractelement <2 x float> %fpvec, i32 0
  %fp1 = extractelement <2 x float> %fpvec, i32 1
  %dp0 = fpext float %fp0 to double
  %dp1 = fpext float %fp1 to double
  tail call i32 (i8*, ...) @printf(i8* getelementptr inbounds ([11 x i8], [11 x i8]* @.str, i64 0, i64 0), i32 %0, double %dp0)
  tail call i32 (i8*, ...) @printf(i8* getelementptr inbounds ([11 x i8], [11 x i8]* @.str, i64 0, i64 0), i32 %0, double %dp1)
  ret void
}

; Function Attrs: nounwind uwtable
define internal void @testI64Vector(i64) #0 {
  %tmp = insertelement <2 x i64> undef, i64 %0, i32 0
  %uivec = insertelement <2 x i64> %tmp, i64 %0, i32 1
  %fpvec = uitofp <2 x i64> %uivec to <2 x float>
  %fp0 = extractelement <2 x float> %fpvec, i32 0
  %fp1 = extractelement <2 x float> %fpvec, i32 1
  %dp0 = fpext float %fp0 to double
  %dp1 = fpext float %fp1 to double
  tail call i32 (i8*, ...) @printf(i8* getelementptr inbounds ([12 x i8], [12 x i8]* @.str.1, i64 0, i64 0), i64 %0, double %dp0)
  tail call i32 (i8*, ...) @printf(i8* getelementptr inbounds ([12 x i8], [12 x i8]* @.str.1, i64 0, i64 0), i64 %0, double %dp1)
  ret void
}

; Function Attrs: nounwind uwtable
define i32 @main() #0 {
  %1 = tail call i32 @puts(i8* getelementptr inbounds ([13 x i8], [13 x i8]* @str, i64 0, i64 0))
  tail call void @testI8Scalar(i8 zeroext 0)
  tail call void @testI8Scalar(i8 zeroext 1)
  tail call void @testI8Scalar(i8 zeroext -1)
  %2 = tail call i32 @puts(i8* getelementptr inbounds ([14 x i8], [14 x i8]* @str.10, i64 0, i64 0))
  tail call void @testI16Scalar(i16 zeroext 0)
  tail call void @testI16Scalar(i16 zeroext 1)
  tail call void @testI16Scalar(i16 zeroext -1)
  %3 = tail call i32 @puts(i8* getelementptr inbounds ([14 x i8], [14 x i8]* @str.11, i64 0, i64 0))
  tail call void @testI32Scalar(i32 0)
  tail call void @testI32Scalar(i32 1)
  tail call void @testI32Scalar(i32 -1)
  %4 = tail call i32 @puts(i8* getelementptr inbounds ([14 x i8], [14 x i8]* @str.12, i64 0, i64 0))
  tail call void @testI64Scalar(i64 0)
  tail call void @testI64Scalar(i64 1)
  tail call void @testI64Scalar(i64 -1)
  %5 = tail call i32 @puts(i8* getelementptr inbounds ([13 x i8], [13 x i8]* @str.13, i64 0, i64 0))
  tail call void @testI8Vector(i8 zeroext 0)
  tail call void @testI8Vector(i8 zeroext 1)
  tail call void @testI8Vector(i8 zeroext -1)
  %6 = tail call i32 @puts(i8* getelementptr inbounds ([14 x i8], [14 x i8]* @str.14, i64 0, i64 0))
  tail call void @testI16Vector(i16 zeroext 0)
  tail call void @testI16Vector(i16 zeroext 1)
  tail call void @testI16Vector(i16 zeroext -1)
  %7 = tail call i32 @puts(i8* getelementptr inbounds ([14 x i8], [14 x i8]* @str.15, i64 0, i64 0))
  tail call void @testI32Vector(i32 0)
  tail call void @testI32Vector(i32 1)
  tail call void @testI32Vector(i32 -1)
  %8 = tail call i32 @puts(i8* getelementptr inbounds ([14 x i8], [14 x i8]* @str.16, i64 0, i64 0))
  tail call void @testI64Vector(i64 0)
  tail call void @testI64Vector(i64 1)
  tail call void @testI64Vector(i64 -1)
  ret i32 0
}

; Function Attrs: nounwind
declare i32 @puts(i8* nocapture readonly) #3
