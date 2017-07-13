struct _point {
  double x;
  double y;
};

typedef struct _point point;

// this line creates two forward references on the same symbol (20.0)
point p1 = { 20.0, 20.0 };

int main () {
  return 0;
}
