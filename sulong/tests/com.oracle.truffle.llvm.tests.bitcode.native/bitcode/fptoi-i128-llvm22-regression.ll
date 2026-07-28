target triple = "x86_64-unknown-linux-gnu"

define i32 @main() {
  %signed.float = fptosi float 4.750000e+00 to i128
  %signed.float.ok = icmp eq i128 %signed.float, 4
  %signed.double = fptosi double -9.750000e+00 to i128
  %signed.double.ok = icmp eq i128 %signed.double, -9
  %unsigned.float = fptoui float 6.500000e+00 to i128
  %unsigned.float.ok = icmp eq i128 %unsigned.float, 6
  %unsigned.double = fptoui double 0x4630000000000000 to i128
  %unsigned.double.ok = icmp eq i128 %unsigned.double, 1267650600228229401496703205376
  %signed.ok = and i1 %signed.float.ok, %signed.double.ok
  %unsigned.ok = and i1 %unsigned.float.ok, %unsigned.double.ok
  %ok = and i1 %signed.ok, %unsigned.ok
  %result = select i1 %ok, i32 0, i32 1
  ret i32 %result
}
