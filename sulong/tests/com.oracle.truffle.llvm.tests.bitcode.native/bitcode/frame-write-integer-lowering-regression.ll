; Regression coverage for integer frame writes lowered from wider integer and pointer values.

define i8 @identity(i8 %value) {
  ret i8 %value
}

define i32 @main() {
  %byte = call i8 @identity(i8 257)
  %byte32 = zext i8 %byte to i32
  %pointer = inttoptr i64 4294967297 to i8*
  %pointer32 = ptrtoint i8* %pointer to i32
  %byte.ok = icmp eq i32 %byte32, 1
  %pointer.ok = icmp eq i32 %pointer32, 1
  %ok = and i1 %byte.ok, %pointer.ok
  %status = select i1 %ok, i32 0, i32 1
  ret i32 %status
}
