define i32 @main() nounwind uwtable readnone {
  br label %.outer

.outer:                                           ; preds = %7, %0
  %i.0.ph = phi i32 [ 0, %0 ], [ %2, %7 ]
  %sum.0.ph = phi i32 [ 0, %0 ], [ %9, %7 ]
  br label %1

; <label>:1                                       ; preds = %.outer, %4
  %i.0 = phi i32 [ %2, %4 ], [ %i.0.ph, %.outer ]
  %2 = add nsw i32 %i.0, 1
  %3 = icmp slt i32 %i.0, 100
  br i1 %3, label %4, label %.loopexit

; <label>:4                                       ; preds = %1
  %5 = and i32 %2, 1
  %6 = icmp eq i32 %5, 0
  br i1 %6, label %1, label %7

; <label>:7                                       ; preds = %4
  %8 = icmp sgt i32 %2, 49
  %9 = add nsw i32 %sum.0.ph, 1
  br i1 %8, label %.loopexit, label %.outer

.loopexit:                                        ; preds = %7, %1
  ret i32 %sum.0.ph
}
