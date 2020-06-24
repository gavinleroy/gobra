package pkg

func example1(ghost x int, ghost m mset[int]) {
  ghost var b bool
  b = x in m
}

func example2(ghost x int, ghost m1 mset[int], ghost m2 mset[bool]) {
  ghost var b bool
  b = x in m1 in m2
  assert x in m1 in m2 == (x in m1) in m2
}

func example3() {
  assert 2 in mset[int] { 1, 2, 3 }
  assert !(2 in mset[int] { 1, 3 })
  assert 2 in mset[int] { 1, 2, 2, 2, 3 }
  assert 1 in mset[int] { 1 } in mset[bool] { true } in mset[bool] { true, false }
}

func example4(ghost x int) {
  assert !(x in mset[int] { } in mset[bool] { } in mset[bool] { } in mset[bool] { })
}

func example5(ghost x int, ghost m1 mset[int], ghost m2 mset[int]) {
  assert x in m1 ==> x in m1 union m2
  assert x in m2 ==> x in m1 union m2
  
  assert x in m1 intersection m2 ==> x in m1
  assert x in m1 intersection m2 ==> x in m2
}

ensures x in m1 union m2 ==> x in m1 || x in m2
func example6(ghost x int, ghost m1 mset[int], ghost m2 mset[int]) {
}

requires m1 subset m2
func example7(ghost x int, ghost m1 mset[int], ghost m2 mset[int]) {
  assert x in m1 ==> x in m2
}
