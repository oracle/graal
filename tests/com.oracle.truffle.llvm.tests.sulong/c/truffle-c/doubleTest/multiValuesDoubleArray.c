int main() {
  double arr[3];
  arr[0] = 1;
  arr[1] = 2.0;
  arr[2] = arr - arr;
  return (int)(arr[0] + arr[1] + arr[2]);
}
