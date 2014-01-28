function fib(num) { 
  if (num < 1) {return 0;}
  n1 = 0;
  n2 = 1;
  i = 1;
  while (i < num) {
    next = n2 + n1;
    n1 = n2;
    n2 = next;
    i = i + 1;
  }
  return n2;
}

function main() {  
  print(fib(42));
}  
