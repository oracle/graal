; Copyright (c) 2026, Oracle and/or its affiliates.
;
; Verify that the pattern is stored count times, rather than treating count as a byte length.

declare void @llvm.experimental.memset.pattern.p0.i32.i64(ptr, i32, i64, i1 immarg)

define i32 @main() {
  %values = alloca [4 x i32], align 4
  call void @llvm.experimental.memset.pattern.p0.i32.i64(ptr %values, i32 287454020, i64 4, i1 false)

  %value0.ptr = getelementptr [4 x i32], ptr %values, i64 0, i64 0
  %value3.ptr = getelementptr [4 x i32], ptr %values, i64 0, i64 3
  %value0 = load i32, ptr %value0.ptr, align 4
  %value3 = load i32, ptr %value3.ptr, align 4
  %check0 = icmp eq i32 %value0, 287454020
  %check3 = icmp eq i32 %value3, 287454020
  %valid = and i1 %check0, %check3
  %result = select i1 %valid, i32 0, i32 1
  ret i32 %result
}
