package com.example.api

import org.scalatra.test.scalatest.ScalatraFunSuite

class PingServletSpec extends ScalatraFunSuite:
  private class RecordingPingLogger extends PingLogger:
    var calls = 0
    var logging: () => Unit = () => ()
    var readiness: () => Unit = () => ()

    override def logPing(): Unit =
      logging()
      calls += 1

    override def checkReady(): Unit = readiness()

  private val pingLogger = new RecordingPingLogger
  addServlet(new PingServlet(pingLogger), "/*")

  test("GET /ping returns a healthy response"):
    val callsBeforeRequest = pingLogger.calls
    pingLogger.logging = () => ()

    get("/ping"):
      status shouldBe 200
      header("Content-Type") should include("application/json")
      body shouldBe """{"status":"ok"}"""

    pingLogger.calls shouldBe callsBeforeRequest + 1

  test("GET /ping recovers when Redis becomes available without an API restart"):
    val callsBeforeRequest = pingLogger.calls
    pingLogger.logging = () => throw new RuntimeException("Redis unavailable")

    get("/ping"):
      status shouldBe 503
      body shouldBe """{"status":"not_ready"}"""

    pingLogger.calls shouldBe callsBeforeRequest
    pingLogger.logging = () => ()

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
    pingLogger.readiness = () => ()

    get("/ready"):
      status shouldBe 200
      body shouldBe """{"status":"ready"}"""

  test("GET /ready returns 503 when Redis is unavailable"):
    pingLogger.readiness = () => throw new RuntimeException("Redis unavailable")

    get("/ready"):
      status shouldBe 503
      body shouldBe """{"status":"not_ready"}"""
