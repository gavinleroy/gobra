package pkg

func foo(ghost x int, ghost s set[int]) {
  //:: ExpectedOutput(assert_error:assertion_error)
  assert (x in s && x # s == 0) || (!(x in s) && x # s == 1)
}
