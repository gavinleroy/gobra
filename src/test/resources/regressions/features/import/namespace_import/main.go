package main

// ##(-I src/test/resources/regressions/features/import/namespace_import)
import b "bar"

// create a second type called Rectangle:
type Rectangle struct {
    Width, Height int
}

requires acc(r.Width)
ensures acc(r.Width)
ensures res == r.Width * r.Width
ensures old(r.Width) == r.Width
func (r *Rectangle) Area() (res int) {
    // note that this is on purpose a wrong implementation to assert
    // that the correct Rectangle type and method gets selected by Gobra
    return r.Width * r.Width
}

func foo() {
    r! := b.Rectangle{Width: 2, Height: 5}
    assert r.Area() == 10
    assert (*(b.Rectangle)).Area(&r) == 10

    r1! := Rectangle{Width: 2, Height: 5}
    assert r1.Area() == 4
    assert (*(Rectangle)).Area(&r1) == 4
}
