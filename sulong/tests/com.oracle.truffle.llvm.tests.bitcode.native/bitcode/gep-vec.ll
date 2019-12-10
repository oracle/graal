; ModuleID = 'gep-vec.bc'
target datalayout = "e-m:e-i64:64-f80:128-n8:16:32:64-S128"
target triple = "x86_64-unknown-linux-gnu"

; Function Attrs: norecurse nounwind uwtable
define void @doloop(float** nocapture, float*, i32* nocapture readonly, i32* nocapture readonly) local_unnamed_addr #0 {
  br label %5

; <label>:5:                                      ; preds = %4, %31
  %6 = phi i64 [ 0, %4 ], [ %32, %31 ]
  %7 = shl i64 %6, 2
  %8 = insertelement <4 x i64> undef, i64 %7, i32 0
  %9 = shufflevector <4 x i64> %8, <4 x i64> undef, <4 x i32> zeroinitializer
  br label %10

; <label>:10:                                     ; preds = %10, %5
  %11 = phi i64 [ 0, %5 ], [ %27, %10 ]
  %12 = phi <4 x i64> [ <i64 0, i64 1, i64 2, i64 3>, %5 ], [ %28, %10 ]
  %13 = add nuw nsw <4 x i64> %12, %9
  %14 = getelementptr inbounds float, float* %1, <4 x i64> %13
  %15 = extractelement <4 x i64> %13, i32 0
  %16 = getelementptr inbounds i32, i32* %2, i64 %15
  %17 = bitcast i32* %16 to <4 x i32>*
  %18 = load <4 x i32>, <4 x i32>* %17, align 4, !tbaa !2
  %19 = getelementptr inbounds i32, i32* %3, i64 %15
  %20 = bitcast i32* %19 to <4 x i32>*
  %21 = load <4 x i32>, <4 x i32>* %20, align 4, !tbaa !2
  %22 = mul nsw <4 x i32> %21, %18
  %23 = sext <4 x i32> %22 to <4 x i64>
  %24 = getelementptr inbounds float, <4 x float*> %14, <4 x i64> %23
  %25 = getelementptr inbounds float*, float** %0, i64 %15
  %26 = bitcast float** %25 to <4 x float*>*
  store <4 x float*> %24, <4 x float*>* %26, align 8, !tbaa !6
  %27 = add i64 %11, 4
  %28 = add <4 x i64> %12, <i64 4, i64 4, i64 4, i64 4>
  %29 = icmp eq i64 %11, 0
  br i1 %29, label %31, label %10, !llvm.loop !8

; <label>:30:                                     ; preds = %31
  ret void

; <label>:31:                                     ; preds = %10
  %32 = add nuw nsw i64 %6, 1
  %33 = icmp eq i64 %32, 128
  br i1 %33, label %30, label %5
}

; Function Attrs: argmemonly nounwind
declare void @llvm.lifetime.start.p0i8(i64, i8* nocapture) #1

; Function Attrs: argmemonly nounwind
declare void @llvm.lifetime.end.p0i8(i64, i8* nocapture) #1

; Function Attrs: norecurse nounwind uwtable writeonly
define void @init(i32* nocapture, i32* nocapture) local_unnamed_addr #2 {
  %3 = getelementptr i32, i32* %0, i64 512
  %4 = getelementptr i32, i32* %1, i64 512
  %5 = icmp ugt i32* %4, %0
  %6 = icmp ugt i32* %3, %1
  %7 = and i1 %5, %6
  br i1 %7, label %22, label %8

; <label>:8:                                      ; preds = %2, %8
  %9 = phi i64 [ %17, %8 ], [ 0, %2 ]
  %10 = phi <8 x i32> [ %18, %8 ], [ <i32 0, i32 1, i32 2, i32 3, i32 4, i32 5, i32 6, i32 7>, %2 ]
  %11 = phi <8 x i32> [ %19, %8 ], [ <i32 0, i32 1, i32 2, i32 3, i32 4, i32 5, i32 6, i32 7>, %2 ]
  %12 = getelementptr inbounds i32, i32* %0, i64 %9
  %13 = bitcast i32* %12 to <8 x i32>*
  store <8 x i32> %10, <8 x i32>* %13, align 4, !tbaa !2, !alias.scope !11, !noalias !14
  %14 = getelementptr inbounds i32, i32* %1, i64 %9
  %15 = add <8 x i32> %11, <i32 512, i32 512, i32 512, i32 512, i32 512, i32 512, i32 512, i32 512>
  %16 = bitcast i32* %14 to <8 x i32>*
  store <8 x i32> %15, <8 x i32>* %16, align 4, !tbaa !2, !alias.scope !14
  %17 = add i64 %9, 8
  %18 = add <8 x i32> %10, <i32 8, i32 8, i32 8, i32 8, i32 8, i32 8, i32 8, i32 8>
  %19 = add <8 x i32> %11, <i32 8, i32 8, i32 8, i32 8, i32 8, i32 8, i32 8, i32 8>
  %20 = icmp eq i64 %17, 512
  br i1 %20, label %21, label %8, !llvm.loop !16

; <label>:21:                                     ; preds = %8, %22
  ret void

; <label>:22:                                     ; preds = %2, %22
  %23 = phi i64 [ %29, %22 ], [ 0, %2 ]
  %24 = getelementptr inbounds i32, i32* %0, i64 %23
  %25 = trunc i64 %23 to i32
  store i32 %25, i32* %24, align 4, !tbaa !2
  %26 = getelementptr inbounds i32, i32* %1, i64 %23
  %27 = trunc i64 %23 to i32
  %28 = add i32 %27, 512
  store i32 %28, i32* %26, align 4, !tbaa !2
  %29 = add nuw nsw i64 %23, 1
  %30 = icmp eq i64 %29, 512
  br i1 %30, label %21, label %22, !llvm.loop !17
}

; Function Attrs: nounwind uwtable
define i32 @main() local_unnamed_addr #3 {
  %1 = alloca [512 x float*], align 16
  %2 = alloca [512 x float], align 16
  %3 = alloca [512 x i32], align 16
  %4 = alloca [512 x i32], align 16
  %5 = bitcast [512 x float*]* %1 to i8*
  call void @llvm.lifetime.start.p0i8(i64 4096, i8* nonnull %5) #4
  %6 = bitcast [512 x float]* %2 to i8*
  call void @llvm.lifetime.start.p0i8(i64 2048, i8* nonnull %6) #4
  %7 = bitcast [512 x i32]* %3 to i8*
  call void @llvm.lifetime.start.p0i8(i64 2048, i8* nonnull %7) #4
  call void @llvm.memset.p0i8.i64(i8* nonnull align 16 %7, i8 0, i64 2048, i1 false)
  %8 = bitcast [512 x i32]* %4 to i8*
  call void @llvm.lifetime.start.p0i8(i64 2048, i8* nonnull %8) #4
  call void @llvm.memset.p0i8.i64(i8* nonnull align 16 %8, i8 0, i64 2048, i1 false)
  %9 = getelementptr inbounds [512 x i32], [512 x i32]* %3, i64 0, i64 0
  %10 = getelementptr inbounds [512 x i32], [512 x i32]* %4, i64 0, i64 0
  call void @init(i32* nonnull %9, i32* nonnull %10)
  %11 = getelementptr inbounds [512 x float*], [512 x float*]* %1, i64 0, i64 0
  %12 = getelementptr inbounds [512 x float], [512 x float]* %2, i64 0, i64 0
  call void @doloop(float** nonnull %11, float* nonnull %12, i32* nonnull %9, i32* nonnull %10)
  br label %15

; <label>:13:                                     ; preds = %15
  %14 = icmp ult i64 %28, 512
  br i1 %14, label %15, label %29

; <label>:15:                                     ; preds = %0, %13
  %16 = phi i64 [ 0, %0 ], [ %28, %13 ]
  %17 = getelementptr inbounds [512 x float*], [512 x float*]* %1, i64 0, i64 %16
  %18 = load float*, float** %17, align 8, !tbaa !6
  %19 = getelementptr inbounds [512 x float], [512 x float]* %2, i64 0, i64 %16
  %20 = getelementptr inbounds [512 x i32], [512 x i32]* %3, i64 0, i64 %16
  %21 = load i32, i32* %20, align 4, !tbaa !2
  %22 = getelementptr inbounds [512 x i32], [512 x i32]* %4, i64 0, i64 %16
  %23 = load i32, i32* %22, align 4, !tbaa !2
  %24 = mul nsw i32 %23, %21
  %25 = sext i32 %24 to i64
  %26 = getelementptr inbounds float, float* %19, i64 %25
  %27 = icmp eq float* %18, %26
  %28 = add nuw nsw i64 %16, 1
  br i1 %27, label %13, label %29

; <label>:29:                                     ; preds = %13, %15
  %30 = phi i32 [ 1, %15 ], [ 0, %13 ]
  call void @llvm.lifetime.end.p0i8(i64 2048, i8* nonnull %8) #4
  call void @llvm.lifetime.end.p0i8(i64 2048, i8* nonnull %7) #4
  call void @llvm.lifetime.end.p0i8(i64 2048, i8* nonnull %6) #4
  call void @llvm.lifetime.end.p0i8(i64 4096, i8* nonnull %5) #4
  ret i32 %30
}

; Function Attrs: argmemonly nounwind
declare void @llvm.memset.p0i8.i64(i8* nocapture writeonly, i8, i64, i1) #1

attributes #0 = { norecurse nounwind uwtable "correctly-rounded-divide-sqrt-fp-math"="false" "disable-tail-calls"="false" "less-precise-fpmad"="false" "min-legal-vector-width"="0" "no-frame-pointer-elim"="false" "no-infs-fp-math"="false" "no-jump-tables"="false" "no-nans-fp-math"="false" "no-signed-zeros-fp-math"="false" "no-trapping-math"="false" "stack-protector-buffer-size"="8" "target-cpu"="x86-64" "target-features"="+avx,+avx2,+fxsr,+mmx,+popcnt,+sse,+sse2,+sse3,+sse4.1,+sse4.2,+ssse3,+x87,+xsave" "unsafe-fp-math"="false" "use-soft-float"="false" }
attributes #1 = { argmemonly nounwind }
attributes #2 = { norecurse nounwind uwtable writeonly "correctly-rounded-divide-sqrt-fp-math"="false" "disable-tail-calls"="false" "less-precise-fpmad"="false" "min-legal-vector-width"="0" "no-frame-pointer-elim"="false" "no-infs-fp-math"="false" "no-jump-tables"="false" "no-nans-fp-math"="false" "no-signed-zeros-fp-math"="false" "no-trapping-math"="false" "stack-protector-buffer-size"="8" "target-cpu"="x86-64" "target-features"="+avx,+avx2,+fxsr,+mmx,+popcnt,+sse,+sse2,+sse3,+sse4.1,+sse4.2,+ssse3,+x87,+xsave" "unsafe-fp-math"="false" "use-soft-float"="false" }
attributes #3 = { nounwind uwtable "correctly-rounded-divide-sqrt-fp-math"="false" "disable-tail-calls"="false" "less-precise-fpmad"="false" "min-legal-vector-width"="0" "no-frame-pointer-elim"="false" "no-infs-fp-math"="false" "no-jump-tables"="false" "no-nans-fp-math"="false" "no-signed-zeros-fp-math"="false" "no-trapping-math"="false" "stack-protector-buffer-size"="8" "target-cpu"="x86-64" "target-features"="+avx,+avx2,+fxsr,+mmx,+popcnt,+sse,+sse2,+sse3,+sse4.1,+sse4.2,+ssse3,+x87,+xsave" "unsafe-fp-math"="false" "use-soft-float"="false" }
attributes #4 = { nounwind }

!llvm.module.flags = !{!0}
!llvm.ident = !{!1}

!0 = !{i32 1, !"wchar_size", i32 4}
!1 = !{!"clang version 8.0.0 "}
!2 = !{!3, !3, i64 0}
!3 = !{!"int", !4, i64 0}
!4 = !{!"omnipotent char", !5, i64 0}
!5 = !{!"Simple C/C++ TBAA"}
!6 = !{!7, !7, i64 0}
!7 = !{!"any pointer", !4, i64 0}
!8 = distinct !{!8, !9, !10}
!9 = !{!"llvm.loop.vectorize.enable", i1 true}
!10 = !{!"llvm.loop.isvectorized", i32 1}
!11 = !{!12}
!12 = distinct !{!12, !13}
!13 = distinct !{!13, !"LVerDomain"}
!14 = !{!15}
!15 = distinct !{!15, !13}
!16 = distinct !{!16, !10}
!17 = distinct !{!17, !10}
