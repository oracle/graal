target triple = "x86_64-unknown-linux-gnu"

declare i1 @llvm.is.fpclass.f32(float, i32 immarg)
declare i1 @llvm.is.fpclass.f64(double, i32 immarg)

define i32 @main() {
  %snan32 = bitcast i32 2139095041 to float
  %qnan32 = bitcast i32 2143289344 to float
  %snan32.ok = call i1 @llvm.is.fpclass.f32(float %snan32, i32 1)
  %qnan32.ok = call i1 @llvm.is.fpclass.f32(float %qnan32, i32 2)
  %negative.zero32.ok = call i1 @llvm.is.fpclass.f32(float -0.000000e+00, i32 32)

  %snan64 = bitcast i64 9218868437227405313 to double
  %qnan64 = bitcast i64 9221120237041090560 to double
  %snan64.ok = call i1 @llvm.is.fpclass.f64(double %snan64, i32 1)
  %qnan64.ok = call i1 @llvm.is.fpclass.f64(double %qnan64, i32 2)
  %negative.zero64.ok = call i1 @llvm.is.fpclass.f64(double -0.000000e+00, i32 32)

  %nan32.ok = and i1 %snan32.ok, %qnan32.ok
  %f32.ok = and i1 %nan32.ok, %negative.zero32.ok
  %nan64.ok = and i1 %snan64.ok, %qnan64.ok
  %f64.ok = and i1 %nan64.ok, %negative.zero64.ok
  %ok = and i1 %f32.ok, %f64.ok
  %result = select i1 %ok, i32 0, i32 1
  ret i32 %result
}
