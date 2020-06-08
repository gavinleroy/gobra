package pkg

func example1(ghost m mset[int]) (ghost n int) {
	n = |m|
}

func example2(ghost m1 mset[int], ghost m2 mset[int]) {
  ghost n := |m1 union m2|
}

func example3() {
  assert |mset[int] { 1, 2, 2 }| == 3
  assert |mset[int] { 1, 2, 2 }| == |mset[int] { 2, 2, 3 }|;
  assert |mset[int] { 1, 2, 2 }| != |set[int] { 1, 2, 2 }|;
  assert |mset[int] { 1, 2 } union mset[int] { 2, 3 }| == 4
}

ensures |m1 union m2| == |m1| + |m2|;
func example4(ghost m1 mset[int], ghost m2 mset[int]) {
}
