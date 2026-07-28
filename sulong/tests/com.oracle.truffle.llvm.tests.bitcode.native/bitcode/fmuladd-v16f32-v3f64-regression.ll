; Regression coverage for LLVM 22 vector llvm.fmuladd forms not handled by the legacy lowering.

declare <16 x float> @llvm.fmuladd.v16f32(<16 x float>, <16 x float>, <16 x float>)
declare <3 x double> @llvm.fmuladd.v3f64(<3 x double>, <3 x double>, <3 x double>)

define i32 @main() {
  %v16f32 = call <16 x float> @llvm.fmuladd.v16f32(<16 x float> <float 2.0, float 3.0, float 4.0, float 5.0, float 6.0, float 7.0, float 8.0, float 9.0, float 10.0, float 11.0, float 12.0, float 13.0, float 14.0, float 15.0, float 16.0, float 17.0>, <16 x float> <float 3.0, float 4.0, float 5.0, float 6.0, float 7.0, float 8.0, float 9.0, float 10.0, float 11.0, float 12.0, float 13.0, float 14.0, float 15.0, float 16.0, float 17.0, float 18.0>, <16 x float> <float 4.0, float 5.0, float 6.0, float 7.0, float 8.0, float 9.0, float 10.0, float 11.0, float 12.0, float 13.0, float 14.0, float 15.0, float 16.0, float 17.0, float 18.0, float 19.0>)
  %v3f64 = call <3 x double> @llvm.fmuladd.v3f64(<3 x double> <double 4.0, double 5.0, double 6.0>, <3 x double> <double 5.0, double 6.0, double 7.0>, <3 x double> <double 6.0, double 7.0, double 8.0>)

  %v16f32.result = extractelement <16 x float> %v16f32, i64 15
  %v3f64.result = extractelement <3 x double> %v3f64, i64 2
  %v16f32.correct = fcmp oeq float %v16f32.result, 3.250000e+02
  %v3f64.correct = fcmp oeq double %v3f64.result, 5.000000e+01
  %all = and i1 %v16f32.correct, %v3f64.correct
  %status = select i1 %all, i32 0, i32 1
  ret i32 %status
}
