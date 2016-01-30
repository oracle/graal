define i32 @add(i32 %a, i32 %b) {
	%1 = add i32 %a, %b
	ret i32 %1
}

define i32 @main() {
	%1 = call i32 @add(i32 1, i32 14)
	ret i32 %1
}
