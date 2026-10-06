package com.example.api

import java.time.{Clock, Instant}

import redis.clients.jedis.JedisPooled

trait PingLogger:
  def logPing(): Unit
  def checkReady(): Unit

final class RedisPingLogger private (
    redisUrl: String,
    clock: Clock = Clock.systemUTC()
) extends PingLogger,
      AutoCloseable:
  private val client = new JedisPooled(redisUrl)

  override def logPing(): Unit =
    val timestamp = Instant.now(clock).toString
    val entry = s"""{"event":"ping","timestamp":"$timestamp"}"""
    client.rpush(RedisPingLogger.Key, entry)
    ()

  override def checkReady(): Unit =
    client.ping()
    ()

  override def close(): Unit = client.close()

object RedisPingLogger:
  val Key = "ping:logs"

  /** Creates a logger only after Redis has answered PING.
    *
    * Any connection failure is deliberately allowed to escape. The caller
    * therefore cannot start the HTTP server with an unusable dependency.
    */
  def connect(redisUrl: String): RedisPingLogger =
    val logger = new RedisPingLogger(redisUrl)
    try
      logger.checkReady()
      logger
    catch
      case error: Throwable =>
        logger.close()
        throw error
