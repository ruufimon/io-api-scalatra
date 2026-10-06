package com.example.api

import cats.effect.{IO, IOApp, Resource}
import cats.effect.unsafe.implicits.global
import org.eclipse.jetty.ee10.servlet.{ServletContextHandler, ServletHolder}
import org.eclipse.jetty.server.Server as JettyServer

object Server extends IOApp.Simple:
  private val port = sys.env.get("PORT").flatMap(_.toIntOption).getOrElse(8080)
  private val redisUrl = sys.env.getOrElse("REDIS_URL", "redis://localhost:6379")

  override val run: IO[Unit] =
    RedisPingLogger
      .resource(redisUrl)
      .flatMap(serverResource)
      .use: server =>
        IO.println(s"Scalatra ping API listening on http://localhost:$port") *>
          IO.interruptibleMany(server.join())

  private def serverResource(pingLogger: PingLogger): Resource[IO, JettyServer] =
    Resource.make(
      IO.blocking {
        val server = new JettyServer(port)
        val context = new ServletContextHandler(ServletContextHandler.NO_SESSIONS)

        context.setContextPath("/")
        context.addServlet(new ServletHolder(new PingServlet(pingLogger)), "/*")
        server.setHandler(context)
        server.start()
        server
      }
    )(server => IO.blocking(server.stop()).handleErrorWith(_ => IO.unit))
