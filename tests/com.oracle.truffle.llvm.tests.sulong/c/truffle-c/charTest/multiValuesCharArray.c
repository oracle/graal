int main() {
  char arr[3];
  arr[0] = 'c';
  arr[1] = 2;
  arr[2] = arr[0] - arr[1];
  return (int)(arr[0] + arr[1] + arr[2]);
}
