int main() {
  long adr = 0;
  int sum = 0;
  sum += adr > &adr;
  sum += adr < &adr;
  sum += adr >= &adr;
  sum += adr <= &adr;
  sum += adr == &adr;
  sum += adr != &adr;
  return sum;
}
