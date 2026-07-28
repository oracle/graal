; Regression coverage for llvm.ssub.sat.v8i16 lane-wise saturation.

declare <8 x i16> @llvm.ssub.sat.v8i16(<8 x i16>, <8 x i16>)

define i32 @main() {
  %result = call <8 x i16> @llvm.ssub.sat.v8i16(
      <8 x i16> <i16 32767, i16 -32768, i16 100, i16 -100, i16 32767, i16 -32768, i16 0, i16 123>,
      <8 x i16> <i16 -1, i16 1, i16 50, i16 -50, i16 0, i16 0, i16 -1, i16 123>)
  %matches = icmp eq <8 x i16> %result, <i16 32767, i16 -32768, i16 50, i16 -50, i16 32767, i16 -32768, i16 1, i16 0>
  %lane0 = extractelement <8 x i1> %matches, i64 0
  %lane1 = extractelement <8 x i1> %matches, i64 1
  %lane2 = extractelement <8 x i1> %matches, i64 2
  %lane3 = extractelement <8 x i1> %matches, i64 3
  %lane4 = extractelement <8 x i1> %matches, i64 4
  %lane5 = extractelement <8 x i1> %matches, i64 5
  %lane6 = extractelement <8 x i1> %matches, i64 6
  %lane7 = extractelement <8 x i1> %matches, i64 7
  %all01 = and i1 %lane0, %lane1
  %all23 = and i1 %lane2, %lane3
  %all45 = and i1 %lane4, %lane5
  %all67 = and i1 %lane6, %lane7
  %all0123 = and i1 %all01, %all23
  %all4567 = and i1 %all45, %all67
  %all = and i1 %all0123, %all4567
  %status = select i1 %all, i32 0, i32 1
  ret i32 %status
}
