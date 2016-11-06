define i32 @times(i32 %a, i32 %b) {
	%1 = add i32 %a, %b
	ret i32 %1
}
                 
define i32 @main() {
	%1 = alloca i32
	store i32 13, i32* %1
	%2 = load i32* %1
	%3 = call i32 @times(i32 %2, i32 14)
	ret i32 %3
}
