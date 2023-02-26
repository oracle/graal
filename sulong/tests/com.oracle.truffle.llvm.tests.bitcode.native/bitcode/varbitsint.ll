; ModuleID = 'small.bc'
source_filename = "small.c"
target datalayout = "e-m:e-i64:64-f80:128-n8:16:32:64-S128"
target triple = "x86_64-unknown-linux-gnu"

%struct.a = type <{ i64, i32, i8 }>

@e = dso_local local_unnamed_addr global %struct.a zeroinitializer, align 8
@f = dso_local local_unnamed_addr global %struct.a zeroinitializer, align 8

; Function Attrs: norecurse nounwind readonly ssp uwtable willreturn
define dso_local { i64, i40 } @g() local_unnamed_addr #0 {
  %1 = load i64, i64* getelementptr inbounds (%struct.a, %struct.a* @e, i64 0, i32 0), align 8, !tbaa.struct !3
  %2 = load i40, i40* bitcast (i32* getelementptr inbounds (%struct.a, %struct.a* @e, i64 0, i32 1) to i40*), align 8, !tbaa.struct !11
  %3 = insertvalue { i64, i40 } undef, i64 %1, 0
  %4 = insertvalue { i64, i40 } %3, i40 %2, 1
  ret { i64, i40 } %4
}

; Function Attrs: nofree norecurse nounwind ssp uwtable willreturn
define dso_local i32 @main() local_unnamed_addr #1 {
  %1 = call { i64, i40 } @g()
  %2 = extractvalue { i64, i40 } %1, 0
  %3 = extractvalue { i64, i40 } %1, 1
  store i64 %2, i64* getelementptr inbounds (%struct.a, %struct.a* @f, i64 0, i32 0), align 8, !tbaa.struct !3
  store i40 %3, i40* bitcast (i32* getelementptr inbounds (%struct.a, %struct.a* @f, i64 0, i32 1) to i40*), align 8, !tbaa.struct !11
  ret i32 0
}

attributes #0 = { norecurse nounwind readonly ssp uwtable willreturn "disable-tail-calls"="false" "frame-pointer"="all" "less-precise-fpmad"="false" "min-legal-vector-width"="0" "no-infs-fp-math"="false" "no-jump-tables"="false" "no-nans-fp-math"="false" "no-signed-zeros-fp-math"="false" "no-trapping-math"="true" "stack-protector-buffer-size"="8" "target-cpu"="penryn" "target-features"="+cx16,+cx8,+fxsr,+mmx,+sahf,+sse,+sse2,+sse3,+sse4.1,+ssse3,+x87" "tune-cpu"="generic" "unsafe-fp-math"="false" "use-soft-float"="false" }
attributes #1 = { nofree norecurse nounwind ssp uwtable willreturn "disable-tail-calls"="false" "frame-pointer"="all" "less-precise-fpmad"="false" "min-legal-vector-width"="0" "no-infs-fp-math"="false" "no-jump-tables"="false" "no-nans-fp-math"="false" "no-signed-zeros-fp-math"="false" "no-trapping-math"="true" "stack-protector-buffer-size"="8" "target-cpu"="penryn" "target-features"="+cx16,+cx8,+fxsr,+mmx,+sahf,+sse,+sse2,+sse3,+sse4.1,+ssse3,+x87" "tune-cpu"="generic" "unsafe-fp-math"="false" "use-soft-float"="false" }

!llvm.module.flags = !{!0, !1}
!llvm.ident = !{!2}

!0 = !{i32 1, !"wchar_size", i32 4}
!1 = !{i32 7, !"PIC Level", i32 2}
!2 = !{!"clang version 12.0.1 (GraalVM.org llvmorg-12.0.1-3-g6e0a5672bc-bgf11ed69a5a 6e0a5672bc058d882dce3d56f90b72b64a6870d7)"}
!3 = !{i64 0, i64 8, !4, i64 8, i64 4, !8, i64 12, i64 1, !10}
!4 = !{!5, !5, i64 0}
!5 = !{!"long", !6, i64 0}
!6 = !{!"omnipotent char", !7, i64 0}
!7 = !{!"Simple C/C++ TBAA"}
!8 = !{!9, !9, i64 0}
!9 = !{!"int", !6, i64 0}
!10 = !{!6, !6, i64 0}
!11 = !{i64 0, i64 4, !8, i64 4, i64 1, !10}
