define i1 @main() {
	%1 = bitcast i64 4697254402757492736 to double
	%2 = fcmp oeq double %1, 1048575.0
	ret i1 %2
}
