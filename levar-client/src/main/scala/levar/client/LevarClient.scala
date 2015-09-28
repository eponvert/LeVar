package levar.client

import levar._
import levar.json._
import play.api.Play.current
import play.api.libs.ws._
import play.api.libs.ws.ning.NingAsyncHttpClientConfigBuilder
import play.api.libs.json._
import scala.concurrent.Future
import scala.concurrent.duration.Duration.Inf
import org.joda.time.DateTime

object LevarClient {

  val knownHttpErrs = Map(
    400 -> "Bad Request",
    401 -> "Unauthorized",
    402 -> "Payment Required",
    403 -> "Forbidden",
    404 -> "Not Found",
    405 -> "Method Not Allowed",
    406 -> "Not Acceptable",
    407 -> "Proxy Authentication Required",
    408 -> "Request Timeout",
    409 -> "Conflict",
    410 -> "Gone",
    411 -> "Length Required",
    412 -> "Precondition Failed",
    413 -> "Payload Too Large",
    414 -> "Request-URI Too Long",
    415 -> "Unsupported Media Type",
    416 -> "Requested Range Not Satisfiable",
    417 -> "Expectation Failed",
    418 -> "I'm a teapot",
    419 -> "Authentication Timeout",
    420 -> "Method Failure",
    420 -> "Enhance Your Calm",
    421 -> "Misdirected Request",
    422 -> "Unprocessable Entity",
    423 -> "Locked",
    424 -> "Failed Dependency",
    426 -> "Upgrade Required",
    428 -> "Precondition Required",
    429 -> "Too Many Requests",
    431 -> "Request Header Fields Too Large",
    440 -> "Login Timeout",
    444 -> "No Response",
    449 -> "Retry With",
    450 -> "Blocked by Windows Parental Controls",
    451 -> "Unavailable For Legal Reasons",
    451 -> "Redirect",
    494 -> "Request Header Too Large",
    495 -> "Cert Error",
    496 -> "No Cert",
    497 -> "HTTP to HTTPS",
    498 -> "Token expired/invalid",
    499 -> "Client Closed Request",
    499 -> "Token required",
    500 -> "Internal Server Error",
    501 -> "Not Implemented",
    502 -> "Bad Gateway",
    503 -> "Service Unavailable",
    504 -> "Gateway Timeout",
    505 -> "HTTP Version Not Supported",
    506 -> "Variant Also Negotiates",
    507 -> "Insufficient Storage",
    508 -> "Loop Detected",
    509 -> "Bandwidth Limit Exceeded",
    510 -> "Not Extended",
    511 -> "Network Authentication Required",
    520 -> "Unknown Error",
    522 -> "Origin Connection Time-out",
    598 -> "Network read timeout error",
    599 -> "Network connect timeout error")
}

class LevarClient(val config: ClientConfig) {

  import LevarClient.knownHttpErrs

  implicit val context = play.api.libs.concurrent.Execution.Implicits.defaultContext

  private val builder = new com.ning.http.client.AsyncHttpClientConfig.Builder()
  private val client = new play.api.libs.ws.ning.NingWSClient(builder.build())

  private def post[A](path: String, body: A)(implicit f: Writes[A]): Future[Unit] =
    client.url(config.url + path)
      .withAuth(config.username, config.password, WSAuthScheme.BASIC)
      .withHeaders("Accept" -> "text/plain")
      .post(Json.toJson(body))
      .map { response =>
        if (response.status != 200) {
          if (response.body.nonEmpty)
            throw new ConnectionError(s"${response.status} ${response.body}")
          else if (response.statusText.nonEmpty)
            throw new ConnectionError(s"${response.status} ${response.statusText}")
          else {
            val msg = knownHttpErrs.getOrElse(response.status, "unknown")
            throw new ConnectionError(s"err=$response $msg")
          }
        }
      }

  private def delete(path: String): Future[Unit] =
    client.url(config.url + path)
      .withAuth(config.username, config.password, WSAuthScheme.BASIC)
      .delete()
      .map { response =>
        if (response.status != 200) {
          if (response.body.nonEmpty)
            throw new ConnectionError(s"${response.status} ${response.body}")
          else if (response.statusText.nonEmpty)
            throw new ConnectionError(s"${response.status} ${response.statusText}")
          else {
            val msg = knownHttpErrs.getOrElse(response.status, "unknown")
            throw new ConnectionError(s"err=$response $msg")
          }
        }
      }

  private def get[A](path: String, query: (String, String)*)(implicit f: Reads[A]): Future[A] = {
    client.url(config.url + path)
      .withAuth(config.username, config.password, WSAuthScheme.BASIC)
      .withQueryString(query: _*)
      .withHeaders("Accept" -> "application/json")
      .get()
      .map { response =>
        if (response.status == 200) response.json.as[A]
        else {
          val msg = if (response.statusText.nonEmpty) {
            response.statusText
          } else {
            knownHttpErrs.getOrElse(response.status, "unknown error")
          }
          throw new ConnectionError(s"{$response.status} - $msg")
        }
      }
  }

  def searchDatasets(org: String): Future[ResultSet[Dataset]] =
    get[ResultSet[Dataset]](s"/api/$org/datasets")

  def createDataset(ds: Dataset, orgOpt: Option[String] = None): Future[Unit] = {
    val org = orgOpt.getOrElse(config.org)
    post[Dataset](s"/api/$org/dataset", ds)
  }

  def getDataset(orgOpt: Option[String], id: String): Future[Dataset] = {
    val org = orgOpt.getOrElse(config.org)
    get[Dataset](s"/api/$org/dataset/$id")
  }

  def uploadDatasetData(ds: String, data: Seq[Datum], orgOpt: Option[String] = None): Future[Unit] = {
    val org = orgOpt.getOrElse(config.org)
    val upd = new Dataset.Update(data = Some(data))
    post[Dataset.Update](s"/api/$org/dataset/$ds", upd)
  }

  def deleteDataset(org: String, id: String): Future[Unit] = {
    delete(s"/api/$org/dataset/$id")
  }
}
