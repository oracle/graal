define i32 @main() {
  %1 = icmp slt i32 5, 0
  br i1 %1, label %2, label %3

; <label>:2                                       ; preds = %0
  br label %4

; <label>:3                                       ; preds = %0
  br label %4

; <label>:4                                     ; preds = %3, %2
  %z.0 = phi i32 [ 6, %2 ], [ 7, %3 ]
  ret i32 %z.0
}
