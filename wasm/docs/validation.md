# Validation

This file lists where and how each validation rule from the WebAssembly is implemented in GraalWasm.

## 3.2 Types

### 3.2.3 Table types

- *The limits `limits` must be valid within range `2^32`.*

	Validated in: `BinaryParser.readTableLimits`.
	
	Note: Limit is lowered to `2^31 - 1` due to Java indices limit.
	
	Tests: `table_invalid_limits-*`.

### 3.2.4 Memory types

- *The limits `limits` must be valid within range `2^16`.*. 

	Validated in: `BinaryParser.readMemoryLimits`.
	
	Tests: `memory_invalid_limits-*`.

### 3.2.5 Global types

(No constraints)

## 3.4 Modules

### 3.4.1 Functions

- *The type `C.types[x]` must be defined in the context.*

	Validated in: `SymbolTable.allocateFunction`.
	
	Tests: `function_invalid_type_index-*`.

- *Under the context `C'`, the expression `express` must be valid with type `t2`.*

	TODO

### 3.4.2 Tables

- *The table type `tabletype` must be valid.*: see §3.2.3.

### 3.4.3 Memories

- *The table type `memtype` must be valid.*: see §3.2.4.

### 3.4.4 Globals

- *The global type `mut t` must be valid.*

	See §3.2.5.
	
- *The expression `expr` must be valid with result type `[t]`.*

	TODO
	
- *The `expr` must be constant.*

	TODO

### 3.4.5 Element Segments

- *The table `C.tables[x]` must be defined in the context.*

	Validated in: `BinaryParser.readElementSection`.

	Tests: `element_invalid_table_index`.

- *The element type `elemtype` must be `funcref`.*

	Validated in: `BinaryParser.readTableSection` and `BinaryParser.readImportSection`.
  
	Tests: TODO.
  
- *The expression `expr` must be valid with result type `[i32]`.*:

	TODO.
	
- *The expression `expo` must be constant.*

	TODO.
	
- *For each `y` in `y*`, the function `C.funcs[y]` must be defined in the context.*

	Validated in: `SymbolTable.function`.

	Tests: `element_invalid_function_index`.


TODO: 3.4.6-3.4.10

### 3.4.10 Modules

- *The length of `C.tables` must not be larger than 1.*

	Validated in: `BinaryParser.readTableSection` and `SymbolTable.validateSingleTable`.
	
	Tests: `two_tables-*`.

- *The length of `C.mems` must not be larger than 1.*

	Validated in: `BinaryParser.readMemorySection` and `SymbolTable.validateSingleMemory`.
	
	Tests: `two_memories-*`.

- *All export names `export_i.name` must be different.*

	Validated in: `SymbolTable.checkUniqueExport`.
	
	Tests: `duplicated_export-*`.
