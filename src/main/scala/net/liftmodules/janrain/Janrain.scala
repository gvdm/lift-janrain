package net.liftmodules.janrain

import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder

import net.liftweb.common._
import net.liftweb.http._
import net.liftweb.json.Extraction._
import net.liftweb.json.JsonParser.parse
import net.liftweb.util.Helpers
import net.liftweb.util.Props
import net.liftweb.http.js.JE.JsVar
import net.liftweb.http.js.JsCmds._
import net.liftweb.http.js.JsCmd

import scala.language.implicitConversions
import scala.xml.Unparsed

object Janrain {
  
  case class ServerSideLogin(loginProcess: SigninResponse => Box[LiftResponse])
  case class ClientSideLogin(loginProcess: SigninResponse => JsCmd)
  
  implicit def serverSideLogin(loginProcess: SigninResponse => Box[LiftResponse]) = ServerSideLogin(loginProcess)
  implicit def clientSideLogin(loginProcess: SigninResponse => JsCmd) = ClientSideLogin(loginProcess)

  implicit val formats = net.liftweb.json.DefaultFormats

  val apikey = Props.get("janrain.apikey").openOr("")
  val appName = Props.get("janrain.appname").openOr("")
  
  var clientSide = false
  
  def init (authHandler: ServerSideLogin): Unit = {
    
    LiftRules.dispatch.append {
      // server side callback
      case req @ Req(List("liftmodule", "janrain", "signupr"), _, _) ⇒ {
        val userData: SigninResponse = getLoggedInUserData(S.param("token").openOr(""))
        val userSignResponse = authHandler.loginProcess(userData)
        userSignResponse match {
          case Empty => S.param("page") match {
            case Full(url) => () => Full(new RedirectResponse(URLDecoder.decode(url, "UTF-8"), null))
            case _ => () => Full(new RedirectResponse("/", null))
          }
          case response => () => response
        }
      }
    }
    
    LiftSession.onBeginServicing = addJs _ :: LiftSession.onBeginServicing
  }
  
  def init(authHandler: ClientSideLogin): Unit = {
    
    def ajaxSignin(token: String): JsCmd = {
      val userData: SigninResponse = getLoggedInUserData(token)
      authHandler.loginProcess(userData)
    }
    
    def clientSideJS(s: LiftSession, r: Req): Unit = S.putInHead(
<script type="text/javascript">
function janrainWidgetOnload() {{
  janrain.events.onProviderLoginToken.addHandler(function(response) {{
    { SHtml.ajaxCall(JsVar("response.token"), ajaxSignin(_)).toJsCmd }
  }});
}};
</script>)
    
    clientSide = true
    LiftSession.onBeginServicing = clientSideJS _ :: addJs _ :: LiftSession.onBeginServicing
  }
  
  def addJs(s: LiftSession, r: Req): Unit = S.putInHead(janrainJS())
  
  def janrainJS() = <script type="text/javascript">
(function() {{
  if (typeof window.janrain !== 'object') window.janrain = {{}};
  if (typeof window.janrain.settings !== 'object') window.janrain.settings = {{}};
   
  janrain.settings.appUrl = 'https://{Unparsed(appName)}';
  { if (clientSide) Unparsed("janrain.settings.tokenAction = 'event';")
    else Unparsed("janrain.settings.tokenUrl = '"+S.hostAndPath+"/liftmodule/janrain/signupr?page='+encodeURI(document.URL);") }

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
    
    val userData: SigninResponse = parse(response).extract[SigninResponse]

    userData
  }

}

//TODO: completely specify all possible JSON responses from all the different providers
case class SigninResponse(stat: String, profile: SigninProfile)
case class SigninProfile(email: Option[String], providerName: String, identifier: String, name: Option[SigninName], displayName: Option[String])
case class SigninName(formatted: Option[String], givenName: Option[String], familyName: Option[String])
