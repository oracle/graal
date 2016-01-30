define i32 @main() {
	%1 = select i1 true, i32 1, i32 2
	ret i32 %1
}
