; Regression test for float/double vector fptoui conversions.

@.str = private unnamed_addr constant [13 x i8] c"%d,%d,%d,%d\0A\00"

define i32 @main() {
  %i16 = fptoui <2 x float> <float 4.200000e+01, float 6.553500e+04> to <2 x i16>
  %i32 = fptoui <2 x double> <double 2.147483648000000e+09, double 4.294967295000000e+09> to <2 x i32>
  %i16.0 = extractelement <2 x i16> %i16, i64 0
  %i16.1 = extractelement <2 x i16> %i16, i64 1
  %i32.0 = extractelement <2 x i32> %i32, i64 0
  %i32.1 = extractelement <2 x i32> %i32, i64 1
  %i16.0.ext = zext i16 %i16.0 to i32
  %i16.1.ext = zext i16 %i16.1 to i32
  call i32 (ptr, ...) @printf(ptr @.str, i32 %i16.0.ext, i32 %i16.1.ext, i32 %i32.0, i32 %i32.1)
  ret i32 0
}

declare i32 @printf(ptr, ...)
