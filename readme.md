# user-management-lagom

This project is an example of implementation for a basic user management using Lagom Framework 1.4.5.

## Get started

```git
git clone https://github.com/botekchristophe/user-management-lagom.git
```

```
cd user-management-lagom
```

```
sbt runAll
```

```curl
curl -X POST \
  http://localhost:9000/api/users \
  -d '{
	"username":"foo",
	"password": "barbarbar"
  }'
```

```curl
curl -X PUT http://localhost:90000/api/users/{{id}}/verify
```

## API

```scala
  def descriptor: Descriptor = {
    import Service._
    named("user").withCalls(
      restCall(Method.GET,    "/api/users",              getUsers _),
      restCall(Method.POST,   "/api/users",              createUser _),
      restCall(Method.GET,    "/api/users/:id",          getUser _),
      restCall(Method.DELETE, "/api/users/:id",          deleteUser _),
      restCall(Method.PUT,    "/api/users/:id/verify",   verifyUser _),
      restCall(Method.PUT,    "/api/users/:id/unverify", unVerifyUser _),
      restCall(Method.POST,   "/api/users/auth",         getUserAuth _),
      restCall(Method.POST,   "/api/users/auth/grant",   userLogin _),
      restCall(Method.POST,   "/api/users/auth/revoke",  revokeToken _),
      restCall(Method.POST,   "/api/users/auth/refresh", refreshToken _)
    )
  }
```

## Persistence entity

![Image of User FSM](https://raw.githubusercontent.com/botekchristophe/user-management-lagom/master/UserFSM.png)

## ReadSide - Cassandra

In order to demonstrate Cassandra Readside with Lagom, two tables are being updated by processing the event stream.

### users table

| id    | username | status     |  email |
|-------|----------|------------|--------|
| $UUID | john     | VERIFIED   | $email |
| $UUID | Maddie   | UNVERIFIED | $email |

### sessions table

| access_token | refresh_token |   userid   |
|--------------|---------------|------------|
|     $UUID    |     $UUID     |    $UUID   |
|     $UUID    |     $UUID     |    $UUID   |


### Model evolution

An example of model evolution can be found in UserEntity

```scala
object UserSerializerRegistry extends JsonSerializerRegistry {
  override def serializers = ...

  private val emailAdded = new JsonMigration(2) {
    override def transform(fromVersion: Int, json: JsObject): JsObject = {
      if (fromVersion < 2) {
        json + ("email" -> JsString("example@company.ca"))
      } else {
        json
      }
    }
  }

  override def migrations = Map[String, JsonMigration](
    classOf[CreateUser].getName -> emailAdded,
    classOf[UserCreated].getName -> emailAdded,
    classOf[UserAggregate].getName -> emailAdded
  )
}
```


## Further work

### error management
If I have the time I will remove all the error thrown and use Eithers.
The model would look like that:

```
Either[Error, T]
```

### unit testing
The unit test kit provided by Lagom are really interesting.
