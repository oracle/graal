#define size 100

typedef int int_array[size];

int main() {
  int_array a;
  int i;
  int sum = 0;
  for (i = 0; i < size; i++) {
    a[i] = i / 10;
  }
  for (i = 0; i < size; i += 10) {
    sum += a[i];
  }
  return sum;
}
