#include <truffle.h>
#include <stdint.h>

typedef struct {
  int8_t valueB;
  int16_t valueS;
  int32_t valueI;
  int64_t valueL;
  float valueF;
  double valueD;
} CLASS;

#define DEF_ACCESSORS(type, name) \
    type getStruct##name(CLASS *c) { \
        return c->value##name; \
    } \
    \
    void setStruct##name(CLASS *c, type v) { \
        c->value##name = v; \
    } \
    \
    type getArray##name(type *arr, int idx) { \
        return arr[idx]; \
    } \
    \
    void setArray##name(type *arr, int idx, type v) { \
        arr[idx] = v; \
    } \

DEF_ACCESSORS(int8_t, B)
DEF_ACCESSORS(int16_t, S)
DEF_ACCESSORS(int32_t, I)
DEF_ACCESSORS(int64_t, L)
DEF_ACCESSORS(float, F)
DEF_ACCESSORS(double, D)
