; ModuleID = 'va_arg_test.c'
source_filename = "va_arg_test.c"
target datalayout = "e-m:e-i8:8:32-i16:16:32-i64:64-i128:128-n32:64-S128"
target triple = "aarch64-unknown-linux-gnu"

%struct.__va_list = type { i8*, i8*, i8*, i32, i32 }

@.str = private unnamed_addr constant [25 x i8] c"Test int va_arg    : %d\0A\00", align 1
@.str.1 = private unnamed_addr constant [25 x i8] c"Test double va_arg : %f\0A\00", align 1

; Function Attrs: noinline nounwind optnone
define dso_local double @testVaArgDouble(i32 %0, ...) #0 !dbg !7 {
  %2 = alloca i32, align 4
  %3 = alloca double, align 8
  %4 = alloca %struct.__va_list, align 8
  %5 = alloca i32, align 4
  %6 = alloca double, align 8
  %7 = alloca %struct.__va_list, align 8
  store i32 %0, i32* %2, align 4
  call void @llvm.dbg.declare(metadata i32* %2, metadata !12, metadata !DIExpression()), !dbg !13
  call void @llvm.dbg.declare(metadata double* %3, metadata !14, metadata !DIExpression()), !dbg !15
  store double 0.000000e+00, double* %3, align 8, !dbg !15
  call void @llvm.dbg.declare(metadata %struct.__va_list* %4, metadata !16, metadata !DIExpression()), !dbg !28
  %8 = bitcast %struct.__va_list* %4 to i8*, !dbg !29
  call void @llvm.va_start(i8* %8), !dbg !29
  call void @llvm.dbg.declare(metadata i32* %5, metadata !30, metadata !DIExpression()), !dbg !32
  store i32 0, i32* %5, align 4, !dbg !32
  br label %9, !dbg !33

9:                                                ; preds = %20, %1
  %10 = load i32, i32* %5, align 4, !dbg !34
  %11 = load i32, i32* %2, align 4, !dbg !36
  %12 = icmp slt i32 %10, %11, !dbg !37
  br i1 %12, label %13, label %23, !dbg !38

13:                                               ; preds = %9
  call void @llvm.dbg.declare(metadata double* %6, metadata !39, metadata !DIExpression()), !dbg !41
  %14 = bitcast %struct.__va_list* %7 to i8*, !dbg !42
  %15 = bitcast %struct.__va_list* %4 to i8*, !dbg !42
  call void @llvm.memcpy.p0i8.p0i8.i64(i8* align 8 %14, i8* align 8 %15, i64 32, i1 false), !dbg !42
  %16 = va_arg %struct.__va_list* %7, double, !dbg !42
  store double %16, double* %6, align 8, !dbg !41
  %17 = load double, double* %6, align 8, !dbg !43
  %18 = load double, double* %3, align 8, !dbg !44
  %19 = fadd double %18, %17, !dbg !44
  store double %19, double* %3, align 8, !dbg !44
  br label %20, !dbg !45

20:                                               ; preds = %13
  %21 = load i32, i32* %5, align 4, !dbg !46
  %22 = add nsw i32 %21, 1, !dbg !46
  store i32 %22, i32* %5, align 4, !dbg !46
  br label %9, !dbg !47, !llvm.loop !48

23:                                               ; preds = %9
  %24 = bitcast %struct.__va_list* %4 to i8*, !dbg !50
  call void @llvm.va_end(i8* %24), !dbg !50
  %25 = load double, double* %3, align 8, !dbg !51
  ret double %25, !dbg !52
}

; Function Attrs: nounwind readnone speculatable willreturn
declare void @llvm.dbg.declare(metadata, metadata, metadata) #1

; Function Attrs: nounwind
declare void @llvm.va_start(i8*) #2

declare dso_local double @va_argDouble(%struct.__va_list*) #3

; Function Attrs: argmemonly nounwind willreturn
declare void @llvm.memcpy.p0i8.p0i8.i64(i8* noalias nocapture writeonly, i8* noalias nocapture readonly, i64, i1 immarg) #4

; Function Attrs: nounwind
declare void @llvm.va_end(i8*) #2

; Function Attrs: noinline nounwind optnone
define dso_local i32 @testVaArgInt(i32 %0, ...) #0 !dbg !53 {
  %2 = alloca i32, align 4
  %3 = alloca i32, align 4
  %4 = alloca %struct.__va_list, align 8
  %5 = alloca i32, align 4
  %6 = alloca double, align 8
  %7 = alloca %struct.__va_list, align 8
  store i32 %0, i32* %2, align 4
  call void @llvm.dbg.declare(metadata i32* %2, metadata !56, metadata !DIExpression()), !dbg !57
  call void @llvm.dbg.declare(metadata i32* %3, metadata !58, metadata !DIExpression()), !dbg !59
  store i32 0, i32* %3, align 4, !dbg !59
  call void @llvm.dbg.declare(metadata %struct.__va_list* %4, metadata !60, metadata !DIExpression()), !dbg !61
  %8 = bitcast %struct.__va_list* %4 to i8*, !dbg !62
  call void @llvm.va_start(i8* %8), !dbg !62
  call void @llvm.dbg.declare(metadata i32* %5, metadata !63, metadata !DIExpression()), !dbg !65
  store i32 0, i32* %5, align 4, !dbg !65
  br label %9, !dbg !66

9:                                                ; preds = %23, %1
  %10 = load i32, i32* %5, align 4, !dbg !67
  %11 = load i32, i32* %2, align 4, !dbg !69
  %12 = icmp slt i32 %10, %11, !dbg !70
  br i1 %12, label %13, label %26, !dbg !71

13:                                               ; preds = %9
  call void @llvm.dbg.declare(metadata double* %6, metadata !72, metadata !DIExpression()), !dbg !74
  %14 = bitcast %struct.__va_list* %7 to i8*, !dbg !75
  %15 = bitcast %struct.__va_list* %4 to i8*, !dbg !75
  call void @llvm.memcpy.p0i8.p0i8.i64(i8* align 8 %14, i8* align 8 %15, i64 32, i1 false), !dbg !75
  %16 = va_arg %struct.__va_list* %7, i32, !dbg !75
  %17 = sitofp i32 %16 to double, !dbg !75
  store double %17, double* %6, align 8, !dbg !74
  %18 = load double, double* %6, align 8, !dbg !76
  %19 = load i32, i32* %3, align 4, !dbg !77
  %20 = sitofp i32 %19 to double, !dbg !77
  %21 = fadd double %20, %18, !dbg !77
  %22 = fptosi double %21 to i32, !dbg !77
  store i32 %22, i32* %3, align 4, !dbg !77
  br label %23, !dbg !78

23:                                               ; preds = %13
  %24 = load i32, i32* %5, align 4, !dbg !79
  %25 = add nsw i32 %24, 1, !dbg !79
  store i32 %25, i32* %5, align 4, !dbg !79
  br label %9, !dbg !80, !llvm.loop !81

26:                                               ; preds = %9
  %27 = bitcast %struct.__va_list* %4 to i8*, !dbg !83
  call void @llvm.va_end(i8* %27), !dbg !83
  %28 = load i32, i32* %3, align 4, !dbg !84
  ret i32 %28, !dbg !85
}

declare dso_local i32 @va_argInt(%struct.__va_list*) #3

; Function Attrs: noinline nounwind optnone
define dso_local i32 @main() #0 !dbg !86 {
  %1 = call i32 (i32, ...) @testVaArgInt(i32 8, double 1.000000e+00, i32 2, double 3.000000e+00, i32 4, double 5.000000e+00, i32 6, double 7.000000e+00, i32 8, double 9.000000e+00, i32 10, double 1.100000e+01, i32 12, double 1.300000e+01, i32 14, double 1.500000e+01, i32 16), !dbg !89
  %2 = call i32 (i8*, ...) @printf(i8* getelementptr inbounds ([25 x i8], [25 x i8]* @.str, i64 0, i64 0), i32 %1), !dbg !90
  %3 = call double (i32, ...) @testVaArgDouble(i32 8, double 1.000000e+00, i32 2, double 3.000000e+00, i32 4, double 5.000000e+00, i32 6, double 7.000000e+00, i32 8, double 9.000000e+00, i32 10, double 1.100000e+01, i32 12, double 1.300000e+01, i32 14, double 1.500000e+01, i32 16), !dbg !91
  %4 = call i32 (i8*, ...) @printf(i8* getelementptr inbounds ([25 x i8], [25 x i8]* @.str.1, i64 0, i64 0), double %3), !dbg !92
  ret i32 0, !dbg !93
}

declare dso_local i32 @printf(i8*, ...) #3

attributes #0 = { noinline nounwind optnone "correctly-rounded-divide-sqrt-fp-math"="false" "disable-tail-calls"="false" "frame-pointer"="non-leaf" "less-precise-fpmad"="false" "min-legal-vector-width"="0" "no-infs-fp-math"="false" "no-jump-tables"="false" "no-nans-fp-math"="false" "no-signed-zeros-fp-math"="false" "no-trapping-math"="true" "stack-protector-buffer-size"="8" "target-cpu"="generic" "target-features"="+neon" "unsafe-fp-math"="false" "use-soft-float"="false" }
attributes #1 = { nounwind readnone speculatable willreturn }
attributes #2 = { nounwind }
attributes #3 = { "correctly-rounded-divide-sqrt-fp-math"="false" "disable-tail-calls"="false" "frame-pointer"="non-leaf" "less-precise-fpmad"="false" "no-infs-fp-math"="false" "no-nans-fp-math"="false" "no-signed-zeros-fp-math"="false" "no-trapping-math"="true" "stack-protector-buffer-size"="8" "target-cpu"="generic" "target-features"="+neon" "unsafe-fp-math"="false" "use-soft-float"="false" }
attributes #4 = { argmemonly nounwind willreturn }

!llvm.dbg.cu = !{!0}
!llvm.module.flags = !{!3, !4, !5}
!llvm.ident = !{!6}

!0 = distinct !DICompileUnit(language: DW_LANG_C99, file: !1, producer: "clang version 11.0.0", isOptimized: false, runtimeVersion: 0, emissionKind: FullDebug, enums: !2, splitDebugInlining: false, nameTableKind: None)
!1 = !DIFile(filename: "va_arg_test.c", directory: "/Users/zslajchrt/work/graaldev/graal/sulong/tests/com.oracle.truffle.llvm.tests.va.native/va_arg")
!2 = !{}
!3 = !{i32 7, !"Dwarf Version", i32 4}
!4 = !{i32 2, !"Debug Info Version", i32 3}
!5 = !{i32 1, !"wchar_size", i32 4}
!6 = !{!"clang version 11.0.0"}
!7 = distinct !DISubprogram(name: "testVaArgDouble", scope: !1, file: !1, line: 37, type: !8, scopeLine: 37, flags: DIFlagPrototyped, spFlags: DISPFlagDefinition, unit: !0, retainedNodes: !2)
!8 = !DISubroutineType(types: !9)
!9 = !{!10, !11, null}
!10 = !DIBasicType(name: "double", size: 64, encoding: DW_ATE_float)
!11 = !DIBasicType(name: "int", size: 32, encoding: DW_ATE_signed)
!12 = !DILocalVariable(name: "count", arg: 1, scope: !7, file: !1, line: 37, type: !11)
!13 = !DILocation(line: 37, column: 28, scope: !7)
!14 = !DILocalVariable(name: "sum", scope: !7, file: !1, line: 38, type: !10)
!15 = !DILocation(line: 38, column: 12, scope: !7)
!16 = !DILocalVariable(name: "args", scope: !7, file: !1, line: 39, type: !17)
!17 = !DIDerivedType(tag: DW_TAG_typedef, name: "va_list", file: !18, line: 14, baseType: !19)
!18 = !DIFile(filename: "/usr/local/Cellar/llvm/11.0.0/lib/clang/11.0.0/include/stdarg.h", directory: "")
!19 = !DIDerivedType(tag: DW_TAG_typedef, name: "__builtin_va_list", file: !1, line: 39, baseType: !20)
!20 = distinct !DICompositeType(tag: DW_TAG_structure_type, name: "__va_list", file: !1, line: 39, size: 256, elements: !21)
!21 = !{!22, !24, !25, !26, !27}
!22 = !DIDerivedType(tag: DW_TAG_member, name: "__stack", scope: !20, file: !1, line: 39, baseType: !23, size: 64)
!23 = !DIDerivedType(tag: DW_TAG_pointer_type, baseType: null, size: 64)
!24 = !DIDerivedType(tag: DW_TAG_member, name: "__gr_top", scope: !20, file: !1, line: 39, baseType: !23, size: 64, offset: 64)
!25 = !DIDerivedType(tag: DW_TAG_member, name: "__vr_top", scope: !20, file: !1, line: 39, baseType: !23, size: 64, offset: 128)
!26 = !DIDerivedType(tag: DW_TAG_member, name: "__gr_offs", scope: !20, file: !1, line: 39, baseType: !11, size: 32, offset: 192)
!27 = !DIDerivedType(tag: DW_TAG_member, name: "__vr_offs", scope: !20, file: !1, line: 39, baseType: !11, size: 32, offset: 224)
!28 = !DILocation(line: 39, column: 13, scope: !7)
!29 = !DILocation(line: 40, column: 5, scope: !7)
!30 = !DILocalVariable(name: "i", scope: !31, file: !1, line: 41, type: !11)
!31 = distinct !DILexicalBlock(scope: !7, file: !1, line: 41, column: 5)
!32 = !DILocation(line: 41, column: 14, scope: !31)
!33 = !DILocation(line: 41, column: 10, scope: !31)
!34 = !DILocation(line: 41, column: 21, scope: !35)
!35 = distinct !DILexicalBlock(scope: !31, file: !1, line: 41, column: 5)
!36 = !DILocation(line: 41, column: 25, scope: !35)
!37 = !DILocation(line: 41, column: 23, scope: !35)
!38 = !DILocation(line: 41, column: 5, scope: !31)
!39 = !DILocalVariable(name: "num", scope: !40, file: !1, line: 42, type: !10)
!40 = distinct !DILexicalBlock(scope: !35, file: !1, line: 41, column: 37)
!41 = !DILocation(line: 42, column: 16, scope: !40)
!42 = !DILocation(line: 42, column: 22, scope: !40)
!43 = !DILocation(line: 43, column: 16, scope: !40)
!44 = !DILocation(line: 43, column: 13, scope: !40)
!45 = !DILocation(line: 44, column: 5, scope: !40)
!46 = !DILocation(line: 41, column: 32, scope: !35)
!47 = !DILocation(line: 41, column: 5, scope: !35)
!48 = distinct !{!48, !38, !49}
!49 = !DILocation(line: 44, column: 5, scope: !31)
!50 = !DILocation(line: 45, column: 5, scope: !7)
!51 = !DILocation(line: 46, column: 12, scope: !7)
!52 = !DILocation(line: 46, column: 5, scope: !7)
!53 = distinct !DISubprogram(name: "testVaArgInt", scope: !1, file: !1, line: 49, type: !54, scopeLine: 49, flags: DIFlagPrototyped, spFlags: DISPFlagDefinition, unit: !0, retainedNodes: !2)
!54 = !DISubroutineType(types: !55)
!55 = !{!11, !11, null}
!56 = !DILocalVariable(name: "count", arg: 1, scope: !53, file: !1, line: 49, type: !11)
!57 = !DILocation(line: 49, column: 22, scope: !53)
!58 = !DILocalVariable(name: "sum", scope: !53, file: !1, line: 50, type: !11)
!59 = !DILocation(line: 50, column: 9, scope: !53)
!60 = !DILocalVariable(name: "args", scope: !53, file: !1, line: 51, type: !17)
!61 = !DILocation(line: 51, column: 13, scope: !53)
!62 = !DILocation(line: 52, column: 5, scope: !53)
!63 = !DILocalVariable(name: "i", scope: !64, file: !1, line: 53, type: !11)
!64 = distinct !DILexicalBlock(scope: !53, file: !1, line: 53, column: 5)
!65 = !DILocation(line: 53, column: 14, scope: !64)
!66 = !DILocation(line: 53, column: 10, scope: !64)
!67 = !DILocation(line: 53, column: 21, scope: !68)
!68 = distinct !DILexicalBlock(scope: !64, file: !1, line: 53, column: 5)
!69 = !DILocation(line: 53, column: 25, scope: !68)
!70 = !DILocation(line: 53, column: 23, scope: !68)
!71 = !DILocation(line: 53, column: 5, scope: !64)
!72 = !DILocalVariable(name: "num", scope: !73, file: !1, line: 54, type: !10)
!73 = distinct !DILexicalBlock(scope: !68, file: !1, line: 53, column: 37)
!74 = !DILocation(line: 54, column: 16, scope: !73)
!75 = !DILocation(line: 54, column: 22, scope: !73)
!76 = !DILocation(line: 55, column: 16, scope: !73)
!77 = !DILocation(line: 55, column: 13, scope: !73)
!78 = !DILocation(line: 56, column: 5, scope: !73)
!79 = !DILocation(line: 53, column: 32, scope: !68)
!80 = !DILocation(line: 53, column: 5, scope: !68)
!81 = distinct !{!81, !71, !82}
!82 = !DILocation(line: 56, column: 5, scope: !64)
!83 = !DILocation(line: 57, column: 5, scope: !53)
!84 = !DILocation(line: 58, column: 12, scope: !53)
!85 = !DILocation(line: 58, column: 5, scope: !53)
!86 = distinct !DISubprogram(name: "main", scope: !1, file: !1, line: 61, type: !87, scopeLine: 61, flags: DIFlagPrototyped, spFlags: DISPFlagDefinition, unit: !0, retainedNodes: !2)
!87 = !DISubroutineType(types: !88)
!88 = !{!11}
!89 = !DILocation(line: 62, column: 41, scope: !86)
!90 = !DILocation(line: 62, column: 5, scope: !86)
!91 = !DILocation(line: 63, column: 41, scope: !86)
!92 = !DILocation(line: 63, column: 5, scope: !86)
!93 = !DILocation(line: 64, column: 1, scope: !86)
