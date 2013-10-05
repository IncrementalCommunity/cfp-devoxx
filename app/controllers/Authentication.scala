/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2013 Association du Paris Java User Group.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package controllers

import models._
import play.api.mvc._
import scala.concurrent.ExecutionContext
import ExecutionContext.Implicits.global
import play.api.Logger
import play.api.Play
import play.api.libs.Crypto
import play.api.data.Form
import play.api.data.Forms._
import play.api.libs.ws.WS
import play.api.libs.json.Json
import org.apache.commons.lang3.RandomStringUtils
import reactivemongo.core.commands.LastError
import reactivemongo.bson.BSONObjectID
import play.api.data.format.Formats._
/**
 * Signup and Signin.
 *
 * Author: nicolas
 * Created: 27/09/2013 09:59
 */
object Authentication extends Controller {
  val loginForm = Form(tuple("email" -> nonEmptyText, "password" -> nonEmptyText))

  def login = Action {
    implicit request =>
      loginForm.bindFromRequest.fold(
        invalidForm => BadRequest(views.html.Application.index(invalidForm)),
        validForm => Async {
          Webuser.checkPassword(validForm._1, validForm._2).map {
            validUser =>
              if (validUser) {
                Redirect(routes.CallForPaper.homeForSpeaker).withSession("email" -> validForm._1)
              } else {
                Unauthorized("User not found")
              }
          }
        }
      )
  }

  def githubLogin = Action {
    implicit request =>
      Play.current.configuration.getString("github.client_id").map {
        clientId: String =>
          val redirectUri = routes.Authentication.callbackGithub.absoluteURL()
          val gitUrl = "https://github.com/login/oauth/authorize?client_id=" + clientId + "&scope=user&state=" + Crypto.sign("ok") + "&redirect_uri=" + redirectUri
          Redirect(gitUrl)
      }.getOrElse {
        InternalServerError("github.client_id is not set in application.conf")
      }
  }

  //POST https://github.com/login/oauth/access_token
  val oauthForm = Form(tuple("code" -> text, "state" -> text))
  val accessTokenForm = Form("access_token" -> text)

  def callbackGithub = Action {
    implicit request =>
      oauthForm.bindFromRequest.fold(invalidForm => {
        BadRequest(views.html.Application.index(loginForm)).flashing("error" -> "Invalid form")
      }, validForm => {
        validForm match {
          case (code, state) if state == Crypto.sign("ok") => {
            val auth = for (clientId <- Play.current.configuration.getString("github.client_id");
                            clientSecret <- Play.current.configuration.getString("github.client_secret")) yield (clientId, clientSecret)
            auth.map {
              case (clientId, clientSecret) => {
                val url = "https://github.com/login/oauth/access_token"
                val wsCall = WS.url(url).post(Map("client_id" -> Seq(clientId), "client_secret" -> Seq(clientSecret), "code" -> Seq(code)))
                Async {
                  wsCall.map {
                    result =>
                      result.status match {
                        case 200 => {
                          val b = result.body
                          try {
                            val accessToken = b.substring(b.indexOf("=") + 1, b.indexOf("&"))

                            Redirect(routes.Authentication.createFromGithub).withSession("access_token" -> accessToken)
                          } catch {
                            case e: IndexOutOfBoundsException => {
                              play.Logger.error("Access token not found in query string")
                              Redirect(routes.Application.index)
                            }
                          }
                        }
                        case _ => {
                          play.Logger.error("Could not complete Github OAuth")
                          Redirect(routes.Application.index)
                        }
                      }
                  }
                }
              }
            }.getOrElse {
              InternalServerError("github.client_secret is not configured in application.conf")
            }
          }
          case other => BadRequest(views.html.Application.index(loginForm)).flashing("error" -> "Invalid state code")
        }
      })
  }

  def showAccessToken = Action {
    implicit request =>
      Ok(request.session.get("access_token").getOrElse("No access token in your current session"))
  }

  val newWebuserForm: Form[Webuser] = Form(
    mapping(
      "email" -> nonEmptyText,
      "firstName" -> nonEmptyText,
      "lastName" -> nonEmptyText
    )(Webuser.createSpeaker)(Webuser.unapplyForm))

  val webuserForm = Form(
    mapping(
      "id" -> optional(of[String]),
      "email" -> nonEmptyText,
      "firstName" -> nonEmptyText,
      "lastName" -> nonEmptyText,
      "password" -> nonEmptyText,
      "profile" -> nonEmptyText
    ) { (id, email, firstName, lastName, password, profile) =>
      Webuser(
        id.map(new BSONObjectID(_)),
        email,
        firstName,
        lastName,
        password,
        profile)
    } { w =>
      Some(
        (w.id.map(_.stringify),
        w.email,
        w.firstName,
        w.lastName,
        w.password,
        w.profile))
    }
  )
  val speakerForm = Form(mapping(
    "email" -> nonEmptyText,
    "bio" -> nonEmptyText(maxLength = 500),
    "lang" -> optional(text),
    "twitter" -> optional(text),
    "company" -> optional(text),
    "blog" -> optional(text)
  )(Speaker.createSpeaker)(Speaker.unapplyForm))

  def createFromGithub = Action {
    implicit request =>
      val url = "https://api.github.com/user?access_token=" + request.session.get("access_token").getOrElse("")
      val futureResult = WS.url(url).withHeaders("User-agent" -> "nicmarti devoxxfr", "Accept" -> "application/json").get()
      val lang = request.headers.get("Accept-Language")
      Async {
        futureResult.map {
          result =>
            result.status match {
              case 200 => {
                //                Ok(result.body).as("application/json")
                val json = Json.parse(result.body)
                val resultParse = (for (email <- json.\("email").asOpt[String].toRight("email not found").right;
                                        name <- json.\("name").asOpt[String].toRight("name not found").right;
                                        bio <- json.\("bio").asOpt[String].toRight("bio not found").right) yield (email, name, bio))

                resultParse.fold(missingField => BadRequest("Sorry, cannot import your github profile due to : [" + missingField + "]"),
                  validFields => {
                    validFields match {
                      case (emailS, nameS, bioS) =>

                        val avatarUrl = json.\("avatar_url").asOpt[String]
                        val company = json.\("company").asOpt[String]
                        val blog = json.\("blog").asOpt[String]

                        // Try to lookup the speaker
                        Async {
                          Webuser.findByEmail(emailS).map {
                            maybeWebuser =>
                              maybeWebuser.map {
                                w =>
                                  Redirect(routes.CallForPaper.homeForSpeaker).withSession("email" -> w.email)
                              }.getOrElse {
                                // Create a new one but ask for confirmation
                                val (firstName, lastName) = if (nameS.indexOf(" ") != -1) {
                                  (nameS.substring(0, nameS.indexOf(" ")), nameS.substring(nameS.indexOf(" ") + 1))
                                } else {
                                  (nameS, nameS)
                                }
                                val w = Webuser(Option(BSONObjectID.generate), emailS, firstName, lastName, RandomStringUtils.randomAlphanumeric(7), "speaker")
                                val s = Speaker(Option(BSONObjectID.generate), emailS, bioS, lang, None, avatarUrl, company, blog)
                                Ok(views.html.Authentication.confirmImport(newWebuserForm.fill(w), speakerForm.fill(s)))
                              }
                          }
                        }
                    }
                  })

              }
              case other => {
                play.Logger.error("Unable to complete call " + result.status + " " + result.statusText + " " + result.body)
                BadRequest("Unable to complete the Github User API call")
              }
            }
        }
      }
  }


  def saveNewSpeaker = Action {
    implicit request =>
      newWebuserForm.bindFromRequest.fold(
        invalidForm => BadRequest(views.html.Application.newUser(invalidForm)),
        validForm => Async {
          Webuser.save(validForm).map {
            _ =>
              Created(views.html.Application.created(validForm.email))
          }.recover {
            case LastError(ok, err, code, errMsg, originalDocument, updated, updatedExisting) =>
              Logger.error("Mongo error, ok: " + ok + " err: " + err + " code: " + code + " errMsg: " + errMsg)
              if (code.get == 11000) Conflict("Email already exists") else InternalServerError("Could not create speaker.")
            case other => {
              Logger.error("Unknown Error " + other)
              InternalServerError("Unknown MongoDB Error")
            }
          }
        }
      )
  }

}