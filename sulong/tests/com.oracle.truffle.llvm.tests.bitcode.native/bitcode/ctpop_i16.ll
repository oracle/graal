declare i8 @llvm.ctpop.i8(i8)
declare i16 @llvm.ctpop.i16(i16)
declare { i65, i1 } @llvm.sadd.with.overflow.i65(i65, i65)
declare { i65, i1 } @llvm.usub.with.overflow.i65(i65, i65)

define i32 @main() {
entry:
  %ctpop_i8 = call i8 @llvm.ctpop.i8(i8 -86)
  %ctpop_i8_ok = icmp eq i8 %ctpop_i8, 4

  %ctpop_i16 = call i16 @llvm.ctpop.i16(i16 -21846)
  %ctpop_i16_ok = icmp eq i16 %ctpop_i16, 8

  %signed = call { i65, i1 } @llvm.sadd.with.overflow.i65(i65 18446744073709551615, i65 1)
  %signed_result = extractvalue { i65, i1 } %signed, 0
  %signed_overflow = extractvalue { i65, i1 } %signed, 1
  %signed_result_ok = icmp eq i65 %signed_result, -18446744073709551616
  %unsigned = call { i65, i1 } @llvm.usub.with.overflow.i65(i65 0, i65 1)
  %unsigned_result = extractvalue { i65, i1 } %unsigned, 0
  %unsigned_overflow = extractvalue { i65, i1 } %unsigned, 1
  %unsigned_result_ok = icmp eq i65 %unsigned_result, 36893488147419103231
  %ctpop_ok = and i1 %ctpop_i8_ok, %ctpop_i16_ok
  %unsigned_overflow_exit = select i1 %unsigned_overflow, i32 0, i32 5
  %unsigned_result_exit = select i1 %unsigned_result_ok, i32 %unsigned_overflow_exit, i32 4
  %signed_overflow_exit = select i1 %signed_overflow, i32 %unsigned_result_exit, i32 3
  %signed_result_exit = select i1 %signed_result_ok, i32 %signed_overflow_exit, i32 2
  %exit = select i1 %ctpop_ok, i32 %signed_result_exit, i32 1
  ret i32 %exit
}
