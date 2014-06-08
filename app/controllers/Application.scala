package controllers

import play.api._
import play.api.mvc._
import play.api.libs.iteratee.{Enumerator, Iteratee}
import play.api.libs.json.JsValue
import models.ChatRoom

object Application extends Controller {

  def index = Action { implicit request =>
    Ok(views.html.index())
  }

  def chatRoom(userName: Option[String]) = Action { implicit request =>
    userName.map {user =>
      Ok(views.html.chatRoom(user))
    }.getOrElse{
      Redirect(routes.Application.index).flashing(
        "error" -> "名前は必須です."
      )
    }
  }

  def chatRoomJs(userName:String) = Action {implicit request =>
    Ok(views.js.chatRoom(userName))
  }

  def chat(userName:String) = WebSocket.async[JsValue] { request =>
    ChatRoom.join(userName)
  }

}