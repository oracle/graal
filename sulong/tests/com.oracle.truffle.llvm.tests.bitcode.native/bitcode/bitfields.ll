; ModuleID = 'bitfields.bc'
target datalayout = "e-m:e-i64:64-f80:128-n8:16:32:64-S128"
target triple = "x86_64-pc-linux-gnu"

@.str = private unnamed_addr constant [34 x i8] c"%x %x %d %d %d %d %d %d %d %d %d\0A\00", align 1
@string = global i8* getelementptr inbounds ([34 x i8], [34 x i8]* @.str, i64 0, i64 0), align 8
@values = global [7 x i128] [i128 0, i128 1, i128 -1, i128 -1000, i128 1000, i128 -1, i128 9223372036854775807], align 16

define void @test17(i64, i64) {
  %format = load i8*, i8** @string, align 8
  %signed_x = trunc i64 %0 to i17
  %signed_x_i32 = sext i17 %signed_x to i32
  %signed_y = trunc i64 %1 to i17
  %signed_y_i32 = sext i17 %signed_y to i32
  %signed_add = add nsw i17 %signed_x, %signed_y
  %signed_add_i32 = sext i17 %signed_add to i32
  %signed_sub = sub nsw i17 %signed_x, %signed_y
  %signed_sub_i32 = sext i17 %signed_sub to i32
  %signed_mul = mul nsw i17 %signed_x, %signed_y
  %signed_mul_i32 = sext i17 %signed_mul to i32
  %signed_sdiv = sdiv i17 %signed_x, %signed_y
  %signed_sdiv_i32 = sext i17 %signed_sdiv to i32
  %signed_srem = srem i17 %signed_x, %signed_y
  %signed_srem_i32 = sext i17 %signed_srem to i32
  %signed_and = and i17 %signed_x, %signed_y
  %signed_and_i32 = sext i17 %signed_and to i32
  %signed_or = or i17 %signed_x, %signed_y
  %signed_or_i32 = sext i17 %signed_or to i32
  %signed_xor = xor i17 %signed_x, %signed_y
  %signed_xor_i32 = sext i17 %signed_xor to i32
  %signed_xorc = xor i17 %signed_x, -1
  %signed_xorc_i32 = sext i17 %signed_xorc to i32
  %3 = tail call i32 (i8*, ...) @printf(i8* %format, i32 %signed_x_i32, i32 %signed_y_i32, i32 %signed_add_i32, i32 %signed_sub_i32, i32 %signed_mul_i32, i32 %signed_sdiv_i32, i32 %signed_srem_i32, i32 %signed_and_i32, i32 %signed_or_i32, i32 %signed_xor_i32, i32 %signed_xorc_i32)
  ret void
}

define void @test113(i128, i128) {
  %format = load i8*, i8** @string, align 8
  %signed_x = trunc i128 %0 to i113
  %signed_x_i32 = trunc i113 %signed_x to i32
  %signed_y = trunc i128 %1 to i113
  %signed_y_i32 = trunc i113 %signed_y to i32
  %signed_add = add nsw i113 %signed_x, %signed_y
  %signed_add_i32 = trunc i113 %signed_add to i32
  %signed_sub = sub nsw i113 %signed_x, %signed_y
  %signed_sub_i32 = trunc i113 %signed_sub to i32
  %signed_mul = mul nsw i113 %signed_x, %signed_y
  %signed_mul_i32 = trunc i113 %signed_mul to i32
  %signed_sdiv = sdiv i113 %signed_x, %signed_y
  %signed_sdiv_i32 = trunc i113 %signed_sdiv to i32
  %signed_srem = srem i113 %signed_x, %signed_y
  %signed_srem_i32 = trunc i113 %signed_srem to i32
  %signed_and = and i113 %signed_x, %signed_y
  %signed_and_i32 = trunc i113 %signed_and to i32
  %signed_or = or i113 %signed_x, %signed_y
  %signed_or_i32 = trunc i113 %signed_or to i32
  %signed_xor = xor i113 %signed_x, %signed_y
  %signed_xor_i32 = trunc i113 %signed_xor to i32
  %signed_xorc = xor i113 %signed_x, -1
  %signed_xorc_i32 = trunc i113 %signed_xorc to i32
  %3 = tail call i32 (i8*, ...) @printf(i8* %format, i32 %signed_x_i32, i32 %signed_y_i32, i32 %signed_add_i32, i32 %signed_sub_i32, i32 %signed_mul_i32, i32 %signed_sdiv_i32, i32 %signed_srem_i32, i32 %signed_and_i32, i32 %signed_or_i32, i32 %signed_xor_i32, i32 %signed_xorc_i32)
  ret void
}

define void @test(i128, i128) {
  %test = sext i128 %0 to i1024
  %test2 = shl i1024 %test, 800
  %test3 = add nsw i1024 %test2, 1
  %test4 = lshr i1024 %test3, 800
  %x = trunc i1024 %test4 to i64
  %y = trunc i128 %1 to i64
  tail call void @test17(i64 %x, i64 %y)
  tail call void @test113(i128 %0, i128 %1)
  ret void
}

; Function Attrs: nounwind
declare i32 @printf(i8* nocapture readonly, ...)

; Function Attrs: nounwind ssp uwtable
define i32 @main() {
  br label %1

; <label>:1:                                      ; preds = %5, %0
  %2 = phi i64 [ 0, %0 ], [ %6, %5 ]
  %3 = getelementptr inbounds [7 x i128], [7 x i128]* @values, i64 0, i64 %2
  br label %8

; <label>:4:                                      ; preds = %5
  ret i32 0

; <label>:5:                                      ; preds = %8
  %6 = add nuw nsw i64 %2, 1
  %7 = icmp eq i64 %6, 7
  br i1 %7, label %4, label %1

; <label>:8:                                      ; preds = %8, %1
  %9 = phi i64 [ 1, %1 ], [ %13, %8 ]
  %10 = load i128, i128* %3, align 16
  %11 = getelementptr inbounds [7 x i128], [7 x i128]* @values, i64 0, i64 %9
  %12 = load i128, i128* %11, align 16
  tail call void @test(i128 %10, i128 %12)
  %s1 = lshr i128 %10, 1
  %s2 = shl i128 %12, 3
  %s3 = add nsw i128 %s2, 1
;  tail call void @test(i128 %s1, i128 %s3)
  %13 = add nuw nsw i64 %9, 1
  %14 = icmp eq i64 %13, 7
  br i1 %14, label %5, label %8
}
