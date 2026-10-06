package com.example.api

import java.time.{Clock, Instant}

import cats.effect.{IO, Resource}
import redis.clients.jedis.JedisPooled

trait PingLogger:
  def logPing(): IO[Unit]
  def checkReady(): IO[Unit]

final class RedisPingLogger private (
    redisUrl: String,
    clock: Clock = Clock.systemUTC()
) extends PingLogger:
  private val client = new JedisPooled(redisUrl)

  override def logPing(): IO[Unit] =
    IO.blocking:
      val timestamp = Instant.now(clock).toString
      val entry = s"""{"event":"ping","timestamp":"$timestamp"}"""
      client.rpush(RedisPingLogger.Key, entry)
      ()

  override def checkReady(): IO[Unit] =
    IO.blocking:
      client.ping()
      ()

  private def close(): IO[Unit] = IO.blocking(client.close())

object RedisPingLogger:
  val Key = "ping:logs"

  def resource(redisUrl: String): Resource[IO, RedisPingLogger] =
    Resource.make(IO.blocking(new RedisPingLogger(redisUrl)))(_.close())
