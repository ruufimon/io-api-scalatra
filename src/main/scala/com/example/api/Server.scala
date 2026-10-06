package com.example.api

import org.eclipse.jetty.ee11.servlet.{ServletContextHandler, ServletHolder}
import org.eclipse.jetty.server.Server as JettyServer

import scala.util.Using

object Server:
  private val port = sys.env.get("PORT").flatMap(_.toIntOption).getOrElse(8080)
  private val redisUrl = sys.env.getOrElse("REDIS_URL", "redis://localhost:6379")

  def main(args: Array[String]): Unit =
    // connect performs PING and throws before Jetty is constructed on failure.
    Using.resource(RedisPingLogger.connect(redisUrl)): pingLogger =>
      val server = createServer(pingLogger)
      server.setStopAtShutdown(true)
      try
        server.start()
        println(s"Scalatra ping API listening on http://localhost:$port")
        server.join()
      finally
        if server.isStarted then server.stop()

  private def createServer(pingLogger: PingLogger): JettyServer =
    val server = new JettyServer(port)
    val context = new ServletContextHandler(ServletContextHandler.NO_SESSIONS)

    context.setContextPath("/")
    context.addServlet(new ServletHolder(new PingServlet(pingLogger)), "/*")
    server.setHandler(context)
    server
