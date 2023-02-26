; ModuleID = 'small.c'
source_filename = "small.c"
target datalayout = "e-m:e-p270:32:32-p271:32:32-p272:64:64-i64:64-f80:128-n8:16:32:64-S128"
target triple = "x86_64-unknown-linux-gnu"

@c = dso_local local_unnamed_addr global i32 0, align 4
@b = dso_local local_unnamed_addr global i32 0, align 4
@a = dso_local global [1 x [1 x i32]] zeroinitializer, align 4

; Function Attrs: nofree norecurse nounwind uwtable
define dso_local i32 @main() local_unnamed_addr #0 {
  %1 = load i32, i32* @c, align 4, !tbaa !3
  %2 = icmp slt i32 %1, 1
  br i1 %2, label %3, label %15

3:                                                ; preds = %0
  %4 = load i32, i32* @b, align 4, !tbaa !3
  %5 = icmp eq i32 %4, 0
  %6 = sext i32 %4 to i64
  %7 = getelementptr inbounds [1 x [1 x i32]], [1 x [1 x i32]]* @a, i64 0, i64 %6, i64 2305843009213693952
  br label %8

8:                                                ; preds = %3, %11
  %9 = phi i32 [ %1, %3 ], [ %12, %11 ]
  br i1 %5, label %11, label %10

10:                                               ; preds = %8
  store volatile i32 100, i32* %7, align 4, !tbaa !3
  br label %11

11:                                               ; preds = %8, %10
  %12 = add i32 %9, 1
  %13 = icmp eq i32 %9, 0
  br i1 %13, label %14, label %8, !llvm.loop !7

14:                                               ; preds = %11
  store i32 1, i32* @c, align 4, !tbaa !3
  br label %15

15:                                               ; preds = %14, %0
  ret i32 0
}

attributes #0 = { nofree norecurse nounwind uwtable "disable-tail-calls"="false" "frame-pointer"="none" "less-precise-fpmad"="false" "min-legal-vector-width"="0" "no-infs-fp-math"="false" "no-jump-tables"="false" "no-nans-fp-math"="false" "no-signed-zeros-fp-math"="false" "no-trapping-math"="true" "stack-protector-buffer-size"="8" "target-cpu"="x86-64" "target-features"="+cx8,+fxsr,+mmx,+sse,+sse2,+x87" "tune-cpu"="generic" "unsafe-fp-math"="false" "use-soft-float"="false" }

!llvm.module.flags = !{!0, !1}
!llvm.ident = !{!2}

!0 = !{i32 7, !"Dwarf Version", i32 5}
!1 = !{i32 1, !"wchar_size", i32 4}
!2 = !{!"clang version 12.0.1 (GraalVM.org llvmorg-12.0.1-3-g6e0a5672bc-bgf11ed69a5a 6e0a5672bc058d882dce3d56f90b72b64a6870d7)"}
!3 = !{!4, !4, i64 0}
!4 = !{!"int", !5, i64 0}
!5 = !{!"omnipotent char", !6, i64 0}
!6 = !{!"Simple C/C++ TBAA"}
!7 = distinct !{!7, !8, !9}
!8 = !{!"llvm.loop.mustprogress"}
!9 = !{!"llvm.loop.unroll.disable"}
