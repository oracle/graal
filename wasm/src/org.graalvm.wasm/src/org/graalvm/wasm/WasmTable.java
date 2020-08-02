package org.graalvm.wasm;

import com.oracle.truffle.api.CompilerDirectives;
import org.graalvm.wasm.exception.WasmExecutionException;
import org.graalvm.wasm.exception.WasmValidationException;

public final class WasmTable {
    private final int tableIndex;
    private final int maxSize;
    @CompilerDirectives.CompilationFinal(dimensions = 1) private Object[] elements;

    public WasmTable(int tableIndex, int initSize, int maxSize) {
        this.tableIndex = tableIndex;
        this.elements = new Object[initSize];
        this.maxSize = maxSize;
    }

    public void ensureSizeAtLeast(int targetSize) {
        if (maxSize >= 0 && targetSize > maxSize) {
            throw new WasmValidationException("Table " + tableIndex + " cannot be resized to " + targetSize + ", " +
                            "declared maximum size is " + maxSize);
        }
        if (elements.length < targetSize) {
            Object[] newElements = new Object[targetSize];
            System.arraycopy(elements, 0, newElements, 0, elements.length);
            elements = newElements;
        }
    }

    public int tableIndex() {
        return tableIndex;
    }

    public int size() {
        return elements.length;
    }

    public int maxSize() {
        return maxSize;
    }

    public Object[] elements() {
        return elements;
    }

    public Object get(int index) {
        return elements[index];
    }

    public void set(int index, Object function) {
        elements[index] = function;
    }

    public void initialize(int i, WasmFunction function) {
        if (elements[i] != null) {
            throw new WasmValidationException("Table " + tableIndex + " already has an element at index " + i + ".");
        }
        elements[i] = function;
    }

    public boolean grow(long delta) {
        throw new WasmExecutionException(null, "Tables cannot be grown.");
    }
}
