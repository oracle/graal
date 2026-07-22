; Regression coverage for the vector llvm.fmuladd intrinsic forms emitted by LLVM 22.

declare <2 x float> @llvm.fmuladd.v2f32(<2 x float>, <2 x float>, <2 x float>)
declare <4 x float> @llvm.fmuladd.v4f32(<4 x float>, <4 x float>, <4 x float>)
declare <4 x double> @llvm.fmuladd.v4f64(<4 x double>, <4 x double>, <4 x double>)
declare <8 x double> @llvm.fmuladd.v8f64(<8 x double>, <8 x double>, <8 x double>)

define i32 @main() {
  %v2f32 = call <2 x float> @llvm.fmuladd.v2f32(<2 x float> <float 2.0, float 3.0>, <2 x float> <float 3.0, float 4.0>, <2 x float> <float 4.0, float 5.0>)
  %v4f32 = call <4 x float> @llvm.fmuladd.v4f32(<4 x float> <float 3.0, float 4.0, float 5.0, float 6.0>, <4 x float> <float 4.0, float 5.0, float 6.0, float 7.0>, <4 x float> <float 5.0, float 6.0, float 7.0, float 8.0>)
  %v4f64 = call <4 x double> @llvm.fmuladd.v4f64(<4 x double> <double 4.0, double 5.0, double 6.0, double 7.0>, <4 x double> <double 5.0, double 6.0, double 7.0, double 8.0>, <4 x double> <double 6.0, double 7.0, double 8.0, double 9.0>)
  %v8f64 = call <8 x double> @llvm.fmuladd.v8f64(<8 x double> <double 5.0, double 6.0, double 7.0, double 8.0, double 9.0, double 10.0, double 11.0, double 12.0>, <8 x double> <double 6.0, double 7.0, double 8.0, double 9.0, double 10.0, double 11.0, double 12.0, double 13.0>, <8 x double> <double 7.0, double 8.0, double 9.0, double 10.0, double 11.0, double 12.0, double 13.0, double 14.0>)

  %v2f32.result = extractelement <2 x float> %v2f32, i64 0
  %v4f32.result = extractelement <4 x float> %v4f32, i64 0
  %v4f64.result = extractelement <4 x double> %v4f64, i64 0
  %v8f64.result = extractelement <8 x double> %v8f64, i64 0
  %v2f32.correct = fcmp oeq float %v2f32.result, 1.000000e+01
  %v4f32.correct = fcmp oeq float %v4f32.result, 1.700000e+01
  %v4f64.correct = fcmp oeq double %v4f64.result, 2.600000e+01
  %v8f64.correct = fcmp oeq double %v8f64.result, 3.700000e+01
  %all.float = and i1 %v2f32.correct, %v4f32.correct
  %all.double = and i1 %v4f64.correct, %v8f64.correct
  %all = and i1 %all.float, %all.double
  %status = select i1 %all, i32 0, i32 1
  ret i32 %status
}
