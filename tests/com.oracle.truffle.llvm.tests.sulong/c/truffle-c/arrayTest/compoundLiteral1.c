int func() {
  static int i = 0;
  return i++;
}

int main() {
  int *arr;
  arr = (int[]){ 1, func(), func() };
  return arr[0] + arr[1] + arr[2] + func();
}
