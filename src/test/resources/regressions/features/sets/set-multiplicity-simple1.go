package pkg

ensures 0 <= n && n <= 1
ensures n == 0 ==> !(x in s)
ensures n == 1 ==> x in s
func example1(ghost x int, ghost s set[int]) (ghost n int) {
  n = x # s
}

func example2(ghost x int, ghost s1 set[int], ghost s2 set[int]) {
  assert 0 < x # s1 union s2 ==> x in s1 || x in s2
  assert 0 < x # s1 intersection s2 ==> x in s1 && x in s2
  assert 0 < x # s1 setminus s2 ==> x in s1 && !(x in s2)
}

requires s1 subset s2
func example3(ghost x int, ghost s1 set[int], ghost s2 set[int]) {
  assert x # s1 <= x # s2
}

func example4() {
  assert 2 # set[int] { 1, 2, 3 } == 1
  assert 2 # set[int] { 1, 2, 2, 3 } == 1
  assert 42 # set[int] { 1, 2, 3 } == 0
  assert 1 # set[int] { } == 0
}

ensures x # set[int] { } == 0
func example5(ghost x int) {
}

ensures x # s <= |s|;
func example6(ghost x int, ghost s set[int]) {
}