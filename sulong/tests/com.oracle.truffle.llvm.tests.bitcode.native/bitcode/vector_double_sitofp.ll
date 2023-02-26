target datalayout = "e-m:e-i64:64-f80:128-n8:16:32:64-S128"
target triple = "x86_64-unknown-linux-gnu"

@.str = private unnamed_addr constant [8 x i8] c"%lf,%lf\00"

define i32 @main() #0 {
  %1 = sitofp <4 x i1> <i1 0, i1 -1, i1 0, i1 -1> to <4 x double>
  %2 = extractelement <4 x double> %1, i64 0
  %3 = extractelement <4 x double> %1, i64 1
  %4 = call i32 (i8*, ...) @printf(i8* nonnull dereferenceable(1) getelementptr inbounds ([8 x i8], [8 x i8]* @.str, i64 0, i64 0), double %2, double %3)
  ret i32 0
}

declare i32 @printf(i8* nocapture readonly, ...) #1
