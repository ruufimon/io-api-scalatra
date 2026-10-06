package com.example.api

import cats.effect.unsafe.IORuntime
import org.scalatra.ScalatraServlet

class PingServlet(pingLogger: PingLogger)(using runtime: IORuntime)
    extends ScalatraServlet:
  get("/ping"):
    pingLogger.logPing().attempt.unsafeRunSync() match
      case Right(_) => jsonResponse(200, """{"status":"ok"}""")
      case Left(_)  => jsonResponse(503, """{"status":"not_ready"}""")

  get("/live"):
    jsonResponse(200, """{"status":"live"}""")

  get("/ready"):
    pingLogger.checkReady().attempt.unsafeRunSync() match
      case Right(_) => jsonResponse(200, """{"status":"ready"}""")
      case Left(_)  => jsonResponse(503, """{"status":"not_ready"}""")

  private def jsonResponse(statusCode: Int, body: String): String =
    status = statusCode
    contentType = "application/json"
    body
