package com.example.api

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import org.scalatra.test.scalatest.ScalatraFunSuite

class PingServletSpec extends ScalatraFunSuite:
  private class RecordingPingLogger extends PingLogger:
    var calls = 0
    var logging: IO[Unit] = IO.unit
    var readiness: IO[Unit] = IO.unit

    override def logPing(): IO[Unit] = logging *> IO(calls += 1)
    override def checkReady(): IO[Unit] = readiness

  private val pingLogger = new RecordingPingLogger
  addServlet(new PingServlet(pingLogger), "/*")

  test("GET /ping returns a healthy response"):
    val callsBeforeRequest = pingLogger.calls
    pingLogger.logging = IO.unit

    get("/ping"):
      status shouldBe 200
      header("Content-Type") should include("application/json")
      body shouldBe """{"status":"ok"}"""

    pingLogger.calls shouldBe callsBeforeRequest + 1

  test("GET /ping recovers when Redis becomes available without an API restart"):
    val callsBeforeRequest = pingLogger.calls
    pingLogger.logging = IO.raiseError(new RuntimeException("Redis unavailable"))

    get("/ping"):
      status shouldBe 503
      body shouldBe """{"status":"not_ready"}"""

    pingLogger.calls shouldBe callsBeforeRequest
    pingLogger.logging = IO.unit

    get("/ping"):
      status shouldBe 200
      body shouldBe """{"status":"ok"}"""

    pingLogger.calls shouldBe callsBeforeRequest + 1

  test("unknown routes return 404"):
    get("/missing"):
      status shouldBe 404

  test("GET /live reports liveness without calling Redis"):
    val callsBeforeRequest = pingLogger.calls

    get("/live"):
      status shouldBe 200
      body shouldBe """{"status":"live"}"""

    pingLogger.calls shouldBe callsBeforeRequest

  test("GET /ready reports readiness when Redis is available"):
    pingLogger.readiness = IO.unit

    get("/ready"):
      status shouldBe 200
      body shouldBe """{"status":"ready"}"""

  test("GET /ready returns 503 when Redis is unavailable"):
    pingLogger.readiness = IO.raiseError(new RuntimeException("Redis unavailable"))

    get("/ready"):
      status shouldBe 503
      body shouldBe """{"status":"not_ready"}"""
