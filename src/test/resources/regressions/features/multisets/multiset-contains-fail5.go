package pkg

func foo() {
  //:: ExpectedOutput(assert_error:assertion_error)
  assert 42 in mset[int] { 1, 2, 3 }
}
