; ModuleID = 'va_arg_test.c'
source_filename = "va_arg_test.c"
target datalayout = "e-m:o-p270:32:32-p271:32:32-p272:64:64-i64:64-f80:128-n8:16:32:64-S128"
target triple = "x86_64-apple-macosx10.14.0"

%struct.__va_list_tag = type { i32, i32, i8*, i8* }

@.str = private unnamed_addr constant [25 x i8] c"Test int va_arg    : %d\0A\00", align 1
@.str.1 = private unnamed_addr constant [25 x i8] c"Test double va_arg : %f\0A\00", align 1

; Function Attrs: nounwind ssp uwtable
define double @testVaArgDouble(i32 %0, ...) local_unnamed_addr #0 !dbg !29 {
  %2 = alloca [1 x %struct.__va_list_tag], align 16
  call void @llvm.dbg.value(metadata i32 %0, metadata !33, metadata !DIExpression()), !dbg !44
  call void @llvm.dbg.value(metadata double 0.000000e+00, metadata !34, metadata !DIExpression()), !dbg !44
  %3 = bitcast [1 x %struct.__va_list_tag]* %2 to i8*, !dbg !45
  call void @llvm.lifetime.start.p0i8(i64 24, i8* nonnull %3) #3, !dbg !45
  call void @llvm.dbg.declare(metadata [1 x %struct.__va_list_tag]* %2, metadata !35, metadata !DIExpression()), !dbg !46
  call void @llvm.va_start(i8* %3), !dbg !47
  call void @llvm.dbg.value(metadata i32 0, metadata !39, metadata !DIExpression()), !dbg !48
  call void @llvm.dbg.value(metadata double 0.000000e+00, metadata !34, metadata !DIExpression()), !dbg !44
  %4 = icmp sgt i32 %0, 0, !dbg !49
  br i1 %4, label %7, label %5, !dbg !50

5:                                                ; preds = %7, %1
  %6 = phi double [ 0.000000e+00, %1 ], [ %11, %7 ], !dbg !44
  call void @llvm.dbg.value(metadata double %6, metadata !34, metadata !DIExpression()), !dbg !44
  call void @llvm.va_end(i8* nonnull %3), !dbg !51
  call void @llvm.lifetime.end.p0i8(i64 24, i8* nonnull %3) #3, !dbg !52
  ret double %6, !dbg !53

7:                                                ; preds = %1, %7
  %8 = phi double [ %11, %7 ], [ 0.000000e+00, %1 ]
  %9 = phi i32 [ %12, %7 ], [ 0, %1 ]
  call void @llvm.dbg.value(metadata double %8, metadata !34, metadata !DIExpression()), !dbg !44
  call void @llvm.dbg.value(metadata i32 %9, metadata !39, metadata !DIExpression()), !dbg !48
  ;%10 = call double @va_argDouble([1 x %struct.__va_list_tag]* nonnull %2) #3, !dbg !54
  %10 = va_arg [1 x %struct.__va_list_tag]* %2, double, !dbg !54
  call void @llvm.dbg.value(metadata double %10, metadata !41, metadata !DIExpression()), !dbg !55
  %11 = fadd double %8, %10, !dbg !56
  call void @llvm.dbg.value(metadata double %11, metadata !34, metadata !DIExpression()), !dbg !44
  %12 = add nuw nsw i32 %9, 1, !dbg !57
  call void @llvm.dbg.value(metadata i32 %12, metadata !39, metadata !DIExpression()), !dbg !48
  %13 = icmp eq i32 %12, %0, !dbg !49
  br i1 %13, label %5, label %7, !dbg !50, !llvm.loop !58
}

; Function Attrs: nounwind readnone speculatable willreturn
declare void @llvm.dbg.declare(metadata, metadata, metadata) #1

; Function Attrs: argmemonly nounwind willreturn
declare void @llvm.lifetime.start.p0i8(i64 immarg, i8* nocapture) #2

; Function Attrs: nounwind
declare void @llvm.va_start(i8*) #3

declare !dbg !4 double @va_argDouble([1 x %struct.__va_list_tag]*) local_unnamed_addr #4

; Function Attrs: argmemonly nounwind willreturn
declare void @llvm.lifetime.end.p0i8(i64 immarg, i8* nocapture) #2

; Function Attrs: nounwind
declare void @llvm.va_end(i8*) #3

; Function Attrs: nounwind ssp uwtable
define i32 @testVaArgInt(i32 %0, ...) local_unnamed_addr #0 !dbg !60 {
  %2 = alloca [1 x %struct.__va_list_tag], align 16
  call void @llvm.dbg.value(metadata i32 %0, metadata !64, metadata !DIExpression()), !dbg !72
  call void @llvm.dbg.value(metadata i32 0, metadata !65, metadata !DIExpression()), !dbg !72
  %3 = bitcast [1 x %struct.__va_list_tag]* %2 to i8*, !dbg !73
  call void @llvm.lifetime.start.p0i8(i64 24, i8* nonnull %3) #3, !dbg !73
  call void @llvm.dbg.declare(metadata [1 x %struct.__va_list_tag]* %2, metadata !66, metadata !DIExpression()), !dbg !74
  call void @llvm.va_start(i8* %3), !dbg !75
  call void @llvm.dbg.value(metadata i32 0, metadata !67, metadata !DIExpression()), !dbg !76
  call void @llvm.dbg.value(metadata i32 0, metadata !65, metadata !DIExpression()), !dbg !72
  %4 = icmp sgt i32 %0, 0, !dbg !77
  br i1 %4, label %9, label %7, !dbg !78

5:                                                ; preds = %9
  %6 = trunc i64 %16 to i32, !dbg !79
  call void @llvm.dbg.value(metadata i32 %6, metadata !65, metadata !DIExpression()), !dbg !72
  br label %7, !dbg !80

7:                                                ; preds = %5, %1
  %8 = phi i32 [ 0, %1 ], [ %6, %5 ], !dbg !72
  call void @llvm.dbg.value(metadata i32 %8, metadata !65, metadata !DIExpression()), !dbg !72
  call void @llvm.va_end(i8* nonnull %3), !dbg !80
  call void @llvm.lifetime.end.p0i8(i64 24, i8* nonnull %3) #3, !dbg !81
  ret i32 %8, !dbg !82

9:                                                ; preds = %1, %9
  %10 = phi i64 [ %16, %9 ], [ 0, %1 ]
  %11 = phi i32 [ %17, %9 ], [ 0, %1 ]
  call void @llvm.dbg.value(metadata i32 undef, metadata !65, metadata !DIExpression()), !dbg !72
  call void @llvm.dbg.value(metadata i32 %11, metadata !67, metadata !DIExpression()), !dbg !76
  ;%12 = call i32 @va_argInt([1 x %struct.__va_list_tag]* nonnull %2) #3, !dbg !83
  %12 = va_arg [1 x %struct.__va_list_tag]* %2, i32, !dbg !83
  %13 = sext i32 %12 to i64, !dbg !83
  %14 = shl i64 %10, 32, !dbg !79
  %15 = ashr exact i64 %14, 32, !dbg !79
  %16 = add nsw i64 %15, %13, !dbg !79
  call void @llvm.dbg.value(metadata i64 %16, metadata !65, metadata !DIExpression(DW_OP_LLVM_convert, 64, DW_ATE_unsigned, DW_OP_LLVM_convert, 32, DW_ATE_unsigned, DW_OP_stack_value)), !dbg !72
  %17 = add nuw nsw i32 %11, 1, !dbg !84
  call void @llvm.dbg.value(metadata i32 %17, metadata !67, metadata !DIExpression()), !dbg !76
  %18 = icmp eq i32 %17, %0, !dbg !77
  br i1 %18, label %5, label %9, !dbg !78, !llvm.loop !85
}

declare !dbg !20 i32 @va_argInt([1 x %struct.__va_list_tag]*) local_unnamed_addr #4

; Function Attrs: nounwind ssp uwtable
define i32 @main() local_unnamed_addr #0 !dbg !87 {
  %1 = call i32 (i32, ...) @testVaArgInt(i32 8, double 1.000000e+00, i32 2, double 3.000000e+00, i32 4, double 5.000000e+00, i32 6, double 7.000000e+00, i32 8, double 9.000000e+00, i32 10, double 1.100000e+01, i32 12, double 1.300000e+01, i32 14, double 1.500000e+01, i32 16), !dbg !90
  %2 = call i32 (i8*, ...) @printf(i8* nonnull dereferenceable(1) getelementptr inbounds ([25 x i8], [25 x i8]* @.str, i64 0, i64 0), i32 %1), !dbg !91
  %3 = call double (i32, ...) @testVaArgDouble(i32 8, double 1.000000e+00, i32 2, double 3.000000e+00, i32 4, double 5.000000e+00, i32 6, double 7.000000e+00, i32 8, double 9.000000e+00, i32 10, double 1.100000e+01, i32 12, double 1.300000e+01, i32 14, double 1.500000e+01, i32 16), !dbg !92
  %4 = call i32 (i8*, ...) @printf(i8* nonnull dereferenceable(1) getelementptr inbounds ([25 x i8], [25 x i8]* @.str.1, i64 0, i64 0), double %3), !dbg !93
  ret i32 0, !dbg !94
}

; Function Attrs: nofree nounwind
declare i32 @printf(i8* nocapture readonly, ...) local_unnamed_addr #5

; Function Attrs: nounwind readnone speculatable willreturn
declare void @llvm.dbg.value(metadata, metadata, metadata) #1

attributes #0 = { nounwind ssp uwtable "correctly-rounded-divide-sqrt-fp-math"="false" "disable-tail-calls"="false" "frame-pointer"="all" "less-precise-fpmad"="false" "min-legal-vector-width"="0" "no-infs-fp-math"="false" "no-jump-tables"="false" "no-nans-fp-math"="false" "no-signed-zeros-fp-math"="false" "no-trapping-math"="false" "stack-protector-buffer-size"="8" "target-cpu"="penryn" "target-features"="+cx16,+cx8,+fxsr,+mmx,+sahf,+sse,+sse2,+sse3,+sse4.1,+ssse3,+x87" "unsafe-fp-math"="false" "use-soft-float"="false" }
attributes #1 = { nounwind readnone speculatable willreturn }
attributes #2 = { argmemonly nounwind willreturn }
attributes #3 = { nounwind }
attributes #4 = { "correctly-rounded-divide-sqrt-fp-math"="false" "disable-tail-calls"="false" "frame-pointer"="all" "less-precise-fpmad"="false" "no-infs-fp-math"="false" "no-nans-fp-math"="false" "no-signed-zeros-fp-math"="false" "no-trapping-math"="false" "stack-protector-buffer-size"="8" "target-cpu"="penryn" "target-features"="+cx16,+cx8,+fxsr,+mmx,+sahf,+sse,+sse2,+sse3,+sse4.1,+ssse3,+x87" "unsafe-fp-math"="false" "use-soft-float"="false" }
attributes #5 = { nofree nounwind "correctly-rounded-divide-sqrt-fp-math"="false" "disable-tail-calls"="false" "frame-pointer"="all" "less-precise-fpmad"="false" "no-infs-fp-math"="false" "no-nans-fp-math"="false" "no-signed-zeros-fp-math"="false" "no-trapping-math"="false" "stack-protector-buffer-size"="8" "target-cpu"="penryn" "target-features"="+cx16,+cx8,+fxsr,+mmx,+sahf,+sse,+sse2,+sse3,+sse4.1,+ssse3,+x87" "unsafe-fp-math"="false" "use-soft-float"="false" }

!llvm.dbg.cu = !{!0}
!llvm.module.flags = !{!24, !25, !26, !27}
!llvm.ident = !{!28}

!0 = distinct !DICompileUnit(language: DW_LANG_C99, file: !1, producer: "clang version 10.0.0 (GraalVM.org llvmorg-10.0.0-4-g22d2637565-bg83994d0b4b 22d26375659ee388e18a96bf6b34e56299f75efc)", isOptimized: true, runtimeVersion: 0, emissionKind: FullDebug, enums: !2, retainedTypes: !3, nameTableKind: None)
!1 = !DIFile(filename: "va_arg_test.c", directory: "/Users/zslajchrt/work/graaldev/graal/sulong/tests/com.oracle.truffle.llvm.tests.va.native/va_arg", checksumkind: CSK_MD5, checksum: "778392f10ba81717f6b292e9c3ffc9e3")
!2 = !{}
!3 = !{!4, !20}
!4 = !DISubprogram(name: "va_argDouble", scope: !1, file: !1, line: 34, type: !5, flags: DIFlagPrototyped, spFlags: DISPFlagOptimized, retainedNodes: !2)
!5 = !DISubroutineType(types: !6)
!6 = !{!7, !8}
!7 = !DIBasicType(name: "double", size: 64, encoding: DW_ATE_float)
!8 = !DIDerivedType(tag: DW_TAG_pointer_type, baseType: !9, size: 64)
!9 = !DICompositeType(tag: DW_TAG_array_type, baseType: !10, size: 192, elements: !18)
!10 = distinct !DICompositeType(tag: DW_TAG_structure_type, name: "__va_list_tag", file: !1, line: 39, size: 192, elements: !11)
!11 = !{!12, !14, !15, !17}
!12 = !DIDerivedType(tag: DW_TAG_member, name: "gp_offset", scope: !10, file: !1, line: 39, baseType: !13, size: 32)
!13 = !DIBasicType(name: "unsigned int", size: 32, encoding: DW_ATE_unsigned)
!14 = !DIDerivedType(tag: DW_TAG_member, name: "fp_offset", scope: !10, file: !1, line: 39, baseType: !13, size: 32, offset: 32)
!15 = !DIDerivedType(tag: DW_TAG_member, name: "overflow_arg_area", scope: !10, file: !1, line: 39, baseType: !16, size: 64, offset: 64)
!16 = !DIDerivedType(tag: DW_TAG_pointer_type, baseType: null, size: 64)
!17 = !DIDerivedType(tag: DW_TAG_member, name: "reg_save_area", scope: !10, file: !1, line: 39, baseType: !16, size: 64, offset: 128)
!18 = !{!19}
!19 = !DISubrange(count: 1)
!20 = !DISubprogram(name: "va_argInt", scope: !1, file: !1, line: 35, type: !21, flags: DIFlagPrototyped, spFlags: DISPFlagOptimized, retainedNodes: !2)
!21 = !DISubroutineType(types: !22)
!22 = !{!23, !8}
!23 = !DIBasicType(name: "int", size: 32, encoding: DW_ATE_signed)
!24 = !{i32 7, !"Dwarf Version", i32 5}
!25 = !{i32 2, !"Debug Info Version", i32 3}
!26 = !{i32 1, !"wchar_size", i32 4}
!27 = !{i32 7, !"PIC Level", i32 2}
!28 = !{!"clang version 10.0.0 (GraalVM.org llvmorg-10.0.0-4-g22d2637565-bg83994d0b4b 22d26375659ee388e18a96bf6b34e56299f75efc)"}
!29 = distinct !DISubprogram(name: "testVaArgDouble", scope: !1, file: !1, line: 37, type: !30, scopeLine: 37, flags: DIFlagPrototyped | DIFlagAllCallsDescribed, spFlags: DISPFlagDefinition | DISPFlagOptimized, unit: !0, retainedNodes: !32)
!30 = !DISubroutineType(types: !31)
!31 = !{!7, !23, null}
!32 = !{!33, !34, !35, !39, !41}
!33 = !DILocalVariable(name: "count", arg: 1, scope: !29, file: !1, line: 37, type: !23)
!34 = !DILocalVariable(name: "sum", scope: !29, file: !1, line: 38, type: !7)
!35 = !DILocalVariable(name: "args", scope: !29, file: !1, line: 39, type: !36)
!36 = !DIDerivedType(tag: DW_TAG_typedef, name: "va_list", file: !37, line: 14, baseType: !38)
!37 = !DIFile(filename: "sdk/mxbuild/darwin-amd64/LLVM_TOOLCHAIN/lib/clang/10.0.0/include/stdarg.h", directory: "/Users/zslajchrt/work/graaldev/graal", checksumkind: CSK_MD5, checksum: "4de3cbd931b589d291e5c39387aecf82")
!38 = !DIDerivedType(tag: DW_TAG_typedef, name: "__builtin_va_list", file: !1, line: 39, baseType: !9)
!39 = !DILocalVariable(name: "i", scope: !40, file: !1, line: 41, type: !23)
!40 = distinct !DILexicalBlock(scope: !29, file: !1, line: 41, column: 5)
!41 = !DILocalVariable(name: "num", scope: !42, file: !1, line: 42, type: !7)
!42 = distinct !DILexicalBlock(scope: !43, file: !1, line: 41, column: 37)
!43 = distinct !DILexicalBlock(scope: !40, file: !1, line: 41, column: 5)
!44 = !DILocation(line: 0, scope: !29)
!45 = !DILocation(line: 39, column: 5, scope: !29)
!46 = !DILocation(line: 39, column: 13, scope: !29)
!47 = !DILocation(line: 40, column: 5, scope: !29)
!48 = !DILocation(line: 0, scope: !40)
!49 = !DILocation(line: 41, column: 23, scope: !43)
!50 = !DILocation(line: 41, column: 5, scope: !40)
!51 = !DILocation(line: 45, column: 5, scope: !29)
!52 = !DILocation(line: 47, column: 1, scope: !29)
!53 = !DILocation(line: 46, column: 5, scope: !29)
!54 = !DILocation(line: 42, column: 22, scope: !42)
!55 = !DILocation(line: 0, scope: !42)
!56 = !DILocation(line: 43, column: 13, scope: !42)
!57 = !DILocation(line: 41, column: 32, scope: !43)
!58 = distinct !{!58, !50, !59}
!59 = !DILocation(line: 44, column: 5, scope: !40)
!60 = distinct !DISubprogram(name: "testVaArgInt", scope: !1, file: !1, line: 49, type: !61, scopeLine: 49, flags: DIFlagPrototyped | DIFlagAllCallsDescribed, spFlags: DISPFlagDefinition | DISPFlagOptimized, unit: !0, retainedNodes: !63)
!61 = !DISubroutineType(types: !62)
!62 = !{!23, !23, null}
!63 = !{!64, !65, !66, !67, !69}
!64 = !DILocalVariable(name: "count", arg: 1, scope: !60, file: !1, line: 49, type: !23)
!65 = !DILocalVariable(name: "sum", scope: !60, file: !1, line: 50, type: !23)
!66 = !DILocalVariable(name: "args", scope: !60, file: !1, line: 51, type: !36)
!67 = !DILocalVariable(name: "i", scope: !68, file: !1, line: 53, type: !23)
!68 = distinct !DILexicalBlock(scope: !60, file: !1, line: 53, column: 5)
!69 = !DILocalVariable(name: "num", scope: !70, file: !1, line: 54, type: !7)
!70 = distinct !DILexicalBlock(scope: !71, file: !1, line: 53, column: 37)
!71 = distinct !DILexicalBlock(scope: !68, file: !1, line: 53, column: 5)
!72 = !DILocation(line: 0, scope: !60)
!73 = !DILocation(line: 51, column: 5, scope: !60)
!74 = !DILocation(line: 51, column: 13, scope: !60)
!75 = !DILocation(line: 52, column: 5, scope: !60)
!76 = !DILocation(line: 0, scope: !68)
!77 = !DILocation(line: 53, column: 23, scope: !71)
!78 = !DILocation(line: 53, column: 5, scope: !68)
!79 = !DILocation(line: 55, column: 13, scope: !70)
!80 = !DILocation(line: 57, column: 5, scope: !60)
!81 = !DILocation(line: 59, column: 1, scope: !60)
!82 = !DILocation(line: 58, column: 5, scope: !60)
!83 = !DILocation(line: 54, column: 22, scope: !70)
!84 = !DILocation(line: 53, column: 32, scope: !71)
!85 = distinct !{!85, !78, !86}
!86 = !DILocation(line: 56, column: 5, scope: !68)
!87 = distinct !DISubprogram(name: "main", scope: !1, file: !1, line: 61, type: !88, scopeLine: 61, flags: DIFlagPrototyped | DIFlagAllCallsDescribed, spFlags: DISPFlagDefinition | DISPFlagOptimized, unit: !0, retainedNodes: !2)
!88 = !DISubroutineType(types: !89)
!89 = !{!23}
!90 = !DILocation(line: 62, column: 41, scope: !87)
!91 = !DILocation(line: 62, column: 5, scope: !87)
!92 = !DILocation(line: 63, column: 41, scope: !87)
!93 = !DILocation(line: 63, column: 5, scope: !87)
!94 = !DILocation(line: 64, column: 1, scope: !87)
