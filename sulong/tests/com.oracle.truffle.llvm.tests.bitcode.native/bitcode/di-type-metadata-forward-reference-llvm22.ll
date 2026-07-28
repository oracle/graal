; LLVM 22 DI type size and offset metadata are serialized after their uses.

define i32 @main() {
  ret i32 0
}

!llvm.dbg.cu = !{!0}
!llvm.module.flags = !{!11}
!0 = distinct !DICompileUnit(language: DW_LANG_C, file: !1, producer: "LLVM 22", isOptimized: false, runtimeVersion: 0, emissionKind: FullDebug, globals: !2)
!1 = !DIFile(filename: "di-type-metadata-forward-reference-llvm22.c", directory: ".")
!2 = !{!3}
!3 = !DIGlobalVariableExpression(var: !4, expr: !DIExpression())
!4 = !DIGlobalVariable(name: "value", scope: !0, file: !1, line: 1, type: !5, isLocal: false, isDefinition: true)
!5 = !DIDerivedType(tag: DW_TAG_member, name: "value", scope: !6, file: !1, line: 1, baseType: !7, size: i64 64, offset: i64 0)
!6 = distinct !DICompositeType(tag: DW_TAG_structure_type, name: "S", file: !1, line: 1, size: 64, elements: !10)
!7 = !DIBasicType(name: "long", size: 64, encoding: DW_ATE_signed)
!10 = !{!5}
!11 = !{i32 2, !"Debug Info Version", i32 3}
