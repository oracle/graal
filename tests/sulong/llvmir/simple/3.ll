define i32 @main() {
  %1 = alloca i32
  store i32 5, i32* %1
  %2 = load i32* %1
  ret i32 %2
}
