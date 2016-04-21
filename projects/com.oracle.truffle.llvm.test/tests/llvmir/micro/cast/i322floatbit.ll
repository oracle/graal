define i1 @main() {
	%1 = bitcast i32 1233125360 to float
	%2 = fcmp oeq float %1, 1048575.0
	ret i1 %2
}
