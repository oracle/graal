union u {
  int a[3];
};

int main() {
  int arr[3];
  return sizeof(union u) >= sizeof(arr);
}
