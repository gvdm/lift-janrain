# Janrain Lift Module

This module enables Janrain integration for your Lift site.

## Using this module

1. Include the dependency:

	```
   "net.liftmodules" %% "janrain_3.0" % "0.5"
	```

   Currently this module is built and published locally for inclusion in your project. Change the Lift and Scala versions as you see fit.

2. Implement a function of type `SignInResponse => JsCmd` (for client side authentication - http://developers.janrain.com/how-to/social-login/deploy-social-login/client-side-authentication/ ) or of type `SignInResponse => Box[LiftResponse]` (for server side authentication) in your user meta model. What follows is an example implementation for a MongoAuth user (using rogue):

	```scala
	def loginOrRegisterUser(userData: SigninResponse): JsCmd = {
	  val userOption: Option[User] =
	    (User where ((u: User) =>
	      u.externId eqs userData.profile.identifier) fetch ()).headOption
	  val user = userOption match {
	    case None    => User.createRecord
	    case Some(u) => u
	  }
	
	  user.externId(userData.profile.identifier).
	    email(userData.profile.email).
	    username(userData.profile.displayName).
	    provider(userData.profile.providerName).
	    firstName(userData.profile.name.givenName).
	    lastName(userData.profile.name.familyName).
	    verified(true).
	    save()
	
	  User.logUserIn(user, true, true) //set user in the session
	  Alert("Hello "+userData.profile.name.givenName)
	}
	```
	
	or similarly
	
	```scala
	def loginOrRegisterUser(userData: SigninResponse): Box[LiftResponse] = {
	  
	  ...
	
	  User.logUserIn(user, true, true) //set user in the session
	  // redirect to user home page
	  Full(new RedirectResponse("/user/"+username.value, null))
	}
	```
	The server side authentication handles how to respond to logins once Janrain returns back to Lift. Providing an Empty response uses the default provided by the module which redirects the user back to the page they initiated the login from as per the following code (taken from `Janrain.init()`):
	
	```scala
	S.param("page") match {
            case Full(url) => () => Full(new RedirectResponse(URLDecoder.decode(url, "UTF-8"), null))
            case _ => () => Full(new RedirectResponse("/", null))
          }
	```

3. Call `net.liftmodules.janrain.Janrain.init` in `Boot.scala` with the implementing function as the parameter, eg: `Janrain.init(User.loginOrRegisterUser _)`.

4. Add the following to `src/main/resources/props/default.props` (and other props files) to configure your API key and janrain application name:

	```
	janrain.apikey=XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX
	janrain.appname=app.name.com
	```

5. Add `<div id="janrainEngageEmbed"></div>` to your template.

