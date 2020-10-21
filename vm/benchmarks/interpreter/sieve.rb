
def run()
  number = 600000

  primes = Array.new(number + 1)

  for i in 2..number
    primes[i] = i
  end

  i = 2
  while i * i <= number do
    if primes[i] != 0 then
      for j in 2..(number - 1)
        if primes[i] * j > number then
          break
        else 
          primes[primes[i] * j] = 0
        end
      end
    end
    i += 1
  end

  return primes[number]
end

