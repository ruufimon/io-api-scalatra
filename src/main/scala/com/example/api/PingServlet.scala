package com.example.api

import org.scalatra.ScalatraServlet

class PingServlet(pingLogger: PingLogger) extends ScalatraServlet:
  get("/ping"):
    try
      pingLogger.logPing()
      jsonResponse(200, """{"status":"ok"}""")
    catch
      case _: RuntimeException =>
        jsonResponse(503, """{"status":"not_ready"}""")

  get("/live"):
    jsonResponse(200, """{"status":"live"}""")

  get("/ready"):
    try
      pingLogger.checkReady()
      jsonResponse(200, """{"status":"ready"}""")
    catch
      case _: RuntimeException =>
        jsonResponse(503, """{"status":"not_ready"}""")

  private def jsonResponse(statusCode: Int, body: String): String =
    status = statusCode
    contentType = "application/json"
    body
