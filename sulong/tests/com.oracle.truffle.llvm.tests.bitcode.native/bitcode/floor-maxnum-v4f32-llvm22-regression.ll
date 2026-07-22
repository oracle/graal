; Regression coverage for vector floor and maxnum, including maxnum NaN and signed-zero rules.

declare <4 x float> @llvm.floor.v4f32(<4 x float>)
declare <4 x float> @llvm.maxnum.v4f32(<4 x float>, <4 x float>)

define i32 @main() {
  %floor = call <4 x float> @llvm.floor.v4f32(<4 x float> <float 1.750000e+00, float -1.250000e+00, float 2.000000e+00, float -2.000000e+00>)
  %floor.matches = fcmp oeq <4 x float> %floor, <float 1.000000e+00, float -2.000000e+00, float 2.000000e+00, float -2.000000e+00>
  %floor.0 = extractelement <4 x i1> %floor.matches, i32 0
  %floor.1 = extractelement <4 x i1> %floor.matches, i32 1
  %floor.2 = extractelement <4 x i1> %floor.matches, i32 2
  %floor.3 = extractelement <4 x i1> %floor.matches, i32 3
  %floor.01 = and i1 %floor.0, %floor.1
  %floor.23 = and i1 %floor.2, %floor.3
  %floor.all = and i1 %floor.01, %floor.23

  %maxnum = call <4 x float> @llvm.maxnum.v4f32(<4 x float> <float 0x7FF8000000000000, float -0.000000e+00, float -0.000000e+00, float 2.000000e+00>, <4 x float> <float 1.000000e+00, float 0.000000e+00, float -0.000000e+00, float -3.000000e+00>)
  %maxnum.0 = extractelement <4 x float> %maxnum, i32 0
  %maxnum.1 = extractelement <4 x float> %maxnum, i32 1
  %maxnum.2 = extractelement <4 x float> %maxnum, i32 2
  %maxnum.3 = extractelement <4 x float> %maxnum, i32 3
  %maxnum.0.match = fcmp oeq float %maxnum.0, 1.000000e+00
  %maxnum.1.bits = bitcast float %maxnum.1 to i32
  %maxnum.2.bits = bitcast float %maxnum.2 to i32
  %maxnum.3.match = fcmp oeq float %maxnum.3, 2.000000e+00
  %maxnum.1.match = icmp eq i32 %maxnum.1.bits, 0
  %maxnum.2.match = icmp eq i32 %maxnum.2.bits, -2147483648
  %maxnum.01 = and i1 %maxnum.0.match, %maxnum.1.match
  %maxnum.23 = and i1 %maxnum.2.match, %maxnum.3.match
  %maxnum.all = and i1 %maxnum.01, %maxnum.23

  %all = and i1 %floor.all, %maxnum.all
  %status = select i1 %all, i32 0, i32 1
  ret i32 %status
}
