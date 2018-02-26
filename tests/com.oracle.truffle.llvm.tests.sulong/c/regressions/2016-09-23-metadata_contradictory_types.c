struct _point {
  int x, y;
};

typedef struct _point myPoint;
typedef struct _point yourPoint;

void foo() { yourPoint p; }

int main() {
  myPoint p;
  return 0;
}
