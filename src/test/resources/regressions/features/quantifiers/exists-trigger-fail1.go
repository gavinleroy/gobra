package pkg;

pure func foo() int { 
  return 42
}

// invalid trigger: doesn't contain the quantified variable
//:: ExpectedOutput(type_error)
requires exists x int :: { foo() } 0 < x 
func bar () { }
