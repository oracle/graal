#include <emmintrin.h>
#include <assert.h>

int main() {
  __m128i val1 = { 54312, 32423 };
  __m128i val2 = { 0x80808080, 0x80808080 };
  __m128i val3 = { 0x8080808080808080L, 0x8080808080808080L };
  __m128i val4 = { 0, 0 };
  __m128i val5 = { -1, -1 };
  assert(_mm_movemask_epi8(val1) == 258);
  assert(_mm_movemask_epi8(val2) == 0x0f0f);
  assert(_mm_movemask_epi8(val3) == 0xffff);
  assert(_mm_movemask_epi8(val4) == 0);
  assert(_mm_movemask_epi8(val5) == 0xffff);
}
