target datalayout = "e-m:e-i64:64-f80:128-n8:16:32:64-S128"
target triple = "x86_64-unknown-linux-gnu"

; define i64* @alloca_exceed_size() {
;   %1 = alloca i64, i64 9000000000494665712, align 16
;   ret i64* %1
; }

define i64* @alloca_max_size() {
  %1 = alloca i64, i64 18446744073709551615, align 16 ; max alloca (0xFFFF_FFFF_FFFF_FFFF)
  store i64 42, i64* %1
  ret i64* %1
}

define i1* @alloca_max_size_i1() {
  %1 = alloca i1, i1 18446744073709551615, align 1 ; max alloca (0xFFFF_FFFF_FFFF_FFFF)
  store i1 1, i1* %1
  ret i1* %1
}

define i64* @alloca_max_size_i64() {
  %1 = alloca i64, i64 288230376151711743, align 16 ; max alloca (0xFFFF_FFFF_FFFF_FFFF / 64)
  store i64 42, i64* %1
  ret i64* %1
}

define i64* @alloca_overflow_int() {
  %1 = alloca i64, i64 18446744069414584336, align 16 ; cast to int would be positive (0xFFFF_FFFF_0000_0010)
  ret i64* %1
}

define i1* @alloca_overflow_int_i1() {
  %1 = alloca i1, i64 18446744069414584336, align 16 ; cast to int would be positive (0xFFFF_FFFF_0000_0010)
  ret i1* %1
}

define i64* @alloca_overflow_int_i64() {
  %1 = alloca i64, i64 288230376084602880, align 16 ; cast to int would be positive (0xFFFF_FFFF_0000_0010 / 64)
  ret i64* %1
}

define i64* @alloca_parameter(i64) {
  %2 = alloca i64, i64 %0, align 16
  store i64 42, i64* %2
  ret i64* %2
}

define i64* @alloca_array_exceed_size() {
  %1 = alloca [9000000000494665712 x i8], align 16
  %2 = bitcast [9000000000494665712 x i8]* %1 to i64*
  ret i64* %2
}

define i64 @alloca_array_negative_offset() {
  %1 = alloca [16 x i16], align 16
  %2 = getelementptr [16 x i16], [16 x i16]* %1, i64 0, i64 -1
  %3 = bitcast [16 x i16]* %1 to i16*
  %4 = ptrtoint i16* %2 to i64
  %5 = ptrtoint i16* %3 to i64
  %6 = sub i64 %5, %4
  ret i64 %6
}

define i64* @alloca_array_overflow_int() {
  %1 = alloca [18446744069414584336 x i8], align 16 ; cast to int would be positive (0xFFFF_FFFF_0000_0010)
  %2 = bitcast [18446744069414584336 x i8]* %1 to i64*
  ret i64* %2
}

define i1 @array_max_size_ptr([18446744073709551615 x i8]*) { ; max vector size 0xFFFFFFFF (-1)
 %2 = icmp eq [18446744073709551615  x i8]* %0, null
 ret i1 %2
}

define i64* @alloca_vector_int_min_value() {
  %1 = alloca <2147483648 x i8>, align 16    ; 0x80000000 (Integer.MIN_VALUE)
  %2 = bitcast <2147483648 x i8>* %1 to i64*
  ret i64* %2
}

define i64* @alloca_vector_int_minus_one() {
  %1 = alloca <4294967295 x i8>, align 16    ; max vector size 0xFFFFFFFF (-1)
  %2 = bitcast <4294967295 x i8>* %1 to i64*
  ret i64* %2
}

define i1 @vector_max_size_ptr(<4294967295 x i8>*) { ; max vector size 0xFFFFFFFF (-1)
 %2 = icmp eq <4294967295 x i8>* %0, null
 ret i1 %2
}

define i1 @alloca_varwidth_min_int_bits() {
  %1 = alloca i1, align 16
  store i1 1, i1* %1
  %2 = load i1, i1* %1
  ret i1 %2
}

define i64 @alloca_varwidth_max_int_bits() {
  %1 = alloca i64, align 16
  %2 = alloca i16777215, align 16          ; (1<<24)-1 (VariableBitWidthType.MAX_INT_BITS)
  store i16777215 1, i16777215* %2         ; check whether we can store to it
  %3 = bitcast i16777215* %2 to i64*
  %4 = bitcast i64* %1 to i64*
  %5 = ptrtoint i64* %3 to i64
  %6 = ptrtoint i64* %4 to i64
  %7 = sub i64 %6, %5
  ret i64 %7
}

define i1 @varwidth_max_int_bits_ptr(i16777215*) { ; (1<<24)-1 (VariableBitWidthType.MAX_INT_BITS)
 %2 = icmp eq i16777215* %0, null
 ret i1 %2
}

;
; define i32 @main() {
;   call i64* @alloca_overflow_int()
;   ret i32 0
; }
