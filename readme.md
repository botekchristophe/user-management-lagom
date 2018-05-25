# user-management-lagom

Basic implementation of a user management service using :
* Lagom 1.4.5
* Scala 2.12.6
* JBcrypt
* Cassandra
* Kafka

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
curl -X POST http://localhost:9000/api/users \
  -d '{
	"username":"foo",
	"email": "foo@bar.com",
	"password": "barbarbar"
  }'
```


## User service

### API

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

### Persistence entity

![Image of User FSM](https://raw.githubusercontent.com/botekchristophe/user-management-lagom/master/UserFSM.png)

### ReadSide - Cassandra

In order to demonstrate Cassandra Readside with Lagom, two tables are being updated by processing the event stream.

#### users table

| id    | username | status     |  email |
|-------|----------|------------|--------|
| $UUID | john     | VERIFIED   | $email |
| $UUID | Maddie   | UNVERIFIED | $email |

#### sessions table

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

## Email service

### API

```scala
trait EmailService extends Service {
  def getEmails: ServiceCall[NotUsed, List[EmailResponse]]

  def emailEvents: Topic[EmailKafkaEvent]

  def descriptor: Descriptor = {
    import Service._
    named("email").withCalls(
      restCall(Method.GET, "/api/emails", getEmails _)
    )
      .withAutoAcl(true)
      .withExceptionSerializer(new DefaultExceptionSerializer(Environment.simple(mode = Mode.Prod)))
      .withTopics(topic("EmailEvents", emailEvents))
  }
}
```

### Persistence entity

![Image of Email FSM](https://raw.githubusercontent.com/botekchristophe/user-management-lagom/master/EmailFSM.png)

### ReadSide - Cassandra

In order to retrieve the history of emails processed.

#### email table


|  id   | recipient |  topic  |  content  |   status   |   date    |
|-------|-----------|---------|-----------|------------|-----------|
| uuid  | me@me.ca  | Welcome | Hello user|  DELIVERED | timestamp |



## Further work - unit testing

![Image of unit testing strategy](https://dannydainton.files.wordpress.com/2017/06/angtft.jpg)

Cheers
