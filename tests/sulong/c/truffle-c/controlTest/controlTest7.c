int main() {
  int a = 5;
  if (a-- == ++a) {
    return 5;
  } else {
    return -1;
  }
}
