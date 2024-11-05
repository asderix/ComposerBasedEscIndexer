package example

import esc.index.*
import esc.commons.*
import esc.utils.Persistence.*
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.*
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route.seal
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.http.scaladsl.server.Directives
import spray.json.DefaultJsonProtocol.*
import spray.json.*
import scala.io.StdIn

import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.directives.DebuggingDirectives
import akka.event.Logging

case class Person(id: String, name: String, dob: String)
case class Address(id: String, address: String)
case class AddressResult(id: String, matchResult: String)

trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val personFormat: RootJsonFormat[Person] = jsonFormat3(Person.apply)
  implicit val addressFormat: RootJsonFormat[Address] = jsonFormat2(
    Address.apply
  )
  implicit val addressResultFormat: RootJsonFormat[AddressResult] = jsonFormat2(
    AddressResult.apply
  )
  implicit val addressResultListFormat: RootJsonFormat[List[AddressResult]] =
    listFormat[AddressResult]
}

object HttpServerRoutingMinimal extends Directives with JsonSupport {
  var indexer: Indexer = null
  var finder: Finder = null

  def main(args: Array[String]): Unit = {
    require(args != null && args.length > 0)

    implicit val system =
      ActorSystem(Behaviors.empty, "composer-based-esc-indexer")
    implicit val executionContext = system.executionContext

    try indexer = IndexFactory.openOrCreateIndex(args(0), args(1))
    catch case _: Throwable => indexer = null

    try finder = IndexFactory.openIndexForSearch(args(0), args(1))
    catch case _: Throwable => finder = null

    val route =
      concat(
        path("person" / "search") {
          get {
            parameter("name") { name =>
              {
                finder match
                  case f: Finder =>
                    val matchResult = finder.findPerson(name, List(), List())
                    complete(
                      HttpEntity(
                        ContentTypes.`application/json`,
                        matchResult.toCompactJson
                      )
                    )
                  case null => complete(s"{ \"status\": \"Index not ready\" }")
              }
            }
          }
        },
        path("find-by-address-list") {
          post {
            entity(as[List[Address]]) { input =>
              finder match
                case f: Finder =>
                  var results: List[AddressResult] = List()
                  for (adr: Address <- input) {
                    val result = finder.findByAddress(adr.address)
                    results = results ++ List(
                      AddressResult(adr.id, result.toCompactJson)
                    )
                  }
                  complete(
                    HttpEntity(
                      ContentTypes.`application/json`,
                      results.toJson.prettyPrint
                    )
                  )
                case null => complete(s"{ \"status\": \"Index not ready\" }")
            }
          }
        },
        path("person") {
          post {
            entity(as[Person]) { input =>
              val person = new IndexPerson(
                input.id,
                "extid",
                input.name,
                List(),
                List()
              )
              indexer.addPerson(person)
              indexer.commit()
              finder = IndexFactory.openIndexForSearch(args(0), args(1))
              complete(
                HttpEntity(
                  ContentTypes.`application/json`,
                  s"{ \"status\": \"indexed\" }"
                )
              )
            }
          }
        },
        path("person-batch") {
          post {
            entity(as[List[Person]]) { persons =>              
              persons.foreach { input =>
                val person =
                  new IndexPerson(
                    input.id,
                    "extid",
                    input.name,
                    List(),
                    List()
                  )
                indexer.addPerson(person)
              }
              indexer.commit()
              finder = IndexFactory.openIndexForSearch(args(0), args(1))
              complete(
                HttpEntity(
                  ContentTypes.`application/json`,
                  s"{ \"status\": \"batch indexed\" }"
                )
              )
            }
          }
        }
      )

    val loggedRoute: Route = DebuggingDirectives.logRequestResult("AkkaHTTPLogger", Logging.InfoLevel)(route)

    val bindingFuture = Http().newServerAt("localhost", 8081).bind(loggedRoute)

    println(
      s"Server now online. Please navigate to http://localhost:8081/\nPress RETURN to stop."
    )
    StdIn.readLine()
    bindingFuture
      .flatMap(_.unbind())
      .onComplete(_ => {
        indexer.close()
        system.terminate()
      })
  }
}
