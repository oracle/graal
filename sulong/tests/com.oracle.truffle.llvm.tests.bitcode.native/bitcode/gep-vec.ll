; ModuleID = 'gep-vec.c'
source_filename = "gep-vec.c"
target datalayout = "e-m:e-p270:32:32-p271:32:32-p272:64:64-i64:64-f80:128-n8:16:32:64-S128"
target triple = "x86_64-unknown-linux-gnu"

; Function Attrs: argmemonly nofree norecurse nosync nounwind uwtable
define dso_local void @doloop(ptr nocapture noundef writeonly %0, ptr noundef %1, ptr nocapture noundef readonly %2, ptr nocapture noundef readonly %3) local_unnamed_addr #0 {
  br label %5

5:                                                ; preds = %4, %5
  %6 = phi i64 [ 0, %4 ], [ %24, %5 ]
  %7 = shl nsw i64 %6, 2
  %8 = or i64 %7, 1
  %9 = or i64 %7, 2
  %10 = or i64 %7, 3
  %11 = insertelement <4 x i64> poison, i64 %7, i64 0
  %12 = insertelement <4 x i64> %11, i64 %8, i64 1
  %13 = insertelement <4 x i64> %12, i64 %9, i64 2
  %14 = insertelement <4 x i64> %13, i64 %10, i64 3
  %15 = getelementptr inbounds float, ptr %1, <4 x i64> %14
  %16 = getelementptr inbounds i32, ptr %2, i64 %7
  %17 = load <4 x i32>, ptr %16, align 4, !tbaa !5
  %18 = getelementptr inbounds i32, ptr %3, i64 %7
  %19 = load <4 x i32>, ptr %18, align 4, !tbaa !5
  %20 = mul nsw <4 x i32> %19, %17
  %21 = sext <4 x i32> %20 to <4 x i64>
  %22 = getelementptr inbounds float, <4 x ptr> %15, <4 x i64> %21
  %23 = getelementptr inbounds ptr, ptr %0, i64 %7
  store <4 x ptr> %22, ptr %23, align 8, !tbaa !9
  %24 = add nuw nsw i64 %6, 1
  %25 = icmp eq i64 %24, 128
  br i1 %25, label %26, label %5, !llvm.loop !11

26:                                               ; preds = %5
  ret void
}

attributes #0 = { argmemonly nofree norecurse nosync nounwind uwtable "frame-pointer"="none" "min-legal-vector-width"="0" "no-trapping-math"="true" "stack-protector-buffer-size"="8" "target-cpu"="x86-64" "target-features"="+avx,+avx2,+crc32,+cx8,+fxsr,+mmx,+popcnt,+sse,+sse2,+sse3,+sse4.1,+sse4.2,+ssse3,+x87,+xsave" "tune-cpu"="generic" }

!llvm.module.flags = !{!0, !1, !2, !3}
!llvm.ident = !{!4}

!0 = !{i32 1, !"wchar_size", i32 4}
!1 = !{i32 7, !"PIC Level", i32 2}
!2 = !{i32 7, !"PIE Level", i32 2}
!3 = !{i32 7, !"uwtable", i32 2}
!4 = !{!"clang version 15.0.5 (GraalVM.org llvmorg-15.0.5-4-g812784cd79-bg8671e93c04 812784cd79a6336349de8a2a4b9ab9ccf6fb0eb1)"}
!5 = !{!6, !6, i64 0}
!6 = !{!"int", !7, i64 0}
!7 = !{!"omnipotent char", !8, i64 0}
!8 = !{!"Simple C/C++ TBAA"}
!9 = !{!10, !10, i64 0}
!10 = !{!"any pointer", !7, i64 0}
!11 = distinct !{!11, !12, !13}
!12 = !{!"llvm.loop.mustprogress"}
!13 = !{!"llvm.loop.unroll.disable"}
