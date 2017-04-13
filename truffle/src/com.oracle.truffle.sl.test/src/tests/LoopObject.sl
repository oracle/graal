function loop(n) {
  obj = new();
  obj.i = 0;  
  while (obj.i < n) {  
    obj.i = obj.i + 1;  
  }  
  return obj.i;
}  

function main() {
  i = 0;
  while (i < 20) {
    loop(1000);
    i = i + 1;
  }
  println(loop(1000));  
}  
