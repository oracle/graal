; ModuleID = 'uitofp_double_mod.bc'
target datalayout = "e-m:e-i64:64-f80:128-n8:16:32:64-S128"
target triple = "x86_64-unknown-linux-gnu"

; Function Attrs: nounwind readnone speculatable
declare void @llvm.dbg.value(metadata, metadata, metadata) #1

; Function Attrs: nounwind
declare i32 @printf(i8* nocapture readonly, ...) #2

@.uadd = private unnamed_addr constant [26 x i8] c"%3hhu sat+ %3hhu = %3hhu\0A\00", align 1
@.uadd.1 = private unnamed_addr constant [23 x i8] c"%3hu sat+ %3hu = %3hu\0A\00", align 1
@.uadd.2 = private unnamed_addr constant [20 x i8] c"%3u sat+ %3u = %3u\0A\00", align 1
@.uadd.3 = private unnamed_addr constant [23 x i8] c"%3lu sat+ %3lu = %3lu\0A\00", align 1
@uadd = private unnamed_addr constant [16 x i8] c"testI8_uadd_sat\00", align 1
@uadd.1 = private unnamed_addr constant [17 x i8] c"testI16_uadd_sat\00", align 1
@uadd.2 = private unnamed_addr constant [17 x i8] c"testI32_uadd_sat\00", align 1
@uadd.3 = private unnamed_addr constant [17 x i8] c"testI64_uadd_sat\00", align 1

; Function Attrs: nounwind uwtable
define internal void @testI8_uadd_sat(i8 %a, i8 %b) #0 {
  %1 = tail call i8 @llvm.uadd.sat.i8(i8 %a, i8 %b)
  %2 = tail call i32 (i8*, ...) @printf(i8* getelementptr inbounds ([26 x i8], [26 x i8]* @.uadd, i64 0, i64 0), i8 %a, i8 %b, i8 %1)
  ret void
}

; Function Attrs: nounwind uwtable
define internal void @testI16_uadd_sat(i16 %a, i16 %b) #0 {
  %1 = tail call i16 @llvm.uadd.sat.i16(i16 %a, i16 %b)
  %2 = tail call i32 (i8*, ...) @printf(i8* getelementptr inbounds ([23 x i8], [23 x i8]* @.uadd.1, i64 0, i64 0), i16 %a, i16 %b, i16 %1)
  ret void
}

; Function Attrs: nounwind uwtable
define internal void @testI32_uadd_sat(i32 %a, i32 %b) #0 {
  %1 = tail call i32 @llvm.uadd.sat.i32(i32 %a, i32 %b)
  %2 = tail call i32 (i8*, ...) @printf(i8* getelementptr inbounds ([20 x i8], [20 x i8]* @.uadd.2, i64 0, i64 0), i32 %a, i32 %b, i32 %1)
  ret void
}

; Function Attrs: nounwind uwtable
define internal void @testI64_uadd_sat(i64 %a, i64 %b) #0 {
  %1 = tail call i64 @llvm.uadd.sat.i64(i64 %a, i64 %b)
  %2 = tail call i32 (i8*, ...) @printf(i8* getelementptr inbounds ([23 x i8], [23 x i8]* @.uadd.3, i64 0, i64 0), i64 %a, i64 %b, i64 %1)
  ret void
}

declare i8 @llvm.uadd.sat.i8(i8 %a, i8 %b) #2

declare i16 @llvm.uadd.sat.i16(i16 %a, i16 %b) #2

declare i32 @llvm.uadd.sat.i32(i32 %a, i32 %b) #2

declare i64 @llvm.uadd.sat.i64(i64 %a, i64 %b) #2

@.usub = private unnamed_addr constant [26 x i8] c"%3hhu sat- %3hhu = %3hhu\0A\00", align 1
@.usub.1 = private unnamed_addr constant [23 x i8] c"%3hu sat- %3hu = %3hu\0A\00", align 1
@.usub.2 = private unnamed_addr constant [20 x i8] c"%3u sat- %3u = %3u\0A\00", align 1
@.usub.3 = private unnamed_addr constant [23 x i8] c"%3lu sat- %3lu = %3lu\0A\00", align 1
@usub = private unnamed_addr constant [16 x i8] c"testI8_usub_sat\00", align 1
@usub.1 = private unnamed_addr constant [17 x i8] c"testI16_usub_sat\00", align 1
@usub.2 = private unnamed_addr constant [17 x i8] c"testI32_usub_sat\00", align 1
@usub.3 = private unnamed_addr constant [17 x i8] c"testI64_usub_sat\00", align 1

; Function Attrs: nounwind uwtable
define internal void @testI8_usub_sat(i8 %a, i8 %b) #0 {
  %1 = tail call i8 @llvm.usub.sat.i8(i8 %a, i8 %b)
  %2 = tail call i32 (i8*, ...) @printf(i8* getelementptr inbounds ([26 x i8], [26 x i8]* @.usub, i64 0, i64 0), i8 %a, i8 %b, i8 %1)
  ret void
}

; Function Attrs: nounwind uwtable
define internal void @testI16_usub_sat(i16 %a, i16 %b) #0 {
  %1 = tail call i16 @llvm.usub.sat.i16(i16 %a, i16 %b)
  %2 = tail call i32 (i8*, ...) @printf(i8* getelementptr inbounds ([23 x i8], [23 x i8]* @.usub.1, i64 0, i64 0), i16 %a, i16 %b, i16 %1)
  ret void
}

; Function Attrs: nounwind uwtable
define internal void @testI32_usub_sat(i32 %a, i32 %b) #0 {
  %1 = tail call i32 @llvm.usub.sat.i32(i32 %a, i32 %b)
  %2 = tail call i32 (i8*, ...) @printf(i8* getelementptr inbounds ([20 x i8], [20 x i8]* @.usub.2, i64 0, i64 0), i32 %a, i32 %b, i32 %1)
  ret void
}

; Function Attrs: nounwind uwtable
define internal void @testI64_usub_sat(i64 %a, i64 %b) #0 {
  %1 = tail call i64 @llvm.usub.sat.i64(i64 %a, i64 %b)
  %2 = tail call i32 (i8*, ...) @printf(i8* getelementptr inbounds ([23 x i8], [23 x i8]* @.usub.3, i64 0, i64 0), i64 %a, i64 %b, i64 %1)
  ret void
}

declare i8 @llvm.usub.sat.i8(i8 %a, i8 %b) #2

declare i16 @llvm.usub.sat.i16(i16 %a, i16 %b) #2

declare i32 @llvm.usub.sat.i32(i32 %a, i32 %b) #2

declare i64 @llvm.usub.sat.i64(i64 %a, i64 %b) #2

@.sadd = private unnamed_addr constant [26 x i8] c"%3hhd sat+ %3hhd = %3hhd\0A\00", align 1
@.sadd.1 = private unnamed_addr constant [23 x i8] c"%3hd sat+ %3hd = %3hd\0A\00", align 1
@.sadd.2 = private unnamed_addr constant [20 x i8] c"%3d sat+ %3d = %3d\0A\00", align 1
@.sadd.3 = private unnamed_addr constant [23 x i8] c"%3ld sat+ %3ld = %3ld\0A\00", align 1
@sadd = private unnamed_addr constant [16 x i8] c"testI8_sadd_sat\00", align 1
@sadd.1 = private unnamed_addr constant [17 x i8] c"testI16_sadd_sat\00", align 1
@sadd.2 = private unnamed_addr constant [17 x i8] c"testI32_sadd_sat\00", align 1
@sadd.3 = private unnamed_addr constant [17 x i8] c"testI64_sadd_sat\00", align 1

; Function Attrs: nounwind uwtable
define internal void @testI8_sadd_sat(i8 %a, i8 %b) #0 {
  %1 = tail call i8 @llvm.sadd.sat.i8(i8 %a, i8 %b)
  %2 = tail call i32 (i8*, ...) @printf(i8* getelementptr inbounds ([26 x i8], [26 x i8]* @.sadd, i64 0, i64 0), i8 %a, i8 %b, i8 %1)
  ret void
}

; Function Attrs: nounwind uwtable
define internal void @testI16_sadd_sat(i16 %a, i16 %b) #0 {
  %1 = tail call i16 @llvm.sadd.sat.i16(i16 %a, i16 %b)
  %2 = tail call i32 (i8*, ...) @printf(i8* getelementptr inbounds ([23 x i8], [23 x i8]* @.sadd.1, i64 0, i64 0), i16 %a, i16 %b, i16 %1)
  ret void
}

; Function Attrs: nounwind uwtable
define internal void @testI32_sadd_sat(i32 %a, i32 %b) #0 {
  %1 = tail call i32 @llvm.sadd.sat.i32(i32 %a, i32 %b)
  %2 = tail call i32 (i8*, ...) @printf(i8* getelementptr inbounds ([20 x i8], [20 x i8]* @.sadd.2, i64 0, i64 0), i32 %a, i32 %b, i32 %1)
  ret void
}

; Function Attrs: nounwind uwtable
define internal void @testI64_sadd_sat(i64 %a, i64 %b) #0 {
  %1 = tail call i64 @llvm.sadd.sat.i64(i64 %a, i64 %b)
  %2 = tail call i32 (i8*, ...) @printf(i8* getelementptr inbounds ([23 x i8], [23 x i8]* @.sadd.3, i64 0, i64 0), i64 %a, i64 %b, i64 %1)
  ret void
}

declare i8 @llvm.sadd.sat.i8(i8 %a, i8 %b) #2

declare i16 @llvm.sadd.sat.i16(i16 %a, i16 %b) #2

declare i32 @llvm.sadd.sat.i32(i32 %a, i32 %b) #2

declare i64 @llvm.sadd.sat.i64(i64 %a, i64 %b) #2

@.ssub = private unnamed_addr constant [26 x i8] c"%3hhd sat- %3hhd = %3hhd\0A\00", align 1
@.ssub.1 = private unnamed_addr constant [23 x i8] c"%3hd sat- %3hd = %3hd\0A\00", align 1
@.ssub.2 = private unnamed_addr constant [20 x i8] c"%3d sat- %3d = %3d\0A\00", align 1
@.ssub.3 = private unnamed_addr constant [23 x i8] c"%3ld sat- %3ld = %3ld\0A\00", align 1
@ssub = private unnamed_addr constant [16 x i8] c"testI8_ssub_sat\00", align 1
@ssub.1 = private unnamed_addr constant [17 x i8] c"testI16_ssub_sat\00", align 1
@ssub.2 = private unnamed_addr constant [17 x i8] c"testI32_ssub_sat\00", align 1
@ssub.3 = private unnamed_addr constant [17 x i8] c"testI64_ssub_sat\00", align 1

; Function Attrs: nounwind uwtable
define internal void @testI8_ssub_sat(i8 %a, i8 %b) #0 {
  %1 = tail call i8 @llvm.ssub.sat.i8(i8 %a, i8 %b)
  %2 = tail call i32 (i8*, ...) @printf(i8* getelementptr inbounds ([26 x i8], [26 x i8]* @.ssub, i64 0, i64 0), i8 %a, i8 %b, i8 %1)
  ret void
}

; Function Attrs: nounwind uwtable
define internal void @testI16_ssub_sat(i16 %a, i16 %b) #0 {
  %1 = tail call i16 @llvm.ssub.sat.i16(i16 %a, i16 %b)
  %2 = tail call i32 (i8*, ...) @printf(i8* getelementptr inbounds ([23 x i8], [23 x i8]* @.ssub.1, i64 0, i64 0), i16 %a, i16 %b, i16 %1)
  ret void
}

; Function Attrs: nounwind uwtable
define internal void @testI32_ssub_sat(i32 %a, i32 %b) #0 {
  %1 = tail call i32 @llvm.ssub.sat.i32(i32 %a, i32 %b)
  %2 = tail call i32 (i8*, ...) @printf(i8* getelementptr inbounds ([20 x i8], [20 x i8]* @.ssub.2, i64 0, i64 0), i32 %a, i32 %b, i32 %1)
  ret void
}

; Function Attrs: nounwind uwtable
define internal void @testI64_ssub_sat(i64 %a, i64 %b) #0 {
  %1 = tail call i64 @llvm.ssub.sat.i64(i64 %a, i64 %b)
  %2 = tail call i32 (i8*, ...) @printf(i8* getelementptr inbounds ([23 x i8], [23 x i8]* @.ssub.3, i64 0, i64 0), i64 %a, i64 %b, i64 %1)
  ret void
}

declare i8 @llvm.ssub.sat.i8(i8 %a, i8 %b) #2

declare i16 @llvm.ssub.sat.i16(i16 %a, i16 %b) #2

declare i32 @llvm.ssub.sat.i32(i32 %a, i32 %b) #2

declare i64 @llvm.ssub.sat.i64(i64 %a, i64 %b) #2

; Function Attrs: nounwind uwtable
define i32 @main() #0 {
  %uadd.1 = tail call i32 @puts(i8* getelementptr inbounds ([16 x i8], [16 x i8]* @uadd, i64 0, i64 0))
  tail call void @testI8_uadd_sat(i8 1, i8 1)
  tail call void @testI8_uadd_sat(i8 127, i8 1)
  tail call void @testI8_uadd_sat(i8 255, i8 1)
  tail call void @testI8_uadd_sat(i8 127, i8 127)
  %uadd.2 = tail call i32 @puts(i8* getelementptr inbounds ([17 x i8], [17 x i8]* @uadd.1, i64 0, i64 0))
  tail call void @testI16_uadd_sat(i16 1, i16 1)
  tail call void @testI16_uadd_sat(i16 32767, i16 1)
  tail call void @testI16_uadd_sat(i16 65535, i16 1)
  tail call void @testI16_uadd_sat(i16 32767, i16 32767)
  %uadd.3 = tail call i32 @puts(i8* getelementptr inbounds ([17 x i8], [17 x i8]* @uadd.2, i64 0, i64 0))
  tail call void @testI32_uadd_sat(i32 1, i32 1)
  tail call void @testI32_uadd_sat(i32 2147483647, i32 1)
  tail call void @testI32_uadd_sat(i32 4294967295, i32 1)
  tail call void @testI32_uadd_sat(i32 2147483647, i32 2147483647)
  %uadd.4 = tail call i32 @puts(i8* getelementptr inbounds ([17 x i8], [17 x i8]* @uadd.3, i64 0, i64 0))
  tail call void @testI64_uadd_sat(i64 1, i64 1)
  tail call void @testI64_uadd_sat(i64 9223372036854775807, i64 1)
  tail call void @testI64_uadd_sat(i64 18446744073709551615, i64 1)
  tail call void @testI64_uadd_sat(i64 9223372036854775807, i64 9223372036854775807)
  %usub.1 = tail call i32 @puts(i8* getelementptr inbounds ([16 x i8], [16 x i8]* @usub, i64 0, i64 0))
  tail call void @testI8_usub_sat(i8 1, i8 1)
  tail call void @testI8_usub_sat(i8 0, i8 1)
  tail call void @testI8_usub_sat(i8 127, i8 128)
  tail call void @testI8_usub_sat(i8 0, i8 255)
  %usub.2 = tail call i32 @puts(i8* getelementptr inbounds ([17 x i8], [17 x i8]* @usub.1, i64 0, i64 0))
  tail call void @testI16_usub_sat(i16 1, i16 1)
  tail call void @testI16_usub_sat(i16 0, i16 1)
  tail call void @testI16_usub_sat(i16 32767, i16 32768)
  tail call void @testI16_usub_sat(i16 0, i16 65535)
  %usub.3 = tail call i32 @puts(i8* getelementptr inbounds ([17 x i8], [17 x i8]* @usub.2, i64 0, i64 0))
  tail call void @testI32_usub_sat(i32 1, i32 1)
  tail call void @testI32_usub_sat(i32 0, i32 1)
  tail call void @testI32_usub_sat(i32 2147483647, i32 2147483648)
  tail call void @testI32_usub_sat(i32 0, i32 4294967295)
  %usub.4 = tail call i32 @puts(i8* getelementptr inbounds ([17 x i8], [17 x i8]* @usub.3, i64 0, i64 0))
  tail call void @testI64_usub_sat(i64 1, i64 1)
  tail call void @testI64_usub_sat(i64 0, i64 1)
  tail call void @testI64_usub_sat(i64 9223372036854775807, i64 9223372036854775808)
  tail call void @testI64_usub_sat(i64 0, i64 18446744073709551615)

  %sadd.1 = tail call i32 @puts(i8* getelementptr inbounds ([16 x i8], [16 x i8]* @sadd, i64 0, i64 0))
  tail call void @testI8_sadd_sat(i8 1, i8 1)
  tail call void @testI8_sadd_sat(i8 127, i8 1)
  tail call void @testI8_sadd_sat(i8 -128, i8 -1)
  tail call void @testI8_sadd_sat(i8 127, i8 127)
  %sadd.2 = tail call i32 @puts(i8* getelementptr inbounds ([17 x i8], [17 x i8]* @sadd.1, i64 0, i64 0))
  tail call void @testI16_sadd_sat(i16 1, i16 1)
  tail call void @testI16_sadd_sat(i16 32767, i16 1)
  tail call void @testI16_sadd_sat(i16 -32768, i16 -1)
  tail call void @testI16_sadd_sat(i16 32767, i16 32767)
  %sadd.3 = tail call i32 @puts(i8* getelementptr inbounds ([17 x i8], [17 x i8]* @sadd.2, i64 0, i64 0))
  tail call void @testI32_sadd_sat(i32 1, i32 1)
  tail call void @testI32_sadd_sat(i32 2147483647, i32 1)
  tail call void @testI32_sadd_sat(i32 -2147483648, i32 -1)
  tail call void @testI32_sadd_sat(i32 2147483647, i32 2147483647)
  %sadd.4 = tail call i32 @puts(i8* getelementptr inbounds ([17 x i8], [17 x i8]* @sadd.3, i64 0, i64 0))
  tail call void @testI64_sadd_sat(i64 1, i64 1)
  tail call void @testI64_sadd_sat(i64 9223372036854775807, i64 1)
  tail call void @testI64_sadd_sat(i64 9223372036854775808, i64 -1)
  tail call void @testI64_sadd_sat(i64 9223372036854775807, i64 9223372036854775807)
  %ssub.1 = tail call i32 @puts(i8* getelementptr inbounds ([16 x i8], [16 x i8]* @ssub, i64 0, i64 0))
  tail call void @testI8_ssub_sat(i8 1, i8 1)
  tail call void @testI8_ssub_sat(i8 -128, i8 1)
  tail call void @testI8_ssub_sat(i8 1, i8 2)
  tail call void @testI8_ssub_sat(i8 0, i8 -128)
  %ssub.2 = tail call i32 @puts(i8* getelementptr inbounds ([17 x i8], [17 x i8]* @ssub.1, i64 0, i64 0))
  tail call void @testI16_ssub_sat(i16 1, i16 1)
  tail call void @testI16_ssub_sat(i16 -32768, i16 1)
  tail call void @testI16_ssub_sat(i16 1, i16 2)
  tail call void @testI16_ssub_sat(i16 0, i16 -32768)
  %ssub.3 = tail call i32 @puts(i8* getelementptr inbounds ([17 x i8], [17 x i8]* @ssub.2, i64 0, i64 0))
  tail call void @testI32_ssub_sat(i32 1, i32 1)
  tail call void @testI32_ssub_sat(i32 -2147483648, i32 1)
  tail call void @testI32_ssub_sat(i32 1, i32 2)
  tail call void @testI32_ssub_sat(i32 -2, i32 -2147483648)
  %ssub.4 = tail call i32 @puts(i8* getelementptr inbounds ([17 x i8], [17 x i8]* @ssub.3, i64 0, i64 0))
  tail call void @testI64_ssub_sat(i64 1, i64 1)
  tail call void @testI64_ssub_sat(i64 -9223372036854775808, i64 1)
  tail call void @testI64_ssub_sat(i64 1, i64 2)
  tail call void @testI64_ssub_sat(i64 0, i64 -9223372036854775808)

ret i32 0
}

; Function Attrs: nounwind
declare i32 @puts(i8* nocapture readonly) #3
