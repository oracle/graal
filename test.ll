; ModuleID = '../sulong-benchmarks/micro/sqrtLoop.c'
target datalayout = "e-p:64:64:64-i1:8:8-i8:8:8-i16:16:16-i32:32:32-i64:64:64-f32:32:32-f64:64:64-v64:64:64-v128:128:128-a0:0:64-s0:64:64-f80:128:128-n8:16:32:64-S128"
target triple = "x86_64-unknown-linux-gnu"

%struct.timevalHarness = type { i64, i64 }

@.str = private unnamed_addr constant [11 x i8] c"%s %ld %ld\00", align 1
@.str1 = private unnamed_addr constant [29 x i8] c"error: not the same results!\00", align 1
@.str2 = private unnamed_addr constant [22 x i8] c"base time value: %ld\0A\00", align 1
@displayScore.str = private unnamed_addr constant [16 x i8] c"sqrtLoop.c: %u\0A\00", align 16

define i32 @main() nounwind uwtable {
  %1 = alloca i32, align 4
  %result = alloca i32, align 4
  %tv_start = alloca %struct.timevalHarness*, align 8
  %tv_end = alloca %struct.timevalHarness*, align 8
  %start = alloca i32, align 4
  %end = alloca i32, align 4
  %time = alloca i64, align 8
  %i = alloca i32, align 4
  %totalCount = alloca i32, align 4
  %2 = alloca i8*
  %problemSize = alloca i32, align 4
  %initResult = alloca i32, align 4
  %compareResult = alloca i64, align 8
  %runTime = alloca i64, align 8
  %baseTime = alloca i32, align 4
  %score = alloca i64, align 8
  %3 = alloca i32
  store i32 0, i32* %1
  %4 = call noalias i8* @malloc(i64 16) nounwind
  %5 = bitcast i8* %4 to %struct.timevalHarness*
  store %struct.timevalHarness* %5, %struct.timevalHarness** %tv_start, align 8
  %6 = call noalias i8* @malloc(i64 16) nounwind
  %7 = bitcast i8* %6 to %struct.timevalHarness*
  store %struct.timevalHarness* %7, %struct.timevalHarness** %tv_end, align 8
  store i32 0, i32* %start, align 4
  store i32 0, i32* %end, align 4
  %8 = call i32 @iterations()
  store i32 %8, i32* %totalCount, align 4
  %9 = load i32* %totalCount, align 4
  %10 = zext i32 %9 to i64
  %11 = call i8* @llvm.stacksave()
  store i8* %11, i8** %2
  %12 = alloca i64, i64 %10, align 16
  %13 = load i32* %totalCount, align 4
  %14 = zext i32 %13 to i64
  %15 = alloca i64, i64 %14, align 16
  %16 = call i32 @init()
  store volatile i32 %16, i32* %initResult, align 4
  %17 = load i32* %result, align 4
  %18 = add nsw i32 %17, 3
  store i32 %18, i32* %result, align 4
  store i32 0, i32* %i, align 4
  br label %19

; <label>:19                                      ; preds = %57, %0
  %20 = load i32* %i, align 4
  %21 = load i32* %totalCount, align 4
  %22 = icmp slt i32 %20, %21
  br i1 %22, label %23, label %60

; <label>:23                                      ; preds = %19
  %24 = call i32 @getProblemsize()
  store i32 %24, i32* %problemSize, align 4
  %25 = load %struct.timevalHarness** %tv_start, align 8
  %26 = call i32 (...)* @gettimeofday(%struct.timevalHarness* %25, i8* null)
  %27 = load i32* %problemSize, align 4
  %28 = call i32 @benchmark(i32 %27)
  store i32 %28, i32* %result, align 4
  %29 = load i32* %result, align 4
  %30 = sext i32 %29 to i64
  %31 = load i32* %i, align 4
  %32 = sext i32 %31 to i64
  %33 = getelementptr inbounds i64* %15, i64 %32
  store i64 %30, i64* %33, align 8
  %34 = load %struct.timevalHarness** %tv_end, align 8
  %35 = call i32 (...)* @gettimeofday(%struct.timevalHarness* %34, i8* null)
  %36 = load %struct.timevalHarness** %tv_end, align 8
  %37 = getelementptr inbounds %struct.timevalHarness* %36, i32 0, i32 0
  %38 = load i64* %37, align 8
  %39 = mul nsw i64 %38, 1000000
  %40 = load %struct.timevalHarness** %tv_end, align 8
  %41 = getelementptr inbounds %struct.timevalHarness* %40, i32 0, i32 1
  %42 = load i64* %41, align 8
  %43 = add nsw i64 %39, %42
  %44 = load %struct.timevalHarness** %tv_start, align 8
  %45 = getelementptr inbounds %struct.timevalHarness* %44, i32 0, i32 0
  %46 = load i64* %45, align 8
  %47 = mul nsw i64 %46, 1000000
  %48 = load %struct.timevalHarness** %tv_start, align 8
  %49 = getelementptr inbounds %struct.timevalHarness* %48, i32 0, i32 1
  %50 = load i64* %49, align 8
  %51 = add nsw i64 %47, %50
  %52 = sub nsw i64 %43, %51
  store i64 %52, i64* %time, align 8
  %53 = load i64* %time, align 8
  %54 = load i32* %i, align 4
  %55 = sext i32 %54 to i64
  %56 = getelementptr inbounds i64* %12, i64 %55
  store i64 %53, i64* %56, align 8
  br label %57

; <label>:57                                      ; preds = %23
  %58 = load i32* %i, align 4
  %59 = add nsw i32 %58, 1
  store i32 %59, i32* %i, align 4
  br label %19

; <label>:60                                      ; preds = %19
  %61 = getelementptr inbounds i64* %15, i64 0
  %62 = load i64* %61, align 8
  store i64 %62, i64* %compareResult, align 8
  store i32 1, i32* %i, align 4
  br label %63

; <label>:63                                      ; preds = %82, %60
  %64 = load i32* %i, align 4
  %65 = load i32* %totalCount, align 4
  %66 = icmp slt i32 %64, %65
  br i1 %66, label %67, label %85

; <label>:67                                      ; preds = %63
  %68 = load i32* %i, align 4
  %69 = sext i32 %68 to i64
  %70 = getelementptr inbounds i64* %15, i64 %69
  %71 = load i64* %70, align 8
  %72 = load i64* %compareResult, align 8
  %73 = icmp ne i64 %71, %72
  br i1 %73, label %74, label %81

; <label>:74                                      ; preds = %67
  %75 = load i64* %compareResult, align 8
  %76 = load i32* %i, align 4
  %77 = sext i32 %76 to i64
  %78 = getelementptr inbounds i64* %15, i64 %77
  %79 = load i64* %78, align 8
  %80 = call i32 (i8*, ...)* @printf(i8* getelementptr inbounds ([11 x i8]* @.str, i32 0, i32 0), i8* getelementptr inbounds ([29 x i8]* @.str1, i32 0, i32 0), i64 %75, i64 %79)
  call void @abort() noreturn nounwind
  unreachable

; <label>:81                                      ; preds = %67
  br label %82

; <label>:82                                      ; preds = %81
  %83 = load i32* %i, align 4
  %84 = add nsw i32 %83, 1
  store i32 %84, i32* %i, align 4
  br label %63

; <label>:85                                      ; preds = %63
  store i64 0, i64* %runTime, align 8
  %86 = load i32* %totalCount, align 4
  %87 = sub nsw i32 %86, 1
  store i32 %87, i32* %i, align 4
  br label %88

; <label>:88                                      ; preds = %101, %85
  %89 = load i32* %i, align 4
  %90 = load i32* %totalCount, align 4
  %91 = sub nsw i32 %90, 1
  %92 = sub nsw i32 %91, 20
  %93 = icmp sgt i32 %89, %92
  br i1 %93, label %94, label %104

; <label>:94                                      ; preds = %88
  %95 = load i64* %runTime, align 8
  %96 = load i32* %i, align 4
  %97 = sext i32 %96 to i64
  %98 = getelementptr inbounds i64* %12, i64 %97
  %99 = load i64* %98, align 8
  %100 = add nsw i64 %95, %99
  store i64 %100, i64* %runTime, align 8
  br label %101

; <label>:101                                     ; preds = %94
  %102 = load i32* %i, align 4
  %103 = add nsw i32 %102, -1
  store i32 %103, i32* %i, align 4
  br label %88

; <label>:104                                     ; preds = %88
  %105 = load i64* %runTime, align 8
  %106 = sdiv i64 %105, 20
  store i64 %106, i64* %runTime, align 8
  %107 = load i64* %runTime, align 8
  %108 = icmp eq i64 %107, 0
  br i1 %108, label %109, label %110

; <label>:109                                     ; preds = %104
  store i64 1, i64* %runTime, align 8
  br label %110

; <label>:110                                     ; preds = %109, %104
  %111 = call i32 @clangBaseTime()
  store i32 %111, i32* %baseTime, align 4
  %112 = load i32* %baseTime, align 4
  %113 = icmp eq i32 %112, 0
  br i1 %113, label %114, label %117

; <label>:114                                     ; preds = %110
  %115 = load i64* %runTime, align 8
  %116 = call i32 (i8*, ...)* @printf(i8* getelementptr inbounds ([22 x i8]* @.str2, i32 0, i32 0), i64 %115)
  br label %130

; <label>:117                                     ; preds = %110
  %118 = call i32 @clangBaseTime()
  %119 = sitofp i32 %118 to double
  %120 = fmul double %119, 1.000000e+02
  %121 = load i64* %runTime, align 8
  %122 = sitofp i64 %121 to double
  %123 = fdiv double %120, %122
  %124 = fptosi double %123 to i64
  store i64 %124, i64* %score, align 8
  %125 = load i64* %score, align 8
  %126 = icmp sgt i64 %125, 10000
  br i1 %126, label %127, label %128

; <label>:127                                     ; preds = %117
  store i64 10000, i64* %score, align 8
  br label %128

; <label>:128                                     ; preds = %127, %117
  %129 = load i64* %score, align 8
  call void @displayScore(i64 %129)
  br label %130

; <label>:130                                     ; preds = %128, %114
  %131 = load %struct.timevalHarness** %tv_start, align 8
  %132 = bitcast %struct.timevalHarness* %131 to i8*
  call void @free(i8* %132) nounwind
  %133 = load %struct.timevalHarness** %tv_end, align 8
  %134 = bitcast %struct.timevalHarness* %133 to i8*
  call void @free(i8* %134) nounwind
  store i32 0, i32* %1
  store i32 1, i32* %3
  %135 = load i8** %2
  call void @llvm.stackrestore(i8* %135)
  %136 = load i32* %1
  ret i32 %136
}

declare noalias i8* @malloc(i64) nounwind

declare i8* @llvm.stacksave() nounwind

declare i32 @gettimeofday(...)

define i32 @benchmark(i32 %n) nounwind uwtable noinline {
  %1 = alloca i32, align 4
  %sum = alloca i32, align 4
  %i = alloca i32, align 4
  store i32 %n, i32* %1, align 4
  store i32 0, i32* %sum, align 4
  store i32 0, i32* %i, align 4
  br label %2

; <label>:2                                       ; preds = %13, %0
  %3 = load i32* %i, align 4
  %4 = load i32* %1, align 4
  %5 = icmp slt i32 %3, %4
  br i1 %5, label %6, label %16

; <label>:6                                       ; preds = %2
  %7 = load i32* %sum, align 4
  %8 = load i32* %i, align 4
  %9 = sitofp i32 %8 to double
  %10 = call double @sqrt(double %9)
  %11 = fptosi double %10 to i32
  %12 = add nsw i32 %7, %11
  store i32 %12, i32* %sum, align 4
  br label %13

; <label>:13                                      ; preds = %6
  %14 = load i32* %i, align 4
  %15 = add nsw i32 %14, 1
  store i32 %15, i32* %i, align 4
  br label %2

; <label>:16                                      ; preds = %2
  %17 = load i32* %sum, align 4
  ret i32 %17
}

declare i32 @printf(i8*, ...)

declare void @abort() noreturn nounwind

define void @displayScore(i64 %score) nounwind uwtable noinline {
  %1 = alloca i64, align 8
  %str = alloca [16 x i8], align 16
  store i64 %score, i64* %1, align 8
  %2 = bitcast [16 x i8]* %str to i8*
  call void @llvm.memcpy.p0i8.p0i8.i64(i8* %2, i8* getelementptr inbounds ([16 x i8]* @displayScore.str, i32 0, i32 0), i64 16, i32 16, i1 false)
  %3 = getelementptr inbounds [16 x i8]* %str, i32 0, i32 0
  %4 = load i64* %1, align 8
  %5 = call i32 (i8*, ...)* @printf(i8* %3, i64 %4)
  ret void
}

declare void @free(i8*) nounwind

declare void @llvm.stackrestore(i8*) nounwind

declare void @llvm.memcpy.p0i8.p0i8.i64(i8* nocapture, i8* nocapture, i64, i32, i1) nounwind

define i32 @getProblemsize() nounwind uwtable {
  ret i32 1000000
}

define i32 @clangBaseTime() nounwind uwtable {
  ret i32 7052
}

declare double @sqrt(double)

define i32 @iterations() nounwind uwtable {
  ret i32 100
}

define i32 @init() nounwind uwtable noinline {
  ret i32 0
}
