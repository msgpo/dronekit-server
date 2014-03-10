package com.geeksville.dapi.model

import com.github.aselab.activerecord._
import com.github.aselab.activerecord.dsl._
import com.github.aselab.activerecord.scalatra._

object Tables extends ActiveRecordTables with ScalatraSupport {
  val vehicles = table[Vehicle]
  val missions = table[Mission]
  val users = table[User]

  override def initialize(config: Map[String, Any]) {
    super.initialize(config)

    // FIXME - don't always reseed
    //transaction {
    val u = User("Tester Bob", "test-bob@3drobotics.com").create
    u.password = "sekrit"
    u.save()

    val v = Vehicle("Test Plane").create
    u.vehicles += v

    u.save()
    //}
  }
}