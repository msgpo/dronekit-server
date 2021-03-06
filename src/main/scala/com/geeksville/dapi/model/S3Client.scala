package com.geeksville.dapi.model

import java.util.Date

import com.geeksville.aws.S3Bucket
import com.geeksville.aws.ConfigCredentials
import com.amazonaws.ClientConfiguration
import com.amazonaws.services.s3.AmazonS3Client
import com.typesafe.config.ConfigFactory

/**
 * The droneshare glue for talking to S3
 */
object S3Client {
  // Not needed - dapi doesn't allow direct user uploads
  // setRules(createExpireRule("upload-expire", "uploads/", 5))
  val credentials = new ConfigCredentials("dapi")

  val config = new ClientConfiguration()
  config.setSocketTimeout(30 * 1000)
  val client = new AmazonS3Client(credentials, config)

  val bucketName = ConfigFactory.load().getString("dapi.s3.bucketName")
  val tlogBucket = new S3Bucket(bucketName, false, client)
  private val backupBucket = new S3Bucket(bucketName + "-backup", false, client)

  // Prerendered map tiles
  // val mapsBucket = new S3Bucket("maps-droneapi", true, client)

  val tlogPrefix = "tlogs/"

  def doBackup(newerThan: Option[Date] = None) =
    tlogBucket.backupTo(backupBucket, newerThan)

}

