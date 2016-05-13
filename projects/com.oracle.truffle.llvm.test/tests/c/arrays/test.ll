; ModuleID = 'fourDimDynIndex.c'
target datalayout = "e-p:64:64:64-i1:8:8-i8:8:8-i16:16:16-i32:32:32-i64:64:64-f32:32:32-f64:64:64-v64:64:64-v128:128:128-a0:0:64-s0:64:64-f80:128:128-n8:16:32:64-S128"
target triple = "x86_64-unknown-linux-gnu"

@array4D = global [2 x [2 x [3 x [2 x i32]]]] [[2 x [3 x [2 x i32]]] [[3 x [2 x i32]] [[2 x i32] [i32 0, i32 1], [2 x i32] [i32 2, i32 3], [2 x i32] [i32 4, i32 5]], [3 x [2 x i32]] [[2 x i32] [i32 6, i32 7], [2 x i32] [i32 8, i32 9], [2 x i32] [i32 10, i32 11]]], [2 x [3 x [2 x i32]]] [[3 x [2 x i32]] [[2 x i32] [i32 12, i32 13], [2 x i32] [i32 14, i32 15], [2 x i32] [i32 16, i32 17]], [3 x [2 x i32]] [[2 x i32] [i32 18, i32 19], [2 x i32] [i32 20, i32 21], [2 x i32] [i32 22, i32 23]]]], align 16

define i32 @main() nounwind uwtable {
  %1 = alloca i32, align 4
  %i = alloca i32, align 4
  %j = alloca i32, align 4
  %k = alloca i32, align 4
  %l = alloca i32, align 4
  store i32 0, i32* %1
  store i32 0, i32* %i, align 4
  br label %2

; <label>:2                                       ; preds = %56, %0
  %3 = load i32* %i, align 4
  %4 = icmp slt i32 %3, 2
  br i1 %4, label %5, label %59

; <label>:5                                       ; preds = %2
  store i32 0, i32* %j, align 4
  br label %6

; <label>:6                                       ; preds = %52, %5
  %7 = load i32* %j, align 4
  %8 = icmp slt i32 %7, 3
  br i1 %8, label %9, label %55

; <label>:9                                       ; preds = %6
  store i32 0, i32* %k, align 4
  br label %10

; <label>:10                                      ; preds = %48, %9
  %11 = load i32* %k, align 4
  %12 = icmp slt i32 %11, 2
  br i1 %12, label %13, label %51

; <label>:13                                      ; preds = %10
  store i32 0, i32* %l, align 4
  br label %14

; <label>:14                                      ; preds = %44, %13
  %15 = load i32* %j, align 4
  %16 = icmp slt i32 %15, 2
  br i1 %16, label %17, label %47

; <label>:17                                      ; preds = %14
  %18 = load i32* %l, align 4
  %19 = sext i32 %18 to i64
  %20 = load i32* %k, align 4
  %21 = sext i32 %20 to i64
  %22 = load i32* %j, align 4
  %23 = sext i32 %22 to i64
  %24 = load i32* %i, align 4
  %25 = sext i32 %24 to i64
  %26 = getelementptr inbounds [2 x [2 x [3 x [2 x i32]]]]* @array4D, i32 0, i64 %25
  %27 = getelementptr inbounds [2 x [3 x [2 x i32]]]* %26, i32 0, i64 %23
  %28 = getelementptr inbounds [3 x [2 x i32]]* %27, i32 0, i64 %21
  %29 = getelementptr inbounds [2 x i32]* %28, i32 0, i64 %19
  %30 = load i32* %29, align 4
  %31 = load i32* %i, align 4
  %32 = mul nsw i32 %31, 12
  %33 = load i32* %j, align 4
  %34 = mul nsw i32 %33, 6
  %35 = add nsw i32 %32, %34
  %36 = load i32* %k, align 4
  %37 = mul nsw i32 %36, 2
  %38 = add nsw i32 %35, %37
  %39 = load i32* %l, align 4
  %40 = add nsw i32 %38, %39
  %41 = icmp ne i32 %30, %40
  br i1 %41, label %42, label %43

; <label>:42                                      ; preds = %17
  call void @abort() noreturn nounwind
  unreachable

; <label>:43                                      ; preds = %17
  br label %44

; <label>:44                                      ; preds = %43
  %45 = load i32* %j, align 4
  %46 = add nsw i32 %45, 1
  store i32 %46, i32* %j, align 4
  br label %14

; <label>:47                                      ; preds = %14
  br label %48

; <label>:48                                      ; preds = %47
  %49 = load i32* %k, align 4
  %50 = add nsw i32 %49, 1
  store i32 %50, i32* %k, align 4
  br label %10

; <label>:51                                      ; preds = %10
  br label %52

; <label>:52                                      ; preds = %51
  %53 = load i32* %j, align 4
  %54 = add nsw i32 %53, 1
  store i32 %54, i32* %j, align 4
  br label %6

; <label>:55                                      ; preds = %6
  br label %56

; <label>:56                                      ; preds = %55
  %57 = load i32* %i, align 4
  %58 = add nsw i32 %57, 1
  store i32 %58, i32* %i, align 4
  br label %2

; <label>:59                                      ; preds = %2
  %60 = load i32* %1
  ret i32 %60
}

declare void @abort() noreturn nounwind
