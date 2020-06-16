; ModuleID = 'gep-vec.bc'
target datalayout = "e-m:e-i64:64-f80:128-n8:16:32:64-S128"
target triple = "x86_64-unknown-linux-gnu"

define void @doloop(float** nocapture, float*, i32* nocapture readonly, i32* nocapture readonly) {
  br label %5

5:                                                ; preds = %31, %4
  %6 = phi i64 [ 0, %4 ], [ %32, %31 ]
  %7 = shl i64 %6, 2
  %8 = insertelement <4 x i64> undef, i64 %7, i32 0
  %9 = shufflevector <4 x i64> %8, <4 x i64> undef, <4 x i32> zeroinitializer
  br label %10

10:                                               ; preds = %10, %5
  %11 = phi i64 [ 0, %5 ], [ %27, %10 ]
  %12 = phi <4 x i64> [ <i64 0, i64 1, i64 2, i64 3>, %5 ], [ %28, %10 ]
  %13 = add nuw nsw <4 x i64> %12, %9
  %14 = getelementptr inbounds float, float* %1, <4 x i64> %13
  %15 = extractelement <4 x i64> %13, i32 0
  %16 = getelementptr inbounds i32, i32* %2, i64 %15
  %17 = bitcast i32* %16 to <4 x i32>*
  %18 = load <4 x i32>, <4 x i32>* %17, align 4, !tbaa !0
  %19 = getelementptr inbounds i32, i32* %3, i64 %15
  %20 = bitcast i32* %19 to <4 x i32>*
  %21 = load <4 x i32>, <4 x i32>* %20, align 4, !tbaa !0
  %22 = mul nsw <4 x i32> %21, %18
  %23 = sext <4 x i32> %22 to <4 x i64>
  %24 = getelementptr inbounds float, <4 x float*> %14, <4 x i64> %23
  %25 = getelementptr inbounds float*, float** %0, i64 %15
  %26 = bitcast float** %25 to <4 x float*>*
  store <4 x float*> %24, <4 x float*>* %26, align 8, !tbaa !4
  %27 = add i64 %11, 4
  %28 = add <4 x i64> %12, <i64 4, i64 4, i64 4, i64 4>
  %29 = icmp eq i64 %11, 0
  br i1 %29, label %31, label %10, !llvm.loop !6

30:                                               ; preds = %31
  ret void

31:                                               ; preds = %10
  %32 = add nuw nsw i64 %6, 1
  %33 = icmp eq i64 %32, 128
  br i1 %33, label %30, label %5
}

!0 = !{!1, !1, i64 0}
!1 = !{!"int", !2, i64 0}
!2 = !{!"omnipotent char", !3, i64 0}
!3 = !{!"Simple C/C++ TBAA"}
!4 = !{!5, !5, i64 0}
!5 = !{!"any pointer", !2, i64 0}
!6 = distinct !{!6, !7, !8}
!7 = !{!"llvm.loop.vectorize.enable", i1 true}
!8 = !{!"llvm.loop.isvectorized", i32 1}
