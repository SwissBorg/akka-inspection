# Akka-Inspection

Akka-Inspection is an extension for Akka that can be used to expose an 
actor's state and make it inspectable from outside the cluster. 

The extension comes in two flavors: a trait specific for actors using
mutation as a way to transform their state and another one for actors that
mutate their state by recursively calling `context.become(...)`. 


## Mutable actor inspection

To add the ability to inspect a "mutable" actor the trait `MutableInspection`
has to be mixed into the `Actor`. 

This requires to implement `fragments` which can be seen as a set of 
getters on the actors state. See the section *FragmentId and Fragment*
for more details.
```scala
val fragments: Map[FragmentId, Fragment]
```

### Automatic derivation
`fragments` can be implemented automatically by using the helper function
`fragmentsFrom`. See *Inspectable* for more details.

```scala
import akka.actor.Actor
import com.swissborg.akkainspection.MutableInspection
import com.swissborg.akkainspection.inspectable.Inspectable
import com.swissborg.akkainspection.inspectable.derivation.semiauto._

class MyMutableActor extends Actor with MutableInspection {
  import MyMutableActor._
  
  var s: State = ???
  
  override def receive  = ???
  
  override val fragments: Map[FragmentId, Fragment] = fragmentsFrom(s)
}

object MyMutableActor {
  case class State(/*...*/)
  object State {
    implicit val stateInspectable: Inspectable[State] = deriveInspectable
  }
}
```

## Immutable actor inspection

To add the ability to inspect a "immutable" actor the trait `ImmutableInspection`
has to be mixed into the `Actor`. 
 
The trait exposes two methods: `inspect` and `withInspection`. These methods
are used to enhance the existing `Receive` methods to be able to inspect the 
state. They do the same thing and choosing one over another is a matter of taste.

`inspect` can be used in an `... orElse ...` pattern whereas `withInspection` wraps around
an existing `Receive`. 

```scala
```

The argument `state` in `inspect` or `withInspection` can be used to give 
a name to the current state in which the actor is. This will be visible
when inspecting the actor.

## FragmentId and Fragment

A `Fragment` represents a part of an actor's state. Such a fragment can
be anything that can be extracted from the state. A `FragmentId` is then
a way to access this fragment. It's simply a type-safe wrapper around a 
string.

Different types of `Fragment`s exits:

#### Const
`Const[T: Render](t: T)` is a fragment that always returns the state-fragment
`t`.

#### Always
`Always[T: Render](t: => T)` is a fragment that always returns the same 
state-fragment `t`. It differs to `Const` as it is pass by-name.

#### Getter
`Getter[S, T: Render](get: S => T)` is a fragment that extract a `T` from the
state `S`.

#### Sensitive
A fragment that be used as a placeholder for sensitive data that should 
not be exposed to the outside.

## Render
`Render[A]` is a typeclass used to render `A` (transform into a `String`). 
By default it uses `toString` but can be overidden by adding an instance
in scope.

## Inspectable
`Inspectable[A]` is a typeclass for `A`s that are inspectable. In other words,
where `A` can deconstructed into at least one `Fragment`. 

### Derivation
An instance can automatically derived for `A`s that case classes or tuples.
More generally, product types.

```scala
object Foo {
  case class A(l: List[Double])
  case class B(s: String, bar: innerB)
  case class innerB(i: Int)
  
  case class State(a: A, b: B)
}
```

The `Inspectable` instance for `State` will derive the most precise `Fragment`s.
The equivalent instance implemented by hand is:

```scala
val stateInspectable: Inspectable[State] = new Inspectable[State] {
  override val fragments: Map[FragmentId, inspection.Fragment[State]] = Map(
    FragmentId("a.l") -> Fragment.getter(_.a.l),
    FragmentId("b.s") -> Fragment.getter(_.b.s),
    FragmentId("b.bar.i") -> Fragment.getter(_.b.bar.i)
  )
}
``` 

#### Semi-automatic derivation
```scala
import com.swissborg.akkainspection.inspectable.Inspectable
import com.swissborg.akkainspection.inspectable.derivation.semiauto._

object Foo {
  case class A(l: List[Double])
  case class B(s: String, bar: innerB)
  case class innerB(i: Int)
  
  case class State(a: A, b: B)
  object State {
    val stateInspectable: Inspectable[State] = deriveInspectable
  }
}
```

#### Automatic derivation
Add the import `com.swissborg.swissborg.akkainspection.inspectable.derivation.auto._` to derive
an `Inspectable` without any boilerplate.