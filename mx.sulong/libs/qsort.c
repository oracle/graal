#include <stddef.h>
#include <stdlib.h>
#include <string.h>

void sulong_swap(void *vp1, void *vp2, const size_t size) {
  char *buffer = (char *)malloc(size);
  memcpy(buffer, vp1, size);
  memcpy(vp1, vp2, size);
  memcpy(vp2, buffer, size);
  free(buffer);
}

void sulong_qsort(char *v, long left, long right, int (*comp)(const void *, const void *), size_t size) {
  int i, last;
  if (left >= right) {
    return;
  }
  sulong_swap(&v[left * size], &v[((left + right) / 2) * size], size);
  last = left;
  for (i = left + 1; i <= right; i++) {
    if (comp(&(v[i * size]), &(v[left * size])) < 0) {
      last++;
      sulong_swap(&(v[last * size]), &(v[i * size]), size);
    }
  }
  sulong_swap(&(v[left * size]), &(v[last * size]), size);
  sulong_qsort(v, left, last - 1, comp, size);
  sulong_qsort(v, last + 1, right, comp, size);
}

void qsort(void *v, size_t number, size_t size, int (*comp)(const void *, const void *)) { sulong_qsort(v, 0, number - 1, comp, size); }
