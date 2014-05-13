/*
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

import models.{Event, Speaker, RequestToTalk}
import library.{NewRequestToTalk, ZapActor}

/**
 * Controller to handle the list of invited speakers.
 * A CFP Member can create a RequestToTalk, this will send an email to the speaker.
 * If the speaker accepts, we change the status of the RTT, and let him create an account if needed.
 * If the speaker declines, then we set the RTT status to Declined.
 *
 * Created by nmartignole on 12/05/2014.
 */
object Wishlist extends SecureCFPController {

  def homeWishlist() = SecuredAction(IsMemberOf("cfp")) {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
      Ok(views.html.Wishlist.homeWishList())
  }

  def newRequestToTalk() = SecuredAction(IsMemberOf("cfp")) {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
      Ok(views.html.Wishlist.newRequestToTalk(RequestToTalk.newRequestToTalkForm))
  }

  def saveRequestToTalk() = SecuredAction(IsMemberOf("cfp")) {
    implicit request: SecuredRequest[play.api.mvc.AnyContent] =>
    RequestToTalk.newRequestToTalkForm.bindFromRequest().fold(
        hasErrors => BadRequest(views.html.Wishlist.newRequestToTalk(hasErrors)),
        successForm => {

          ZapActor.actor ! NewRequestToTalk(successForm)

          Redirect(routes.Wishlist.homeWishlist()).flashing("success"->"Request created, email has been sent to the speaker")
        }
      )
  }
}