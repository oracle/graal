; ModuleID = 'fp80toui1.bc'
target datalayout = "e-m:e-i64:64-f80:128-n8:16:32:64-S128"
target triple = "x86_64-pc-linux-gnu"

@.str.u1_121 = private unnamed_addr constant [23 x i8] c"u1 -4.0 = %2d (%0.8x)\0A\00", align 1
@.str.u1_122 = private unnamed_addr constant [23 x i8] c"u1 -3.0 = %2d (%0.8x)\0A\00", align 1
@.str.u1_123 = private unnamed_addr constant [23 x i8] c"u1 -2.0 = %2d (%0.8x)\0A\00", align 1
@.str.u1_124 = private unnamed_addr constant [23 x i8] c"u1 -1.0 = %2d (%0.8x)\0A\00", align 1
@.str.u1_125 = private unnamed_addr constant [23 x i8] c"u1 -0.8 = %2d (%0.8x)\0A\00", align 1
@.str.u1_126 = private unnamed_addr constant [23 x i8] c"u1 -0.5 = %2d (%0.8x)\0A\00", align 1
@.str.u1_127 = private unnamed_addr constant [23 x i8] c"u1 -0.3 = %2d (%0.8x)\0A\00", align 1
@.str.u1_128 = private unnamed_addr constant [23 x i8] c"u1  0.0 = %2d (%0.8x)\0A\00", align 1
@.str.u1_129 = private unnamed_addr constant [23 x i8] c"u1  0.3 = %2d (%0.8x)\0A\00", align 1
@.str.u1_130 = private unnamed_addr constant [23 x i8] c"u1  0.5 = %2d (%0.8x)\0A\00", align 1
@.str.u1_131 = private unnamed_addr constant [23 x i8] c"u1  0.8 = %2d (%0.8x)\0A\00", align 1
@.str.u1_132 = private unnamed_addr constant [23 x i8] c"u1  1.0 = %2d (%0.8x)\0A\00", align 1
@.str.u1_133 = private unnamed_addr constant [23 x i8] c"u1  2.0 = %2d (%0.8x)\0A\00", align 1
@.str.u1_134 = private unnamed_addr constant [23 x i8] c"u1  3.0 = %2d (%0.8x)\0A\00", align 1
@.str.u1_135 = private unnamed_addr constant [23 x i8] c"u1  4.0 = %2d (%0.8x)\0A\00", align 1

@.str.u1_zext_121 = private unnamed_addr constant [28 x i8] c"u1_zext -4.0 = %2d (%0.8x)\0A\00", align 1
@.str.u1_zext_122 = private unnamed_addr constant [28 x i8] c"u1_zext -3.0 = %2d (%0.8x)\0A\00", align 1
@.str.u1_zext_123 = private unnamed_addr constant [28 x i8] c"u1_zext -2.0 = %2d (%0.8x)\0A\00", align 1
@.str.u1_zext_124 = private unnamed_addr constant [28 x i8] c"u1_zext -1.0 = %2d (%0.8x)\0A\00", align 1
@.str.u1_zext_125 = private unnamed_addr constant [28 x i8] c"u1_zext -0.8 = %2d (%0.8x)\0A\00", align 1
@.str.u1_zext_126 = private unnamed_addr constant [28 x i8] c"u1_zext -0.5 = %2d (%0.8x)\0A\00", align 1
@.str.u1_zext_127 = private unnamed_addr constant [28 x i8] c"u1_zext -0.3 = %2d (%0.8x)\0A\00", align 1
@.str.u1_zext_128 = private unnamed_addr constant [28 x i8] c"u1_zext  0.0 = %2d (%0.8x)\0A\00", align 1
@.str.u1_zext_129 = private unnamed_addr constant [28 x i8] c"u1_zext  0.3 = %2d (%0.8x)\0A\00", align 1
@.str.u1_zext_130 = private unnamed_addr constant [28 x i8] c"u1_zext  0.5 = %2d (%0.8x)\0A\00", align 1
@.str.u1_zext_131 = private unnamed_addr constant [28 x i8] c"u1_zext  0.8 = %2d (%0.8x)\0A\00", align 1
@.str.u1_zext_132 = private unnamed_addr constant [28 x i8] c"u1_zext  1.0 = %2d (%0.8x)\0A\00", align 1
@.str.u1_zext_133 = private unnamed_addr constant [28 x i8] c"u1_zext  2.0 = %2d (%0.8x)\0A\00", align 1
@.str.u1_zext_134 = private unnamed_addr constant [28 x i8] c"u1_zext  3.0 = %2d (%0.8x)\0A\00", align 1
@.str.u1_zext_135 = private unnamed_addr constant [28 x i8] c"u1_zext  4.0 = %2d (%0.8x)\0A\00", align 1

define i32 @u1_zext(x86_fp80) #0 {
  %c = fptoui x86_fp80 %0 to i1
  %c_ext = zext i1 %c to i32
  %r = select i1 %c, i32 %c_ext, i32 23
  ret i32 %r
}

define i32 @u1(x86_fp80) #0 {
  %c = fptoui x86_fp80 %0 to i1
  %c_ext = zext i1 %c to i32
  ret i32 %c_ext
}

define i32 @main() {
  %u1_249 = tail call i32 @u1(x86_fp80 0xKC0018000000000000000)
  %u1_250 = tail call i32 (i8*, ...) @printf(i8* getelementptr inbounds ([23 x i8], [23 x i8]* @.str.u1_121, i64 0, i64 0), i32 %u1_249, i32 %u1_249)
  %u1_251 = tail call i32 @u1(x86_fp80 0xKC000C000000000000000)
  %u1_252 = tail call i32 (i8*, ...) @printf(i8* getelementptr inbounds ([23 x i8], [23 x i8]* @.str.u1_122, i64 0, i64 0), i32 %u1_251, i32 %u1_251)
  %u1_253 = tail call i32 @u1(x86_fp80 0xKC0008000000000000000)
  %u1_254 = tail call i32 (i8*, ...) @printf(i8* getelementptr inbounds ([23 x i8], [23 x i8]* @.str.u1_123, i64 0, i64 0), i32 %u1_253, i32 %u1_253)
  %u1_255 = tail call i32 @u1(x86_fp80 0xKBFFF8000000000000000)
  %u1_256 = tail call i32 (i8*, ...) @printf(i8* getelementptr inbounds ([23 x i8], [23 x i8]* @.str.u1_124, i64 0, i64 0), i32 %u1_255, i32 %u1_255)
  %u1_257 = tail call i32 @u1(x86_fp80 0xKBFFECCCCCCCCCCCCD000)
  %u1_258 = tail call i32 (i8*, ...) @printf(i8* getelementptr inbounds ([23 x i8], [23 x i8]* @.str.u1_125, i64 0, i64 0), i32 %u1_257, i32 %u1_257)
  %u1_259 = tail call i32 @u1(x86_fp80 0xKBFFE8000000000000000)
  %u1_260 = tail call i32 (i8*, ...) @printf(i8* getelementptr inbounds ([23 x i8], [23 x i8]* @.str.u1_126, i64 0, i64 0), i32 %u1_259, i32 %u1_259)
  %u1_261 = tail call i32 @u1(x86_fp80 0xKBFFD9999999999999800)
  %u1_262 = tail call i32 (i8*, ...) @printf(i8* getelementptr inbounds ([23 x i8], [23 x i8]* @.str.u1_127, i64 0, i64 0), i32 %u1_261, i32 %u1_261)
  %u1_263 = tail call i32 @u1(x86_fp80 0xK00000000000000000000)
  %u1_264 = tail call i32 (i8*, ...) @printf(i8* getelementptr inbounds ([23 x i8], [23 x i8]* @.str.u1_128, i64 0, i64 0), i32 %u1_263, i32 %u1_263)
  %u1_265 = tail call i32 @u1(x86_fp80 0xK3FFD9999999999999800)
  %u1_266 = tail call i32 (i8*, ...) @printf(i8* getelementptr inbounds ([23 x i8], [23 x i8]* @.str.u1_129, i64 0, i64 0), i32 %u1_265, i32 %u1_265)
  %u1_267 = tail call i32 @u1(x86_fp80 0xK3FFE8000000000000000)
  %u1_268 = tail call i32 (i8*, ...) @printf(i8* getelementptr inbounds ([23 x i8], [23 x i8]* @.str.u1_130, i64 0, i64 0), i32 %u1_267, i32 %u1_267)
  %u1_269 = tail call i32 @u1(x86_fp80 0xK3FFECCCCCCCCCCCCD000)
  %u1_270 = tail call i32 (i8*, ...) @printf(i8* getelementptr inbounds ([23 x i8], [23 x i8]* @.str.u1_131, i64 0, i64 0), i32 %u1_269, i32 %u1_269)
  %u1_271 = tail call i32 @u1(x86_fp80 0xK3FFF8000000000000000)
  %u1_272 = tail call i32 (i8*, ...) @printf(i8* getelementptr inbounds ([23 x i8], [23 x i8]* @.str.u1_132, i64 0, i64 0), i32 %u1_271, i32 %u1_271)
  %u1_273 = tail call i32 @u1(x86_fp80 0xK40008000000000000000)
  %u1_274 = tail call i32 (i8*, ...) @printf(i8* getelementptr inbounds ([23 x i8], [23 x i8]* @.str.u1_133, i64 0, i64 0), i32 %u1_273, i32 %u1_273)
  %u1_275 = tail call i32 @u1(x86_fp80 0xK4000C000000000000000)
  %u1_276 = tail call i32 (i8*, ...) @printf(i8* getelementptr inbounds ([23 x i8], [23 x i8]* @.str.u1_134, i64 0, i64 0), i32 %u1_275, i32 %u1_275)
  %u1_277 = tail call i32 @u1(x86_fp80 0xK40018000000000000000)
  %u1_278 = tail call i32 (i8*, ...) @printf(i8* getelementptr inbounds ([23 x i8], [23 x i8]* @.str.u1_135, i64 0, i64 0), i32 %u1_277, i32 %u1_277)

  %u1_248 = tail call i32 @putchar(i32 10)

  %u1_zext_249 = tail call i32 @u1_zext(x86_fp80 0xKC0018000000000000000)
  %u1_zext_250 = tail call i32 (i8*, ...) @printf(i8* getelementptr inbounds ([28 x i8], [28 x i8]* @.str.u1_zext_121, i64 0, i64 0), i32 %u1_zext_249, i32 %u1_zext_249)
  %u1_zext_251 = tail call i32 @u1_zext(x86_fp80 0xKC000C000000000000000)
  %u1_zext_252 = tail call i32 (i8*, ...) @printf(i8* getelementptr inbounds ([28 x i8], [28 x i8]* @.str.u1_zext_122, i64 0, i64 0), i32 %u1_zext_251, i32 %u1_zext_251)
  %u1_zext_253 = tail call i32 @u1_zext(x86_fp80 0xKC0008000000000000000)
  %u1_zext_254 = tail call i32 (i8*, ...) @printf(i8* getelementptr inbounds ([28 x i8], [28 x i8]* @.str.u1_zext_123, i64 0, i64 0), i32 %u1_zext_253, i32 %u1_zext_253)
  %u1_zext_255 = tail call i32 @u1_zext(x86_fp80 0xKBFFF8000000000000000)
  %u1_zext_256 = tail call i32 (i8*, ...) @printf(i8* getelementptr inbounds ([28 x i8], [28 x i8]* @.str.u1_zext_124, i64 0, i64 0), i32 %u1_zext_255, i32 %u1_zext_255)
  %u1_zext_257 = tail call i32 @u1_zext(x86_fp80 0xKBFFECCCCCCCCCCCCD000)
  %u1_zext_258 = tail call i32 (i8*, ...) @printf(i8* getelementptr inbounds ([28 x i8], [28 x i8]* @.str.u1_zext_125, i64 0, i64 0), i32 %u1_zext_257, i32 %u1_zext_257)
  %u1_zext_259 = tail call i32 @u1_zext(x86_fp80 0xKBFFE8000000000000000)
  %u1_zext_260 = tail call i32 (i8*, ...) @printf(i8* getelementptr inbounds ([28 x i8], [28 x i8]* @.str.u1_zext_126, i64 0, i64 0), i32 %u1_zext_259, i32 %u1_zext_259)
  %u1_zext_261 = tail call i32 @u1_zext(x86_fp80 0xKBFFD9999999999999800)
  %u1_zext_262 = tail call i32 (i8*, ...) @printf(i8* getelementptr inbounds ([28 x i8], [28 x i8]* @.str.u1_zext_127, i64 0, i64 0), i32 %u1_zext_261, i32 %u1_zext_261)
  %u1_zext_263 = tail call i32 @u1_zext(x86_fp80 0xK00000000000000000000)
  %u1_zext_264 = tail call i32 (i8*, ...) @printf(i8* getelementptr inbounds ([28 x i8], [28 x i8]* @.str.u1_zext_128, i64 0, i64 0), i32 %u1_zext_263, i32 %u1_zext_263)
  %u1_zext_265 = tail call i32 @u1_zext(x86_fp80 0xK3FFD9999999999999800)
  %u1_zext_266 = tail call i32 (i8*, ...) @printf(i8* getelementptr inbounds ([28 x i8], [28 x i8]* @.str.u1_zext_129, i64 0, i64 0), i32 %u1_zext_265, i32 %u1_zext_265)
  %u1_zext_267 = tail call i32 @u1_zext(x86_fp80 0xK3FFE8000000000000000)
  %u1_zext_268 = tail call i32 (i8*, ...) @printf(i8* getelementptr inbounds ([28 x i8], [28 x i8]* @.str.u1_zext_130, i64 0, i64 0), i32 %u1_zext_267, i32 %u1_zext_267)
  %u1_zext_269 = tail call i32 @u1_zext(x86_fp80 0xK3FFECCCCCCCCCCCCD000)
  %u1_zext_270 = tail call i32 (i8*, ...) @printf(i8* getelementptr inbounds ([28 x i8], [28 x i8]* @.str.u1_zext_131, i64 0, i64 0), i32 %u1_zext_269, i32 %u1_zext_269)
  %u1_zext_271 = tail call i32 @u1_zext(x86_fp80 0xK3FFF8000000000000000)
  %u1_zext_272 = tail call i32 (i8*, ...) @printf(i8* getelementptr inbounds ([28 x i8], [28 x i8]* @.str.u1_zext_132, i64 0, i64 0), i32 %u1_zext_271, i32 %u1_zext_271)
  %u1_zext_273 = tail call i32 @u1_zext(x86_fp80 0xK40008000000000000000)
  %u1_zext_274 = tail call i32 (i8*, ...) @printf(i8* getelementptr inbounds ([28 x i8], [28 x i8]* @.str.u1_zext_133, i64 0, i64 0), i32 %u1_zext_273, i32 %u1_zext_273)
  %u1_zext_275 = tail call i32 @u1_zext(x86_fp80 0xK4000C000000000000000)
  %u1_zext_276 = tail call i32 (i8*, ...) @printf(i8* getelementptr inbounds ([28 x i8], [28 x i8]* @.str.u1_zext_134, i64 0, i64 0), i32 %u1_zext_275, i32 %u1_zext_275)
  %u1_zext_277 = tail call i32 @u1_zext(x86_fp80 0xK40018000000000000000)
  %u1_zext_278 = tail call i32 (i8*, ...) @printf(i8* getelementptr inbounds ([28 x i8], [28 x i8]* @.str.u1_zext_135, i64 0, i64 0), i32 %u1_zext_277, i32 %u1_zext_277)

  ret i32 0
}

declare i32 @printf(i8* nocapture readonly, ...)

declare i32 @putchar(i32)

attributes #0 = { noinline }
