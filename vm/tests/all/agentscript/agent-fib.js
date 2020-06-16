function minusOne(n) {
  return n - 1;
}
function minusTwo(n) {
  return n - 2;
}
function fib(n) {
  if (n < 1) return 0;
  if (n < 2) return 1;
  else return fib(minusOne(n)) + fib(minusTwo(n));
}
print("Three is the result " + fib(4));
