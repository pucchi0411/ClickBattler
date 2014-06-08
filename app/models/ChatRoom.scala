package models

import akka.actor._
import akka.pattern.ask
import akka.util.Timeout
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.collection.mutable.Set

import play.api.Play.current
import play.api.libs.concurrent.Execution.Implicits._

import play.api._
import play.api.libs.json._
import play.api.libs.iteratee._
import play.api.libs.concurrent._
import scala.concurrent.Future

case class Join(userName: String)

case class Quit(userName: String)

case class Talk(userName: String, text: String)

case class NotifyJoin(userName: String)

case class Connected(enumerator: Enumerator[JsValue])

case class CannotConnect(msg: String)


object ChatRoom {

  implicit val timeout = Timeout(10 second)

  lazy val default = {
    Akka.system.actorOf(Props[ChatRoom])
  }

  def join(userName: String): Future[(Iteratee[JsValue, _], Enumerator[JsValue])] = {
    (default ? Join(userName)).map {
      case Connected(enumerator) =>
        val iteratee = Iteratee.foreach[JsValue] {
          event =>
            default ! Talk(userName, (event \ "text").as[String])
        }.map {
          _ =>
            default ! Quit(userName)
        }

        (iteratee, enumerator)

      case CannotConnect(error) =>
        val iteratee = Done[JsValue, Unit]((), Input.EOF)

        val enumerator = Enumerator[JsValue](
          JsObject(Seq("error" -> JsString(error)))
        ).andThen(Enumerator.enumInput(Input.EOF))

        (iteratee, enumerator)
    }
  }

}

case class User(name: String, var clickCount: Long)

class ChatRoom extends Actor {

  var members = Set.empty[User]
  val (chatEnumerator, chatChannel) = Concurrent.broadcast[JsValue]

  def receive = {

    case Join(userName) => {
      if (members.exists(_.name == userName)) {
        sender ! CannotConnect("このユーザー名は使用されています.")
      } else if (userName.isEmpty) {
        sender ! CannotConnect("このユーザー名は使用できません.")
      } else {
        members.add(User(userName, 0))
        sender ! Connected(chatEnumerator)
        self ! NotifyJoin(userName)
      }
    }

    case NotifyJoin(userName) => {
      notifyAll("join", User(userName, 0), "が入室しました.")
    }

    case Talk(userName, message) => {
      members.find(_.name == userName).foreach{ u =>
        val newU = User(u.name, u.clickCount + 1)
        members.add(newU)
        members -= u
        notifyAll("talk", newU, message)
      }
    }

    case Quit(userName) => {
      members.find(_.name == userName).foreach {
        u =>
          members - u
          notifyAll("quit", u, "が退出しました.")
      }
    }

  }

  def notifyAll(kind: String, user: User, text: String): Unit = {
    val msg = JsObject(
      Seq(
        "kind" -> JsString(kind),
        "user" -> JsString(user.name),
        "message" -> JsString(text),
        "count" ->JsNumber(user.clickCount),
        "members" -> JsArray(
          members.toList.sortBy(_.clickCount).reverse.map {
            m =>
              JsObject(
                Seq(
                  "name" -> JsString(m.name),
                  "count" -> JsNumber(m.clickCount)
                )
              )
          }
        )
      )
    )
    chatChannel.push(msg)
  }

}
