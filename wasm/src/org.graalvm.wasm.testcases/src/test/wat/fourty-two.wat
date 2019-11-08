(module
  (type $t0 (func))
  (type $t1 (func (result i32)))
  (import "env" "memory" (memory (;0;) 256 256))
  (func $main (export "_main") (type $t1) (result i32)
    i32.const 42)
  (global $g0 (mut i32) (i32.const 66560))
  (global $__heap_base (export "__heap_base") i32 (i32.const 66560))
  (global $__data_end (export "__data_end") i32 (i32.const 1024)))
