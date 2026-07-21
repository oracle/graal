declare void @llvm.set.rounding(i32)
declare double @llvm.rint.f64(double)
declare <2 x double> @llvm.rint.v2f64(<2 x double>)
declare double @llvm.vector.reduce.fadd.v2f64(double, <2 x double>)
declare i1 @llvm.is.fpclass.f64(double, i32 immarg)
declare double @llvm.experimental.constrained.fadd.f64(double, double, metadata, metadata)
declare double @llvm.experimental.constrained.fmul.f64(double, double, metadata, metadata)
declare double @llvm.experimental.constrained.fdiv.f64(double, double, metadata, metadata)

@reduce.half.ulp = global double 0x3CA0000000000000
@reduce.zero = global double 0.000000e+00
@rint.input = global double 1.500000e+00
@underflow.min = global double 0x0000000000000001
@underflow.negative.min = global double 0x8000000000000001
@overflow.max = global double 0x7FEFFFFFFFFFFFFF

define i32 @main() {
entry:
  %reduce.slot = alloca double
  call void @llvm.set.rounding(i32 2)
  %add.up = call double @llvm.experimental.constrained.fadd.f64(double 1.000000e+00, double 0x3CA0000000000000, metadata !"round.dynamic", metadata !"fpexcept.ignore")
  %rint.up.input = load volatile double, ptr @rint.input
  %rint.up = call double @llvm.rint.f64(double %rint.up.input)
  %rint.up.vector = call <2 x double> @llvm.rint.v2f64(<2 x double> <double 1.500000e+00, double -2.500000e+00>)
  %rint.up.0 = extractelement <2 x double> %rint.up.vector, i32 0
  %rint.up.1 = extractelement <2 x double> %rint.up.vector, i32 1
  %reduce.input.0 = load volatile double, ptr @reduce.half.ulp
  %reduce.input.1 = load volatile double, ptr @reduce.zero
  %reduce.vector.0 = insertelement <2 x double> poison, double %reduce.input.0, i32 0
  %reduce.vector = insertelement <2 x double> %reduce.vector.0, double %reduce.input.1, i32 1
  %reduce.up.raw = call double @llvm.vector.reduce.fadd.v2f64(double 1.000000e+00, <2 x double> %reduce.vector)
  store volatile double %reduce.up.raw, ptr %reduce.slot
  %mul.up = call double @llvm.experimental.constrained.fmul.f64(double 0x3FF0000000000001, double 0x3FF0000000000001, metadata !"round.dynamic", metadata !"fpexcept.ignore")
  %underflow.min.value = load volatile double, ptr @underflow.min
  %underflow.up = call double @llvm.experimental.constrained.fmul.f64(double %underflow.min.value, double 5.000000e-01, metadata !"round.dynamic", metadata !"fpexcept.ignore")
  call void @llvm.set.rounding(i32 3)
  %add.down = call double @llvm.experimental.constrained.fadd.f64(double 1.000000e+00, double 0x3CA0000000000000, metadata !"round.dynamic", metadata !"fpexcept.ignore")
  %rint.down.input = load volatile double, ptr @rint.input
  %rint.down = call double @llvm.rint.f64(double %rint.down.input)
  %cancel.down = call double @llvm.experimental.constrained.fadd.f64(double 1.000000e+00, double -1.000000e+00, metadata !"round.dynamic", metadata !"fpexcept.ignore")
  %overflow.down = call double @llvm.experimental.constrained.fadd.f64(double 0x7FEFFFFFFFFFFFFF, double 0x7FEFFFFFFFFFFFFF, metadata !"round.dynamic", metadata !"fpexcept.ignore")
  %div.down = call double @llvm.experimental.constrained.fdiv.f64(double 1.000000e+00, double 1.000000e+01, metadata !"round.dynamic", metadata !"fpexcept.ignore")
  %underflow.negative.min.value = load volatile double, ptr @underflow.negative.min
  %underflow.down = call double @llvm.experimental.constrained.fmul.f64(double %underflow.negative.min.value, double 5.000000e-01, metadata !"round.dynamic", metadata !"fpexcept.ignore")
  %overflow.max.value = load volatile double, ptr @overflow.max
  %reduce.overflow.vector.0 = insertelement <2 x double> poison, double %overflow.max.value, i32 0
  %reduce.overflow.vector = insertelement <2 x double> %reduce.overflow.vector.0, double 0.000000e+00, i32 1
  %reduce.overflow = call double @llvm.vector.reduce.fadd.v2f64(double %overflow.max.value, <2 x double> %reduce.overflow.vector)
  call void @llvm.set.rounding(i32 0)
  %div.zero = call double @llvm.experimental.constrained.fdiv.f64(double 1.000000e+00, double 1.000000e+01, metadata !"round.dynamic", metadata !"fpexcept.ignore")
  %reduce.up = load volatile double, ptr %reduce.slot
  %add.up.ok = fcmp oeq double %add.up, 0x3FF0000000000001
  %rint.up.ok = fcmp oeq double %rint.up, 2.000000e+00
  %rint.up.0.ok = fcmp oeq double %rint.up.0, 2.000000e+00
  %rint.up.1.ok = fcmp oeq double %rint.up.1, -2.000000e+00
  %reduce.up.ok = fcmp oeq double %reduce.up, 0x3FF0000000000001
  %mul.up.ok = fcmp oeq double %mul.up, 0x3FF0000000000003
  %underflow.up.ok = fcmp oeq double %underflow.up, 0x0000000000000001
  %add.down.ok = fcmp oeq double %add.down, 1.000000e+00
  %rint.down.ok = fcmp oeq double %rint.down, 1.000000e+00
  %cancel.down.ok = call i1 @llvm.is.fpclass.f64(double %cancel.down, i32 32)
  %overflow.down.ok = fcmp oeq double %overflow.down, 0x7FEFFFFFFFFFFFFF
  %div.down.ok = fcmp oeq double %div.down, 0x3FB9999999999999
  %underflow.down.ok = fcmp oeq double %underflow.down, 0x8000000000000001
  %reduce.overflow.ok = fcmp oeq double %reduce.overflow, 0x7FEFFFFFFFFFFFFF
  %div.zero.ok = fcmp oeq double %div.zero, 0x3FB9999999999999
  %ok0 = and i1 %add.up.ok, %rint.up.ok
  %ok1 = and i1 %reduce.up.ok, %add.down.ok
  %ok2 = and i1 %rint.down.ok, %ok0
  %ok3 = and i1 %mul.up.ok, %cancel.down.ok
  %ok4 = and i1 %overflow.down.ok, %div.down.ok
  %ok5 = and i1 %div.zero.ok, %rint.up.0.ok
  %ok6 = and i1 %rint.up.1.ok, %underflow.up.ok
  %ok7 = and i1 %underflow.down.ok, %reduce.overflow.ok
  %ok01 = and i1 %ok1, %ok2
  %ok34 = and i1 %ok3, %ok4
  %ok56 = and i1 %ok5, %ok6
  %ok3456 = and i1 %ok34, %ok56
  %ok34567 = and i1 %ok3456, %ok7
  %ok = and i1 %ok01, %ok34567
  %exit = select i1 %ok, i32 0, i32 1
  ret i32 %exit
}
