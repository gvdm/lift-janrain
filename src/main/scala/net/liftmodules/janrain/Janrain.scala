package net.liftmodules.janrain

import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import scala.xml.Unparsed
import net.liftweb.common._
import net.liftweb.http._
import net.liftweb.json.Extraction._
import net.liftweb.json.JsonParser.parse
import net.liftweb.util.Helpers
import net.liftweb.util.Props
import net.liftweb.http.js.JE._
import net.liftweb.http.js.JsExp
import net.liftweb.http.js.JsCmds._
import net.liftweb.http.js.JsCmd

object Janrain {

  implicit val formats = net.liftweb.json.DefaultFormats

  val apikey = Props.get("janrain.apikey").openOr("")
  val appName = Props.get("janrain.appname").openOr("")
  
  var clientSide = false
  
  def init (authHandler: SigninResponse => Box[LiftResponse]): Unit = {
    
    LiftRules.dispatch.append {
      // server side callback
      case req @ Req(List("liftmodule", "janrain", "signupr"), _, _) ⇒ {
        val userData: SigninResponse = getLoggedInUserData(S.param("token").openOr(""))
        val userSignResponse = authHandler(userData)
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
  
  def init(authHandler: SigninResponse => Unit, siteLoginAction: () => JsCmd, ajaxLoginAction: AnonFunc = AnonFunc(JsReturn(false))): Unit = {

    LiftRules.dispatch.append {
      // client side ajax callback
      case req @ Req(List("liftmodule", "janrain", "engage_callback_url"), _, PostRequest) ⇒ {
        val userData: SigninResponse = getLoggedInUserData(S.param("token").openOr(""))
        authHandler(userData)
        () => Full(JsonResponse(decompose(userData)))
      }
    }
    
    def clientSideJS(s: LiftSession, r: Req): Unit = {
      S.fmapFunc(() => JavaScriptResponse(siteLoginAction())) { funcName => 
      S.putInHead(
<script type="text/javascript">
function janrainWidgetOnload() {{
  janrain.events.onProviderLoginToken.addHandler(function(response) {{
    $.ajax({{
      type: "POST",
      url: "/liftmodule/janrain/engage_callback_url",
      data: "token=" + response.token,
      success: { ajaxLoginAction.toJsCmd },
      complete: function() {{  lift.ajax("{funcName}=_"); }}
    }});
  }});
}};
</script>)}}
    
    clientSide = true
    LiftSession.onBeginServicing = clientSideJS _ :: addJs _ :: LiftSession.onBeginServicing
  }
  
  def addJs(s: LiftSession, r: Req): Unit = S.putInHead(janrainJS())
  
  def janrainJS() = <script type="text/javascript">
(function() {{
  if (typeof window.janrain !== 'object') window.janrain = {{}};
  if (typeof window.janrain.settings !== 'object') window.janrain.settings = {{}};
   
  janrain.settings.tokenUrl = { if (clientSide) Unparsed("'"+S.hostAndPath+"/liftmodule/janrain/engage_callback_url'; janrain.settings.tokenAction = 'event';")
    else Unparsed("'"+S.hostAndPath+"/liftmodule/janrain/signupr?page='+encodeURI(document.URL);") }

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

case class SigninResponse(stat: String, profile: SigninProfile)
case class SigninProfile(email: String, providerName: String, identifier: String, name: SigninName, displayName: String)
case class SigninName(formatted: String, givenName: String, familyName: String)
