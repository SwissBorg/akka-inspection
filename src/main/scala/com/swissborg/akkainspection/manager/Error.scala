package com.swissborg.akkainspection.manager

sealed abstract class Error extends Product with Serializable

/** The actor is not known to be inspectable. */
final case class ActorNotInspectable(id: String) extends Error

/** The actor is known to be inspectable but cannot be reached. */
final case class UnreachableInspectableActor(id: String) extends Error
