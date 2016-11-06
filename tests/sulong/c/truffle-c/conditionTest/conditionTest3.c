int main() {
  int sum = 1 || 2;
  sum = !(!!1 && 1 || 2 && 3);
  sum += sum || !sum && 0;
  return sum;
}
