define i1 @main() {
	%1 = bitcast float 1048575.0 to i32
	%2 = icmp eq i32 %1, 1233125360
	ret i1 %2
}
