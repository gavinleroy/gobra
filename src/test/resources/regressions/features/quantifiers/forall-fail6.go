package pkg;

func foo(ghost n int) bool

// invalid: quantifier body isn't pure.
//:: ExpectedOutput(type_error)
requires forall k int :: foo(k)
func bar() { }