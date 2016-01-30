define i32 @main() {
  %1 = icmp slt i32 5, 0
  br i1 %1, label %2, label %4

; <label>:2                                       ; preds = %0
  %3 = add nsw i32 5, 1
  br label %6

; <label>:4                                       ; preds = %0
  %5 = add nsw i32 5, 2
  br label %6

; <label>:6                                       ; preds = %4, %2
  %z.0 = phi i32 [ %3, %2 ], [ %5, %4 ]
  ret i32 %z.0
}