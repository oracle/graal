@func.initialized.b = internal unnamed_addr global i1 false
@a = common global i32* null, align 8

define i32 @main() nounwind uwtable {
  %.b = load i1* @func.initialized.b, align 1
  ret i32 1
}
