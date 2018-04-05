#include <polyglot.h>
#include <stdlib.h>

struct Point {
  int x;
  int y;
};

POLYGLOT_DECLARE_STRUCT(Point)

void *allocPoint(int x, int y) {
  struct Point *ret = malloc(sizeof(*ret));
  ret->x = x;
  ret->y = y;
  return polyglot_from_Point(ret);
}

void freePoint(struct Point *point) {
  free(point);
}

int readPoint(struct Point *point) {
  return point->x * 1000 + point->y;
}

void *allocPointArray(int length) {
  struct Point *ret = calloc(length, sizeof(*ret));
  return polyglot_from_Point_array(ret, length);
}

int readPointArray(struct Point *array, int idx) {
  return readPoint(&array[idx]);
}

struct Nested {
  long primArray[13];
  struct Point pointArray[5];
  struct Point *ptrArray[7];
  struct Point *aliasedPtr;
};

POLYGLOT_DECLARE_STRUCT(Nested)

void *allocNested() {
  struct Nested *ret = calloc(1, sizeof(*ret));
  for (int i = 0; i < 13; i++) {
    ret->primArray[i] = 3 * i + 1;
  }
  for (int i = 0; i < 7; i++) {
    ret->ptrArray[i] = allocPoint(2 * i, 2 * i + 1);
  }
  return polyglot_from_Nested(ret);
}

void freeNested(struct Nested *nested) {
  for (int i = 0; i < 7; i++) {
    freePoint(nested->ptrArray[i]);
  }
  free(nested);
}

long hashNested(struct Nested *nested) {
  long ret = 0;
  for (int i = 0; i < 13; i++) {
    ret = 13 * ret + nested->primArray[i];
  }
  for (int i = 0; i < 5; i++) {
    ret = 13 * ret + nested->pointArray[i].x;
    ret = 13 * ret + nested->pointArray[i].y;
  }
  for (int i = 0; i < 7; i++) {
    ret = 13 * ret + nested->ptrArray[i]->x;
    ret = 13 * ret + nested->ptrArray[i]->y;
  }
  return ret;
}

int getAliasedPtrIndex(struct Nested *nested) {
  return nested->aliasedPtr - nested->pointArray;
}

int findPoint(struct Nested *nested, struct Point *point) {
  for (int i = 0; i < 7; i++) {
    if (nested->ptrArray[i] == point) {
      return i;
    }
  }
  return -1;
}
