; ModuleID = 'phiFrameNuller.bc'
target datalayout = "e-m:e-i64:64-f80:128-n8:16:32:64-S128"
target triple = "x86_64-pc-linux-gnu"

@.str = private unnamed_addr constant [4 x i8] c"%d\0A\00", align 1

; Function Attrs: sspstrong uwtable
define i32 @advance(i32) {
  %2 = and i32 %0, 1
  %3 = icmp eq i32 %2, 0
  %4 = lshr i32 %0, 1
  %5 = mul nsw i32 %0, 3
  %6 = add nsw i32 %5, 1
  %7 = select i1 %3, i32 %4, i32 %6
  ret i32 %7
}

; Function Attrs: norecurse sspstrong uwtable
define i32 @main() personality i8* null {
  br label %3

; <label>:1:                                      ; preds = %3
  %2 = invoke i32 @advance(i32 %4)
          to label %3 unwind label %7

    ; This test tests a corner case in phi resolution, where the lifetime of a
    ; phi starts and ends at the same point in the code.
    ;
    ; The phi in question is %4. It is defined here, and the last usage is in
    ; the invoke %2, which is a direct prececessor of this block.
    ;
    ; This test ensures that the frame nuller and the phi resolution happen in
    ; the correct order: The frame nuller must first null out the old value of
    ; %4, then the phi resolution can produce the new value of %4.
; <label>:3:                                      ; preds = %1, %0
  %4 = phi i32 [ 27, %0 ], [ %2, %1 ]
  %5 = tail call i32 (i8*, ...) @printf(i8* getelementptr inbounds ([4 x i8], [4 x i8]* @.str, i64 0, i64 0), i32 %4)
  %6 = icmp sgt i32 %4, 1
  br i1 %6, label %1, label %9

; <label>:7:                                      ; preds = %5
  %8 = landingpad { i8*, i32 }
          cleanup
  resume { i8*, i32 } %8

; <label>:9:                                      ; preds = %1, %12
  ret i32 0
}

; Function Attrs: nounwind
declare i32 @printf(i8* nocapture readonly, ...)
