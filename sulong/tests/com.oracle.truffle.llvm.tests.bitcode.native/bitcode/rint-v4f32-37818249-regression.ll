declare <4 x float> @llvm.rint.v4f32(<4 x float>)

define i32 @main() {
entry:
  %rint = call <4 x float> @llvm.rint.v4f32(<4 x float> <float 1.500000e+00, float 2.500000e+00, float -1.500000e+00, float -2.500000e+00>)
  %expected0 = insertelement <4 x float> poison, float 2.000000e+00, i32 0
  %expected1 = insertelement <4 x float> %expected0, float 2.000000e+00, i32 1
  %expected2 = insertelement <4 x float> %expected1, float -2.000000e+00, i32 2
  %expected = insertelement <4 x float> %expected2, float -2.000000e+00, i32 3
  %matches = fcmp oeq <4 x float> %rint, %expected
  %match0 = extractelement <4 x i1> %matches, i32 0
  %match1 = extractelement <4 x i1> %matches, i32 1
  %match2 = extractelement <4 x i1> %matches, i32 2
  %match3 = extractelement <4 x i1> %matches, i32 3
  %all0 = and i1 %match0, %match1
  %all1 = and i1 %match2, %match3
  %all = and i1 %all0, %all1
  %exit = select i1 %all, i32 0, i32 1
  ret i32 %exit
}
