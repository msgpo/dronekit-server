/*******************************************************************************
 * Copyright 2013 Kevin Hester
 * 
 * See LICENSE.txt for license details.
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package com.geeksville.aws

import com.amazonaws.services.s3._
import com.amazonaws.services.s3.model._
import com.amazonaws._
import java.io.InputStream
import com.amazonaws.auth.PropertiesCredentials
import sun.misc.BASE64Encoder
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import scala.collection.JavaConverters._
import java.util.TimeZone
import java.text.SimpleDateFormat

class S3Bucket(bucketName: String) {

  val credentials = new ConfigCredentials
  val client = new AmazonS3Client(credentials)

  /// FIXME - move this some place better as a singleton
  val isoDateFormat = {
    val tz = TimeZone.getTimeZone("UTC")
    val df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'")
    df.setTimeZone(tz)
    df
  }

  // At startup make sure our bucket exists
  createBucket()

  /// Return a URL that can be use for reading a file (FIXME, make secure)
  def getReadURL(bucket: String, objKey: String) =
    "https://%s.%s/%s".format(bucketName, objKey)

  def createBucket() {
    // FIXME, support creating buckets in different regions
    println("Creating AWS bucket: " + bucketName)
    client.createBucket(bucketName)
    // makeReadable(name)
  }

  def createExpireRule(id: String, prefix: String, days: Int) = new BucketLifecycleConfiguration.Rule()
    .withId(id)
    .withPrefix(prefix)
    .withExpirationInDays(days)
    .withStatus(BucketLifecycleConfiguration.ENABLED.toString())

  def setRules(rules: BucketLifecycleConfiguration.Rule*) {
    val configuration = new BucketLifecycleConfiguration().withRules(rules.asJava)

    // Save configuration.
    client.setBucketLifecycleConfiguration(bucketName, configuration);
  }

  def makeReadable() {
    // Make bucket readable by default
    // FIXME the following is very insecure - need to instead use cloudfront private keys
    // http://docs.amazonwebservices.com/AmazonS3/latest/API/RESTObjectPUT.html
    val policy = """{
      "Version":"2008-10-17",
      "Statement":[{
        "Sid":"AllowPublicRead",
            "Effect":"Allow",
          "Principal": {
                "AWS": "*"
             },
          "Action":["s3:GetObject"],
          "Resource":["arn:aws:s3:::%s/*"
          ]
        }
      ]
    }""".format(bucketName)
    client.setBucketPolicy(bucketName, policy)
  }

  def plusOneHour = {
    val expiration = new java.util.Date
    // Add 1 hour.
    expiration.setTime(expiration.getTime + 1000 * 60 * 60)
    expiration
  }

  /// Generate a URL which is good for limited time upload permissions
  /// FIXME - better to use cloudfront for reading 
  /// http://docs.amazonwebservices.com/AmazonCloudFront/latest/DeveloperGuide/HowToPrivateContent.html
  def createPresignedURLRequest(objKey: String, mimeType: String) = {
    val expiration = plusOneHour

    val urlReq = new GeneratePresignedUrlRequest(bucketName, objKey)
      .withMethod(HttpMethod.PUT)
      .withBucketName(bucketName)
      .withKey(objKey)
      .withExpiration(expiration)

    // urlReq.addRequestParameter("Content-Type", mimeType)
    urlReq.addRequestParameter("x-amz-storage-class", "REDUCED_REDUNDANCY")

    // Does not seem to work
    // urlReq.addRequestParameter("x-amz-acl", "public-read")

    urlReq
  }

  /**
   * Upload a file to S3
   */
  def uploadStream(key: String, stream: InputStream, mimeType: String, length: Long, highValue: Boolean = false) {
    val metadata = new ObjectMetadata()
    metadata.setContentType(mimeType)
    metadata.setContentLength(length)

    var req = new PutObjectRequest(bucketName, key, stream, metadata)

    if (!highValue)
      req = req.withStorageClass("REDUCED_REDUNDANCY")

    client.putObject(req)
  }

  /**
   * Read from a S3 file
   * You MUST close the returned InputStream, otherwise connections will leak.
   * @param range an optional range of bytes to restrict to reading
   */
  def downloadStream(key: String, range: Option[Pair[Long, Long]] = None) = {
    val req = new GetObjectRequest(bucketName, key)

    range.foreach { case (start, end) => req.setRange(start, end) }

    val obj = client.getObject(req)
    obj.getObjectContent()
  }

  /// Generate a URL which is good for limited time upload permissions
  /// FIXME - better to use cloudfront for reading 
  /// http://docs.amazonwebservices.com/AmazonCloudFront/latest/DeveloperGuide/HowToPrivateContent.html
  def generatePresignedUpload(objKey: String, mimeType: String) = {
    println("Requesting S3 presign %s/%s/%s".format(bucketName, objKey, mimeType))
    val urlReq = createPresignedURLRequest(objKey, mimeType)
    println("S3 presigned request: " + urlReq)
    val u = client.generatePresignedUrl(urlReq)
    println("Generated presigned upload: " + u)
    u
  }

  def copyObject(srcKey: String, destKey: String) = client.copyObject(bucketName, srcKey, bucketName, destKey)

  /**
   * Generate policy -> signature in a form acceptable for HTTP browser upload to S3
   */
  def s3Policy = {

    // |{"success_action_redirect": "http://localhost/"},
    // |{"expiration": "%s", - not needed because we have an expire rule that covers the entire uploads folder
    val policyJson = """
        |{"expiration": "2015-01-01T00:00:00Z",
    	|"conditions": [ 
    	|{"bucket": "%s"}, 
	    |["starts-with", "$key", "uploads/"],
	    |{"acl": "private"},
	    |["starts-with", "$Content-Type", ""],
	    |["content-length-range", 0, 50048576]
	    |]
    	|}""".stripMargin.format(bucketName)

    val policy = (new BASE64Encoder()).encode(policyJson.getBytes("UTF-8")).replaceAll("\n", "").replaceAll("\r", "")

    val awsSecretKey = credentials.getAWSSecretKey

    val hmac = Mac.getInstance("HmacSHA1")
    hmac.init(new SecretKeySpec(
      awsSecretKey.getBytes("UTF-8"), "HmacSHA1"));
    val signature = (new BASE64Encoder()).encode(
      hmac.doFinal(policy.getBytes("UTF-8")))
      .replaceAll("\n", "");

    policy -> signature
  }
}
