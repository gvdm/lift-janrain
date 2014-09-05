# Janrain Lift Module

This module enables Janrain integration for your Lift site.

## Using this module

1. Include the dependency:

```
   "net.liftmodules" %% "janrain_3.0" % "0.1-SNAPSHOT"
```

2. Implement `net.liftmodules.janrain.JanrainUser` for your user model, what follows is an example implementation for a MongoAuth user (using rogue):

```scala
object MongoAuthJanrain extends JanrainUser {
  def loginOrRegisterUser(userData: SigninResponse) = {
    val userOption: Option[User] = (User where ((u: User) => u.externId eqs userData.profile.identifier) fetch ()).headOption
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
  }
}
```

3. Call `net.liftmodules.janrain.Janrain.init` in `Boot.scala` with the implementing object as the parameter eg:

   	`Janrain.init(MongoAuthJanrain)`

4. Add `<div id="janrainEngageEmbed"></div>` to your template.

