// todo there is some issue with the dependencies.
//package com.swissborg.akkainspection.manager
//
//import com.swissborg.akkainspection.laws.arbitrary._
//import monocle.law.discipline
//import org.scalatest.FunSuite
////import org.scalatest.funsuite.AnyFunSuite
//import org.typelevel.discipline.scalatest.Discipline
//
//class EventsSpec extends FunSuite with Discipline {
//  checkAll("FragmentIdsRequest", discipline.IsoTests(FragmentIdsRequest.grpcIso))
//  checkAll("InspectableActorsRequest", discipline.IsoTests(InspectableActorsRequest.grpcIso))
//  checkAll("FragmentIdsResponse", discipline.PrismTests(FragmentIdsResponse.grpcPrism))
//}
