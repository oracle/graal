define i1 @main() {
	%1 = bitcast double 1048575.0 to i64
	%2 = icmp eq i64 %1, 4697254402757492736
	ret i1 %2
}
