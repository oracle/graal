#include <polyglot.h>

struct Point {
  int x;
  int y;
};

POLYGLOT_DECLARE_STRUCT(Point)

int distSquared(void *a, void *b) {
  int distX = polyglot_as_Point(b)->x - polyglot_as_Point(a)->x;
  int distY = polyglot_as_Point(b)->y - polyglot_as_Point(a)->y;
  return distX * distX + distY * distY;
}

void flipPoint(void *value) {
  struct Point *point = polyglot_as_Point(value);
  int tmp = point->x;
  point->x = point->y;
  point->y = tmp;
}

int sumPoints(void *pointArray) {
  int sum;

  struct Point *arr = polyglot_as_Point_array(pointArray);
  int len = polyglot_get_array_size(pointArray);
  for (int i = 0; i < len; i++) {
    sum += arr[i].x + arr[i].y;
  }

  return sum;
}

void fillPoints(void *pointArray, int x, int y) {
  struct Point *arr = polyglot_as_Point_array(pointArray);
  int len = polyglot_get_array_size(pointArray);

  for (int i = 0; i < len; i++) {
    arr[i].x = x;
    arr[i].y = y;
  }
}

struct Nested {
  struct Point arr[5];
  struct Point direct;
  struct Nested *next;
};

POLYGLOT_DECLARE_STRUCT(Nested)

void fillNested(void *arg) {
  int value = 42;

  struct Nested *nested = polyglot_as_Nested(arg);
  while (nested) {
    for (int i = 0; i < 5; i++) {
      nested->arr[i].x = value++;
      nested->arr[i].y = value++;
    }
    nested->direct.x = value++;
    nested->direct.y = value++;

    nested = nested->next;
  }
}

struct BitFields {
  int x : 4;
  int y : 3;
  int z;
};

POLYGLOT_DECLARE_STRUCT(BitFields)

int accessBitFields(void *arg) {
	struct BitFields *obj = polyglot_as_BitFields(arg);
	return obj->x + obj->y + obj->z;
}

struct FusedArray {
  struct Point origin;
  struct Point path[0];
};

POLYGLOT_DECLARE_STRUCT(FusedArray)

int fillFusedArray(void *arg) {
  struct FusedArray *fused = polyglot_as_FusedArray(arg);
  int i;

  fused->origin.x = 3;
  fused->origin.y = 7;

  for (i = 0; i < 7; i++) {
    fused->path[i].x = 2 * i;
    fused->path[i].y = 5 * i;    
  }
}
