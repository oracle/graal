target triple = "x86_64-unknown-linux-gnu"

@counter = global i64 10

declare i65 @llvm.ctpop.i65(i65)
declare float @llvm.vector.reduce.fmax.v2f32(<2 x float>)
declare double @llvm.vector.reduce.fmax.v2f64(<2 x double>)
declare i1 @llvm.is.fpclass.f32(float, i32 immarg)
declare i1 @llvm.is.fpclass.f64(double, i32 immarg)

define i32 @main() {
  %unsigned.fp80 = uitofp i64 -1 to x86_fp80
  %unsigned.roundtrip = fptoui x86_fp80 %unsigned.fp80 to i64
  %unsigned.ok = icmp eq i64 %unsigned.roundtrip, -1
  %signed.fp80 = sitofp i64 -9223372036854775808 to x86_fp80
  %signed.roundtrip = fptosi x86_fp80 %signed.fp80 to i64
  %signed.ok = icmp eq i64 %signed.roundtrip, -9223372036854775808

  %vector = fptoui <2 x double> <double 3.276800e+04, double 6.553500e+04> to <2 x i16>
  %vector.0 = extractelement <2 x i16> %vector, i32 0
  %vector.1 = extractelement <2 x i16> %vector, i32 1
  %vector.0.ok = icmp eq i16 %vector.0, -32768
  %vector.1.ok = icmp eq i16 %vector.1, -1

  ; Values below 2^31 must still take the signed conversion path before reinterpreting the bits.
  %vector32 = fptoui <2 x double> <double 2.1474836475e+09, double 4.294967295e+09> to <2 x i32>
  %vector32.0 = extractelement <2 x i32> %vector32, i32 0
  %vector32.1 = extractelement <2 x i32> %vector32, i32 1
  %vector32.0.ok = icmp eq i32 %vector32.0, 2147483647
  %vector32.1.ok = icmp eq i32 %vector32.1, -1

  %popcount = call i65 @llvm.ctpop.i65(i65 -1)
  %popcount.ok = icmp eq i65 %popcount, 65

  %old = atomicrmw add ptr @counter, i64 5 seq_cst
  %new = load i64, ptr @counter
  %old.ok = icmp eq i64 %old, 10
  %new.ok = icmp eq i64 %new, 15

  ; Pointer-valued xchg on native storage must accept pointer objects, not only Java longs.
  %pointer.storage = alloca ptr
  %first = inttoptr i64 4096 to ptr
  %second = inttoptr i64 8192 to ptr
  store ptr %first, ptr %pointer.storage
  %old.pointer = atomicrmw xchg ptr %pointer.storage, ptr %second seq_cst
  %new.pointer = load ptr, ptr %pointer.storage
  %old.pointer.ok = icmp eq ptr %old.pointer, %first
  %new.pointer.ok = icmp eq ptr %new.pointer, %second

  ; fmax reductions use maxnum semantics and ignore a NaN when a numeric lane is present.
  %maximum = call double @llvm.vector.reduce.fmax.v2f64(<2 x double> <double 0x7FF8000000000000, double 3.000000e+00>)
  %maximum.ok = fcmp oeq double %maximum, 3.000000e+00
  %all.nan.maximum.f32 = call float @llvm.vector.reduce.fmax.v2f32(<2 x float> <float 0x7FF8000000000000, float 0x7FF8000000000000>)
  %all.nan.maximum.f64 = call double @llvm.vector.reduce.fmax.v2f64(<2 x double> <double 0x7FF8000000000000, double 0x7FF8000000000000>)
  %all.nan.maximum.f32.ok = call i1 @llvm.is.fpclass.f32(float %all.nan.maximum.f32, i32 3)
  %all.nan.maximum.f64.ok = call i1 @llvm.is.fpclass.f64(double %all.nan.maximum.f64, i32 3)

  %a = and i1 %unsigned.ok, %signed.ok
  %b = and i1 %vector.0.ok, %vector.1.ok
  %b32 = and i1 %vector32.0.ok, %vector32.1.ok
  %c = and i1 %popcount.ok, %old.ok
  %d = and i1 %new.ok, %a
  %e = and i1 %b, %c
  %pointers.ok = and i1 %old.pointer.ok, %new.pointer.ok
  %extra0 = and i1 %b32, %pointers.ok
  %all.nan.maximum.ok = and i1 %all.nan.maximum.f32.ok, %all.nan.maximum.f64.ok
  %maximums.ok = and i1 %maximum.ok, %all.nan.maximum.ok
  %extra = and i1 %extra0, %maximums.ok
  %base.ok = and i1 %d, %e
  %ok = and i1 %base.ok, %extra
  %result = select i1 %ok, i32 0, i32 1
  ret i32 %result
}
