; ModuleID = 'fptosi1.bc'
target datalayout = "e-m:e-i64:64-f80:128-n8:16:32:64-S128"
target triple = "x86_64-pc-linux-gnu"

@.str.i1_46 = private unnamed_addr constant [23 x i8] c"i1 -4.0 = %2d (%0.8x)\0A\00", align 1
@.str.i1_47 = private unnamed_addr constant [23 x i8] c"i1 -3.0 = %2d (%0.8x)\0A\00", align 1
@.str.i1_48 = private unnamed_addr constant [23 x i8] c"i1 -2.0 = %2d (%0.8x)\0A\00", align 1
@.str.i1_49 = private unnamed_addr constant [23 x i8] c"i1 -1.0 = %2d (%0.8x)\0A\00", align 1
@.str.i1_50 = private unnamed_addr constant [23 x i8] c"i1 -0.8 = %2d (%0.8x)\0A\00", align 1
@.str.i1_51 = private unnamed_addr constant [23 x i8] c"i1 -0.5 = %2d (%0.8x)\0A\00", align 1
@.str.i1_52 = private unnamed_addr constant [23 x i8] c"i1 -0.3 = %2d (%0.8x)\0A\00", align 1
@.str.i1_53 = private unnamed_addr constant [23 x i8] c"i1  0.0 = %2d (%0.8x)\0A\00", align 1
@.str.i1_54 = private unnamed_addr constant [23 x i8] c"i1  0.3 = %2d (%0.8x)\0A\00", align 1
@.str.i1_55 = private unnamed_addr constant [23 x i8] c"i1  0.5 = %2d (%0.8x)\0A\00", align 1
@.str.i1_56 = private unnamed_addr constant [23 x i8] c"i1  0.8 = %2d (%0.8x)\0A\00", align 1
@.str.i1_57 = private unnamed_addr constant [23 x i8] c"i1  1.0 = %2d (%0.8x)\0A\00", align 1
@.str.i1_58 = private unnamed_addr constant [23 x i8] c"i1  2.0 = %2d (%0.8x)\0A\00", align 1
@.str.i1_59 = private unnamed_addr constant [23 x i8] c"i1  3.0 = %2d (%0.8x)\0A\00", align 1
@.str.i1_60 = private unnamed_addr constant [23 x i8] c"i1  4.0 = %2d (%0.8x)\0A\00", align 1

@.str.i1_sext_46 = private unnamed_addr constant [28 x i8] c"i1_sext -4.0 = %2d (%0.8x)\0A\00", align 1
@.str.i1_sext_47 = private unnamed_addr constant [28 x i8] c"i1_sext -3.0 = %2d (%0.8x)\0A\00", align 1
@.str.i1_sext_48 = private unnamed_addr constant [28 x i8] c"i1_sext -2.0 = %2d (%0.8x)\0A\00", align 1
@.str.i1_sext_49 = private unnamed_addr constant [28 x i8] c"i1_sext -1.0 = %2d (%0.8x)\0A\00", align 1
@.str.i1_sext_50 = private unnamed_addr constant [28 x i8] c"i1_sext -0.8 = %2d (%0.8x)\0A\00", align 1
@.str.i1_sext_51 = private unnamed_addr constant [28 x i8] c"i1_sext -0.5 = %2d (%0.8x)\0A\00", align 1
@.str.i1_sext_52 = private unnamed_addr constant [28 x i8] c"i1_sext -0.3 = %2d (%0.8x)\0A\00", align 1
@.str.i1_sext_53 = private unnamed_addr constant [28 x i8] c"i1_sext  0.0 = %2d (%0.8x)\0A\00", align 1
@.str.i1_sext_54 = private unnamed_addr constant [28 x i8] c"i1_sext  0.3 = %2d (%0.8x)\0A\00", align 1
@.str.i1_sext_55 = private unnamed_addr constant [28 x i8] c"i1_sext  0.5 = %2d (%0.8x)\0A\00", align 1
@.str.i1_sext_56 = private unnamed_addr constant [28 x i8] c"i1_sext  0.8 = %2d (%0.8x)\0A\00", align 1
@.str.i1_sext_57 = private unnamed_addr constant [28 x i8] c"i1_sext  1.0 = %2d (%0.8x)\0A\00", align 1
@.str.i1_sext_58 = private unnamed_addr constant [28 x i8] c"i1_sext  2.0 = %2d (%0.8x)\0A\00", align 1
@.str.i1_sext_59 = private unnamed_addr constant [28 x i8] c"i1_sext  3.0 = %2d (%0.8x)\0A\00", align 1
@.str.i1_sext_60 = private unnamed_addr constant [28 x i8] c"i1_sext  4.0 = %2d (%0.8x)\0A\00", align 1

@.str.i1_zext_46 = private unnamed_addr constant [28 x i8] c"i1_zext -4.0 = %2d (%0.8x)\0A\00", align 1
@.str.i1_zext_47 = private unnamed_addr constant [28 x i8] c"i1_zext -3.0 = %2d (%0.8x)\0A\00", align 1
@.str.i1_zext_48 = private unnamed_addr constant [28 x i8] c"i1_zext -2.0 = %2d (%0.8x)\0A\00", align 1
@.str.i1_zext_49 = private unnamed_addr constant [28 x i8] c"i1_zext -1.0 = %2d (%0.8x)\0A\00", align 1
@.str.i1_zext_50 = private unnamed_addr constant [28 x i8] c"i1_zext -0.8 = %2d (%0.8x)\0A\00", align 1
@.str.i1_zext_51 = private unnamed_addr constant [28 x i8] c"i1_zext -0.5 = %2d (%0.8x)\0A\00", align 1
@.str.i1_zext_52 = private unnamed_addr constant [28 x i8] c"i1_zext -0.3 = %2d (%0.8x)\0A\00", align 1
@.str.i1_zext_53 = private unnamed_addr constant [28 x i8] c"i1_zext  0.0 = %2d (%0.8x)\0A\00", align 1
@.str.i1_zext_54 = private unnamed_addr constant [28 x i8] c"i1_zext  0.3 = %2d (%0.8x)\0A\00", align 1
@.str.i1_zext_55 = private unnamed_addr constant [28 x i8] c"i1_zext  0.5 = %2d (%0.8x)\0A\00", align 1
@.str.i1_zext_56 = private unnamed_addr constant [28 x i8] c"i1_zext  0.8 = %2d (%0.8x)\0A\00", align 1
@.str.i1_zext_57 = private unnamed_addr constant [28 x i8] c"i1_zext  1.0 = %2d (%0.8x)\0A\00", align 1
@.str.i1_zext_58 = private unnamed_addr constant [28 x i8] c"i1_zext  2.0 = %2d (%0.8x)\0A\00", align 1
@.str.i1_zext_59 = private unnamed_addr constant [28 x i8] c"i1_zext  3.0 = %2d (%0.8x)\0A\00", align 1
@.str.i1_zext_60 = private unnamed_addr constant [28 x i8] c"i1_zext  4.0 = %2d (%0.8x)\0A\00", align 1

define i32 @i1_zext(float) #0 {
  %2 = fptosi float %0 to i1
  %3 = zext i1 %2 to i32
  %4 = select i1 %2, i32 %3, i32 23
  ret i32 %4
}

define i32 @i1_sext(float) #0 {
  %2 = fptosi float %0 to i1
  %3 = sext i1 %2 to i32
  %4 = select i1 %2, i32 %3, i32 23
  ret i32 %4
}

define i32 @i1(float) #0 {
  %2 = fptosi float %0 to i1
  %3 = zext i1 %2 to i32
  ret i32 %3
}

define i32 @main() {
  %i1_94  = tail call i32 @i1(float -4.000000e+00)
  %i1_95  = tail call i32 (i8*, ...) @printf(i8* getelementptr inbounds ([23 x i8], [23 x i8]* @.str.i1_46, i64 0, i64 0), i32 %i1_94, i32 %i1_94)
  %i1_96  = tail call i32 @i1(float -3.000000e+00)
  %i1_97  = tail call i32 (i8*, ...) @printf(i8* getelementptr inbounds ([23 x i8], [23 x i8]* @.str.i1_47, i64 0, i64 0), i32 %i1_96, i32 %i1_96)
  %i1_98  = tail call i32 @i1(float -2.000000e+00)
  %i1_99  = tail call i32 (i8*, ...) @printf(i8* getelementptr inbounds ([23 x i8], [23 x i8]* @.str.i1_48, i64 0, i64 0), i32 %i1_98, i32 %i1_98)
  %i1_100 = tail call i32 @i1(float -1.000000e+00)
  %i1_101 = tail call i32 (i8*, ...) @printf(i8* getelementptr inbounds ([23 x i8], [23 x i8]* @.str.i1_49, i64 0, i64 0), i32 %i1_100, i32 %i1_100)
  %i1_102 = tail call i32 @i1(float 0xBFE99999A0000000)
  %i1_103 = tail call i32 (i8*, ...) @printf(i8* getelementptr inbounds ([23 x i8], [23 x i8]* @.str.i1_50, i64 0, i64 0), i32 %i1_102, i32 %i1_102)
  %i1_104 = tail call i32 @i1(float -5.000000e-01)
  %i1_105 = tail call i32 (i8*, ...) @printf(i8* getelementptr inbounds ([23 x i8], [23 x i8]* @.str.i1_51, i64 0, i64 0), i32 %i1_104, i32 %i1_104)
  %i1_106 = tail call i32 @i1(float 0xBFD3333340000000)
  %i1_107 = tail call i32 (i8*, ...) @printf(i8* getelementptr inbounds ([23 x i8], [23 x i8]* @.str.i1_52, i64 0, i64 0), i32 %i1_106, i32 %i1_106)
  %i1_108 = tail call i32 @i1(float 0.000000e+00)
  %i1_109 = tail call i32 (i8*, ...) @printf(i8* getelementptr inbounds ([23 x i8], [23 x i8]* @.str.i1_53, i64 0, i64 0), i32 %i1_108, i32 %i1_108)
  %i1_110 = tail call i32 @i1(float 0x3FD3333340000000)
  %i1_111 = tail call i32 (i8*, ...) @printf(i8* getelementptr inbounds ([23 x i8], [23 x i8]* @.str.i1_54, i64 0, i64 0), i32 %i1_110, i32 %i1_110)
  %i1_112 = tail call i32 @i1(float 5.000000e-01)
  %i1_113 = tail call i32 (i8*, ...) @printf(i8* getelementptr inbounds ([23 x i8], [23 x i8]* @.str.i1_55, i64 0, i64 0), i32 %i1_112, i32 %i1_112)
  %i1_114 = tail call i32 @i1(float 0x3FE99999A0000000)
  %i1_115 = tail call i32 (i8*, ...) @printf(i8* getelementptr inbounds ([23 x i8], [23 x i8]* @.str.i1_56, i64 0, i64 0), i32 %i1_114, i32 %i1_114)
  %i1_116 = tail call i32 @i1(float 1.000000e+00)
  %i1_117 = tail call i32 (i8*, ...) @printf(i8* getelementptr inbounds ([23 x i8], [23 x i8]* @.str.i1_57, i64 0, i64 0), i32 %i1_116, i32 %i1_116)
  %i1_118 = tail call i32 @i1(float 2.000000e+00)
  %i1_119 = tail call i32 (i8*, ...) @printf(i8* getelementptr inbounds ([23 x i8], [23 x i8]* @.str.i1_58, i64 0, i64 0), i32 %i1_118, i32 %i1_118)
  %i1_120 = tail call i32 @i1(float 3.000000e+00)
  %i1_121 = tail call i32 (i8*, ...) @printf(i8* getelementptr inbounds ([23 x i8], [23 x i8]* @.str.i1_59, i64 0, i64 0), i32 %i1_120, i32 %i1_120)
  %i1_122 = tail call i32 @i1(float 4.000000e+00)
  %i1_123 = tail call i32 (i8*, ...) @printf(i8* getelementptr inbounds ([23 x i8], [23 x i8]* @.str.i1_60, i64 0, i64 0), i32 %i1_122, i32 %i1_122)

  %u1_248 = tail call i32 @putchar(i32 10)

  %i1_zext_94  = tail call i32 @i1_zext(float -4.000000e+00)
  %i1_zext_95  = tail call i32 (i8*, ...) @printf(i8* getelementptr inbounds ([28 x i8], [28 x i8]* @.str.i1_zext_46, i64 0, i64 0), i32 %i1_zext_94, i32 %i1_zext_94)
  %i1_zext_96  = tail call i32 @i1_zext(float -3.000000e+00)
  %i1_zext_97  = tail call i32 (i8*, ...) @printf(i8* getelementptr inbounds ([28 x i8], [28 x i8]* @.str.i1_zext_47, i64 0, i64 0), i32 %i1_zext_96, i32 %i1_zext_96)
  %i1_zext_98  = tail call i32 @i1_zext(float -2.000000e+00)
  %i1_zext_99  = tail call i32 (i8*, ...) @printf(i8* getelementptr inbounds ([28 x i8], [28 x i8]* @.str.i1_zext_48, i64 0, i64 0), i32 %i1_zext_98, i32 %i1_zext_98)
  %i1_zext_100 = tail call i32 @i1_zext(float -1.000000e+00)
  %i1_zext_101 = tail call i32 (i8*, ...) @printf(i8* getelementptr inbounds ([28 x i8], [28 x i8]* @.str.i1_zext_49, i64 0, i64 0), i32 %i1_zext_100, i32 %i1_zext_100)
  %i1_zext_102 = tail call i32 @i1_zext(float 0xBFE99999A0000000)
  %i1_zext_103 = tail call i32 (i8*, ...) @printf(i8* getelementptr inbounds ([28 x i8], [28 x i8]* @.str.i1_zext_50, i64 0, i64 0), i32 %i1_zext_102, i32 %i1_zext_102)
  %i1_zext_104 = tail call i32 @i1_zext(float -5.000000e-01)
  %i1_zext_105 = tail call i32 (i8*, ...) @printf(i8* getelementptr inbounds ([28 x i8], [28 x i8]* @.str.i1_zext_51, i64 0, i64 0), i32 %i1_zext_104, i32 %i1_zext_104)
  %i1_zext_106 = tail call i32 @i1_zext(float 0xBFD3333340000000)
  %i1_zext_107 = tail call i32 (i8*, ...) @printf(i8* getelementptr inbounds ([28 x i8], [28 x i8]* @.str.i1_zext_52, i64 0, i64 0), i32 %i1_zext_106, i32 %i1_zext_106)
  %i1_zext_108 = tail call i32 @i1_zext(float 0.000000e+00)
  %i1_zext_109 = tail call i32 (i8*, ...) @printf(i8* getelementptr inbounds ([28 x i8], [28 x i8]* @.str.i1_zext_53, i64 0, i64 0), i32 %i1_zext_108, i32 %i1_zext_108)
  %i1_zext_110 = tail call i32 @i1_zext(float 0x3FD3333340000000)
  %i1_zext_111 = tail call i32 (i8*, ...) @printf(i8* getelementptr inbounds ([28 x i8], [28 x i8]* @.str.i1_zext_54, i64 0, i64 0), i32 %i1_zext_110, i32 %i1_zext_110)
  %i1_zext_112 = tail call i32 @i1_zext(float 5.000000e-01)
  %i1_zext_113 = tail call i32 (i8*, ...) @printf(i8* getelementptr inbounds ([28 x i8], [28 x i8]* @.str.i1_zext_55, i64 0, i64 0), i32 %i1_zext_112, i32 %i1_zext_112)
  %i1_zext_114 = tail call i32 @i1_zext(float 0x3FE99999A0000000)
  %i1_zext_115 = tail call i32 (i8*, ...) @printf(i8* getelementptr inbounds ([28 x i8], [28 x i8]* @.str.i1_zext_56, i64 0, i64 0), i32 %i1_zext_114, i32 %i1_zext_114)
  %i1_zext_116 = tail call i32 @i1_zext(float 1.000000e+00)
  %i1_zext_117 = tail call i32 (i8*, ...) @printf(i8* getelementptr inbounds ([28 x i8], [28 x i8]* @.str.i1_zext_57, i64 0, i64 0), i32 %i1_zext_116, i32 %i1_zext_116)
  %i1_zext_118 = tail call i32 @i1_zext(float 2.000000e+00)
  %i1_zext_119 = tail call i32 (i8*, ...) @printf(i8* getelementptr inbounds ([28 x i8], [28 x i8]* @.str.i1_zext_58, i64 0, i64 0), i32 %i1_zext_118, i32 %i1_zext_118)
  %i1_zext_120 = tail call i32 @i1_zext(float 3.000000e+00)
  %i1_zext_121 = tail call i32 (i8*, ...) @printf(i8* getelementptr inbounds ([28 x i8], [28 x i8]* @.str.i1_zext_59, i64 0, i64 0), i32 %i1_zext_120, i32 %i1_zext_120)
  %i1_zext_122 = tail call i32 @i1_zext(float 4.000000e+00)
  %i1_zext_123 = tail call i32 (i8*, ...) @printf(i8* getelementptr inbounds ([28 x i8], [28 x i8]* @.str.i1_zext_60, i64 0, i64 0), i32 %i1_zext_122, i32 %i1_zext_122)

  %u1_249 = tail call i32 @putchar(i32 10)

  %i1_sext_94  = tail call i32 @i1_sext(float -4.000000e+00)
  %i1_sext_95  = tail call i32 (i8*, ...) @printf(i8* getelementptr inbounds ([28 x i8], [28 x i8]* @.str.i1_sext_46, i64 0, i64 0), i32 %i1_sext_94, i32 %i1_sext_94)
  %i1_sext_96  = tail call i32 @i1_sext(float -3.000000e+00)
  %i1_sext_97  = tail call i32 (i8*, ...) @printf(i8* getelementptr inbounds ([28 x i8], [28 x i8]* @.str.i1_sext_47, i64 0, i64 0), i32 %i1_sext_96, i32 %i1_sext_96)
  %i1_sext_98  = tail call i32 @i1_sext(float -2.000000e+00)
  %i1_sext_99  = tail call i32 (i8*, ...) @printf(i8* getelementptr inbounds ([28 x i8], [28 x i8]* @.str.i1_sext_48, i64 0, i64 0), i32 %i1_sext_98, i32 %i1_sext_98)
  %i1_sext_100 = tail call i32 @i1_sext(float -1.000000e+00)
  %i1_sext_101 = tail call i32 (i8*, ...) @printf(i8* getelementptr inbounds ([28 x i8], [28 x i8]* @.str.i1_sext_49, i64 0, i64 0), i32 %i1_sext_100, i32 %i1_sext_100)
  %i1_sext_102 = tail call i32 @i1_sext(float 0xBFE99999A0000000)
  %i1_sext_103 = tail call i32 (i8*, ...) @printf(i8* getelementptr inbounds ([28 x i8], [28 x i8]* @.str.i1_sext_50, i64 0, i64 0), i32 %i1_sext_102, i32 %i1_sext_102)
  %i1_sext_104 = tail call i32 @i1_sext(float -5.000000e-01)
  %i1_sext_105 = tail call i32 (i8*, ...) @printf(i8* getelementptr inbounds ([28 x i8], [28 x i8]* @.str.i1_sext_51, i64 0, i64 0), i32 %i1_sext_104, i32 %i1_sext_104)
  %i1_sext_106 = tail call i32 @i1_sext(float 0xBFD3333340000000)
  %i1_sext_107 = tail call i32 (i8*, ...) @printf(i8* getelementptr inbounds ([28 x i8], [28 x i8]* @.str.i1_sext_52, i64 0, i64 0), i32 %i1_sext_106, i32 %i1_sext_106)
  %i1_sext_108 = tail call i32 @i1_sext(float 0.000000e+00)
  %i1_sext_109 = tail call i32 (i8*, ...) @printf(i8* getelementptr inbounds ([28 x i8], [28 x i8]* @.str.i1_sext_53, i64 0, i64 0), i32 %i1_sext_108, i32 %i1_sext_108)
  %i1_sext_110 = tail call i32 @i1_sext(float 0x3FD3333340000000)
  %i1_sext_111 = tail call i32 (i8*, ...) @printf(i8* getelementptr inbounds ([28 x i8], [28 x i8]* @.str.i1_sext_54, i64 0, i64 0), i32 %i1_sext_110, i32 %i1_sext_110)
  %i1_sext_112 = tail call i32 @i1_sext(float 5.000000e-01)
  %i1_sext_113 = tail call i32 (i8*, ...) @printf(i8* getelementptr inbounds ([28 x i8], [28 x i8]* @.str.i1_sext_55, i64 0, i64 0), i32 %i1_sext_112, i32 %i1_sext_112)
  %i1_sext_114 = tail call i32 @i1_sext(float 0x3FE99999A0000000)
  %i1_sext_115 = tail call i32 (i8*, ...) @printf(i8* getelementptr inbounds ([28 x i8], [28 x i8]* @.str.i1_sext_56, i64 0, i64 0), i32 %i1_sext_114, i32 %i1_sext_114)
  %i1_sext_116 = tail call i32 @i1_sext(float 1.000000e+00)
  %i1_sext_117 = tail call i32 (i8*, ...) @printf(i8* getelementptr inbounds ([28 x i8], [28 x i8]* @.str.i1_sext_57, i64 0, i64 0), i32 %i1_sext_116, i32 %i1_sext_116)
  %i1_sext_118 = tail call i32 @i1_sext(float 2.000000e+00)
  %i1_sext_119 = tail call i32 (i8*, ...) @printf(i8* getelementptr inbounds ([28 x i8], [28 x i8]* @.str.i1_sext_58, i64 0, i64 0), i32 %i1_sext_118, i32 %i1_sext_118)
  %i1_sext_120 = tail call i32 @i1_sext(float 3.000000e+00)
  %i1_sext_121 = tail call i32 (i8*, ...) @printf(i8* getelementptr inbounds ([28 x i8], [28 x i8]* @.str.i1_sext_59, i64 0, i64 0), i32 %i1_sext_120, i32 %i1_sext_120)
  %i1_sext_122 = tail call i32 @i1_sext(float 4.000000e+00)
  %i1_sext_123 = tail call i32 (i8*, ...) @printf(i8* getelementptr inbounds ([28 x i8], [28 x i8]* @.str.i1_sext_60, i64 0, i64 0), i32 %i1_sext_122, i32 %i1_sext_122)

  ret i32 0
}

declare i32 @printf(i8* nocapture readonly, ...)

declare i32 @putchar(i32)

attributes #0 = { noinline }
