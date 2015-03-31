function test() {
  i = 0;
  sum = 0;  
  while (i < 300000) { 
    sum = sum +  i;
    i = i + 1;  
  }
  return sum;
}

function main() {  
  test();
}  
