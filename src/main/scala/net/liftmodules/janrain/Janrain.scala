package net.liftmodules.janrain

import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import scala.xml.Unparsed
import net.liftweb.common.Box
import net.liftweb.http.LiftRules
import net.liftweb.http.LiftRulesMocker.toLiftRules
import net.liftweb.http.LiftSession
import net.liftweb.http.RedirectResponse
import net.liftweb.http.Req
import net.liftweb.http.S
import net.liftweb.http.provider.servlet.HTTPRequestServlet
import net.liftweb.json.JsonParser.parse
import net.liftweb.util.Helpers
import net.liftweb.util.Props
import net.liftweb.common.Full

object Janrain {

  val apikey = Props.get("janrain.apikey").openOr("")
  val appName = Props.get("janrain.appname").openOr("")
  
  def janrainJS = <script type="text/javascript">
(function() {{
   if (typeof window.janrain !== 'object') window.janrain = {{}};
   if (typeof window.janrain.settings !== 'object') window.janrain.settings = {{}};
   
   janrain.settings.tokenUrl = {Unparsed("'"+S.hostAndPath)}/signupr';

    function isReady() {{ janrain.ready = true; }};
   if (document.addEventListener) {{
     document.addEventListener("DOMContentLoaded", isReady, false);
   }} else {{
     window.attachEvent('onload', isReady);
   }}

    var e = document.createElement('script');
   e.type = 'text/javascript';
   e.id = 'janrainAuthWidget';

    if (document.location.protocol === 'https:') {{
     e.src = 'https://rpxnow.com/js/lib/{Unparsed(appName)}/engage.js';
   }} else {{
     e.src = 'http://widget-cdn.rpxnow.com/js/lib/{Unparsed(appName)}/engage.js';
   }}

    var s = document.getElementsByTagName('script')[0];
   s.parentNode.insertBefore(e, s);
}})();
                  </script>

  def init(authHandler: SigninResponse => Unit) {
    LiftRules.dispatch.append {
      case req @ Req(List("signupr"), _, _) ⇒
        val userData: SigninResponse = getLoggedInUserData(S.param("token").openOr(""))
        authHandler(userData)
        () => Full(new RedirectResponse("/", null))
    }
    
    def addJs(s: LiftSession, r: Req): Unit = S.putInHead(janrainJS)
    LiftSession.onBeginServicing = addJs _ :: LiftSession.onBeginServicing
  }

  def getLoggedInUserData(token: String): SigninResponse = {
    val query = Map(
      "apiKey" -> apikey,
      "token" -> token
    )

    val queryStr = query.map(pair ⇒ pair._1 + "=" + pair._2).reduce(_ + "&" + _)

    val url = new URL("https://rpxnow.com/api/v2/auth_info")
    val conn: HttpURLConnection = url.openConnection().asInstanceOf[HttpURLConnection]
    conn.setRequestMethod("POST");
    conn.setDoOutput(true)
    conn.connect()
    val osw = new OutputStreamWriter(conn.getOutputStream(), "UTF-8")
    osw.write(queryStr)
    osw.close()

    val response = new String(Helpers.readWholeStream(conn.getInputStream()))

    implicit val formats = net.liftweb.json.DefaultFormats //need this for extract to work
    val userData: SigninResponse = parse(response).extract[SigninResponse]

    userData
  }

}

case class SigninResponse(stat: String, profile: SigninProfile)
case class SigninProfile(email: String, providerName: String, identifier: String, name: SigninName, displayName: String)
case class SigninName(formatted: String, givenName: String, familyName: String)
