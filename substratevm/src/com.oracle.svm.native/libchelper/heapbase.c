const void* heapBase(void) {
  extern int __svm_heap_base;
  return &__svm_heap_base;
}

