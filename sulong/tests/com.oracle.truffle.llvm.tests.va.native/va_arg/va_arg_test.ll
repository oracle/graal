; ModuleID = 'va_arg_test.c'
source_filename = "va_arg_test.c"
target datalayout = "e-m:o-i64:64-f80:128-n8:16:32:64-S128"
target triple = "x86_64-apple-macosx10.14.0"

%struct.__va_list_tag = type { i32, i32, i8*, i8* }

@.str = private unnamed_addr constant [25 x i8] c"Test int va_arg    : %d\0A\00", align 1
@.str.1 = private unnamed_addr constant [25 x i8] c"Test double va_arg : %f\0A\00", align 1

; Function Attrs: nounwind ssp uwtable
define double @testVaArgDouble(i32, ...) local_unnamed_addr #0 !dbg !8 {
  %2 = alloca [1 x %struct.__va_list_tag], align 16
  call void @llvm.dbg.value(metadata i32 %0, metadata !14, metadata !DIExpression()), !dbg !36
  call void @llvm.dbg.value(metadata double 0.000000e+00, metadata !15, metadata !DIExpression()), !dbg !36
  %3 = bitcast [1 x %struct.__va_list_tag]* %2 to i8*, !dbg !37
  call void @llvm.lifetime.start.p0i8(i64 24, i8* nonnull %3) #3, !dbg !37
  call void @llvm.dbg.declare(metadata [1 x %struct.__va_list_tag]* %2, metadata !16, metadata !DIExpression()), !dbg !38
  %4 = getelementptr inbounds [1 x %struct.__va_list_tag], [1 x %struct.__va_list_tag]* %2, i64 0, i64 0, !dbg !39
  call void @llvm.va_start(i8* nonnull %3), !dbg !39
  call void @llvm.dbg.value(metadata i32 0, metadata !31, metadata !DIExpression()), !dbg !40
  call void @llvm.dbg.value(metadata double 0.000000e+00, metadata !15, metadata !DIExpression()), !dbg !36
  %5 = icmp sgt i32 %0, 0, !dbg !41
  br i1 %5, label %8, label %6, !dbg !42

6:                                                ; preds = %8, %1
  %7 = phi double [ 0.000000e+00, %1 ], [ %12, %8 ], !dbg !36
  call void @llvm.dbg.value(metadata double %7, metadata !15, metadata !DIExpression()), !dbg !36
  call void @llvm.va_end(i8* nonnull %3), !dbg !43
  call void @llvm.lifetime.end.p0i8(i64 24, i8* nonnull %3) #3, !dbg !44
  ret double %7, !dbg !45

8:                                                ; preds = %1, %8
  %9 = phi double [ %12, %8 ], [ 0.000000e+00, %1 ]
  %10 = phi i32 [ %13, %8 ], [ 0, %1 ]
  call void @llvm.dbg.value(metadata double %9, metadata !15, metadata !DIExpression()), !dbg !36
  call void @llvm.dbg.value(metadata i32 %10, metadata !31, metadata !DIExpression()), !dbg !40
  %11 = va_arg %struct.__va_list_tag* %4, double, !dbg !46
  call void @llvm.dbg.value(metadata double %11, metadata !33, metadata !DIExpression()), !dbg !47
  %12 = fadd double %9, %11, !dbg !48
  %13 = add nuw nsw i32 %10, 1, !dbg !49
  call void @llvm.dbg.value(metadata double %12, metadata !15, metadata !DIExpression()), !dbg !36
  call void @llvm.dbg.value(metadata i32 %13, metadata !31, metadata !DIExpression()), !dbg !40
  %14 = icmp eq i32 %13, %0, !dbg !41
  br i1 %14, label %6, label %8, !dbg !42, !llvm.loop !50
}

; Function Attrs: nounwind readnone speculatable
declare void @llvm.dbg.declare(metadata, metadata, metadata) #1

; Function Attrs: argmemonly nounwind
declare void @llvm.lifetime.start.p0i8(i64 immarg, i8* nocapture) #2

; Function Attrs: nounwind
declare void @llvm.va_start(i8*) #3

declare double @va_argDouble(%struct.__va_list_tag*) local_unnamed_addr #4

; Function Attrs: argmemonly nounwind
declare void @llvm.lifetime.end.p0i8(i64 immarg, i8* nocapture) #2

; Function Attrs: nounwind
declare void @llvm.va_end(i8*) #3

; Function Attrs: nounwind ssp uwtable
define i32 @testVaArgInt(i32, ...) local_unnamed_addr #0 !dbg !52 {
  %2 = alloca [1 x %struct.__va_list_tag], align 16
  call void @llvm.dbg.value(metadata i32 %0, metadata !56, metadata !DIExpression()), !dbg !64
  call void @llvm.dbg.value(metadata i32 0, metadata !57, metadata !DIExpression()), !dbg !64
  %3 = bitcast [1 x %struct.__va_list_tag]* %2 to i8*, !dbg !65
  call void @llvm.lifetime.start.p0i8(i64 24, i8* nonnull %3) #3, !dbg !65
  call void @llvm.dbg.declare(metadata [1 x %struct.__va_list_tag]* %2, metadata !58, metadata !DIExpression()), !dbg !66
  %4 = getelementptr inbounds [1 x %struct.__va_list_tag], [1 x %struct.__va_list_tag]* %2, i64 0, i64 0, !dbg !67
  call void @llvm.va_start(i8* nonnull %3), !dbg !67
  call void @llvm.dbg.value(metadata i32 0, metadata !59, metadata !DIExpression()), !dbg !68
  call void @llvm.dbg.value(metadata i32 0, metadata !57, metadata !DIExpression()), !dbg !64
  %5 = icmp sgt i32 %0, 0, !dbg !69
  br i1 %5, label %10, label %8, !dbg !70

6:                                                ; preds = %10
  %7 = trunc i64 %17 to i32, !dbg !71
  call void @llvm.dbg.value(metadata i32 %7, metadata !57, metadata !DIExpression()), !dbg !64
  br label %8, !dbg !72

8:                                                ; preds = %6, %1
  %9 = phi i32 [ 0, %1 ], [ %7, %6 ], !dbg !64
  call void @llvm.dbg.value(metadata i32 %9, metadata !57, metadata !DIExpression()), !dbg !64
  call void @llvm.va_end(i8* nonnull %3), !dbg !72
  call void @llvm.lifetime.end.p0i8(i64 24, i8* nonnull %3) #3, !dbg !73
  ret i32 %9, !dbg !74

10:                                               ; preds = %1, %10
  %11 = phi i64 [ %17, %10 ], [ 0, %1 ]
  %12 = phi i32 [ %18, %10 ], [ 0, %1 ]
  call void @llvm.dbg.value(metadata i32 %12, metadata !59, metadata !DIExpression()), !dbg !68
  %13 = va_arg %struct.__va_list_tag* %4, i32, !dbg !75
  %14 = sext i32 %13 to i64, !dbg !75
  %15 = shl i64 %11, 32, !dbg !71
  %16 = ashr exact i64 %15, 32, !dbg !71
  %17 = add nsw i64 %16, %14, !dbg !71
  %18 = add nuw nsw i32 %12, 1, !dbg !76
  call void @llvm.dbg.value(metadata i32 undef, metadata !57, metadata !DIExpression()), !dbg !64
  call void @llvm.dbg.value(metadata i32 %18, metadata !59, metadata !DIExpression()), !dbg !68
  %19 = icmp eq i32 %18, %0, !dbg !69
  br i1 %19, label %6, label %10, !dbg !70, !llvm.loop !77
}

declare i32 @va_argInt(%struct.__va_list_tag*) local_unnamed_addr #4

; Function Attrs: nounwind ssp uwtable
define i32 @main() local_unnamed_addr #0 !dbg !79 {
  %1 = tail call i32 (i32, ...) @testVaArgInt(i32 8, double 1.000000e+00, i32 2, double 3.000000e+00, i32 4, double 5.000000e+00, i32 6, double 7.000000e+00, i32 8, double 9.000000e+00, i32 10, double 1.100000e+01, i32 12, double 1.300000e+01, i32 14, double 1.500000e+01, i32 16), !dbg !82
  %2 = tail call i32 (i8*, ...) @printf(i8* getelementptr inbounds ([25 x i8], [25 x i8]* @.str, i64 0, i64 0), i32 %1), !dbg !83
  %3 = tail call double (i32, ...) @testVaArgDouble(i32 8, double 1.000000e+00, i32 2, double 3.000000e+00, i32 4, double 5.000000e+00, i32 6, double 7.000000e+00, i32 8, double 9.000000e+00, i32 10, double 1.100000e+01, i32 12, double 1.300000e+01, i32 14, double 1.500000e+01, i32 16), !dbg !84
  %4 = tail call i32 (i8*, ...) @printf(i8* getelementptr inbounds ([25 x i8], [25 x i8]* @.str.1, i64 0, i64 0), double %3), !dbg !85
  ret i32 0, !dbg !86
}

; Function Attrs: nofree nounwind
declare i32 @printf(i8* nocapture readonly, ...) local_unnamed_addr #5

; Function Attrs: nounwind readnone speculatable
declare void @llvm.dbg.value(metadata, metadata, metadata) #1

attributes #0 = { nounwind ssp uwtable "correctly-rounded-divide-sqrt-fp-math"="false" "disable-tail-calls"="false" "less-precise-fpmad"="false" "min-legal-vector-width"="0" "no-frame-pointer-elim"="true" "no-frame-pointer-elim-non-leaf" "no-infs-fp-math"="false" "no-jump-tables"="false" "no-nans-fp-math"="false" "no-signed-zeros-fp-math"="false" "no-trapping-math"="false" "stack-protector-buffer-size"="8" "target-cpu"="penryn" "target-features"="+cx16,+cx8,+fxsr,+mmx,+sahf,+sse,+sse2,+sse3,+sse4.1,+ssse3,+x87" "unsafe-fp-math"="false" "use-soft-float"="false" }
attributes #1 = { nounwind readnone speculatable }
attributes #2 = { argmemonly nounwind }
attributes #3 = { nounwind }
attributes #4 = { "correctly-rounded-divide-sqrt-fp-math"="false" "disable-tail-calls"="false" "less-precise-fpmad"="false" "no-frame-pointer-elim"="true" "no-frame-pointer-elim-non-leaf" "no-infs-fp-math"="false" "no-nans-fp-math"="false" "no-signed-zeros-fp-math"="false" "no-trapping-math"="false" "stack-protector-buffer-size"="8" "target-cpu"="penryn" "target-features"="+cx16,+cx8,+fxsr,+mmx,+sahf,+sse,+sse2,+sse3,+sse4.1,+ssse3,+x87" "unsafe-fp-math"="false" "use-soft-float"="false" }
attributes #5 = { nofree nounwind "correctly-rounded-divide-sqrt-fp-math"="false" "disable-tail-calls"="false" "less-precise-fpmad"="false" "no-frame-pointer-elim"="true" "no-frame-pointer-elim-non-leaf" "no-infs-fp-math"="false" "no-nans-fp-math"="false" "no-signed-zeros-fp-math"="false" "no-trapping-math"="false" "stack-protector-buffer-size"="8" "target-cpu"="penryn" "target-features"="+cx16,+cx8,+fxsr,+mmx,+sahf,+sse,+sse2,+sse3,+sse4.1,+ssse3,+x87" "unsafe-fp-math"="false" "use-soft-float"="false" }

!llvm.dbg.cu = !{!0}
!llvm.module.flags = !{!3, !4, !5, !6}
!llvm.ident = !{!7}

!0 = distinct !DICompileUnit(language: DW_LANG_C99, file: !1, producer: "clang version 9.0.0 (GraalVM.org llvmorg-9.0.0-5-g80b1d876fd-bgb66b241662 80b1d876fd4296b48433de5b66eaebe551897508)", isOptimized: true, runtimeVersion: 0, emissionKind: FullDebug, enums: !2, nameTableKind: GNU)
!1 = !DIFile(filename: "va_arg_test.c", directory: "/Users/zslajchrt/work/graaldev/graal/sulong/tests/com.oracle.truffle.llvm.tests.va.native/src")
!2 = !{}
!3 = !{i32 2, !"Dwarf Version", i32 4}
!4 = !{i32 2, !"Debug Info Version", i32 3}
!5 = !{i32 1, !"wchar_size", i32 4}
!6 = !{i32 7, !"PIC Level", i32 2}
!7 = !{!"clang version 9.0.0 (GraalVM.org llvmorg-9.0.0-5-g80b1d876fd-bgb66b241662 80b1d876fd4296b48433de5b66eaebe551897508)"}
!8 = distinct !DISubprogram(name: "testVaArgDouble", scope: !1, file: !1, line: 8, type: !9, scopeLine: 8, flags: DIFlagPrototyped | DIFlagAllCallsDescribed, spFlags: DISPFlagDefinition | DISPFlagOptimized, unit: !0, retainedNodes: !13)
!9 = !DISubroutineType(types: !10)
!10 = !{!11, !12, null}
!11 = !DIBasicType(name: "double", size: 64, encoding: DW_ATE_float)
!12 = !DIBasicType(name: "int", size: 32, encoding: DW_ATE_signed)
!13 = !{!14, !15, !16, !31, !33}
!14 = !DILocalVariable(name: "count", arg: 1, scope: !8, file: !1, line: 8, type: !12)
!15 = !DILocalVariable(name: "sum", scope: !8, file: !1, line: 9, type: !11)
!16 = !DILocalVariable(name: "args", scope: !8, file: !1, line: 10, type: !17)
!17 = !DIDerivedType(tag: DW_TAG_typedef, name: "va_list", file: !18, line: 14, baseType: !19)
!18 = !DIFile(filename: "sdk/mxbuild/darwin-amd64/LLVM_TOOLCHAIN/lib/clang/9.0.0/include/stdarg.h", directory: "/Users/zslajchrt/work/graaldev/graal")
!19 = !DIDerivedType(tag: DW_TAG_typedef, name: "__builtin_va_list", file: !1, line: 10, baseType: !20)
!20 = !DICompositeType(tag: DW_TAG_array_type, baseType: !21, size: 192, elements: !29)
!21 = distinct !DICompositeType(tag: DW_TAG_structure_type, name: "__va_list_tag", file: !1, line: 10, size: 192, elements: !22)
!22 = !{!23, !25, !26, !28}
!23 = !DIDerivedType(tag: DW_TAG_member, name: "gp_offset", scope: !21, file: !1, line: 10, baseType: !24, size: 32)
!24 = !DIBasicType(name: "unsigned int", size: 32, encoding: DW_ATE_unsigned)
!25 = !DIDerivedType(tag: DW_TAG_member, name: "fp_offset", scope: !21, file: !1, line: 10, baseType: !24, size: 32, offset: 32)
!26 = !DIDerivedType(tag: DW_TAG_member, name: "overflow_arg_area", scope: !21, file: !1, line: 10, baseType: !27, size: 64, offset: 64)
!27 = !DIDerivedType(tag: DW_TAG_pointer_type, baseType: null, size: 64)
!28 = !DIDerivedType(tag: DW_TAG_member, name: "reg_save_area", scope: !21, file: !1, line: 10, baseType: !27, size: 64, offset: 128)
!29 = !{!30}
!30 = !DISubrange(count: 1)
!31 = !DILocalVariable(name: "i", scope: !32, file: !1, line: 12, type: !12)
!32 = distinct !DILexicalBlock(scope: !8, file: !1, line: 12, column: 5)
!33 = !DILocalVariable(name: "num", scope: !34, file: !1, line: 13, type: !11)
!34 = distinct !DILexicalBlock(scope: !35, file: !1, line: 12, column: 37)
!35 = distinct !DILexicalBlock(scope: !32, file: !1, line: 12, column: 5)
!36 = !DILocation(line: 0, scope: !8)
!37 = !DILocation(line: 10, column: 5, scope: !8)
!38 = !DILocation(line: 10, column: 13, scope: !8)
!39 = !DILocation(line: 11, column: 5, scope: !8)
!40 = !DILocation(line: 0, scope: !32)
!41 = !DILocation(line: 12, column: 23, scope: !35)
!42 = !DILocation(line: 12, column: 5, scope: !32)
!43 = !DILocation(line: 16, column: 5, scope: !8)
!44 = !DILocation(line: 18, column: 1, scope: !8)
!45 = !DILocation(line: 17, column: 5, scope: !8)
!46 = !DILocation(line: 13, column: 22, scope: !34)
!47 = !DILocation(line: 0, scope: !34)
!48 = !DILocation(line: 14, column: 13, scope: !34)
!49 = !DILocation(line: 12, column: 32, scope: !35)
!50 = distinct !{!50, !42, !51}
!51 = !DILocation(line: 15, column: 5, scope: !32)
!52 = distinct !DISubprogram(name: "testVaArgInt", scope: !1, file: !1, line: 20, type: !53, scopeLine: 20, flags: DIFlagPrototyped | DIFlagAllCallsDescribed, spFlags: DISPFlagDefinition | DISPFlagOptimized, unit: !0, retainedNodes: !55)
!53 = !DISubroutineType(types: !54)
!54 = !{!12, !12, null}
!55 = !{!56, !57, !58, !59, !61}
!56 = !DILocalVariable(name: "count", arg: 1, scope: !52, file: !1, line: 20, type: !12)
!57 = !DILocalVariable(name: "sum", scope: !52, file: !1, line: 21, type: !12)
!58 = !DILocalVariable(name: "args", scope: !52, file: !1, line: 22, type: !17)
!59 = !DILocalVariable(name: "i", scope: !60, file: !1, line: 24, type: !12)
!60 = distinct !DILexicalBlock(scope: !52, file: !1, line: 24, column: 5)
!61 = !DILocalVariable(name: "num", scope: !62, file: !1, line: 25, type: !11)
!62 = distinct !DILexicalBlock(scope: !63, file: !1, line: 24, column: 37)
!63 = distinct !DILexicalBlock(scope: !60, file: !1, line: 24, column: 5)
!64 = !DILocation(line: 0, scope: !52)
!65 = !DILocation(line: 22, column: 5, scope: !52)
!66 = !DILocation(line: 22, column: 13, scope: !52)
!67 = !DILocation(line: 23, column: 5, scope: !52)
!68 = !DILocation(line: 0, scope: !60)
!69 = !DILocation(line: 24, column: 23, scope: !63)
!70 = !DILocation(line: 24, column: 5, scope: !60)
!71 = !DILocation(line: 26, column: 13, scope: !62)
!72 = !DILocation(line: 28, column: 5, scope: !52)
!73 = !DILocation(line: 30, column: 1, scope: !52)
!74 = !DILocation(line: 29, column: 5, scope: !52)
!75 = !DILocation(line: 25, column: 22, scope: !62)
!76 = !DILocation(line: 24, column: 32, scope: !63)
!77 = distinct !{!77, !70, !78}
!78 = !DILocation(line: 27, column: 5, scope: !60)
!79 = distinct !DISubprogram(name: "main", scope: !1, file: !1, line: 32, type: !80, scopeLine: 32, flags: DIFlagPrototyped | DIFlagAllCallsDescribed, spFlags: DISPFlagDefinition | DISPFlagOptimized, unit: !0, retainedNodes: !2)
!80 = !DISubroutineType(types: !81)
!81 = !{!12}
!82 = !DILocation(line: 33, column: 38, scope: !79)
!83 = !DILocation(line: 33, column: 2, scope: !79)
!84 = !DILocation(line: 34, column: 38, scope: !79)
!85 = !DILocation(line: 34, column: 2, scope: !79)
!86 = !DILocation(line: 35, column: 1, scope: !79)
